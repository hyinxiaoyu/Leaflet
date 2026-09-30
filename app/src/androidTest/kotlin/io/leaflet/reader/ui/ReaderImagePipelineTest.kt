package io.leaflet.reader.ui

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.leaflet.markdown.BlockImage
import io.leaflet.markdown.ImageKind
import io.leaflet.model.DocumentId
import io.leaflet.reader.ImageSample
import io.leaflet.reader.ui.reader.DeviceImage
import io.leaflet.reader.ui.reader.ImageFailure
import io.leaflet.reader.ui.reader.LibraryImageLoader
import io.leaflet.storage.db.DocumentDao
import io.leaflet.storage.db.DocumentEntity
import io.leaflet.storage.db.LibraryDao
import io.leaflet.storage.db.LibraryEntity
import io.leaflet.storage.scan.TreeEntry
import io.leaflet.storage.scan.TreeLister
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream

/**
 * R3-06 closeout: the REAL [LibraryImageLoader] over a live
 * [ContentResolver] backed by a real provider (`androidx.core.content.FileProvider`
 * declared in the debug manifest), plus deterministic negative cases injected
 * through a counting [ContentResolver] wrapper.
 *
 * This is the evidence the fake-loader page tests cannot give: real provider
 * stream, real `BitmapFactory` decode, `inSampleSize` sampling inside the
 * documented decode budget, decoded pixels verified, and cancellation that
 * never touches the provider.
 */
@RunWith(AndroidJUnit4::class)
class ReaderImagePipelineTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private val document =
        DocumentEntity(
            documentId = "doc-1",
            libraryId = "lib-1",
            relativePath = "notes/doc.md",
            uri = "content://stub/document/doc",
            displayName = "doc.md",
            title = "doc",
            extension = "md",
            size = 10,
            lastModified = 10,
            contentHash = null,
        )

    private val library =
        LibraryEntity(
            libraryId = "lib-1",
            displayName = "Docs",
            rootTreeUri = "content://stub/tree/root",
            createdAt = 1,
            lastScannedAt = 1,
        )

    /** Serves the `notes/img/big.png` chain the loader walks. */
    private class PathTreeLister(
        private val segments: List<String>,
        private val fileUri: String,
    ) : TreeLister {
        override suspend fun treeDocumentId(treeUri: String): String = "root"

        override suspend fun listChildren(
            treeUri: String,
            parentDocumentId: String,
        ): List<TreeEntry> {
            val index = if (parentDocumentId == "root") 0 else parentDocumentId.removePrefix("dir-").toInt() + 1
            if (index >= segments.size) return emptyList()
            val last = index == segments.size - 1
            return listOf(
                TreeEntry(
                    documentId = if (last) "file" else "dir-$index",
                    displayName = segments[index],
                    isDirectory = !last,
                    size = 1L,
                    lastModified = 1L,
                    documentUri = if (last) fileUri else "content://stub/dir-$index",
                ),
            )
        }
    }

    private class FixedDocumentDao(private val row: DocumentEntity?) : DocumentDao {
        override fun observeByLibrary(libraryId: String): Flow<List<DocumentEntity>> = flowOf(emptyList())

        override suspend fun listByLibrary(libraryId: String): List<DocumentEntity> = emptyList()

        override suspend fun findById(documentId: String): DocumentEntity? = row?.takeIf { it.documentId == documentId }

        override suspend fun findByPath(
            libraryId: String,
            relativePath: String,
        ): DocumentEntity? = null

        override suspend fun upsert(document: DocumentEntity) = Unit

        override suspend fun update(document: DocumentEntity) = Unit

        override suspend fun deleteById(documentId: String) = Unit

        override suspend fun deleteByLibrary(libraryId: String) = Unit

        override suspend fun countByLibrary(libraryId: String): Int = 0
    }

    private class FixedLibraryDao(private val row: LibraryEntity?) : LibraryDao {
        override fun observeAll(): Flow<List<LibraryEntity>> = flowOf(emptyList())

        override suspend fun list(): List<LibraryEntity> = emptyList()

        override suspend fun findById(libraryId: String): LibraryEntity? = row?.takeIf { it.libraryId == libraryId }

        override suspend fun findByTreeUri(normalizedTreeUri: String): LibraryEntity? = null

        override suspend fun insert(library: LibraryEntity) = Unit

        override suspend fun update(library: LibraryEntity) = Unit

        override suspend fun deleteById(libraryId: String) = Unit
    }

    private fun loader(
        fileUri: String,
        ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO,
    ): LibraryImageLoader =
        LibraryImageLoader(
            documents = FixedDocumentDao(document),
            libraries = FixedLibraryDao(library),
            lister = PathTreeLister(listOf("notes", "img", "big.png"), fileUri),
            contentResolver = context.contentResolver,
            ioDispatcher = ioDispatcher,
        )

    private val ref =
        BlockImage(
            alt = "big",
            destination = "img/big.png",
            kind = ImageKind.LOCAL,
            path = "img/big.png",
        )

    @Before
    fun setUp() {
        StubImageProvider.reset()
    }

    @After
    fun tearDown() {
        StubImageProvider.reset()
    }

    /** A solid-colour PNG of the requested size. */
    private fun png(
        width: Int,
        height: Int,
        color: Int = 0xFF3366CC.toInt(),
    ): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(color)
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        bitmap.recycle()
        return out.toByteArray()
    }

    private val imageUri: String = "content://${context.packageName}.test.images/image/big.png"

    private fun peakPssDuring(block: () -> Unit): Int {
        var peak = 0
        var running = true
        val sampler =
            Thread {
                while (running) {
                    val info = android.os.Debug.MemoryInfo()
                    android.os.Debug.getMemoryInfo(info)
                    if (info.totalPss > peak) peak = info.totalPss
                    Thread.sleep(20)
                }
            }.also {
                it.isDaemon = true
                it.start()
            }
        try {
            block()
        } finally {
            running = false
            sampler.join(2_000)
        }
        return peak
    }

    @Test
    fun oversizedProviderImageIsSampledAndPixelsComeFromTheRealProvider() {
        // 3000x2000 = 24 MB undecoded ARGB; the loader must downsample it.
        StubImageProvider.pngBytes = png(3000, 2000)
        val targetWidth = 300
        var result: DeviceImage? = null

        val peak =
            peakPssDuring {
                result = runBlocking { loader(imageUri).load(DocumentId("doc-1"), ref, targetWidth) }
            }

        val ready = result as? DeviceImage.Ready ?: error("expected Ready, got $result")
        assertTrue("sampled below the source width (was ${ready.widthPx})", ready.widthPx < 3000)
        assertTrue("sampled at least the requested width (was ${ready.widthPx})", ready.widthPx >= targetWidth)
        val decodedBytes = ready.widthPx.toLong() * ready.heightPx.toLong() * ImageSample.BYTES_PER_PIXEL
        assertTrue(
            "decoded bitmap ${ready.widthPx}x${ready.heightPx} = $decodedBytes B must stay inside " +
                "ImageSample.MAX_DECODE_BYTES=${ImageSample.MAX_DECODE_BYTES}",
            decodedBytes <= ImageSample.MAX_DECODE_BYTES,
        )
        assertEquals("the decoded pixel comes from the provider's PNG", 0xFF3366CC.toInt(), ready.bitmap.getPixel(0, 0))
        assertEquals("one bounds pass + one sampled decode", 2, StubImageProvider.openCount)
        println(
            "R3-06 image pipeline | source=3000x2000 target=${targetWidth}px decoded=${ready.widthPx}x${ready.heightPx} " +
                "decodedBytes=$decodedBytes peakPss=${peak}KB",
        )
    }

    @Test
    fun smallImageLoadsAtItsNaturalSize() {
        StubImageProvider.pngBytes = png(120, 80, 0xFF00AA55.toInt())

        val result = runBlocking { loader(imageUri).load(DocumentId("doc-1"), ref, 1080) }

        val ready = result as? DeviceImage.Ready ?: error("expected Ready, got $result")
        assertEquals(120, ready.widthPx)
        assertEquals(80, ready.heightPx)
        assertEquals(0xFF00AA55.toInt(), ready.bitmap.getPixel(10, 10))
    }

    @Test
    fun missingAndRevokedStreamsFailAsMissingWithoutCrashing() {
        StubImageProvider.pngBytes = png(32, 32)
        StubImageProvider.missing = true
        assertEquals(
            DeviceImage.Failed(ImageFailure.MISSING),
            runBlocking { loader(imageUri).load(DocumentId("doc-1"), ref, 300) },
        )

        StubImageProvider.reset()
        StubImageProvider.revoked = true
        assertEquals(
            DeviceImage.Failed(ImageFailure.MISSING),
            runBlocking { loader(imageUri).load(DocumentId("doc-1"), ref, 300) },
        )
    }

    @Test
    fun nonLocalOrPathlessReferencesNeverOpenAStream() {
        StubImageProvider.pngBytes = png(10, 10)
        val loader = loader(imageUri)
        val remote = ref.copy(kind = ImageKind.REMOTE, path = "", destination = "https://example.com/x.png")
        val noPath = ref.copy(path = "")

        assertEquals(
            DeviceImage.Failed(ImageFailure.UNSUPPORTED),
            runBlocking { loader.load(DocumentId("doc-1"), remote, 300) },
        )
        assertEquals(
            "a pathless reference is unsupported, never a provider read",
            DeviceImage.Failed(ImageFailure.UNSUPPORTED),
            runBlocking { loader.load(DocumentId("doc-1"), noPath, 300) },
        )
        assertEquals("no stream open for rejected references", 0, StubImageProvider.openCount)
    }

    @Test
    fun cancelledLoadNeverTouchesTheProvider() =
        runTest {
            StubImageProvider.pngBytes = png(2000, 2000)
            val loader = loader(imageUri, ioDispatcher = StandardTestDispatcher(testScheduler))

            val job = launch { loader.load(DocumentId("doc-1"), ref, 300) }
            job.cancel()
            advanceUntilIdle()

            assertTrue("the cancelled load must not run", job.isCancelled)
            assertEquals("a cancelled load must not open a stream", 0, StubImageProvider.openCount)
        }

    /** Cancelling while the provider is still handing over the stream. */
    @Test
    fun cancellationDuringTheProviderOpenPropagates() {
        StubImageProvider.pngBytes = png(2000, 2000)
        StubImageProvider.openDelayMs = 400
        val loader = loader(imageUri)

        val failure =
            runBlocking {
                val job = async(Dispatchers.IO) { loader.load(DocumentId("doc-1"), ref, 300) }
                assertTrue("the provider open must have started", StubImageProvider.awaitOpenStarted())
                job.cancel()
                try {
                    job.await()
                    null
                } catch (e: kotlinx.coroutines.CancellationException) {
                    e
                }
            }

        assertTrue(
            "cancellation must propagate as CancellationException (got $failure)",
            failure is kotlinx.coroutines.CancellationException,
        )
    }
}
