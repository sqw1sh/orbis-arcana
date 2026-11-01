package com.orbisarcana.core;

/** Mastery XP for results, not key presses (§7.2), with anti-farm multipliers. */
public final class XpRules {
    public static final double ATTACK_FIRST_HIT = 1.0;
    public static final double ATTACK_EXTRA_TARGET = 0.25;
    public static final int MAX_EXTRA_TARGETS = 4;          // +1.0 at most
    public static final double KILL_BONUS = 0.5;
    public static final double CONTROL = 1.0;
    public static final double SUPPORT = 1.0;
    public static final double MOBILITY_COMBAT = 1.0;
    public static final double MOBILITY_CALM = 0.3;
    /** Out-of-combat mobility XP is capped per in-game day. */
    public static final double MOBILITY_CALM_DAILY_CAP = 30;
    public static final double MOBILITY_MIN_DISTANCE = 3.0;
    /** Healing/shield: 1.0 per 20% of the target's max health, at most 2.0 per cast. */
    public static final double HEAL_SHARE_PER_POINT = 0.2;
    public static final double HEAL_MAX_PER_CAST = 2.0;

    public static final double SPAWNER_MULT = 0.5;
    public static final double PVP_MULT = 0.25;
    /** Same target: after this many hits within {@link #SAME_TARGET_WINDOW_TICKS}, XP halves. */
    public static final int SAME_TARGET_HITS = 20;
    public static final long SAME_TARGET_WINDOW_TICKS = 20 * 60 * 5;
    public static final double SAME_TARGET_MULT = 0.5;
    /** XP one player can earn from hitting the same other player per real hour of game time. */
    public static final double PVP_HOURLY_CAP = 40;

    /** Weight of the {@code index}-th distinct target hit by one cast (0 = first). */
    public static double attackWeight(int index) {
        if (index == 0) return ATTACK_FIRST_HIT;
        return index <= MAX_EXTRA_TARGETS ? ATTACK_EXTRA_TARGET : 0;
    }

    /**
     * Weight for really restored health (overheal excluded by the caller), already capped by what this
     * cast has granted before ({@code grantedSoFar}).
     */
    public static double healWeight(double healed, double targetMaxHealth, double grantedSoFar) {
        if (healed <= 0 || targetMaxHealth <= 0) return 0;
        double w = healed / (HEAL_SHARE_PER_POINT * targetMaxHealth);
        return Math.max(0, Math.min(w, HEAL_MAX_PER_CAST - grantedSoFar));
    }

    public static double mobilityWeight(double distance, boolean inCombat) {
        if (distance < MOBILITY_MIN_DISTANCE) return 0;
        return inCombat ? MOBILITY_COMBAT : MOBILITY_CALM;
    }

    /** Multiplier from how often the same target was hit recently (§7.2 anti-farm). */
    public static double sameTargetMult(int recentHits) {
        return recentHits >= SAME_TARGET_HITS ? SAME_TARGET_MULT : 1.0;
    }

    private XpRules() {}
}
