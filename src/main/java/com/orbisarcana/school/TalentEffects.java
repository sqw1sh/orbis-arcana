package com.orbisarcana.school;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.orbisarcana.OrbisArcana;
import com.orbisarcana.core.TalentRules;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
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
            "water/echo", "water/root", "water/a1", "water/a2", "water/b1", "water/b2");

    public static final double EMBERS_IGNITE_MULT = 1.3;
    public static final double LIVING_WAVE_HEAL_MULT = 1.1;
    public static final int CROWN_EVERY = 3;
    public static final float ASH_SHIELD = 4f;
    public static final long ASH_SHIELD_COOLDOWN = 20 * 20;
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
        p.setAbsorptionAmount(Math.max(p.getAbsorptionAmount(), ASH_SHIELD));
    }

    // ---- Water ----

    /** Root "Living Wave": +10% healing. */
    public static double healMult(ServerPlayer caster) {
        return AttunementService.hasTalent(caster, Schools.WATER, TalentRules.ROOT) ? LIVING_WAVE_HEAL_MULT : 1.0;
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
    }

    @SubscribeEvent
    static void onServerStopping(ServerStoppingEvent event) {
        FIRE_CASTS.clear();
        ASH_READY.clear();
        MERCY_READY.clear();
    }

    private TalentEffects() {}
}
