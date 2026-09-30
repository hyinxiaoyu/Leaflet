package io.leaflet.storage.saf

import io.leaflet.model.DocumentId
import io.leaflet.storage.FakeContentAccess
import io.leaflet.storage.FakeDocumentDao
import io.leaflet.storage.StorageException
import io.leaflet.storage.db.DocumentEntity
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * DocumentSource skeleton tests (overview contract 3, ADR-0002):
 * read-only open by id, typed grant-loss/gone/read failures, caller-owned
 * streams, and in-tree relative resolution (escape/unknown-scheme rejected).
 */
class DocumentSourceImplTest {
    private val dispatcher = UnconfinedTestDispatcher()

    private fun setup(): Fixture {
        val docs = FakeDocumentDao()
        val access = FakeContentAccess()
        return Fixture(DocumentSourceImpl(docs, access), docs, access)
    }

    private fun doc(
        id: String,
        libraryId: String,
        path: String,
        uri: String,
    ): DocumentEntity =
        DocumentEntity(
            documentId = id,
            libraryId = libraryId,
            relativePath = path,
            uri = uri,
            displayName = path.substringAfterLast('/'),
            title = null,
            extension = "md",
            size = 4L,
            lastModified = 9L,
            contentHash = null,
        )

    @Test
    fun openStreamReturnsBytesAndLeavesCloseToCaller() =
        runTest(dispatcher) {
            val f = setup()
            f.docs.upsert(doc("d1", "lib", "a.md", "content://doc/a"))
            f.access.blobs["content://doc/a"] = "hello".toByteArray()

            val text = f.source.openStream(DocumentId("d1")).use { it.readBytes().toString(Charsets.UTF_8) }

            assertEquals("hello", text)
            assertEquals(listOf("content://doc/a"), f.access.closedUris)
        }

    @Test
    fun openUnknownIdIsGone() =
        runTest(dispatcher) {
            val f = setup()

            try {
                f.source.openStream(DocumentId("missing"))
                fail("expected DocumentGone")
            } catch (e: StorageException.DocumentGone) {
                assertEquals("missing", e.documentId)
            }
        }

    @Test
    fun revokedGrantMapsToPermissionLost() =
        runTest(dispatcher) {
            val f = setup()
            f.docs.upsert(doc("d1", "lib", "a.md", "content://doc/a"))
            f.access.revokedUris.add("content://doc/a")

            try {
                f.source.openStream(DocumentId("d1"))
                fail("expected PermissionLost")
            } catch (e: StorageException.PermissionLost) {
                assertEquals("content://doc/a", e.uri)
            }
        }

    @Test
    fun vanishedFileMapsToDocumentGone() =
        runTest(dispatcher) {
            val f = setup()
            f.docs.upsert(doc("d1", "lib", "a.md", "content://doc/a"))
            f.access.goneUris.add("content://doc/a")

            try {
                f.source.openStream(DocumentId("d1"))
                fail("expected DocumentGone")
            } catch (e: StorageException.DocumentGone) {
                assertEquals("d1", e.documentId)
            }
        }

    @Test
    fun nullStreamMapsToDocumentGone() =
        runTest(dispatcher) {
            val f = setup()
            f.docs.upsert(doc("d1", "lib", "a.md", "content://doc/a"))
            f.access.nullStreamUris.add("content://doc/a")

            try {
                f.source.openStream(DocumentId("d1"))
                fail("expected DocumentGone")
            } catch (e: StorageException.DocumentGone) {
                assertEquals("d1", e.documentId)
            }
        }

    @Test
    fun brokenStreamMapsToReadFailed() =
        runTest(dispatcher) {
            val f = setup()
            f.docs.upsert(doc("d1", "lib", "a.md", "content://doc/a"))
            f.access.brokenUris.add("content://doc/a")

            try {
                f.source.openStream(DocumentId("d1"))
                fail("expected ReadFailed")
            } catch (e: StorageException.ReadFailed) {
                assertEquals("d1", e.documentId)
            }
        }

    @Test
    fun resolveSiblingFindsDocument() =
        runTest(dispatcher) {
            val f = setup()
            f.docs.upsert(doc("d1", "lib", "notes/a.md", "content://doc/a"))
            f.docs.upsert(doc("d2", "lib", "notes/b.md", "content://doc/b"))

            val resolved = f.source.resolveRelative(DocumentId("d1"), "./b.md")

            assertEquals(DocumentId("d2"), resolved?.documentId)
            assertEquals("b.md", resolved?.displayName)
        }

    @Test
    fun resolveParentTraversalWithinTree() =
        runTest(dispatcher) {
            val f = setup()
            f.docs.upsert(doc("d1", "lib", "a/b/c.md", "content://doc/c"))
            f.docs.upsert(doc("d2", "lib", "a/top.md", "content://doc/top"))

            val resolved = f.source.resolveRelative(DocumentId("d1"), "../top.md")

            assertEquals(DocumentId("d2"), resolved?.documentId)
        }

    /**
     * R3-06 gap closure: CJK, literal-space and percent-encoded relative
     * references resolved through the REAL [DocumentSourceImpl] + DAO lookup,
     * not just the pure path helper.
     */
    @Test
    fun resolveCjkSpaceAndEncodedReferencesThroughTheRealDao() =
        runTest(dispatcher) {
            val f = setup()
            f.docs.upsert(doc("d1", "lib", "笔记/说明.md", "content://doc/a"))
            f.docs.upsert(doc("d2", "lib", "笔记/空 格.md", "content://doc/b"))
            f.docs.upsert(doc("d3", "lib", "顶层.md", "content://doc/c"))

            assertEquals(
                DocumentId("d2"),
                f.source.resolveRelative(DocumentId("d1"), "空 格.md")?.documentId,
            )
            assertEquals(
                DocumentId("d2"),
                f.source.resolveRelative(DocumentId("d1"), "./%E7%A9%BA%20%E6%A0%BC.md")?.documentId,
            )
            assertEquals(
                DocumentId("d3"),
                f.source.resolveRelative(DocumentId("d1"), "../顶层.md")?.documentId,
            )
            assertNull(f.source.resolveRelative(DocumentId("d1"), "../../顶.md"))
            assertTrue("no stream is opened by resolution", f.access.closedUris.isEmpty())
        }

    @Test
    fun resolveEscapeOrSchemeOrMissingReturnsNull() =
        runTest(dispatcher) {
            val f = setup()
            f.docs.upsert(doc("d1", "lib", "a.md", "content://doc/a"))

            assertNull(f.source.resolveRelative(DocumentId("d1"), "../../escape.md"))
            assertNull(f.source.resolveRelative(DocumentId("d1"), "https://example.com/x.md"))
            assertNull(f.source.resolveRelative(DocumentId("d1"), "foo://bar"))
            assertNull(f.source.resolveRelative(DocumentId("d1"), "#anchor"))
            assertNull(f.source.resolveRelative(DocumentId("d1"), "./missing.md"))
            assertNull(f.source.resolveRelative(DocumentId("d1"), "/absolute.md"))
            assertNull(f.source.resolveRelative(DocumentId("nope"), "./a.md"))
            assertTrue(f.access.closedUris.isEmpty())
        }

    @Test
    fun resolveDoesNotCrossLibraries() =
        runTest(dispatcher) {
            val f = setup()
            f.docs.upsert(doc("d1", "lib-a", "a.md", "content://doc/a"))
            // Same relative path, different library: must NOT resolve.
            f.docs.upsert(doc("d2", "lib-b", "b.md", "content://doc/b"))

            assertNull(f.source.resolveRelative(DocumentId("d1"), "./b.md"))
        }

    private class Fixture(
        val source: DocumentSourceImpl,
        val docs: FakeDocumentDao,
        val access: FakeContentAccess,
    )
}
