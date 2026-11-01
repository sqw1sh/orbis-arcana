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
import com.orbisarcana.spell.Frost;

import com.orbisarcana.mana.ManaService;
import net.minecraft.core.particles.BlockParticleOption;
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
    /** player -> flow stacks. */
    private static final Map<UUID, Double> FLOW = new HashMap<>();
    /** player -> last cast id that already gave flow (one stack per cast, zones pulse many times). */
    private static final Map<UUID, Integer> FLOW_CAST = new HashMap<>();
    /** player -> Bulwark charge. */
    private static final Map<UUID, Double> BULWARK = new HashMap<>();
    /** player -> Impulse charge, and the position last tick. */
    private static final Map<UUID, Double> IMPULSE = new HashMap<>();
    private static final Map<UUID, Vec3> LAST_POS = new HashMap<>();

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
        if (!school.equals(Schools.FIRE) || !target.isAlive()) return;
        int tier = tier(caster, Schools.FIRE);
        if (tier <= 0) return;
        long now = caster.level().getGameTime();
        if (HEAT.size() > 256) HEAT.values().removeIf(h -> now - h.lastTick() > EchoRules.HEAT_DECAY_TICKS);
        Heat old = HEAT.get(target.getUUID());
        boolean live = old != null && old.caster().equals(caster.getUUID()) && now - old.lastTick() <= EchoRules.HEAT_DECAY_TICKS;
        if (live && old.lastCast() == castId) return;
        double stacks = live ? old.stacks() : 0;
        stacks += EchoRules.stackGain(AttunementService.isDissonant(caster));
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
        double stacks = (live ? old.stacks() : 0) + EchoRules.stackGain(AttunementService.isDissonant(caster));
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

    // ---- Water: Flow ----

    /** A healing/support cast succeeded (counted once per cast). */
    public static void onHealCast(ServerPlayer player, int castId) {
        int tier = tier(player, Schools.WATER);
        if (tier <= 0) return;
        Integer last = FLOW_CAST.put(player.getUUID(), castId);
        if (last != null && last == castId) return;
        int needed = EchoRules.flowNeeded(tier);
        double stacks = Math.min(needed, FLOW.getOrDefault(player.getUUID(), 0.0) + EchoRules.stackGain(AttunementService.isDissonant(player)));
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
                bulwark(player) + absorbed * EchoRules.stackGain(AttunementService.isDissonant(player)));
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
                before + moved * EchoRules.IMPULSE_PER_BLOCK * EchoRules.stackGain(AttunementService.isDissonant(player)));
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
        IMPULSE.remove(id);
        LAST_POS.remove(id);
        HEAT.values().removeIf(h -> h.caster().equals(id));
        FROST.values().removeIf(h -> h.caster().equals(id));
    }

    @SubscribeEvent
    static void onServerStopping(ServerStoppingEvent event) {
        HEAT.clear();
        FROST.clear();
        FLOW.clear();
        FLOW_CAST.clear();
        BULWARK.clear();
        IMPULSE.clear();
        LAST_POS.clear();
    }

    private EchoService() {}
}
