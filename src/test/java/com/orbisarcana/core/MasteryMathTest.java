package com.orbisarcana.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MasteryMathTest {
    @Test
    void xpCurve() {
        assertEquals(10, MasteryMath.xpRequired(1, Rarity.COMMON));
        assertEquals(13, MasteryMath.xpRequired(2, Rarity.COMMON));
        assertEquals(18, MasteryMath.xpRequired(1, Rarity.EPIC));
        assertEquals(0, MasteryMath.xpRequired(15, Rarity.COMMON));
        assertTrue(MasteryMath.xpRequired(14, Rarity.DIVINE) > MasteryMath.xpRequired(14, Rarity.COMMON));
    }

    @Test
    void regularLevelsGiveThirtyThreePercent() {
        assertEquals(11, MasteryMath.regularLevels(15)); // levels 2-4, 6-9, 11-14 (see docs/DECISIONS.md D1)
        assertEquals(1.33, MasteryMath.primaryStatMult(15), 1e-9);
        assertEquals(1.0, MasteryMath.primaryStatMult(1), 1e-9);
        assertEquals(MasteryMath.primaryStatMult(4), MasteryMath.primaryStatMult(5), 1e-9);
    }

    @Test
    void costAndCastReduction() {
        assertEquals(1.0, MasteryMath.costMult(1), 1e-9);
        assertEquals(0.88, MasteryMath.costMult(15), 1e-9);
        assertEquals(0.90, MasteryMath.castTimeMult(15), 1e-9);
    }

    @Test
    void cycleWeightClamped() {
        assertEquals(1.0, MasteryMath.cycleWeight(0.2, 1.0), 1e-9);
        assertEquals(12.0, MasteryMath.cycleWeight(3, 60), 1e-9);
        assertEquals(2.0, MasteryMath.cycleWeight(1, 5), 1e-9);
    }
}
