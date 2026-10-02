package io.leaflet.reader.ui.library

/**
 * One breadcrumb entry (REQ-0012 FR-1/FR-3): the Library root or one
 * directory on the path to the currently open prefix.
 *
 * [target] is the exact prefix the entry opens, `""` for the Library root.
 * It is a plain relative path — never a content URI — and literal percent
 * characters are preserved exactly as indexed.
 */
data class BreadcrumbSegment(
    /** Visible label; may be shortened by the UI, never rewritten here. */
    val label: String,
    /** Exact prefix this entry opens; `""` is the Library root. */
    val target: String,
    /** True only for the directory that is currently open. */
    val isCurrent: Boolean,
)

/**
 * A breadcrumb tap together with the provenance it was generated from
 * (REQ-0012 FR-2): the Library and the exact prefix that were on screen when
 * the entry was rendered.
 *
 * The UI must pass the state it actually rendered, never a bare path. A path
 * such as the Library root is valid in *every* Library, so without the origin a
 * queue-delayed tap could be applied to a Library or a directory the user has
 * since left (NAV-ACC-01).
 */
data class AncestorRequest(
    /** Exact prefix to open; `""` is the Library root. */
    val target: String,
    /** Library the entry was rendered for. */
    val libraryId: String,
    /** Prefix that was open when the entry was rendered. */
    val prefix: String,
)

/**
 * Breadcrumb for [prefix] inside a Library shown as [rootLabel]
 * (REQ-0012 FR-1): the root first, then one entry per path segment.
 *
 * Pure path mapping — no SAF query, no index read, no URI decoding. The last
 * entry is always the current directory, so a root prefix yields the root
 * entry alone. Case, spaces, Chinese characters, and literal `%` survive.
 */
fun breadcrumbSegments(
    prefix: String,
    rootLabel: String,
): List<BreadcrumbSegment> {
    val normalized = normalizePrefix(prefix)
    val names = normalized.split('/').filter { it.isNotEmpty() }
    val segments = ArrayList<BreadcrumbSegment>(names.size + 1)
    segments += BreadcrumbSegment(rootLabel, "", names.isEmpty())
    var target = ""
    names.forEachIndexed { index, name ->
        target = childPath(target, name)
        segments += BreadcrumbSegment(name, target, index == names.lastIndex)
    }
    return segments
}

/**
 * True when [target] is the Library root, the current prefix, or an ancestor
 * of the current prefix (REQ-0012 FR-2/AC-2).
 *
 * Matching is by whole path segment, so `a` is not an ancestor of `ab`, and a
 * sibling, a tree escape (`../x`), or an unrelated prefix is rejected. The
 * current prefix itself is accepted; the caller turns it into a no-op.
 */
fun isAncestorTarget(
    prefix: String,
    target: String,
): Boolean {
    val current = normalizePrefix(prefix)
    val candidate = normalizePrefix(target)
    return candidate.isEmpty() || current == candidate || current.startsWith("$candidate/")
}

/** `/`-trimmed relative prefix; the root is `""`. */
internal fun normalizePrefix(prefix: String): String = prefix.trim('/')
