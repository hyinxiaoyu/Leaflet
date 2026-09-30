package io.leaflet.storage.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room row for one user-authorized folder root (REQ-0001 FR-1).
 *
 * The [rootTreeUri] column is the persisted SAF tree URI string: the access
 * handle, never a file path. It carries a unique index so repeated adds of
 * the same tree resolve to the existing row (REQ-0001 FR-1/AC-2).
 *
 * Folder-first rule (ADR-0001): deleting this row cascades to local index
 * rows only and MUST never touch the source files.
 */
@Entity(
    tableName = "libraries",
    indices = [Index(value = ["rootTreeUri"], unique = true)],
)
data class LibraryEntity(
    @PrimaryKey val libraryId: String,
    val displayName: String,
    val rootTreeUri: String,
    val createdAt: Long,
    val lastScannedAt: Long?,
)

/**
 * Room row for one indexed Markdown/TXT file (REQ-0001 FR-2).
 *
 * Only metadata is stored: no body text ever enters this table (AGENTS.md
 * storage hard constraint, ADR-0001). [uri] is the content URI string used
 * to open a read-only stream; [relativePath] is the `/`-separated path
 * relative to the library root, used for display and in-tree resolution.
 */
@Entity(
    tableName = "documents",
    foreignKeys = [
        ForeignKey(
            entity = LibraryEntity::class,
            parentColumns = ["libraryId"],
            childColumns = ["libraryId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["libraryId"]),
        Index(value = ["libraryId", "relativePath"], unique = true),
    ],
)
data class DocumentEntity(
    @PrimaryKey val documentId: String,
    val libraryId: String,
    val relativePath: String,
    val uri: String,
    val displayName: String,
    val title: String?,
    val extension: String,
    val size: Long,
    val lastModified: Long,
    val contentHash: String?,
)

/**
 * Room row for one indexed directory (REQ-0001 A1 / LT-3).
 *
 * Metadata only: the `/`-separated path relative to the library root, so the
 * browse screen can show a folder that contains no indexed Markdown/TXT file
 * (an empty directory, or one holding only unsupported files). The scanner
 * never opens a stream for a directory and never copies or edits originals
 * (ADR-0001); [relativePath] is the whole payload.
 *
 * The composite primary key dedupes repeated visits of one path inside a
 * single transaction, and the library foreign key cascades local rows when
 * the user removes the Library — source folders are never touched.
 */
@Entity(
    tableName = "directories",
    primaryKeys = ["libraryId", "relativePath"],
    foreignKeys = [
        ForeignKey(
            entity = LibraryEntity::class,
            parentColumns = ["libraryId"],
            childColumns = ["libraryId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["libraryId"])],
)
data class DirectoryEntity(
    val libraryId: String,
    val relativePath: String,
)

/**
 * Room row for the versioned long-term reading position of one document
 * (REQ-0003 FR-1, overview contract 5).
 *
 * The block/offset/progress/anchor columns mirror
 * [io.leaflet.model.ReadingPosition] 1:1. The extra columns are T07 inputs:
 * [updatedAt] records when the row was written (throttle/debug evidence) and
 * [fingerprint] carries the document identity observed at save time
 * (`"$lastModified:$size"`, or a content hash once T05/T13 introduces one).
 * T07 compares the stored fingerprint with the live file before trusting
 * block indexes, then falls back anchor -> progress -> top.
 *
 * Unknown future [version] values MUST be treated as absent by readers
 * (fall back to top), never crash (REQ-0003 edge case).
 */
@Entity(
    tableName = "reading_positions",
    foreignKeys = [
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["documentId"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class ReadingPositionEntity(
    @PrimaryKey val documentId: String,
    val version: Int = 1,
    val blockIndex: Int = 0,
    val blockOffset: Int = 0,
    val progress: Float = 0f,
    val anchor: String? = null,
    val updatedAt: Long = 0L,
    val fingerprint: String? = null,
    val contentHash: String? = null,
)

/**
 * Room row for one most-recently-read entry (REQ-0003 FR-5).
 *
 * Deliberately NOT a foreign key to `documents`: when a source file is
 * deleted or its authorization is lost, the entry is kept so the UI can show
 * the title snapshot with a recoverable "removed / re-authorize" prompt
 * (REQ-0003 FR-6/AC-6) instead of silently vanishing. Removing a Library or
 * clearing the database still deletes these rows with the rest of the local
 * references; source files are never affected.
 */
@Entity(
    tableName = "recent_reads",
    indices = [Index(value = ["lastOpenedAt"])],
)
data class RecentReadEntity(
    @PrimaryKey val documentId: String,
    val libraryId: String,
    val titleSnapshot: String,
    val lastOpenedAt: Long,
)
