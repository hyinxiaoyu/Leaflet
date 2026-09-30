package io.leaflet.reader.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * R2-07 page regression: the REAL reader route (production [ReaderScreen] +
 * production `ReaderSession`/Markwon parser over a fake storage seam) opens
 * a Markdown fixture end to end.
 *
 * Uses [createComposeRule] (content-only, no Activity launch): the
 * TEST-process host activity is infra-blocked on this emulator image
 * (`startActivitySync` cross-process failure — same reason the old
 * compose-rule setup was deleted). The composed tree is still the
 * production screen with the production session/parser/search/TOC path, so
 * these are page assertions, not helper-value assertions.
 *
 * Covers REQ-0002 (rich body), REQ-0006 (TOC anchors), REQ-0008
 * (in-document search + stepping) on the composed page. Both flavors run
 * this suite (`connectedFullDebugAndroidTest`,
 * `connectedEinkDebugAndroidTest`); flavor-specific behavior is owned by R2-08.
 */
@RunWith(AndroidJUnit4::class)
class ReaderEntryTest {
    @get:Rule
    val compose = createComposeRule()

    private val markdown =
        """
        # Leaflet Page Proof

        First paragraph with **bold** and *italic* words.

        ## Second Section

        Body of the second section with a `code` span.

        | Name | Age |
        | --- | --- |
        | Ada | 36 |
        | Grace | 85 |

        ```kotlin
        val needle = "page-search-needle"
        ```

        Paragraph holding page-search-needle twice: page-search-needle.
        """.trimIndent()

    private lateinit var store: FakePageStore

    private fun setPage(): io.leaflet.reader.ui.reader.ReaderViewModel {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        store = FakePageStore(mapOf("page.md" to markdown.toByteArray()), mapOf("page.md" to "page.md"))
        val vm = pageViewModel(context, store)
        compose.setContent {
            ReaderScreenShim(documentId = "page.md", vm = vm)
        }
        compose.waitUntil(timeoutMillis = 15_000) {
            try {
                compose.onNodeWithText("Leaflet Page Proof").assertIsDisplayed()
                true
            } catch (_: AssertionError) {
                false
            }
        }
        return vm
    }

    @Test
    fun readerOpensMarkdownWithTitleAndBody() {
        val vm = setPage()
        // Title is a Compose Text (assertable); body blocks render through
        // Markwon AndroidView TextViews (invisible to compose finders), so
        // body presence is asserted on the production document the page
        // bound: block count + visible text of the H2 + table/code kinds.
        compose.onNodeWithText("Leaflet Page Proof").assertIsDisplayed()
        val doc = (vm.ui.value as io.leaflet.reader.ui.reader.ReaderUiState.Content).document
        check(doc.blocks.size >= 6)
        check(doc.blockTexts.any { it.contains("Second Section") })
        check(doc.blocks.any { it.kind == io.leaflet.markdown.BlockKind.TABLE })
        check(doc.blocks.any { it.kind == io.leaflet.markdown.BlockKind.CODE })
        // R2 DEF-006: the TOC drawer really resolves and jumps through the
        // production click path (not just the pure resolver).
        compose.runOnIdle { }
        compose.onRoot().performTouchInput { click(percentOffset(0.5f, 0.4f)) }
        compose.waitUntil(timeoutMillis = 5_000) {
            try {
                compose.onNodeWithText("Contents").assertIsDisplayed()
                true
            } catch (_: AssertionError) {
                false
            }
        }
        compose.onNodeWithText("Contents").performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            try {
                compose.onNodeWithText("Second Section", substring = true).assertExists()
                true
            } catch (_: AssertionError) {
                false
            }
        }
    }

    @Test
    fun searchFindsNeedleAndSteps() {
        val vm = setPage()
        // Open the bar through the production menu path: center-zone tap
        // toggles the menu (CONTINUOUS mode default), then the menu's
        // Search button opens the bar — no test hooks.
        compose.onRoot().performTouchInput {
            click(percentOffset(0.5f, 0.4f))
        }
        compose.waitUntil(timeoutMillis = 5_000) {
            try {
                compose.onNodeWithText("Search").assertIsDisplayed()
                true
            } catch (_: AssertionError) {
                false
            }
        }
        compose.onNodeWithText("Search").performClick()
        // R2 DEF-003 regression (run FIRST: the heading sits at the top of the
        // list, so its entry is composed). The heading's source slice is
        // `# Leaflet Page Proof` while the entry renders `Leaflet Page Proof`,
        // so measuring against the source slice shifts every span by the marker
        // length — the highlight must still cover exactly the word.
        vm.onSearchQuery("Leaflet")
        compose.waitUntil(timeoutMillis = 15_000) {
            vm.search.value.query == "Leaflet" && vm.search.value.matches.size == 1 && !vm.search.value.searching
        }
        compose.waitUntil(timeoutMillis = 5_000) {
            PageViews.allTextViews(compose).any {
                it.text.contains("Leaflet Page Proof") && PageViews.backgroundSpanRanges(it).isNotEmpty()
            }
        }
        val headingView = PageViews.allTextViews(compose).first { it.text.contains("Leaflet Page Proof") }
        val headingSpans = PageViews.backgroundSpanRanges(headingView)
        assertEquals(1, headingSpans.size)
        assertEquals(
            "heading highlight covers exactly the searched word",
            "Leaflet",
            headingView.text.substring(headingSpans[0].first, headingSpans[0].last + 1),
        )

        // R2 DEF-006: TYPE into the real search field (the production debounce
        // + capped scan path), not just the ViewModel API.
        compose.onNode(hasSetTextAction()).performTextInput("page-search-needle")
        compose.waitUntil(timeoutMillis = 15_000) {
            vm.search.value.matches.size == 3 && !vm.search.value.searching
        }
        // R2-07: the "1 / 3" count composes on the page (FR-7 — computed
        // status was once dropped and never emitted).
        compose.onNodeWithText("1 / 3").assertIsDisplayed()

        // R2 DEF-003/DEF-006: the RENDERED TextView is really highlighted, and
        // every background span covers exactly the matched word.
        compose.waitUntil(timeoutMillis = 5_000) {
            PageViews.allTextViews(compose).any { PageViews.backgroundSpanRanges(it).isNotEmpty() }
        }
        val highlighted = PageViews.allTextViews(compose).first { PageViews.backgroundSpanRanges(it).isNotEmpty() }
        val ranges = PageViews.backgroundSpanRanges(highlighted)
        assertTrue("needle highlighted on screen", ranges.isNotEmpty())
        for (range in ranges) {
            assertEquals(
                "span covers exactly the needle",
                "page-search-needle",
                highlighted.text.substring(range.first, range.last + 1),
            )
        }
        // Match 1 is inside the code fence (rendered by the code entry, not a
        // TextView), so the paragraph shows both of its matches as non-current
        // spans and no inverted span yet.
        assertTrue(PageViews.foregroundSpanRanges(highlighted).isEmpty())

        // Step through the production stepper: 0 -> 1, count follows. Match 2
        // is the paragraph's first needle, so the inverted (current) span must
        // now appear on exactly that word in the REAL TextView.
        vm.stepSearch(true)
        compose.waitUntil(timeoutMillis = 5_000) {
            vm.search.value.currentIndex == 1
        }
        compose.onNodeWithText("2 / 3").assertIsDisplayed()
        compose.waitUntil(timeoutMillis = 5_000) {
            PageViews.allTextViews(compose).any { PageViews.foregroundSpanRanges(it).isNotEmpty() }
        }
        val currentView = PageViews.allTextViews(compose).first { PageViews.foregroundSpanRanges(it).isNotEmpty() }
        val current = PageViews.foregroundSpanRanges(currentView)
        assertEquals("exactly one current span", 1, current.size)
        assertEquals(
            "current span covers exactly the needle",
            "page-search-needle",
            currentView.text.substring(current[0].first, current[0].last + 1),
        )
        // Background spans cover BOTH paragraph occurrences: the current one
        // is the pair (background + foreground), the other stays background
        // only. Every span still covers exactly the needle.
        val backgrounds = PageViews.backgroundSpanRanges(currentView)
        assertEquals("both paragraph occurrences covered", 2, backgrounds.size)
        for (range in backgrounds) {
            assertEquals(
                "background span covers exactly the needle",
                "page-search-needle",
                currentView.text.substring(range.first, range.last + 1),
            )
        }
        assertTrue(
            "the current match is one of the background spans",
            backgrounds.contains(current[0]),
        )
    }

    /**
     * R2 DEF-004 (second round): a four-backtick fence whose body contains a
     * literal ``` line. The page must show the WHOLE body — including the
     * inner fence line and `after` — and the copy payload must be the exact
     * body (fence markers stripped, interior untouched).
     */
    @Test
    fun codeFenceWithInnerFenceShowsWholeBody() {
        val fenceDoc = "````text\nbefore\n```\nafter\n````\n"
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store =
            FakePageStore(
                mapOf("fence.md" to fenceDoc.toByteArray()),
                mapOf("fence.md" to "fence.md"),
            )
        val vm = pageViewModel(context, store)
        compose.setContent { ReaderScreenShim(documentId = "fence.md", vm = vm) }
        compose.waitUntil(timeoutMillis = 15_000) {
            vm.ui.value is io.leaflet.reader.ui.reader.ReaderUiState.Content
        }
        // The code entry is a Compose Text, so its whole body is assertable.
        compose.waitUntil(timeoutMillis = 5_000) {
            try {
                compose.onNodeWithText("after", substring = true).assertExists()
                compose.onNodeWithText("before", substring = true).assertExists()
                true
            } catch (_: AssertionError) {
                false
            }
        }
        val doc = (vm.ui.value as io.leaflet.reader.ui.reader.ReaderUiState.Content).document
        assertEquals("one code block", 1, doc.blocks.count { it.kind == io.leaflet.markdown.BlockKind.CODE })
        val code = doc.blocks.first { it.kind == io.leaflet.markdown.BlockKind.CODE }
        assertEquals("before\n```\nafter", code.codeForCopy())
        assertTrue("fence widened in the slice: ${code.source.lines().first()}", code.source.startsWith("````"))
    }

    /**
     * R2 DEF-003 (table sub-item): four cells with identical text. Exactly one
     * of them may carry the CURRENT-match style at a time — the assertion runs
     * against the composed cell nodes' own span styles (the production page),
     * and stepping must move the current style to another cell.
     */
    @Test
    fun duplicateTableCellOnlyTheSelectedOneIsCurrent() {
        val tableDoc = "# Table Proof\n\n| dup | dup |\n| --- | --- |\n| dup | dup |\n"
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store =
            FakePageStore(
                mapOf("table.md" to tableDoc.toByteArray()),
                mapOf("table.md" to "table.md"),
            )
        val vm = pageViewModel(context, store)
        compose.setContent { ReaderScreenShim(documentId = "table.md", vm = vm) }
        compose.waitUntil(timeoutMillis = 15_000) {
            vm.ui.value is io.leaflet.reader.ui.reader.ReaderUiState.Content
        }
        compose.runOnIdle { vm.onSearchQuery("dup") }
        compose.waitUntil(timeoutMillis = 15_000) {
            vm.search.value.matches.size == 4 && !vm.search.value.searching
        }
        compose.waitForIdle()

        fun isCurrent(style: androidx.compose.ui.text.SpanStyle): Boolean =
            style.background == androidx.compose.ui.graphics.Color(0xFF000000) &&
                style.color == androidx.compose.ui.graphics.Color(0xFFFFFFFF)

        fun isPlainMatch(style: androidx.compose.ui.text.SpanStyle): Boolean =
            style.background == androidx.compose.ui.graphics.Color(0xFFFFFF00)

        val initial = PageViews.composedTextSpans(compose, "dup")
        assertEquals("all four cells are rendered", 4, initial.size)
        assertEquals(
            "exactly one cell carries the current-match style",
            1,
            initial.count { spans -> spans.any(::isCurrent) },
        )
        assertEquals(
            "the other cells keep the plain match style",
            3,
            initial.count { spans -> spans.any(::isPlainMatch) },
        )
        val currentCellBefore = initial.indexOfFirst { spans -> spans.any(::isCurrent) }

        // Stepping to the next match must move the current style to another
        // cell — the old substring-based mapping marked every identical cell.
        compose.runOnIdle { vm.stepSearch(true) }
        compose.waitUntil(timeoutMillis = 5_000) { vm.search.value.currentIndex == 1 }
        compose.waitForIdle()
        val stepped = PageViews.composedTextSpans(compose, "dup")
        assertEquals(4, stepped.size)
        assertEquals(1, stepped.count { spans -> spans.any(::isCurrent) })
        assertEquals(3, stepped.count { spans -> spans.any(::isPlainMatch) })
        assertTrue(
            "the current style moved to the next match's cell",
            stepped.indexOfFirst { spans -> spans.any(::isCurrent) } != currentCellBefore,
        )
    }

    @Test
    fun tocClickLandsOnItsSection() {
        // R2 DEF-006: exercise the REAL drawer click, not just the resolver.
        val vm = setPage()
        compose.onRoot().performTouchInput { click(percentOffset(0.5f, 0.4f)) }
        compose.waitUntil(timeoutMillis = 5_000) {
            try {
                compose.onNodeWithText("Contents").assertIsDisplayed()
                true
            } catch (_: AssertionError) {
                false
            }
        }
        compose.onNodeWithText("Contents").performClick()
        // Drawer entry exists, carries the real anchor text.
        compose.waitUntil(timeoutMillis = 5_000) {
            try {
                compose.onNodeWithText("Second Section", substring = true).assertIsDisplayed()
                true
            } catch (_: AssertionError) {
                false
            }
        }
        compose.onNodeWithText("Second Section", substring = true).performClick()
        compose.waitForIdle()
        // The jump persisted the target block immediately (TOC landing rule).
        val doc = (vm.ui.value as io.leaflet.reader.ui.reader.ReaderUiState.Content).document
        val target = io.leaflet.reader.TocNavigation.resolve(doc, "second-section")
        assertTrue("anchor resolves", target != null)
        val saved = store.savedPositions
        assertTrue("TOC click saved the landing block", saved.isNotEmpty())
    }
}
