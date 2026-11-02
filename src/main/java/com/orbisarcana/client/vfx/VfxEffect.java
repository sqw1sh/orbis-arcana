package com.orbisarcana.client.vfx;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.orbisarcana.client.vfx.Molang.Expr;

import net.minecraft.resources.Identifier;

/**
 * A particle effect in the Bedrock/Snowstorm format, as Blockbench writes it (roadmap V4), compiled for the mod's own
 * VFX engine. Supported: emitter rate (instant, steady), lifetime (once, looping), shapes (point, sphere, disc, box)
 * with direction, initial speed and spin, particle lifetime, dynamic motion (acceleration, drag, rotation), billboard
 * size/facing/UV with flipbooks, tinting (fixed or gradient), {@code particles_add} / {@code particles_blend}, and
 * Molang initialisation of emitter and particle variables. Collision and events are not simulated (yet).
 */
final class VfxEffect {
    enum Shape { POINT, SPHERE, DISC, BOX }
    enum Facing { LOOKAT_XYZ, LOOKAT_Y, DIRECTION, EMITTER_XZ, EMITTER_XY }

    record Gradient(float[] stops, float[][] colors, Expr interpolant) {}

    final Identifier id;
    final Identifier texture;
    final boolean additive;

    // emitter
    final @Nullable Expr creation, perUpdate, particleCreation, particleUpdate;
    final @Nullable Expr instantCount;
    final @Nullable Expr steadyRate, steadyMax;
    final boolean looping;
    final Expr activeTime, sleepTime;
    final Shape shape;
    final Expr[] offset;
    final Expr radius;
    final Expr[] halfDims;
    final boolean surfaceOnly;
    final @Nullable String directionMode; // outwards / inwards, or null for a vector
    final Expr[] direction;
    final float[] planeNormal;
    final boolean localSpace;

    // particle
    final Expr speed, lifetime, spin, spinRate;
    final Expr[] acceleration;
    final Expr drag;
    final Expr[] size;
    final Facing facing;
    final float texW, texH;
    final Expr[] uv, uvSize;
    // flipbook (null base = plain uv)
    final @Nullable Expr[] fbBase;
    final float[] fbSize = new float[2], fbStep = new float[2];
    final float fbFps;
    final Expr fbMaxFrame;
    final boolean fbStretch, fbLoop;
    // tint
    final @Nullable Expr[] color;
    final @Nullable Gradient gradient;

    VfxEffect(Identifier id, JsonObject root) {
        this.id = id;
        JsonObject eff = root.has("particle_effect") ? root.getAsJsonObject("particle_effect") : root;
        JsonObject desc = obj(eff, "description");
        JsonObject brp = obj(desc, "basic_render_parameters");
        String material = str(brp, "material", "particles_add");
        additive = !material.equals("particles_blend") && !material.equals("particles_alpha") && !material.equals("particles_opaque");
        String tex = str(brp, "texture", "textures/vfx/sheet");
        texture = Identifier.fromNamespaceAndPath(id.getNamespace(), tex.endsWith(".png") ? tex : tex + ".png");

        JsonObject c = obj(eff, "components");
        JsonObject init = obj(c, "minecraft:emitter_initialization");
        creation = expr(init, "creation_expression");
        perUpdate = expr(init, "per_update_expression");
        JsonObject pinit = obj(c, "minecraft:particle_initialization");
        particleCreation = expr(pinit, "creation_expression");
        particleUpdate = expr(pinit, "per_update_expression");

        JsonObject instant = c.has("minecraft:emitter_rate_instant") ? obj(c, "minecraft:emitter_rate_instant") : null;
        instantCount = instant == null ? null : Molang.of(instant.get("num_particles"), 10);
        JsonObject steady = c.has("minecraft:emitter_rate_steady") ? obj(c, "minecraft:emitter_rate_steady") : null;
        steadyRate = steady == null ? null : Molang.of(steady.get("spawn_rate"), 1);
        steadyMax = steady == null ? null : Molang.of(steady.get("max_particles"), 50);

        if (c.has("minecraft:emitter_lifetime_looping")) {
            JsonObject l = obj(c, "minecraft:emitter_lifetime_looping");
            looping = true;
            activeTime = Molang.of(l.get("active_time"), 10);
            sleepTime = Molang.of(l.get("sleep_time"), 0);
        } else {
            JsonObject l = obj(c, "minecraft:emitter_lifetime_once");
            looping = false;
            activeTime = Molang.of(l.get("active_time"), instantCount != null ? 0.05 : 1);
            sleepTime = Molang.ZERO;
        }

        JsonObject shapeObj;
        if (c.has("minecraft:emitter_shape_sphere")) { shape = Shape.SPHERE; shapeObj = obj(c, "minecraft:emitter_shape_sphere"); }
        else if (c.has("minecraft:emitter_shape_disc")) { shape = Shape.DISC; shapeObj = obj(c, "minecraft:emitter_shape_disc"); }
        else if (c.has("minecraft:emitter_shape_box")) { shape = Shape.BOX; shapeObj = obj(c, "minecraft:emitter_shape_box"); }
        else if (c.has("minecraft:emitter_shape_entity_aabb")) { shape = Shape.BOX; shapeObj = obj(c, "minecraft:emitter_shape_entity_aabb"); }
        else { shape = Shape.POINT; shapeObj = obj(c, "minecraft:emitter_shape_point"); }
        offset = vec(shapeObj.get("offset"), 0);
        radius = Molang.of(shapeObj.get("radius"), 1);
        halfDims = vec(shapeObj.get("half_dimensions"), 0.5);
        surfaceOnly = shapeObj.has("surface_only") && shapeObj.get("surface_only").getAsBoolean();
        JsonElement dir = shapeObj.get("direction");
        if (dir != null && dir.isJsonPrimitive() && dir.getAsJsonPrimitive().isString()) {
            directionMode = dir.getAsString().toLowerCase(Locale.ROOT);
            direction = vec(null, 0);
        } else {
            directionMode = dir == null ? "outwards" : null;
            direction = vec(dir, 0);
        }
        float[] normal = {0, 1, 0};
        JsonElement pn = shapeObj.get("plane_normal");
        if (pn != null && pn.isJsonPrimitive()) {
            switch (pn.getAsString()) {
                case "x" -> normal = new float[] {1, 0, 0};
                case "z" -> normal = new float[] {0, 0, 1};
                default -> { }
            }
        } else if (pn != null && pn.isJsonArray()) {
            JsonArray a = pn.getAsJsonArray();
            normal = new float[] {a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat()};
        }
        planeNormal = normal;
        JsonObject local = obj(c, "minecraft:emitter_local_space");
        localSpace = local.has("position") && local.get("position").getAsBoolean();

        speed = Molang.of(c.get("minecraft:particle_initial_speed"), 0);
        JsonObject spinObj = obj(c, "minecraft:particle_initial_spin");
        spin = Molang.of(spinObj.get("rotation"), 0);
        spinRate = Molang.of(spinObj.get("rotation_rate"), 0);
        JsonObject life = obj(c, "minecraft:particle_lifetime_expression");
        lifetime = Molang.of(life.get("max_lifetime"), 1);
        JsonObject motion = obj(c, "minecraft:particle_motion_dynamic");
        acceleration = vec(motion.get("linear_acceleration"), 0);
        drag = Molang.of(motion.get("linear_drag_coefficient"), 0);

        JsonObject bb = obj(c, "minecraft:particle_appearance_billboard");
        size = vec2(bb.get("size"), 0.1);
        String facingMode = str(bb, "facing_camera_mode", "lookat_xyz");
        facing = switch (facingMode) {
            case "lookat_y", "rotate_y" -> Facing.LOOKAT_Y;
            case "lookat_direction", "direction_x", "direction_y", "direction_z" -> Facing.DIRECTION;
            case "emitter_transform_xz" -> Facing.EMITTER_XZ;
            case "emitter_transform_xy", "emitter_transform_yz" -> Facing.EMITTER_XY;
            default -> Facing.LOOKAT_XYZ;
        };
        JsonObject uvObj = obj(bb, "uv");
        texW = uvObj.has("texture_width") ? uvObj.get("texture_width").getAsFloat() : 512;
        texH = uvObj.has("texture_height") ? uvObj.get("texture_height").getAsFloat() : 512;
        uv = vec2(uvObj.get("uv"), 0);
        uvSize = vec2(uvObj.get("uv_size"), texW);
        if (uvObj.has("flipbook")) {
            JsonObject fb = uvObj.getAsJsonObject("flipbook");
            fbBase = vec2(fb.get("base_UV"), 0);
            float[] s = floats2(fb.get("size_UV")), st = floats2(fb.get("step_UV"));
            fbSize[0] = s[0];
            fbSize[1] = s[1];
            fbStep[0] = st[0];
            fbStep[1] = st[1];
            fbFps = fb.has("frames_per_second") ? fb.get("frames_per_second").getAsFloat() : 8;
            fbMaxFrame = Molang.of(fb.get("max_frame"), 1);
            fbStretch = fb.has("stretch_to_lifetime") && fb.get("stretch_to_lifetime").getAsBoolean();
            fbLoop = fb.has("loop") && fb.get("loop").getAsBoolean();
        } else {
            fbBase = null;
            fbFps = 0;
            fbMaxFrame = Molang.ZERO;
            fbStretch = false;
            fbLoop = false;
        }

        JsonObject tint = obj(c, "minecraft:particle_appearance_tinting");
        JsonElement col = tint.get("color");
        if (col != null && col.isJsonObject() && col.getAsJsonObject().has("gradient")) {
            JsonObject g = col.getAsJsonObject();
            JsonElement stopsEl = g.get("gradient");
            List<float[]> pairs = new ArrayList<>();
            if (stopsEl.isJsonObject()) {
                for (var e : stopsEl.getAsJsonObject().entrySet()) {
                    float[] rgba = color(e.getValue());
                    pairs.add(new float[] {Float.parseFloat(e.getKey()), rgba[0], rgba[1], rgba[2], rgba[3]});
                }
            } else {
                JsonArray arr = stopsEl.getAsJsonArray();
                for (int i = 0; i < arr.size(); i++) {
                    float[] rgba = color(arr.get(i));
                    pairs.add(new float[] {arr.size() == 1 ? 0 : (float) i / (arr.size() - 1), rgba[0], rgba[1], rgba[2], rgba[3]});
                }
            }
            pairs.sort((a, b) -> Float.compare(a[0], b[0]));
            float[] stops = new float[pairs.size()];
            float[][] colors = new float[pairs.size()][];
            for (int i = 0; i < pairs.size(); i++) {
                stops[i] = pairs.get(i)[0];
                colors[i] = new float[] {pairs.get(i)[1], pairs.get(i)[2], pairs.get(i)[3], pairs.get(i)[4]};
            }
            // Blockbench's default interpolant runs over the particle's life
            gradient = new Gradient(stops, colors, g.has("interpolant") ? Molang.of(g.get("interpolant"), 0) : Molang.compile("v.particle_age / v.particle_lifetime"));
            color = null;
        } else if (col != null && col.isJsonArray()) {
            JsonArray a = col.getAsJsonArray();
            color = new Expr[] {Molang.of(a.get(0), 1), Molang.of(a.get(1), 1), Molang.of(a.get(2), 1), a.size() > 3 ? Molang.of(a.get(3), 1) : Molang.constant(1)};
            gradient = null;
        } else if (col != null && col.isJsonPrimitive()) {
            float[] rgba = color(col);
            color = new Expr[] {Molang.constant(rgba[0]), Molang.constant(rgba[1]), Molang.constant(rgba[2]), Molang.constant(rgba[3])};
            gradient = null;
        } else {
            color = null;
            gradient = null;
        }
    }

    // ---- JSON helpers ----

    private static JsonObject obj(JsonObject o, String key) {
        return o != null && o.has(key) && o.get(key).isJsonObject() ? o.getAsJsonObject(key) : new JsonObject();
    }

    private static String str(JsonObject o, String key, String fallback) {
        return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : fallback;
    }

    private static @Nullable Expr expr(JsonObject o, String key) {
        return o.has(key) ? Molang.of(o.get(key), 0) : null;
    }

    private static Expr[] vec(@Nullable JsonElement e, double fallback) {
        if (e == null || !e.isJsonArray()) return new Expr[] {Molang.constant(fallback), Molang.constant(fallback), Molang.constant(fallback)};
        JsonArray a = e.getAsJsonArray();
        return new Expr[] {Molang.of(a.get(0), fallback), Molang.of(a.size() > 1 ? a.get(1) : null, fallback), Molang.of(a.size() > 2 ? a.get(2) : null, fallback)};
    }

    private static Expr[] vec2(@Nullable JsonElement e, double fallback) {
        if (e == null || !e.isJsonArray()) return new Expr[] {Molang.constant(fallback), Molang.constant(fallback)};
        JsonArray a = e.getAsJsonArray();
        return new Expr[] {Molang.of(a.get(0), fallback), Molang.of(a.size() > 1 ? a.get(1) : null, fallback)};
    }

    private static float[] floats2(@Nullable JsonElement e) {
        if (e == null || !e.isJsonArray()) return new float[] {0, 0};
        JsonArray a = e.getAsJsonArray();
        return new float[] {a.get(0).getAsFloat(), a.size() > 1 ? a.get(1).getAsFloat() : 0};
    }

    /** "#RRGGBB", "#AARRGGBB" (Bedrock puts alpha first in 8-digit colours) or [r, g, b, a] in 0..1. */
    private static float[] color(JsonElement e) {
        if (e.isJsonArray()) {
            JsonArray a = e.getAsJsonArray();
            return new float[] {a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat(), a.size() > 3 ? a.get(3).getAsFloat() : 1};
        }
        String s = e.getAsString().replace("#", "");
        long v = Long.parseLong(s, 16);
        if (s.length() == 8) return new float[] {(v >> 16 & 0xFF) / 255f, (v >> 8 & 0xFF) / 255f, (v & 0xFF) / 255f, (v >> 24 & 0xFF) / 255f};
        return new float[] {(v >> 16 & 0xFF) / 255f, (v >> 8 & 0xFF) / 255f, (v & 0xFF) / 255f, 1};
    }
}
