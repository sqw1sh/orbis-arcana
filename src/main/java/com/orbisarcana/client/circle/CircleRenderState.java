package com.orbisarcana.client.circle;

import java.util.List;

import com.orbisarcana.client.circle.CircleDefinition.Layer;

import net.minecraft.world.phys.Vec3;

/**
 * Per-frame snapshot of one circle, extracted before rendering. {@code center} is camera-relative,
 * {@code u}/{@code v} are unit axes of the circle plane.
 */
record CircleRenderState(Vec3 center, Vec3 u, Vec3 v, float radius, float scale, float alpha, float progress,
                         float timeSec, float flash, int color, int outlineColor, int rarityLayers,
                         List<Layer> layers, boolean rimOnly, int glyphSeed) {}
