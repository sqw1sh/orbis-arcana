package com.orbisarcana.core;

/**
 * Spell charges (e.g. wind step milestone 15: two charges). The whole state is one number,
 * {@code fullAt}: the game tick when every charge is back. Each use pushes it by one cooldown, and
 * charges come back one per cooldown. With one charge this is exactly a plain cooldown, so the saved
 * cooldown map stays compatible.
 */
public final class Charges {
    /** Charges available at {@code now}. */
    public static int available(long fullAt, long now, int max, int cooldown) {
        if (cooldown <= 0 || fullAt <= now) return max;
        long missing = (fullAt - now + cooldown - 1) / cooldown;
        return (int) Math.max(0, max - missing);
    }

    /** First tick at which at least one charge is available. */
    public static long readyAt(long fullAt, int max, int cooldown) {
        return fullAt - (long) (max - 1) * Math.max(0, cooldown);
    }

    /** New {@code fullAt} after spending one charge at {@code now}. */
    public static long afterUse(long fullAt, long now, int cooldown) {
        return Math.max(fullAt, now) + Math.max(0, cooldown);
    }

    private Charges() {}
}
