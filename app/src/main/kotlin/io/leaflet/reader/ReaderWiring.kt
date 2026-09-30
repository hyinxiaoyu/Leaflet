package io.leaflet.reader

import io.leaflet.markdown.LeafletDocumentParser
import io.leaflet.model.DocumentId
import io.leaflet.model.LibraryId
import io.leaflet.model.ReadingPosition
import io.leaflet.model.RecentRead
import io.leaflet.reader.ui.reader.ReaderStore
import io.leaflet.reader.ui.reader.RecentHealth
import io.leaflet.reader.ui.reader.RecentStore
import io.leaflet.reader.ui.reader.StoredPosition
import io.leaflet.storage.DocumentSource
import io.leaflet.storage.StorageException
import io.leaflet.storage.db.DocumentDao
import io.leaflet.storage.db.fingerprintOf
import java.io.InputStream

/**
 * Production [ReaderDocumentGateway] over `:core:storage` (T07).
 *
 * Delegates stream opens to [DocumentSource] (read-only; the session closes
 * the stream via `use {}` on every path, including cancellation) and index
 * metadata to [DocumentDao]. Cancellation propagates untouched; typed
 * storage exceptions propagate unwrapped — the session maps them to
 * `AUTHORIZATION_LOST` / `FILE_MISSING` recovery states.
 */
class StorageReaderGateway(
    private val documents: DocumentDao,
    private val source: DocumentSource,
) : ReaderDocumentGateway {
    override suspend fun openStream(documentId: DocumentId): InputStream = source.openStream(documentId)

    override suspend fun describe(documentId: DocumentId): DocumentDescriptor? {
        val row = documents.findById(documentId.value) ?: return null
        return DocumentDescriptor(
            fileName = row.displayName,
            fingerprint = fingerprintOf(row.lastModified, row.size),
            libraryId = LibraryId(row.libraryId),
            titleFallback = row.displayName.substringBeforeLast('.').ifBlank { row.displayName },
        )
    }
}

/**
 * Single wiring point for the Reader route (T07): gateway + parser +
 * repository-backed [ReaderStore]/[RecentStore], constructed once in
 * [AppContainer] so the two flavors share the same logic (ADR-0003).
 */
class ReaderWiring(
    val gateway: ReaderDocumentGateway,
    val parser: LeafletDocumentParser,
    private val repository: io.leaflet.storage.repo.LibraryRepositoryImpl,
    private val source: DocumentSource,
) {
    fun readerStore(): ReaderStore =
        object : ReaderStore {
            override suspend fun openStream(documentId: DocumentId): InputStream = gateway.openStream(documentId)

            override suspend fun describe(documentId: DocumentId): DocumentDescriptor? = gateway.describe(documentId)

            /**
             * R2 DEF-005: `describe` reads the INDEX ROW (Room) only — it never
             * touches the real file, so an unchanged row cannot prove that the
             * document is still reachable. This probe opens the SAF stream
             * (read-only, closed immediately) and classifies the failure.
             */
            override suspend fun probeAccess(documentId: DocumentId): io.leaflet.reader.ReaderFailure? =
                try {
                    gateway.openStream(documentId).use { it.read() }
                    null
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    io.leaflet.reader.readerFailureReasonOf(e)
                }

            override suspend fun resolveRelative(
                fromId: DocumentId,
                ref: String,
            ): io.leaflet.storage.ResolvedResource? =
                try {
                    source.resolveRelative(fromId, ref)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (_: Exception) {
                    null
                }

            override suspend fun loadPosition(documentId: DocumentId): StoredPosition? {
                // R2-05: the fingerprint MUST be the save-time one from the
                // position row (F06: rebuilding it from the current document
                // row always equals the live fingerprint and disables the
                // stale path). Null row → null (no restore, start at top).
                val snap = repository.loadPositionWithFingerprint(documentId) ?: return null
                return StoredPosition(snap.position, snap.fingerprint)
            }

            override suspend fun savePosition(
                position: ReadingPosition,
                lastModified: Long,
                size: Long,
            ) = repository.savePosition(position, lastModified, size)

            override suspend fun recordRecent(entry: RecentRead) = repository.recordRecent(entry)
        }

    fun recentStore(): RecentStore =
        object : RecentStore {
            override fun observeRecent(limit: Int) = repository.observeRecent(limit)

            override suspend fun resolveLibraryName(libraryId: LibraryId): String? = repository.libraryNameForReader(libraryId)

            override suspend fun checkHealth(entry: RecentRead): RecentHealth {
                val present = repository.hasDocumentRow(entry.documentId)
                if (!present) return RecentHealth.GONE
                return try {
                    source.openStream(entry.documentId).close()
                    RecentHealth.OK
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: StorageException.PermissionLost) {
                    RecentHealth.AUTH_LOST
                } catch (e: SecurityException) {
                    RecentHealth.AUTH_LOST
                } catch (_: Exception) {
                    // Indexed but unreadable: keep the record, report gone.
                    RecentHealth.GONE
                }
            }

            override suspend fun removeRecent(documentId: DocumentId) {
                repository.removeRecent(documentId)
            }

            override suspend fun clearRecent() {
                repository.clearRecent()
            }
        }
}
