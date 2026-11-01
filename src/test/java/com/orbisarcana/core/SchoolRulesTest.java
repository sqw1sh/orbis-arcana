package com.orbisarcana.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SchoolRulesTest {
    @Test
    void rewardsByResonance() {
        assertEquals(0, SchoolRules.talentPoints(2));
        assertEquals(1, SchoolRules.talentPoints(3));
        assertEquals(3, SchoolRules.talentPoints(10));
        assertEquals(0, SchoolRules.echoTier(0));
        assertEquals(1, SchoolRules.echoTier(4));
        assertEquals(2, SchoolRules.echoTier(5));
        assertEquals(0, SchoolRules.resonanceXpRequired(10));
        assertTrue(SchoolRules.resonanceXpRequired(9) > SchoolRules.resonanceXpRequired(1));
    }

    @Test
    void powerAndCost() {
        assertEquals(1.10, SchoolRules.powerMult(true, false, 3, false), 1e-9);
        assertEquals(1.15, SchoolRules.powerMult(true, false, 10, false), 1e-9);
        assertEquals(1.10 * 0.85, SchoolRules.powerMult(true, false, 3, true), 1e-9);
        assertEquals(0.90, SchoolRules.powerMult(false, true, 0, true), 1e-9);
        assertEquals(1.0, SchoolRules.powerMult(false, true, 0, false), 1e-9);
        assertEquals(0.95, SchoolRules.costMult(true), 1e-9);
        assertEquals(0.25, SchoolRules.resonanceShare(true, true), 1e-9);
        assertEquals(0.1, SchoolRules.resonanceShare(false, false), 1e-9);
    }

    @Test
    void switchCost() {
        assertEquals(new SchoolRules.SwitchCost(4, 5), SchoolRules.switchCost(false, 0));
        assertEquals(new SchoolRules.SwitchCost(8, 5), SchoolRules.switchCost(true, 4));
        assertEquals(new SchoolRules.SwitchCost(2, 3), SchoolRules.switchCost(false, 5));
        assertTrue(SchoolRules.isFreeBinding(false));
        assertTrue(SchoolRules.canSwitchAt(100, 100));
    }
}
