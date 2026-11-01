package com.orbisarcana.core;

/** Dominant school, resonance and school switching (§10.2, §10.3, §10.6). */
public final class SchoolRules {
    public static final int MAX_RESONANCE = 10;

    /** Resonance XP = mastery XP of the dominant school's spells x0.5, other schools x0.1 (§10.3). */
    public static final double SAME_SCHOOL_SHARE = 0.5;
    public static final double OTHER_SCHOOL_SHARE = 0.1;

    public static final double DOMINANT_POWER = 1.10;
    public static final double DOMINANT_COST = 0.95;
    /** Resonance 10: the seal is complete, +5% more power for the school. */
    public static final double FULL_SEAL_POWER = 0.05;

    /** Dissonance after a switch (§10.6): 20 minutes. */
    public static final long DISSONANCE_TICKS = 20L * 60 * 20;
    public static final double DISSONANCE_NEW_POWER = 0.85;
    public static final double DISSONANCE_OLD_POWER = 0.90;
    public static final double DISSONANCE_RESONANCE_MULT = 0.5;
    public static final double DISSONANCE_ECHO_MULT = 0.5;
    /** Pause between switches: one in-game day. */
    public static final long SWITCH_COOLDOWN_TICKS = 24000;
    public static final int RITUAL_TICKS = 200;
    public static final int SWITCH_XP_LEVELS = 5;
    public static final int INK_ELEMENTAL = 4;
    public static final int INK_HIGHER = 8;
    /** Returning to a school with resonance >= 5 costs half ("the old path is easier"). */
    public static final int DISCOUNT_RESONANCE = 5;

    /** Resonance XP to go from {@code level} to {@code level + 1} (D8: 40 x 1.35^level, prototype). */
    public static int resonanceXpRequired(int level) {
        if (level >= MAX_RESONANCE) return 0;
        return (int) Math.round(40 * Math.pow(1.35, level));
    }

    /** Talent points earned at resonance 3, 6 and 9 (§10.3). */
    public static int talentPoints(int resonance) {
        int points = 0;
        for (int at : new int[] {3, 6, 9}) {
            if (resonance >= at) points++;
        }
        return points;
    }

    /** Echo tier: 0 none, 1 from resonance 1, 2 from resonance 5. */
    public static int echoTier(int resonance) {
        return resonance >= 5 ? 2 : resonance >= 1 ? 1 : 0;
    }

    /**
     * Power multiplier for a spell of some school, given the player's attunement.
     *
     * @param isDominant    the spell's school is the dominant one
     * @param isPrevious    the spell's school is the one left by the last switch
     * @param dissonance    Dissonance is active
     */
    public static double powerMult(boolean isDominant, boolean isPrevious, int resonance, boolean dissonance) {
        if (isDominant) {
            double m = DOMINANT_POWER + (resonance >= MAX_RESONANCE ? FULL_SEAL_POWER : 0);
            return dissonance ? m * DISSONANCE_NEW_POWER : m;
        }
        return isPrevious && dissonance ? DISSONANCE_OLD_POWER : 1.0;
    }

    public static double costMult(boolean isDominant) {
        return isDominant ? DOMINANT_COST : 1.0;
    }

    /** Share of mastery XP that becomes resonance XP of the dominant school. */
    public static double resonanceShare(boolean sameSchool, boolean dissonance) {
        double share = sameSchool ? SAME_SCHOOL_SHARE : OTHER_SCHOOL_SHARE;
        return dissonance ? share * DISSONANCE_RESONANCE_MULT : share;
    }

    /** Cost of switching to a school (§10.6); see {@link #isFreeBinding} for the free first binding. */
    public record SwitchCost(int ink, int xpLevels) {
        public static final SwitchCost FREE = new SwitchCost(0, 0);
    }

    public static SwitchCost switchCost(boolean higherSchool, int targetResonance) {
        int ink = higherSchool ? INK_HIGHER : INK_ELEMENTAL;
        int levels = SWITCH_XP_LEVELS;
        if (targetResonance >= DISCOUNT_RESONANCE) {
            ink = (ink + 1) / 2;
            levels = (levels + 1) / 2;
        }
        return new SwitchCost(ink, levels);
    }

    /** The very first binding of a character is free and starts no Dissonance (D9). */
    public static boolean isFreeBinding(boolean everBound) {
        return !everBound;
    }

    public static boolean canSwitchAt(long now, long nextSwitchAt) {
        return now >= nextSwitchAt;
    }

    private SchoolRules() {}
}
