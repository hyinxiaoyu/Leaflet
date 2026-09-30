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
            // Missing id/name: entry-level error at the scanner, not a throw here (FR-9).
            if (docId.isNullOrEmpty() || name.isNullOrEmpty()) {
                rows.add(TreeEntry("", name ?: "", false, null, null, ""))
                continue
            }
            val mime = if (mimeCol >= 0) cursor.getString(mimeCol) else null
            val isDir = mime == DocumentsContract.Document.MIME_TYPE_DIR
            val size = if (sizeCol >= 0 && !cursor.isNull(sizeCol)) cursor.getLong(sizeCol) else null
            val mtime = if (mtimeCol >= 0 && !cursor.isNull(mtimeCol)) cursor.getLong(mtimeCol) else null
            val docUri = DocumentsContract.buildDocumentUriUsingTree(tree, docId).toString()
            rows.add(TreeEntry(docId, name, isDir, size, mtime, docUri))
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
