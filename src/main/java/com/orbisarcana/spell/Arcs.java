package com.orbisarcana.spell;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Electric arcs drawn with particles (Lightning school). */
public final class Arcs {
    private static final double STEP = 0.35;
    private static final double JITTER = 0.18;

    /** A jagged spark line from {@code a} to {@code b}. */
    public static void draw(ServerLevel level, Vec3 a, Vec3 b) {
        Vec3 d = b.subtract(a);
        int n = Math.max(2, (int) (d.length() / STEP));
        var rnd = level.getRandom();
        for (int i = 0; i <= n; i++) {
            Vec3 p = a.add(d.scale((double) i / n));
            double j = i == 0 || i == n ? 0 : JITTER;
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.x + (rnd.nextDouble() - 0.5) * j, p.y + (rnd.nextDouble() - 0.5) * j,
                    p.z + (rnd.nextDouble() - 0.5) * j, 1, 0, 0, 0, 0);
        }
    }

    /** Middle of an entity's body. */
    public static Vec3 body(Entity e) {
        return e.position().add(0, e.getBbHeight() * 0.55, 0);
    }

    private Arcs() {}
}
