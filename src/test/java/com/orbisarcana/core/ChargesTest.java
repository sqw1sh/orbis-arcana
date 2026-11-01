package com.orbisarcana.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ChargesTest {
    @Test
    void singleChargeIsAPlainCooldown() {
        long full = Charges.afterUse(0, 100, 40);
        assertEquals(140, full);
        assertEquals(140, Charges.readyAt(full, 1, 40));
        assertEquals(0, Charges.available(full, 120, 1, 40));
        assertEquals(1, Charges.available(full, 140, 1, 40));
    }

    @Test
    void twoChargesRechargeOneByOne() {
        long full = Charges.afterUse(0, 100, 40);      // used one at 100
        assertEquals(1, Charges.available(full, 100, 2, 40));
        assertTrue(Charges.readyAt(full, 2, 40) <= 100); // second still usable now
        full = Charges.afterUse(full, 100, 40);        // used the second
        assertEquals(180, full);
        assertEquals(0, Charges.available(full, 100, 2, 40));
        assertEquals(140, Charges.readyAt(full, 2, 40));
        assertEquals(1, Charges.available(full, 140, 2, 40));
        assertEquals(2, Charges.available(full, 180, 2, 40));
    }
}
