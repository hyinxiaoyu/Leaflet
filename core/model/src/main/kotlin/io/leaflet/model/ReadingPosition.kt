package io.leaflet.model

/**
 * Versioned long-term reading position for one document.
 *
 * Recovery priority on open: same-[version] [blockIndex]/[blockOffset] first,
 * then [anchor], then [progress], then document top. Raw pixel scroll offsets
 * are transient only and MUST NOT be stored here.
 *
 * @property documentId internal stable document key (see [Document.id]).
 * @property version contract version; readers MUST tolerate unknown future
 *   versions by treating the record as absent (fall back to top), never crash.
 * @property blockIndex index into the parsed render-block list.
 * @property blockOffset offset inside the block (e.g. line or character offset,
 *   interpretation owned by the Reader, T06).
 * @property progress visible progress in 0..1, computed from the block model.
 *   Constant files (empty / single block) report 0.
 * @property anchor optional fallback anchor: heading text or TOC path, used
 *   when block indexes are invalid after external edits. Null means "no anchor".
 */
data class ReadingPosition(
    val documentId: DocumentId,
    val version: Int = 1,
    val blockIndex: Int = 0,
    val blockOffset: Int = 0,
    val progress: Float = 0f,
    val anchor: String? = null,
)
