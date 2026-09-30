package io.leaflet.reader.ui

import android.content.Context
import io.leaflet.markdown.LeafletDocumentParser
import io.leaflet.model.DocumentId
import io.leaflet.model.LibraryId
import io.leaflet.model.ReadingPosition
import io.leaflet.model.RecentRead
import io.leaflet.reader.DocumentDescriptor
import io.leaflet.reader.ui.reader.ReaderStore
import io.leaflet.reader.ui.reader.ReaderViewModel
import kotlinx.coroutines.Dispatchers
import java.io.ByteArrayInputStream
import java.io.InputStream

/**
 * R2-07 page harness (both flavors): the REAL `ReaderScreen` route driven by
 * a [ReaderViewModel] whose [ReaderStore] is a fake at the storage seam —
 * the session, parser (Markwon), search, TOC, tap arbitration, and Compose
 * entries are all production. Composed with `createComposeRule` (content
 * only): TEST-process activities are infra-blocked on this emulator image
 * (`startActivitySync` cross-process failure), so no host activity is
 * launched — the composed tree is still the production screen.
 */
internal class FakePageStore(
    // Content is mutable so a page test can model an EXTERNAL edit / deletion
    // between two opens (R2 DEF-005); the fingerprint follows the bytes.
    files: Map<String, ByteArray>,
    val fileNames: Map<String, String> = emptyMap(),
    private val libraryId: LibraryId = LibraryId("lib-page"),
) : ReaderStore {
    private val positions = mutableMapOf<String, Pair<ReadingPosition, String?>>()
    val savedPositions = mutableListOf<String>()

    /** Every write as (documentId, blockIndex), in order (DEF-010 evidence). */
    val savedBlocks = mutableListOf<Pair<String, Int>>()

    fun savedBlocksFor(documentId: String): List<Int> = savedBlocks.filter { it.first == documentId }.map { it.second }

    private fun fileNameFor(id: DocumentId): String = fileNames[id.value] ?: id.value

    override suspend fun probeAccess(documentId: DocumentId): io.leaflet.reader.ReaderFailure? {
        if (grantRevoked || denyAll) return io.leaflet.reader.ReaderFailure.AUTHORIZATION_LOST
        if (fileGone) return io.leaflet.reader.ReaderFailure.FILE_MISSING
        if (!bytes.containsKey(documentId.value)) return io.leaflet.reader.ReaderFailure.FILE_MISSING
        return null
    }

    override suspend fun openStream(documentId: DocumentId): InputStream {
        openCount++
        if (documentId.value == slowOpenFor && slowOpenMs > 0) kotlinx.coroutines.delay(slowOpenMs)
        if (grantRevoked) throw SecurityException("stub grant revoked")
        if (fileGone) throw java.io.IOException("gone: ${documentId.value}")
        if (denyAll) throw SecurityException("stub grant revoked")
        val blob = bytes[documentId.value] ?: throw java.io.IOException("gone: ${documentId.value}")
        return ByteArrayInputStream(blob)
    }

    private val bytes: MutableMap<String, ByteArray> = LinkedHashMap(files)

    /** Models a revoked SAF grant: every storage access throws. */
    var denyAll: Boolean = false

    /**
     * R2 DEF-005: the INDEX ROW stays exactly as it was (same fingerprint)
     * while the provider grant is gone — the production gap that a
     * fingerprint comparison cannot see.
     */
    var grantRevoked: Boolean = false

    /** The indexed file vanished under an unchanged row/fingerprint. */
    var fileGone: Boolean = false

    /** Rows whose fingerprint is unknown (`null`) — never proof of freshness. */
    var unknownFingerprint: Boolean = false

    /** Stream opens observed (probe + load accounting). */
    var openCount: Int = 0
        private set

    /** Metadata reads observed (tests can wait for a recheck to reach the store). */
    var describeCount: Int = 0
        private set

    /** Models a slow provider: delays a metadata read by this long. */
    var describeDelayMs: Long = 0

    /** Models a slow load: delays the stream open of THIS document. */
    var slowOpenFor: String? = null
    var slowOpenMs: Long = 0

    /**
     * Models an overwrite that keeps mtime:size identical, so a
     * fingerprint-based freshness check cannot see it (the explicit Refresh
     * entry must still re-read).
     */
    var forceSameFingerprint: Boolean = false

    /** Models an external edit (or a rescan picking up one). */
    fun setBytes(
        documentId: String,
        content: ByteArray,
    ) {
        bytes[documentId] = content
    }

    /**
     * R3-06 in-tree resolution table: reference (as written in the document,
     * e.g. `other.md` or `../../top.md`) to target document id. References
     * absent here model a missing/out-of-tree target and resolve to null,
     * exactly like `DocumentSourceImpl`.
     */
    var relativeResolution: Map<String, String> = emptyMap()

    /** Models the file disappearing from the tree/index. */
    fun remove(documentId: String) {
        bytes.remove(documentId)
    }

    override suspend fun describe(documentId: DocumentId): DocumentDescriptor? {
        describeCount++
        if (describeDelayMs > 0) kotlinx.coroutines.delay(describeDelayMs)
        if (denyAll) throw SecurityException("stub grant revoked")
        val blob = bytes[documentId.value] ?: return null
        val name = fileNameFor(documentId)
        // Mirrors production's `mtime:size`: an edit that changes the size is
        // visible as a changed fingerprint (forceSameFingerprint models the
        // same-mtime same-size overwrite that only Refresh can catch).
        return DocumentDescriptor(
            fileName = name,
            fingerprint =
                when {
                    unknownFingerprint -> null
                    forceSameFingerprint -> "42:fixed"
                    else -> "42:${blob.size}"
                },
            libraryId = libraryId,
            titleFallback = name.substringBeforeLast('.'),
        )
    }

    override suspend fun resolveRelative(
        fromId: DocumentId,
        ref: String,
    ): io.leaflet.storage.ResolvedResource? {
        val target = relativeResolution[ref] ?: return null
        return io.leaflet.storage.ResolvedResource(DocumentId(target), target)
    }

    override suspend fun loadPosition(documentId: DocumentId): io.leaflet.reader.ui.reader.StoredPosition? {
        val (pos, fp) = positions[documentId.value] ?: return null
        return io.leaflet.reader.ui.reader.StoredPosition(pos, fp)
    }

    override suspend fun savePosition(
        position: ReadingPosition,
        lastModified: Long,
        size: Long,
    ) {
        positions[position.documentId.value] = position to "$lastModified:$size"
        savedPositions.add(position.documentId.value)
        savedBlocks.add(position.documentId.value to position.blockIndex)
    }

    override suspend fun recordRecent(entry: RecentRead) = Unit
}

/** Builds a production-parser [ReaderViewModel] over [FakePageStore]. */
internal fun pageViewModel(
    context: Context,
    store: ReaderStore,
): ReaderViewModel =
    ReaderViewModel(
        store = store,
        parser = LeafletDocumentParser.create(context),
        ioDispatcher = Dispatchers.IO,
        clock = System::currentTimeMillis,
    )
