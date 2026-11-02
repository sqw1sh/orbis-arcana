package com.orbisarcana.api;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

/**
 * A school of magic (§10.1, §16), an entry of the {@code orbisarcana:school} registry. Addons register their own with a
 * {@code DeferredRegister} on {@link OrbisArcanaAPI#SCHOOL_REGISTRY}; the name is the lang key
 * {@code school.<namespace>.<path>}, the talent texts {@code talent.<namespace>.<path>.<node>}.
 *
 * @param color  primary colour of its circles and HUD (0xRRGGBB)
 * @param higher a higher school: its first spell needs a Seal of Comprehension, switching to it costs more
 * @param echo   its Echo, or null for none (§10.5)
 */
@ApiStatus.Experimental
public record School(int color, boolean higher, @Nullable SchoolEcho echo) {
    public School(int color, boolean higher) {
        this(color, higher, null);
    }
}
