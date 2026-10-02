package io.leaflet.reader

/**
 * Reading mode (T08, REQ-0005 FR-4).
 *
 * Stored string values live in `SettingsStore.readingMode`
 * (`CONTINUOUS_SCROLL` / `PAGE_TURN`, defaults in `:core:storage`
 * `ReaderSettings`); this enum is the typed reader-side view. Switching
 * modes keeps the current position: the screen reuses the same list state,
 * so the first visible block is untouched — [keepBlock] is the clamp the
 * switch path (and the T09 relayout path) applies to that block address.
 *
 * T09 seam: typography/layout changes (font size, line spacing, margins)
 * MUST route the pre-change visible block through [keepBlock] and scroll
 * back to it after relayout, so the reading context survives reflow.
 */
enum class ReadingMode(val stored: String) {
    CONTINUOUS_SCROLL("CONTINUOUS_SCROLL"),
    PAGE_TURN("PAGE_TURN"),
    ;

    companion object {
        fun of(stored: String): ReadingMode = entries.firstOrNull { it.stored == stored } ?: PAGE_TURN

        /** Both display modes use ebook page turns unless the user chose scrolling (FR-4). */
        @Suppress("UNUSED_PARAMETER")
        fun defaultForEink(isEink: Boolean): ReadingMode = PAGE_TURN
    }
}

/** Clamp a kept block address into a (possibly changed) document. Empty docs keep 0. */
fun keepBlock(
    blockIndex: Int,
    blockCount: Int,
): Int {
    if (blockCount <= 0) return 0
    return blockIndex.coerceIn(0, blockCount - 1)
}
