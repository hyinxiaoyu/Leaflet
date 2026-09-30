package io.leaflet.storage.db

import io.leaflet.model.DocumentId
import io.leaflet.model.LibraryId
import io.leaflet.model.ReadingPosition
import io.leaflet.model.RecentRead
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Entity <-> model mapping tests: 1:1 contract fields survive the round
 * trip; entity-only columns (timestamps, fingerprints) never leak into
 * models; unknown future position versions pass through untouched (readers
 * treat them as absent downstream, never crash).
 */
class MappersTest {
    @Test
    fun libraryRoundTrip() {
        val entity = LibraryEntity("lib-1", "Docs", "content://tree/docs", 100L, 200L)

        val model = entity.toModel()

        assertEquals(LibraryId("lib-1"), model.id)
        assertEquals("Docs", model.displayName)
        assertEquals("content://tree/docs", model.rootTreeUri)
    }

    @Test
    fun documentRoundTripDropsAuxiliaryColumns() {
        val entity =
            DocumentEntity(
                documentId = "d1",
                libraryId = "lib-1",
                relativePath = "notes/a.md",
                uri = "content://doc/a",
                displayName = "a.md",
                title = "Extracted Title",
                extension = "md",
                size = 42L,
                lastModified = 7L,
                contentHash = "abc",
            )

        val model = entity.toModel()

        assertEquals(DocumentId("d1"), model.id)
        assertEquals(LibraryId("lib-1"), model.libraryId)
        assertEquals("notes/a.md", model.relativePath)
        assertEquals("content://doc/a", model.uri)
        assertEquals("md", model.extension)
    }

    @Test
    fun positionRoundTripPreservesVersionAndAnchor() {
        val entity =
            ReadingPositionEntity(
                documentId = "d1",
                version = 1,
                blockIndex = 9,
                blockOffset = 2,
                progress = 0.5f,
                anchor = "Heading",
                updatedAt = 123L,
                fingerprint = fingerprintOf(7L, 42L),
            )

        val model = entity.toModel()

        assertEquals(
            ReadingPosition(DocumentId("d1"), 1, 9, 2, 0.5f, "Heading"),
            model,
        )
    }

    @Test
    fun unknownFutureVersionPassesThrough() {
        val entity =
            ReadingPositionEntity(
                documentId = "d1",
                version = 99,
                blockIndex = 3,
                blockOffset = 1,
                progress = 0.1f,
                anchor = "H",
                updatedAt = 1L,
                fingerprint = "0:0",
            )

        assertEquals(99, entity.toModel().version)
    }

    @Test
    fun positionToEntityStampsFingerprint() {
        val entity =
            ReadingPosition(DocumentId("d1"), blockIndex = 4).toEntity(
                updatedAt = 500L,
                fingerprint = fingerprintOf(7L, 42L),
            )

        assertEquals(500L, entity.updatedAt)
        assertEquals("7:42", entity.fingerprint)
        assertNull(entity.contentHash)
    }

    @Test
    fun recentRoundTrip() {
        val entity = RecentReadEntity("d1", "lib-1", "Title snapshot", 900L)

        val model = entity.toModel()

        assertEquals(RecentRead(DocumentId("d1"), "Title snapshot", LibraryId("lib-1"), 900L), model)
        assertEquals(entity, model.toEntity())
    }

    @Test
    fun fingerprintFormatsMtimeAndSize() {
        assertEquals("10:5", fingerprintOf(10L, 5L))
        assertTrue(fingerprintOf(0L, 0L).isNotEmpty())
    }
}
