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

    @Test fun batteryThresholdZero() {
        assertTrue(RuleLogic.batteryBelow(0, 0))
        assertFalse(RuleLogic.batteryBelow(1, 0))
        assertTrue(RuleLogic.batteryAbove(0, 0))
    }

    @Test fun batteryThresholdHundred() {
        assertTrue(RuleLogic.batteryBelow(100, 100))
        assertTrue(RuleLogic.batteryAbove(100, 100))
        assertFalse(RuleLogic.batteryAbove(99, 100))
    }

    @Test fun batteryFirstRunUnknownPreviousFires() {
        assertTrue(
            RuleLogic.shouldFireBattery(
                prevPercent = -1, percent = 80, threshold = 90,
                below = true, latched = true, force = false,
            ),
        )
        assertTrue(
            RuleLogic.shouldFireBattery(
                prevPercent = -1, percent = 100, threshold = 100,
                below = false, latched = true, force = false,
            ),
        )
    }

    @Test fun batteryCrossesZeroThreshold() {
        assertTrue(
            RuleLogic.shouldFireBattery(
                prevPercent = 1, percent = 0, threshold = 0,
                below = true, latched = true, force = false,
            ),
        )
    }

    @Test fun batteryCrossesHundredThreshold() {
        assertTrue(
            RuleLogic.shouldFireBattery(
                prevPercent = 99, percent = 100, threshold = 100,
                below = false, latched = true, force = false,
            ),
        )
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

    @Test fun ssidBlankPatternOrCurrentDoesNotMatch() {
        assertFalse(RuleLogic.ssidMatches("Home", ""))
        assertFalse(RuleLogic.ssidMatches("Home", "   "))
        assertFalse(RuleLogic.ssidMatches(null, "Home"))
        assertFalse(RuleLogic.ssidMatches("", "Home"))
    }

    @Test fun ssidCaseInsensitive() {
        assertTrue(RuleLogic.ssidMatches("Home", "hOmE"))
        assertTrue(RuleLogic.ssidMatches("\"Home\"", "\"HOME\""))
    }

    @Test fun ssidQuotesAreTrimmed() {
        assertTrue(RuleLogic.ssidMatches("\"Home\"", "Home"))
        assertTrue(RuleLogic.ssidMatches("Home", "\"Home\""))
        assertTrue(RuleLogic.ssidMatches("\"Home\"", "\"home\""))
    }

    @Test fun ssidSpacesArePartOfTheName() {
        assertTrue(RuleLogic.ssidMatches("Home Office", "home office"))
        assertFalse(RuleLogic.ssidMatches(" Home", "Home"))
        assertFalse(RuleLogic.ssidMatches("Home ", "Home"))
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

    @Test fun timeWindowStartEqualsEndIsOneMinute() {
        // Bounds are inclusive, so start == end covers that single minute.
        assertTrue(RuleLogic.inTimeWindow(10 * 60, 10 * 60, 10 * 60))
        assertFalse(RuleLogic.inTimeWindow(10 * 60 + 1, 10 * 60, 10 * 60))
    }

    @Test fun timeWindowEndingAtMidnight() {
        assertTrue(RuleLogic.inTimeWindow(23 * 60 + 30, 23 * 60, 0))
        assertTrue(RuleLogic.inTimeWindow(0, 23 * 60, 0))
        assertFalse(RuleLogic.inTimeWindow(1, 23 * 60, 0))
    }

    @Test fun timeWindow23_59ToMidnight() {
        assertTrue(RuleLogic.inTimeWindow(23 * 60 + 59, 23 * 60 + 59, 0))
        assertTrue(RuleLogic.inTimeWindow(0, 23 * 60 + 59, 0))
        assertFalse(RuleLogic.inTimeWindow(1, 23 * 60 + 59, 0))
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

    @Test fun dayMaskZeroMatchesNoDay() {
        for (day in Calendar.SUNDAY..Calendar.SATURDAY) {
            assertFalse(RuleLogic.dayBitSet(0, day))
        }
    }

    @Test fun dayMaskAllDaysMatchesEveryDay() {
        for (day in Calendar.SUNDAY..Calendar.SATURDAY) {
            assertTrue(RuleLogic.dayBitSet(0b1111111, day))
        }
    }

    @Test fun dayMaskSundayBoundary() {
        assertTrue(RuleLogic.dayBitSet(0b0000001, Calendar.SUNDAY))
        assertFalse(RuleLogic.dayBitSet(0b0000001, Calendar.MONDAY))
    }

    @Test fun dayMaskSaturdayBoundary() {
        assertTrue(RuleLogic.dayBitSet(0b1000000, Calendar.SATURDAY))
        assertFalse(RuleLogic.dayBitSet(0b1000000, Calendar.SUNDAY))
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
