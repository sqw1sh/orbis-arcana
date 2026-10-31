package com.orbisarcana.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.orbisarcana.core.LearnRules.Context;
import com.orbisarcana.core.LearnRules.Denial;

class LearnRulesTest {
    private static Optional<Denial> check(Rarity r, int mastery, int res, int peak) {
        return LearnRules.check(new Context(r, false, mastery, res, peak, false, false, true));
    }

    @Test
    void requirementsByRarity() {
        assertEquals(Optional.empty(), check(Rarity.RARE, 0, 0, 0));
        assertEquals(Optional.of(Denial.NEEDS_MASTERY_5), check(Rarity.EPIC, 4, 9, 9));
        assertEquals(Optional.empty(), check(Rarity.EPIC, 5, 0, 0));
        assertEquals(Optional.of(Denial.NEEDS_MASTERY_10_OR_RESONANCE_4), check(Rarity.LEGENDARY, 9, 3, 9));
        assertEquals(Optional.empty(), check(Rarity.LEGENDARY, 0, 4, 4));
        assertEquals(Optional.empty(), check(Rarity.LEGENDARY, 10, 0, 0));
        assertEquals(Optional.of(Denial.NEEDS_MASTERY_10_AND_RESONANCE_6), check(Rarity.MYTHIC, 10, 6, 5));
        assertEquals(Optional.empty(), check(Rarity.MYTHIC, 10, 0, 6)); // peak counts, current resonance may be lower
    }

    @Test
    void knownSealAndDisabled() {
        assertEquals(Optional.of(Denial.ALREADY_KNOWN), LearnRules.check(new Context(Rarity.COMMON, true, 0, 0, 0, false, false, false)));
        assertEquals(Optional.of(Denial.NEEDS_SEAL), LearnRules.check(new Context(Rarity.RARE, false, 0, 0, 0, true, false, true)));
        assertEquals(Optional.empty(), LearnRules.check(new Context(Rarity.RARE, false, 0, 0, 0, true, true, true)));
        assertEquals(Optional.empty(), LearnRules.check(new Context(Rarity.MYTHIC, false, 0, 0, 0, true, false, false)));
    }

    @Test
    void addXpLevelsUpAndCaps() {
        // Common: 10 XP for 1->2, 13 for 2->3
        var p = MasteryMath.addXp(1, 0, 25, Rarity.COMMON, 15);
        assertEquals(3, p.level());
        assertEquals(2.0, p.xp(), 1e-9);
        var capped = MasteryMath.addXp(8, 0, 10_000, Rarity.COMMON, LearnRules.DISSOLVE_MAX_LEVEL);
        assertEquals(9, capped.level());
        assertTrue(capped.xp() < MasteryMath.xpRequired(9, Rarity.COMMON));
        var max = MasteryMath.addXp(14, 0, 10_000, Rarity.COMMON, 15);
        assertEquals(15, max.level());
        assertEquals(0.0, max.xp(), 1e-9);
    }

    @Test
    void dissolveXp() {
        assertEquals(1.5, LearnRules.dissolveXp(1, Rarity.COMMON), 1e-9);
        assertEquals(0.0, LearnRules.dissolveXp(9, Rarity.COMMON), 1e-9);
    }
}
