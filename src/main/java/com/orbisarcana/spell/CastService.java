package com.orbisarcana.spell;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.orbisarcana.OrbisArcana;
import com.orbisarcana.core.ActiveCast;
import com.orbisarcana.core.ActiveCast.Outcome;
import com.orbisarcana.core.CastDenial;
import com.orbisarcana.core.CastMode;
import com.orbisarcana.core.CastRules;
import com.orbisarcana.core.Charges;
import com.orbisarcana.core.MasteryMath;
import com.orbisarcana.data.ModAttachments;
import com.orbisarcana.mana.ManaService;
import com.orbisarcana.network.CastPayloads.CastDenied;
import com.orbisarcana.network.CastPayloads.CastEnded;
import com.orbisarcana.network.CastPayloads.CastStarted;
import com.orbisarcana.network.CastPayloads.CooldownSet;
import com.orbisarcana.progression.KnownSpells;
import com.orbisarcana.school.AttunementService;
import com.orbisarcana.school.EchoService;
import com.orbisarcana.school.TalentEffects;
import com.orbisarcana.spell.behavior.CastContext;
import com.orbisarcana.spell.behavior.ChannelBehavior;
import com.orbisarcana.spell.behavior.SpellBehaviors;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import com.orbisarcana.network.Net;

/**
 * Server-side cast lifecycle (§8.2): validate, snapshot, reserve mana, charge, release or interrupt.
 * The server is the only source of truth; the client just sends {@code CastRequest}/{@code CastStop}.
 */
@EventBusSubscriber(modid = OrbisArcana.MODID)
public final class CastService {
    /** Damage of at least this share of max HP may break a cast (§8.3). */
    private static final float INTERRUPT_DAMAGE_SHARE = 0.15f;
    private static final double INTERRUPT_CHANCE = 0.5;
    /** Movement while charging/channelling: x0.65 (§8.4). */
    private static final AttributeModifier CASTING_SLOW = new AttributeModifier(
            OrbisArcana.id("casting_slow"), -0.35, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    /** Extra slack when a TARGET cast checks that its target is still in range. */
    private static final double RANGE_SLACK = 4.0;
    /** Max cast requests per player per second (§15.6). */
    private static final int REQUESTS_PER_SECOND = 10;

    /** {@code schoolPower}: dominant-school / Dissonance multiplier taken at cast start (§10.2, §10.6). */
    private record ServerCast(int id, Identifier spellId, SpellDefinition spell, ActiveCast core, int mastery,
                              Map<String, Double> params, double schoolPower, double drainPerTick,
                              @Nullable Vec3 groundPoint, @Nullable LivingEntity target) {}

    private static final Map<UUID, ServerCast> ACTIVE = new HashMap<>();
    private static final Map<UUID, Long> GCD_UNTIL = new HashMap<>();
    private static final Map<UUID, Long> NO_FALL_UNTIL = new HashMap<>();
    private static final Map<UUID, long[]> RATE = new HashMap<>();
    private static int nextCastId = 1;

    // ---- entry points (network) ----

    public static void request(ServerPlayer player, Identifier spellId) {
        if (!player.isAlive() || player.isSpectator() || rateLimited(player)) return;
        long now = player.level().getGameTime();

        Optional<SpellDefinition> def = SpellRegistry.get(player.registryAccess(), spellId);
        KnownSpells knowledge = player.getData(ModAttachments.KNOWLEDGE);
        var progress = knowledge.progress(spellId);
        int mastery = progress.map(KnownSpells.SpellProgress::level).orElse(1);
        // Dominant school: -5% cost (§10.2); snapshotted at start like everything else (§8.2)
        double cost = def.map(d -> d.cost() * MasteryMath.costMult(mastery) * AttunementService.costMult(player, d.school())).orElse(0.0);
        Map<String, Double> params = def.isPresent() && progress.isPresent() ? params(def.get(), progress.get()) : Map.of();
        int cooldown = def.map(SpellDefinition::cooldownTicks).orElse(0);
        long fullAt = player.getData(ModAttachments.RESOURCES).cooldowns().getOrDefault(spellId, 0L);

        Optional<CastDenial> denial = CastRules.check(new CastRules.Request(
                def.isPresent() && SpellBehaviors.get(def.get().behavior()).isPresent(),
                def.map(SpellDefinition::enabled).orElse(false),
                progress.isPresent(),
                ACTIVE.containsKey(player.getUUID()),
                now,
                GCD_UNTIL.getOrDefault(player.getUUID(), 0L),
                Charges.readyAt(fullAt, charges(params), cooldown),
                ManaService.mana(player),
                cost));
        if (denial.isEmpty() && def.get().cast().mode() == CastMode.TOGGLE) {
            // TOGGLE arrives with its spells (S7)
            denial = Optional.of(CastDenial.DISABLED);
        }
        if (denial.isPresent()) {
            deny(player, spellId, denial.get());
            return;
        }

        SpellDefinition spell = def.get();
        double range = params.getOrDefault("range", 24.0);

        Vec3 ground = null;
        LivingEntity target = null;
        switch (spell.targeting()) {
            case GROUND -> {
                BlockHitResult hit = clipBlocks(player, range);
                if (hit.getType() == HitResult.Type.MISS) {
                    deny(player, spellId, CastDenial.OUT_OF_RANGE);
                    return;
                }
                ground = hit.getLocation();
            }
            case TARGET -> {
                target = pickTarget(player, range);
                if (target == null && !spell.allowSelf()) {
                    deny(player, spellId, CastDenial.NO_TARGET);
                    return;
                }
            }
            case DIRECTIONAL, SELF -> {}
        }

        double castTimeMult = MasteryMath.castTimeMult(mastery);
        int castTicks = (int) Math.round(spell.cast().ticks() * castTimeMult);
        int minTicks = (int) Math.round(spell.cast().minTicks() * castTimeMult);
        CastMode mode = spell.cast().mode();
        // Water Echo "Flow": with full stacks the next charged cast fires at once (§10.5)
        if (mode == CastMode.CHARGED && EchoService.consumeFlow(player)) {
            mode = CastMode.INSTANT;
            castTicks = 0;
            minTicks = 0;
        }
        // CHANNEL: "cost" is mana per second, drained every tick (§5.3); nothing is reserved up front
        boolean channel = mode == CastMode.CHANNEL;
        ActiveCast core = new ActiveCast(mode, now, castTicks, minTicks, channel ? 0 : cost);

        // Reserve: take the full cost now, refund the unspent part on interruption (§8.3)
        if (!channel) ManaService.setMana(player, ManaService.mana(player) - cost);

        ServerCast cast = new ServerCast(nextCastId++, spellId, spell, core, mastery, params,
                AttunementService.powerMult(player, spell.school()), channel ? cost / 20.0 : 0, ground, target);
        ACTIVE.put(player.getUUID(), cast);
        Net.toPlayerAndNearby(player,
                new CastStarted(cast.id(), player.getId(), spellId, now, castTicks, mastery,
                        Optional.ofNullable(ground), target == null ? -1 : target.getId(), sealLevel(player, spell.school())));

        if (core.mode == CastMode.INSTANT) {
            finish(player, cast, Outcome.RELEASED);
        } else {
            applySlow(player);
        }
    }

    public static void stop(ServerPlayer player, int castId, boolean cancel) {
        ServerCast cast = ACTIVE.get(player.getUUID());
        if (cast == null || cast.id() != castId) return;
        long now = player.level().getGameTime();
        finish(player, cast, !cancel && cast.core().canRelease(now) ? Outcome.RELEASED : Outcome.CANCELLED);
    }

    /** Breaks the player's current cast, if any. */
    public static void interrupt(ServerPlayer player, Outcome reason) {
        ServerCast cast = ACTIVE.get(player.getUUID());
        if (cast != null) finish(player, cast, reason);
    }

    public static boolean isCasting(ServerPlayer player) {
        return ACTIVE.containsKey(player.getUUID());
    }

    /** Id of the player's active cast, or -1. */
    public static int activeCastId(ServerPlayer player) {
        ServerCast cast = ACTIVE.get(player.getUUID());
        return cast == null ? -1 : cast.id();
    }

    /** Used by {@code wind_step} milestone 5. */
    public static void grantNoFall(ServerPlayer player, int ticks) {
        NO_FALL_UNTIL.put(player.getUUID(), player.level().getGameTime() + ticks);
    }

    // ---- lifecycle ----

    private static void finish(ServerPlayer player, ServerCast cast, Outcome outcome) {
        ACTIVE.remove(player.getUUID());
        removeSlow(player);
        long now = player.level().getGameTime();
        ActiveCast core = cast.core();

        if (outcome == Outcome.RELEASED) {
            SpellBehaviors.get(cast.spell().behavior()).ifPresent(b -> b.release(context(player, cast, now)));
            if (TalentEffects.crownOfFlameFree(player, cast.spell().school())) {
                ManaService.setMana(player, ManaService.mana(player) + core.cost);
            }
            int cooldown = cast.spell().cooldownTicks();
            if (cooldown > 0) {
                var cooldowns = player.getData(ModAttachments.RESOURCES).cooldowns();
                long fullAt = Charges.afterUse(cooldowns.getOrDefault(cast.spellId(), 0L), now, cooldown);
                cooldowns.put(cast.spellId(), fullAt);
                Net.toPlayer(player, new CooldownSet(cast.spellId(), fullAt, cooldown));
            }
        } else {
            double refund = core.cost - core.manaSpent(outcome, now);
            ManaService.setMana(player, ManaService.mana(player) + refund);
        }
        GCD_UNTIL.put(player.getUUID(), now + CastRules.GCD_TICKS);
        Net.toPlayerAndNearby(player, new CastEnded(cast.id(), player.getId(), outcome.ordinal()));
    }

    private static CastContext context(ServerPlayer player, ServerCast cast, long now) {
        return new CastContext(player, cast.spellId(), cast.id(), cast.spell(), cast.mastery(), cast.params(),
                cast.core().power(now) * cast.schoolPower(), cast.groundPoint(), cast.target());
    }

    /** One tick of a CHANNEL cast: drain mana, run the behavior, end at the max duration or when mana runs out. */
    private static void tickChannel(ServerPlayer player, ServerCast cast, long now) {
        double mana = ManaService.mana(player);
        if (mana < cast.drainPerTick()) {
            finish(player, cast, Outcome.RELEASED);
            return;
        }
        ManaService.setMana(player, mana - cast.drainPerTick());
        int elapsed = (int) cast.core().elapsed(now);
        SpellBehaviors.get(cast.spell().behavior()).ifPresent(b -> {
            if (b instanceof ChannelBehavior channel) channel.channelTick(context(player, cast, now), elapsed);
        });
        if (ACTIVE.get(player.getUUID()) == cast && elapsed >= cast.core().castTicks) finish(player, cast, Outcome.RELEASED);
    }

    /** Snapshot of the spell params: enabled milestones applied, primary stat scaled by mastery (§7.4). */
    private static Map<String, Double> params(SpellDefinition spell, KnownSpells.SpellProgress progress) {
        int mastery = progress.level();
        Map<String, Double> params = new HashMap<>(spell.paramsAt(mastery, level -> !progress.isMilestoneEnabled(level)));
        params.computeIfPresent(spell.primaryStat(), (k, v) -> v * MasteryMath.primaryStatMult(mastery));
        return params;
    }

    /** Dominant seal drawn on the circle (§9.5): 0 none, 1 dominant school, 2 full seal at resonance 10. */
    private static int sealLevel(ServerPlayer player, Identifier school) {
        var a = AttunementService.attunement(player);
        if (a.dominant().filter(school::equals).isEmpty()) return 0;
        var p = a.schools().get(school);
        return p != null && p.resonance() >= com.orbisarcana.core.SchoolRules.MAX_RESONANCE ? 2 : 1;
    }

    private static int charges(Map<String, Double> params) {
        return Math.max(1, (int) Math.round(params.getOrDefault("charges", 1.0)));
    }

    private static void deny(ServerPlayer player, Identifier spellId, CastDenial reason) {
        Net.toPlayer(player, new CastDenied(spellId, reason.ordinal()));
    }

    private static boolean rateLimited(ServerPlayer player) {
        long second = player.level().getGameTime() / 20;
        long[] window = RATE.computeIfAbsent(player.getUUID(), u -> new long[] {second, 0});
        if (window[0] != second) {
            window[0] = second;
            window[1] = 0;
        }
        return ++window[1] > REQUESTS_PER_SECOND;
    }

    // ---- targeting ----

    private static BlockHitResult clipBlocks(ServerPlayer player, double range) {
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(range));
        return player.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
    }

    private static @Nullable LivingEntity pickTarget(ServerPlayer player, double range) {
        Vec3 eye = player.getEyePosition();
        BlockHitResult block = clipBlocks(player, range);
        Vec3 end = block.getType() == HitResult.Type.MISS ? eye.add(player.getLookAngle().scale(range)) : block.getLocation();
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eye, end,
                player.getBoundingBox().expandTowards(end.subtract(eye)).inflate(1.0),
                e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator() && e.isPickable(),
                eye.distanceToSqr(end));
        return hit != null && hit.getEntity() instanceof LivingEntity living ? living : null;
    }

    // ---- movement penalty ----

    private static void applySlow(ServerPlayer player) {
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null && !speed.hasModifier(CASTING_SLOW.id())) speed.addTransientModifier(CASTING_SLOW);
        player.setSprinting(false);
    }

    private static void removeSlow(ServerPlayer player) {
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) speed.removeModifier(CASTING_SLOW.id());
    }

    // ---- events ----

    /** Active casts tick with the server (players driven only by packets, e.g. mock players, tick too). */
    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        if (ACTIVE.isEmpty()) return;
        for (UUID id : List.copyOf(ACTIVE.keySet())) {
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(id);
            ServerCast cast = ACTIVE.get(id);
            if (player == null) {
                ACTIVE.remove(id);
            } else if (cast != null) {
                tickCast(player, cast);
            }
        }
    }

    private static void tickCast(ServerPlayer player, ServerCast cast) {
        if (cast.core().mode == CastMode.CHANNEL) {
            tickChannel(player, cast, player.level().getGameTime());
            if (ACTIVE.get(player.getUUID()) != cast) return;
        }
        player.setSprinting(false);
        LivingEntity target = cast.target();
        if (target != null && target != player) {
            double maxRange = cast.params().getOrDefault("range", 24.0) + RANGE_SLACK;
            if (!target.isAlive() || target.level() != player.level() || target.distanceTo(player) > maxRange) {
                finish(player, cast, Outcome.LOST_TARGET);
            }
        }
    }

    @SubscribeEvent
    static void onDamage(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !isCasting(player)) return;
        if (event.getHealthDamage() >= player.getMaxHealth() * INTERRUPT_DAMAGE_SHARE
                && player.getRandom().nextDouble() < INTERRUPT_CHANCE) {
            interrupt(player, Outcome.INTERRUPTED);
        }
    }

    @SubscribeEvent
    static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) interrupt(player, Outcome.INTERRUPTED);
    }

    @SubscribeEvent
    static void onAttack(AttackEntityEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ServerCast cast = ACTIVE.get(player.getUUID());
            if (cast != null && cast.core().mode == CastMode.CHARGED) finish(player, cast, Outcome.INTERRUPTED);
        }
    }

    @SubscribeEvent
    static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) interrupt(player, Outcome.INTERRUPTED);
    }

    /** Re-sends running cooldowns (they are saved server-side) and drops expired ones. */
    @SubscribeEvent
    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        long now = player.level().getGameTime();
        var cooldowns = player.getData(ModAttachments.RESOURCES).cooldowns();
        cooldowns.values().removeIf(until -> until <= now);
        cooldowns.forEach((spellId, fullAt) -> SpellRegistry.get(player.registryAccess(), spellId)
                .ifPresent(def -> Net.toPlayer(player, new CooldownSet(spellId, fullAt, def.cooldownTicks()))));
    }

    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            interrupt(player, Outcome.INTERRUPTED);
            UUID id = player.getUUID();
            GCD_UNTIL.remove(id);
            NO_FALL_UNTIL.remove(id);
            RATE.remove(id);
        }
    }

    @SubscribeEvent
    static void onFall(LivingFallEvent event) {
        Entity e = event.getEntity();
        Long until = NO_FALL_UNTIL.get(e.getUUID());
        if (until != null && e.level().getGameTime() <= until) event.setCanceled(true);
    }

    @SubscribeEvent
    static void onServerStopping(ServerStoppingEvent event) {
        ACTIVE.clear();
        GCD_UNTIL.clear();
        NO_FALL_UNTIL.clear();
        RATE.clear();
    }

    private CastService() {}
}
