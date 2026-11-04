package com.orbisarcana.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.orbisarcana.core.TalentRules.Denial;

class TalentRulesTest {
    @Test
    void threePointsForFourNodes() {
        assertEquals(Optional.of(Denial.NO_POINTS), TalentRules.check(true, "a1", Set.of(), 2));
        assertEquals(Optional.empty(), TalentRules.check(true, "a1", Set.of(), 3));
        assertEquals(Optional.of(Denial.NEEDS_FIRST_NODE), TalentRules.check(true, "a2", Set.of(), 6));
        assertEquals(Optional.empty(), TalentRules.check(true, "a2", Set.of("a1"), 6));
        assertEquals(Optional.empty(), TalentRules.check(true, "b1", Set.of("a1", "a2"), 9));
        // third nodes need the second; a whole deep branch takes all three points
        assertEquals(Optional.of(Denial.NEEDS_FIRST_NODE), TalentRules.check(true, "a3", Set.of("a1"), 9));
        assertEquals(Optional.empty(), TalentRules.check(true, "a3", Set.of("a1", "a2"), 9));
        assertEquals(Optional.of(Denial.NO_POINTS), TalentRules.check(true, "b1", Set.of("a1", "a2", "a3"), 10));
        assertEquals(Optional.of(Denial.NO_POINTS), TalentRules.check(true, "b2", Set.of("a1", "a2", "b1"), 10));
    }

    @Test
    void otherDenials() {
        assertEquals(Optional.of(Denial.NOT_DOMINANT), TalentRules.check(false, "a1", Set.of(), 9));
        assertEquals(Optional.of(Denial.ALREADY_PICKED), TalentRules.check(true, "a1", Set.of("a1"), 9));
        assertEquals(Optional.of(Denial.UNKNOWN_NODE), TalentRules.check(true, "root", Set.of(), 9));
        assertEquals(1, TalentRules.pointsLeft(6, Set.of("a1")));
    }
}
