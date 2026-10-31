package com.orbisarcana.core;

/** Why the server refused to start a cast (§8.2, §13.2). */
public enum CastDenial {
    UNKNOWN_SPELL,
    DISABLED,
    NOT_LEARNED,
    ALREADY_CASTING,
    GLOBAL_COOLDOWN,
    COOLDOWN,
    NOT_ENOUGH_MANA,
    NO_TARGET,
    OUT_OF_RANGE;

    public String id() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
