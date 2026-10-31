package com.orbisarcana.client.circle;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.orbisarcana.client.circle.CircleDefinition.Layer;

import net.minecraft.world.phys.Vec3;

/**
 * Procedural circle geometry (§9.5). Everything is built from flat quads in the circle plane, given by
 * a centre (camera-relative) and two unit axes {@code u}, {@code v}.
 */
final class CircleRenderer {
    private static final float TAU = (float) (Math.PI * 2);

    private final PoseStack.Pose pose;
    private final VertexConsumer buf;
    private Vec3 c = Vec3.ZERO;
    private Vec3 u = new Vec3(1, 0, 0);
    private Vec3 v = new Vec3(0, 0, 1);
    private int color;

    CircleRenderer(PoseStack.Pose pose, VertexConsumer buf) {
        this.pose = pose;
        this.buf = buf;
    }

    void draw(CircleRenderState s) {
        c = s.center();
        u = s.u();
        v = s.v();
        float r = s.radius() * s.scale();
        float spin = s.timeSec() * (1f + s.progress());

        // Rim: progress arc in school colour over a faint full ring
        color = argb(s.alpha() * 0.35f, s.color());
        ring(r, r * 0.035f, 0, TAU);
        color = argb(s.alpha(), s.color());
        ring(r, r * 0.05f, 0, TAU * Math.max(0.02f, s.progress()));
        if (s.rimOnly()) return;

        // Rarity rings in the rarity colour (outline only, §5.4)
        color = argb(s.alpha() * 0.8f, s.outlineColor());
        for (int i = 1; i < s.rarityLayers(); i++) {
            ring(r * (1f + 0.07f * i), r * 0.018f, 0, TAU);
        }

        for (Layer layer : s.layers()) {
            int base = layer.color().orElse(s.color());
            color = argb(s.alpha(), base);
            float lr = (float) layer.radius() * r;
            float angle = (float) Math.toRadians(layer.rotateSpeed()) * spin;
            switch (layer.type()) {
                case RING -> ring(lr, (float) layer.width() * r, 0, layer.progress() ? TAU * s.progress() : TAU);
                case POLYGON -> {
                    for (int k = 0; k < layer.count(); k++) {
                        polygon(lr, layer.sides(), angle + TAU * k / (layer.sides() * layer.count()), (float) layer.width() * r);
                    }
                }
                case GLYPH_RING -> glyphRing(s, layer, lr, angle, (float) layer.size() * r, base);
                case SATELLITE -> {
                    for (int k = 0; k < layer.count(); k++) {
                        float a = angle + TAU * k / layer.count();
                        diamond(lr * cos(a), lr * sin(a), (float) layer.size() * r);
                    }
                }
            }
        }

        if (s.flash() > 0) {
            color = argb(s.flash() * 0.6f, 0xFFFFFF);
            ring(r * 0.5f, r * 0.5f, 0, TAU);
        }
    }

    private void glyphRing(CircleRenderState s, Layer layer, float radius, float angle, float size, int base) {
        int n = layer.count();
        for (int i = 0; i < n; i++) {
            boolean lit = !layer.progress() || (i + 0.5f) / n <= s.progress();
            color = argb(s.alpha() * (lit ? 1f : 0.3f), base);
            float a = angle + TAU * i / n;
            glyph(Math.floorMod(s.glyphSeed() * 31 + i * 7919, 5), radius * cos(a), radius * sin(a), a, size);
        }
    }

    /** Small rune centred at (x, y), its "up" pointing away from the circle centre. */
    private void glyph(int shape, float x, float y, float outward, float s) {
        float ux = cos(outward), uy = sin(outward); // radial
        float tx = -uy, ty = ux;                    // tangent
        float w = s * 0.18f;
        switch (shape) {
            case 0 -> { // cross
                segment(x - ux * s, y - uy * s, x + ux * s, y + uy * s, w);
                segment(x - tx * s * 0.6f, y - ty * s * 0.6f, x + tx * s * 0.6f, y + ty * s * 0.6f, w);
            }
            case 1 -> { // triangle
                float ax = x + ux * s, ay = y + uy * s;
                float bx = x - ux * s * 0.6f + tx * s * 0.8f, by = y - uy * s * 0.6f + ty * s * 0.8f;
                float cx = x - ux * s * 0.6f - tx * s * 0.8f, cy = y - uy * s * 0.6f - ty * s * 0.8f;
                segment(ax, ay, bx, by, w);
                segment(bx, by, cx, cy, w);
                segment(cx, cy, ax, ay, w);
            }
            case 2 -> { // diamond outline
                float ax = x + ux * s, ay = y + uy * s, bx = x + tx * s * 0.6f, by = y + ty * s * 0.6f;
                float cx = x - ux * s, cy = y - uy * s, dx = x - tx * s * 0.6f, dy = y - ty * s * 0.6f;
                segment(ax, ay, bx, by, w);
                segment(bx, by, cx, cy, w);
                segment(cx, cy, dx, dy, w);
                segment(dx, dy, ax, ay, w);
            }
            case 3 -> { // arrow
                segment(x - ux * s, y - uy * s, x + ux * s, y + uy * s, w);
                segment(x + ux * s, y + uy * s, x + ux * s * 0.3f + tx * s * 0.5f, y + uy * s * 0.3f + ty * s * 0.5f, w);
                segment(x + ux * s, y + uy * s, x + ux * s * 0.3f - tx * s * 0.5f, y + uy * s * 0.3f - ty * s * 0.5f, w);
            }
            default -> { // fork
                segment(x - ux * s, y - uy * s, x, y, w);
                segment(x, y, x + ux * s + tx * s * 0.5f, y + uy * s + ty * s * 0.5f, w);
                segment(x, y, x + ux * s - tx * s * 0.5f, y + uy * s - ty * s * 0.5f, w);
            }
        }
    }

    private void ring(float radius, float width, float from, float to) {
        int segs = Math.max(12, (int) (48 * Math.abs(to - from) / TAU * Math.min(3f, 0.6f + radius)));
        float inner = radius - width / 2, outer = radius + width / 2;
        for (int i = 0; i < segs; i++) {
            float a0 = from + (to - from) * i / segs, a1 = from + (to - from) * (i + 1) / segs;
            quad(inner * cos(a0), inner * sin(a0), outer * cos(a0), outer * sin(a0),
                    outer * cos(a1), outer * sin(a1), inner * cos(a1), inner * sin(a1));
        }
    }

    private void polygon(float radius, int sides, float angle, float width) {
        for (int i = 0; i < sides; i++) {
            float a0 = angle + TAU * i / sides, a1 = angle + TAU * (i + 1) / sides;
            segment(radius * cos(a0), radius * sin(a0), radius * cos(a1), radius * sin(a1), width);
        }
    }

    private void diamond(float x, float y, float s) {
        quad(x + s, y, x, y + s, x - s, y, x, y - s);
    }

    private void segment(float x0, float y0, float x1, float y1, float width) {
        float dx = x1 - x0, dy = y1 - y0;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1.0E-5f) return;
        float nx = -dy / len * width / 2, ny = dx / len * width / 2;
        quad(x0 + nx, y0 + ny, x1 + nx, y1 + ny, x1 - nx, y1 - ny, x0 - nx, y0 - ny);
    }

    private void quad(float x0, float y0, float x1, float y1, float x2, float y2, float x3, float y3) {
        vertex(x0, y0);
        vertex(x1, y1);
        vertex(x2, y2);
        vertex(x3, y3);
    }

    private void vertex(float x, float y) {
        buf.addVertex(pose, (float) (c.x + u.x * x + v.x * y), (float) (c.y + u.y * x + v.y * y), (float) (c.z + u.z * x + v.z * y))
                .setColor(color);
    }

    private static int argb(float alpha, int rgb) {
        int a = Math.max(0, Math.min(255, (int) (alpha * 255)));
        return a << 24 | (rgb & 0xFFFFFF);
    }

    private static float cos(float a) {
        return (float) Math.cos(a);
    }

    private static float sin(float a) {
        return (float) Math.sin(a);
    }
}
