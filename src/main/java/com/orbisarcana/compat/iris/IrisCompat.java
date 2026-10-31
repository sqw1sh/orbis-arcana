package com.orbisarcana.compat.iris;

import java.lang.reflect.Method;

import org.jspecify.annotations.Nullable;

import com.orbisarcana.OrbisArcana;

/**
 * Soft Iris integration through its public API, via reflection so Iris stays an optional runtime-only mod.
 * Shader packs skip geometry from pipelines they do not know, so circles need a fallback while one is active.
 */
public final class IrisCompat {
    private static boolean initialized;
    private static @Nullable Object api;
    private static @Nullable Method isShaderPackInUse;

    public static boolean isShaderPackInUse() {
        if (!initialized) init();
        if (api == null || isShaderPackInUse == null) return false;
        try {
            return (boolean) isShaderPackInUse.invoke(api);
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    private static void init() {
        initialized = true;
        try {
            Class<?> cls = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            api = cls.getMethod("getInstance").invoke(null);
            isShaderPackInUse = cls.getMethod("isShaderPackInUse");
            OrbisArcana.LOGGER.info("Iris detected: magic circles switch to the shader-safe mode while a shader pack is on");
        } catch (ReflectiveOperationException | LinkageError e) {
            api = null; // Iris not installed
        }
    }

    private IrisCompat() {}
}
