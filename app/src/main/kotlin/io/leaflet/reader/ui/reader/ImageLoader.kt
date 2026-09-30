package io.leaflet.reader.ui.reader

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import io.leaflet.markdown.BlockImage
import io.leaflet.markdown.ImageKind
import io.leaflet.markdown.ResourceLinks
import io.leaflet.model.DocumentId
import io.leaflet.reader.ImageSample
import io.leaflet.storage.db.DocumentDao
import io.leaflet.storage.db.LibraryDao
import io.leaflet.storage.scan.TreeLister
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.InputStream

/**
 * Image load result for one in-text image (T10, REQ-0009 FR-6–FR-9).
 *
 * Bitmaps are loaded per entry with no shared cache, so closing the
 * document (or recycling the entry) simply drops the reference — there is
 * no cache to invalidate and nothing is copied to app storage (REQ-0009
 * Storage §101–102).
 */
sealed interface DeviceImage {
    data class Ready(val bitmap: Bitmap, val widthPx: Int, val heightPx: Int) : DeviceImage

    data class Failed(val reason: ImageFailure) : DeviceImage
}

enum class ImageFailure {
    MISSING,
    BROKEN,
    UNSUPPORTED,
}

/**
 * SAF-backed image source for in-text local images (T10).
 *
 * Resolution never concatenates content URIs: the authored ref is
 * normalized with [ResourceLinks.resolveImagePath] (in-tree `..` allowed,
 * escape rejected — the same mirror `:core:markdown` tests pin), then the
 * library tree is walked from its root via [TreeLister] with exact
 * display-name matches, and the stream opens from the provider-issued
 * document URI only. Remote destinations never reach this loader (the UI
 * placeholders them without a call), so no code path here can issue a
 * network request.
 */
interface ImageLoader {
    suspend fun load(
        fromId: DocumentId,
        ref: BlockImage,
        targetWidthPx: Int,
    ): DeviceImage
}

class LibraryImageLoader(
    private val documents: DocumentDao,
    private val libraries: LibraryDao,
    private val lister: TreeLister,
    private val contentResolver: ContentResolver,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ImageLoader {
    override suspend fun load(
        fromId: DocumentId,
        ref: BlockImage,
        targetWidthPx: Int,
    ): DeviceImage =
        withContext(ioDispatcher) {
            ensureActive()
            if (ref.kind != ImageKind.LOCAL || ref.path.isEmpty()) {
                return@withContext DeviceImage.Failed(ImageFailure.UNSUPPORTED)
            }
            val fromRow =
                try {
                    documents.findById(fromId.value)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    null
                } ?: return@withContext DeviceImage.Failed(ImageFailure.MISSING)
            val library =
                try {
                    libraries.findById(fromRow.libraryId)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    null
                } ?: return@withContext DeviceImage.Failed(ImageFailure.MISSING)
            val normalized =
                ResourceLinks.resolveImagePath(fromRow.relativePath, ref.path)
                    ?: return@withContext DeviceImage.Failed(ImageFailure.MISSING)
            val uriString =
                findImageUri(library.rootTreeUri, normalized)
                    ?: return@withContext DeviceImage.Failed(ImageFailure.MISSING)
            ensureActive()
            decodeSampled(uriString, targetWidthPx.coerceAtLeast(1))
        }

    private suspend fun findImageUri(
        treeUri: String,
        normalizedPath: String,
    ): String? {
        val segments = normalizedPath.split('/')
        val rootId =
            try {
                lister.treeDocumentId(treeUri)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                return null
            }
        var parentId = rootId
        for (index in segments.indices) {
            currentCoroutineContext().ensureActive()
            val last = index == segments.size - 1
            val children =
                try {
                    lister.listChildren(treeUri, parentId)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    return null
                }
            // Exact display-name match only (mirrors the document index
            // lookup's explainability; case-only variants resolve only when
            // the provider names match exactly — REQ-0009 Edge Cases).
            val hit =
                children.firstOrNull {
                    it.documentId.isNotEmpty() && it.displayName == segments[index] && it.isDirectory != last
                } ?: return null
            if (last) return hit.documentUri
            parentId = hit.documentId
        }
        return null
    }

    private suspend fun decodeSampled(
        uriString: String,
        reqWidthPx: Int,
    ): DeviceImage {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        // NOTE: with inJustDecodeBounds the decode RESULT is always null by
        // design (dims land in `bounds`); only a null STREAM means MISSING.
        // The old `open(..)?.use { decode } ?: MISSING` Elvis mistook the
        // null bitmap for a failed open and failed EVERY image (R2-03 F04).
        (open(uriString) ?: return DeviceImage.Failed(ImageFailure.MISSING)).use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        val srcWidth = bounds.outWidth
        val srcHeight = bounds.outHeight
        if (srcWidth <= 0 || srcHeight <= 0) return DeviceImage.Failed(ImageFailure.BROKEN)
        val sample = ImageSample.sampleFor(srcWidth, srcHeight, reqWidthPx)
        currentCoroutineContext().ensureActive()
        val options =
            BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
        val bitmap =
            try {
                open(uriString)?.use { BitmapFactory.decodeStream(it, null, options) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            } ?: return DeviceImage.Failed(ImageFailure.BROKEN)
        if (bitmap == null) return DeviceImage.Failed(ImageFailure.BROKEN)
        return DeviceImage.Ready(bitmap, bitmap.width, bitmap.height)
    }

    private fun open(uriString: String): InputStream? =
        try {
            contentResolver.openInputStream(Uri.parse(uriString))
        } catch (e: SecurityException) {
            // Lost grant → placeholder; document-level recovery owns the prompt.
            null
        } catch (e: Exception) {
            null
        }
}
