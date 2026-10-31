package com.orbisarcana.core;

/**
 * One cast in progress (§8.2): CASTING until released, interrupted or (CHARGED + autocast) fully charged.
 * Cost, power and timing are snapshotted at start, so swapping a focus mid-cast changes nothing.
 * Mana is reserved at start and charged on release; an interruption loses part of it (§8.3).
 */
public final class ActiveCast {
    public enum Outcome {
        /** Released normally; {@link #power} tells how charged it was. */
        RELEASED,
        /** Released before {@code min_ticks} or cancelled by the player. */
        CANCELLED,
        /** Broken by damage, stun, silence, death, dimension change. */
        INTERRUPTED,
        /** Target vanished or went out of range: not the player's fault. */
        LOST_TARGET
    }

    public final CastMode mode;
    public final long startTick;
    public final int castTicks;
    public final int minTicks;
    public final double cost;

    public ActiveCast(CastMode mode, long startTick, int castTicks, int minTicks, double cost) {
        this.mode = mode;
        this.startTick = startTick;
        this.castTicks = Math.max(0, castTicks);
        this.minTicks = Math.min(Math.max(0, minTicks), this.castTicks);
        this.cost = cost;
    }

    public long elapsed(long now) {
        return Math.max(0, now - startTick);
    }

    /** 0..1 charge progress; INSTANT and zero-length casts are always 1. */
    public double progress(long now) {
        if (castTicks == 0) return 1.0;
        return Math.min(1.0, elapsed(now) / (double) castTicks);
    }

    public boolean isFullyCharged(long now) {
        return elapsed(now) >= castTicks;
    }

    /** Whether letting go now fires the spell (otherwise it is a cancel). */
    public boolean canRelease(long now) {
        return switch (mode) {
            case INSTANT, TOGGLE -> true;
            case CHARGED -> elapsed(now) >= minTicks;
            case CHANNEL -> true;
        };
    }

    /**
     * Power multiplier of a release (D4): CHARGED released between {@code min_ticks} and full charge
     * fires with power equal to its progress; everything else fires at full power.
     */
    public double power(long now) {
        return mode == CastMode.CHARGED ? progress(now) : 1.0;
    }

    /** Mana lost for this outcome at {@code now}. RELEASED spends the full cost. */
    public double manaSpent(Outcome outcome, long now) {
        return switch (outcome) {
            case RELEASED -> cost;
            case CANCELLED, INTERRUPTED -> ManaMath.interruptLoss(cost, progress(now));
            case LOST_TARGET -> ManaMath.blamelessLoss(cost);
        };
    }
}
