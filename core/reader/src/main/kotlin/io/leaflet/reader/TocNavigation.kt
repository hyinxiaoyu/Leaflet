package io.leaflet.reader

import io.leaflet.markdown.ParsedDocument

/**
 * TOC jump resolution (T08, REQ-0006 FR-3/FR-4; R2-02 single-map).
 *
 * Jumps go anchor → block index through the document's OWN position map
 * (`positionOps.locateAnchor`) — the same map the parser binds by TOC order
 * (R2-02: k-th entry → k-th heading, duplicates and substring titles
 * included) and the same map position restore and link fragments use.
 * There is exactly ONE anchor→block mapping in the codebase; this object
 * holds no matching logic of its own (the old occurrence-aware
 * `contains` scan is deleted, not kept as a fallback).
 *
 * The actual scroll is instant (`scrollToItem`, never smooth/animated —
 * E-Ink rule); arrival feedback is a static highlight (the target block
 * index, held by the screen with no animation, E-Ink safe).
 */
object TocNavigation {
    /**
     * Block index for [anchor], or null when the anchor is unknown.
     * Never throws, never negative, never block 0 as a fallback.
     */
    fun resolve(
        document: ParsedDocument,
        anchor: String,
    ): Int? {
        if (anchor.isEmpty() || document.blocks.isEmpty()) return null
        return document.positionOps?.locateAnchor(anchor)?.blockIndex
            ?.takeIf { it in document.blocks.indices }
    }

    /**
     * Current-chapter indicator for the TOC drawer: the nearest heading at
     * or above [blockIndex], or null for heading-less documents (TXT shows
     * the "无章节标题" placeholder instead of a fake entry, FR-6).
     */
    fun currentChapter(
        document: ParsedDocument,
        blockIndex: Int,
    ): String? {
        val top = blockIndex.coerceAtMost(document.blocks.size - 1).coerceAtLeast(0)
        if (document.blocks.isEmpty()) return null
        for (i in top downTo 0) {
            if (document.blocks[i].kind == io.leaflet.markdown.BlockKind.HEADING) {
                val visible = document.blockTexts.getOrElse(i) { "" }
                return visible.ifBlank { document.blocks[i].text }.takeIf { it.isNotBlank() }
            }
        }
        return null
    }
}
