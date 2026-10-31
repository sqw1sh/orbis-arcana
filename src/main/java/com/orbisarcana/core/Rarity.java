package com.orbisarcana.core;

/** The 8 fixed spell rarities (§5.4). Not extensible by addons. */
public enum Rarity {
    COMMON("common", 0xD9D9D9, 1.00, 1.0, 1, 0.6),
    UNCOMMON("uncommon", 0x55FF55, 1.15, 1.2, 2, 0.7),
    RARE("rare", 0x5599FF, 1.30, 1.45, 2, 0.8),
    EPIC("epic", 0xAA44FF, 1.50, 1.75, 3, 0.95),
    LEGENDARY("legendary", 0xFFAA00, 1.75, 2.1, 3, 1.1),
    MYTHIC("mythic", 0xFF5555, 2.05, 2.5, 4, 1.25),
    ANCIENT("ancient", 0xB5884A, 2.40, 3.0, 4, 1.4),
    DIVINE("divine", 0xFFF2A8, 2.80, 3.6, 5, 1.6);

    public final String id;
    public final int color;
    public final double powerBudget;
    public final double masteryXpMult;
    public final int circleLayers;
    public final double circleBaseRadius;

    Rarity(String id, int color, double powerBudget, double masteryXpMult, int circleLayers, double circleBaseRadius) {
        this.id = id;
        this.color = color;
        this.powerBudget = powerBudget;
        this.masteryXpMult = masteryXpMult;
        this.circleLayers = circleLayers;
        this.circleBaseRadius = circleBaseRadius;
    }

    public static Rarity byId(String id) {
        for (Rarity r : values()) {
            if (r.id.equals(id)) return r;
        }
        throw new IllegalArgumentException("Unknown rarity: " + id);
    }
}
