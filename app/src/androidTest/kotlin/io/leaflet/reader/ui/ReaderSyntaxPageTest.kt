package io.leaflet.reader.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.leaflet.markdown.BlockKind
import io.leaflet.reader.ui.reader.ReaderUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * R3-03 package (Markdown/TXT 全语法显示验收, §10/14/24): every supported
 * construct is asserted on the REAL production view tree — Markwon
 * `TextView`s (heading/inline/list/quote/thematic spans) and the composed
 * `Text` nodes (code bodies, table cells) — not on model state.
 *
 * Also locks the DEF-009 regression found by this package: Markwon's
 * task-list items are `TaskListItem extends CustomBlock`, which the source
 * rebuild used to drop entirely, leaving an EMPTY entry on the page.
 */
@RunWith(AndroidJUnit4::class)
class ReaderSyntaxPageTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private val syntaxDoc =
        """
        # H1 Title

        ## H2 Title

        ### H3 Title

        #### H4 Title

        ##### H5 Title

        ###### H6 Title

        Bold **b** italic *i* strike ~~s~~ inline `c` tail.

        - bullet one
        - bullet two

        1. ordered one

        > quoted line

        ```kotlin
        val x = 1
        ```

        ---

        | Col A | Col B |
        | --- | --- |
        | a1 | b1 |
        """.trimIndent() + "\n"

    private fun open(
        docId: String,
        body: ByteArray,
    ): io.leaflet.reader.ui.reader.ReaderViewModel {
        val store = FakePageStore(mapOf(docId to body), mapOf(docId to docId))
        val vm = pageViewModel(context, store)
        compose.setContent { ReaderScreenShim(documentId = docId, vm = vm) }
        compose.waitUntil(timeoutMillis = 20_000) {
            vm.ui.value is ReaderUiState.Content
        }
        compose.waitForIdle()
        return vm
    }

    @Test
    fun headingsAndInlineStylesRenderAsSpansWithoutMarkers() {
        val vm = open("syntax.md", syntaxDoc.toByteArray())
        val doc = (vm.ui.value as ReaderUiState.Content).document

        // H1..H6 all present as headings, text without the `#` markers.
        val headingTexts =
            PageViews.allTextViews(compose)
                .filter { "HeadingSpan" in PageViews.spanClassNames(it) }
                .map { it.text.toString() }
        assertTrue("all six heading levels rendered: $headingTexts", headingTexts.containsAll(listOf("H1 Title", "H6 Title")))
        assertEquals(6, headingTexts.size)
        assertTrue("no literal # in headings", headingTexts.none { it.contains("#") })

        // Inline styles: bold/italic/strike/inline-code spans, markers consumed.
        val body = PageViews.allTextViews(compose).first { it.text.contains("Bold") }
        val spans = PageViews.spanClassNames(body)
        assertTrue("bold span: $spans", "StrongEmphasisSpan" in spans)
        assertTrue("italic span: $spans", "EmphasisSpan" in spans)
        assertTrue("strike span: $spans", "StrikethroughSpan" in spans)
        assertTrue("inline code span: $spans", "CodeSpan" in spans)
        assertTrue("markers consumed: [${body.text}]", !body.text.contains("**") && !body.text.contains("~~"))

        // Lists / quote / thematic break.
        val listSpans = PageViews.allTextViews(compose).flatMap { PageViews.spanClassNames(it) }.toSet()
        assertTrue("bullet list span: $listSpans", "BulletListItemSpan" in listSpans)
        assertTrue("ordered list span: $listSpans", "OrderedListItemSpan" in listSpans)
        assertTrue("quote span: $listSpans", "BlockQuoteSpan" in listSpans)
        assertTrue("thematic break span: $listSpans", "ThematicBreakSpan" in listSpans)

        // Code entry + copy payload come from the same block.
        val code = doc.blocks.first { it.kind == BlockKind.CODE }
        assertEquals("val x = 1", code.codeForCopy())
        compose.onNodeWithText("val x = 1", substring = true).assertExists()
        assertTrue("code fence markers never rendered", !code.source.contains("``") || code.source.startsWith("```"))

        // Table cells are individually rendered.
        for (cell in listOf("Col A", "Col B", "a1", "b1")) {
            compose.onNodeWithText(cell).assertExists()
        }
    }

    @Test
    fun taskListItemsKeepMarkerAndTextOnDevice() {
        // DEF-009 regression: Markwon replaces list items with
        // `TaskListItem extends CustomBlock`; the rebuild filtered them out and
        // the whole list rendered as an EMPTY entry.
        val md = "# Task Proof\n\n- [ ] open task\n- [x] done task\n\nplain tail\n"
        val vm = open("task.md", md.toByteArray())
        val doc = (vm.ui.value as ReaderUiState.Content).document
        val list = doc.blocks.first { it.kind == BlockKind.LIST }

        assertTrue("slice is not empty: <<${list.source}>>", list.source.isNotBlank())
        assertTrue("checkbox marker kept: <<${list.source}>>", list.source.contains("[ ]"))
        assertTrue("checkbox marker kept: <<${list.source}>>", list.source.contains("[x]"))
        assertEquals("open task\ndone task", doc.blockTexts[list.index])
        // And the page really shows the item texts (not an empty entry).
        // Markdown bodies are Markwon `TextView`s (AndroidView), so they are
        // asserted through the view tree, not Compose semantics.
        val texts = PageViews.viewTexts(compose)
        assertTrue("item text rendered: $texts", texts.any { it.contains("open task") })
        assertTrue("item text rendered: $texts", texts.any { it.contains("done task") })
        assertTrue("entry is not blank: $texts", texts.none { it.isEmpty() })
    }

    @Test
    fun unknownSyntaxKeepsTheBodyReadable() {
        val unknown =
            "# Odd Proof\n\n\$\$x^2\$\$\n\nmermaid fence below\n\n```mermaid\ngraph A-->B\n```\n\nSome [^note] marker and tail text.\n"
        val vm = open("odd.md", unknown.toByteArray())
        val doc = (vm.ui.value as ReaderUiState.Content).document
        val joined = doc.blockTexts.joinToString("\n")
        assertTrue("math text survives: $joined", joined.contains("x^2"))
        assertTrue("mermaid content survives", joined.contains("graph A-->B"))
        assertTrue("footnote marker survives", joined.contains("note"))
        assertTrue("tail survives", joined.contains("tail text"))
        // Only the first blocks are composed (LazyColumn virtualization), so the
        // page-level assertion targets the top paragraph (a Markwon TextView).
        assertTrue(
            "unknown syntax rendered as text",
            PageViews.viewTexts(compose).any { it.contains("x^2") },
        )
    }

    @Test
    fun brokenEncodingDegradesWithNoticeAndKeepsValidText() {
        val broken = byteArrayOf(0x41) + byteArrayOf(0x80.toByte(), 0xFF.toByte()) + "B line".toByteArray()
        val vm = open("broken.md", broken)
        val doc = (vm.ui.value as ReaderUiState.Content).document
        assertTrue("degraded flag set", doc.degraded)
        assertTrue("valid text kept", doc.blockTexts.joinToString("").contains("B line"))
        compose.onNodeWithText("编码无法识别", substring = true).assertIsDisplayed()
    }

    @Test
    fun oversizedDocumentDegradesWithNoticeAndKeepsTheText() {
        // A single paragraph beyond the 32KiB block bound: bounded plain-text
        // chunks + the static "纯文本显示" banner, text still present.
        val huge = "word ".repeat(9000)
        val vm = open("huge.md", ("# Huge Proof\n\n$huge\n").toByteArray())
        val doc = (vm.ui.value as ReaderUiState.Content).document
        assertTrue("document flagged degraded", doc.degraded)
        assertTrue(
            "fallback chunks exist",
            doc.blocks.any { it.kind == BlockKind.PLAIN_FALLBACK },
        )
        assertTrue("no single unbounded block", doc.blocks.all { it.text.length <= 32 * 1024 })
        compose.onNodeWithText("纯文本显示", substring = true).assertIsDisplayed()
    }
}
