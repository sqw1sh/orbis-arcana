package com.orbisarcana.client.vfx;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.orbisarcana.OrbisArcana;
import com.orbisarcana.client.ClientConfig;
import com.orbisarcana.compat.iris.IrisCompat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;

/**
 * The mod's own particle system (roadmap V4), separate from the vanilla particle engine: emitters of {@link VfxEffect}s
 * simulated at 20 Hz on the client, interpolated and drawn as textured billboards in the mod's VFX pipelines. Effects are
 * started by the server ({@code spell/Vfx}, payload {@code VfxPlay}) or locally. Quality (client config) scales the
 * particle counts and caps the total.
 */
@EventBusSubscriber(modid = OrbisArcana.MODID, value = Dist.CLIENT)
public final class VfxManager {
    private static final float DT = 0.05f;
    private static final double MAX_DISTANCE = 96;
    private static final float LOOSE_LOOP_SECONDS = 30;
    private static final int S_P_AGE = Molang.slot("particle_age"), S_P_LIFE = Molang.slot("particle_lifetime");
    private static final int[] S_P_RANDOM = {Molang.slot("particle_random_1"), Molang.slot("particle_random_2"),
            Molang.slot("particle_random_3"), Molang.slot("particle_random_4")};
    private static final int S_E_AGE = Molang.slot("emitter_age"), S_E_LIFE = Molang.slot("emitter_lifetime");
    private static final int[] S_E_RANDOM = {Molang.slot("emitter_random_1"), Molang.slot("emitter_random_2"),
            Molang.slot("emitter_random_3"), Molang.slot("emitter_random_4")};

    private static final List<Emitter> EMITTERS = new ArrayList<>();
    private static int particleCount;

    static final class Particle {
        double x, y, z, px, py, pz, vx, vy, vz;
        float age, life, rot, prot, rotRate;
        float w, h;
        final Molang.Scope scope = new Molang.Scope();
    }

    static final class Emitter {
        final VfxEffect fx;
        double x, y, z;
        /** Emitter space: right, up, forward. */
        final Vector3f ax = new Vector3f(1, 0, 0), ay = new Vector3f(0, 1, 0), az = new Vector3f(0, 0, 1);
        final float tr, tg, tb;
        final float scale;
        /** Stretch of the emitter shape per axis, and the multiplier of particle counts. */
        final Vector3f spread;
        final float density;
        final int attach;
        float age, spawnDebt, activeTime, sleepTime;
        boolean instantDone;
        final List<Particle> particles = new ArrayList<>();
        final Molang.Scope scope = new Molang.Scope();

        Emitter(VfxEffect fx, Vec3 pos, Vec3 forward, int tint, float scale, int attach, Vector3f spread, float density) {
            this.fx = fx;
            this.spread = spread;
            this.density = density;
            x = pos.x;
            y = pos.y;
            z = pos.z;
            this.scale = scale;
            this.attach = attach;
            tr = tint == 0 ? 1 : (tint >> 16 & 0xFF) / 255f;
            tg = tint == 0 ? 1 : (tint >> 8 & 0xFF) / 255f;
            tb = tint == 0 ? 1 : (tint & 0xFF) / 255f;
            if (forward.lengthSqr() > 1.0E-6) {
                az.set((float) forward.x, (float) forward.y, (float) forward.z).normalize();
                Vector3f up = Math.abs(az.y) > 0.95f ? new Vector3f(0, 0, 1) : new Vector3f(0, 1, 0);
                ax.set(up).cross(az).normalize();
                ay.set(az).cross(ax).normalize();
            }
            for (int s : S_E_RANDOM) scope.set(s, ThreadLocalRandom.current().nextDouble());
            if (fx.creation != null) fx.creation.eval(scope);
            activeTime = (float) fx.activeTime.eval(scope);
            sleepTime = (float) fx.sleepTime.eval(scope);
            scope.set(S_E_LIFE, activeTime);
        }

        /** Stopped: no more particles; the emitter goes once the last ones die (looping effects only end this way). */
        boolean stopped;

        boolean done() {
            return (stopped || !fx.looping && age > activeTime) && particles.isEmpty();
        }
    }

    // ---- public API ----

    /** Starts {@code effect} at {@code pos}; {@code forward} orients the emitter (zero: world axes); {@code tint} 0 = none. */
    public static void spawn(Identifier effect, Vec3 pos, Vec3 forward, int tint, float scale, int attachEntity) {
        spawn(effect, pos, forward, tint, scale, attachEntity, new Vector3f(1, 1, 1), 1);
    }

    /** As above, the emitter shape stretched by {@code spread} per axis and {@code density} times the particles. */
    public static void spawn(Identifier effect, Vec3 pos, Vec3 forward, int tint, float scale, int attachEntity, Vector3f spread, float density) {
        VfxEffect fx = VfxLoader.get(effect);
        Minecraft mc = Minecraft.getInstance();
        if (fx == null || mc.level == null) return;
        if (mc.gameRenderer.mainCamera().position().distanceToSqr(pos) > MAX_DISTANCE * MAX_DISTANCE) return;
        EMITTERS.add(new Emitter(fx, pos, forward, tint, scale <= 0 ? 1 : scale, attachEntity, spread, density <= 0 ? 1 : density));
    }

    /** Stops the looping emitters attached to an entity (their particles live out their lives). */
    public static void detach(int entityId) {
        for (Emitter e : EMITTERS) if (e.attach == entityId) e.stopped = true;
    }

    public static int particleCount() {
        return particleCount;
    }

    // ---- simulation ----

    @SubscribeEvent
    static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.isPaused()) return;
        ClientConfig.VfxQuality q = ClientConfig.VFX_QUALITY.get();
        int cap = q.cap;
        int total = 0;
        for (Iterator<Emitter> it = EMITTERS.iterator(); it.hasNext(); ) {
            Emitter e = it.next();
            if (e.attach >= 0) {
                Entity entity = mc.level.getEntity(e.attach);
                // the entity may arrive a moment after the effect: wait half a second before giving up on it
                if (entity == null && e.age < 0.5f) {
                    // keep the spawn position meanwhile
                } else if (entity == null || !entity.isAlive()) {
                    e.stopped = true;
                } else {
                    e.x = entity.getX();
                    e.y = entity.getY() + entity.getBbHeight() / 2;
                    e.z = entity.getZ();
                }
            }
            tickEmitter(e, q.spawnScale, cap - particleCount);
            if (e.done()) it.remove();
            total += e.particles.size();
        }
        particleCount = total;
    }

    private static void tickEmitter(Emitter e, float spawnScale, int room) {
        VfxEffect fx = e.fx;
        e.scope.set(S_E_AGE, e.age);
        if (fx.perUpdate != null) fx.perUpdate.eval(e.scope);
        float cycle = e.activeTime + e.sleepTime;
        float local = fx.looping && cycle > 0 ? e.age % cycle : e.age;
        // loops not tied to an entity stop on their own after 30 s, so nothing can be left glowing forever
        if (fx.looping && e.attach < 0 && e.age > LOOSE_LOOP_SECONDS) e.stopped = true;
        boolean active = !e.stopped && (fx.looping ? local < e.activeTime : e.age <= e.activeTime);
        if (active && room > 0) {
            if (fx.instantCount != null && !e.instantDone) {
                int n = (int) Math.round(fx.instantCount.eval(e.scope) * spawnScale * e.density);
                for (int i = 0; i < Math.min(n, room); i++) spawnParticle(e);
                e.instantDone = !fx.looping;
            }
            if (fx.steadyRate != null) {
                e.spawnDebt += (float) (fx.steadyRate.eval(e.scope) * spawnScale * e.density * DT);
                int max = (int) Math.max(1, fx.steadyMax.eval(e.scope) * spawnScale * e.density);
                while (e.spawnDebt >= 1 && e.particles.size() < max && room-- > 0) {
                    spawnParticle(e);
                    e.spawnDebt -= 1;
                }
                if (e.spawnDebt > 1) e.spawnDebt = 1;
            }
        }
        if (fx.looping && local + DT >= cycle) e.instantDone = false;
        e.age += DT;

        Vector3f acc = new Vector3f();
        for (Iterator<Particle> it = e.particles.iterator(); it.hasNext(); ) {
            Particle p = it.next();
            p.px = p.x;
            p.py = p.y;
            p.pz = p.z;
            p.prot = p.rot;
            p.age += DT;
            if (p.age >= p.life) {
                it.remove();
                continue;
            }
            p.scope.set(S_P_AGE, p.age);
            if (fx.particleUpdate != null) fx.particleUpdate.eval(p.scope);
            toWorld(e, (float) fx.acceleration[0].eval(p.scope), (float) fx.acceleration[1].eval(p.scope), (float) fx.acceleration[2].eval(p.scope), acc);
            double drag = fx.drag.eval(p.scope);
            p.vx += (acc.x * e.scale - p.vx * drag) * DT;
            p.vy += (acc.y * e.scale - p.vy * drag) * DT;
            p.vz += (acc.z * e.scale - p.vz * drag) * DT;
            p.x += p.vx * DT;
            p.y += p.vy * DT;
            p.z += p.vz * DT;
            p.rot += p.rotRate * DT;
            p.w = (float) fx.size[0].eval(p.scope) * e.scale;
            p.h = (float) fx.size[1].eval(p.scope) * e.scale;
        }
    }

    private static void spawnParticle(Emitter e) {
        VfxEffect fx = e.fx;
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        Particle p = new Particle();
        p.scope.inherit(e.scope);
        for (int s : S_P_RANDOM) p.scope.set(s, rnd.nextDouble());
        p.scope.set(S_P_AGE, 0);
        if (fx.particleCreation != null) fx.particleCreation.eval(p.scope);
        p.life = Math.max(0.05f, (float) fx.lifetime.eval(p.scope));
        p.scope.set(S_P_LIFE, p.life);

        // position in emitter space by shape
        float ox = (float) fx.offset[0].eval(p.scope), oy = (float) fx.offset[1].eval(p.scope), oz = (float) fx.offset[2].eval(p.scope);
        float lx = 0, ly = 0, lz = 0;
        switch (fx.shape) {
            case SPHERE -> {
                float r = (float) fx.radius.eval(p.scope);
                Vector3f d = new Vector3f((float) rnd.nextGaussian(), (float) rnd.nextGaussian(), (float) rnd.nextGaussian()).normalize();
                float k = fx.surfaceOnly ? r : r * (float) Math.cbrt(rnd.nextDouble());
                lx = d.x * k; ly = d.y * k; lz = d.z * k;
            }
            case DISC -> {
                float r = (float) fx.radius.eval(p.scope), a = (float) (rnd.nextDouble() * Math.PI * 2);
                float k = fx.surfaceOnly ? r : r * (float) Math.sqrt(rnd.nextDouble());
                Vector3f n = new Vector3f(fx.planeNormal[0], fx.planeNormal[1], fx.planeNormal[2]).normalize();
                Vector3f t1 = Math.abs(n.y) > 0.9f ? new Vector3f(1, 0, 0) : new Vector3f(0, 1, 0).cross(n).normalize();
                Vector3f t2 = new Vector3f(n).cross(t1);
                lx = (t1.x * (float) Math.cos(a) + t2.x * (float) Math.sin(a)) * k;
                ly = (t1.y * (float) Math.cos(a) + t2.y * (float) Math.sin(a)) * k;
                lz = (t1.z * (float) Math.cos(a) + t2.z * (float) Math.sin(a)) * k;
            }
            case BOX -> {
                lx = (float) ((rnd.nextDouble() * 2 - 1) * fx.halfDims[0].eval(p.scope));
                ly = (float) ((rnd.nextDouble() * 2 - 1) * fx.halfDims[1].eval(p.scope));
                lz = (float) ((rnd.nextDouble() * 2 - 1) * fx.halfDims[2].eval(p.scope));
            }
            default -> { }
        }
        Vector3f dir = new Vector3f();
        if (fx.directionMode == null) {
            dir.set((float) fx.direction[0].eval(p.scope), (float) fx.direction[1].eval(p.scope), (float) fx.direction[2].eval(p.scope));
        } else {
            dir.set(lx, ly, lz);
            if (dir.lengthSquared() < 1.0E-6f) dir.set((float) rnd.nextGaussian(), (float) rnd.nextGaussian(), (float) rnd.nextGaussian());
            if (fx.directionMode.equals("inwards")) dir.negate();
        }
        if (dir.lengthSquared() > 1.0E-6f) dir.normalize();
        Vector3f wp = new Vector3f(), wd = new Vector3f();
        toWorld(e, (lx + ox) * e.spread.x, (ly + oy) * e.spread.y, (lz + oz) * e.spread.z, wp);
        toWorld(e, dir.x, dir.y, dir.z, wd);
        p.x = p.px = e.x + wp.x * e.scale;
        p.y = p.py = e.y + wp.y * e.scale;
        p.z = p.pz = e.z + wp.z * e.scale;
        float speed = (float) fx.speed.eval(p.scope) * e.scale;
        p.vx = wd.x * speed;
        p.vy = wd.y * speed;
        p.vz = wd.z * speed;
        p.rot = p.prot = (float) fx.spin.eval(p.scope);
        p.rotRate = (float) fx.spinRate.eval(p.scope);
        p.w = (float) fx.size[0].eval(p.scope) * e.scale;
        p.h = (float) fx.size[1].eval(p.scope) * e.scale;
        e.particles.add(p);
    }

    /** Emitter space (x right, y up, z forward) to world directions. */
    private static void toWorld(Emitter e, float x, float y, float z, Vector3f out) {
        out.set(e.ax.x * x + e.ay.x * y + e.az.x * z, e.ax.y * x + e.ay.y * y + e.az.y * z, e.ax.z * x + e.ay.z * y + e.az.z * z);
    }

    @SubscribeEvent
    static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        EMITTERS.clear();
        particleCount = 0;
    }

    // ---- rendering ----

    @SubscribeEvent
    static void submit(SubmitCustomGeometryEvent event) {
        if (EMITTERS.isEmpty()) return;
        CameraRenderState cam = event.getLevelRenderState().cameraRenderState;
        if (cam == null || cam.pos == null) return;
        float partial = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
        float brightness = IrisCompat.isShaderPackInUse() ? 0.6f : 1f;
        Map<net.minecraft.client.renderer.rendertype.RenderType, List<Emitter>> groups = new HashMap<>();
        for (Emitter e : EMITTERS) if (!e.particles.isEmpty()) groups.computeIfAbsent(VfxRenderTypes.get(e.fx.texture, e.fx.additive), k -> new ArrayList<>()).add(e);
        Quaternionf q = cam.orientation;
        Vector3f right = q.transform(new Vector3f(1, 0, 0)), up = q.transform(new Vector3f(0, 1, 0));
        for (var group : groups.entrySet()) {
            List<Emitter> list = group.getValue();
            event.getSubmitNodeCollector().submitCustomGeometry(event.getPoseStack(), group.getKey(), (pose, buf) -> {
                for (Emitter e : list) for (Particle p : e.particles) quad(e, p, cam.pos, right, up, partial, brightness, pose, buf);
            });
        }
    }

    private static void quad(Emitter e, Particle p, Vec3 cam, Vector3f camRight, Vector3f camUp, float partial, float brightness,
                             com.mojang.blaze3d.vertex.PoseStack.Pose pose, VertexConsumer buf) {
        VfxEffect fx = e.fx;
        float x = (float) (p.px + (p.x - p.px) * partial - cam.x);
        float y = (float) (p.py + (p.y - p.py) * partial - cam.y);
        float z = (float) (p.pz + (p.z - p.pz) * partial - cam.z);
        Vector3f r = new Vector3f(), u = new Vector3f();
        switch (fx.facing) {
            case LOOKAT_Y -> {
                u.set(0, 1, 0);
                r.set(-z, 0, x);
                if (r.lengthSquared() < 1.0E-6f) r.set(1, 0, 0);
                r.normalize();
            }
            case DIRECTION -> {
                u.set((float) p.vx, (float) p.vy, (float) p.vz);
                if (u.lengthSquared() < 1.0E-6f) u.set(camUp);
                u.normalize();
                r.set(u).cross(x, y, z);
                if (r.lengthSquared() < 1.0E-6f) r.set(camRight);
                r.normalize();
            }
            case EMITTER_XZ -> { r.set(e.ax); u.set(e.az); }
            case EMITTER_XY -> { r.set(e.ax); u.set(e.ay); }
            default -> { r.set(camRight); u.set(camUp); }
        }
        float rot = (float) Math.toRadians(p.prot + (p.rot - p.prot) * partial);
        if (rot != 0 && fx.facing != VfxEffect.Facing.DIRECTION) {
            float c = (float) Math.cos(rot), s = (float) Math.sin(rot);
            Vector3f r2 = new Vector3f(r).mul(c).add(new Vector3f(u).mul(s));
            Vector3f u2 = new Vector3f(u).mul(c).sub(new Vector3f(r).mul(s));
            r.set(r2);
            u.set(u2);
        }
        r.mul(p.w);
        u.mul(p.h);

        // UV: plain or flipbook frame
        float u0, v0, us, vs;
        if (fx.fbBase != null) {
            int max = Math.max(1, (int) fx.fbMaxFrame.eval(p.scope));
            int frame = fx.fbStretch ? (int) (p.age / p.life * max) : (int) (p.age * fx.fbFps);
            frame = fx.fbLoop ? Math.floorMod(frame, max) : Math.min(frame, max - 1);
            u0 = ((float) fx.fbBase[0].eval(p.scope) + fx.fbStep[0] * frame) / fx.texW;
            v0 = ((float) fx.fbBase[1].eval(p.scope) + fx.fbStep[1] * frame) / fx.texH;
            us = fx.fbSize[0] / fx.texW;
            vs = fx.fbSize[1] / fx.texH;
        } else {
            u0 = (float) fx.uv[0].eval(p.scope) / fx.texW;
            v0 = (float) fx.uv[1].eval(p.scope) / fx.texH;
            us = (float) fx.uvSize[0].eval(p.scope) / fx.texW;
            vs = (float) fx.uvSize[1].eval(p.scope) / fx.texH;
        }

        // colour: gradient over life or fixed, times the emitter tint
        float cr = 1, cg = 1, cb = 1, ca = 1;
        if (fx.gradient != null) {
            float t = (float) fx.gradient.interpolant().eval(p.scope);
            float[] stops = fx.gradient.stops();
            float[][] cols = fx.gradient.colors();
            int i = 0;
            while (i < stops.length - 1 && t > stops[i + 1]) i++;
            float[] a = cols[i], b = cols[Math.min(i + 1, cols.length - 1)];
            float span = i + 1 < stops.length ? stops[i + 1] - stops[i] : 1;
            float k = span <= 0 ? 0 : Math.max(0, Math.min(1, (t - stops[i]) / span));
            cr = a[0] + (b[0] - a[0]) * k;
            cg = a[1] + (b[1] - a[1]) * k;
            cb = a[2] + (b[2] - a[2]) * k;
            ca = a[3] + (b[3] - a[3]) * k;
        } else if (fx.color != null) {
            cr = (float) fx.color[0].eval(p.scope);
            cg = (float) fx.color[1].eval(p.scope);
            cb = (float) fx.color[2].eval(p.scope);
            ca = (float) fx.color[3].eval(p.scope);
        }
        int argb = argb(ca * brightness, cr * e.tr, cg * e.tg, cb * e.tb);

        buf.addVertex(pose, x - r.x - u.x, y - r.y - u.y, z - r.z - u.z).setUv(u0, v0 + vs).setColor(argb);
        buf.addVertex(pose, x + r.x - u.x, y + r.y - u.y, z + r.z - u.z).setUv(u0 + us, v0 + vs).setColor(argb);
        buf.addVertex(pose, x + r.x + u.x, y + r.y + u.y, z + r.z + u.z).setUv(u0 + us, v0).setColor(argb);
        buf.addVertex(pose, x - r.x + u.x, y - r.y + u.y, z - r.z + u.z).setUv(u0, v0).setColor(argb);
    }

    private static int argb(float a, float r, float g, float b) {
        return clamp(a) << 24 | clamp(r) << 16 | clamp(g) << 8 | clamp(b);
    }

    private static int clamp(float v) {
        return Math.max(0, Math.min(255, (int) (v * 255)));
    }

    private VfxManager() {}
}
