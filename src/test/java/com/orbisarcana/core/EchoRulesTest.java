package com.orbisarcana.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class EchoRulesTest {
    @Test
    void tiersAndDissonance() {
        assertEquals(4.0, EchoRules.heatBurstDamage(1, false), 1e-9);
        assertEquals(6.0, EchoRules.heatBurstDamage(2, false), 1e-9);
        assertEquals(3.0, EchoRules.heatBurstDamage(2, true), 1e-9);
        assertEquals(1.0, EchoRules.stackGain(false), 1e-9);
        assertEquals(0.5, EchoRules.stackGain(true), 1e-9);
        assertEquals(3, EchoRules.flowNeeded(1));
        assertEquals(2, EchoRules.flowNeeded(2));
    }
}
