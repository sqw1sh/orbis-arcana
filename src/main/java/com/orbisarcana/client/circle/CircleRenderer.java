package com.orbisarcana.client.circle;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.orbisarcana.client.circle.CircleDefinition.Layer;

import net.minecraft.world.phys.Vec3;

/**
 * Volumetric magic circles (§9.5, roadmap V3), all textured from the circle atlas and drawn additive:
 * <ul>
 * <li>the school plate (its sacred geometry) and the spell's own layers from its circle JSON (its identity);</li>
 * <li>by rarity: a rune band, the ornament plate, a floating inner plate with a rune wall, filigree and orbiting
 * satellites, tilted armillary rings, an outer rune wall, a halo (common 1 ring … divine a full armillary sphere);</li>
 * <li>by mastery: a tick ring at 5, more satellites at 10, a gold crown at 15; the dominant seal under all.</li>
 * </ul>
 * Lines are glowing tubes: a flat strip plus a crossing fin along the normal, so they keep volume when seen edge-on.
 * The circle plane is given by a centre (camera-relative) and unit axes {@code u}, {@code v}; {@code n = u x v}.
 */
final class CircleRenderer {
    private static final float TAU = (float) (Math.PI * 2);
    private static final int GOLD = 0xFFC040;

    private final PoseStack.Pose pose;
    private final VertexConsumer buf;
    private Vec3 c = Vec3.ZERO, u = new Vec3(1, 0, 0), v = new Vec3(0, 0, 1), n = new Vec3(0, 1, 0);
    private int color;
    /** Overall brightness: shader packs draw the circle pipeline as emissive with bloom, so world circles are dimmed under them. */
    private float intensity = 1f;
    /** Segments per full ring, from the circle's size on screen. */
    private int lod = 48;

    CircleRenderer(PoseStack.Pose pose, VertexConsumer buf) {
        this.pose = pose;
        this.buf = buf;
    }

    CircleRenderer intensity(float intensity) {
        this.intensity = intensity;
        return this;
    }

    void draw(CircleRenderState s) {
        c = s.center();
        u = s.u();
        v = s.v();
        n = u.cross(v);
        if (n.lengthSqr() < 1.0E-6) return;
        n = n.normalize();
        float r = s.radius() * s.scale();
        if (r <= 0) return;
        double dist = c.length();
        lod = Math.max(12, Math.min(96, Math.round(r * 40f / (1f + (float) dist / 12f))));
        float spin = s.timeSec() * (1f + 2f * s.progress());
        float a = s.alpha() * intensity;
        if (s.broken()) a *= 0.55f + 0.45f * (float) Math.abs(Math.sin(s.timeSec() * 37.0));
        int school = s.color(), rarityColor = s.outlineColor();

        // rim: faint full ring and the charge arc in the school colour
        tube(r, r * 0.045f, 0, 0, TAU, argb(a * 0.35f, school));
        tube(r, r * 0.06f, 0, -TAU / 4, -TAU / 4 + TAU * Math.max(0.02f, s.progress()), argb(a, school));
        if (s.rimOnly()) return;

        int k = s.rarity();
        int step = 0;
        if (s.plate() >= 0) {
            plate(s.plate(), r * 0.97f, spin * 0.12f, 0, argb(a * 0.6f * stagger(s, step++), school));
        }
        drawLayers(s, r, spin, a * stagger(s, step++));

        if (k >= 1) band(CircleAtlas.RUNES_A, r * 1.05f, r * 1.17f, 0, -spin * 0.25f, a * 0.55f * stagger(s, step++), school, s.progress());
        if (k >= 2) plate(CircleAtlas.ORNAMENT, r * 1.34f, spin * 0.08f, -0.002f * r, argb(a * 0.35f * stagger(s, step++), rarityColor));
        if (k >= 3) {
            float st = stagger(s, step++);
            plate(s.plate() >= 0 ? s.plate() : CircleAtlas.ORNAMENT, r * 0.42f, spin * 0.6f, 0.24f * r, argb(a * 0.5f * st, school));
            wall(CircleAtlas.RUNES_B, r * 0.5f, 0.03f * r, 0.15f * r, -spin * 0.4f, argb(a * 0.4f * st, school));
        }
        int satellites = k >= 4 ? 4 : 0;
        if (k >= 4) band(CircleAtlas.FILIGREE, r * 1.36f, r * 1.44f, 0, spin * 0.18f, a * 0.45f * stagger(s, step++), rarityColor, 1f);
        if (k >= 5) armillary(r * 1.12f, (float) Math.toRadians(70), spin * 0.8f, 0, argb(a * 0.6f * stagger(s, step++), school));
        if (k >= 6) {
            float st = stagger(s, step++);
            armillary(r * 1.12f, (float) Math.toRadians(70), spin * 0.8f + TAU / 4, 0, argb(a * 0.6f * st, school));
            wall(CircleAtlas.RUNES_B, r * 1.2f, 0, 0.18f * r, spin * 0.15f, argb(a * 0.3f * st, rarityColor));
        }
        if (k >= 7) {
            float st = stagger(s, step++);
            armillary(r * 1.12f, (float) Math.toRadians(30), -spin * 0.6f, 0, argb(a * 0.6f * st, rarityColor));
            sprite(0, 0, 0.32f * r, r * 0.5f, CircleAtlas.FLARE_U0, argb(a * 0.25f * st, school));
        }

        // mastery (§7): tick ring at 5, more satellites at 10, a gold crown at 15
        if (s.mastery() >= 5) {
            float tr = k >= 6 ? r * 1.5f : r * 1.22f;
            band(CircleAtlas.TICKS, tr, tr + r * 0.05f, 0, -spin * 0.1f, a * 0.45f * stagger(s, step++), school, 1f);
        }
        if (s.mastery() >= 10) satellites = Math.max(satellites, 2) + 2;
        if (satellites > 0) {
            float st = stagger(s, step++);
            for (int i = 0; i < satellites; i++) {
                float ang = spin * 0.7f + TAU * i / satellites;
                float sr = r * (k >= 4 ? 1.52f : 1.2f);
                float h = 0.1f * r + 0.05f * r * (float) Math.sin(s.timeSec() * 2.2f + i);
                model(CircleModels.CRYSTAL, sr * cos(ang), sr * sin(ang), h, r * 0.24f, s.timeSec() * 1.5f + i, argb(a * 0.85f * st, school));
                sprite(sr * cos(ang), sr * sin(ang), h, r * 0.18f, CircleAtlas.FLARE_U0, argb(a * 0.3f * st, school));
            }
        }
        if (s.mastery() >= 15) {
            float st = stagger(s, step++);
            float gr = r * (k >= 4 ? 1.62f : 1.3f);
            band(CircleAtlas.FILIGREE, gr, gr + r * 0.07f, 0, -spin * 0.12f, a * 0.55f * st, GOLD, 1f);
            for (int i = 0; i < 4; i++) {
                float ang = TAU * i / 4 - spin * 0.12f;
                sprite(gr * 1.03f * cos(ang), gr * 1.03f * sin(ang), 0.02f * r, r * 0.14f, CircleAtlas.SPARKLE_U0, argb(a * st, GOLD));
            }
        }
        if (s.seal() >= 1) {
            plate(CircleAtlas.SEAL, r * (s.seal() >= 2 ? 1.95f : 1.8f), -spin * 0.05f, -0.004f * r, argb(a * 0.35f, school));
        }

        // release flash: a burst of light and a bright rim
        if (s.flash() > 0) {
            sprite(0, 0, 0.05f * r, r * (1.2f + 0.8f * (1f - s.flash())), CircleAtlas.FLARE_U0, argb(s.flash() * 0.6f * intensity, 0xFFFFFF));
            tube(r, r * 0.12f, 0, 0, TAU, argb(s.flash() * 0.5f * intensity, 0xFFFFFF));
        }
    }

    /** Elements appear one after another over the first ~0.5 s of the cast. */
    private static float stagger(CircleRenderState s, int i) {
        return Math.max(0f, Math.min(1f, (s.ageSec() - i * 0.05f) / 0.2f));
    }

    /** The spell's own layers (circle JSON): rings, polygons, glyph rings and satellites, as glowing tubes and runes. */
    private void drawLayers(CircleRenderState s, float r, float spin, float a) {
        for (Layer layer : s.layers()) {
            int base = layer.color().orElse(s.color());
            int col = argb(a, base);
            float lr = (float) layer.radius() * r;
            float angle = (float) Math.toRadians(layer.rotateSpeed()) * spin;
            float w = (float) layer.width() * r * 2.4f;
            switch (layer.type()) {
                case RING -> tube(lr, w, 0.004f * r, 0, layer.progress() ? TAU * s.progress() : TAU, col);
                case POLYGON -> {
                    for (int k = 0; k < layer.count(); k++) {
                        polygon(lr, layer.sides(), angle + TAU * k / (layer.sides() * layer.count()), w, 0.004f * r, col);
                    }
                }
                case GLYPH_RING -> {
                    int cnt = layer.count();
                    for (int i = 0; i < cnt; i++) {
                        boolean lit = !layer.progress() || (i + 0.5f) / cnt <= s.progress();
                        float ga = angle + TAU * i / cnt;
                        glyph(s.glyphSeed() * 31 + i * 7, lr * cos(ga), lr * sin(ga), ga, (float) layer.size() * r * 2.2f, 0.006f * r,
                                argb(a * (lit ? 1f : 0.3f), base));
                    }
                }
                case SATELLITE -> {
                    for (int k = 0; k < layer.count(); k++) {
                        float sa = angle + TAU * k / layer.count();
                        float size = (float) layer.size() * r * 2.6f;
                        sprite(lr * cos(sa), lr * sin(sa), 0.03f * r, size, CircleAtlas.SPARKLE_U0, col);
                    }
                }
            }
        }
    }

    // ---- elements ----

    /** A plate (square quad) of the atlas, centred, rotated by {@code angle}, lifted by {@code h} along the normal. */
    private void plate(int index, float radius, float angle, float h, int col) {
        if ((col >>> 24) == 0) return;
        color = col;
        float half = radius / CircleAtlas.PLATE_FILL;
        float u0 = CircleAtlas.plateU0(index), v0 = CircleAtlas.plateV0(index), sz = CircleAtlas.PLATE_SIZE;
        float cs = cos(angle) * half, sn = sin(angle) * half;
        vertex(-cs + sn, -sn - cs, h, u0, v0);
        vertex(cs + sn, sn - cs, h, u0 + sz, v0);
        vertex(cs - sn, sn + cs, h, u0 + sz, v0 + sz);
        vertex(-cs - sn, -sn + cs, h, u0, v0 + sz);
    }

    /**
     * A flat textured ring (annulus) between r0 and r1: consecutive tiles of the strip around it, as many as keep a tile
     * its own proportions; the part up to {@code lit} (0..1 of the turn) is bright, the rest dim.
     */
    private void band(CircleAtlas.Strip strip, float r0, float r1, float h, float angle, float a, int rgb, float lit) {
        int count = tiles(strip, (r0 + r1) / 2, r1 - r0);
        int per = Math.max(1, (int) Math.ceil((float) lod / count)), segs = count * per;
        for (int i = 0; i < segs; i++) {
            float t0 = (float) i / segs, t1 = (float) (i + 1) / segs;
            color = argb(a * ((t0 + t1) / 2 <= lit ? 1f : 0.35f), rgb);
            if ((color >>> 24) == 0) continue;
            float a0 = angle + TAU * t0, a1 = angle + TAU * t1;
            float[] uv = tileU(strip, i / per, i % per, per);
            quadUv(r0 * cos(a0), r0 * sin(a0), h, uv[0], strip.v1(),
                    r1 * cos(a0), r1 * sin(a0), h, uv[0], strip.v0(),
                    r1 * cos(a1), r1 * sin(a1), h, uv[1], strip.v0(),
                    r0 * cos(a1), r0 * sin(a1), h, uv[1], strip.v1());
        }
    }

    /** A cylindrical wall of the strip at radius r between heights h0 and h1 (runes standing around the circle). */
    private void wall(CircleAtlas.Strip strip, float r, float h0, float h1, float angle, int col) {
        if ((col >>> 24) == 0) return;
        color = col;
        int count = tiles(strip, r, h1 - h0);
        int per = Math.max(1, (int) Math.ceil((float) lod / count)), segs = count * per;
        for (int i = 0; i < segs; i++) {
            float a0 = angle + TAU * i / segs, a1 = angle + TAU * (i + 1) / segs;
            float[] uv = tileU(strip, i / per, i % per, per);
            quadUv(r * cos(a0), r * sin(a0), h0, uv[0], strip.v1(),
                    r * cos(a0), r * sin(a0), h1, uv[0], strip.v0(),
                    r * cos(a1), r * sin(a1), h1, uv[1], strip.v0(),
                    r * cos(a1), r * sin(a1), h0, uv[1], strip.v1());
        }
    }

    /** How many tiles of the strip fit around a circle of radius r when a tile is {@code height} tall. */
    private static int tiles(CircleAtlas.Strip strip, float r, float height) {
        float tileW = height * strip.aspect();
        return Math.max(4, Math.round(TAU * r / Math.max(1.0E-4f, tileW)));
    }

    /** u range of piece {@code k} (of {@code per}) of tile {@code tile}: tiles cycle through the strip, never across its end. */
    private static float[] tileU(CircleAtlas.Strip strip, int tile, int k, int per) {
        int t = strip.repeatsPerStrip();
        float base = (float) Math.floorMod(tile, t) / t;
        return new float[] {base + (float) k / per / t, base + (float) (k + 1) / per / t};
    }

    /** A glowing tube along the ring from angle {@code from} to {@code to}: a flat strip plus a fin along the normal. */
    private void tube(float radius, float width, float h, float from, float to, int col) {
        if ((col >>> 24) == 0 || width <= 0) return;
        color = col;
        float sweep = to - from;
        int segs = Math.max(6, (int) (lod * Math.abs(sweep) / TAU));
        float in = radius - width / 2, out = radius + width / 2, half = width / 2;
        float tv0 = CircleAtlas.TUBE.v0(), tv1 = CircleAtlas.TUBE.v1(), tu = CircleAtlas.TUBE_U;
        for (int i = 0; i < segs; i++) {
            float a0 = from + sweep * i / segs, a1 = from + sweep * (i + 1) / segs;
            float c0 = cos(a0), s0 = sin(a0), c1 = cos(a1), s1 = sin(a1);
            quadUv(in * c0, in * s0, h, tu, tv0, out * c0, out * s0, h, tu, tv1, out * c1, out * s1, h, tu, tv1, in * c1, in * s1, h, tu, tv0);
            quadUv(radius * c0, radius * s0, h - half, tu, tv0, radius * c0, radius * s0, h + half, tu, tv1,
                    radius * c1, radius * s1, h + half, tu, tv1, radius * c1, radius * s1, h - half, tu, tv0);
        }
    }

    /** Straight glowing line (flat strip + fin) between two points of the plane. */
    private void line(float x0, float y0, float x1, float y1, float width, float h, int col) {
        float dx = x1 - x0, dy = y1 - y0, len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1.0E-5f) return;
        color = col;
        float nx = -dy / len * width / 2, ny = dx / len * width / 2, half = width / 2;
        float tv0 = CircleAtlas.TUBE.v0(), tv1 = CircleAtlas.TUBE.v1(), tu = CircleAtlas.TUBE_U;
        quadUv(x0 + nx, y0 + ny, h, tu, tv0, x1 + nx, y1 + ny, h, tu, tv0, x1 - nx, y1 - ny, h, tu, tv1, x0 - nx, y0 - ny, h, tu, tv1);
        quadUv(x0, y0, h - half, tu, tv0, x1, y1, h - half, tu, tv0, x1, y1, h + half, tu, tv1, x0, y0, h + half, tu, tv1);
    }

    private void polygon(float radius, int sides, float angle, float width, float h, int col) {
        for (int i = 0; i < sides; i++) {
            float a0 = angle + TAU * i / sides, a1 = angle + TAU * (i + 1) / sides;
            line(radius * cos(a0), radius * sin(a0), radius * cos(a1), radius * sin(a1), width, h, col);
        }
    }

    /** One rune of the atlas, centred at (x, y), its top pointing away from the centre. */
    private void glyph(int index, float x, float y, float outward, float size, float h, int col) {
        if ((col >>> 24) == 0) return;
        color = col;
        float ux = cos(outward) * size / 2, uy = sin(outward) * size / 2;   // radial (glyph up)
        float tx = -uy, ty = ux;                                           // tangent (glyph right)
        float gu = CircleAtlas.glyphU0(index), gv = CircleAtlas.GLYPH_V0, gs = CircleAtlas.GLYPH_SIZE;
        vertex(x - tx + ux, y - ty + uy, h, gu, gv);
        vertex(x + tx + ux, y + ty + uy, h, gu + gs, gv);
        vertex(x + tx - ux, y + ty - uy, h, gu + gs, gv + gs);
        vertex(x - tx - ux, y - ty - uy, h, gu, gv + gs);
    }

    /** A light sprite as three crossed quads (plane, and two upright), so it glows from every side without a camera. */
    private void sprite(float x, float y, float h, float size, float spriteU0, int col) {
        if ((col >>> 24) == 0) return;
        color = col;
        float s = size / 2, u0 = spriteU0, v0 = CircleAtlas.FLARE_V0, sz = CircleAtlas.SPRITE_SIZE;
        quadUv(x - s, y - s, h, u0, v0, x + s, y - s, h, u0 + sz, v0, x + s, y + s, h, u0 + sz, v0 + sz, x - s, y + s, h, u0, v0 + sz);
        quadUv(x - s, y, h - s, u0, v0, x + s, y, h - s, u0 + sz, v0, x + s, y, h + s, u0 + sz, v0 + sz, x - s, y, h + s, u0, v0 + sz);
        quadUv(x, y - s, h - s, u0, v0, x, y + s, h - s, u0 + sz, v0, x, y + s, h + s, u0 + sz, v0 + sz, x, y - s, h + s, u0, v0 + sz);
    }

    /**
     * A ring tilted by {@code tilt} out of the circle plane and turned by {@code yaw} around the normal (one ring of an
     * armillary sphere), drawn as a tube in its own plane.
     */
    private void armillary(float radius, float tilt, float yaw, float h, int col) {
        if ((col >>> 24) == 0) return;
        color = col;
        // ring axes in plane coordinates (x along u, y along v, z along n)
        float ax = cos(yaw), ay = sin(yaw), az = 0;                       // stays in the plane
        float bx = -sin(yaw) * cos(tilt), by = cos(yaw) * cos(tilt), bz = sin(tilt);
        float width = radius * 0.05f, half = width / 2;
        float tv0 = CircleAtlas.TUBE.v0(), tv1 = CircleAtlas.TUBE.v1(), tu = CircleAtlas.TUBE_U;
        int segs = lod;
        for (int i = 0; i < segs; i++) {
            float a0 = TAU * i / segs, a1 = TAU * (i + 1) / segs;
            float c0 = cos(a0), s0 = sin(a0), c1 = cos(a1), s1 = sin(a1);
            // radial direction in the ring plane, and the ring plane normal for the fin
            float p0x = (ax * c0 + bx * s0), p0y = (ay * c0 + by * s0), p0z = (az * c0 + bz * s0);
            float p1x = (ax * c1 + bx * s1), p1y = (ay * c1 + by * s1), p1z = (az * c1 + bz * s1);
            float nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx;
            quadUv(p0x * (radius - half), p0y * (radius - half), h + p0z * (radius - half), tu, tv0,
                    p0x * (radius + half), p0y * (radius + half), h + p0z * (radius + half), tu, tv1,
                    p1x * (radius + half), p1y * (radius + half), h + p1z * (radius + half), tu, tv1,
                    p1x * (radius - half), p1y * (radius - half), h + p1z * (radius - half), tu, tv0);
            quadUv(p0x * radius - nx * half, p0y * radius - ny * half, h + p0z * radius - nz * half, tu, tv0,
                    p0x * radius + nx * half, p0y * radius + ny * half, h + p0z * radius + nz * half, tu, tv1,
                    p1x * radius + nx * half, p1y * radius + ny * half, h + p1z * radius + nz * half, tu, tv1,
                    p1x * radius - nx * half, p1y * radius - ny * half, h + p1z * radius - nz * half, tu, tv0);
        }
    }

    /**
     * A Blockbench ornament (CircleModels) standing at (x, y) of the plane, {@code size} tall, turned by {@code yaw}
     * around the normal; faces use the soft flare so facets read as glowing crystal, tops brighter than sides.
     */
    private void model(net.minecraft.resources.Identifier id, float x, float y, float h, float size, float yaw, int col) {
        CircleModels.Model m = CircleModels.get(id);
        if (m == null || (col >>> 24) == 0) return;
        float k = size / m.height(), cy = cos(yaw), sy = sin(yaw);
        float u0 = CircleAtlas.FLARE_U0, v0 = CircleAtlas.FLARE_V0, sz = CircleAtlas.SPRITE_SIZE;
        float[][] uv = {{u0, v0}, {u0 + sz, v0}, {u0 + sz, v0 + sz}, {u0, v0 + sz}};
        for (var cube : m.cubes()) {
            for (int f = 0; f < CircleModels.FACES.length; f++) {
                color = f == 2 ? col : argb(((col >>> 24) / 255f) * (f == 3 ? 0.45f : 0.75f), col);
                for (int corner = 0; corner < 4; corner++) {
                    int idx = CircleModels.FACES[f][corner] * 3;
                    // model x/z lie in the circle plane (turned by yaw), model y along the normal
                    float mx = cube.get(idx) * k, my = cube.get(idx + 1) * k, mz = cube.get(idx + 2) * k;
                    vertex(x + mx * cy - mz * sy, y + mx * sy + mz * cy, h + my, uv[corner][0], uv[corner][1]);
                }
            }
        }
    }

    // ---- vertices ----

    /** Segment count for a strip: a multiple of its tile count, close to the LOD. */
    private int segments(int tiles) {
        int per = Math.max(1, Math.round((float) lod / tiles));
        return tiles * per;
    }

    private void quadUv(float x0, float y0, float h0, float u0, float v0, float x1, float y1, float h1, float u1, float v1,
                        float x2, float y2, float h2, float u2, float v2, float x3, float y3, float h3, float u3, float v3) {
        vertex(x0, y0, h0, u0, v0);
        vertex(x1, y1, h1, u1, v1);
        vertex(x2, y2, h2, u2, v2);
        vertex(x3, y3, h3, u3, v3);
    }

    private void vertex(float x, float y, float h, float tu, float tv) {
        buf.addVertex(pose, (float) (c.x + u.x * x + v.x * y + n.x * h), (float) (c.y + u.y * x + v.y * y + n.y * h),
                        (float) (c.z + u.z * x + v.z * y + n.z * h))
                .setUv(tu, tv).setColor(color);
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
