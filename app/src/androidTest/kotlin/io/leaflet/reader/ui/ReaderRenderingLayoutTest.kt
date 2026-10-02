package io.leaflet.reader.ui

import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.leaflet.markdown.BlockImage
import io.leaflet.model.DisplayMode
import io.leaflet.model.DocumentId
import io.leaflet.reader.R
import io.leaflet.reader.ui.reader.DeviceImage
import io.leaflet.reader.ui.reader.ImageLoader
import io.leaflet.reader.ui.reader.ReaderScreen
import io.leaflet.reader.ui.reader.ReaderUiState
import io.leaflet.reader.ui.theme.LeafletTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** UX-07 geometry/feedback regressions on the production reader, parser, and entry path. */
@RunWith(AndroidJUnit4::class)
class ReaderRenderingLayoutTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun open(
        body: String,
        mode: DisplayMode = DisplayMode.STANDARD,
        loader: ImageLoader? = null,
    ) {
        val vm = pageViewModel(context, FakePageStore(mapOf("layout.md" to body.toByteArray())))
        val settings = pageSettingsViewModel(defaultMode = mode)
        compose.setContent {
            LeafletTheme(displayMode = mode, darkTheme = false) {
                ReaderScreen(
                    viewModel = vm,
                    documentId = "layout.md",
                    onBack = {},
                    settingsViewModel = settings,
                    defaultDisplayMode = mode,
                    imageLoader = loader,
                )
            }
        }
        compose.waitUntil(20_000) { vm.ui.value is ReaderUiState.Content }
        compose.waitForIdle()
    }

    private fun assertTableColumns(mode: DisplayMode) {
        open(
            """
            | A | B | C |
            | --- | --- | --- |
            | Longer name | value | end |
            | x | other | last |
            """.trimIndent(),
            mode,
        )
        screenshot("table-${mode.name.lowercase()}")

        fun left(text: String) = compose.onNodeWithText(text).fetchSemanticsNode().boundsInRoot.left
        assertEquals(left("A"), left("Longer name"), 1f)
        assertEquals(left("B"), left("value"), 1f)
        assertEquals(left("B"), left("other"), 1f)
        assertEquals(left("C"), left("end"), 1f)
        assertEquals(left("C"), left("last"), 1f)
        compose.onNodeWithText("last").assertIsDisplayed()
    }

    @Test
    fun columnsAlignInStandardMode() = assertTableColumns(DisplayMode.STANDARD)

    @Test
    fun columnsAlignInEinkMode() = assertTableColumns(DisplayMode.EINK)

    private fun bitmapLoader(
        width: Int,
        height: Int,
    ): ImageLoader =
        object : ImageLoader {
            override suspend fun load(
                fromId: DocumentId,
                ref: BlockImage,
                targetWidthPx: Int,
            ): DeviceImage {
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(0xFF2E7D32.toInt())
                return DeviceImage.Ready(bitmap, width, height)
            }
        }

    @Test
    fun smallImageNeverUpscalesAtDeviceDensity() {
        open("![small icon](leaf-16.png)", loader = bitmapLoader(16, 16))
        compose.waitUntil(10_000) {
            compose.onAllNodesWithContentDescription("small icon").fetchSemanticsNodes().isNotEmpty()
        }
        screenshot("small-image")
        val bounds = compose.onNodeWithContentDescription("small icon").fetchSemanticsNode().boundsInRoot
        assertEquals(16f, bounds.width, 1f)
        assertEquals(16f, bounds.height, 1f)
    }

    @Test
    fun tallImageKeepsAspectAndHeightCap() {
        open("![tall image](tall.png)", loader = bitmapLoader(100, 1000))
        compose.waitUntil(10_000) {
            compose.onAllNodesWithContentDescription("tall image").fetchSemanticsNodes().isNotEmpty()
        }
        val bounds = compose.onNodeWithContentDescription("tall image").fetchSemanticsNode().boundsInRoot
        val density = context.resources.displayMetrics.density
        assertTrue("height is capped at 320 dp", bounds.height <= 320f * density + 1)
        assertTrue("image never upscales", bounds.width <= 100f && bounds.height <= 1000f)
        assertEquals("aspect ratio stays 1:10", 0.1f, bounds.width / bounds.height, 0.002f)
    }

    @Test
    fun copyFeedbackExpiresWithoutMovingCodeAndCopiesExactBody() {
        open("```kotlin\nval answer = 42\n```", mode = DisplayMode.EINK)
        val copy = context.getString(R.string.reader_code_copy)
        val copied = context.getString(R.string.reader_code_copied)
        val codeBefore = compose.onNodeWithText("val answer = 42").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithText(copy).performClick()
        compose.onNodeWithText(copied).assertExists()
        screenshot("copy-feedback")
        assertEquals(codeBefore, compose.onNodeWithText("val answer = 42").fetchSemanticsNode().boundsInRoot)
        compose.runOnIdle {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            assertEquals("val answer = 42", clipboard.primaryClip?.getItemAt(0)?.text?.toString())
        }
        compose.waitUntil(4_000) { compose.onAllNodesWithText(copied).fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText(copy).assertExists()
        assertEquals(codeBefore, compose.onNodeWithText("val answer = 42").fetchSemanticsNode().boundsInRoot)
    }

    @Test
    fun repeatedCopyRestartsFeedbackTimeout() {
        open("```\nrepeat me\n```", mode = DisplayMode.EINK)
        val copy = context.getString(R.string.reader_code_copy)
        val copied = context.getString(R.string.reader_code_copied)
        compose.onNodeWithText(copy).performClick()
        Thread.sleep(1_300)
        compose.onNodeWithText(copied).performClick()
        Thread.sleep(1_000)
        compose.onNodeWithText(copied).assertExists()
        compose.waitUntil(4_000) { compose.onAllNodesWithText(copied).fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText(copy).assertExists()
    }

    /** Optional emulator evidence; normal runs create no image files. */
    private fun screenshot(name: String) {
        val prefix = InstrumentationRegistry.getArguments().getString("renderingScreenshotPrefix") ?: return
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(context.getExternalFilesDir(null), "$prefix-$name.png").outputStream().use {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }
}
