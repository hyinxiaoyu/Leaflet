package io.leaflet.reader

import io.leaflet.markdown.ParsedDocument
import io.leaflet.model.DisplayMode
import io.leaflet.model.DocumentId

/**
 * Reader input (frozen T01 contract §4): which document, under which display
 * strategy. App hands a cancellable load task to the Reader; stale background
 * results MUST NOT overwrite a newer document (T06).
 *
 * T08 navigation (REQ-0005/REQ-0006): [PageTurn] (viewport-minus-overlap
 * geometry + head/tail clamp), [TapZones] (left/center/right), [GestureArbitration]
 * (content gestures win; long press never turns), [TocNavigation] (anchor →
 * block jumps, static arrival highlight), [ReadingMode] (CONTINUOUS/PAGE_TURN
 * switch keeps the visible block via [keepBlock], the T09 relayout seam).
 * Every programmatic move is instant (`scrollToItem` / instant `scrollBy`) —
 * no `smoothScroll`/animated-scroll call exists on the E-Ink path.
 */
data class ReaderInput(
    val documentId: DocumentId,
    val displayMode: DisplayMode,
)

/** Reader loading/error/degraded states (frozen T01 contract §4). */
sealed interface ReaderState {
    /** Loading: static text/percentage/thin bar only — infinite animations forbidden. */
    data object Loading : ReaderState

    data class Content(val document: ParsedDocument, val positionBlockIndex: Int) : ReaderState

    /** Oversized content served with an explanatory banner, never silent truncation. */
    data class Degraded(val document: ParsedDocument, val notice: DegradedNotice) : ReaderState

    data class Failed(val reason: ReaderFailure) : ReaderState
}

data class DegradedNotice(val message: String)

/** Recoverable failure reasons: lost permission, vanished file, parse failure. */
enum class ReaderFailure {
    AUTHORIZATION_LOST,
    FILE_MISSING,
    PARSE_FAILED,
    CANCELLED,
}
