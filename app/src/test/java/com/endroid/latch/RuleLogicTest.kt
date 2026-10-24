package com.endroid.latch

import com.endroid.latch.engine.RuleLogic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class RuleLogicTest {
    @Test fun batteryBelow() {
        assertTrue(RuleLogic.batteryBelow(50, 98))
        assertTrue(RuleLogic.batteryBelow(98, 98))
        assertFalse(RuleLogic.batteryBelow(99, 98))
    }

    @Test fun batteryAbove() {
        assertTrue(RuleLogic.batteryAbove(80, 20))
        assertFalse(RuleLogic.batteryAbove(10, 20))
    }

    @Test fun fireOnForceEvenIfLatched() {
        assertTrue(
            RuleLogic.shouldFireBattery(
                prevPercent = 50, percent = 50, threshold = 98,
                below = true, latched = true, force = true,
            ),
        )
    }

    @Test fun fireWhenCrossesBelow() {
        assertTrue(
            RuleLogic.shouldFireBattery(
                prevPercent = 99, percent = 97, threshold = 98,
                below = true, latched = true, force = false,
            ),
        )
    }

    @Test fun noFireWhileStillBelowAndLatched() {
        assertFalse(
            RuleLogic.shouldFireBattery(
                prevPercent = 50, percent = 49, threshold = 98,
                below = true, latched = true, force = false,
            ),
        )
    }

    @Test fun fireFirstTimeNotLatched() {
        assertTrue(
            RuleLogic.shouldFireBattery(
                prevPercent = 50, percent = 50, threshold = 98,
                below = true, latched = false, force = false,
            ),
        )
    }

    @Test fun ssid() {
        assertTrue(RuleLogic.ssidMatches("\"Home\"", "Home"))
        assertTrue(RuleLogic.ssidMatches("Home", "home"))
        assertFalse(RuleLogic.ssidMatches(null, "Home"))
    }

    @Test fun timeWindowNormal() {
        assertTrue(RuleLogic.inTimeWindow(10 * 60, 9 * 60, 12 * 60))
        assertFalse(RuleLogic.inTimeWindow(8 * 60, 9 * 60, 12 * 60))
    }

    @Test fun timeWindowWrap() {
        assertTrue(RuleLogic.inTimeWindow(23 * 60, 22 * 60, 2 * 60))
        assertTrue(RuleLogic.inTimeWindow(1 * 60, 22 * 60, 2 * 60))
        assertFalse(RuleLogic.inTimeWindow(12 * 60, 22 * 60, 2 * 60))
    }

    @Test fun dayBits() {
        assertTrue(RuleLogic.dayBitSet(0b1111111, Calendar.SUNDAY))
        assertTrue(RuleLogic.dayBitSet(0b0000010, Calendar.MONDAY))
        assertFalse(RuleLogic.dayBitSet(0b0000010, Calendar.SUNDAY))
    }

    @Test fun brightnessRaw() {
        assertEquals(1, RuleLogic.brightnessRaw(0))
        assertEquals(255, RuleLogic.brightnessRaw(100))
        assertEquals(76, RuleLogic.brightnessRaw(30))
    }

    @Test fun ssidPartialContains() {
        // current RuleLogic is equality-ish; document behavior
        assertFalse(RuleLogic.ssidMatches("Office-Guest", "Office"))
    }

    @Test fun dayBitsSaturday() {
        assertTrue(RuleLogic.dayBitSet(0b1111111, Calendar.SATURDAY))
    }

    @Test fun cooldownAllowsWhenDisabled() {
        assertTrue(RuleLogic.cooldownOk(0, 1000L, 1000L))
        assertTrue(RuleLogic.cooldownOk(-5, 1000L, 1000L))
    }

    @Test fun cooldownBlocksInsideWindow() {
        assertFalse(RuleLogic.cooldownOk(60, 0L, 59_000L))
        assertTrue(RuleLogic.cooldownOk(60, 0L, 60_000L))
        assertTrue(RuleLogic.cooldownOk(60, 0L, 61_000L))
    }

    @Test fun cooldownEdgeExactlyOnBoundary() {
        assertFalse(RuleLogic.cooldownOk(1, 1000L, 1999L))
        assertTrue(RuleLogic.cooldownOk(1, 1000L, 2000L))
    }
}
