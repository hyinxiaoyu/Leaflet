package io.leaflet.storage.scan

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import java.io.IOException

/**
 * One row of a directory listing: either a sub-directory or a document file.
 *
 * Metadata only — the scanner never opens file streams (REQ-0001 FR-3), so
 * this type carries no stream handle and needs no closing. Rows with a blank
 * [documentId] or [displayName] model a provider row with missing columns;
 * the scanner records them as entry-level errors and continues (FR-9) instead
 * of aborting the walk.
 */
data class TreeEntry(
    val documentId: String,
    val displayName: String,
    val isDirectory: Boolean,
    val size: Long?,
    val lastModified: Long?,
    val documentUri: String,
    /**
     * True when the provider did not state a usable MIME type, so the row is
     * neither provably a directory nor provably a file (CR-20261001-05).
     * Consumers must treat it as unclassifiable: never index it as a file, and
     * never accept it as evidence that another entry is absent.
     */
    val typeUnknown: Boolean = false,
)

/**
 * Minimal tree-enumeration surface the scanner walks (BFS over
 * `DocumentsContract` child queries).
 *
 * Kept behind an interface so JVM unit tests drive [LibraryScanner] with an
 * in-memory fake (slow queries, missing metadata, deep trees, mid-walk
 * failures); production uses [ContentResolverTreeLister] and is covered by
 * instrumented tests through the extended stub provider. Failures surface as
 * exceptions: `SecurityException` for a lost grant, `IOException` (including
 * a null cursor) for provider failures. Implementations must not swallow
 * coroutine cancellation.
 */
interface TreeLister {
    /** Provider document id of the tree root, or throws when unreachable. */
    suspend fun treeDocumentId(treeUri: String): String

    /** Direct children of [parentDocumentId] inside [treeUri]. */
    suspend fun listChildren(
        treeUri: String,
        parentDocumentId: String,
    ): List<TreeEntry>
}

/**
 * Row classification shared by the production cursor parser and its JVM tests
 * (CR-20261001-05). Pure: no `Cursor`, `Uri`, or `ContentResolver` is involved,
 * so the "which fields does this row actually provide" rule is testable without
 * an Android runtime.
 *
 * Incomplete metadata is preserved rather than overwritten:
 * - a blank [documentId] or [displayName] stays a row the scanner reports as an
 *   entry-level error (FR-9) and skips — the identity/type it still has is kept;
 * - the directory bit comes from [mimeType] alone, so a folder that lost its
 *   name is still a folder and its subtree counts as present-but-unprovable
 *   instead of vanished;
 * - a row with no MIME type is marked [TreeEntry.typeUnknown]: it is neither a
 *   directory nor a file, and must not be indexed as one or used as absence
 *   evidence for another entry.
 */
internal fun classifyTreeRow(
    documentId: String?,
    displayName: String?,
    mimeType: String?,
    size: Long?,
    lastModified: Long?,
): TreeEntry =
    TreeEntry(
        documentId = documentId ?: "",
        displayName = displayName ?: "",
        isDirectory = mimeType == DocumentsContract.Document.MIME_TYPE_DIR,
        size = size,
        lastModified = lastModified,
        documentUri = "",
        // A blank/absent MIME means the provider did not state what this row is.
        typeUnknown = mimeType.isNullOrEmpty(),
    )

/**
 * The FINAL [TreeEntry] the scanner receives for one provider row
 * (CR-20261001-05): the classifier's decision plus the row's document URI.
 *
 * This is the projection the production cursor parser uses for every row, and it
 * is pure — the caller supplies [documentUriFor], so no `Cursor`, `Uri`, or
 * `ContentResolver` is needed to test it. Deriving the emitted entry from the
 * classifier's result (instead of rebuilding it field by field) means a field
 * added to [TreeEntry] can never be silently dropped between the classifier and
 * the scanner again.
 */
internal fun readTreeEntry(
    documentId: String?,
    displayName: String?,
    mimeType: String?,
    size: Long?,
    lastModified: Long?,
    documentUriFor: (String) -> String,
): TreeEntry {
    val row = classifyTreeRow(documentId, displayName, mimeType, size, lastModified)
    val documentUri = if (row.documentId.isEmpty()) "" else documentUriFor(row.documentId)
    return row.copy(documentUri = documentUri)
}

/**
 * Production [TreeLister] over the real [ContentResolver] (read-only).
 *
 * Each directory is one `query()` call whose cursor is closed before return
 * (`use {}`) on every path, including cancellation and failure — no leaked
 * descriptors. Content URIs are built only with
 * [DocumentsContract.buildDocumentUriUsingTree], never by path concatenation
 * (ADR-0002). Blocking resolver I/O runs on the caller's dispatcher
 * ([LibraryScanner] confines it to its IO dispatcher); cancellation is
 * observed between directories, never swallowed here.
 */
class ContentResolverTreeLister(
    private val resolver: ContentResolver,
) : TreeLister {
    override suspend fun treeDocumentId(treeUri: String): String = DocumentsContract.getTreeDocumentId(Uri.parse(treeUri))

    override suspend fun listChildren(
        treeUri: String,
        parentDocumentId: String,
    ): List<TreeEntry> {
        val tree = Uri.parse(treeUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentDocumentId)
        val cursor: Cursor =
            resolver.query(childrenUri, PROJECTION, null, null, null)
                ?: throw IOException("Provider returned a null cursor for $parentDocumentId")
        return cursor.use { readEntries(tree, it) }
    }

    private fun readEntries(
        tree: Uri,
        cursor: Cursor,
    ): List<TreeEntry> {
        val idCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
        val nameCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        val mimeCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
        val sizeCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
        val mtimeCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
        val rows = mutableListOf<TreeEntry>()
        while (cursor.moveToNext()) {
            val docId = if (idCol >= 0) cursor.getString(idCol) else null
            val name = if (nameCol >= 0) cursor.getString(nameCol) else null
            val mime = if (mimeCol >= 0) cursor.getString(mimeCol) else null
            val size = if (sizeCol >= 0 && !cursor.isNull(sizeCol)) cursor.getLong(sizeCol) else null
            val mtime = if (mtimeCol >= 0 && !cursor.isNull(mtimeCol)) cursor.getLong(mtimeCol) else null
            // One row → one emitted entry, through the shared projection: the
            // scanner must receive exactly what the classifier decided
            // (CR-20261001-05). Rebuilding the entry field by field here used to
            // drop `typeUnknown`, so a provider row with a missing MIME was
            // delivered as a file.
            rows.add(
                readTreeEntry(docId, name, mime, size, mtime) { documentId ->
                    DocumentsContract.buildDocumentUriUsingTree(tree, documentId).toString()
                },
            )
        }
        return rows
    }

    private companion object {
        val PROJECTION =
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_SIZE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            )
    }
}
