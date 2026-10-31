package com.orbisarcana.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ManaRegenTest {
    @Test
    void combatAndCalmRates() {
        assertEquals(0.1, ManaRegen.perTick(0, 1.0), 1e-9);
        assertEquals(0.1, ManaRegen.perTick(99, 1.0), 1e-9);
        assertEquals(0.25, ManaRegen.perTick(100, 1.0), 1e-9);
        assertEquals(0.5, ManaRegen.perTick(1000, 2.0), 1e-9);
    }
}
