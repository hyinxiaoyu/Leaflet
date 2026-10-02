package io.leaflet.reader.ui.reader

import kotlin.math.roundToInt

/** Character/line coordinates survive typography changes; pixel coordinates do not. */
internal data class BlockTextLines(
    val length: Int,
    val starts: IntArray,
    val tops: FloatArray,
) {
    fun characterAt(y: Float): Int {
        val line = tops.indexOfLast { it <= y }.coerceAtLeast(0)
        return starts.getOrElse(line) { 0 }.coerceIn(0, length)
    }

    fun topFor(character: Int): Float {
        val offset = character.coerceIn(0, length)
        val line = starts.indexOfLast { it <= offset }.coerceAtLeast(0)
        return tops.getOrElse(line) { 0f }
    }
}

/** Holds only composed items; text origins include the item's labels and padding. */
internal class BlockPositionGeometry {
    private class Entry {
        var blockTop = 0f
        var textTop = 0f
        var lines: (() -> BlockTextLines?)? = null
    }

    private val entries = mutableMapOf<Int, Entry>()

    fun blockTop(
        index: Int,
        top: Float,
    ) {
        entries.getOrPut(index) { Entry() }.blockTop = top
    }

    fun textTop(
        index: Int,
        top: Float,
    ) {
        entries.getOrPut(index) { Entry() }.textTop = top
    }

    fun textLines(
        index: Int,
        lines: () -> BlockTextLines?,
    ) {
        entries.getOrPut(index) { Entry() }.lines = lines
    }

    fun remove(index: Int) {
        entries.remove(index)
    }

    fun characterOffset(
        index: Int,
        scrollPx: Int,
        itemHeightPx: Int,
        textLength: Int,
    ): Int {
        val entry = entries[index]
        val lines = entry?.lines?.invoke()
        if (lines != null) return lines.characterAt(scrollPx - (entry.textTop - entry.blockTop))
        if (itemHeightPx <= 0 || textLength <= 0) return 0
        // Composite entries (tables/media) have no single text layout. Keep a
        // bounded character address using their measured extent as a fallback.
        return (scrollPx.coerceAtLeast(0).toDouble() * textLength / itemHeightPx).roundToInt().coerceIn(0, textLength)
    }

    fun scrollOffset(
        index: Int,
        character: Int,
        itemHeightPx: Int,
        textLength: Int,
    ): Int {
        if (character <= 0) return 0
        val entry = entries[index]
        val lines = entry?.lines?.invoke()
        if (lines != null) return (entry.textTop - entry.blockTop + lines.topFor(character)).roundToInt().coerceAtLeast(0)
        if (textLength <= 0 || itemHeightPx <= 0) return 0
        return (character.coerceIn(0, textLength).toDouble() * itemHeightPx / textLength).roundToInt()
    }
}
