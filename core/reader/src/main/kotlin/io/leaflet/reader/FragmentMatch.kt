package io.leaflet.reader

import io.leaflet.markdown.ParsedDocument
import io.leaflet.markdown.ResourceLinks

/**
 * Fragment jump resolution for tapped links (T10, REQ-0009 FR-4/FR-5;
 * R2-02 single-map).
 *
 * Tries the raw fragment first (covers TOC anchors such as `overview-1`),
 * then the TOC-frozen normalization ([ResourceLinks.normalizeFragment]).
 * Both go through [TocNavigation]'s single position-map lookup; duplicate
 * headings land on their own anchor instance (order-bound), never all on
 * the first. Unknown anchors return null so the caller shows an explicit
 * prompt and never jumps to the top pretending success (FR-5).
 */
object FragmentMatch {
    /**
     * Block index for [rawFragment], or null when nothing matches. Never
     * throws, never negative, never falls back to block 0.
     */
    fun resolve(
        document: ParsedDocument,
        rawFragment: String,
    ): Int? {
        if (rawFragment.isEmpty()) return null
        TocNavigation.resolve(document, rawFragment)?.let { return it }
        val normalized = ResourceLinks.normalizeFragment(rawFragment)
        if (normalized != rawFragment) {
            TocNavigation.resolve(document, normalized)?.let { return it }
        }
        return null
    }
}
