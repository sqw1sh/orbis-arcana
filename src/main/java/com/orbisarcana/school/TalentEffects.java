package com.orbisarcana.school;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.orbisarcana.OrbisArcana;
import com.orbisarcana.core.TalentRules;
import com.orbisarcana.spell.Frost;
import com.orbisarcana.spell.Shields;
import net.minecraft.world.effect.MobEffects;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.projectile.Projectile;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Gameplay effects of talents and root passives (§10.5). Effects come school by school; nodes listed in
 * {@link #IMPLEMENTED} work, others are shown as "in development" in the grimoire.
 */
@EventBusSubscriber(modid = OrbisArcana.MODID)
public final class TalentEffects {
    /** "school/node" keys whose effect is live (Water A1/A2 work through the Tidal Stream cone). */
    public static final Set<String> IMPLEMENTED = Set.of(
            "fire/echo", "fire/root", "fire/a1", "fire/a2", "fire/b1", "fire/b2",
            "water/echo", "water/root", "water/a1", "water/a2", "water/b1", "water/b2",
            "earth/echo", "earth/root", "earth/a1", "earth/a2", "earth/b1", "earth/b2",
            "air/echo", "air/root", "air/a1", "air/a2", "air/b1", "air/b2",
            "ice/echo", "ice/root", "ice/a1", "ice/a2", "ice/b1", "ice/b2",
            "lightning/echo", "lightning/root", "lightning/a1", "lightning/a2", "lightning/b1", "lightning/b2",
            "nature/echo", "nature/root", "nature/a1", "nature/a2", "nature/b1", "nature/b2",
            "light/echo", "light/root", "light/a1", "light/a2", "light/b1", "light/b2",
            "dark/echo", "dark/root", "dark/a1", "dark/a2", "dark/b1", "dark/b2",
            "astral/echo", "astral/root", "astral/a1", "astral/a2", "astral/b1", "astral/b2");

    public static final double EMBERS_IGNITE_MULT = 1.3;
    public static final double LIVING_WAVE_HEAL_MULT = 1.1;
    public static final int CROWN_EVERY = 3;
    public static final float ASH_SHIELD = 4f;
    public static final long ASH_SHIELD_COOLDOWN = 20 * 20;
    public static final int ASH_SHIELD_TICKS = 10 * 20;
    public static final double WELLSPRING_SHARE = 0.3;
    public static final double WELLSPRING_RADIUS = 6.0;
    public static final long TIDAL_MERCY_COOLDOWN = 10 * 20;

    private static final Map<UUID, Integer> FIRE_CASTS = new HashMap<>();
    private static final Map<UUID, Long> ASH_READY = new HashMap<>();
    private static final Map<UUID, Long> MERCY_READY = new HashMap<>();

    public static boolean implemented(String school, String node) {
        return IMPLEMENTED.contains(school + "/" + node);
    }

    // ---- Fire ----

    /** Root "Embers": burning from your spells lasts +30%. */
    public static double igniteMult(ServerPlayer caster) {
        return AttunementService.hasTalent(caster, Schools.FIRE, TalentRules.ROOT) ? EMBERS_IGNITE_MULT : 1.0;
    }

    /** A2 "Crown of Flame": true for every 3rd released fire spell, whose mana is then refunded. */
    public static boolean crownOfFlameFree(ServerPlayer caster, Identifier spellSchool) {
        if (!spellSchool.equals(Schools.FIRE) || !AttunementService.hasTalent(caster, Schools.FIRE, TalentRules.A2)) return false;
        int n = FIRE_CASTS.merge(caster.getUUID(), 1, Integer::sum);
        return n % CROWN_EVERY == 0;
    }

    /** B1 "Tempering": immune to being set on fire. */
    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer p && p.isOnFire() && AttunementService.hasTalent(p, Schools.FIRE, TalentRules.B1)) {
            p.clearFire();
        }
    }

    /** B2 "Ash Shield": taking damage while burning grants a 4 HP shield, 20 s cooldown. */
    @SubscribeEvent
    static void onDamage(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || event.getHealthDamage() <= 0 || !p.isOnFire()) return;
        if (!AttunementService.hasTalent(p, Schools.FIRE, TalentRules.B2)) return;
        long now = p.level().getGameTime();
        if (now < ASH_READY.getOrDefault(p.getUUID(), 0L)) return;
        ASH_READY.put(p.getUUID(), now + ASH_SHIELD_COOLDOWN);
        Shields.grant(p, ASH_SHIELD, ASH_SHIELD_TICKS, null);
    }

    // ---- Earth ----

    public static final double STONE_CORE_STABILITY = 0.2;
    public static final long UNBREAKABLE_COOLDOWN = 5 * 60 * 20;
    private static final Map<UUID, Long> UNBREAKABLE_READY = new HashMap<>();

    public static double stoneCoreStability(ServerPlayer player) {
        return AttunementService.hasTalent(player, Schools.EARTH, TalentRules.ROOT) ? STONE_CORE_STABILITY : 0;
    }

    /** B1 "Fortress": -10% damage taken while Bulwark is at 50% or more. */
    @SubscribeEvent
    static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && AttunementService.hasTalent(p, Schools.EARTH, TalentRules.B1)
                && EchoService.bulwark(p) >= com.orbisarcana.core.EchoRules.BULWARK_MAX / 2) {
            event.setAmount((float) (event.getAmount() * com.orbisarcana.core.EchoRules.FORTRESS_MULT));
        }
    }

    /** B2 "Unbreakable Pattern": a lethal hit leaves 1 HP, once per 5 minutes. */
    @SubscribeEvent
    static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !AttunementService.hasTalent(p, Schools.EARTH, TalentRules.B2)) return;
        long now = p.level().getGameTime();
        if (now < UNBREAKABLE_READY.getOrDefault(p.getUUID(), 0L)) return;
        UNBREAKABLE_READY.put(p.getUUID(), now + UNBREAKABLE_COOLDOWN);
        event.setCanceled(true);
        p.setHealth(1f);
    }

    // ---- Lightning ----

    public static final float REVERSE_CURRENT_DAMAGE = 3f;
    public static final long REVERSE_CURRENT_COOLDOWN = 4 * 20;
    private static final Map<UUID, Long> REVERSE_READY = new HashMap<>();

    /** B1 "Lightning Rod": your lightning breaks mobs' casting (drawing a bow, drinking, illager spells). */
    public static void lightningRod(ServerPlayer caster, LivingEntity target) {
        if (!(target instanceof net.minecraft.world.entity.Mob mob) || !AttunementService.hasTalent(caster, Schools.LIGHTNING, TalentRules.B1)) return;
        mob.stopUsingItem();
        if (mob instanceof net.minecraft.world.entity.monster.illager.SpellcasterIllager illager && illager.isCastingSpell()) {
            illager.setTarget(null); // casting goals need a target; the spell enum itself is protected
        }
    }

    /** B2 "Reverse Current": when hit, spark the attacker (4 s cooldown). */
    @SubscribeEvent
    static void onDamageLightning(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !(event.getSource().getEntity() instanceof LivingEntity attacker)
                || attacker == p || !attacker.isAlive() || !AttunementService.hasTalent(p, Schools.LIGHTNING, TalentRules.B2)) return;
        long now = p.level().getGameTime();
        if (now < REVERSE_READY.getOrDefault(p.getUUID(), 0L)) return;
        REVERSE_READY.put(p.getUUID(), now + REVERSE_CURRENT_COOLDOWN);
        attacker.hurtServer(p.level(), com.orbisarcana.registry.ModDamageTypes.source(attacker, Schools.LIGHTNING, null, p), REVERSE_CURRENT_DAMAGE);
        com.orbisarcana.spell.Arcs.draw(p.level(), com.orbisarcana.spell.Arcs.body(p), com.orbisarcana.spell.Arcs.body(attacker));
    }

    // ---- Ice ----

    public static final double CRYSTALLIZATION_MULT = 1.25;
    public static final double ICE_ROSE_RADIUS = 2.5;
    public static final float ICE_ROSE_DAMAGE = 3f;
    public static final int QUIET_WINTER_TICKS = 60;
    public static final double WHITE_WALL_HEALTH_SHARE = 0.3;
    public static final float WHITE_WALL_SHIELD = 6f;
    public static final int WHITE_WALL_TICKS = 10 * 20;
    public static final long WHITE_WALL_COOLDOWN = 90 * 20;
    private static final Map<UUID, Long> WHITE_WALL_READY = new HashMap<>();

    /** Root "Cold Blood": casting Ice spells does not slow you. */
    public static boolean coldBlood(ServerPlayer caster, Identifier spellSchool) {
        return spellSchool.equals(Schools.ICE) && AttunementService.hasTalent(caster, Schools.ICE, TalentRules.ROOT);
    }

    /** A1 "Crystallization": +25% damage to frozen targets. */
    @SubscribeEvent
    static void onIncomingIce(LivingIncomingDamageEvent event) {
        if (event.getSource().getEntity() instanceof ServerPlayer p && Frost.isFrozen(event.getEntity())
                && AttunementService.hasTalent(p, Schools.ICE, TalentRules.A1)) {
            event.setAmount((float) (event.getAmount() * CRYSTALLIZATION_MULT));
        }
    }

    /**
     * A2 "Ice Rose": your hit shatters a freeze, shards hurt those around. B1 "Quiet Winter": melee attackers are
     * slowed. B2 "White Wall": dropping below 30% HP grants an ice shell (90 s cooldown).
     */
    @SubscribeEvent
    static void onDamageIce(LivingDamageEvent.Post event) {
        LivingEntity victim = event.getEntity();
        var source = event.getSource();
        if (source.getEntity() instanceof ServerPlayer p && p != victim && Frost.isFrozen(victim)
                && AttunementService.hasTalent(p, Schools.ICE, TalentRules.A2)) {
            Frost.unfreeze(victim);
            var level = p.level();
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, victim.getBoundingBox().inflate(ICE_ROSE_RADIUS),
                    e -> e.isAlive() && e != p && e != victim && !(e instanceof Player))) {
                e.hurtServer(level, com.orbisarcana.registry.ModDamageTypes.source(e, Schools.ICE, null, p), ICE_ROSE_DAMAGE);
            }
            level.sendParticles(Frost.ICE, victim.getX(), victim.getY() + victim.getBbHeight() / 2, victim.getZ(), 50, 0.8, 0.5, 0.8, 0.25);
        }
        if (!(victim instanceof ServerPlayer p)) return;
        if (source.getDirectEntity() instanceof LivingEntity attacker && source.getEntity() == attacker && attacker != p
                && AttunementService.hasTalent(p, Schools.ICE, TalentRules.B1)) {
            attacker.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, QUIET_WINTER_TICKS, 1), p);
        }
        if (event.getHealthDamage() > 0 && p.isAlive() && p.getHealth() < p.getMaxHealth() * WHITE_WALL_HEALTH_SHARE
                && AttunementService.hasTalent(p, Schools.ICE, TalentRules.B2)) {
            long now = p.level().getGameTime();
            if (now < WHITE_WALL_READY.getOrDefault(p.getUUID(), 0L)) return;
            WHITE_WALL_READY.put(p.getUUID(), now + WHITE_WALL_COOLDOWN);
            Shields.grant(p, WHITE_WALL_SHIELD, WHITE_WALL_TICKS, null);
            p.level().sendParticles(Frost.ICE, p.getX(), p.getY() + 1, p.getZ(), 40, 0.5, 0.8, 0.5, 0.1);
        }
    }

    // ---- Air ----

    public static final double LIGHT_STEP_MAX_FALL = 10.0;
    public static final double VORTEX_EDGE_SPEED_MULT = 1.3;
    public static final double EVASIVE_CHANCE = 0.1;

    /** A1 "Vortex Edge": your spell projectiles fly 30% faster. */
    public static double projectileSpeedMult(ServerPlayer caster) {
        return AttunementService.hasTalent(caster, Schools.AIR, TalentRules.A1) ? VORTEX_EDGE_SPEED_MULT : 1.0;
    }

    /** Root "Light Step": no fall damage from up to 10 blocks. */
    @SubscribeEvent
    static void onFall(LivingFallEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && event.getDistance() <= LIGHT_STEP_MAX_FALL
                && AttunementService.hasTalent(p, Schools.AIR, TalentRules.ROOT)) {
            event.setCanceled(true);
        }
    }

    /** B2 "Evasive Pattern": 10% chance to dodge a projectile. */
    @SubscribeEvent
    static void onProjectileHit(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && event.getSource().getDirectEntity() instanceof Projectile
                && AttunementService.hasTalent(p, Schools.AIR, TalentRules.B2) && p.getRandom().nextDouble() < EVASIVE_CHANCE) {
            event.setCanceled(true);
            p.level().sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 1, p.getZ(), 10, 0.3, 0.5, 0.3, 0.05);
        }
    }

    // ---- Light ----

    public static final double SPARK_SHARE = 0.25;
    public static final float SPARK_MAX_SHIELD = 4f;
    public static final int SPARK_TICKS = 10 * 20;
    public static final double EXPOSURE_MULT = 1.2;
    public static final double SOLAR_VOW_MULT = 0.95;
    public static final double SOLAR_VOW_RADIUS = 6;

    public static boolean isUndead(LivingEntity e) {
        return e.getType().builtInRegistryHolder().is(net.minecraft.tags.EntityTypeTags.UNDEAD);
    }

    /** Root "Spark": 25% of overheal becomes a shield (up to 4 HP of shields on the target). */
    public static void spark(ServerPlayer caster, LivingEntity target, float overheal) {
        if (overheal <= 0 || !AttunementService.hasTalent(caster, Schools.LIGHT, TalentRules.ROOT)) return;
        float room = SPARK_MAX_SHIELD - Shields.total(target);
        float amount = Math.min(room, (float) (overheal * SPARK_SHARE));
        if (amount > 0.05f) Shields.grant(target, amount, SPARK_TICKS, null);
    }

    /**
     * A1 "Exposure": +20% damage to undead and marked (glowing) targets. B2 "Solar Vow": players within 6 blocks of a
     * vow keeper (the keeper too) take 5% less damage.
     */
    @SubscribeEvent
    static void onIncomingLight(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        if (event.getSource().getEntity() instanceof ServerPlayer p && p != victim
                && (isUndead(victim) || victim.hasEffect(MobEffects.GLOWING)) && AttunementService.hasTalent(p, Schools.LIGHT, TalentRules.A1)) {
            event.setAmount((float) (event.getAmount() * EXPOSURE_MULT));
        }
        if (victim instanceof ServerPlayer target && !target.level().getEntitiesOfClass(ServerPlayer.class,
                target.getBoundingBox().inflate(SOLAR_VOW_RADIUS),
                k -> k.isAlive() && k.distanceTo(target) <= SOLAR_VOW_RADIUS && AttunementService.hasTalent(k, Schools.LIGHT, TalentRules.B2)).isEmpty()) {
            event.setAmount((float) (event.getAmount() * SOLAR_VOW_MULT));
        }
    }

    // ---- Dark ----

    public static final double ABSORPTION_SHARE = 0.05;
    public static final int NIGHT_VEIL_TICKS = 40;
    public static final long NIGHT_VEIL_COOLDOWN = 30 * 20;
    public static final double BLIND_SPOT_RADIUS = 24;
    private static final Map<UUID, Long> VEIL_READY = new HashMap<>();

    /** A1 "Absorption": your Dark magic heals you for 5% of the damage dealt. */
    @SubscribeEvent
    static void onDamageDark(LivingDamageEvent.Post event) {
        if (event.getSource().getEntity() instanceof ServerPlayer p && p != event.getEntity() && event.getHealthDamage() > 0
                && event.getSource().is(com.orbisarcana.registry.ModDamageTypes.forSchool(Schools.DARK))
                && AttunementService.hasTalent(p, Schools.DARK, TalentRules.A1)) {
            p.heal((float) (event.getHealthDamage() * ABSORPTION_SHARE));
        }
    }

    /** B1 "Night Veil": a kill hides you for 2 s (30 s cooldown). */
    @SubscribeEvent
    static void onKillDark(LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer p) || p == event.getEntity()
                || !AttunementService.hasTalent(p, Schools.DARK, TalentRules.B1)) return;
        long now = p.level().getGameTime();
        if (now < VEIL_READY.getOrDefault(p.getUUID(), 0L)) return;
        VEIL_READY.put(p.getUUID(), now + NIGHT_VEIL_COOLDOWN);
        p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, NIGHT_VEIL_TICKS, 0, false, false));
    }

    /** B2 "Blind Spot": when you teleport, mobs hunting you lose you. */
    public static void blindSpot(ServerPlayer p) {
        if (!AttunementService.hasTalent(p, Schools.DARK, TalentRules.B2)) return;
        for (var mob : p.level().getEntitiesOfClass(net.minecraft.world.entity.Mob.class, p.getBoundingBox().inflate(BLIND_SPOT_RADIUS),
                m -> m.getTarget() == p)) {
            mob.setTarget(null);
        }
    }

    @SubscribeEvent
    static void onPearl(net.neoforged.neoforge.event.entity.EntityTeleportEvent.EnderPearl event) {
        blindSpot(event.getPlayer());
    }

    // ---- Astral ----

    /** B1 "Warp": -20% projectile damage while one of your stars is near (orbiting stars count). */
    @SubscribeEvent
    static void onProjectileAstral(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && event.getSource().getDirectEntity() instanceof Projectile
                && AttunementService.hasTalent(p, Schools.ASTRAL, TalentRules.B1)
                && (com.orbisarcana.spell.Stars.near(p, com.orbisarcana.core.EchoRules.WARP_RADIUS)
                        || com.orbisarcana.spell.behavior.OrbitBehavior.stars(p) > 0)) {
            event.setAmount((float) (event.getAmount() * com.orbisarcana.core.EchoRules.WARP_MULT));
        }
    }

    // ---- Water ----

    /** Water root "Living Wave": +10% healing; Nature B2 "Great Crown": +15% near leaves or logs. */
    public static double healMult(ServerPlayer caster) {
        double mult = AttunementService.hasTalent(caster, Schools.WATER, TalentRules.ROOT) ? LIVING_WAVE_HEAL_MULT : 1.0;
        if (AttunementService.hasTalent(caster, Schools.NATURE, TalentRules.B2) && nearTrees(caster)) mult *= GREAT_CROWN_HEAL_MULT;
        return mult;
    }

    public static final double GREAT_CROWN_HEAL_MULT = 1.15;
    private static final int GREAT_CROWN_REACH = 3;

    private static boolean nearTrees(ServerPlayer p) {
        var origin = p.blockPosition();
        for (var pos : net.minecraft.core.BlockPos.betweenClosed(origin.offset(-GREAT_CROWN_REACH, -1, -GREAT_CROWN_REACH),
                origin.offset(GREAT_CROWN_REACH, GREAT_CROWN_REACH + 2, GREAT_CROWN_REACH))) {
            var state = p.level().getBlockState(pos);
            if (state.is(net.minecraft.tags.BlockTags.LEAVES) || state.is(net.minecraft.tags.BlockTags.LOGS)) return true;
        }
        return false;
    }

    /**
     * After the caster healed {@code target} for {@code amount}: B1 "Wellspring" heals a second ally for 30%,
     * B2 "Tidal Mercy" removes one harmful effect from the target (10 s cooldown).
     */
    public static void afterHeal(ServerPlayer caster, LivingEntity target, float amount) {
        if (AttunementService.hasTalent(caster, Schools.WATER, TalentRules.B1)) {
            caster.level().getEntitiesOfClass(Player.class, target.getBoundingBox().inflate(WELLSPRING_RADIUS),
                            p -> p != target && p.isAlive() && p.getHealth() < p.getMaxHealth())
                    .stream()
                    .min(Comparator.comparingDouble(p -> p.getHealth() / p.getMaxHealth()))
                    .ifPresent(p -> p.heal((float) (amount * WELLSPRING_SHARE)));
        }
        if (AttunementService.hasTalent(caster, Schools.WATER, TalentRules.B2)) {
            long now = caster.level().getGameTime();
            if (now < MERCY_READY.getOrDefault(caster.getUUID(), 0L)) return;
            target.getActiveEffects().stream()
                    .filter(e -> e.getEffect().value().getCategory() == MobEffectCategory.HARMFUL)
                    .map(MobEffectInstance::getEffect)
                    .findFirst()
                    .ifPresent(effect -> {
                        target.removeEffect(effect);
                        MERCY_READY.put(caster.getUUID(), now + TIDAL_MERCY_COOLDOWN);
                    });
        }
    }

    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        FIRE_CASTS.remove(id);
        ASH_READY.remove(id);
        MERCY_READY.remove(id);
        UNBREAKABLE_READY.remove(id);
        WHITE_WALL_READY.remove(id);
        REVERSE_READY.remove(id);
        VEIL_READY.remove(id);
    }

    @SubscribeEvent
    static void onServerStopping(ServerStoppingEvent event) {
        FIRE_CASTS.clear();
        ASH_READY.clear();
        MERCY_READY.clear();
        UNBREAKABLE_READY.clear();
        WHITE_WALL_READY.clear();
        REVERSE_READY.clear();
        VEIL_READY.clear();
    }

    private TalentEffects() {}
}
