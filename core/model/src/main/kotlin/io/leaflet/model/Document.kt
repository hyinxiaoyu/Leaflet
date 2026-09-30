package io.leaflet.model

/**
 * Index metadata for one Markdown/TXT file inside a [Library].
 *
 * KDoc contract (frozen T01): [id] is the internal stable identifier. [uri]
 * (content URI) and [relativePath] (path relative to the library root, for
 * display and in-tree resolution) are access/display information — they are
 * NOT assumed stable across renames or moves. After a rename the document is
 * treated as "same library, changed metadata" and position recovery falls back
 * via anchor/progress (see [ReadingPosition]).
 *
 * Leaflet never copies source files; this row holds auxiliary data only.
 */
data class Document(
    val id: DocumentId,
    val libraryId: LibraryId,
    /** File name with extension, e.g. `mergetree.md`. */
    val displayName: String,
    /** Path relative to the library root, e.g. `Database/ClickHouse/mergetree.md`. */
    val relativePath: String,
    /** Content URI string used to open a read-only stream. */
    val uri: String,
    /** Lowercase extension without dot: `md`, `markdown` or `txt`. */
    val extension: String,
)
