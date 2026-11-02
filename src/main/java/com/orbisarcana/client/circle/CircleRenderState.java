package com.orbisarcana.client.circle;

import java.util.List;

import com.orbisarcana.client.circle.CircleDefinition.Layer;

import net.minecraft.world.phys.Vec3;

/**
 * Per-frame snapshot of one circle, extracted before rendering. {@code center} is camera-relative,
 * {@code u}/{@code v} are unit axes of the circle plane; {@code rarity} is the ordinal, {@code plate} the atlas plate
 * (-1: none), {@code ageSec} the time since the cast began, {@code broken} an interrupted cast fading out.
 */
record CircleRenderState(Vec3 center, Vec3 u, Vec3 v, float radius, float scale, float alpha, float progress,
                         float timeSec, float flash, int color, int outlineColor, int rarity,
                         List<Layer> layers, boolean rimOnly, int glyphSeed,
                         int plate, int mastery, int seal, float ageSec, boolean broken) {
    /** A small circle without the volumetric composition (toggles, Echo marks): rim, plate if any, its own layers. */
    static CircleRenderState simple(Vec3 center, Vec3 u, Vec3 v, float radius, float alpha, float progress, float timeSec,
                                    int color, int plate, List<Layer> layers, boolean rimOnly, int glyphSeed) {
        return new CircleRenderState(center, u, v, radius, 1f, alpha, progress, timeSec, 0f, color, color, 0, layers, rimOnly,
                glyphSeed, plate, 1, 0, 10f, false);
    }
}
