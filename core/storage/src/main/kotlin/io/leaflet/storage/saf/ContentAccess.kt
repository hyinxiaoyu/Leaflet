package io.leaflet.storage.saf

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import java.io.InputStream

/**
 * Minimal SAF surface used by `:core:storage`, kept behind an interface so
 * repository logic stays JVM-testable with a fake (real [ContentResolver]
 * behavior is covered by instrumented tests).
 *
 * All URIs cross this boundary as strings (the stored form). Only the
 * production implementation parses them — never by path concatenation
 * (ADR-0002: a content URI is not a file path).
 *
 * Stream ownership: [openInput] returns an open read-only stream; the CALLER
 * closes it (via `use {}`). Implementations never close a stream they return.
 */
interface ContentAccess {
    /**
     * Opens the content for reading (`"r"` only — this boundary never
     * requests write access). Returns null when the provider has no stream;
     * throws `SecurityException` when the grant is gone, `FileNotFoundException`
     * (or `IOException`) when the entry vanished.
     */
    fun openInput(uriString: String): InputStream?

    /** Tree/document URIs currently holding a persisted read grant. */
    fun persistedReadUris(): Set<String>

    /**
     * Persists a read grant for a tree URI chosen via
     * `ACTION_OPEN_DOCUMENT_TREE`. Called by [io.leaflet.storage.repo.LibraryRepositoryImpl.addLibrary]
     * immediately after the row is inserted (T05 owns the picker + intent
     * that produces the URI; this call only persists what the user granted).
     */
    fun takeReadPermission(treeUriString: String)

    /**
     * Releases a persisted read grant. Called ONLY from
     * [io.leaflet.storage.repo.LibraryRepositoryImpl.removeLibrary] after it
     * has verified no remaining library shares the grant (T05 never calls
     * this during scanning).
     */
    fun releaseReadPermission(treeUriString: String)
}

/** Production [ContentAccess] backed by the real [ContentResolver] (read-only). */
class ContentResolverAccess(private val resolver: ContentResolver) : ContentAccess {
    override fun openInput(uriString: String): InputStream? = resolver.openInputStream(Uri.parse(uriString))

    override fun persistedReadUris(): Set<String> =
        resolver.persistedUriPermissions
            .filter { it.isReadPermission }
            .map { it.uri.toString() }
            .toSet()

    override fun takeReadPermission(treeUriString: String) {
        resolver.takePersistableUriPermission(
            Uri.parse(treeUriString),
            Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
    }

    override fun releaseReadPermission(treeUriString: String) {
        resolver.releasePersistableUriPermission(
            Uri.parse(treeUriString),
            Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
    }
}
