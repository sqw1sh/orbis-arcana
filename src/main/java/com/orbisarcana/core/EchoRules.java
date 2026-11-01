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

    // Ice: Frost
    public static final int FROST_MAX = 5;
    public static final long FROST_DECAY_TICKS = 160;
    public static final int FROST_SLOW_TICKS = 80;
    public static final int FREEZE_TICKS_TIER1 = 30;
    public static final int FREEZE_TICKS_TIER2 = 40;

    public static int freezeTicks(int tier) {
        return tier >= 2 ? FREEZE_TICKS_TIER2 : FREEZE_TICKS_TIER1;
    }

    // Lightning: Conductivity
    public static final long MARK_TICKS = 80;
    /** Root "Conductor": marks hold 6 s instead of 4. */
    public static final long CONDUCTOR_MARK_TICKS = 120;
    public static final double CONDUCT_RANGE = 8;
    public static final int CONDUCT_JUMPS = 2;
    public static final double STORM_NET_RANGE = 6;
    public static final float STORM_NET_DAMAGE = 1f;
    /** Shown on the HUD as marked targets out of this many. */
    public static final int MARKS_SHOWN = 5;

    /** Damage of an arc jumping to a marked target: 3, or 4.5 at tier 2; Dissonance halves it. */
    public static double conductDamage(int tier, boolean dissonance) {
        double dmg = tier >= 2 ? 4.5 : 3.0;
        return dissonance ? dmg * SchoolRules.DISSONANCE_ECHO_MULT : dmg;
    }

    // Nature: Sprouting
    public static final int SPROUT_MAX = 3;
    public static final long SPROUT_MATURE_TICKS = 100;
    /** Root "Seed": sprouts mature 1 s sooner. */
    public static final long SEED_MATURE_TICKS = 80;
    public static final double SPROUT_RADIUS = 3;
    public static final int WILD_GROWTH_TICKS = 20;
    /** B1 "Healing Grove": sprouts heal +50%. */
    public static final double HEALING_GROVE_MULT = 1.5;

    /** What a mature sprout heals or stings for: 2, or 3 at tier 2; Dissonance halves it. */
    public static double sproutPower(int tier, boolean dissonance) {
        double p = tier >= 2 ? 3.0 : 2.0;
        return dissonance ? p * SchoolRules.DISSONANCE_ECHO_MULT : p;
    }

    // Light: Radiance
    /** Charge = HP healed, shielded or prevented. */
    public static final double RADIANCE_MAX = 20;
    public static final double RADIANCE_RADIUS = 5;
    public static final double RADIANCE_TIER2_RADIUS = 7;
    /** B1 "Veil": the pulse shields allies. */
    public static final float VEIL_SHIELD = 4f;
    public static final int VEIL_TICKS = 10 * 20;

    /** A2 "Retribution Ray": pulse damage, 4 or 6 at tier 2; Dissonance halves it. */
    public static double retributionDamage(int tier, boolean dissonance) {
        double dmg = tier >= 2 ? 6.0 : 4.0;
        return dissonance ? dmg * SchoolRules.DISSONANCE_ECHO_MULT : dmg;
    }

    // Air: Impulse
    public static final double IMPULSE_MAX = 10;
    /** Charge per block moved while in combat. */
    public static final double IMPULSE_PER_BLOCK = 0.5;
    /** Dash distance bonus at full charge: +40%, +60% at tier 2. */
    public static final double IMPULSE_BONUS_TIER1 = 0.4;
    public static final double IMPULSE_BONUS_TIER2 = 0.6;

    /** Dash distance multiplier for {@code charge}; Dissonance halves the bonus. */
    public static double impulseMult(double charge, int tier, boolean dissonance) {
        double bonus = (tier >= 2 ? IMPULSE_BONUS_TIER2 : IMPULSE_BONUS_TIER1) * Math.min(1, charge / IMPULSE_MAX);
        return 1 + (dissonance ? bonus * SchoolRules.DISSONANCE_ECHO_MULT : bonus);
    }

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
