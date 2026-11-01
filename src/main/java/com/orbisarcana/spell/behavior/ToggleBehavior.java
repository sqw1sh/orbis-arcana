package com.orbisarcana.spell.behavior;

/**
 * Behavior of a TOGGLE spell (§5.3): on until toggled off or out of mana. {@code ticks} counts from enabling.
 * {@link #release} is the enable step.
 */
public interface ToggleBehavior extends SpellBehavior {
    default void tick(CastContext ctx, int ticks) {}

    /** {@code manual}: the player toggled it off (not out of mana, death or logout). */
    default void disable(CastContext ctx, int ticks, boolean manual) {}
}
