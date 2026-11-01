package com.orbisarcana.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class XpRulesTest {
    @Test
    void attackWeightsPerTarget() {
        assertEquals(1.0, XpRules.attackWeight(0), 1e-9);
        assertEquals(0.25, XpRules.attackWeight(1), 1e-9);
        assertEquals(0.25, XpRules.attackWeight(4), 1e-9);
        assertEquals(0.0, XpRules.attackWeight(5), 1e-9);
        double total = 0;
        for (int i = 0; i < 10; i++) total += XpRules.attackWeight(i);
        assertEquals(2.0, total, 1e-9); // 1.0 + at most +1.0
    }

    @Test
    void healWeightCappedPerCast() {
        assertEquals(1.0, XpRules.healWeight(4, 20, 0), 1e-9);   // 20% of max HP
        assertEquals(2.0, XpRules.healWeight(20, 20, 0), 1e-9);  // capped at 2.0
        assertEquals(0.5, XpRules.healWeight(20, 20, 1.5), 1e-9);
        assertEquals(0.0, XpRules.healWeight(0, 20, 0), 1e-9);
    }

    @Test
    void mobilityAndAntiFarm() {
        assertEquals(0.0, XpRules.mobilityWeight(2.5, true), 1e-9);
        assertEquals(1.0, XpRules.mobilityWeight(8, true), 1e-9);
        assertEquals(0.3, XpRules.mobilityWeight(8, false), 1e-9);
        assertEquals(1.0, XpRules.sameTargetMult(19), 1e-9);
        assertEquals(0.5, XpRules.sameTargetMult(20), 1e-9);
    }
}
