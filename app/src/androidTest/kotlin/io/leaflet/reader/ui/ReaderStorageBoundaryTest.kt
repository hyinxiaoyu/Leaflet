package io.leaflet.reader.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.leaflet.markdown.LeafletDocumentParser
import io.leaflet.model.DocumentId
import io.leaflet.reader.ReaderFailure
import io.leaflet.reader.ReaderWiring
import io.leaflet.reader.StorageReaderGateway
import io.leaflet.reader.ui.reader.ReaderUiState
import io.leaflet.reader.ui.reader.ReaderViewModel
import io.leaflet.storage.db.DocumentEntity
import io.leaflet.storage.db.LeafletDatabase
import io.leaflet.storage.repo.LibraryRepositoryImpl
import io.leaflet.storage.saf.ContentAccess
import io.leaflet.storage.saf.DocumentSourceImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.InputStream

/**
 * R2 DEF-005/DEF-006 second round: the same-document freshness check against
 * the REAL storage stack — real Room database, real `LibraryRepositoryImpl`,
 * real `DocumentSourceImpl`/`StorageReaderGateway` chain — with only the SAF
 * provider faked at [ContentAccess], the documented seam.
 *
 * This is the boundary the page suite's `FakePageStore` used to hide: the
 * index ROW stays present and its fingerprint stays unchanged while the
 * provider grant is revoked (or the entry disappears). `StorageReaderGateway.
 * describe` reads the row only, so nothing but an actual document access can
 * detect it — the check must therefore surface the recovery states instead of
 * serving the cached content.
 */
@RunWith(AndroidJUnit4::class)
class ReaderStorageBoundaryTest {
    @get:Rule
    val compose = createComposeRule()

    /**
     * SAF seam fake: a switchable grant over in-memory bytes. `revoked` models
     * a revoked persisted grant, `missing` an entry the provider no longer
     * serves; both leave the caller's Room row untouched.
     */
    private class SwitchableContentAccess(
        private val contents: Map<String, ByteArray>,
    ) : ContentAccess {
        var revoked: Boolean = false
        var missing: Boolean = false
        var openCount: Int = 0
            private set

        override fun openInput(uriString: String): InputStream? {
            openCount++
            if (revoked) throw SecurityException("grant revoked")
            val bytes = contents[uriString] ?: throw java.io.FileNotFoundException("gone: $uriString")
            if (missing) throw java.io.FileNotFoundException("gone: $uriString")
            return ByteArrayInputStream(bytes)
        }

        override fun persistedReadUris(): Set<String> = contents.keys

        override fun takeReadPermission(treeUriString: String) = Unit

        override fun releaseReadPermission(treeUriString: String) = Unit
    }

    private class Harness(
        val access: SwitchableContentAccess,
        val db: LeafletDatabase,
        val vm: ReaderViewModel,
    )

    private fun harness(
        documentId: String = "boundary-doc",
        bytes: ByteArray = "# Boundary Proof\n\nbody text\n".toByteArray(),
    ): Harness {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val uri = "content://leaflet.boundary.tree/$documentId.md"
        val access = SwitchableContentAccess(mapOf(uri to bytes))
        val db =
            Room.inMemoryDatabaseBuilder(context, LeafletDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        val repo = LibraryRepositoryImpl(db, access, Dispatchers.IO)
        val source = DocumentSourceImpl(db.documentDao(), access)
        val gateway = StorageReaderGateway(db.documentDao(), source)
        val parser = LeafletDocumentParser.create(context)
        runBlocking {
            val library = repo.addLibrary("Boundary Library", "content://leaflet.boundary.tree")
            db.documentDao().upsert(
                DocumentEntity(
                    documentId = documentId,
                    libraryId = library.id.value,
                    relativePath = "$documentId.md",
                    uri = uri,
                    displayName = "$documentId.md",
                    title = null,
                    extension = "md",
                    size = bytes.size.toLong(),
                    lastModified = 42L,
                    contentHash = null,
                ),
            )
        }
        val wiring = ReaderWiring(gateway, parser, repo, source)
        val vm =
            ReaderViewModel(
                store = wiring.readerStore(),
                parser = parser,
                ioDispatcher = Dispatchers.IO,
                clock = System::currentTimeMillis,
            )
        return Harness(access, db, vm)
    }

    @Test
    fun revokedGrantWithUnchangedRowSurfacesReauthorize() {
        val h = harness()
        try {
            compose.setContent { ReaderScreenShim(documentId = "boundary-doc", vm = h.vm) }
            compose.waitUntil(timeoutMillis = 20_000) {
                h.vm.ui.value.let { it is ReaderUiState.Content && it.document.title == "Boundary Proof" }
            }
            // Real Room row still present, fingerprint unchanged — only the
            // provider grant is gone.
            h.access.revoked = true
            compose.runOnIdle { h.vm.open(DocumentId("boundary-doc")) }
            compose.waitUntil(timeoutMillis = 20_000) {
                (h.vm.ui.value as? ReaderUiState.Failed)?.reason == ReaderFailure.AUTHORIZATION_LOST
            }
            compose.waitUntil(timeoutMillis = 20_000) {
                try {
                    compose.onNodeWithText("Re-authorize folder", substring = true).assertIsDisplayed()
                    true
                } catch (_: AssertionError) {
                    false
                }
            }
        } finally {
            h.db.close()
        }
    }

    @Test
    fun vanishedFileWithUnchangedRowSurfacesGonePrompt() {
        val h = harness(documentId = "boundary-gone")
        try {
            compose.setContent { ReaderScreenShim(documentId = "boundary-gone", vm = h.vm) }
            compose.waitUntil(timeoutMillis = 20_000) {
                h.vm.ui.value is ReaderUiState.Content
            }
            h.access.missing = true
            compose.runOnIdle { h.vm.open(DocumentId("boundary-gone")) }
            compose.waitUntil(timeoutMillis = 20_000) {
                (h.vm.ui.value as? ReaderUiState.Failed)?.reason == ReaderFailure.FILE_MISSING
            }
            compose.waitUntil(timeoutMillis = 20_000) {
                try {
                    compose.onNodeWithText("moved, renamed, or deleted", substring = true).assertIsDisplayed()
                    true
                } catch (_: AssertionError) {
                    false
                }
            }
        } finally {
            h.db.close()
        }
    }

    @Test
    fun reachableUnchangedDocumentIsReusedWithoutReReading() {
        // The other half of the rule: a reachable document with an unchanged
        // fingerprint must NOT be re-read (rotation keeps the parse).
        val h = harness(documentId = "boundary-same")
        try {
            compose.setContent { ReaderScreenShim(documentId = "boundary-same", vm = h.vm) }
            compose.waitUntil(timeoutMillis = 20_000) {
                h.vm.ui.value is ReaderUiState.Content
            }
            val opensAfterFirstLoad = h.access.openCount
            val before = h.vm.ui.value as ReaderUiState.Content
            compose.runOnIdle { h.vm.open(DocumentId("boundary-same")) }
            // A same-document recheck keeps the current content (DEF-013), so
            // there is no state transition to wait for — wait on the probe
            // itself: exactly one reachability open, and no second parse.
            compose.waitUntil(timeoutMillis = 20_000) {
                h.access.openCount > opensAfterFirstLoad || h.vm.ui.value !is ReaderUiState.Content
            }
            compose.waitForIdle()
            assertTrue(
                "still showing the same document",
                h.vm.ui.value is ReaderUiState.Content &&
                    (h.vm.ui.value as ReaderUiState.Content).document === before.document,
            )
            assertEquals(
                "no extra full open beyond the reachability probe",
                opensAfterFirstLoad + 1,
                h.access.openCount,
            )
        } finally {
            h.db.close()
        }
    }
}
