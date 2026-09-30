package io.leaflet.reader

import io.leaflet.markdown.MatchLocation
import io.leaflet.markdown.ParsedDocument
import io.leaflet.markdown.SearchLimits
import io.leaflet.markdown.collectBlockMatches
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * In-document literal search state machine (T11, REQ-0008 FR-1/FR-4/FR-6;
 * R2-06 cap + cooperative cancellation, finding F07).
 *
 * Frozen semantics (see `docs/architecture/reader.md` §7):
 * - Literal substring match over rendered visible text only
 *   ([ParsedDocument.blockTexts]); Markdown syntax markers never participate
 *   because they are absent from visible text. No regex, no full-text
 *   re-parse — the already-loaded document is reused on every keystroke.
 * - Case-sensitive (`String.indexOf` semantics; REQ-0008 OQ frozen here).
 * - Empty or blank-only queries never search (zero results, no error).
 * - Matches never span blocks: a query crossing a block boundary yields no
 *   match for that span (REQ-0008 AC-7, locked by test).
 * - Next/previous wraps around document order (stable: block, then offset).
 * - Cancellation: the caller debounces input ([DEBOUNCE_MS]) and cancels the
 *   previous job; [isStale] drops late results so an expired job never
 *   overwrites the current one (REQ-0008 FR-4/AC-4).
 *
 * R2-06 bounds (REQ-0008 AC-8): a 10 MB repeated-character document holds
 * ~10M matches for a 1-char query. Both entry points stop at
 * [MAX_SEARCH_MATCHES] in document order via the shared
 * [collectBlockMatches] scan (single implementation with
 * `BlockPositionMap.locateQuery`, so order and cap stay identical). The
 * suspend path additionally checks cancellation per block, so a 200k-block
 * scan stays killable; the sync path is for tests and small inputs only.
 * Truncation is never silent: [SearchResult.capped] tells the UI to show
 * "n / 5000+ · refine" instead of a bare count.
 */
object DocumentSearch {
    /**
     * Input debounce before a search job runs (REQ-0008 AC-4: 200 ms of
     * rapid typing settles on the last query only).
     */
    const val DEBOUNCE_MS: Long = 200L

    /**
     * Hard result cap, aliased from `:core:markdown` [SearchLimits] (single
     * source of truth; kept here so callers never import across modules for
     * the bound).
     */
    const val MAX_SEARCH_MATCHES: Int = SearchLimits.MAX_SEARCH_MATCHES

    /**
     * Literal visible-text matches for [query] in document order. Never
     * throws, never re-parses; empty/blank queries return empty. Capped at
     * [MAX_SEARCH_MATCHES] — callers needing the completeness flag use
     * [searchCapped].
     */
    fun search(
        document: ParsedDocument,
        query: String,
    ): List<MatchLocation> {
        if (query.isBlank()) return emptyList()
        val out = ArrayList<MatchLocation>(256)
        for (b in document.blockTexts.indices) {
            if (collectBlockMatches(document.blockTexts[b], query, b, out)) break
        }
        return out
    }

    /**
     * Capped search with completeness flag + cooperative cancellation
     * (R2-06 production path): `ensureActive` per block keeps a 10 MB ×
     * 200k-block scan killable, and the loop stops at [MAX_SEARCH_MATCHES]
     * with `capped = true`.
     */
    suspend fun searchCapped(
        document: ParsedDocument,
        query: String,
    ): SearchResult {
        val out = ArrayList<MatchLocation>(1024)
        val texts = document.blockTexts
        for (b in texts.indices) {
            // Same T13 yield rationale as the parse loops: without a real
            // suspension point the runTest dispatcher starves and production
            // cancellation cannot land mid-scan on a 200k-block document.
            if (b % 512 == 0) kotlinx.coroutines.yield()
            currentCoroutineContext().ensureActive()
            if (collectBlockMatches(texts[b], query, b, out)) {
                return SearchResult(out, capped = true)
            }
        }
        return SearchResult(out, capped = false)
    }

    /**
     * Next/previous match index with wrap-around. [total] must be positive;
     * callers guard emptiness via [SearchUiState.current].
     */
    fun stepIndex(
        current: Int,
        total: Int,
        forward: Boolean,
    ): Int {
        require(total > 0) { "stepIndex needs a non-empty match list" }
        return if (forward) {
            (current + 1) % total
        } else {
            (current - 1 + total) % total
        }
    }

    /**
     * Version guard: a finished job applies only when its generation still
     * equals the current one (query change / document switch bumps it).
     */
    fun isStale(
        resultGeneration: Long,
        currentGeneration: Long,
    ): Boolean = resultGeneration != currentGeneration
}

/**
 * Capped search result (R2-06): the first [MAX_SEARCH_MATCHES] matches in
 * document order plus whether the scan was cut. `capped = true` means the
 * document holds MORE matches than returned — the UI must say so (never a
 * bare count that pretends completeness).
 */
data class SearchResult(
    val matches: List<MatchLocation>,
    val capped: Boolean,
) {
    val total: Int get() = matches.size
}

/**
 * Search UI state for one open document (owned by the app ViewModel;
 * cleared on document switch per REQ-0008 FR-5).
 */
data class SearchUiState(
    val query: String = "",
    val matches: List<MatchLocation> = emptyList(),
    val currentIndex: Int = 0,
    val searching: Boolean = false,
    val generation: Long = 0,
    /**
     * Bumped on every jump request (new results with a hit, next/previous).
     * The screen keys its one-shot `scrollToItem` effect on this so a
     * recomposition with an unchanged index never re-scrolls.
     */
    val jumpToken: Long = 0,
    /**
     * R2-06: true when [matches] is the capped first-[MAX_SEARCH_MATCHES]
     * window, not the complete set. The bar shows "n / 5000+ · refine"
     * instead of "n / m".
     */
    val capped: Boolean = false,
) {
    val total: Int get() = matches.size

    /** Current match, or null when there are no matches. */
    val current: MatchLocation? get() = matches.getOrNull(currentIndex)
}
