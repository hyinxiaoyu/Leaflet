package io.leaflet.storage.saf

import io.leaflet.model.DocumentId
import io.leaflet.storage.DocumentSource
import io.leaflet.storage.ResolvedResource
import io.leaflet.storage.StorageException
import io.leaflet.storage.db.DocumentDao
import io.leaflet.storage.db.DocumentEntity
import java.io.FileNotFoundException
import java.io.InputStream

/**
 * SAF-backed [DocumentSource] skeleton (overview contract 3,
 * ADR-0002). The ONLY document-access exit for the Reader side.
 *
 * Guarantees:
 * - Read-only: streams come from [ContentAccess.openInput], which requests
 *   `"r"` mode only. Nothing in this class writes to the provider, the
 *   database, or the app's private storage (ADR-0001).
 * - Stream ownership: the CALLER closes the returned stream (use `use {}`).
 *   Methods that open a stream internally for probing close it before
 *   returning — no leaked descriptors on any path, including exceptions.
 * - Cancellation transparency: coroutine cancellation is never swallowed or
 *   converted; it propagates to the caller (storage architecture §3.3).
 *   Blocking resolver I/O runs on `Dispatchers.IO` at the repository/call
 *   layer, not hidden here.
 * - Typed recoverable failures: grant loss → [StorageException.PermissionLost],
 *   vanished file → [StorageException.DocumentGone], other I/O →
 *   [StorageException.ReadFailed]. The UI maps these to `ReaderState.Failed`
 *   with a recovery action, never a crash.
 *
 * Resolution ([resolveRelative]): pure library-relative path normalization
 * via [RelativePaths] (in-tree `..` allowed, tree escape rejected, remote
 * and unknown schemes rejected), then a metadata lookup for the normalized
 * path. Content URIs are never built by concatenating path segments —
 * stream access always uses the stored URI of the looked-up document.
 *
 * Persisted grants ([ContentAccess.persistedReadUris] /
 * [takeReadPermission] / [releaseReadPermission]) are exercised only by
 * [io.leaflet.storage.repo.LibraryRepositoryImpl] at add/remove time (T04);
 * T05 owns the picker intent that produces the URI.
 */
class DocumentSourceImpl(
    private val documentDao: DocumentDao,
    private val access: ContentAccess,
) : DocumentSource {
    override suspend fun openStream(documentId: DocumentId): InputStream {
        val row: DocumentEntity =
            documentDao.findById(documentId.value)
                ?: throw StorageException.DocumentGone(documentId.value, null)
        return openUri(row.uri, documentId.value)
    }

    override suspend fun resolveRelative(
        fromDocumentId: DocumentId,
        relativeRef: String,
    ): ResolvedResource? {
        val from: DocumentEntity =
            documentDao.findById(fromDocumentId.value) ?: return null
        val targetPath =
            RelativePaths.resolveSibling(from.relativePath, relativeRef) ?: return null
        val target: DocumentEntity =
            documentDao.findByPath(from.libraryId, targetPath) ?: return null
        return ResolvedResource(
            documentId = DocumentId(target.documentId),
            displayName = target.displayName,
        )
    }

    private fun openUri(
        uriString: String,
        documentId: String,
    ): InputStream {
        try {
            return access.openInput(uriString)
                ?: throw StorageException.DocumentGone(documentId, null)
        } catch (e: SecurityException) {
            throw StorageException.PermissionLost(uriString, e)
        } catch (e: FileNotFoundException) {
            throw StorageException.DocumentGone(documentId, e)
        } catch (e: java.io.IOException) {
            throw StorageException.ReadFailed(documentId, e)
        }
    }
}
