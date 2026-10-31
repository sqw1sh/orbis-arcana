package com.orbisarcana.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ManaMathTest {
    @Test
    void interruptLossScalesWithProgress() {
        assertEquals(1.0, ManaMath.interruptLoss(10, 0), 1e-9);
        assertEquals(5.0, ManaMath.interruptLoss(10, 1), 1e-9);
        assertEquals(3.0, ManaMath.interruptLoss(10, 0.5), 1e-9);
        assertEquals(5.0, ManaMath.interruptLoss(10, 7), 1e-9);
        assertEquals(1.0, ManaMath.blamelessLoss(10), 1e-9);
    }
}
