package io.leaflet.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Throttle-gate tests (T07, REQ-0003 FR-2/AC-2): 10s of continuous movement
 * at the default 2s interval produces at most 6 writes; identical addresses
 * never write; the exit flush path bypasses this gate (asserted by the
 * ReaderViewModel/jvm contract — this gate is scroll-only).
 */
class PositionThrottlerTest {
    @Test
    fun continuousScrollingIsThrottled() {
        var now = 0L
        val gate = PositionThrottler(clock = { now }, intervalMs = 2_000L)
        var writes = 0
        // 10s of movement, one observation every 100ms.
        var t = 0L
        while (t <= 10_000L) {
            now = t
            if (gate.shouldSave(blockIndex = (t / 100).toInt(), blockOffset = 0)) {
                writes++
                gate.markSaved((t / 100).toInt(), 0)
            }
            t += 100
        }
        assertTrue("expected <= 6 writes, got $writes", writes <= 6)
        assertTrue("expected >= 4 writes, got $writes", writes >= 4)
    }

    @Test
    fun identicalAddressNeverWrites() {
        var now = 0L
        val gate = PositionThrottler(clock = { now }, intervalMs = 2_000L)
        assertTrue(gate.shouldSave(3, 0))
        gate.markSaved(3, 0)
        now = 60_000L // long after the interval …
        assertFalse(gate.shouldSave(3, 0)) // … but no movement means no write.
    }

    @Test
    fun restoreEchoIsSeededWithoutWrite() {
        var now = 0L
        val gate = PositionThrottler(clock = { now }, intervalMs = 2_000L)
        gate.markSaved(7, 0) // seed with the restored address after binding
        assertFalse(gate.shouldSave(7, 0))
        // Same-ms movement is still inside the save interval: no write yet.
        assertFalse(gate.shouldSave(8, 0))
        now = 2_001L
        assertTrue(gate.shouldSave(8, 0))
    }

    @Test
    fun firstObservationAlwaysWrites() {
        val gate = PositionThrottler(clock = { 1_000L }, intervalMs = 2_000L)
        assertTrue(gate.shouldSave(0, 0))
    }

    @Test
    fun defaultIntervalIsTwoSeconds() {
        assertEquals(2_000L, PositionThrottler.SAVE_INTERVAL_MS)
    }
}
