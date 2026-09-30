package io.leaflet.reader.ui

import androidx.compose.runtime.Composable
import io.leaflet.reader.ui.reader.ImageLoader
import io.leaflet.reader.ui.reader.ReaderScreen
import io.leaflet.reader.ui.reader.ReaderViewModel

/**
 * R2-07 image shim: the production [ReaderScreen] with a caller-supplied
 * [ImageLoader] (fake bytes at the SAF seam; production entry path).
 */
@Composable
fun ImageShim(
    documentId: String,
    vm: ReaderViewModel,
    imageLoader: ImageLoader?,
) {
    ReaderScreen(
        viewModel = vm,
        documentId = documentId,
        onBack = {},
        imageLoader = imageLoader,
    )
}
