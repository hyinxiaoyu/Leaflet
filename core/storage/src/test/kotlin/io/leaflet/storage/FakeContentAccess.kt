package io.leaflet.storage

import io.leaflet.storage.saf.ContentAccess
import java.io.ByteArrayInputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream

/**
 * In-memory [ContentAccess] fake: URI string -> bytes.
 *
 * Knows three failure modes plus the closed-stream contract:
 * - [revokedUris]: `openInput` throws `SecurityException` (grant loss).
 * - [goneUris]: `openInput` throws `FileNotFoundException` (file vanished).
 * - [brokenUris]: `openInput` throws `IOException` (other I/O failure).
 * - Returned streams record closure in [closedUris] so tests can assert the
 *   caller closed what it opened (streams here are only probes — production
 *   ownership stays with the caller via `use {}`).
 */
class FakeContentAccess(
    val blobs: MutableMap<String, ByteArray> = mutableMapOf(),
    val revokedUris: MutableSet<String> = mutableSetOf(),
    val goneUris: MutableSet<String> = mutableSetOf(),
    val brokenUris: MutableSet<String> = mutableSetOf(),
) : ContentAccess {
    val takenPermissions = mutableListOf<String>()
    val releasedPermissions = mutableListOf<String>()
    val grantedPermissions = mutableSetOf<String>()
    val closedUris = mutableListOf<String>()
    var takePermissionFailure: RuntimeException? = null
    var releasePermissionFailure: RuntimeException? = null
    var nullStreamUris: MutableSet<String> = mutableSetOf()

    override fun openInput(uriString: String): InputStream? {
        if (uriString in revokedUris) throw SecurityException("grant revoked: $uriString")
        if (uriString in goneUris) throw FileNotFoundException("gone: $uriString")
        if (uriString in brokenUris) throw IOException("broken: $uriString")
        if (uriString in nullStreamUris) return null
        val bytes = blobs[uriString] ?: throw FileNotFoundException("unknown: $uriString")
        return TrackingInputStream(uriString, bytes, onClose = { closedUris.add(uriString) })
    }

    override fun persistedReadUris(): Set<String> = grantedPermissions.toSet()

    override fun takeReadPermission(treeUriString: String) {
        takePermissionFailure?.let { throw it }
        takenPermissions.add(treeUriString)
        grantedPermissions.add(treeUriString)
    }

    override fun releaseReadPermission(treeUriString: String) {
        releasePermissionFailure?.let { throw it }
        releasedPermissions.add(treeUriString)
        grantedPermissions.remove(treeUriString)
    }

    private class TrackingInputStream(
        uri: String,
        bytes: ByteArray,
        private val onClose: (String) -> Unit,
    ) : InputStream() {
        private val delegate = ByteArrayInputStream(bytes)
        private val uriString = uri

        override fun read(): Int = delegate.read()

        override fun read(
            b: ByteArray,
            off: Int,
            len: Int,
        ): Int = delegate.read(b, off, len)

        override fun close() {
            delegate.close()
            onClose(uriString)
        }
    }
}
