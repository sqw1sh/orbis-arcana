package com.orbisarcana.core;

import java.util.Optional;

/** Server-side checks before a cast starts (§8.2). Targeting/range checks happen in the behavior. */
public final class CastRules {
    /** Global pause between casts: 0.25 s. */
    public static final int GCD_TICKS = 5;

    public record Request(boolean exists, boolean enabled, boolean known, boolean casting,
                          long now, long gcdUntil, long cooldownUntil, double mana, double cost) {}

    public static Optional<CastDenial> check(Request r) {
        if (!r.exists()) return Optional.of(CastDenial.UNKNOWN_SPELL);
        if (!r.enabled()) return Optional.of(CastDenial.DISABLED);
        if (!r.known()) return Optional.of(CastDenial.NOT_LEARNED);
        if (r.casting()) return Optional.of(CastDenial.ALREADY_CASTING);
        if (r.now() < r.gcdUntil()) return Optional.of(CastDenial.GLOBAL_COOLDOWN);
        if (r.now() < r.cooldownUntil()) return Optional.of(CastDenial.COOLDOWN);
        if (r.mana() < r.cost()) return Optional.of(CastDenial.NOT_ENOUGH_MANA);
        return Optional.empty();
    }

    private CastRules() {}
}
