package com.ishaara.mobile.core

import org.junit.Assert.assertEquals
import org.junit.Test

class StuckDetectorTest {
    @Test
    fun firstOccurrenceIsFine() {
        assertEquals(StuckSignal.OK, StuckDetector().record("s", "tap1"))
    }

    @Test
    fun secondOccurrenceHints() {
        val d = StuckDetector()
        d.record("s", "tap1")
        assertEquals(StuckSignal.HINT, d.record("s", "tap1"))
    }

    @Test
    fun thirdOccurrenceStops() {
        val d = StuckDetector()
        d.record("s", "tap1")
        d.record("s", "tap1")
        assertEquals(StuckSignal.STOP, d.record("s", "tap1"))
    }

    @Test
    fun changeOfScreenOrActionResets() {
        val d = StuckDetector()
        d.record("s", "tap1")
        assertEquals(StuckSignal.OK, d.record("s2", "tap1"))
        assertEquals(StuckSignal.OK, d.record("s2", "tap2"))
    }
}
