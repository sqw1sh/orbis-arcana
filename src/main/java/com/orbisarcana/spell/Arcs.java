package com.orbisarcana.spell;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Lightning bolts (Lightning school): jagged lines of glowing segments, the VFX {@code bolt} and {@code bolt_glow}. */
public final class Arcs {
    private static final double STEP = 0.6;
    private static final double JITTER = 0.32;
    private static final Identifier BOLT = Vfx.id("bolt"), GLOW = Vfx.id("bolt_glow"), HIT = Vfx.id("discharge_hit"),
            SPARKS = Vfx.id("discharge_sparks");

    /** A jagged bolt from {@code a} to {@code b}: each segment is drawn along itself, scaled to its length. */
    public static void draw(ServerLevel level, Vec3 a, Vec3 b) {
        Vec3 d = b.subtract(a);
        int n = Math.max(2, (int) (d.length() / STEP));
        var rnd = level.getRandom();
        Vec3 prev = a;
        for (int i = 1; i <= n; i++) {
            Vec3 p = a.add(d.scale((double) i / n));
            double j = i == n ? 0 : JITTER;
            p = p.add((rnd.nextDouble() - 0.5) * j, (rnd.nextDouble() - 0.5) * j, (rnd.nextDouble() - 0.5) * j);
            Vec3 seg = p.subtract(prev);
            float len = (float) seg.length();
            if (len > 1.0E-3) {
                Vec3 mid = prev.add(seg.scale(0.5));
                Vfx.play(level, BOLT, mid, seg, 0, len);
                Vfx.play(level, GLOW, mid, seg, 0, len);
            }
            prev = p;
        }
    }

    /** The strike on a target: a blue-white flash and sparks. */
    public static void strike(ServerLevel level, Vec3 at) {
        Vfx.play(level, HIT, at, Vec3.ZERO, 0, 1f);
        Vfx.play(level, SPARKS, at, Vec3.ZERO, 0, 1f);
    }

    /** Middle of an entity's body. */
    public static Vec3 body(Entity e) {
        return e.position().add(0, e.getBbHeight() * 0.55, 0);
    }

    private Arcs() {}
}
