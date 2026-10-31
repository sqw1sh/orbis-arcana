package com.orbisarcana.spell.behavior;

/**
 * Server logic of a spell type (§15.5). Registered in {@code orbisarcana:spell_behavior}; spells pick
 * one by id in their JSON. Targeting (ground point / target entity) is resolved by the cast service
 * before {@link #release} is called.
 */
@FunctionalInterface
public interface SpellBehavior {
    /** Applies the spell effect. Called once when the cast is released. */
    void release(CastContext ctx);
}
