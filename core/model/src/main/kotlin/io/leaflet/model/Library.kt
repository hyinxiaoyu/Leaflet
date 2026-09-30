package io.leaflet.model

/**
 * A library: one user-authorized folder root (SAF tree URI) whose Markdown/TXT
 * files Leaflet indexes read-only.
 *
 * @property id internal stable identifier, used as the Room primary key.
 * @property displayName user-visible name chosen at import time.
 * @property rootTreeUri persisted SAF tree URI string; the access handle, not a file path.
 */
data class Library(
    val id: LibraryId,
    val displayName: String,
    val rootTreeUri: String,
)
