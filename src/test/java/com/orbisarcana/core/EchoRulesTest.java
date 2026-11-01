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
        assertEquals(10.0, EchoRules.bulwarkDamage(20, false), 1e-9);
        assertEquals(5.0, EchoRules.bulwarkDamage(20, true), 1e-9);
        assertEquals(1.4, EchoRules.impulseMult(10, 1, false), 1e-9);
        assertEquals(1.6, EchoRules.impulseMult(20, 2, false), 1e-9);
        assertEquals(1.15, EchoRules.impulseMult(5, 2, true), 1e-9);
        assertEquals(1.0, EchoRules.impulseMult(0, 1, false), 1e-9);
        assertEquals(30, EchoRules.freezeTicks(1));
        assertEquals(40, EchoRules.freezeTicks(2));
        assertEquals(3.0, EchoRules.conductDamage(1, false), 1e-9);
        assertEquals(2.25, EchoRules.conductDamage(2, true), 1e-9);
        assertEquals(2.0, EchoRules.sproutPower(1, false), 1e-9);
        assertEquals(1.5, EchoRules.sproutPower(2, true), 1e-9);
        assertEquals(4.0, EchoRules.retributionDamage(1, false), 1e-9);
        assertEquals(3.0, EchoRules.retributionDamage(2, true), 1e-9);
        assertEquals(2.0, EchoRules.duskDamage(1, false), 1e-9);
        assertEquals(1.5, EchoRules.duskDamage(2, true), 1e-9);
        assertEquals(5.0, EchoRules.duskMana(false, true), 1e-9);
        assertEquals(1.5, EchoRules.duskMana(true, false), 1e-9);
        assertEquals(4.0, EchoRules.pulseDamage(1, false), 1e-9);
        assertEquals(3.0, EchoRules.pulseDamage(2, true), 1e-9);
    }
}
