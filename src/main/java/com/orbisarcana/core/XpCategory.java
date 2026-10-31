package com.orbisarcana.core;

/** Kind of result that grants mastery XP (§7.2). */
public enum XpCategory {
    ATTACK,
    CONTROL,
    HEAL,
    SHIELD,
    SUPPORT,
    MOBILITY;

    public String id() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
