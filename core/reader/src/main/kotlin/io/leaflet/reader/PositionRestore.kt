package io.leaflet.reader

import io.leaflet.markdown.BlockKind
import io.leaflet.markdown.ParsedDocument
import io.leaflet.model.ReadingPosition

/**
 * Position-restore planner (T07, REQ-0003 FR-3/FR-4).
 *
 * Pure function over the frozen [ReadingPosition] contract (overview §3
 * contract 5): same-fingerprint block/offset first, then anchor, then
 * progress, then document top. No pixel values enter the decision — the
 * block index is the primary address, `blockOffset` only refines it.
 *
 * Fingerprints are opaque `mtime:size` strings produced by the app layer
 * (`:core:storage` `fingerprintOf`); this module only compares them for
 * equality. A null/unknown stored fingerprint is treated as stale (never
 * trusted), mirroring REQ-0001 FR-4's "missing means maybe-changed" rule.
 */
enum class RestoreKind {
    EXACT,
    ANCHOR,
    PROGRESS,
    TOP,
}

data class RestoreResult(
    val blockIndex: Int,
    val blockOffset: Int,
    val kind: RestoreKind,
    /** True when the exact position was lost: the UI shows the stale-content notice. */
    val showStaleNotice: Boolean,
)

object PositionRestore {
    /** Contract version this planner trusts; unknown versions fall back to top (REQ-0003 edge). */
    const val CONTRACT_VERSION = 1

    fun restore(
        saved: ReadingPosition?,
        savedFingerprint: String?,
        liveFingerprint: String?,
        blockCount: Int,
        locateAnchor: (String) -> Int?,
    ): RestoreResult {
        if (blockCount <= 0) return RestoreResult(0, 0, RestoreKind.TOP, false)
        if (saved == null || saved.version != CONTRACT_VERSION) {
            return RestoreResult(0, 0, RestoreKind.TOP, false)
        }
        val fresh = savedFingerprint != null && savedFingerprint == liveFingerprint
        if (fresh && saved.blockIndex in 0 until blockCount) {
            return RestoreResult(
                saved.blockIndex,
                saved.blockOffset.coerceAtLeast(0),
                RestoreKind.EXACT,
                false,
            )
        }
        val anchor = saved.anchor
        if (!anchor.isNullOrEmpty()) {
            val hit = locateAnchor(anchor)
            if (hit != null && hit in 0 until blockCount) {
                return RestoreResult(hit, 0, RestoreKind.ANCHOR, true)
            }
        }
        if (saved.progress.isFinite() && saved.progress > 0f && saved.progress <= 1f) {
            val index = (saved.progress * blockCount).toInt().coerceIn(0, blockCount - 1)
            return RestoreResult(index, 0, RestoreKind.PROGRESS, true)
        }
        // Top fallback: only worth a notice when the user had read past the
        // top. A saved-at-top record landing on top again stays silent.
        return RestoreResult(0, 0, RestoreKind.TOP, saved.blockIndex != 0)
    }

    /**
     * Visible progress in 0..1 from a block address. Constant files (empty /
     * single block) report 0 — no division by zero (REQ-0003 edge case).
     */
    fun progressForBlock(
        blockIndex: Int,
        blockCount: Int,
    ): Float {
        if (blockCount <= 1) return 0f
        return blockIndex.coerceIn(0, blockCount - 1).toFloat() / (blockCount - 1)
    }
}

/**
 * Best-effort anchor lookup inside one parsed document (fallback path only).
 *
 * Tries the position map first (TOC-slug keys, bound by TOC order in
 * `BlockPositionMap.withAnchors`), then matches the saved text against TOC
 * entries and resolves through the SAME map (never a second `contains`
 * scan — R2-02 single-map rule; the old heading-text search conflated
 * duplicates and substring titles). Returns null when nothing matches —
 * the caller then falls back to progress, never crashes.
 */
fun locateAnchorInDoc(
    document: ParsedDocument,
    anchor: String,
): Int? {
    if (anchor.isEmpty()) return null
    document.positionOps?.locateAnchor(anchor)?.let {
        if (it.blockIndex in document.blocks.indices) return it.blockIndex
    }
    val entry =
        document.toc.firstOrNull { it.anchor == anchor || it.text == anchor }
            ?: return null
    return document.positionOps?.locateAnchor(entry.anchor)?.blockIndex
        ?.takeIf { it in document.blocks.indices }
}

/**
 * Fallback-anchor source for a block address: the nearest preceding heading's
 * visible text, or null when the document has none above (e.g. plain TXT).
 * Duplicate headings resolve to the first match at restore time — a
 * documented best-effort limit until REQ-0006 settles the anchor structure.
 */
fun anchorForBlock(
    document: ParsedDocument,
    blockIndex: Int,
): String? {
    val top = blockIndex.coerceAtMost(document.blocks.size - 1)
    for (i in top downTo 0) {
        if (document.blocks[i].kind == BlockKind.HEADING) {
            val visible = document.blockTexts.getOrElse(i) { "" }
            if (visible.isNotBlank()) return visible
            val source = document.blocks[i].text
            if (source.isNotBlank()) return source
        }
    }
    return null
}
