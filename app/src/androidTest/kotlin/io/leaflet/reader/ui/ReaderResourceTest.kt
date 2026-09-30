package io.leaflet.reader.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.leaflet.markdown.BlockImage
import io.leaflet.markdown.ImageKind
import io.leaflet.model.DocumentId
import io.leaflet.reader.SettingsViewModel
import io.leaflet.reader.ui.reader.DeviceImage
import io.leaflet.reader.ui.reader.ImageFailure
import io.leaflet.reader.ui.reader.ImageLoader
import io.leaflet.reader.ui.reader.ReaderScreen
import io.leaflet.reader.ui.reader.ReaderUiState
import io.leaflet.reader.ui.reader.ReaderViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * R3-06 acceptance on the REAL reader route: link following, rejected links,
 * external handoff, and in-text image placeholders (REQ-0009).
 *
 * The storage seam fakes in-tree resolution ([FakePageStore.relativeResolution]);
 * the production `followLink` decision path, `ReaderScreen` link buttons,
 * notice rendering, and image entries are the real code. The production
 * `LibraryImageLoader` decode path (real provider, sampling, peak memory) is
 * NOT covered here — see the R3-06 delivery report.
 */
@RunWith(AndroidJUnit4::class)
class ReaderResourceTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun openPage(
        files: Map<String, ByteArray>,
        docId: String = "res.md",
        settings: SettingsViewModel = pageSettingsViewModel(),
        relativeResolution: Map<String, String> = emptyMap(),
        imageLoader: ImageLoader? = null,
        onOpenExternal: (String) -> Unit = {},
        onOpenDocument: (DocumentId) -> Unit = {},
        expect: String,
    ): ReaderViewModel {
        val store = FakePageStore(files, files.keys.associateWith { it })
        store.relativeResolution = relativeResolution
        return composePage(store, docId, settings, imageLoader, onOpenExternal, onOpenDocument, expect)
    }

    private fun composePage(
        store: FakePageStore,
        docId: String,
        settings: SettingsViewModel,
        imageLoader: ImageLoader?,
        onOpenExternal: (String) -> Unit,
        onOpenDocument: (DocumentId) -> Unit,
        expect: String,
    ): ReaderViewModel {
        val vm = pageViewModel(context, store)
        compose.setContent {
            ReaderScreen(
                viewModel = vm,
                documentId = docId,
                onBack = {},
                settingsViewModel = settings,
                imageLoader = imageLoader,
                onOpenDocument = onOpenDocument,
                onOpenExternal = onOpenExternal,
            )
        }
        compose.waitUntil(timeoutMillis = 20_000) {
            PageViews.viewTexts(compose).any { it.contains(expect) } ||
                PageViews.composeTexts(compose).any { it.contains(expect) }
        }
        return vm
    }

    private val progressLine = Regex("^(\\d+) / (\\d+) · (\\d+)%$")

    private fun firstBlock(): Int {
        val text =
            PageViews.composeTexts(compose).firstOrNull { progressLine.matches(it.trim()) }
                ?: error("no progress line")
        return progressLine.find(text.trim())!!.groupValues[1].toInt()
    }

    // -- links ----------------------------------------------------------------

    /** An HTTPS link is handed to the OS unchanged, on the user's tap. */
    @Test
    fun externalLinkTapHandsTheHttpsUrlToTheOs() {
        val opened = mutableListOf<String>()
        val vm =
            openPage(
                files = mapOf("res.md" to "Text with [the site](https://example.com/page?a=1) inside.\n".toByteArray()),
                onOpenExternal = { opened.add(it) },
                expect = "Text with",
            )
        val documentBefore = compose.runOnIdle { (vm.ui.value as ReaderUiState.Content).document }

        compose.onNodeWithText("↗ the site").performClick()
        compose.waitForIdle()

        assertEquals(listOf("https://example.com/page?a=1"), opened)
        val documentAfter = compose.runOnIdle { (vm.ui.value as ReaderUiState.Content).document }
        assertTrue("an external tap keeps the current document", documentAfter === documentBefore)
    }

    /** No handler for the URL: the recoverable notice replaces a silent drop. */
    @Test
    fun missingExternalAppShowsTheRecoverableNotice() {
        val store =
            FakePageStore(
                mapOf("res.md" to "Text with [the site](https://example.com/none) inside.\n".toByteArray()),
                mapOf("res.md" to "res.md"),
            )
        val vm = pageViewModel(context, store)
        compose.setContent {
            ReaderScreen(
                viewModel = vm,
                documentId = "res.md",
                onBack = {},
                // Models the production branch the R3-06 fix added: the
                // launcher reports that nothing could take the URL.
                onOpenExternal = { vm.reportNoExternalApp() },
            )
        }
        compose.waitUntil(timeoutMillis = 20_000) {
            PageViews.viewTexts(compose).any { it.contains("Text with") }
        }
        val documentBefore = compose.runOnIdle { (vm.ui.value as ReaderUiState.Content).document }

        compose.onNodeWithText("↗ the site").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("没有可打开该链接的应用").assertIsDisplayed()
        val documentAfter = compose.runOnIdle { (vm.ui.value as ReaderUiState.Content).document }
        assertTrue("the document survives a missing external handler", documentAfter === documentBefore)
    }

    /** `other.md#anchor`: opens the target document and lands on the anchor. */
    @Test
    fun crossDocumentFragmentLinkOpensTheTargetAndLandsOnTheAnchor() {
        val source = "Intro text with [the section](other.md#section-two) inside.\n"
        val target =
            buildString {
                append("# Other Document\n\n")
                repeat(6) { append("Filler paragraph $it alpha beta gamma delta epsilon.\n\n") }
                append("## Section Two\n\n")
                append("THE-ANCHOR-BODY text after the anchor.\n\n")
            }
        val opened = mutableListOf<String>()
        val vm =
            openPage(
                files = mapOf("res.md" to source.toByteArray(), "other.md" to target.toByteArray()),
                relativeResolution = mapOf("other.md" to "other.md", "other.md#section-two" to "other.md"),
                onOpenDocument = { opened.add(it.value) },
                expect = "Intro text",
            )

        compose.onNodeWithText("the section").performClick()
        compose.waitUntil(timeoutMillis = 20_000) { opened.isNotEmpty() }
        assertEquals(listOf("other.md"), opened)

        // The production navigation call opens the target; the screen then
        // consumes the pending fragment and scrolls to the anchor.
        compose.runOnIdle { vm.open(DocumentId("other.md")) }
        compose.waitUntil(timeoutMillis = 20_000) {
            PageViews.viewTexts(compose).any { it.contains("THE-ANCHOR-BODY") }
        }
        assertTrue(
            "the anchor section is on the page",
            PageViews.composeTexts(compose).any { it.contains("Section Two") } ||
                PageViews.viewTexts(compose).any { it.contains("THE-ANCHOR-BODY") },
        )
    }

    /** Same-document duplicate headings: the jump uses the first heading. */
    @Test
    fun duplicateHeadingAnchorLandsOnTheFirstMatch() {
        val body =
            buildString {
                append("Intro one.\n\nIntro two.\n\n")
                append("## Overview\n\nFIRST-OVERVIEW-BODY.\n\n")
                repeat(8) { append("Filler alpha beta gamma delta $it.\n\n") }
                append("## Other\n\nOther body.\n\n")
                repeat(8) { append("More filler alpha beta gamma delta $it.\n\n") }
                append("## Overview\n\nSECOND-OVERVIEW-BODY.\n\n")
            }
        val vm = openPage(files = mapOf("res.md" to body.toByteArray()), expect = "Intro one")

        // Drive the production decision path directly with the parsed link.
        val link =
            io.leaflet.markdown.BlockLink(
                label = "jump",
                destination = "",
                kind = io.leaflet.markdown.DocLinkKind.SAME_DOC_ANCHOR,
                fragment = "overview",
            )
        compose.runOnIdle { vm.followLink(link, onOpenDocument = {}, onOpenExternal = {}) }
        compose.waitUntil(timeoutMillis = 20_000) {
            PageViews.viewTexts(compose).any { it.contains("FIRST-OVERVIEW-BODY") }
        }
        val document = compose.runOnIdle { (vm.ui.value as ReaderUiState.Content).document }
        val firstIndex = document.blocks.indexOfFirst { it.text.contains("Overview") }
        val secondIndex = document.blocks.indexOfLast { it.text.contains("Overview") }
        assertTrue("both headings exist ($firstIndex/$secondIndex)", firstIndex in 0 until secondIndex)
        val landed = firstBlock() - 1
        assertTrue(
            "a duplicate heading jump must use the first match (landed=$landed, first=$firstIndex, second=$secondIndex)",
            landed >= firstIndex && landed < secondIndex,
        )
    }

    /** An unknown same-document anchor keeps the document and the position. */
    @Test
    fun unknownAnchorShowsNoticeAndKeepsTheContext() {
        val body = "# Doc\n\nFirst paragraph alpha.\n\nSecond paragraph beta.\n"
        val vm = openPage(files = mapOf("res.md" to body.toByteArray()), expect = "First paragraph")
        val before = compose.runOnIdle { (vm.ui.value as ReaderUiState.Content).document }

        val link =
            io.leaflet.markdown.BlockLink(
                label = "bad",
                destination = "",
                kind = io.leaflet.markdown.DocLinkKind.SAME_DOC_ANCHOR,
                fragment = "no-such-anchor",
            )
        compose.runOnIdle { vm.followLink(link, onOpenDocument = {}, onOpenExternal = {}) }
        compose.waitForIdle()

        compose.onNodeWithText("本文中没有该锚点，位置保持不变").assertIsDisplayed()
        val after = compose.runOnIdle { (vm.ui.value as ReaderUiState.Content).document }
        assertTrue("the document is untouched by a rejected anchor", after === before)
    }

    /** A target outside the authorized tree is rejected with a notice. */
    @Test
    fun outOfTreeRelativeLinkIsRejectedWithNotice() {
        val body = "Text with [escape](../../outside.md) inside.\n"
        val vm = openPage(files = mapOf("res.md" to body.toByteArray()), expect = "Text with")
        val before = compose.runOnIdle { (vm.ui.value as ReaderUiState.Content).document }

        compose.onNodeWithText("escape").performClick()
        compose.waitUntil(timeoutMillis = 20_000) {
            PageViews.composeTexts(compose).any { it.contains("目标不存在或超出资料库范围") }
        }

        compose.onNodeWithText("目标不存在或超出资料库范围").assertIsDisplayed()
        val after = compose.runOnIdle { (vm.ui.value as ReaderUiState.Content).document }
        assertTrue("the document survives a rejected target", after === before)
    }

    // -- images ---------------------------------------------------------------

    /** A remote image renders a static placeholder and never calls the loader. */
    @Test
    fun remoteImageShowsStaticPlaceholderWithoutLoading() {
        val calls = mutableListOf<String>()
        val loader = recordingLoader(calls)
        openPage(
            files = mapOf("res.md" to "# Images\n\n![remote pic](https://example.com/pic.png)\n".toByteArray()),
            imageLoader = loader,
            expect = "Images",
        )
        compose.waitForIdle()
        assertTrue(
            "the remote placeholder caption is on the page",
            PageViews.composeTexts(compose).any { it.contains("远程图片不自动加载") },
        )
        assertTrue("no loader call for a remote image (got $calls)", calls.isEmpty())
    }

    /** CJK characters in a relative image path reach the loader unchanged. */
    @Test
    fun relativeImageWithCjkPathReachesTheLoader() {
        val calls = mutableListOf<String>()
        val loader = recordingLoader(calls)
        openPage(
            files = mapOf("res.md" to "# Images\n\n![图](资料/图片名.png)\n".toByteArray()),
            imageLoader = loader,
            expect = "Images",
        )
        compose.waitUntil(timeoutMillis = 20_000) { calls.isNotEmpty() }
        assertTrue(
            "the CJK relative path must reach the loader unmangled (calls=$calls)",
            calls.isNotEmpty() && calls.all { it == "资料/图片名.png" },
        )
    }

    /** A missing local image shows the recoverable "missing" placeholder. */
    @Test
    fun missingLocalImageShowsPlaceholder() {
        val calls = mutableListOf<String>()
        val loader =
            object : ImageLoader {
                override suspend fun load(
                    fromId: DocumentId,
                    ref: BlockImage,
                    targetWidthPx: Int,
                ): DeviceImage {
                    calls.add(ref.destination)
                    return DeviceImage.Failed(ImageFailure.MISSING)
                }
            }
        openPage(
            files = mapOf("res.md" to "# Images\n\n![gone](page/gone.png)\n".toByteArray()),
            imageLoader = loader,
            expect = "Images",
        )
        compose.waitUntil(timeoutMillis = 20_000) {
            PageViews.composeTexts(compose).any { it.contains("图片缺失") }
        }
        assertTrue("the missing placeholder is shown (calls=$calls)", calls.contains("page/gone.png"))
    }

    private fun recordingLoader(calls: MutableList<String>): ImageLoader =
        object : ImageLoader {
            override suspend fun load(
                fromId: DocumentId,
                ref: BlockImage,
                targetWidthPx: Int,
            ): DeviceImage {
                calls.add(ref.destination)
                return if (ref.kind == ImageKind.LOCAL) {
                    val bmp = android.graphics.Bitmap.createBitmap(8, 8, android.graphics.Bitmap.Config.ARGB_8888)
                    DeviceImage.Ready(bmp, bmp.width, bmp.height)
                } else {
                    DeviceImage.Failed(ImageFailure.UNSUPPORTED)
                }
            }
        }
}
