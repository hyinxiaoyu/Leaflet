package io.leaflet.storage.scan

/**
 * File-name extension handling for library scans (REQ-0001 FR-2).
 *
 * Only `.md` / `.markdown` / `.txt` are indexed, case-insensitively. Every
 * other extension is ignored silently at the file level (an unsupported-only
 * directory still scans successfully with zero documents — REQ-0001 edge
 * case), while empty directories are simply walked with no entries.
 */
val SUPPORTED_DOCUMENT_EXTENSIONS: Set<String> = setOf("md", "markdown", "txt")

/** Lowercase extension without dot, or null when the name has none. */
fun extensionOf(fileName: String): String? {
    val dot = fileName.lastIndexOf('.')
    if (dot < 0 || dot == fileName.length - 1) return null
    return fileName.substring(dot + 1).lowercase()
}

/** True when [fileName] carries a supported Markdown/TXT extension (any case). */
fun isSupportedDocument(fileName: String): Boolean = extensionOf(fileName) in SUPPORTED_DOCUMENT_EXTENSIONS

/**
 * One discovered document file, relative to its library root.
 *
 * Metadata only — the scanner never opens file streams (REQ-0001 FR-3). Size
 * and mtime are nullable: a null (or otherwise unreliable) value means "the
 * provider could not tell", which must NEVER be treated as "unchanged"
 * (REQ-0001 FR-4). The scanner stores sentinel values (`-1` / `0`) for these
 * rows and always re-indexes them on the next scan.
 */
data class ScannedFile(
    val relativePath: String,
    val displayName: String,
    val extension: String,
    val size: Long?,
    val lastModified: Long?,
    val documentUri: String,
)

/**
 * Machine-readable scan failure class for the terminal [ScanResult].
 * The human-readable recovery text lives in `failureReason`; this enum only
 * lets callers/tests branch without parsing strings.
 */
enum class ScanFailureKind {
    PERMISSION,
    PROVIDER,
    OVERFLOW,
    UNKNOWN,
}

/**
 * One tolerated entry-level problem (REQ-0001 FR-9).
 *
 * A bad entry never aborts the walk: it is recorded here, skipped (or kept
 * as-is when it matches a prior row), and surfaced in the terminal
 * [ScanResult]. Directory entries ([isDirectory] = true) additionally mark
 * the whole scan's absence evidence as unprovable (subtree skipped),
 * which suppresses disappeared-item cleanup for that run.
 */
data class EntryError(
    val path: String,
    val reason: String,
    val isDirectory: Boolean,
)

/** Static, throttled scan progress for one library (REQ-0001 FR-7). */
enum class ScanProgressState {
    IDLE,
    SCANNING,
    CANCELLING,
    SUCCEEDED,
    FAILED,
    CANCELLED,
}

/**
 * Progress snapshot. Updates are throttled to >= 500 ms by [LibraryScanner]
 * and rendered as static text + file counts — never an animation driver
 * (E-Ink constraint, REQ-0001 FR-7).
 */
data class ScanProgress(
    val libraryId: String,
    val state: ScanProgressState = ScanProgressState.IDLE,
    val scannedFiles: Int = 0,
    val currentDir: String = "",
    val entryErrors: Int = 0,
    val message: String? = null,
)

/**
 * Terminal outcome of one [LibraryScanner.scan] call.
 *
 * Counts are observable index effects (rows inserted / updated / skipped by
 * the mtime+size rule / deleted by disappeared-item cleanup). [entryErrors]
 * carries every tolerated per-entry problem; [failureReason] is non-null only
 * for FAILED outcomes (grant loss, provider failure, traversal overflow).
 */
data class ScanResult(
    val libraryId: String,
    val status: io.leaflet.storage.ScanStatus,
    val seen: Int = 0,
    val inserted: Int = 0,
    val updated: Int = 0,
    val skipped: Int = 0,
    val deleted: Int = 0,
    val entryErrors: List<EntryError> = emptyList(),
    val failureReason: String? = null,
    val failureKind: ScanFailureKind? = null,
)

/** Read-side detail for one library's browse screen (Room-backed). */
data class LibraryDetail(
    val libraryId: String,
    val displayName: String,
    val rootTreeUri: String,
    val lastScannedAt: Long?,
    val documentCount: Int,
)

/**
 * Default library display name derived from a tree URI string (pure).
 *
 * Takes the last path segment, URL-decodes it (`primary%3ADocs` ->
 * `primary:Docs`), and keeps the part after the final `:` (volume prefix).
 * Falls back to `"Library"` for blank/garbled input. Renaming never affects
 * dedupe (REQ-0001 FR-1): the dedupe key stays the normalized tree URI.
 */
fun defaultDisplayNameForTree(treeUri: String): String {
    var trimmed = treeUri.trim()
    while (trimmed.length > 1 && trimmed.endsWith("/")) trimmed = trimmed.dropLast(1)
    // A bare authority (`content://tree`) carries no document path: fall back.
    val noScheme = trimmed.substringAfter("://", trimmed)
    if ('/' !in noScheme) return "Library"
    val last = noScheme.substringAfterLast('/').trim()
    if (last.isEmpty()) return "Library"
    val decoded =
        try {
            java.net.URLDecoder.decode(last, Charsets.UTF_8.name())
        } catch (e: IllegalArgumentException) {
            last
        }
    return decoded.substringAfterLast(':').trim().ifEmpty { "Library" }
}
