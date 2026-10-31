package com.orbisarcana.compat.iris;

import java.lang.reflect.Method;

import org.jspecify.annotations.Nullable;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.orbisarcana.OrbisArcana;

/**
 * Soft Iris integration through its public API, via reflection so Iris stays an optional runtime-only mod.
 * Iris maps render pipelines to shader-pack programs by name and skips geometry of unknown pipelines,
 * so our circle pipeline is assigned the {@code BASIC} program (position + colour) explicitly.
 */
public final class IrisCompat {
    private static boolean initialized;
    private static @Nullable Object api;
    private static @Nullable Method isShaderPackInUse;
    private static boolean pipelineAssigned;

    public static boolean isLoaded() {
        if (!initialized) init();
        return api != null;
    }

    public static boolean isShaderPackInUse() {
        if (!isLoaded() || isShaderPackInUse == null) return false;
        try {
            return (boolean) isShaderPackInUse.invoke(api);
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    /** Whether our pipeline is known to Iris, i.e. circles render as meshes under shader packs. */
    public static boolean isPipelineAssigned() {
        return pipelineAssigned;
    }

    /** Tells Iris to draw {@code pipeline} with the shader pack's {@code BASIC} program. Safe without Iris. */
    public static void assignBasicProgram(RenderPipeline pipeline) {
        if (!isLoaded() || pipelineAssigned) return;
        try {
            Class<?> programClass = Class.forName("net.irisshaders.iris.api.v0.IrisProgram");
            Object basic = programClass.getMethod("valueOf", String.class).invoke(null, "BASIC");
            api.getClass().getMethod("assignPipeline", RenderPipeline.class, programClass).invoke(api, pipeline, basic);
            pipelineAssigned = true;
            OrbisArcana.LOGGER.info("Iris: magic circle pipeline assigned to the BASIC program");
        } catch (ReflectiveOperationException | LinkageError e) {
            OrbisArcana.LOGGER.warn("Iris: could not assign the magic circle pipeline, circles fall back to particles under shaders", e);
        }
    }

    private static void init() {
        initialized = true;
        try {
            Class<?> cls = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            api = cls.getMethod("getInstance").invoke(null);
            isShaderPackInUse = cls.getMethod("isShaderPackInUse");
            OrbisArcana.LOGGER.info("Iris detected");
        } catch (ReflectiveOperationException | LinkageError e) {
            api = null; // Iris not installed
        }
    }

    private IrisCompat() {}
}
