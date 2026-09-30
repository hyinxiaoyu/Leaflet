package io.leaflet.storage.saf

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileNotFoundException

/**
 * Stub [ContentProvider] serving byte blobs for SAF instrumented tests (no
 * system picker, no real user grant). Manifest-registered under
 * `io.leaflet.storage.stub` via `src/androidTest/AndroidManifest.xml`.
 *
 * Flip [revoked] to model a withdrawn grant (every open throws
 * `SecurityException`); leave [blobs] empty to model a vanished file
 * (`FileNotFoundException`). A stub provider cannot issue persistable grants
 * the way the system DocumentsProvider does, so process-rebuild persistence
 * of a real `takePersistableUriPermission` grant is UNVERIFIED here and owned
 * by T05's real-provider pass.
 */
class StubProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun openFile(
        uri: Uri,
        mode: String,
    ): ParcelFileDescriptor? {
        if (revoked) throw SecurityException("stub grant revoked")
        val name = uri.lastPathSegment ?: throw FileNotFoundException("$uri")
        val bytes = blobs[name] ?: throw FileNotFoundException("$uri")
        val file = File(context!!.cacheDir, "t04-stub-$name").also { it.writeBytes(bytes) }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?,
    ): Cursor = MatrixCursor(arrayOf("_id"))

    override fun query(
        uri: Uri,
        projection: Array<String>?,
        queryArgs: Bundle?,
        cancellationSignal: CancellationSignal?,
    ): Cursor = MatrixCursor(arrayOf("_id"))

    override fun getType(uri: Uri): String? = "text/markdown"

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

    companion object {
        val blobs = mutableMapOf<String, ByteArray>()
        var revoked = false

        fun uri(name: String): String = "content://io.leaflet.storage.stub/$name"
    }
}
