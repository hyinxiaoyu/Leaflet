package io.leaflet.reader.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeLeft
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.leaflet.reader.ReadingMode
import io.leaflet.reader.SettingsViewModel
import io.leaflet.reader.ui.reader.ReaderUiState
import io.leaflet.reader.ui.reader.ReaderViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * R3-05 page-interaction acceptance on the REAL reader route (production
 * `ReaderScreen` + session/parser/Markwon; storage faked at the
 * `ReaderStore` seam). REQ-0005 (page turns, tap zones, mode), REQ-0006 (TOC
 * landing), REQ-0007 (table/code reachability), REQ-0004 (mode switch).
 *
 * Everything is measured on the composed page, not on the model:
 * - the turn distance is derived from the REAL viewport and the REAL overlap
 *   setting (3% vs 8%) and compared with `PageTurn`'s contract;
 * - five consecutive turns, head/tail clamps, and repeated taps run through
 *   real touch input while the static progress line is read back;
 * - centre-menu, long-press, link buttons, and horizontal table/code
 *   gestures must never turn a page.
 *
 * Emulator evidence only: ghosting and hardware refresh belong to R3-09.
 */
@RunWith(AndroidJUnit4::class)
class ReaderInteractionTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private val progressLine = Regex("^(\\d+) / (\\d+) · (\\d+)%$")

    // -- fixtures -------------------------------------------------------------

    private fun numberedDocument(paragraphs: Int): String =
        buildString {
            append("# Interaction Proof\n\n")
            for (i in 1..paragraphs) {
                append("Paragraph %02d alpha beta gamma delta epsilon zeta eta.\n\n".format(i))
            }
        }

    private fun openPage(
        body: String,
        settings: SettingsViewModel = pageSettingsViewModel(),
        docId: String = "interaction.md",
        expect: String = "Paragraph 01",
    ): ReaderViewModel {
        val store = FakePageStore(mapOf(docId to body.toByteArray()), mapOf(docId to docId))
        val vm = pageViewModel(context, store)
        compose.setContent {
            ReaderScreenShim(documentId = docId, vm = vm, settingsViewModel = settings)
        }
        compose.waitUntil(timeoutMillis = 20_000) {
            PageViews.viewTexts(compose).any { it.contains(expect) } ||
                PageViews.composeTexts(compose).any { it.contains(expect) }
        }
        return vm
    }

    /** Waits until a settings flow reports [expected] (writes are async). */
    private fun awaitSetting(
        expected: Any,
        read: () -> Any,
    ) {
        val deadline = System.currentTimeMillis() + 5_000
        var seen: Any = Unit
        while (System.currentTimeMillis() < deadline) {
            seen = read()
            if (seen == expected) return
            Thread.sleep(20)
        }
        assertEquals("setting did not reach its expected value", expected, seen)
    }

    private fun usePageTurnMode(settings: SettingsViewModel) {
        compose.runOnIdle { settings.setReadingMode(ReadingMode.PAGE_TURN.stored) }
        val deadline = System.currentTimeMillis() + 5_000
        var stored = ""
        while (System.currentTimeMillis() < deadline) {
            stored = flowValue(settings.readingMode)
            if (stored == ReadingMode.PAGE_TURN.stored) break
            Thread.sleep(20)
        }
        assertEquals("the production settings path must reach PAGE_TURN", ReadingMode.PAGE_TURN.stored, stored)
        compose.waitForIdle()
    }

    private fun progress(): Triple<Int, Int, Int> {
        val text =
            PageViews.composeTexts(compose).firstOrNull { progressLine.matches(it.trim()) }
                ?: error("no progress line in ${PageViews.composeTexts(compose)}")
        val match = progressLine.find(text.trim())!!
        return Triple(match.groupValues[1].toInt(), match.groupValues[2].toInt(), match.groupValues[3].toInt())
    }

    private fun firstBlock(): Int = progress().first

    /**
     * The bottom-most composed paragraph and its root-relative top. One turn
     * scrolls it up by ~(1 - overlap) × viewport, so the overlap band keeps it
     * composed and measurable afterwards.
     */
    private fun bottomAnchor(): Pair<String, Float> =
        PageViews.textTopsByPrefix(compose, "Paragraph ")
            .maxByOrNull { it.second }
            ?: error("no paragraph TextView composed")

    private fun anchorAfter(text: String): Float =
        PageViews.textTopsByPrefix(compose, "Paragraph ")
            .firstOrNull { it.first == text }
            ?.second
            ?: error("anchor '$text' left the composition after the turn")

    /**
     * The reader list's own bounds. The tap-zone detector sits on the
     * LazyColumn, so taps must be aimed inside this rect (the page margin is
     * padding around it, where the detector never receives events).
     */
    private fun listRect(): androidx.compose.ui.geometry.Rect =
        PageViews.scrollerBounds(compose, horizontal = false) ?: error("no vertical scroller on the page")

    /** Asserts the point really is in [zone] before it is tapped. */
    private fun zoneOf(point: Offset): io.leaflet.reader.TapZone {
        val rect = listRect()
        return io.leaflet.reader.TapZones.zoneFor(point.x - rect.left, rect.width.toInt())
    }

    private fun tapNext() {
        val rect = listRect()
        val point = Offset(rect.right - 8f, rect.center.y)
        assertEquals("next-page tap must land in the NEXT zone", io.leaflet.reader.TapZone.NEXT, zoneOf(point))
        compose.onRoot().performTouchInput { click(point) }
        compose.waitForIdle()
    }

    private fun tapPrev() {
        val rect = listRect()
        val point = Offset(rect.left + 8f, rect.center.y)
        assertEquals("previous-page tap must land in the PREV zone", io.leaflet.reader.TapZone.PREV, zoneOf(point))
        compose.onRoot().performTouchInput { click(point) }
        compose.waitForIdle()
    }

    private fun tapCenter() {
        val rect = listRect()
        val point = Offset(rect.center.x, rect.center.y)
        assertEquals("centre tap must land in the MENU zone", io.leaflet.reader.TapZone.MENU, zoneOf(point))
        compose.onRoot().performTouchInput { click(point) }
        compose.waitForIdle()
    }

    /** One measured forward turn at the current overlap; returns the distance. */
    private fun measuredTurnDistance(): Float {
        val (anchor, before) = bottomAnchor()
        tapNext()
        return before - anchorAfter(anchor)
    }

    /** Taps backwards until the head clamp leaves the page at the very top. */
    private fun returnToTop() {
        repeat(60) {
            val before = firstBlock()
            tapPrev()
            if (before == 1 && firstBlock() == 1) return
        }
    }

    /** (value, maxValue) of every horizontal scroller on the page. */
    private fun horizontalScrolls(): List<Pair<Float, Float>> = PageViews.horizontalScrollOffsets(compose)

    // -- release-triggered ebook gestures ------------------------------------

    @Test
    fun bodySwipeStaysStillUntilReleaseAndThenTurnsExactlyOnce() {
        val settings = pageSettingsViewModel()
        openPage(numberedDocument(60), settings)
        assertEquals("fresh Reader uses ebook paging", ReadingMode.PAGE_TURN.stored, flowValue(settings.readingMode))
        val rect = listRect()
        val (anchor, before) = bottomAnchor()
        val from = Offset(rect.left + rect.width * 0.8f, rect.center.y)
        val to = Offset(rect.left + rect.width * 0.2f, rect.center.y)
        compose.onRoot().performTouchInput {
            down(from)
            advanceEventTime(100)
            moveTo(to)
        }
        compose.waitForIdle()
        assertEquals("body must not track the finger", before, anchorAfter(anchor), 2f)
        compose.onRoot().performTouchInput { up() }
        compose.waitForIdle()
        val swipeDistance = before - anchorAfter(anchor)
        assertTrue("release advances a screen", swipeDistance > 0f)
        tapPrev()
        val tapDistance = measuredTurnDistance()
        assertEquals("one swipe and one tap have the same page distance", tapDistance, swipeDistance, 2f)
        compose.onRoot().performTouchInput {
            swipe(to, from, durationMillis = 200)
        }
        compose.waitForIdle()
        assertEquals("swipe right returns to the previous screen", before, anchorAfter(anchor), 2f)
    }

    @Test
    fun sidePageMarginsAreActiveTapZones() {
        val settings = pageSettingsViewModel()
        openPage(numberedDocument(60), settings)
        usePageTurnMode(settings)
        val area = compose.onNodeWithTag("reader.gestures").fetchSemanticsNode().boundsInRoot
        val (anchor, before) = bottomAnchor()
        compose.onRoot().performTouchInput { click(Offset(area.right - 1f, area.center.y)) }
        compose.waitForIdle()
        assertTrue("right margin turns forward", anchorAfter(anchor) < before)
        compose.onRoot().performTouchInput { click(Offset(area.left + 1f, area.center.y)) }
        compose.waitForIdle()
        assertEquals("left margin returns backward", before, anchorAfter(anchor), 2f)
    }

    @Test
    fun shortVerticalAndHeldBodyGesturesNeverTurn() {
        val settings = pageSettingsViewModel()
        openPage(numberedDocument(60), settings)
        usePageTurnMode(settings)
        val rect = listRect()
        val (anchor, before) = bottomAnchor()
        val from = Offset(rect.right - 20f, rect.center.y)
        val shortDragPx = 24f * context.resources.displayMetrics.density
        for (end in listOf(from + Offset(-shortDragPx, 0f), from + Offset(0f, -100f))) {
            compose.onRoot().performTouchInput { swipe(from, end, durationMillis = 150) }
            compose.waitForIdle()
            assertEquals("rejected drag must not fall through to tap", before, anchorAfter(anchor), 2f)
        }
        compose.onRoot().performTouchInput {
            down(from)
            advanceEventTime(600)
            moveTo(from + Offset(-100f, 0f))
            up()
        }
        compose.waitForIdle()
        assertEquals("held gesture never turns", before, anchorAfter(anchor), 2f)
    }

    // -- overlap / distance ---------------------------------------------------

    /**
     * The overlap setting is applied to the REAL viewport: at 3% the page
     * advances further than at 8%, and both distances match
     * `viewport × (1 - overlap)`. The viewport is derived from the two
     * measurements themselves, so no device size is assumed.
     */
    @Test
    fun turnDistanceMatchesTheConfiguredOverlapOnTheRealViewport() {
        val settings = pageSettingsViewModel()
        openPage(numberedDocument(60), settings)
        usePageTurnMode(settings)

        compose.runOnIdle { settings.setPageOverlap(0.03f) }
        awaitSetting(0.03f) { flowValue(settings.pageOverlap) }
        compose.waitForIdle()
        val atThree = measuredTurnDistance()

        returnToTop()
        compose.runOnIdle { settings.setPageOverlap(0.08f) }
        awaitSetting(0.08f) { flowValue(settings.pageOverlap) }
        compose.waitForIdle()
        val atEight = measuredTurnDistance()

        assertTrue("both turns must move the page (3%=$atThree, 8%=$atEight)", atThree > 0f && atEight > 0f)
        assertTrue("a larger overlap must move less (3%=$atThree, 8%=$atEight)", atThree > atEight)
        val viewport = (atThree - atEight) / (0.08f - 0.03f)
        assertTrue("derived viewport must be plausible (was $viewport)", viewport > 200f)
        assertEquals("3% overlap distance", 0.97, (atThree / viewport).toDouble(), 0.02)
        assertEquals("8% overlap distance", 0.92, (atEight / viewport).toDouble(), 0.02)
    }

    /** Five consecutive turns advance monotonically and keep the document. */
    @Test
    fun fiveConsecutiveTurnsAdvanceWithoutLosingTheDocument() {
        val settings = pageSettingsViewModel()
        val vm = openPage(numberedDocument(80), settings)
        usePageTurnMode(settings)

        val seen = ArrayList<Int>()
        repeat(5) {
            seen.add(firstBlock())
            tapNext()
        }
        seen.add(firstBlock())

        assertEquals("one step per turn, never backwards", seen.sorted(), seen)
        assertTrue("five turns advance the page (was $seen)", seen.last() > seen.first())
        compose.runOnIdle {
            check(vm.ui.value is ReaderUiState.Content) { "document lost after turns" }
        }
    }

    /**
     * Tail clamp: after repeated forward taps the page stops moving, the last
     * block is on screen, and further taps are no-ops (no overshoot, no crash).
     */
    @Test
    fun tailClampStopsAtTheLastBlockAndFurtherTapsAreNoOps() {
        val settings = pageSettingsViewModel()
        openPage(numberedDocument(12), settings)
        usePageTurnMode(settings)

        repeat(20) { tapNext() }
        val clamped = firstBlock()
        assertTrue(
            "the last block is composed at the tail",
            PageViews.viewTexts(compose).any { it.startsWith("Paragraph 12") },
        )

        tapNext()
        assertEquals("a further forward tap must not move past the tail", clamped, firstBlock())
        tapNext()
        assertEquals("repeated taps stay clamped", clamped, firstBlock())
    }

    /** Head clamp: ten repeated backward taps land on block 1 and stop there. */
    @Test
    fun headClampStopsAtTheFirstBlock() {
        val settings = pageSettingsViewModel()
        openPage(numberedDocument(40), settings)
        usePageTurnMode(settings)

        repeat(6) { tapNext() }
        val steps = ArrayList<Int>()
        repeat(10) {
            steps.add(firstBlock())
            tapPrev()
        }
        steps.add(firstBlock())

        assertEquals("backward taps must walk home, was $steps", 1, firstBlock())
        tapPrev()
        assertEquals("a further backward tap must not move above the head", 1, firstBlock())
    }

    /** Centre tap toggles the menu and never turns the page (REQ-0005 FR-5). */
    @Test
    fun centreTapTogglesTheMenuWithoutTurning() {
        val settings = pageSettingsViewModel()
        openPage(numberedDocument(40), settings)
        usePageTurnMode(settings)

        val before = firstBlock()
        tapCenter()
        compose.onNodeWithText("Contents").assertIsDisplayed()
        compose.onNodeWithText("Search").assertIsDisplayed()
        assertEquals("opening the menu must not turn the page", before, firstBlock())

        tapCenter()
        assertEquals("closing the menu must not turn the page", before, firstBlock())
    }

    /** A real long-press never turns, even inside the next-page zone. */
    @Test
    fun longPressNeverTurnsThePage() {
        val settings = pageSettingsViewModel()
        openPage(numberedDocument(40), settings)
        usePageTurnMode(settings)

        val before = firstBlock()
        compose.onRoot().performTouchInput { longClick(Offset(listRect().right - 8f, listRect().center.y)) }
        compose.waitForIdle()

        assertEquals("long-press must not turn the page", before, firstBlock())
    }

    /** Side zones are inert in CONTINUOUS mode; only the centre menu reacts. */
    @Test
    fun continuousModeSideZonesDoNotTurn() {
        val settings = pageSettingsViewModel()
        openPage(numberedDocument(40), settings)
        compose.runOnIdle { settings.setReadingMode(ReadingMode.CONTINUOUS_SCROLL.stored) }
        awaitSetting(ReadingMode.CONTINUOUS_SCROLL.stored) { flowValue(settings.readingMode) }
        compose.waitForIdle()
        val before = firstBlock()
        repeat(3) { tapNext() }
        repeat(3) { tapPrev() }

        assertEquals("side taps are inert while scrolling is continuous", before, firstBlock())
    }

    /**
     * A link button is a real Compose control: tapping it follows the link and
     * must never be read as a page turn (T08 arbitration on the real page).
     */
    @Test
    fun linkButtonTapDoesNotTurnThePage() {
        val settings = pageSettingsViewModel()
        val body =
            """
            # Interaction Proof

            Paragraph 01 see [the notes](notes.md) for details.

            Paragraph 02 alpha beta gamma delta epsilon zeta eta.
            """.trimIndent()
        val vm = openPage(body, settings, docId = "links.md", expect = "Paragraph 01")
        usePageTurnMode(settings)
        val before = firstBlock()

        compose.onNodeWithText("the notes").performClick()
        compose.waitForIdle()

        assertEquals("following a link must not turn the page", before, firstBlock())
        compose.runOnIdle {
            check(vm.ui.value is ReaderUiState.Content) { "link tap lost the document" }
        }
    }

    /**
     * A horizontal gesture inside a wide table scrolls the table to its last
     * column and never turns the page (REQ-0007 FR-8).
     */
    @Test
    fun wideTableHorizontalGestureReachesLastColumnWithoutTurning() {
        val settings = pageSettingsViewModel()
        val long = "content-value-that-keeps-this-column-wide"
        val body =
            """
            # Interaction Proof

            | First Column $long | Second Column $long | Third Column $long | Fourth Column $long | Fifth Column $long | Last Column Token |
            | --- | --- | --- | --- | --- | --- |
            | a | b | c | d | e | LAST-COLUMN-VALUE |
            """.trimIndent()
        openPage(body, settings, docId = "table.md", expect = "First Column")
        usePageTurnMode(settings)
        val before = firstBlock()

        val initialMax = horizontalScrolls().maxOfOrNull { it.second } ?: 0f
        assertTrue("the wide table must be horizontally scrollable (max=$initialMax)", initialMax > 0f)

        var reachedEnd = false
        var value = 0f
        var max = initialMax
        for (attempt in 0 until 20) {
            compose.onAllNodes(
                androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(
                    androidx.compose.ui.semantics.SemanticsProperties.HorizontalScrollAxisRange,
                ),
            ).onFirst().performTouchInput { swipeLeft() }
            compose.waitForIdle()
            val current = horizontalScrolls().maxByOrNull { it.second } ?: (0f to 0f)
            value = current.first
            max = current.second
            if (value >= max - 1f) {
                reachedEnd = true
                break
            }
        }
        // Reaching `maxValue` means the container scrolled to the end of its
        // content: the final column is aligned with the viewport edge. The
        // per-cell `boundsInRoot` of a table cell reports empty once an
        // ancestor's scroll clip trims it, so the end-of-range measurement is
        // the reliable reachability probe (noted in the R3-05 delivery).
        assertTrue("the last table column became reachable (scroll $value / $max)", reachedEnd)
        val scroller = PageViews.scrollerBounds(compose, horizontal = true)
        assertTrue("the table scroller stays on screen (was $scroller)", scroller != null && !scroller.isEmpty)
        assertEquals("a horizontal table gesture must not turn the page", before, firstBlock())
    }

    /**
     * A long code line with soft wrap OFF scrolls horizontally to its very end
     * and that gesture never turns the page (REQ-0007 FR-5/AC-4).
     */
    @Test
    fun longCodeLineHorizontalGestureReachesTheTailWithoutTurning() {
        val settings = pageSettingsViewModel()
        val longLine = "val value = " + (1..80).joinToString(" + ") { "token$it" }
        val body = "# Interaction Proof\n\n```kotlin\n$longLine\n```\n\nParagraph 01 after the code.\n"
        openPage(body, settings, docId = "code.md", expect = "Paragraph 01")
        compose.runOnIdle { settings.setCodeSoftWrap(false) }
        usePageTurnMode(settings)
        val before = firstBlock()

        val initialMax = horizontalScrolls().maxOfOrNull { it.second } ?: 0f
        assertTrue("the long code line must be horizontally scrollable (max=$initialMax)", initialMax > 0f)
        val initialValue = horizontalScrolls().maxOfOrNull { it.first } ?: 0f
        assertEquals("the code line starts at its beginning", 0f, initialValue, 0.5f)

        var reachedEnd = false
        var previous = initialValue
        for (attempt in 0 until 15) {
            compose.onNodeWithText("val value", substring = true).performTouchInput { swipeLeft() }
            compose.waitForIdle()
            val (value, max) = horizontalScrolls().maxByOrNull { it.second } ?: (0f to 0f)
            assertTrue("each swipe moves the line left (was $previous, now $value)", value > previous)
            previous = value
            if (value >= max - 1f) {
                reachedEnd = true
                break
            }
        }
        assertTrue("the code line tail became reachable", reachedEnd)
        assertEquals("a horizontal code gesture must not turn the page", before, firstBlock())
    }

    /**
     * TOC landing then a page turn: the jump sets the visible block and the
     * next turn continues from there (REQ-0006).
     */
    @Test
    fun tocJumpThenTurnContinuesFromTheLandingBlock() {
        val settings = pageSettingsViewModel()
        val body =
            buildString {
                append("# Interaction Proof\n\n")
                for (i in 1..12) {
                    append("## Section $i\n\n")
                    repeat(3) { append("Paragraph %02d body alpha beta gamma delta.\n\n".format(i)) }
                }
            }
        openPage(body, settings, docId = "toc.md", expect = "Section 1")
        usePageTurnMode(settings)

        tapCenter()
        compose.onNodeWithText("Contents").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Section 4").performClick()
        compose.waitForIdle()
        val landed = firstBlock()
        assertTrue("TOC jump must move past the top (landed at $landed)", landed > 1)

        tapNext()
        assertTrue("a turn after the jump must advance", firstBlock() > landed)
    }

    /**
     * Rotation recalculates the turn distance from the new viewport: the
     * document is not reparsed and a turn still moves the page.
     */
    @Test
    fun rotationKeepsTheDocumentWithoutReparse() {
        val settings = pageSettingsViewModel()
        val vm = openPage(numberedDocument(80), settings, docId = "rotate.md")
        usePageTurnMode(settings)
        val documentBefore = compose.runOnIdle { (vm.ui.value as ReaderUiState.Content).document }

        val activity = PageViews.activityOf(compose)
        val original = PageViews.orientationOf(activity)
        val requested =
            if (original == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            } else {
                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }
        // Rotate away: the host Activity is recreated, so only ViewModel-level
        // state is asserted here (the composed tree is mid-recreation).
        PageViews.rotateTo(activity, requested)
        compose.waitForIdle()
        val documentAfter = compose.runOnIdle { (vm.ui.value as ReaderUiState.Content).document }
        assertTrue("rotation must not reparse the document", documentAfter === documentBefore)

        // NOTE (R3-05): `createComposeRule`'s host Activity is destroyed and
        // recreated by the orientation change, which tears down the composed
        // tree for the rest of the test — the page cannot be measured here.
        // Turning after a rotation is therefore covered by the code path
        // (distance is read from `listState.layoutInfo` on every turn) plus
        // the existing `ReaderDisplayTest.realRotationKeepsPositionWithoutReparse`;
        // the delivery report marks per-orientation distance measurement NOT_RUN.
        PageViews.rotateTo(activity, original)
        compose.waitForIdle()
        val back = compose.runOnIdle { vm.ui.value as ReaderUiState.Content }
        assertTrue("rotating back keeps the same document", back.document === documentBefore)
        assertEquals("rotating back keeps the restore block", documentBefore, back.document)
    }

    // -- UX-06: non-reflowing overlay chrome / first-run onboarding ----------

    /** Asserts the reading list kept its exact on-screen rect (<= 0.5 px). */
    private fun assertListGeometryUnchanged(
        step: String,
        before: androidx.compose.ui.geometry.Rect,
    ) {
        val now = listRect()
        assertEquals("$step: list left moved", before.left, now.left, 0.5f)
        assertEquals("$step: list top moved", before.top, now.top, 0.5f)
        assertEquals("$step: list width changed", before.width, now.width, 0.5f)
        assertEquals("$step: list height changed", before.height, now.height, 0.5f)
    }

    /**
     * UX-06 acceptance (ISS-018): toggling the reading menu or opening the
     * search bar must not resize the reading list. Every control floats over
     * the body, so the list keeps its exact rect — no relayout pass, and
     * therefore no E-Ink reflow flash.
     */
    @Test
    fun menuAndSearchOverlaysNeverResizeTheReadingList() {
        val settings = pageSettingsViewModel()
        openPage(numberedDocument(40), settings)
        usePageTurnMode(settings)

        val before = listRect()
        tapCenter()
        compose.onNodeWithText("Contents").assertIsDisplayed()
        assertListGeometryUnchanged("menu opened", before)

        compose.onNodeWithText("Search").performClick()
        compose.waitForIdle()
        assertListGeometryUnchanged("search bar opened", before)

        tapCenter()
        assertListGeometryUnchanged("menu closed", before)
    }

    /**
     * UX-06 acceptance (ISS-019): the document title is transient chrome, not a
     * permanent 64 dp header — it arrives with the reading menu and leaves with
     * it, and the body owns the whole viewport.
     *
     * A heading-less fixture is used so `document.title` is the file name and
     * cannot be confused with an H1 block rendered in the body.
     */
    @Test
    fun documentTitleIsTransientChromeNotAPermanentHeader() {
        val settings = pageSettingsViewModel()
        val body =
            buildString {
                for (i in 1..20) {
                    append("Paragraph %02d alpha beta gamma delta epsilon zeta eta.\n\n".format(i))
                }
            }
        openPage(body, settings, docId = "interaction.md")
        // `document.title` for a heading-less `interaction.md` is "interaction".
        compose.onNodeWithText("interaction").assertDoesNotExist()

        tapCenter()
        compose.onNodeWithText("interaction").assertIsDisplayed()

        tapCenter()
        compose.onNodeWithText("interaction").assertDoesNotExist()
    }

    /** Waits until a settings flag reports [expected] (writes are async). */
    private fun awaitBoolean(
        expected: Boolean,
        read: () -> Boolean,
    ) {
        val deadline = System.currentTimeMillis() + 5_000
        var seen = !expected
        while (System.currentTimeMillis() < deadline) {
            seen = read()
            if (seen == expected) return
            Thread.sleep(20)
        }
        assertEquals("setting did not reach its expected value", expected, seen)
    }

    /**
     * UX-06 acceptance (ISS-020): the tap-zone onboarding shows on a fresh
     * install, states the three zones, and "Got it" dismisses it for good — the
     * dismissal is persisted, so later opens never see it again.
     */
    @Test
    fun firstRunTapZoneHintAppearsDismissesAndIsPersisted() {
        val settings = pageSettingsViewModel(tapZonesHintSeen = false)
        openPage(numberedDocument(20), settings)

        compose.onNodeWithText("Tap zones").assertIsDisplayed()
        compose.onNodeWithText("Previous page").assertIsDisplayed()
        compose.onNodeWithText("Reading menu").assertIsDisplayed()
        compose.onNodeWithText("Next page").assertIsDisplayed()

        compose.onNodeWithText("Got it").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Tap zones").assertDoesNotExist()
        compose.onNodeWithText("Got it").assertDoesNotExist()

        // The dismissal is stored, which is what keeps it a one-time hint.
        awaitBoolean(true) { flowValue(settings.tapZonesHintSeen) }
    }

    /**
     * UX-06: while the hint is up, a tap in a page zone only dismisses it — the
     * hint consumes the touch, so nothing turns from under the overlay.
     */
    @Test
    fun tapZoneHintConsumesTheFirstTapWithoutTurning() {
        val settings = pageSettingsViewModel(tapZonesHintSeen = false)
        openPage(numberedDocument(40), settings)
        usePageTurnMode(settings)

        val before = firstBlock()
        tapNext()

        compose.onNodeWithText("Tap zones").assertDoesNotExist()
        assertEquals("the dismissing tap must not turn the page", before, firstBlock())
        awaitBoolean(true) { flowValue(settings.tapZonesHintSeen) }
    }

    // -- code review 2026-09-30: overlay occlusion regressions ---------------

    /** Root-relative rect of the reader chrome layer carrying [tag]. */
    private fun chromeBounds(tag: String): androidx.compose.ui.geometry.Rect = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot

    /**
     * CR-20260930-01/02/03 closure helper: the topmost composed body line must
     * start at or below the bottom edge of the top chrome. Body text renders
     * through `AndroidView` TextViews, which the plain view walk sees; Compose
     * chrome text does not appear there, so this measures the body alone.
     */
    private fun assertBodyStartsBelowTopChrome(step: String) {
        val chrome = chromeBounds(io.leaflet.reader.ui.reader.ReaderChromeTags.TOP_OVERLAY)
        val tops = PageViews.textTopsByPrefix(compose, "")
        assertTrue("$step: no body text composed", tops.isNotEmpty())
        val highest = tops.minOf { it.second }
        assertTrue(
            "$step: body text starts at $highest, behind the top chrome ending at ${chrome.bottom}",
            highest >= chrome.bottom - 1f,
        )
    }

    /** The lowest composed body line must end at or above the bottom chrome. */
    private fun assertBodyEndsAboveBottomChrome(step: String) {
        val chrome = chromeBounds(io.leaflet.reader.ui.reader.ReaderChromeTags.BOTTOM_OVERLAY)
        val rects = PageViews.textRectsByPrefix(compose, "")
        assertTrue("$step: no body text composed", rects.isNotEmpty())
        val lowest = rects.maxByOrNull { it.second.bottom }!!
        assertTrue(
            "$step: '${lowest.first}' ends at ${lowest.second.bottom}, behind the bottom chrome starting at ${chrome.top}",
            lowest.second.bottom <= chrome.top + 1f,
        )
    }

    /**
     * CR-20260930-01: the tail clamp must not stop while the last lines are still
     * behind the bottom chrome. Repeated forward turns have to reveal the final
     * line above the footer, and above the reading menu once it is open, before
     * further turns become no-ops.
     */
    @Test
    fun tailTurnRevealsTheLastLineAboveTheFooterAndTheOpenMenu() {
        val settings = pageSettingsViewModel()
        openPage(numberedDocument(40), settings)
        usePageTurnMode(settings)

        // Zero-height chrome while the menu is closed: reach the tail, then prove
        // the last line is clear of the footer and that further turns are no-ops.
        repeat(30) { tapNext() }
        assertBodyEndsAboveBottomChrome("menu closed")
        val atTail = firstBlock()
        tapNext()
        assertEquals("the tail must be clamped once the last line is revealed", atTail, firstBlock())

        // With the reading menu open the reserved tail gap grows; the tail must
        // still be revealable above it (this is the case the fixed 32 dp gap lost).
        tapCenter()
        compose.waitForIdle()
        assertTrue("the reading menu must be open", chromeBounds(io.leaflet.reader.ui.reader.ReaderChromeTags.BOTTOM_OVERLAY).height > 0f)
        repeat(5) { tapNext() }
        assertBodyEndsAboveBottomChrome("menu open")
        val openMenuTail = firstBlock()
        tapNext()
        assertEquals("the tail must stay clamped with the menu open", openMenuTail, firstBlock())
    }

    /**
     * CR-20260930-02: choosing a chapter in the table of contents must land the
     * heading below the top chrome — the reading menu (and its title overlay)
     * closes on the jump, so the heading is not covered — and the following page
     * turn must keep the body clear of the chrome.
     */
    @Test
    fun tocLandingIsNotCoveredByTheTopChrome() {
        val settings = pageSettingsViewModel()
        val body =
            buildString {
                append("# Interaction Proof\n\n")
                for (i in 1..12) {
                    append("## Section $i\n\n")
                    repeat(4) { append("Paragraph %02d body alpha beta gamma delta.\n\n".format(i)) }
                }
            }
        openPage(body, settings, docId = "toc-cover.md", expect = "Section 1")
        usePageTurnMode(settings)

        tapCenter()
        compose.onNodeWithText("Contents").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Section 4").performClick()
        compose.waitForIdle()

        // The menu closed with the jump, so nothing overlays the heading…
        compose.onNodeWithText("Contents").assertDoesNotExist()
        val heading =
            PageViews.textRectsByPrefix(compose, "Section 4").firstOrNull()
                ?: error("the TOC landing heading is not composed")
        val chrome = chromeBounds(io.leaflet.reader.ui.reader.ReaderChromeTags.TOP_OVERLAY)
        assertTrue(
            "the landing heading starts at ${heading.second.top}, behind the top chrome ending at ${chrome.bottom}",
            heading.second.top >= chrome.bottom - 1f,
        )
        // …and the turn that follows keeps the body below the chrome too.
        tapNext()
        assertBodyStartsBelowTopChrome("turn after the TOC jump")
    }

    /**
     * CR-20260930-03: a search hit in the FIRST block cannot be revealed by
     * scrolling — the list is already at its head. The reserved headroom under
     * the search bar has to keep it visible, in both reading modes, and after
     * stepping away to a later match and back.
     */
    @Test
    fun firstBlockSearchMatchStaysBelowTheSearchBar() {
        val settings = pageSettingsViewModel()
        val body =
            buildString {
                append("needle in block zero alpha beta gamma.\n\n")
                for (i in 2..30) {
                    val needle = if (i % 10 == 0) "needle" else "alpha"
                    append("Paragraph %02d $needle beta gamma delta epsilon.\n\n".format(i))
                }
            }
        val vm = openPage(body, settings, docId = "head-search.md", expect = "needle in block zero")

        // Search is opened through the real UI (centre tap -> Search) and typed
        // into the real field; only the stepping uses the ViewModel API.
        tapCenter()
        compose.onNodeWithText("Search").performClick()
        compose.waitForIdle()
        compose.onAllNodes(androidx.compose.ui.test.hasSetTextAction()).onFirst()
            .performTextInput("needle")
        compose.waitUntil(timeoutMillis = 15_000) { vm.search.value.matches.size >= 3 }
        compose.waitForIdle()

        fun assertCurrentMatchBelowChrome(step: String) {
            val current = vm.search.value.current ?: error("no current search match")
            val document = (vm.ui.value as ReaderUiState.Content).document
            val prefix = document.blockTexts[current.blockIndex]
            val tops = PageViews.textTopsByPrefix(compose, prefix)
            val chrome = chromeBounds(io.leaflet.reader.ui.reader.ReaderChromeTags.TOP_OVERLAY)
            assertTrue("$step: current match is composed", tops.isNotEmpty())
            assertTrue("$step: current match is below the search bar ($tops / $chrome)", tops.all { it.second >= chrome.bottom - 1f })
        }

        // Check the landing block; preceding context can legitimately remain behind the overlay.
        assertCurrentMatchBelowChrome("first match at the document head")

        compose.runOnIdle { vm.stepSearch(true) }
        compose.waitForIdle()
        assertCurrentMatchBelowChrome("later match")
        // Search wraps: one previous step returns from match 2 to match 1.
        compose.runOnIdle { vm.stepSearch(false) }
        compose.waitForIdle()
        assertEquals(0, vm.search.value.currentIndex)
        assertCurrentMatchBelowChrome("back at the first match")

        usePageTurnMode(settings)
        assertCurrentMatchBelowChrome("page-turn mode")
    }
}
