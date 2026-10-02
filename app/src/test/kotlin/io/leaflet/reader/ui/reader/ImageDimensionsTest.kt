package io.leaflet.reader.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class ImageDimensionsTest {
    @Test
    fun tinyIconKeepsItsPhysicalPixelSize() {
        assertEquals(ImageDimensions(16, 16), boundedImageDimensions(16, 16, 1080, 960))
    }

    @Test
    fun photoIsLimitedByViewportWidth() {
        assertEquals(ImageDimensions(300, 225), boundedImageDimensions(800, 600, 300, 320))
    }

    @Test
    fun tallImageIsLimitedByHeightWithoutAspectClamp() {
        assertEquals(ImageDimensions(32, 320), boundedImageDimensions(100, 1000, 300, 320))
    }

    @Test
    fun panoramicImageKeepsItsAspectRatio() {
        assertEquals(ImageDimensions(300, 3), boundedImageDimensions(3000, 30, 300, 320))
    }

    @Test
    fun subpixelDimensionStillOccupiesOnePixel() {
        assertEquals(ImageDimensions(300, 1), boundedImageDimensions(Int.MAX_VALUE, 1, 300, 320))
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidBoundsAreRejected() {
        boundedImageDimensions(16, 16, 0, 320)
    }
}
