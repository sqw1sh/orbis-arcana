package com.orbisarcana.core;

/** Where the magic circle appears (§5.2). */
public enum Targeting {
    DIRECTIONAL,
    GROUND,
    TARGET,
    SELF;

    public String id() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
