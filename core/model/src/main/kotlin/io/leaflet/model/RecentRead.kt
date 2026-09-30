package io.leaflet.model

/** Runtime display strategy. Orthogonal to the `full`/`eink` build flavor. */
enum class DisplayMode {
    STANDARD,
    EINK,
}

/**
 * One recent-read entry: last-opened ordering snapshot for "pick up where you left off".
 *
 * @property documentId internal stable document key.
 * @property titleSnapshot title text captured at open time, so the row still
 *   renders when the source file is temporarily unreachable.
 * @property libraryId owning library, for grouping and re-authorization prompts.
 * @property lastOpenedAtEpochMs wall clock millis of the last successful open.
 */
data class RecentRead(
    val documentId: DocumentId,
    val titleSnapshot: String,
    val libraryId: LibraryId,
    val lastOpenedAtEpochMs: Long,
)
