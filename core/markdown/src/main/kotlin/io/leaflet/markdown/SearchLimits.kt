package io.leaflet.markdown

/**
 * In-document search bounds (R2-06, REQ-0008 AC-8).
 *
 * A 10 MB repeated-character document holds ~10M matches for a 1-char query;
 * materializing them would OOM. Every production search loop stops at
 * [MAX_SEARCH_MATCHES] in document order and reports the cut via a capped
 * flag (see `:core:reader` `SearchResult`) — explicit capped semantics, never
 * a silent undercount. The UI shows "n / 5000+" plus a refine hint.
 */
object SearchLimits {
    const val MAX_SEARCH_MATCHES: Int = 5_000
}

/**
 * Appends literal matches of [query] in [text] to [out] as [MatchLocation]
 * rows for [blockIndex], advancing one char per hit (overlapping needles
 * match repeatedly: "aa" hits twice in "aaa").
 *
 * Single shared scan for [BlockPositionMap.locateQuery] (sync) and
 * `:core:reader` `DocumentSearch` (sync + cancellable suspend): one
 * implementation so the no-cross-block rule, document order, and cap stay
 * identical on every path.
 *
 * @return true when [limit] was reached: [out] then holds exactly the first
 *   [limit] matches and the caller stops scanning further blocks.
 */
fun collectBlockMatches(
    text: String,
    query: String,
    blockIndex: Int,
    out: MutableList<MatchLocation>,
    limit: Int = SearchLimits.MAX_SEARCH_MATCHES,
): Boolean {
    if (query.isEmpty() || text.isEmpty()) return false
    var from = 0
    while (from < text.length) {
        val hit = text.indexOf(query, from)
        if (hit < 0) return false
        out.add(MatchLocation(blockIndex, hit, hit + query.length))
        if (out.size >= limit) return true
        from = hit + 1
    }
    return false
}
