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

    public static boolean exists(Identifier id) {
        return ALL.contains(id);
    }

    public static boolean isHigher(Identifier id) {
        return HIGHER.contains(id);
    }

    private Schools() {}
}
