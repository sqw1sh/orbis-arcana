package com.orbisarcana.core;

/** Mastery formulas (§7). Pure Java, no Minecraft dependencies. */
public final class MasteryMath {
    public static final int MAX_LEVEL = 15;
    public static final double STAT_PER_LEVEL = 0.03;
    public static final double MAX_COST_REDUCTION = 0.12;
    public static final double MAX_CAST_TIME_REDUCTION = 0.10;

    /** XP needed to go from {@code level} to {@code level + 1}; 0 at max level. */
    public static int xpRequired(int level, Rarity rarity) {
        if (level >= MAX_LEVEL) return 0;
        return (int) Math.round(10 * Math.pow(1.25, level - 1) * rarity.masteryXpMult);
    }

    public static double cycleWeight(double castSec, double cooldownSec) {
        return clamp((castSec + cooldownSec) / 3.0, 1.0, 12.0);
    }

    public static double xpGain(double categoryWeight, double castSec, double cooldownSec, double serverRate) {
        return categoryWeight * cycleWeight(castSec, cooldownSec) * serverRate;
    }

    public static boolean isMilestone(int level) {
        return level == 5 || level == 10 || level == 15;
    }

    /** Number of regular (non-milestone) levels gained above level 1. */
    public static int regularLevels(int level) {
        int n = 0;
        for (int l = 2; l <= Math.min(level, MAX_LEVEL); l++) {
            if (!isMilestone(l)) n++;
        }
        return n;
    }

    /** Multiplier for the spell's primary stat: +3% per regular level. */
    public static double primaryStatMult(int level) {
        return 1.0 + STAT_PER_LEVEL * regularLevels(level);
    }

    /** Linear cost multiplier, down to -12% at level 15. */
    public static double costMult(int level) {
        return 1.0 - MAX_COST_REDUCTION * progress(level);
    }

    /** Linear cast-time multiplier, down to -10% at level 15. */
    public static double castTimeMult(int level) {
        return 1.0 - MAX_CAST_TIME_REDUCTION * progress(level);
    }

    private static double progress(int level) {
        return clamp((level - 1) / (double) (MAX_LEVEL - 1), 0.0, 1.0);
    }

    static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    private MasteryMath() {}
}
