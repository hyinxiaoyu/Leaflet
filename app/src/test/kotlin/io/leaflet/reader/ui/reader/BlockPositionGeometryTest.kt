package io.leaflet.reader.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class BlockPositionGeometryTest {
    @Test
    fun textLinesMapCharactersAcrossDifferentFontLayouts() {
        val compact = BlockTextLines(30, intArrayOf(0, 10, 20), floatArrayOf(0f, 20f, 40f))
        val large = BlockTextLines(30, intArrayOf(0, 5, 10, 15, 20, 25), floatArrayOf(0f, 30f, 60f, 90f, 120f, 150f))
        assertEquals(20, compact.characterAt(45f))
        assertEquals(120f, large.topFor(20))
        assertEquals(20, large.characterAt(large.topFor(20)))
    }

    @Test
    fun textOriginAccountsForItemPaddingAndCodeLabels() {
        val geometry = BlockPositionGeometry()
        geometry.blockTop(2, 100f)
        geometry.textTop(2, 112f)
        geometry.textLines(2) { BlockTextLines(30, intArrayOf(0, 10, 20), floatArrayOf(0f, 20f, 40f)) }
        assertEquals(10, geometry.characterOffset(2, 35, 100, 30))
        assertEquals(52, geometry.scrollOffset(2, 20, 100, 30))
    }

    @Test
    fun compositeEntriesUseBoundedCharacterFallback() {
        val geometry = BlockPositionGeometry()
        assertEquals(250, geometry.characterOffset(0, 100, 400, 1000))
        assertEquals(200, geometry.scrollOffset(0, 500, 400, 1000))
        assertEquals(1000, geometry.characterOffset(0, 800, 400, 1000))
        assertEquals(0, geometry.characterOffset(0, 50, 0, 0))
    }

    @Test
    fun disposedItemDropsItsOldTextLayout() {
        val geometry = BlockPositionGeometry()
        geometry.textLines(1) { BlockTextLines(100, intArrayOf(0, 20), floatArrayOf(0f, 20f)) }
        geometry.remove(1)
        assertEquals(50, geometry.characterOffset(1, 50, 100, 100))
    }
}
