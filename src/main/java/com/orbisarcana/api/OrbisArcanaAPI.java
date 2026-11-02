package com.orbisarcana.api;

import java.util.Optional;

import org.jetbrains.annotations.ApiStatus;

import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * Entry point for addons (§16). All methods are server-side and work on a {@link ServerPlayer}. Events live in
 * {@code com.orbisarcana.api.event} and are posted on {@code NeoForge.EVENT_BUS}. Spell behaviors register in the
 * {@code orbisarcana:spell_behavior} registry, schools in {@link #SCHOOL_REGISTRY}.
 */
@ApiStatus.Experimental
public interface OrbisArcanaAPI {
    ResourceKey<Registry<School>> SCHOOL_REGISTRY = ResourceKey.createRegistryKey(Identifier.fromNamespaceAndPath("orbisarcana", "school"));

    /** The live implementation, available once the mod is constructed. */
    static OrbisArcanaAPI get() {
        return Holder.instance;
    }

    // ---- knowledge and mastery ----

    boolean knows(ServerPlayer player, Identifier spell);

    /** Mastery level 1-15 of a known spell. */
    Optional<Integer> mastery(ServerPlayer player, Identifier spell);

    /** Teaches the spell (no requirements checked); false if it was known already. */
    boolean learn(ServerPlayer player, Identifier spell);

    // ---- mana ----

    double mana(ServerPlayer player);

    double maxMana(ServerPlayer player);

    void setMana(ServerPlayer player, double mana);

    // ---- schools ----

    Optional<Identifier> dominantSchool(ServerPlayer player);

    int resonance(ServerPlayer player, Identifier school);

    /** True while {@code school} is dominant and the player picked talent {@code node} ("root", "a1", "a2", "b1", "b2"). */
    boolean hasTalent(ServerPlayer player, Identifier school, String node);

    // ---- casting ----

    /** Starts a cast as if the player pressed the key: every usual rule applies. */
    void cast(ServerPlayer player, Identifier spell);

    /**
     * An addon behavior reports that its spell damaged {@code target}: grants mastery XP (§7.2) and feeds the school's
     * Echo, as the built-in behaviors do. Call it after the damage, once per target per cast.
     */
    void reportHit(ServerPlayer caster, Identifier spell, int castId, LivingEntity target);

    /** An addon behavior reports {@code healed} HP of real healing on {@code target}: mastery XP and support Echoes. */
    void reportHeal(ServerPlayer caster, Identifier spell, int castId, LivingEntity target, float healed);

    /** Set by the mod on construction. */
    @ApiStatus.Internal
    final class Holder {
        static OrbisArcanaAPI instance;

        public static void set(OrbisArcanaAPI api) {
            instance = api;
        }

        private Holder() {}
    }
}
