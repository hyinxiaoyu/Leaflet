package io.leaflet.reader.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.leaflet.model.DocumentId
import io.leaflet.model.ReadingPosition
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * R2-07 page regression: error recovery + position persistence on the REAL
 * reader route (production `ReaderScreen` + session/parser, fake storage
 * seam) — REQ-0003 FR-6/AC-6 (gone/auth-lost prompts + retained records),
 * R2-05 wiring (typed storage failures → recovery states), rotation shape
 * (same ViewModel re-open is a no-op, session cache guards the parse).
 */
@RunWith(AndroidJUnit4::class)
class ReaderRecoveryTest {
    @get:Rule
    val compose = createComposeRule()

    private fun vmFor(files: Map<String, ByteArray>): io.leaflet.reader.ui.reader.ReaderViewModel {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return pageViewModel(context, FakePageStore(files, files.mapValues { (k, _) -> k }))
    }

    @Test
    fun missingDocumentShowsGonePrompt() {
        val vm = vmFor(emptyMap())
        compose.setContent {
            ReaderScreenShim(documentId = "gone.md", vm = vm)
        }
        // Production mapping: null describe → FILE_MISSING prompt text.
        compose.waitUntil(timeoutMillis = 15_000) {
            try {
                compose.onNodeWithText("moved, renamed, or deleted", substring = true).assertIsDisplayed()
                true
            } catch (_: AssertionError) {
                false
            }
        }
    }

    @Test
    fun revokedGrantShowsReauthorizePrompt() {
        val blowUp =
            object : io.leaflet.reader.ui.reader.ReaderStore {
                override suspend fun openStream(documentId: DocumentId): java.io.InputStream = throw SecurityException("stub grant revoked")

                override suspend fun describe(documentId: DocumentId): io.leaflet.reader.DocumentDescriptor? =
                    throw SecurityException("stub grant revoked")

                override suspend fun resolveRelative(
                    fromId: DocumentId,
                    ref: String,
                ): io.leaflet.storage.ResolvedResource? = null

                override suspend fun loadPosition(documentId: DocumentId) = null

                override suspend fun savePosition(
                    position: ReadingPosition,
                    lastModified: Long,
                    size: Long,
                ) = Unit

                override suspend fun recordRecent(entry: io.leaflet.model.RecentRead) = Unit
            }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val vm = pageViewModel(context, blowUp)
        compose.setContent {
            ReaderScreenShim(documentId = "locked.md", vm = vm)
        }
        // Production mapping: SecurityException → AUTHORIZATION_LOST prompt.
        compose.waitUntil(timeoutMillis = 15_000) {
            try {
                compose.onNodeWithText("Re-authorize folder", substring = true).assertIsDisplayed()
                true
            } catch (_: AssertionError) {
                false
            }
        }
    }

    @Test
    fun jumpFlushPersistsPositionRow() {
        val md = "# Keep\n\nAlpha\n\nBeta\n\nGamma\n\nDelta\n"
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = FakePageStore(mapOf("keep.md" to md.toByteArray()), mapOf("keep.md" to "keep.md"))
        val vm = pageViewModel(context, store)
        compose.setContent {
            ReaderScreenShim(documentId = "keep.md", vm = vm)
        }
        compose.waitUntil(timeoutMillis = 15_000) {
            try {
                compose.onNodeWithText("Keep").assertIsDisplayed()
                true
            } catch (_: AssertionError) {
                false
            }
        }
        // Jump + flush through the production persist path: the row must
        // reach the store (no setContent-twice: the rebuild half is the
        // next test over a pre-seeded store).
        compose.runOnIdle {
            val doc = (vm.ui.value as io.leaflet.reader.ui.reader.ReaderUiState.Content).document
            check(doc.blocks.size >= 4)
        }
        vm.jumpToBlock(3)
        val flushed = vm.flushPosition()
        compose.runOnIdle {
            kotlinx.coroutines.runBlocking { flushed.join() }
        }
        compose.runOnIdle {
            check(store.savedPositions.contains("keep.md"))
        }
    }

    @Test
    fun reopenRestoresPersistedBlock() {
        // Process-rebuild shape WITHOUT setContent-twice: the store is
        // pre-seeded with a saved row (as jumpFlushPersistsPositionRow
        // produces), so a FRESH ViewModel must restore block 3 from the
        // persisted row, not from RAM.
        val md = "# Keep\n\nAlpha\n\nBeta\n\nGamma\n\nDelta\n"
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = FakePageStore(mapOf("keep.md" to md.toByteArray()), mapOf("keep.md" to "keep.md"))
        // Seed the persisted row BEFORE the screen is composed. Blocking the
        // main thread here would stall the ViewModel's Main-bound scope, so the
        // seed happens outside the composition and only setContent is idled.
        kotlinx.coroutines.runBlocking {
            store.savePosition(
                ReadingPosition(
                    documentId = DocumentId("keep.md"),
                    version = 1,
                    blockIndex = 3,
                    blockOffset = 0,
                    progress = 0.5f,
                    anchor = null,
                ),
                42L,
                md.toByteArray().size.toLong(),
            )
        }
        val vm2 = pageViewModel(context, store)
        compose.setContent {
            ReaderScreenShim(documentId = "keep.md", vm = vm2)
        }
        compose.waitUntil(timeoutMillis = 15_000) {
            val state = vm2.ui.value
            state is io.leaflet.reader.ui.reader.ReaderUiState.Content && state.restoreBlock == 3
        }
    }

    @Test
    fun txtOpensAsLiteralParagraphs() {
        val txt = "# Not a heading\n\n[not-a-link](nowhere) **literal**\n"
        val vm = vmFor(mapOf("note.txt" to txt.toByteArray()))
        compose.setContent {
            ReaderScreenShim(documentId = "note.txt", vm = vm)
        }
        compose.waitUntil(timeoutMillis = 15_000) {
            val state = vm.ui.value
            state is io.leaflet.reader.ui.reader.ReaderUiState.Content &&
                state.document.literalText &&
                state.document.blockTexts.any { it.contains("# Not a heading") }
        }
        // R2 DEF-001/DEF-006: the production screen shows the markers
        // VERBATIM. Literal blocks render as Compose Text (no Markwon
        // AndroidView at all), so the page itself carries the exact string —
        // a re-parsing regression would fail this assertion.
        compose.waitUntil(timeoutMillis = 15_000) {
            try {
                compose.onNodeWithText("# Not a heading", substring = true).assertExists()
                compose.onNodeWithText("[not-a-link](nowhere) **literal**", substring = true).assertExists()
                true
            } catch (_: AssertionError) {
                false
            }
        }
        // ...and the re-parse path is genuinely bypassed: no Markwon view was
        // created for the TXT document.
        assertTrue(
            "no Markwon TextView for a TXT document",
            PageViews.allTextViews(compose).isEmpty(),
        )
    }

    @Test
    fun markdownRendersMarkersAsSpansNotCharacters() {
        // The mirror image of the TXT case (DEF-001 acceptance): the same
        // characters in a .md file MUST become rich text — a real bold span on
        // the REAL TextView — with the markers gone from the bound text.
        val md = "# Heading Proof\n\nBody with **bold** words.\n"
        val vm = vmFor(mapOf("rich.md" to md.toByteArray()))
        compose.setContent {
            ReaderScreenShim(documentId = "rich.md", vm = vm)
        }
        compose.waitUntil(timeoutMillis = 15_000) {
            PageViews.viewTexts(compose).any { it.contains("Body with") }
        }
        val body = PageViews.allTextViews(compose).first { it.text.contains("Body with") }
        assertTrue("markers consumed on device", !body.text.contains("**"))
        assertTrue(
            "bold span applied by Markwon; text=[${body.text}] " +
                "spans=${PageViews.spansOf(body).map { it.javaClass.name }}",
            PageViews.hasBoldSpan(body),
        )
        val heading = PageViews.allTextViews(compose).first { it.text.contains("Heading Proof") }
        assertTrue("heading text has no literal #", !heading.text.contains("#"))
    }

    @Test
    fun reopenAfterExternalEditShowsNewContent() {
        // R2 DEF-005: same ViewModel, same document id — the file changed on
        // disk, so returning to the reader must NOT serve the stale parse.
        val store = FakePageStore(mapOf("live.md" to "# V1\n\nfirst version body\n".toByteArray()))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val vm = pageViewModel(context, store)
        compose.setContent { ReaderScreenShim(documentId = "live.md", vm = vm) }
        compose.waitUntil(timeoutMillis = 15_000) {
            vm.ui.value.let { it is io.leaflet.reader.ui.reader.ReaderUiState.Content && it.document.title == "V1" }
        }
        // External edit, then the user re-enters the document.
        store.setBytes("live.md", "# V2\n\nsecond version body\n".toByteArray())
        compose.runOnIdle { vm.open(io.leaflet.model.DocumentId("live.md")) }
        compose.waitUntil(timeoutMillis = 15_000) {
            vm.ui.value.let { it is io.leaflet.reader.ui.reader.ReaderUiState.Content && it.document.title == "V2" }
        }
        // And the page really rebinds: the old body is gone from the views.
        compose.waitUntil(timeoutMillis = 15_000) {
            PageViews.viewTexts(compose).any { it.contains("second version body") }
        }
        assertTrue(PageViews.viewTexts(compose).none { it.contains("first version body") })
    }

    @Test
    fun reopenAfterDeletionShowsGonePromptInsteadOfCachedContent() {
        // R2 DEF-005: the cached document used to mask the missing file.
        val store = FakePageStore(mapOf("gone-later.md" to "# Still Here\n\nbody\n".toByteArray()))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val vm = pageViewModel(context, store)
        compose.setContent { ReaderScreenShim(documentId = "gone-later.md", vm = vm) }
        compose.waitUntil(timeoutMillis = 15_000) {
            vm.ui.value is io.leaflet.reader.ui.reader.ReaderUiState.Content
        }
        store.remove("gone-later.md")
        compose.runOnIdle { vm.open(io.leaflet.model.DocumentId("gone-later.md")) }
        compose.waitUntil(timeoutMillis = 15_000) {
            val s = vm.ui.value
            s is io.leaflet.reader.ui.reader.ReaderUiState.Failed &&
                s.reason == io.leaflet.reader.ReaderFailure.FILE_MISSING
        }
        // The production screen swaps to the recovery prompt.
        compose.waitUntil(timeoutMillis = 15_000) {
            try {
                compose.onNodeWithText("moved, renamed, or deleted", substring = true).assertIsDisplayed()
                true
            } catch (_: AssertionError) {
                false
            }
        }
    }

    @Test
    fun reopenAfterGrantLossShowsReauthorizeInsteadOfCachedContent() {
        val md = "# Locked Later\n\nbody\n"
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = FakePageStore(mapOf("locked-later.md" to md.toByteArray()))
        val vm = pageViewModel(context, store)
        compose.setContent { ReaderScreenShim(documentId = "locked-later.md", vm = vm) }
        compose.waitUntil(timeoutMillis = 15_000) {
            vm.ui.value is io.leaflet.reader.ui.reader.ReaderUiState.Content
        }
        // The grant is revoked between the two opens: describe now throws.
        store.denyAll = true
        compose.runOnIdle { vm.open(io.leaflet.model.DocumentId("locked-later.md")) }
        compose.waitUntil(timeoutMillis = 15_000) {
            val s = vm.ui.value
            s is io.leaflet.reader.ui.reader.ReaderUiState.Failed &&
                s.reason == io.leaflet.reader.ReaderFailure.AUTHORIZATION_LOST
        }
        compose.waitUntil(timeoutMillis = 15_000) {
            try {
                compose.onNodeWithText("Re-authorize folder", substring = true).assertIsDisplayed()
                true
            } catch (_: AssertionError) {
                false
            }
        }
    }

    @Test
    fun reopenAfterGrantRevocationWithUnchangedIndexShowsReauthorize() {
        // R2 DEF-005 (second round): the Room row AND its fingerprint are
        // unchanged — only the provider grant is gone. The fingerprint
        // comparison alone cannot see this, so the cached content used to
        // survive; the reachability probe must surface AUTHORIZATION_LOST.
        val md = "# Revoked Later\n\nbody\n"
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = FakePageStore(mapOf("revoked-later.md" to md.toByteArray()))
        val vm = pageViewModel(context, store)
        compose.setContent { ReaderScreenShim(documentId = "revoked-later.md", vm = vm) }
        compose.waitUntil(timeoutMillis = 15_000) {
            vm.ui.value is io.leaflet.reader.ui.reader.ReaderUiState.Content
        }
        store.grantRevoked = true
        compose.runOnIdle { vm.open(io.leaflet.model.DocumentId("revoked-later.md")) }
        compose.waitUntil(timeoutMillis = 15_000) {
            val s = vm.ui.value
            s is io.leaflet.reader.ui.reader.ReaderUiState.Failed &&
                s.reason == io.leaflet.reader.ReaderFailure.AUTHORIZATION_LOST
        }
        val doc = vm.ui.value
        assertTrue("state is Failed", doc is io.leaflet.reader.ui.reader.ReaderUiState.Failed)
        compose.waitUntil(timeoutMillis = 15_000) {
            try {
                compose.onNodeWithText("Re-authorize folder", substring = true).assertIsDisplayed()
                true
            } catch (_: AssertionError) {
                false
            }
        }
    }

    @Test
    fun reopenAfterFileVanishesWithUnchangedIndexShowsGonePrompt() {
        // Same gap, file-vanished flavour: row present, fingerprint unchanged,
        // provider has no entry for the document any more.
        val md = "# Vanished Later\n\nbody\n"
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = FakePageStore(mapOf("vanished-later.md" to md.toByteArray()))
        val vm = pageViewModel(context, store)
        compose.setContent { ReaderScreenShim(documentId = "vanished-later.md", vm = vm) }
        compose.waitUntil(timeoutMillis = 15_000) {
            vm.ui.value is io.leaflet.reader.ui.reader.ReaderUiState.Content
        }
        store.fileGone = true
        compose.runOnIdle { vm.open(io.leaflet.model.DocumentId("vanished-later.md")) }
        compose.waitUntil(timeoutMillis = 15_000) {
            val s = vm.ui.value
            s is io.leaflet.reader.ui.reader.ReaderUiState.Failed &&
                s.reason == io.leaflet.reader.ReaderFailure.FILE_MISSING
        }
        compose.waitUntil(timeoutMillis = 15_000) {
            try {
                compose.onNodeWithText("moved, renamed, or deleted", substring = true).assertIsDisplayed()
                true
            } catch (_: AssertionError) {
                false
            }
        }
    }

    @Test
    fun unknownFingerprintNeverCountsAsFresh() {
        // DEF-005 acceptance: an unknown fingerprint is not evidence of
        // freshness — `null == null` must not reuse the cached parse.
        val md = "# Unknown Fingerprint\n\nbody\n"
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = FakePageStore(mapOf("unknown.md" to md.toByteArray()))
        store.unknownFingerprint = true
        val vm = pageViewModel(context, store)
        compose.setContent { ReaderScreenShim(documentId = "unknown.md", vm = vm) }
        compose.waitUntil(timeoutMillis = 15_000) {
            vm.ui.value is io.leaflet.reader.ui.reader.ReaderUiState.Content
        }
        val opensBefore = store.openCount
        compose.runOnIdle { vm.open(io.leaflet.model.DocumentId("unknown.md")) }
        compose.waitUntil(timeoutMillis = 15_000) {
            vm.ui.value is io.leaflet.reader.ui.reader.ReaderUiState.Content && store.openCount > opensBefore
        }
        assertTrue("unknown fingerprint forced a re-read", store.openCount > opensBefore)
    }

    @Test
    fun unchangedReopenKeepsTheSameDocumentWithoutReparsing() {
        // Rotation shape, DEF-005 version: an unchanged fingerprint keeps the
        // SAME ParsedDocument instance, so no re-parse and no position loss.
        val md = "# Same File\n\nAlpha\n\nBeta\n\nGamma\n"
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = FakePageStore(mapOf("same.md" to md.toByteArray()))
        val vm = pageViewModel(context, store)
        compose.setContent { ReaderScreenShim(documentId = "same.md", vm = vm) }
        compose.waitUntil(timeoutMillis = 15_000) {
            vm.ui.value is io.leaflet.reader.ui.reader.ReaderUiState.Content
        }
        val before =
            compose.runOnIdle {
                (vm.ui.value as io.leaflet.reader.ui.reader.ReaderUiState.Content)
            }
        compose.runOnIdle { vm.open(io.leaflet.model.DocumentId("same.md")) }
        // The freshness check is asynchronous; once it settles, Content must be
        // the SAME document instance with the same restore address.
        compose.waitUntil(timeoutMillis = 15_000) {
            val after = vm.ui.value
            after is io.leaflet.reader.ui.reader.ReaderUiState.Content &&
                after.document === before.document &&
                after.restoreBlock == before.restoreBlock
        }
    }

    @Test
    fun refreshRereadsEvenWhenFingerprintIsUnchanged() {
        // DEF-005: the explicit "Refresh" entry must re-read the file — a
        // same-size overwrite can leave mtime:size identical.
        val store = FakePageStore(mapOf("same-size.md" to "# One\n\nAAAA\n".toByteArray()))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val vm = pageViewModel(context, store)
        compose.setContent { ReaderScreenShim(documentId = "same-size.md", vm = vm) }
        compose.waitUntil(timeoutMillis = 15_000) {
            vm.ui.value.let { it is io.leaflet.reader.ui.reader.ReaderUiState.Content && it.document.title == "One" }
        }
        store.setBytes("same-size.md", "# Two\n\nBBBB\n".toByteArray())
        store.forceSameFingerprint = true
        compose.runOnIdle { vm.refresh(io.leaflet.model.DocumentId("same-size.md")) }
        compose.waitUntil(timeoutMillis = 15_000) {
            vm.ui.value.let { it is io.leaflet.reader.ui.reader.ReaderUiState.Content && it.document.title == "Two" }
        }
    }
}
