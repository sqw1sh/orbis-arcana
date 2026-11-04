package com.orbisarcana.client.vfx;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.orbisarcana.OrbisArcana;
import com.orbisarcana.compat.iris.IrisCompat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * Runs and draws the volumetric effects ({@link VfxMesh}): each instance follows its entity if attached, ages, and is
 * drawn every frame as textured quads relative to the camera; trails keep the last positions of their entity. Big
 * impacts shake the camera of the players near them.
 */
@EventBusSubscriber(modid = OrbisArcana.MODID, value = Dist.CLIENT)
public final class VfxMeshes {
    private static final float DT = 0.05f, LOOSE_LOOP_SECONDS = 30;
    private static final List<Instance> INSTANCES = new ArrayList<>();
    private static final List<float[]> SHAKES = new ArrayList<>(); // {strength, time left, total time}

    private static final class Instance {
        final VfxMesh m;
        double x, y, z, px, py, pz;
        final Vector3f ax = new Vector3f(1, 0, 0), ay = new Vector3f(0, 1, 0), az = new Vector3f(0, 0, 1);
        final float tr, tg, tb, scale;
        final int attach;
        float age;
        boolean stopped;
        final Deque<Vec3> trail = new ArrayDeque<>();

        Instance(VfxMesh m, Vec3 pos, Vec3 forward, int tint, float scale, int attach) {
            this.m = m;
            x = px = pos.x;
            y = py = pos.y;
            z = pz = pos.z;
            this.scale = scale;
            this.attach = attach;
            tr = tint == 0 ? 1 : (tint >> 16 & 0xFF) / 255f;
            tg = tint == 0 ? 1 : (tint >> 8 & 0xFF) / 255f;
            tb = tint == 0 ? 1 : (tint & 0xFF) / 255f;
            if (m.alongForward && forward.lengthSqr() > 1.0E-6) {
                // the shape's axis (local Y) along the forward direction
                ay.set((float) forward.x, (float) forward.y, (float) forward.z).normalize();
                Vector3f ref = Math.abs(ay.y) > 0.95f ? new Vector3f(1, 0, 0) : new Vector3f(0, 1, 0);
                ax.set(ref).cross(ay).normalize();
                az.set(ax).cross(ay).normalize();
            }
        }

        boolean done() {
            if (m.loop) return stopped && trail.isEmpty();
            return age >= m.life && (m.shape != VfxMesh.Shape.TRAIL || trail.isEmpty());
        }
    }

    /** Starts {@code mesh} (the server's VfxPlay lands here for mesh effects). */
    static void spawn(VfxMesh mesh, Vec3 pos, Vec3 forward, int tint, float scale, int attach) {
        INSTANCES.add(new Instance(mesh, pos, forward, tint, scale <= 0 ? 1 : scale, attach));
        if (mesh.shakeStrength > 0) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;
            double d = mc.player.position().distanceTo(pos);
            float k = (float) (1 - d / (mesh.shakeRadius * Math.max(1, scale)));
            if (k > 0) SHAKES.add(new float[] {mesh.shakeStrength * k * Math.min(2, scale), mesh.shakeTime, mesh.shakeTime});
        }
    }

    static void detach(int entityId) {
        for (Instance i : INSTANCES) if (i.attach == entityId) i.stopped = true;
    }

    @SubscribeEvent
    static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.isPaused()) return;
        for (Iterator<Instance> it = INSTANCES.iterator(); it.hasNext(); ) {
            Instance i = it.next();
            i.px = i.x;
            i.py = i.y;
            i.pz = i.z;
            if (i.attach >= 0) {
                Entity e = mc.level.getEntity(i.attach);
                if (e == null && i.age < 0.5f) {
                    // the entity may arrive a moment after the effect
                } else if (e == null || !e.isAlive()) {
                    i.stopped = true;
                } else {
                    i.x = e.getX();
                    i.y = e.getY() + e.getBbHeight() / 2;
                    i.z = e.getZ();
                }
            }
            if (i.m.loop && i.attach < 0 && i.age > LOOSE_LOOP_SECONDS) i.stopped = true;
            if (i.m.shape == VfxMesh.Shape.TRAIL) {
                if (!i.stopped && (i.m.loop || i.age < i.m.life)) i.trail.addFirst(new Vec3(i.px, i.py, i.pz));
                else if (!i.trail.isEmpty()) i.trail.removeLast(); // the tail catches up once the head is gone
                while (i.trail.size() > i.m.samples) i.trail.removeLast();
            }
            i.age += DT;
            if (i.done()) it.remove();
        }
        for (Iterator<float[]> it = SHAKES.iterator(); it.hasNext(); ) {
            float[] s = it.next();
            s[1] -= DT;
            if (s[1] <= 0) it.remove();
        }
    }

    @SubscribeEvent
    static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        INSTANCES.clear();
        SHAKES.clear();
    }

    @SubscribeEvent
    static void shakeCamera(ViewportEvent.ComputeCameraAngles event) {
        if (SHAKES.isEmpty()) return;
        float amount = 0;
        for (float[] s : SHAKES) amount += s[0] * (s[1] / s[2]) * (s[1] / s[2]);
        amount = Math.min(amount, 6);
        double t = (System.nanoTime() / 1.0E9) * 38;
        event.setYaw(event.getYaw() + (float) (Math.sin(t * 1.1) * amount * 0.6));
        event.setPitch(event.getPitch() + (float) (Math.sin(t * 1.7 + 1) * amount * 0.5));
        event.setRoll(event.getRoll() + (float) (Math.sin(t * 0.9 + 2) * amount * 0.4));
    }

    // ---- rendering ----

    @SubscribeEvent
    static void submit(SubmitCustomGeometryEvent event) {
        if (INSTANCES.isEmpty()) return;
        CameraRenderState cam = event.getLevelRenderState().cameraRenderState;
        if (cam == null || cam.pos == null) return;
        float partial = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
        float brightness = IrisCompat.isShaderPackInUse() ? 0.6f : 1f;
        Map<RenderType, List<Instance>> groups = new HashMap<>();
        for (Instance i : INSTANCES) groups.computeIfAbsent(VfxRenderTypes.getMesh(i.m.texture, i.m.additive), k -> new ArrayList<>()).add(i);
        for (var group : groups.entrySet()) {
            List<Instance> list = group.getValue();
            event.getSubmitNodeCollector().submitCustomGeometry(event.getPoseStack(), group.getKey(), (pose, buf) -> {
                for (Instance i : list) draw(i, cam.pos, partial, brightness, pose, buf);
            });
        }
    }

    private static void draw(Instance i, Vec3 cam, float partial, float brightness, PoseStack.Pose pose, VertexConsumer buf) {
        VfxMesh m = i.m;
        float time = i.age + partial * DT;
        float t = m.loop ? (m.life > 0 ? (time % m.life) / m.life : 0) : Math.min(1, time / m.life);
        float[] c = new float[4];
        m.color(t, c);
        Draw d = new Draw(i, buf, pose, cam, partial, brightness, c, time, t);
        if (m.shape == VfxMesh.Shape.TRAIL) {
            d.trail();
            return;
        }
        if (!m.loop && time > m.life) return;
        switch (m.shape) {
            case SPHERE -> d.sphere();
            case CYLINDER, CONE -> d.tube();
            case RING -> d.ring();
            case SPIRAL -> d.spiral();
            default -> { }
        }
    }

    /** One instance's frame: its centre and axes relative to the camera, colour, scrolling and spin. */
    private static final class Draw {
        final Instance i;
        final VfxMesh m;
        final VertexConsumer buf;
        final PoseStack.Pose pose;
        final Vec3 cam;
        final float cx, cy, cz, t, time, uOff, vOff, spinRad;
        final float r, g, b, a;
        final Vector3f p = new Vector3f(), n = new Vector3f();

        Draw(Instance i, VertexConsumer buf, PoseStack.Pose pose, Vec3 cam, float partial, float brightness, float[] c, float time, float t) {
            this.i = i;
            this.m = i.m;
            this.buf = buf;
            this.pose = pose;
            this.cam = cam;
            this.t = t;
            this.time = time;
            cx = (float) (i.px + (i.x - i.px) * partial - cam.x);
            cy = (float) (i.py + (i.y - i.py) * partial - cam.y);
            cz = (float) (i.pz + (i.z - i.pz) * partial - cam.z);
            uOff = m.uScroll * time;
            vOff = m.vScroll * time;
            spinRad = (float) Math.toRadians(m.spin * time);
            r = c[0] * i.tr * brightness;
            g = c[1] * i.tg * brightness;
            b = c[2] * i.tb * brightness;
            a = c[3];
        }

        /** Local point (x across, y along the axis, z across) turned by the spin, into camera-relative space. */
        void place(float lx, float ly, float lz, Vector3f out) {
            float cs = (float) Math.cos(spinRad), sn = (float) Math.sin(spinRad);
            float rx = lx * cs - lz * sn, rz = lx * sn + lz * cs;
            out.set(i.ax.x * rx + i.ay.x * ly + i.az.x * rz, i.ax.y * rx + i.ay.y * ly + i.az.y * rz, i.ax.z * rx + i.ay.z * ly + i.az.z * rz);
        }

        void vertex(float lx, float ly, float lz, float nx, float ny, float nz, float u, float v, float fade) {
            float s = i.scale;
            place(lx * s, ly * s, lz * s, p);
            place(nx, ny, nz, n);
            float alpha = a * fade;
            if (m.fresnel != 0) {
                float wx = p.x + cx, wy = p.y + cy, wz = p.z + cz;
                float len = (float) Math.sqrt(wx * wx + wy * wy + wz * wz);
                float ndv = len < 1.0E-4f ? 1 : Math.abs((n.x * wx + n.y * wy + n.z * wz) / (len * Math.max(1.0E-4f, n.length())));
                alpha *= m.fresnel > 0 ? (float) Math.pow(1 - ndv, m.fresnel) : (float) Math.pow(ndv, -m.fresnel);
            }
            int argb = ((int) (Math.max(0, Math.min(1, alpha)) * 255) << 24) | ((int) (Math.min(1, r) * 255) << 16)
                    | ((int) (Math.min(1, g) * 255) << 8) | (int) (Math.min(1, b) * 255);
            buf.addVertex(pose, cx + p.x, cy + p.y, cz + p.z).setUv(u * m.uScale + uOff, v * m.vScale + vOff).setColor(argb);
        }

        void sphere() {
            float rad = m.radius.at(t);
            int seg = m.segments, rings = m.rings;
            for (int y = 0; y < rings; y++) for (int x = 0; x < seg; x++) {
                corner(rad, x, y, seg, rings);
                corner(rad, x, y + 1, seg, rings);
                corner(rad, x + 1, y + 1, seg, rings);
                corner(rad, x + 1, y, seg, rings);
            }
        }

        private void corner(float rad, int x, int y, int seg, int rings) {
            double th = Math.PI * y / rings, ph = Math.PI * 2 * x / seg;
            float nx = (float) (Math.sin(th) * Math.cos(ph)), ny = (float) Math.cos(th), nz = (float) (Math.sin(th) * Math.sin(ph));
            vertex(nx * rad, ny * rad, nz * rad, nx, ny, nz, (float) x / seg, (float) y / rings, 1);
        }

        /** Cylinder or cone along the axis from the centre: radius at the base, radius_top at the end, height long. */
        void tube() {
            float r0 = m.radius.at(t), r1 = m.radiusTop.at(t), h = m.height.at(t);
            int seg = m.segments, st = Math.max(1, m.rings);
            for (int k = 0; k < st; k++) for (int x = 0; x < seg; x++) {
                float s0 = (float) k / st, s1 = (float) (k + 1) / st;
                tubeVertex(r0, r1, h, x, s0, seg);
                tubeVertex(r0, r1, h, x, s1, seg);
                tubeVertex(r0, r1, h, x + 1, s1, seg);
                tubeVertex(r0, r1, h, x + 1, s0, seg);
            }
        }

        private void tubeVertex(float r0, float r1, float h, int x, float s, int seg) {
            double ph = Math.PI * 2 * x / seg;
            float rad = r0 + (r1 - r0) * s, cs = (float) Math.cos(ph), sn = (float) Math.sin(ph);
            // the ends fade out so a beam or a column has no hard rim
            float fade = Math.min(1, Math.min(s, 1 - s) * 8);
            vertex(cs * rad, s * h, sn * rad, cs, 0, sn, (float) x / seg, s, m.shape == VfxMesh.Shape.CONE ? Math.min(1, (1 - s) * 6) : fade);
        }

        /** Flat ring around the axis: radius in the middle of a band thickness wide; v runs across the band. */
        void ring() {
            float rad = m.radius.at(t), th = m.thickness.at(t) / 2;
            int seg = m.segments;
            for (int x = 0; x < seg; x++) {
                ringVertex(rad - th, x, 0, seg);
                ringVertex(rad + th, x, 1, seg);
                ringVertex(rad + th, x + 1, 1, seg);
                ringVertex(rad - th, x + 1, 0, seg);
            }
        }

        private void ringVertex(float rad, int x, float v, int seg) {
            double ph = Math.PI * 2 * x / seg;
            // soft edges across the band
            vertex((float) Math.cos(ph) * Math.max(0, rad), 0, (float) Math.sin(ph) * Math.max(0, rad), 0, 1, 0, (float) x / seg, v, 1);
        }

        /** Ribbons winding up around the axis: radius from base to top, height long, thickness wide (along the axis). */
        void spiral() {
            float r0 = m.radius.at(t), r1 = m.radiusTop.at(t), h = m.height.at(t), w = m.thickness.at(t) / 2;
            int seg = m.segments;
            for (int arm = 0; arm < m.arms; arm++) {
                double off = Math.PI * 2 * arm / m.arms;
                for (int k = 0; k < seg; k++) {
                    spiralVertex(r0, r1, h, -w, k, seg, off, 0);
                    spiralVertex(r0, r1, h, w, k, seg, off, 1);
                    spiralVertex(r0, r1, h, w, k + 1, seg, off, 1);
                    spiralVertex(r0, r1, h, -w, k + 1, seg, off, 0);
                }
            }
        }

        private void spiralVertex(float r0, float r1, float h, float dy, int k, int seg, double off, float v) {
            float s = (float) k / seg;
            double ang = off + s * m.turns * Math.PI * 2;
            float rad = r0 + (r1 - r0) * s, cs = (float) Math.cos(ang), sn = (float) Math.sin(ang);
            float fade = Math.min(1, Math.min(s, 1 - s) * 6);
            vertex(cs * rad, s * h + dy, sn * rad, cs, 0, sn, s, v, fade);
        }

        /** A ribbon through the entity's last positions, facing the camera, thinning and fading to the tail. */
        void trail() {
            // camera-relative points: the head (interpolated) then the stored positions
            List<Vec3> pts = new ArrayList<>();
            if (!i.stopped) pts.add(new Vec3(cx, cy, cz));
            for (Vec3 h : i.trail) pts.add(h.subtract(cam));
            if (pts.size() < 2) return;
            for (int k = 0; k < pts.size() - 1; k++) trailQuad(pts, k, pts.size());
        }

        private void trailQuad(List<Vec3> pts, int k, int count) {
            Vec3 a0 = pts.get(k), a1 = pts.get(k + 1);
            float s0 = (float) k / (count - 1), s1 = (float) (k + 1) / (count - 1);
            Vector3f side0 = side(pts, k), side1 = side(pts, k + 1);
            float w0 = m.width.at(s0) * i.scale / 2, w1 = m.width.at(s1) * i.scale / 2;
            raw(a0, side0, -w0, s0, 0);
            raw(a0, side0, w0, s0, 1);
            raw(a1, side1, w1, s1, 1);
            raw(a1, side1, -w1, s1, 0);
        }

        private Vector3f side(List<Vec3> pts, int k) {
            Vec3 a = pts.get(Math.max(0, k - 1)), b = pts.get(Math.min(pts.size() - 1, k + 1)), at = pts.get(k);
            Vector3f dir = new Vector3f((float) (a.x - b.x), (float) (a.y - b.y), (float) (a.z - b.z));
            Vector3f view = new Vector3f((float) at.x, (float) at.y, (float) at.z);
            Vector3f s = dir.cross(view, new Vector3f());
            if (s.lengthSquared() < 1.0E-8f) s.set(0, 1, 0);
            return s.normalize();
        }

        private void raw(Vec3 at, Vector3f side, float w, float s, float v) {
            float alpha = a * (1 - s);
            int argb = ((int) (Math.max(0, Math.min(1, alpha)) * 255) << 24) | ((int) (Math.min(1, r) * 255) << 16)
                    | ((int) (Math.min(1, g) * 255) << 8) | (int) (Math.min(1, b) * 255);
            buf.addVertex(pose, (float) at.x + side.x * w, (float) at.y + side.y * w, (float) at.z + side.z * w)
                    .setUv(s * m.uScale + uOff, v * m.vScale + vOff).setColor(argb);
        }
    }

    private VfxMeshes() {}
}
