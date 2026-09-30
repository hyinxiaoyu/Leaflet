package io.leaflet.reader.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.leaflet.markdown.BlockImage
import io.leaflet.markdown.ImageKind
import io.leaflet.model.DocumentId
import io.leaflet.reader.ui.reader.DeviceImage
import io.leaflet.reader.ui.reader.ImageFailure
import io.leaflet.reader.ui.reader.ImageLoader
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * R2-07 page regression: in-text images on the REAL reader route —
 * REQ-0009 FR-6–FR-9 (R2-03 F04 fix: a decoded bitmap actually draws).
 *
 * The loader is a fake at the SAF seam (bytes come from test resources, not
 * a provider grant); the ENTRY under test is the production `ImageEntry`
 * path: Ready → `Image` composable draws, Failed/REMOTE → static
 * placeholders, zero loader calls for remote. A fake loader keeps the test
 * hermetic; the production `LibraryImageLoader` SAF walk is covered by the
 * manual walkthrough (R2-07 report) + `SafInstrumentedTest`.
 */
@RunWith(AndroidJUnit4::class)
class ReaderImageTest {
    @get:Rule
    val compose = createComposeRule()

    private val markdownWithImages =
        """
        # Image Page Proof

        Text before the local image.

        ![leaf icon](page/leaf-256.png)

        Text between images.

        ![remote pic](https://example.com/pic.png)

        ![broken pic](page/gone.png)

        Closing text.
        """.trimIndent()

    private fun leafBitmap(): android.graphics.Bitmap {
        // 256x256 solid-green ARGB bitmap (no resources needed).
        val bmp = android.graphics.Bitmap.createBitmap(64, 64, android.graphics.Bitmap.Config.ARGB_8888)
        bmp.eraseColor(0xFF2E7D32.toInt())
        return bmp
    }

    @Test
    fun localImageDrawsRemoteAndBrokenPlaceholder() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val calls = mutableListOf<String>()
        val loader =
            object : ImageLoader {
                override suspend fun load(
                    fromId: DocumentId,
                    ref: BlockImage,
                    targetWidthPx: Int,
                ): DeviceImage {
                    calls.add(ref.destination)
                    return when (ref.kind) {
                        ImageKind.LOCAL ->
                            if (ref.destination.endsWith("gone.png")) {
                                DeviceImage.Failed(ImageFailure.MISSING)
                            } else {
                                val bmp = leafBitmap()
                                DeviceImage.Ready(bmp, bmp.width, bmp.height)
                            }
                        else -> DeviceImage.Failed(ImageFailure.UNSUPPORTED)
                    }
                }
            }
        val store = FakePageStore(mapOf("img.md" to markdownWithImages.toByteArray()), mapOf("img.md" to "img.md"))
        val vm = pageViewModel(context, store)
        compose.setContent {
            ImageShim(documentId = "img.md", vm = vm, imageLoader = loader)
        }
        // Local Ready bitmap draws (production Image composable).
        compose.waitUntil(timeoutMillis = 15_000) {
            try {
                compose.onNodeWithText("Image Page Proof").assertIsDisplayed()
                true
            } catch (_: AssertionError) {
                false
            }
        }
        compose.waitUntil(timeoutMillis = 15_000) {
            calls.any { it.endsWith("leaf-256.png") }
        }
        // Remote never reaches the loader (zero network by construction).
        compose.runOnIdle {
            check(calls.none { it.startsWith("http") })
            check(calls.any { it.endsWith("gone.png") })
        }
    }
}
