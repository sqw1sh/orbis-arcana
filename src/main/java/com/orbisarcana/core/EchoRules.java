package com.orbisarcana.core;

/** Numbers of school Echoes (§10.5), prototype values. Dissonance halves Echo efficiency (§10.6). */
public final class EchoRules {
    // Fire: Heat
    public static final int HEAT_MAX = 5;
    public static final double HEAT_RADIUS = 2.0;
    public static final long HEAT_DECAY_TICKS = 160;
    public static final int FANNING_IGNITE_TICKS = 60;

    // Water: Flow
    public static final int FLOW_NEEDED_TIER1 = 3;
    public static final int FLOW_NEEDED_TIER2 = 2;

    // Earth: Bulwark
    public static final double BULWARK_MAX = 20;
    /** Charge needed for the next attack spell to release a wave. */
    public static final double BULWARK_MIN_RELEASE = 5;
    public static final double BULWARK_DAMAGE_PER_CHARGE = 0.5;
    public static final double BULWARK_RADIUS = 3.0;
    public static final double BULWARK_TIER2_RADIUS_BONUS = 1.0;
    public static final double RIFT_RADIUS_BONUS = 1.5;
    public static final int STRATA_STUN_TICKS = 10;
    /** Fortress: -10% damage while Bulwark is at 50% or more. */
    public static final double FORTRESS_MULT = 0.9;

    /** Bulwark wave damage for {@code charge}; Dissonance halves it. */
    public static double bulwarkDamage(double charge, boolean dissonance) {
        double dmg = charge * BULWARK_DAMAGE_PER_CHARGE;
        return dissonance ? dmg * SchoolRules.DISSONANCE_ECHO_MULT : dmg;
    }

    /** Heat burst damage: 4 at tier 1, 6 at tier 2, halved during Dissonance. */
    public static double heatBurstDamage(int tier, boolean dissonance) {
        double dmg = tier >= 2 ? 6.0 : 4.0;
        return dissonance ? dmg * SchoolRules.DISSONANCE_ECHO_MULT : dmg;
    }

    /** Stacks gained per effective cast: 1, or 0.5 during Dissonance. */
    public static double stackGain(boolean dissonance) {
        return dissonance ? SchoolRules.DISSONANCE_ECHO_MULT : 1.0;
    }

    public static int flowNeeded(int tier) {
        return tier >= 2 ? FLOW_NEEDED_TIER2 : FLOW_NEEDED_TIER1;
    }

    private EchoRules() {}
}
