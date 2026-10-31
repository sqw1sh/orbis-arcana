package com.orbisarcana.core;

/** Mana formulas (§8). */
public final class ManaMath {
    /** Mana lost when a cast is interrupted at {@code progress} (0..1): 10% at start up to 50% when almost done. */
    public static double interruptLoss(double cost, double progress) {
        return cost * (0.10 + 0.40 * MasteryMath.clamp(progress, 0.0, 1.0));
    }

    /** Loss when the interruption is not the player's fault (target vanished / out of range). */
    public static double blamelessLoss(double cost) {
        return cost * 0.10;
    }

    private ManaMath() {}
}
