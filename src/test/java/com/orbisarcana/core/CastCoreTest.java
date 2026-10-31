package com.orbisarcana.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.orbisarcana.core.ActiveCast.Outcome;

class CastCoreTest {
    private static CastRules.Request ok() {
        return new CastRules.Request(true, true, true, false, 100, 0, 0, 50, 10);
    }

    @Test
    void rulesInPriorityOrder() {
        assertEquals(Optional.empty(), CastRules.check(ok()));
        assertEquals(Optional.of(CastDenial.NOT_LEARNED), CastRules.check(new CastRules.Request(true, true, false, true, 100, 0, 0, 0, 10)));
        assertEquals(Optional.of(CastDenial.ALREADY_CASTING), CastRules.check(new CastRules.Request(true, true, true, true, 100, 0, 0, 50, 10)));
        assertEquals(Optional.of(CastDenial.GLOBAL_COOLDOWN), CastRules.check(new CastRules.Request(true, true, true, false, 100, 101, 0, 50, 10)));
        assertEquals(Optional.of(CastDenial.COOLDOWN), CastRules.check(new CastRules.Request(true, true, true, false, 100, 0, 140, 50, 10)));
        assertEquals(Optional.of(CastDenial.NOT_ENOUGH_MANA), CastRules.check(new CastRules.Request(true, true, true, false, 100, 0, 100, 9.9, 10)));
    }

    @Test
    void chargedCastLifecycle() {
        ActiveCast c = new ActiveCast(CastMode.CHARGED, 1000, 14, 8, 10);
        assertFalse(c.canRelease(1007));
        assertTrue(c.canRelease(1008));
        assertEquals(0.5, c.progress(1007), 1e-9);
        assertEquals(8 / 14.0, c.power(1008), 1e-9);
        assertTrue(c.isFullyCharged(1014));
        assertEquals(1.0, c.power(1020), 1e-9);
    }

    @Test
    void manaSpentPerOutcome() {
        ActiveCast c = new ActiveCast(CastMode.CHARGED, 0, 10, 5, 10);
        assertEquals(10.0, c.manaSpent(Outcome.RELEASED, 10), 1e-9);
        assertEquals(1.0, c.manaSpent(Outcome.CANCELLED, 0), 1e-9);
        assertEquals(3.0, c.manaSpent(Outcome.INTERRUPTED, 5), 1e-9);
        assertEquals(1.0, c.manaSpent(Outcome.LOST_TARGET, 9), 1e-9);
    }

    @Test
    void instantIsAlwaysReady() {
        ActiveCast c = new ActiveCast(CastMode.INSTANT, 50, 0, 0, 12);
        assertTrue(c.canRelease(50));
        assertEquals(1.0, c.progress(50), 1e-9);
        assertEquals(1.0, c.power(50), 1e-9);
    }
}
