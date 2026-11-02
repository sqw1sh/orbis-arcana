package com.orbisarcana.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.orbisarcana.core.FocusRules.Hand;
import com.orbisarcana.core.FocusRules.Type;

class FocusRulesTest {
    @Test
    void tiersScale() {
        assertEquals(0.10, FocusRules.stats(Type.WAND, 1).castSpeed(), 1e-9);
        assertEquals(0.20, FocusRules.stats(Type.WAND, 3).castSpeed(), 1e-9);
        assertEquals(-0.10, FocusRules.stats(Type.WAND, 2).range(), 1e-9);
        assertEquals(0.25, FocusRules.stats(Type.STAFF, 3).power(), 1e-9);
        assertEquals(-0.15, FocusRules.stats(Type.TOME, 2).cost(), 1e-9);
        assertEquals(0.10, FocusRules.stats(Type.TOME, 1).masteryXp(), 1e-9);
    }

    @Test
    void hands() {
        assertTrue(FocusRules.works(Type.WAND, Hand.OFF, true));
        assertFalse(FocusRules.works(Type.TOME, Hand.MAIN, true));
        assertTrue(FocusRules.works(Type.STAFF, Hand.MAIN, true));
        assertFalse(FocusRules.works(Type.STAFF, Hand.MAIN, false), "the staff is two-handed");
        assertFalse(FocusRules.works(Type.STAFF, Hand.OFF, true));
    }
}
