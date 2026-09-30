package io.leaflet.reader

import io.leaflet.markdown.MatchLocation

/**
 * Mapping of visible-text match ranges onto the string a block entry actually
 * renders (T11, REQ-0008 FR-2/FR-3; R2 DEF-003, second round).
 *
 * Search coordinates live in visible text (`blockTexts`); some entries render a
 * different string (Markwon consumes markers, a list keeps its `- ` bullets, a
 * heading slice carries `# `). Rules:
 * - Rendered string equals visible text (TXT paragraphs, plain fallback, table
 *   slices): the range is reused as-is.
 * - Otherwise each match keeps its IDENTITY: its substring is re-located at the
 *   occurrence with the SAME ORDINAL as in the visible text. (Mapping every
 *   match to the first occurrence collapsed duplicates onto one span and made
 *   the current match point at the wrong word.)
 * - A match that cannot be located in the rendered string is dropped; an
 *   unmappable *current* match sets [BlockHighlight.blockFallback] so the
 *   caller emphasises the block instead of guessing a position.
 *
 * Never throws; out-of-range input degrades to empty/null, never a crash.
 */
object SearchHighlight {
    /** Inclusive visible-text range for one match (`endInBlock` is exclusive). */
    fun rangeOf(match: MatchLocation): IntRange = match.startInBlock..(match.endInBlock - 1).coerceAtLeast(match.startInBlock - 1)

    /**
     * Highlight for one block. [displayed] is the exact string the entry
     * renders, [visible] its `blockTexts` row, [ranges]/[current] are
     * visible-relative (see [rangeOf]). Null when there is nothing to show.
     */
    fun highlightForBlock(
        displayed: String,
        visible: String,
        ranges: List<IntRange>,
        current: IntRange?,
    ): BlockHighlight? {
        if (ranges.isEmpty()) return null
        val mapped = LinkedHashSet<IntRange>()
        for (range in ranges) {
            mapRange(displayed, visible, range)?.let { mapped.add(it) }
        }
        val curMapped = current?.let { mapRange(displayed, visible, it) }
        if (mapped.isEmpty() && curMapped == null) return null
        return BlockHighlight(
            others = mapped.filterNot { it == curMapped }.toList(),
            current = curMapped,
            blockFallback = curMapped == null,
        )
    }

    /**
     * Visible range → displayed range, preserving the occurrence ORDINAL.
     * Null when the range names no characters or when [displayed] holds fewer
     * occurrences than [visible] (the match cannot be placed without guessing).
     * Identical strings short-circuit to the range itself.
     */
    private fun mapRange(
        displayed: String,
        visible: String,
        range: IntRange,
    ): IntRange? {
        if (visible.isEmpty()) return null
        val from = range.first.coerceIn(0, visible.length)
        val to = (range.last + 1).coerceIn(0, visible.length)
        if (from >= to) return null
        if (displayed == visible) {
            return if (to <= displayed.length) from..(to - 1) else null
        }
        val sub = visible.substring(from, to)
        // Ordinal = how many occurrences of `sub` start before this match.
        var ordinal = 0
        for (hit in occurrences(visible, sub)) {
            if (hit.first >= from) break
            ordinal++
        }
        return occurrences(displayed, sub).getOrNull(ordinal)
    }

    /** All (possibly overlapping) occurrences of [needle] in [haystack]. */
    private fun occurrences(
        haystack: String,
        needle: String,
    ): List<IntRange> {
        if (needle.isEmpty() || haystack.isEmpty()) return emptyList()
        val out = ArrayList<IntRange>()
        var from = 0
        while (from < haystack.length) {
            val hit = haystack.indexOf(needle, from)
            if (hit < 0) break
            out.add(hit..(hit + needle.length - 1))
            from = hit + 1
        }
        return out
    }
}

/**
 * Static (animation-free, E-Ink safe) highlight for one rendered block:
 * [others] get a flat background, [current] an inverted bold span. When the
 * current match cannot be mapped into rendered coordinates
 * ([blockFallback]), the caller bolds the whole block instead (same static
 * convention as the TOC arrival highlight).
 */
data class BlockHighlight(
    val others: List<IntRange>,
    val current: IntRange?,
    val blockFallback: Boolean,
) {
    companion object {
        /** No highlight: render the block plain. */
        val EMPTY = BlockHighlight(emptyList(), null, false)
    }
}
