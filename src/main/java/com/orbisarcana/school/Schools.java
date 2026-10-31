package com.orbisarcana.school;

import java.util.List;

import com.orbisarcana.OrbisArcana;

import net.minecraft.resources.Identifier;

/** Built-in school ids (§10.1). Becomes a datapack/addon registry in S6. */
public final class Schools {
    public static final Identifier FIRE = OrbisArcana.id("fire");
    public static final Identifier EARTH = OrbisArcana.id("earth");
    public static final Identifier WATER = OrbisArcana.id("water");
    public static final Identifier AIR = OrbisArcana.id("air");
    public static final Identifier ICE = OrbisArcana.id("ice");
    public static final Identifier LIGHTNING = OrbisArcana.id("lightning");
    public static final Identifier NATURE = OrbisArcana.id("nature");
    public static final Identifier LIGHT = OrbisArcana.id("light");
    public static final Identifier DARK = OrbisArcana.id("dark");
    public static final Identifier ASTRAL = OrbisArcana.id("astral");

    public static final List<Identifier> ALL = List.of(FIRE, EARTH, WATER, AIR, ICE, LIGHTNING, NATURE, LIGHT, DARK, ASTRAL);
    public static final List<Identifier> HIGHER = List.of(LIGHT, DARK, ASTRAL);

    /** Primary circle colour of a school (§5.4: the school sets the colour, rarity only the outline). */
    public static int color(Identifier id) {
        return switch (id.getPath()) {
            case "fire" -> 0xFF6A2A;
            case "earth" -> 0xC09050;
            case "water" -> 0x3FA9F5;
            case "air" -> 0xC8F5EE;
            case "ice" -> 0x9EE4FF;
            case "lightning" -> 0xFFE95A;
            case "nature" -> 0x6BD66B;
            case "light" -> 0xFFF2B0;
            case "dark" -> 0x9B5CD6;
            case "astral" -> 0x7A8CFF;
            default -> 0xFFFFFF;
        };
    }

    public static boolean exists(Identifier id) {
        return ALL.contains(id);
    }

    public static boolean isHigher(Identifier id) {
        return HIGHER.contains(id);
    }

    private Schools() {}
}
