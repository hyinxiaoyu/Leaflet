package io.leaflet.storage.saf

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import java.io.File
import java.io.FileNotFoundException

/**
 * Directory-tree extension of the T04 [StubProvider] for T05 scanner
 * instrumented tests: serves a configurable in-memory directory tree through
 * the real `DocumentsContract` child-query path, so
 * [ContentResolverTreeLister] is exercised against a live
 * [android.content.ContentResolver].
 *
 * Manifest-registered under `io.leaflet.storage.stub.tree` via
 * `src/androidTest/AndroidManifest.xml` (test-only, never merged into
 * release builds). Directory enumeration reads [nodes]/[children];
 * open-file inherits the [blobs] table for acidic source-integrity probes.
 *
 * Failure injection: [childFailure] maps a directory document id to an
 * exception thrown from `query()` (grant loss via `SecurityException`,
 * provider failure via `RuntimeException`); [missingColumnsFor] lists entry
 * ids whose rows report null name/mime (FR-9); [nullCursorFor] lists
 * directory ids whose query returns null (provider failure path). Query
 * paths honor [CancellationSignal] like a real provider would.
 */
class StubTreeProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun openFile(
        uri: Uri,
        mode: String,
    ): ParcelFileDescriptor? {
        if (revoked) throw SecurityException("stub tree grant revoked")
        val name = uri.lastPathSegment ?: throw FileNotFoundException("$uri")
        val bytes = blobs[name] ?: throw FileNotFoundException("$uri")
        val file = File(context!!.cacheDir, "t05-stub-$name").also { it.writeBytes(bytes) }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?,
    ): Cursor = queryTree(uri, projection, null)

    override fun query(
        uri: Uri,
        projection: Array<String>?,
        queryArgs: Bundle?,
        cancellationSignal: CancellationSignal?,
    ): Cursor = queryTree(uri, projection, cancellationSignal)

    private fun queryTree(
        uri: Uri,
        projection: Array<String>?,
        signal: CancellationSignal?,
    ): Cursor {
        if (signal?.isCanceled == true) {
            throw android.os.OperationCanceledException("cancelled")
        }
        val match = matcher.match(uri)
        // The scanner only issues child-listing queries shaped by
        // buildChildDocumentsUriUsingTree; anything else is a test bug.
        if (match != CHILDREN) throw FileNotFoundException("unknown uri: $uri")
        // NOTE: getTreeDocumentId returns the TREE root id ("root") for every
        // URI under the tree; the enumerated PARENT is getDocumentId.
        val docId = DocumentsContract.getDocumentId(uri)
        childFailure[docId]?.let { throw it }
        if (docId in nullCursorFor) {
            @Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
            return null as Cursor
        }
        val cols = projection ?: ALL_COLUMNS
        val cursor = MatrixCursor(cols)
        for (childId in children[docId].orEmpty()) {
            if (signal?.isCanceled == true) {
                throw android.os.OperationCanceledException("cancelled")
            }
            val node = nodes[childId] ?: continue
            val values: List<Any?> =
                cols.map { col ->
                    when (col) {
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID -> childId
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME ->
                            if (childId in missingColumnsFor) null else node.name
                        DocumentsContract.Document.COLUMN_MIME_TYPE ->
                            if (childId in missingColumnsFor) {
                                null
                            } else if (node.isDir) {
                                DocumentsContract.Document.MIME_TYPE_DIR
                            } else {
                                "text/markdown"
                            }
                        DocumentsContract.Document.COLUMN_SIZE ->
                            if (childId in missingColumnsFor) null else node.size
                        DocumentsContract.Document.COLUMN_LAST_MODIFIED ->
                            if (childId in missingColumnsFor) null else node.mtime
                        else -> null
                    }
                }
            cursor.addRow(values)
        }
        return cursor
    }

    override fun getType(uri: Uri): String? = "vnd.android.document/directory"

    override fun insert(
        uri: Uri,
        values: ContentValues?,
    ): Uri? = null

    override fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<String>?,
    ): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<String>?,
    ): Int = 0

    data class Node(
        val name: String,
        val isDir: Boolean,
        val size: Long?,
        val mtime: Long?,
    )

    companion object {
        private const val AUTHORITY = "io.leaflet.storage.stub.tree"
        private const val CHILDREN = 1
        private val matcher =
            UriMatcher(UriMatcher.NO_MATCH).apply {
                // Real child-listing URIs from buildChildDocumentsUriUsingTree end
                // in `/children` (tree/<tree>/document/<parent>/children); plain
                // `tree/*` never arrives from the production lister.
                addURI(AUTHORITY, "tree/*/document/*/children", CHILDREN)
            }
        private val ALL_COLUMNS =
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_SIZE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            )

        val blobs = mutableMapOf<String, ByteArray>()
        var revoked = false
        val nodes = mutableMapOf<String, Node>()
        val children = mutableMapOf<String, MutableList<String>>()
        val childFailure = mutableMapOf<String, RuntimeException>()
        val missingColumnsFor = mutableSetOf<String>()
        val nullCursorFor = mutableSetOf<String>()

        fun treeUri(rootId: String): String = "content://$AUTHORITY/tree/$rootId"

        fun reset() {
            blobs.clear()
            revoked = false
            nodes.clear()
            children.clear()
            childFailure.clear()
            missingColumnsFor.clear()
            nullCursorFor.clear()
            nodes["root"] = Node("root", true, null, null)
            children["root"] = mutableListOf()
        }

        fun addDir(
            parentId: String,
            id: String,
            name: String,
        ) {
            nodes[id] = Node(name, true, null, null)
            children.getOrPut(parentId) { mutableListOf() }.add(id)
            children.getOrPut(id) { mutableListOf() }
        }

        fun addFile(
            parentId: String,
            id: String,
            name: String,
            size: Long?,
            mtime: Long?,
        ) {
            nodes[id] = Node(name, false, size, mtime)
            children.getOrPut(parentId) { mutableListOf() }.add(id)
        }

        fun removeFile(
            parentId: String,
            id: String,
        ) {
            children[parentId]?.remove(id)
            nodes.remove(id)
        }

        init {
            reset()
        }
    }
}
