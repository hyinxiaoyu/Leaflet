package io.leaflet.reader.ui

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileNotFoundException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * R3-06 test-only image provider (debug source set; never shipped in release).
 *
 * Declared with authority `${applicationId}.test.images` in
 * `app/src/debug/AndroidManifest.xml` so it runs in the app's own process and
 * `LibraryImageLoader` can reach it through a REAL
 * `ContentResolver.openInputStream` — the evidence the fake-loader page tests
 * cannot provide.
 *
 * Failure injection mirrors the storage stub providers: [revoked] makes every
 * open throw `SecurityException`, [missing] throws
 * `FileNotFoundException`, `pngBytes == null` looks like an empty provider,
 * and [openDelayMs] + [openStarted] let a test cancel a load while the
 * provider is still handing over the stream.
 */
class StubImageProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun openFile(
        uri: Uri,
        mode: String,
    ): ParcelFileDescriptor? {
        openCount += 1
        openStarted.countDown()
        if (openDelayMs > 0) Thread.sleep(openDelayMs)
        if (revoked) throw SecurityException("stub image grant revoked")
        if (missing) throw FileNotFoundException("$uri")
        val bytes = pngBytes ?: throw FileNotFoundException("$uri")
        val file = File(context!!.cacheDir, "r3-06-stub-image.png")
        file.writeBytes(bytes)
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun getType(uri: Uri): String = "image/png"

    override fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?,
    ): Cursor? = null

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
        /** PNG bytes served by the next open (null models an empty provider). */
        @Volatile
        var pngBytes: ByteArray? = null

        /** Every open throws `FileNotFoundException`. */
        @Volatile
        var missing: Boolean = false

        /** Every open throws `SecurityException` (withdrawn grant). */
        @Volatile
        var revoked: Boolean = false

        /** Artificial provider latency before the stream is handed over. */
        @Volatile
        var openDelayMs: Long = 0

        /** Count of `openFile` calls since [reset]. */
        @Volatile
        var openCount: Int = 0
            private set

        /** Signals that an open has begun (for cancellation timing). */
        @Volatile
        var openStarted: CountDownLatch = CountDownLatch(1)

        fun reset() {
            pngBytes = null
            missing = false
            revoked = false
            openDelayMs = 0
            openCount = 0
            openStarted = CountDownLatch(1)
        }

        fun awaitOpenStarted(timeoutMs: Long = 5_000): Boolean = openStarted.await(timeoutMs, TimeUnit.MILLISECONDS)
    }
}
