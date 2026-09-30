package io.leaflet.storage.saf

/**
 * Tree-URI normalization for library dedupe (REQ-0001 FR-1/AC-2).
 *
 * The dedupe key is the trimmed URI string with redundant trailing slashes
 * removed. Display names never participate in dedupe: renaming a library
 * does not create a second row for the same tree.
 *
 * KNOWN LIMIT (REQ-0001 open question, UNVERIFIED): whether normalized tree
 * URI equality covers every provider's URI aliasing (the same directory
 * granted twice yielding textually different URIs) is not proven. T05 must
 * re-check this against at least one real SAF provider; until then,
 * textually different URIs are treated as different libraries, and removal
 * defensively checks for shared grants before releasing (see
 * `LibraryRepositoryImpl.removeLibrary`).
 */
object TreeUris {
    fun normalize(raw: String): String {
        var s = raw.trim()
        while (s.length > 1 && s.endsWith("/")) s = s.dropLast(1)
        return s
    }
}
