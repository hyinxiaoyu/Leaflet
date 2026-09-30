package io.leaflet.model

/**
 * Application-internal stable identifier for a document.
 *
 * This is the only long-term key for reading positions, recent reads and
 * navigation arguments. It is typically the stringified Room row id (UUID or
 * auto-increment key) and MUST NOT be equated with the content URI.
 */
@JvmInline
value class DocumentId(val value: String)

/** Application-internal stable identifier for a library (an authorized folder root). */
@JvmInline
value class LibraryId(val value: String)

/** Identifier of a manual library scan job, for observing scan progress. */
@JvmInline
value class ScanJobId(val value: String)
