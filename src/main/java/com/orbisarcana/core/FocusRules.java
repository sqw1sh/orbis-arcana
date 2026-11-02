package com.orbisarcana.core;

/**
 * Foci (§11.1): what a wand, staff or tome of a tier adds to the magic attributes, and which hand it works from.
 * Values are deltas on attributes whose base is 1 (power, cast speed, range, cost, mastery XP) or 0 (stability).
 */
public final class FocusRules {
    public enum Type { WAND, STAFF, TOME }

    public enum Hand { MAIN, OFF }

    public static final int TIERS = 3;

    /** Attribute deltas of a focus. */
    public record Stats(double power, double castSpeed, double range, double stability, double cost, double masteryXp) {
        public static final Stats NONE = new Stats(0, 0, 0, 0, 0, 0);
    }

    /** Wand +10/15/20% cast speed, -10% range; staff +15/20/25% power, +20/25/30% range, +0.2/0.25/0.3 stability; tome -10/15/20% cost, +10/15/20% mastery XP. */
    public static Stats stats(Type type, int tier) {
        int t = Math.max(1, Math.min(TIERS, tier)) - 1;
        return switch (type) {
            case WAND -> new Stats(0, 0.10 + 0.05 * t, -0.10, 0, 0, 0);
            case STAFF -> new Stats(0.15 + 0.05 * t, 0, 0.20 + 0.05 * t, 0.20 + 0.05 * t, 0, 0);
            case TOME -> new Stats(0, 0, 0, 0, -(0.10 + 0.05 * t), 0.10 + 0.05 * t);
        };
    }

    /**
     * Whether a focus works from {@code hand}. Wands work from either hand, tomes only from the off hand, the two-handed
     * staff only from the main hand with the off hand empty.
     */
    public static boolean works(Type type, Hand hand, boolean offHandEmpty) {
        return switch (type) {
            case WAND -> true;
            case TOME -> hand == Hand.OFF;
            case STAFF -> hand == Hand.MAIN && offHandEmpty;
        };
    }

    private FocusRules() {}
}
