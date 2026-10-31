package com.orbisarcana.client.circle;

import java.util.List;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.phys.Vec3;

/**
 * Fallback for shader packs that break custom geometry (§9.7): the rim is traced with dust particles,
 * progress shown by how much of the rim is traced. Throttled to every other game tick.
 */
final class CircleParticles {
    private static long lastEmitTick = Long.MIN_VALUE;

    static void emit(ClientLevel level, Vec3 cam, List<CircleRenderState> states) {
        long tick = level.getGameTime();
        if (tick - lastEmitTick < 2) return;
        lastEmitTick = tick;
        for (CircleRenderState s : states) {
            DustParticleOptions dust = new DustParticleOptions(s.color(), 0.6f);
            float r = s.radius() * s.scale();
            int points = Math.max(8, (int) (r * 10 * Math.max(0.05f, s.progress())));
            double arc = Math.PI * 2 * Math.max(0.05f, s.progress());
            Vec3 center = s.center().add(cam);
            for (int i = 0; i < points; i++) {
                double a = arc * i / points + s.timeSec();
                Vec3 p = center.add(s.u().scale(Math.cos(a) * r)).add(s.v().scale(Math.sin(a) * r));
                level.addParticle(dust, p.x, p.y, p.z, 0, 0, 0);
            }
        }
    }

    private CircleParticles() {}
}
