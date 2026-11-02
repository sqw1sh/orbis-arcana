package com.orbisarcana.school;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.orbisarcana.OrbisArcana;
import com.orbisarcana.core.EchoRules;
import com.orbisarcana.core.SchoolRules;
import com.orbisarcana.core.TalentRules;
import com.orbisarcana.network.CastPayloads.EchoState;
import com.orbisarcana.network.CastPayloads.HeatMark;
import com.orbisarcana.network.Net;
import com.orbisarcana.registry.ModDamageTypes;
import com.orbisarcana.spell.Arcs;
import com.orbisarcana.spell.Frost;
import java.util.List;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import com.orbisarcana.mana.ManaService;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

/**
 * School Echoes (§10.5), active only for the dominant school from resonance 1. Session state, not saved.
 * Fire "Heat": stacks on targets, bursts at 5. Water "Flow": healing casts stack, then the next charged
 * cast is instant.
 */
@EventBusSubscriber(modid = OrbisArcana.MODID)
public final class EchoService {
    /** {@code lastCast}: one stack per target per cast (multi-projectile casts and burning ground pulses). */
    private record Heat(UUID caster, double stacks, long lastTick, int lastCast) {}

    /** target -> heat placed by one caster. */
    private static final Map<UUID, Heat> HEAT = new HashMap<>();
    /** target -> Frost placed by one caster (same bookkeeping as Heat). */
    private static final Map<UUID, Heat> FROST = new HashMap<>();
    /** A Nature sprout growing at {@code pos}. */
    private record Sprout(UUID caster, ServerLevel level, Vec3 pos, long matureAt, int castId) {}

    private static final List<Sprout> SPROUTS = new java.util.ArrayList<>();

    /** Conductivity mark on a target ({@code entityId} for expiry messages). */
    private record Mark(UUID caster, long until, int castId, int entityId) {}

    /** target -> Conductivity mark. */
    private static final Map<UUID, Mark> MARKS = new HashMap<>();
    /** player -> flow stacks. */
    private static final Map<UUID, Double> FLOW = new HashMap<>();
    /** player -> last cast id that already gave flow (one stack per cast, zones pulse many times). */
    private static final Map<UUID, Integer> FLOW_CAST = new HashMap<>();
    /** player -> Radiance charge. */
    private static final Map<UUID, Double> RADIANCE = new HashMap<>();
    /** player -> Bulwark charge. */
    private static final Map<UUID, Double> BULWARK = new HashMap<>();
    /** player -> Impulse charge, and the position last tick. */
    private static final Map<UUID, Double> IMPULSE = new HashMap<>();
    private static final Map<UUID, Vec3> LAST_POS = new HashMap<>();

    /** Echo charge per effective action: Dissonance halves it (§10.6), the Ring of Echo adds 20% (§11.2). */
    private static double gain(ServerPlayer player) {
        return EchoRules.stackGain(AttunementService.isDissonant(player)) * com.orbisarcana.item.Accessories.echoGainMult(player);
    }

    /** Echo tier of {@code school} for the player: 0 unless it is dominant with resonance >= 1. */
    public static int tier(ServerPlayer player, Identifier school) {
        var a = AttunementService.attunement(player);
        if (a.dominant().filter(school::equals).isEmpty()) return 0;
        var p = a.schools().get(school);
        return p == null ? 0 : SchoolRules.echoTier(p.resonance());
    }

    // ---- Fire: Heat ----

    /** A spell of {@code school} from cast {@code castId} damaged {@code target}. */
    public static void onSpellHit(ServerPlayer caster, Identifier school, LivingEntity target, int castId) {
        if (school.equals(Schools.ICE)) {
            frost(caster, target, castId);
            return;
        }
        if (school.equals(Schools.NATURE)) {
            onEffectiveCast(caster, school, target.position(), castId);
            return;
        }
        if (school.equals(Schools.LIGHTNING)) {
            TalentEffects.lightningRod(caster, target);
            conduct(caster, target, castId);
            return;
        }
        if (!school.equals(Schools.FIRE) || !target.isAlive()) return;
        int tier = tier(caster, Schools.FIRE);
        if (tier <= 0) return;
        long now = caster.level().getGameTime();
        if (HEAT.size() > 256) HEAT.values().removeIf(h -> now - h.lastTick() > EchoRules.HEAT_DECAY_TICKS);
        Heat old = HEAT.get(target.getUUID());
        boolean live = old != null && old.caster().equals(caster.getUUID()) && now - old.lastTick() <= EchoRules.HEAT_DECAY_TICKS;
        if (live && old.lastCast() == castId) return;
        double stacks = live ? old.stacks() : 0;
        stacks += gain(caster);
        if (stacks >= EchoRules.HEAT_MAX) {
            HEAT.remove(target.getUUID());
            heatBurst(caster, target, tier);
            stacks = 0;
        } else {
            HEAT.put(target.getUUID(), new Heat(caster.getUUID(), stacks, now, castId));
        }
        Net.toPlayer(caster, new EchoState(Schools.FIRE, (float) stacks, EchoRules.HEAT_MAX));
        Net.toNearby(target, new HeatMark(target.getId(), (float) stacks, Schools.FIRE));
    }

    private static void heatBurst(ServerPlayer caster, LivingEntity centre, int tier) {
        ServerLevel level = caster.level();
        float damage = (float) EchoRules.heatBurstDamage(tier, AttunementService.isDissonant(caster));
        boolean fanning = AttunementService.hasTalent(caster, Schools.FIRE, TalentRules.A1);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, centre.getBoundingBox().inflate(EchoRules.HEAT_RADIUS),
                e -> e.isAlive() && e != caster && e.distanceTo(centre) <= EchoRules.HEAT_RADIUS + centre.getBbWidth())) {
            e.hurtServer(level, ModDamageTypes.source(e, Schools.FIRE, null, caster), damage);
            if (fanning) e.igniteForTicks(EchoRules.FANNING_IGNITE_TICKS);
        }
        level.sendParticles(ParticleTypes.FLAME, centre.getX(), centre.getY() + centre.getBbHeight() / 2, centre.getZ(), 40, 0.8, 0.5, 0.8, 0.08);
        level.sendParticles(ParticleTypes.EXPLOSION, centre.getX(), centre.getY() + centre.getBbHeight() / 2, centre.getZ(), 1, 0, 0, 0, 0);
    }

    // ---- Ice: Frost ----

    /** Ice hits stack Frost on the target (one per cast): each stack slows more, the 5th freezes. */
    private static void frost(ServerPlayer caster, LivingEntity target, int castId) {
        int tier = tier(caster, Schools.ICE);
        if (tier <= 0 || !target.isAlive()) return;
        long now = caster.level().getGameTime();
        if (FROST.size() > 256) FROST.values().removeIf(h -> now - h.lastTick() > EchoRules.FROST_DECAY_TICKS);
        Heat old = FROST.get(target.getUUID());
        boolean live = old != null && old.caster().equals(caster.getUUID()) && now - old.lastTick() <= EchoRules.FROST_DECAY_TICKS;
        if (live && old.lastCast() == castId) return;
        double stacks = (live ? old.stacks() : 0) + gain(caster);
        if (stacks >= EchoRules.FROST_MAX) {
            FROST.remove(target.getUUID());
            Frost.freeze(target, EchoRules.freezeTicks(tier), caster);
            stacks = 0;
        } else {
            FROST.put(target.getUUID(), new Heat(caster.getUUID(), stacks, now, castId));
            target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, EchoRules.FROST_SLOW_TICKS, Math.max(0, (int) stacks - 1)), caster);
        }
        Net.toPlayer(caster, new EchoState(Schools.ICE, (float) stacks, EchoRules.FROST_MAX));
        Net.toNearby(target, new HeatMark(target.getId(), (float) stacks, Schools.ICE));
    }

    // ---- Lightning: Conductivity ----

    /**
     * A lightning hit arcs on to marked targets near it (consuming their marks), then marks the target. One mark per
     * target per cast, so a jump never comes back to the same cast's targets.
     */
    private static void conduct(ServerPlayer caster, LivingEntity target, int castId) {
        int tier = tier(caster, Schools.LIGHTNING);
        if (tier <= 0) return;
        ServerLevel level = caster.level();
        long now = level.getGameTime();
        int jumps = EchoRules.CONDUCT_JUMPS + (AttunementService.hasTalent(caster, Schools.LIGHTNING, TalentRules.A1) ? 1 : 0);
        float damage = (float) EchoRules.conductDamage(tier, AttunementService.isDissonant(caster));
        List<LivingEntity> marked = level.getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(EchoRules.CONDUCT_RANGE),
                        e -> e != target && e.isAlive() && markedBy(e, caster, now) && MARKS.get(e.getUUID()).castId() != castId)
                .stream().sorted(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(target))).limit(jumps).toList();
        for (LivingEntity e : marked) {
            MARKS.remove(e.getUUID());
            Net.toNearby(e, new HeatMark(e.getId(), 0f, Schools.LIGHTNING));
            e.hurtServer(level, ModDamageTypes.source(e, Schools.LIGHTNING, null, caster), damage);
            Arcs.draw(level, Arcs.body(target), Arcs.body(e));
        }
        long hold = AttunementService.hasTalent(caster, Schools.LIGHTNING, TalentRules.ROOT) ? EchoRules.CONDUCTOR_MARK_TICKS : EchoRules.MARK_TICKS;
        if (target.isAlive()) {
            MARKS.put(target.getUUID(), new Mark(caster.getUUID(), now + hold, castId, target.getId()));
            Net.toNearby(target, new HeatMark(target.getId(), 1f, Schools.LIGHTNING));
        }
        sendMarks(caster, now);
    }

    public static boolean markedBy(LivingEntity e, ServerPlayer caster, long now) {
        Mark m = MARKS.get(e.getUUID());
        return m != null && m.caster().equals(caster.getUUID()) && now < m.until();
    }

    private static void sendMarks(ServerPlayer caster, long now) {
        long n = MARKS.values().stream().filter(m -> m.caster().equals(caster.getUUID()) && now < m.until()).count();
        Net.toPlayer(caster, new EchoState(Schools.LIGHTNING, (float) Math.min(n, EchoRules.MARKS_SHOWN), EchoRules.MARKS_SHOWN));
    }

    /** Once a second: marks run out; with A2 "Storm Net" weak arcs join marked targets of one caster. */
    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || MARKS.isEmpty() || level.getGameTime() % 20 != 0) return;
        long now = level.getGameTime();
        var it = MARKS.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            if (now < e.getValue().until()) continue;
            it.remove();
            var entity = level.getEntity(e.getValue().entityId());
            if (entity != null) Net.toNearby(entity, new HeatMark(entity.getId(), 0f, Schools.LIGHTNING));
            ServerPlayer owner = level.getServer().getPlayerList().getPlayer(e.getValue().caster());
            if (owner != null) sendMarks(owner, now);
        }
        Map<UUID, List<LivingEntity>> byCaster = new HashMap<>();
        for (Mark m : MARKS.values()) {
            if (level.getEntity(m.entityId()) instanceof LivingEntity le && le.isAlive()) byCaster.computeIfAbsent(m.caster(), k -> new java.util.ArrayList<>()).add(le);
        }
        byCaster.forEach((casterId, list) -> {
            ServerPlayer caster = level.getServer().getPlayerList().getPlayer(casterId);
            if (caster == null || list.size() < 2 || !AttunementService.hasTalent(caster, Schools.LIGHTNING, TalentRules.A2)) return;
            for (int i = 0; i < list.size(); i++) {
                for (int j = i + 1; j < list.size(); j++) {
                    LivingEntity a = list.get(i), b = list.get(j);
                    if (a.distanceTo(b) > EchoRules.STORM_NET_RANGE) continue;
                    a.hurtServer(level, ModDamageTypes.source(a, Schools.LIGHTNING, null, caster), EchoRules.STORM_NET_DAMAGE);
                    b.hurtServer(level, ModDamageTypes.source(b, Schools.LIGHTNING, null, caster), EchoRules.STORM_NET_DAMAGE);
                    Arcs.draw(level, Arcs.body(a), Arcs.body(b));
                }
            }
        });
    }

    // ---- Nature: Sprouting ----

    /** A Nature cast hit, healed or held something: plant a sprout there (one per cast, at most 3 growing). */
    public static void onEffectiveCast(ServerPlayer caster, Identifier school, Vec3 pos, int castId) {
        if (!school.equals(Schools.NATURE)) return;
        int tier = tier(caster, Schools.NATURE);
        if (tier <= 0) return;
        UUID id = caster.getUUID();
        if (SPROUTS.stream().anyMatch(s -> s.caster().equals(id) && s.castId() == castId)) return;
        if (sprouts(caster) >= EchoRules.SPROUT_MAX) return;
        long grow = AttunementService.hasTalent(caster, Schools.NATURE, TalentRules.ROOT) ? EchoRules.SEED_MATURE_TICKS : EchoRules.SPROUT_MATURE_TICKS;
        SPROUTS.add(new Sprout(id, caster.level(), pos, caster.level().getGameTime() + grow, castId));
        Net.toPlayer(caster, new EchoState(Schools.NATURE, sprouts(caster), EchoRules.SPROUT_MAX));
    }

    public static int sprouts(ServerPlayer caster) {
        return (int) SPROUTS.stream().filter(s -> s.caster().equals(caster.getUUID())).count();
    }

    @SubscribeEvent
    static void onSproutTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || SPROUTS.isEmpty()) return;
        long now = level.getGameTime();
        for (Sprout s : List.copyOf(SPROUTS)) {
            if (s.level() != level) continue;
            ServerPlayer caster = level.getServer().getPlayerList().getPlayer(s.caster());
            if (caster == null) {
                SPROUTS.remove(s);
                continue;
            }
            if (now < s.matureAt()) {
                if (now % 10 == 0) level.sendParticles(ParticleTypes.HAPPY_VILLAGER, s.pos().x, s.pos().y + 0.3, s.pos().z, 2, 0.15, 0.1, 0.15, 0);
                continue;
            }
            SPROUTS.remove(s);
            mature(caster, s);
            Net.toPlayer(caster, new EchoState(Schools.NATURE, sprouts(caster), EchoRules.SPROUT_MAX));
        }
    }

    /**
     * A sprout matures: heals the most wounded ally near it, otherwise stings the enemies near it. A1 "Thorny
     * Branches": it always stings too; A2 "Wild Growth": enemies near it are entangled.
     */
    private static void mature(ServerPlayer caster, Sprout s) {
        ServerLevel level = s.level();
        int tier = Math.max(1, tier(caster, Schools.NATURE));
        double power = EchoRules.sproutPower(tier, AttunementService.isDissonant(caster));
        AABB area = new AABB(s.pos(), s.pos()).inflate(EchoRules.SPROUT_RADIUS, 2, EchoRules.SPROUT_RADIUS);
        var wounded = level.getEntitiesOfClass(net.minecraft.world.entity.player.Player.class, area,
                        p -> p.isAlive() && p.getHealth() < p.getMaxHealth())
                .stream().min(java.util.Comparator.comparingDouble(p -> p.getHealth() / p.getMaxHealth()));
        boolean thorny = AttunementService.hasTalent(caster, Schools.NATURE, TalentRules.A1);
        boolean healed = false;
        if (wounded.isPresent()) {
            double mult = AttunementService.hasTalent(caster, Schools.NATURE, TalentRules.B1) ? EchoRules.HEALING_GROVE_MULT : 1.0;
            wounded.get().heal((float) (power * mult * TalentEffects.healMult(caster)));
            healed = true;
        }
        if (!healed || thorny) {
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, area,
                    e -> e.isAlive() && e != caster && !(e instanceof net.minecraft.world.entity.player.Player))) {
                e.hurtServer(level, ModDamageTypes.source(e, Schools.NATURE, null, caster), (float) power);
                if (AttunementService.hasTalent(caster, Schools.NATURE, TalentRules.A2)) {
                    com.orbisarcana.spell.behavior.RootsBehavior.entangle(caster, e, EchoRules.WILD_GROWTH_TICKS, Schools.NATURE);
                }
            }
        }
        level.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, s.pos().x, s.pos().y + 0.5, s.pos().z, 25, 1, 0.4, 1, 0);
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, s.pos().x, s.pos().y + 0.5, s.pos().z, 15, 0.8, 0.4, 0.8, 0);
    }

    // ---- Light: Radiance ----

    /** The player healed, shielded or spared someone {@code amount} HP: Radiance charges; full, it pulses. */
    public static void onSupport(ServerPlayer player, float amount) {
        int tier = tier(player, Schools.LIGHT);
        if (tier <= 0 || amount <= 0) return;
        double before = radiance(player);
        double charge = before + amount * gain(player);
        if (charge >= EchoRules.RADIANCE_MAX) {
            charge = 0;
            radiancePulse(player, tier);
        }
        RADIANCE.put(player.getUUID(), charge);
        if ((int) charge != (int) before) Net.toPlayer(player, new EchoState(Schools.LIGHT, (float) Math.floor(charge), (int) EchoRules.RADIANCE_MAX));
    }

    public static double radiance(ServerPlayer player) {
        return RADIANCE.getOrDefault(player.getUUID(), 0.0);
    }

    /**
     * Full Radiance: allies around lose their harmful effects. B1 "Veil": they get a shield; A2 "Retribution Ray":
     * enemies around are burnt.
     */
    private static void radiancePulse(ServerPlayer player, int tier) {
        ServerLevel level = player.level();
        double r = tier >= 2 ? EchoRules.RADIANCE_TIER2_RADIUS : EchoRules.RADIANCE_RADIUS;
        boolean veil = AttunementService.hasTalent(player, Schools.LIGHT, TalentRules.B1);
        for (var ally : level.getEntitiesOfClass(net.minecraft.world.entity.player.Player.class, player.getBoundingBox().inflate(r),
                a -> a.isAlive() && a.distanceTo(player) <= r)) {
            ally.getActiveEffects().stream()
                    .filter(e -> e.getEffect().value().getCategory() == net.minecraft.world.effect.MobEffectCategory.HARMFUL)
                    .map(MobEffectInstance::getEffect).toList()
                    .forEach(ally::removeEffect);
            if (veil) com.orbisarcana.spell.Shields.grant(ally, EchoRules.VEIL_SHIELD, EchoRules.VEIL_TICKS, null);
        }
        if (AttunementService.hasTalent(player, Schools.LIGHT, TalentRules.A2)) {
            float damage = (float) EchoRules.retributionDamage(tier, AttunementService.isDissonant(player));
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(r),
                    e -> e.isAlive() && !(e instanceof net.minecraft.world.entity.player.Player) && e.distanceTo(player) <= r)) {
                e.hurtServer(level, ModDamageTypes.source(e, Schools.LIGHT, null, player), damage);
            }
        }
        for (int i = 0; i < 24; i++) {
            double a = Math.PI * 2 * i / 24;
            level.sendParticles(ParticleTypes.END_ROD, player.getX() + Math.cos(a) * r, player.getY() + 0.5, player.getZ() + Math.sin(a) * r, 1, 0, 0.1, 0, 0.02);
        }
        level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1, player.getZ(), 40, 0.5, 0.8, 0.5, 0.15);
    }

    // ---- Dark: Dusk ----

    private record Dusk(UUID caster, long until, int entityId) {}

    /** target -> Dusk mark. */
    private static final Map<UUID, Dusk> DUSK = new HashMap<>();
    /** player -> marks absorbed towards the next Eclipse. */
    private static final Map<UUID, Integer> ECLIPSE = new HashMap<>();
    private static boolean quiet;

    /** Runs damage that must not absorb Dusk marks (damage over time, the mark's own blow). */
    public static void quietly(Runnable damage) {
        boolean was = quiet;
        quiet = true;
        try {
            damage.run();
        } finally {
            quiet = was;
        }
    }

    /** A Dark debuff leaves the caster's Dusk mark on {@code target}. */
    public static void markDusk(ServerPlayer caster, LivingEntity target) {
        if (target instanceof net.minecraft.world.entity.player.Player || !target.isAlive() || tier(caster, Schools.DARK) <= 0) return;
        long now = caster.level().getGameTime();
        DUSK.put(target.getUUID(), new Dusk(caster.getUUID(), now + EchoRules.DUSK_MARK_TICKS, target.getId()));
        Net.toNearby(target, new HeatMark(target.getId(), 1f, Schools.DARK));
        sendDusk(caster, now);
    }

    public static boolean duskBy(LivingEntity e, ServerPlayer caster) {
        Dusk d = DUSK.get(e.getUUID());
        return d != null && d.caster().equals(caster.getUUID()) && caster.level().getGameTime() < d.until();
    }

    private static void sendDusk(ServerPlayer caster, long now) {
        long n = DUSK.values().stream().filter(d -> d.caster().equals(caster.getUUID()) && now < d.until()).count();
        Net.toPlayer(caster, new EchoState(Schools.DARK, (float) Math.min(n, EchoRules.DUSK_SHOWN), EchoRules.DUSK_SHOWN));
    }

    /** Any harmful effect the player puts on a mob marks it. */
    @SubscribeEvent
    static void onEffectAdded(net.neoforged.neoforge.event.entity.living.MobEffectEvent.Added event) {
        if (event.getEffectSource() instanceof ServerPlayer p && p != event.getEntity()
                && event.getEffectInstance().getEffect().value().getCategory() == net.minecraft.world.effect.MobEffectCategory.HARMFUL) {
            markDusk(p, event.getEntity());
        }
    }

    /** The marker's blow absorbs the mark: extra damage and mana; A2 "Eclipse" every 3rd time. */
    @SubscribeEvent
    static void onDuskHit(LivingDamageEvent.Post event) {
        if (quiet || DUSK.isEmpty() || event.getHealthDamage() <= 0) return;
        LivingEntity victim = event.getEntity();
        if (!(event.getSource().getEntity() instanceof ServerPlayer p) || p == victim || !duskBy(victim, p)) return;
        int tier = tier(p, Schools.DARK);
        if (tier <= 0) return;
        ServerLevel level = p.level();
        DUSK.remove(victim.getUUID());
        Net.toNearby(victim, new HeatMark(victim.getId(), 0f, Schools.DARK));
        boolean dissonant = AttunementService.isDissonant(p);
        if (victim.isAlive()) {
            float bonus = (float) EchoRules.duskDamage(tier, dissonant);
            quietly(() -> {
                victim.damageCooldownTime = 0;
                victim.hurtServer(level, ModDamageTypes.source(victim, Schools.DARK, null, p), bonus);
            });
        }
        double mana = EchoRules.duskMana(dissonant, AttunementService.hasTalent(p, Schools.DARK, TalentRules.ROOT));
        ManaService.setMana(p, Math.min(ManaService.maxMana(p), ManaService.mana(p) + mana));
        Vec3 c = victim.getBoundingBox().getCenter();
        level.sendParticles(ParticleTypes.SQUID_INK, c.x, c.y, c.z, 15, 0.3, 0.4, 0.3, 0.05);
        if (AttunementService.hasTalent(p, Schools.DARK, TalentRules.A2)
                && ECLIPSE.merge(p.getUUID(), 1, Integer::sum) >= EchoRules.ECLIPSE_EVERY) {
            ECLIPSE.put(p.getUUID(), 0);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, victim.getBoundingBox().inflate(EchoRules.ECLIPSE_RADIUS),
                    e -> e.isAlive() && !(e instanceof net.minecraft.world.entity.player.Player))) {
                e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, EchoRules.ECLIPSE_BLIND_TICKS, 0), p);
                if (e instanceof net.minecraft.world.entity.Mob mob) mob.setTarget(null);
            }
            level.sendParticles(ParticleTypes.SQUID_INK, c.x, c.y, c.z, 60, EchoRules.ECLIPSE_RADIUS / 2, 0.8, EchoRules.ECLIPSE_RADIUS / 2, 0.1);
        }
        sendDusk(p, level.getGameTime());
    }

    @SubscribeEvent
    static void onDuskTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || DUSK.isEmpty() || level.getGameTime() % 20 != 0) return;
        long now = level.getGameTime();
        var it = DUSK.entrySet().iterator();
        while (it.hasNext()) {
            Dusk d = it.next().getValue();
            if (now < d.until()) continue;
            it.remove();
            var entity = level.getEntity(d.entityId());
            if (entity != null) Net.toNearby(entity, new HeatMark(entity.getId(), 0f, Schools.DARK));
            ServerPlayer owner = level.getServer().getPlayerList().getPlayer(d.caster());
            if (owner != null) sendDusk(owner, now);
        }
    }

    // ---- Astral: Constellation ----

    private static final Identifier SPIKE = OrbisArcana.id("spike");

    /** With the Astral Echo every released spell places a star where it aimed (Star Spike places its own). */
    public static void onCastReleased(com.orbisarcana.spell.behavior.CastContext ctx) {
        if (tier(ctx.caster(), Schools.ASTRAL) <= 0 || ctx.spell().behavior().equals(SPIKE)) return;
        Vec3 pos = ctx.groundPoint() != null ? ctx.groundPoint().add(0, 1, 0)
                : ctx.target() != null ? ctx.target().getBoundingBox().getCenter()
                : ctx.caster().position().add(ctx.caster().getLookAngle().multiply(1, 0, 1).scale(2)).add(0, 1, 0);
        com.orbisarcana.spell.Stars.place(ctx.caster(), pos, 0, 0);
    }

    // ---- Water: Flow ----

    /** A healing/support cast succeeded (counted once per cast). */
    public static void onHealCast(ServerPlayer player, int castId) {
        int tier = tier(player, Schools.WATER);
        if (tier <= 0) return;
        Integer last = FLOW_CAST.put(player.getUUID(), castId);
        if (last != null && last == castId) return;
        int needed = EchoRules.flowNeeded(tier);
        double stacks = Math.min(needed, FLOW.getOrDefault(player.getUUID(), 0.0) + gain(player));
        FLOW.put(player.getUUID(), stacks);
        Net.toPlayer(player, new EchoState(Schools.WATER, (float) stacks, needed));
    }

    /** True (and the stacks are spent) if Flow makes this cast instant. */
    public static boolean consumeFlow(ServerPlayer player) {
        int tier = tier(player, Schools.WATER);
        if (tier <= 0) return false;
        int needed = EchoRules.flowNeeded(tier);
        if (FLOW.getOrDefault(player.getUUID(), 0.0) < needed) return false;
        FLOW.put(player.getUUID(), 0.0);
        Net.toPlayer(player, new EchoState(Schools.WATER, 0f, needed));
        return true;
    }

    // ---- Earth: Bulwark ----

    /** Damage absorbed by armour, shields and absorption charges Bulwark. */
    @SubscribeEvent
    static void onDamage(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        int tier = tier(player, Schools.EARTH);
        if (tier <= 0) return;
        double absorbed = event.getOriginalDamage() - event.getHealthDamage();
        if (absorbed <= 0) return;
        double charge = Math.min(EchoRules.BULWARK_MAX,
                bulwark(player) + absorbed * gain(player));
        BULWARK.put(player.getUUID(), charge);
        Net.toPlayer(player, new EchoState(Schools.EARTH, (float) charge, (int) EchoRules.BULWARK_MAX));
    }

    public static double bulwark(ServerPlayer player) {
        return BULWARK.getOrDefault(player.getUUID(), 0.0);
    }

    /** For tests and admin tools. */
    public static void setBulwark(ServerPlayer player, double charge) {
        BULWARK.put(player.getUUID(), charge);
    }

    /** An attack spell was released: a charged Bulwark bursts as a stone wave around the caster. */
    public static void onAttackCast(ServerPlayer player) {
        int tier = tier(player, Schools.EARTH);
        double charge = bulwark(player);
        if (tier <= 0 || charge < EchoRules.BULWARK_MIN_RELEASE) return;
        BULWARK.put(player.getUUID(), 0.0);
        Net.toPlayer(player, new EchoState(Schools.EARTH, 0f, (int) EchoRules.BULWARK_MAX));
        double radius = EchoRules.BULWARK_RADIUS + (tier >= 2 ? EchoRules.BULWARK_TIER2_RADIUS_BONUS : 0)
                + (AttunementService.hasTalent(player, Schools.EARTH, TalentRules.A1) ? EchoRules.RIFT_RADIUS_BONUS : 0);
        boolean strata = AttunementService.hasTalent(player, Schools.EARTH, TalentRules.A2);
        float damage = (float) EchoRules.bulwarkDamage(charge, AttunementService.isDissonant(player));
        ServerLevel level = player.level();
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(radius, 1, radius),
                e -> e.isAlive() && e != player && !(e instanceof net.minecraft.world.entity.player.Player) && e.distanceTo(player) <= radius)) {
            e.hurtServer(level, ModDamageTypes.source(e, Schools.EARTH, null, player), damage);
            var away = e.position().subtract(player.position()).multiply(1, 0, 1);
            if (away.lengthSqr() > 1.0E-4) e.push(away.normalize().x * 0.6, 0.35, away.normalize().z * 0.6);
            if (strata) e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, EchoRules.STRATA_STUN_TICKS, 9), player);
        }
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState()),
                player.getX(), player.getY() + 0.2, player.getZ(), 80, radius * 0.5, 0.2, radius * 0.5, 0.2);
    }

    // ---- Air: Impulse ----

    /** Moving while in combat charges Impulse (real players only: they tick). */
    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Vec3 now = player.position();
        Vec3 last = LAST_POS.put(player.getUUID(), now);
        if (last == null || !ManaService.inCombat(player) || tier(player, Schools.AIR) <= 0) return;
        double moved = Math.sqrt(Math.pow(now.x - last.x, 2) + Math.pow(now.z - last.z, 2));
        if (moved < 0.05 || moved > 4) return; // standing still, or a teleport
        double before = impulse(player);
        double charge = Math.min(EchoRules.IMPULSE_MAX,
                before + moved * EchoRules.IMPULSE_PER_BLOCK * gain(player));
        IMPULSE.put(player.getUUID(), charge);
        if ((int) charge != (int) before) Net.toPlayer(player, new EchoState(Schools.AIR, (float) Math.floor(charge), (int) EchoRules.IMPULSE_MAX));
    }

    public static double impulse(ServerPlayer player) {
        return IMPULSE.getOrDefault(player.getUUID(), 0.0);
    }

    /** For tests and admin tools. */
    public static void setImpulse(ServerPlayer player, double charge) {
        IMPULSE.put(player.getUUID(), charge);
    }

    /** A dash starts: spends Impulse and returns its distance multiplier (1 without Air Echo). */
    public static double consumeImpulse(ServerPlayer player) {
        int tier = tier(player, Schools.AIR);
        double charge = impulse(player);
        if (tier <= 0 || charge <= 0) return 1.0;
        IMPULSE.put(player.getUUID(), 0.0);
        Net.toPlayer(player, new EchoState(Schools.AIR, 0f, (int) EchoRules.IMPULSE_MAX));
        return EchoRules.impulseMult(charge, tier, AttunementService.isDissonant(player));
    }

    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        FLOW.remove(id);
        FLOW_CAST.remove(id);
        BULWARK.remove(id);
        ECLIPSE.remove(id);
        DUSK.values().removeIf(d -> d.caster().equals(id));
        RADIANCE.remove(id);
        IMPULSE.remove(id);
        LAST_POS.remove(id);
        HEAT.values().removeIf(h -> h.caster().equals(id));
        FROST.values().removeIf(h -> h.caster().equals(id));
        MARKS.values().removeIf(m -> m.caster().equals(id));
        SPROUTS.removeIf(s -> s.caster().equals(id));
    }

    @SubscribeEvent
    static void onServerStopping(ServerStoppingEvent event) {
        HEAT.clear();
        FROST.clear();
        MARKS.clear();
        SPROUTS.clear();
        FLOW.clear();
        FLOW_CAST.clear();
        BULWARK.clear();
        DUSK.clear();
        ECLIPSE.clear();
        RADIANCE.clear();
        IMPULSE.clear();
        LAST_POS.clear();
    }

    private EchoService() {}
}
