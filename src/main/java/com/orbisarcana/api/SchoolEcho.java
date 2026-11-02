package com.orbisarcana.api;

import org.jetbrains.annotations.ApiStatus;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * The Echo of an addon school (§10.5): called only while that school is the caster's dominant one and its resonance is
 * at least 1. {@code tier} is 1, or 2 from resonance 6. Built-in Echoes are implemented by the mod itself.
 */
@ApiStatus.Experimental
public interface SchoolEcho {
    /** A spell of the school from cast {@code castId} damaged {@code target}. */
    default void onSpellHit(ServerPlayer caster, LivingEntity target, int castId, int tier) {}

    /** The caster healed, shielded or spared someone {@code amount} HP with any spell. */
    default void onSupport(ServerPlayer caster, float amount, int tier) {}
}
