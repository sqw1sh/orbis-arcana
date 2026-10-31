package com.orbisarcana.core;

/** Mana regeneration rules (§8.1). */
public final class ManaRegen {
    public static final double COMBAT_PER_SEC = 2.0;
    public static final double CALM_PER_SEC = 5.0;
    /** Ticks without dealing/taking damage before the calm rate applies (5 s). */
    public static final long CALM_DELAY_TICKS = 100;
    public static final double DEATH_KEEP_FRACTION = 0.5;

    public static boolean inCombat(long ticksSinceCombat) {
        return ticksSinceCombat < CALM_DELAY_TICKS;
    }

    /** Mana restored per tick; {@code regenMult} is the {@code mana_regen} attribute. */
    public static double perTick(long ticksSinceCombat, double regenMult) {
        double perSec = inCombat(ticksSinceCombat) ? COMBAT_PER_SEC : CALM_PER_SEC;
        return perSec * regenMult / 20.0;
    }

    private ManaRegen() {}
}
