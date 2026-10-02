package io.leaflet.reader.ui.reader

internal data class ImageDimensions(val widthPx: Int, val heightPx: Int)

/** Fit decoded physical pixels into the viewport without upscaling or clamping aspect ratio. */
internal fun boundedImageDimensions(
    widthPx: Int,
    heightPx: Int,
    maxWidthPx: Int,
    maxHeightPx: Int,
): ImageDimensions {
    require(widthPx > 0 && heightPx > 0 && maxWidthPx > 0 && maxHeightPx > 0)
    val scale = minOf(1.0, maxWidthPx.toDouble() / widthPx, maxHeightPx.toDouble() / heightPx)
    return ImageDimensions(
        (widthPx * scale).toInt().coerceAtLeast(1),
        (heightPx * scale).toInt().coerceAtLeast(1),
    )
}
