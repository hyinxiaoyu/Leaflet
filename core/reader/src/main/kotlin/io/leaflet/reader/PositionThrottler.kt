package io.leaflet.reader

/**
 * Throttled position-save gate (T07, REQ-0003 FR-2).
 *
 * Pure decision logic so the "write at most once per interval, only on
 * real movement" rule is JVM-testable with a fake clock: scrolling for 10s
 * at the default 2s interval produces at most 6 writes (AC-2), and the
 * exit/pause flush path bypasses this gate entirely (it always writes).
 *
 * Protocol: the owner feeds every observed block address into [shouldSave];
 * on `true` it persists, then calls [markSaved]. Seeding via [markSaved]
 * (e.g. with the restored address right after binding) suppresses the
 * restore-scroll echo without a database write.
 */
class PositionThrottler(
    private val clock: () -> Long = System::currentTimeMillis,
    private val intervalMs: Long = SAVE_INTERVAL_MS,
) {
    private var lastSaveAt: Long? = null
    private var lastBlock: Int = -1
    private var lastOffset: Int = -1

    fun shouldSave(
        blockIndex: Int,
        blockOffset: Int,
    ): Boolean {
        if (blockIndex == lastBlock && blockOffset == lastOffset) return false
        val at = lastSaveAt
        if (at != null && clock() - at < intervalMs) return false
        return true
    }

    fun markSaved(
        blockIndex: Int,
        blockOffset: Int,
    ) {
        lastSaveAt = clock()
        lastBlock = blockIndex
        lastOffset = blockOffset
    }

    fun reset() {
        lastSaveAt = null
        lastBlock = -1
        lastOffset = -1
    }

    companion object {
        /**
         * Save interval, initial value 2s (REQ-0003 FR-2/AC-2, Open-Questions
         * initial value). UNVERIFIED: T13 baselines may re-tune; centralized here.
         */
        const val SAVE_INTERVAL_MS: Long = 2_000L
    }
}
