package com.orbisarcana.client.vfx;

import java.util.Locale;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.resources.Identifier;

/**
 * A volumetric VFX effect (roadmap V8): a textured 3D shape that grows, turns and fades over its life — a sphere (cores,
 * blasts, bubbles), a cylinder or cone (beams, columns, jets), a flat ring (shockwaves), a spiral ribbon (vortices) or a
 * ribbon trail behind an entity. The texture (an energy strip from tools/vfx-energy.cjs) repeats and scrolls over the
 * surface; a fresnel term fades the silhouette (cores) or the facing part (rims, shells). Loaded from the same folder
 * as the particle effects, by the key {@code "orbisarcana:mesh"}; the server plays it like any effect.
 *
 * <pre>{ "orbisarcana:mesh": { "shape": "sphere|cylinder|cone|ring|spiral|trail", "texture": "textures/vfx/energy/flame",
 *   "additive": true, "life": s, "loop": false, "axis": "up|forward", "stretch": false, "segments": n, "rings": n,
 *   "radius": curve, "radius_top": curve, "height": curve, "thickness": curve, "turns": n, "arms": n, "samples": n,
 *   "uv": [u, v], "scroll": [u/s, v/s], "spin": deg/s, "fresnel": f (&gt; 0 rim, &lt; 0 core), "color": [[t, [r, g, b, a]], ...],
 *   "shake": { "strength": deg, "radius": blocks, "time": s } } }</pre>
 * A curve is a number or [[t, value], ...] over the life (t 0..1), linear between keys.
 */
final class VfxMesh {
    enum Shape { SPHERE, CYLINDER, CONE, RING, SPIRAL, TRAIL }

    /** Piecewise-linear curve over the life. */
    record Curve(float[] t, float[] v) {
        float at(float x) {
            if (x <= t[0]) return v[0];
            for (int i = 1; i < t.length; i++) {
                if (x <= t[i]) return v[i - 1] + (v[i] - v[i - 1]) * (x - t[i - 1]) / Math.max(1.0E-6f, t[i] - t[i - 1]);
            }
            return v[v.length - 1];
        }

        static Curve of(JsonElement e, float fallback) {
            if (e == null) return new Curve(new float[] {0}, new float[] {fallback});
            if (e.isJsonPrimitive()) return new Curve(new float[] {0}, new float[] {e.getAsFloat()});
            JsonArray a = e.getAsJsonArray();
            float[] t = new float[a.size()], v = new float[a.size()];
            for (int i = 0; i < a.size(); i++) {
                t[i] = a.get(i).getAsJsonArray().get(0).getAsFloat();
                v[i] = a.get(i).getAsJsonArray().get(1).getAsFloat();
            }
            return new Curve(t, v);
        }
    }

    final Identifier id;
    final Shape shape;
    final Identifier texture;
    /** stretch: the scale lengthens the shape along its axis only (beams scaled to their length keep their girth). */
    final boolean additive, loop, alongForward, stretch;
    final float life, spin, fresnel, uScale, vScale, uScroll, vScroll, turns;
    final int segments, rings, arms, samples;
    final Curve radius, radiusTop, height, thickness, width;
    final float[] colorT;
    final float[][] colors;
    final float shakeStrength, shakeRadius, shakeTime;

    VfxMesh(Identifier id, JsonObject root) {
        this.id = id;
        JsonObject m = root.getAsJsonObject("orbisarcana:mesh");
        shape = Shape.valueOf(m.get("shape").getAsString().toUpperCase(Locale.ROOT));
        String tex = m.has("texture") ? m.get("texture").getAsString() : "textures/vfx/energy/energy";
        texture = Identifier.fromNamespaceAndPath(id.getNamespace(), tex.endsWith(".png") ? tex : tex + ".png");
        additive = !m.has("additive") || m.get("additive").getAsBoolean();
        loop = m.has("loop") && m.get("loop").getAsBoolean();
        alongForward = m.has("axis") && m.get("axis").getAsString().equals("forward");
        stretch = m.has("stretch") && m.get("stretch").getAsBoolean();
        life = num(m, "life", 1);
        spin = num(m, "spin", 0);
        fresnel = num(m, "fresnel", 0);
        turns = num(m, "turns", 1.5f);
        segments = (int) num(m, "segments", 24);
        rings = (int) num(m, "rings", 12);
        arms = (int) num(m, "arms", 2);
        samples = (int) num(m, "samples", 10);
        float[] uv = pair(m, "uv", 1, 1), scroll = pair(m, "scroll", 0, 0);
        uScale = uv[0];
        vScale = uv[1];
        uScroll = scroll[0];
        vScroll = scroll[1];
        radius = Curve.of(m.get("radius"), 1);
        radiusTop = m.has("radius_top") ? Curve.of(m.get("radius_top"), 1) : radius;
        height = Curve.of(m.get("height"), 1);
        thickness = Curve.of(m.get("thickness"), 0.3f);
        width = Curve.of(m.get("width"), 0.5f);
        JsonArray c = m.has("color") ? m.getAsJsonArray("color") : null;
        int n = c == null ? 1 : c.size();
        colorT = new float[n];
        colors = new float[n][];
        for (int i = 0; i < n; i++) {
            if (c == null) {
                colors[i] = new float[] {1, 1, 1, 1};
                continue;
            }
            JsonArray key = c.get(i).getAsJsonArray(), rgba = key.get(1).getAsJsonArray();
            colorT[i] = key.get(0).getAsFloat();
            colors[i] = new float[] {rgba.get(0).getAsFloat(), rgba.get(1).getAsFloat(), rgba.get(2).getAsFloat(), rgba.size() > 3 ? rgba.get(3).getAsFloat() : 1};
        }
        JsonObject shake = m.has("shake") ? m.getAsJsonObject("shake") : null;
        shakeStrength = shake == null ? 0 : num(shake, "strength", 0);
        shakeRadius = shake == null ? 0 : num(shake, "radius", 16);
        shakeTime = shake == null ? 0 : num(shake, "time", 0.4f);
    }

    /** Colour (r, g, b, a) at life fraction {@code t}. */
    void color(float t, float[] out) {
        if (t <= colorT[0] || colors.length == 1) {
            System.arraycopy(colors[0], 0, out, 0, 4);
            return;
        }
        for (int i = 1; i < colors.length; i++) {
            if (t <= colorT[i]) {
                float k = (t - colorT[i - 1]) / Math.max(1.0E-6f, colorT[i] - colorT[i - 1]);
                for (int j = 0; j < 4; j++) out[j] = colors[i - 1][j] + (colors[i][j] - colors[i - 1][j]) * k;
                return;
            }
        }
        System.arraycopy(colors[colors.length - 1], 0, out, 0, 4);
    }

    static boolean isMesh(JsonObject root) {
        return root.has("orbisarcana:mesh");
    }

    private static float num(JsonObject o, String key, float fallback) {
        return o.has(key) ? o.get(key).getAsFloat() : fallback;
    }

    private static float[] pair(JsonObject o, String key, float a, float b) {
        if (!o.has(key)) return new float[] {a, b};
        JsonArray arr = o.getAsJsonArray(key);
        return new float[] {arr.get(0).getAsFloat(), arr.get(1).getAsFloat()};
    }
}
