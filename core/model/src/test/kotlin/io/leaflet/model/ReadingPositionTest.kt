package io.leaflet.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the T01-frozen position contract against silent drift: versioned,
 * block-based, with nullable anchor fallback — never pixel-based.
 */
class ReadingPositionTest {
    @Test
    fun defaultsAreVersionedWithNoAnchor() {
        val position = ReadingPosition(documentId = DocumentId("doc-1"))

        assertEquals(1, position.version)
        assertEquals(0, position.blockIndex)
        assertEquals(0, position.blockOffset)
        assertEquals(0f, position.progress, 0f)
        assertNull(position.anchor)
    }

    @Test
    fun anchorFallbackSurvivesCopy() {
        val position =
            ReadingPosition(
                documentId = DocumentId("doc-1"),
                blockIndex = 42,
                anchor = "Database/ClickHouse#mergetree",
                progress = 0.5f,
            )

        val restored = position.copy()
        assertEquals(1, restored.version)
        assertEquals("Database/ClickHouse#mergetree", restored.anchor)
        assertTrue(restored.progress in 0f..1f)
    }
}
