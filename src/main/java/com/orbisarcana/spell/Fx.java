package com.orbisarcana.spell;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/**
 * The mod's stock effects (roadmap V8), the replacement of vanilla particle bursts: each is a VFX emitter
 * ({@code art/vfx/particles/<name>.json}, written by tools/vfx-effects.cjs) that sends 10 particles from a box of
 * half-size 1, drawn in white and tinted here. {@link #play} takes a vanilla burst's count and spread as they were.
 */
public enum Fx {
    /** Soft glowing motes drifting up: magic in the air, blessings, enchanting. */
    MOTE("mote", 0xFFF2C8),
    /** Four-point stars that flare and fade: light and astral. */
    TWINKLE("twinkle", 0xFFFFFF),
    /** Fast streaks flying out and slowing: hits, crits, electricity. */
    SPARK("spark", 0xA8D8FF),
    /** Short lightning arcs. */
    ARC("arc", 0x9FD8FF),
    /** Grey smoke puffs rising. */
    SMOKE("smoke", 0x9A9A9A),
    /** White puffs of air. */
    CLOUD("smoke", 0xF2F6FA),
    /** Tongues of flame rising. */
    FLAME("flame", 0xFFFFFF),
    /** Ice shards and frost glints falling slowly. */
    FROST("frost", 0xBFEFFF),
    /** Water drops thrown up and falling. */
    SPLASH("splash", 0x6AB0FF),
    /** Leaves and petals fluttering down: nature and healing. */
    LEAF("leaf", 0x7CD65A),
    /** Stone chips thrown out and falling. */
    DEBRIS("debris", 0x8A8076),
    /** Dark smoke of shadow magic. */
    SHADOW("shadow", 0x3A1E58),
    /** A flat ring growing on the ground, with a flash. */
    SHOCKWAVE("shockwave", 0xFFFFFF),
    /** A crescent of wind streaks around the point. */
    SLASH("slash", 0xE8F4FF),
    /** A rune circle rising and turning. */
    RUNE("rune", 0xC890FF);

    /** Particles one emitter sends at density 1. */
    public static final int BASE_COUNT = 10;

    private final Identifier id;
    private final int tint;

    Fx(String effect, int tint) {
        this.id = Vfx.id(effect);
        this.tint = tint;
    }

    /** {@code count} particles spread over (dx, dy, dz) around {@code pos}, as a vanilla burst would be, in the stock colour. */
    public void play(ServerLevel level, Vec3 pos, int count, double dx, double dy, double dz) {
        play(level, pos, count, dx, dy, dz, tint);
    }

    /** As above in {@code tint} (0xRRGGBB, e.g. a school colour). */
    public void play(ServerLevel level, Vec3 pos, int count, double dx, double dy, double dz, int tint) {
        Vfx.play(level, id, pos, Vec3.ZERO, tint, 1f, (float) dx, (float) dy, (float) dz, Math.max(1, count) / (float) BASE_COUNT);
    }

    public void play(ServerLevel level, double x, double y, double z, int count, double dx, double dy, double dz) {
        play(level, new Vec3(x, y, z), count, dx, dy, dz);
    }

    public void play(ServerLevel level, double x, double y, double z, int count, double dx, double dy, double dz, int tint) {
        play(level, new Vec3(x, y, z), count, dx, dy, dz, tint);
    }
}
