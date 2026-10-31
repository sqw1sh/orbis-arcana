package com.orbisarcana.core;

/** Input/cast mode (§5.3). */
public enum CastMode {
    /** Press and it fires. */
    INSTANT,
    /** Hold to charge, release after {@code min_ticks}. */
    CHARGED,
    /** Hold to keep the effect going; mana is drained per tick. */
    CHANNEL,
    /** Press toggles a persistent effect. */
    TOGGLE;

    public String id() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
