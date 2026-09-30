package io.leaflet.storage.db

import io.leaflet.model.Document
import io.leaflet.model.DocumentId
import io.leaflet.model.Library
import io.leaflet.model.LibraryId
import io.leaflet.model.ReadingPosition
import io.leaflet.model.RecentRead

/**
 * Entity <-> model conversions (T01-frozen model shapes are the authority;
 * entities carry extra columns — timestamps, fingerprints — that models
 * deliberately omit).
 *
 * Unknown future [ReadingPositionEntity.version] values pass through
 * untouched here; readers MUST treat them as absent (fall back to top),
 * never crash (REQ-0003 edge case).
 *
 * Document identity fingerprint for staleness checks: `mtime:size`
 * (see [fingerprintOf]).
 */
fun fingerprintOf(
    lastModified: Long,
    size: Long,
): String = "$lastModified:$size"

fun LibraryEntity.toModel(): Library = Library(id = LibraryId(libraryId), displayName = displayName, rootTreeUri = rootTreeUri)

fun DocumentEntity.toModel(): Document =
    Document(
        id = DocumentId(documentId),
        libraryId = LibraryId(libraryId),
        displayName = displayName,
        relativePath = relativePath,
        uri = uri,
        extension = extension,
    )

fun ReadingPositionEntity.toModel(): ReadingPosition =
    ReadingPosition(
        documentId = DocumentId(documentId),
        version = version,
        blockIndex = blockIndex,
        blockOffset = blockOffset,
        progress = progress,
        anchor = anchor,
    )

fun ReadingPosition.toEntity(
    updatedAt: Long,
    fingerprint: String?,
    contentHash: String? = null,
): ReadingPositionEntity =
    ReadingPositionEntity(
        documentId = documentId.value,
        version = version,
        blockIndex = blockIndex,
        blockOffset = blockOffset,
        progress = progress,
        anchor = anchor,
        updatedAt = updatedAt,
        fingerprint = fingerprint,
        contentHash = contentHash,
    )

fun RecentReadEntity.toModel(): RecentRead =
    RecentRead(
        documentId = DocumentId(documentId),
        titleSnapshot = titleSnapshot,
        libraryId = LibraryId(libraryId),
        lastOpenedAtEpochMs = lastOpenedAt,
    )

fun RecentRead.toEntity(): RecentReadEntity =
    RecentReadEntity(
        documentId = documentId.value,
        libraryId = libraryId.value,
        titleSnapshot = titleSnapshot,
        lastOpenedAt = lastOpenedAtEpochMs,
    )
