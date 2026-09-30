package io.leaflet.reader.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import io.leaflet.model.DisplayMode
import io.leaflet.reader.SettingsStore
import io.leaflet.reader.SettingsViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * R2 DEF-006 page-test helpers: the REAL view tree behind the composed
 * reader screen. `ReaderScreen` binds Markdown bodies to
 * `android.widget.TextView` through `AndroidView`, and those views are
 * invisible to Compose finders — the previous suite only asserted model
 * state, which is exactly why it could not catch display regressions.
 *
 * Reading semantics (`fetchSemanticsNode`) is a Compose synchronization
 * primitive: it may not run on the main thread. The root view is therefore
 * fetched on the test thread, and the plain-`View` walk (text, spans,
 * typography) runs on the main thread — mixing the two would throw
 * `Functions that involve synchronization ... cannot be run from the main
 * thread`.
 */
internal object PageViews {
    /**
     * Runs [block] on the MAIN thread and returns its result (rethrows
     * failures, times out instead of hanging forever).
     *
     * R2 DEF-008: the page harness must not touch the Android view tree or the
     * Activity from the instrumentation thread — `AndroidView` interop mutates
     * that tree during composition/teardown on the main thread, and
     * `Activity.setRequestedOrientation` off-main tears the Activity down
     * concurrently with Compose's own dispose pass (observed as a
     * `WrappedComposition.dispose` → `SlotWriter.removeGroup`
     * `ArrayIndexOutOfBoundsException` while the test activity was destroyed).
     * Compose synchronization APIs (`fetchSemanticsNode`, `waitForIdle`) stay
     * on the test thread and are handled by the caller.
     */
    fun <T> onMain(block: () -> T): T {
        if (Looper.myLooper() == Looper.getMainLooper()) return block()
        val latch = CountDownLatch(1)
        var result: T? = null
        var failure: Throwable? = null
        val accepted =
            Handler(Looper.getMainLooper()).post {
                try {
                    result = block()
                } catch (t: Throwable) {
                    failure = t
                } finally {
                    latch.countDown()
                }
            }
        check(accepted) { "main looper rejected the task" }
        check(latch.await(20, TimeUnit.SECONDS)) { "main-thread task timed out" }
        failure?.let { throw it }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    /** Text content of every TextView in the composed tree, in traversal order. */
    fun viewTexts(rule: ComposeContentTestRule): List<String> = allTextViews(rule).map { it.text.toString() }

    /** TextViews whose text contains [needle]. */
    fun textViewsContaining(
        rule: ComposeContentTestRule,
        needle: String,
    ): List<TextView> = allTextViews(rule).filter { it.text?.contains(needle) == true }

    /**
     * Live TextViews under the composition root. The root is resolved with the
     * Compose test API on the calling (test) thread, then the plain-`View` walk
     * runs on the main thread.
     */
    fun allTextViews(rule: ComposeContentTestRule): List<TextView> {
        val root = rootView(rule)
        return onMain { collectTextViews(root) }
    }

    // -- spans ---------------------------------------------------------------

    fun hasMonospaceSpan(textView: TextView): Boolean = spansOf(textView).any { it is android.text.style.TypefaceSpan }

    /**
     * Markwon renders bold as its own `StrongEmphasisSpan` (a
     * MetricAffectingSpan), NOT as a `StyleSpan` — checking only for
     * `StyleSpan` is how a page test would mis-report "no bold".
     */
    fun hasBoldSpan(textView: TextView): Boolean =
        spansOf(textView).any {
            (it as? android.text.style.StyleSpan)?.style == android.graphics.Typeface.BOLD ||
                it.javaClass.simpleName == "StrongEmphasisSpan"
        }

    /** Markwon renders italic as `EmphasisSpan`. */
    fun hasItalicSpan(textView: TextView): Boolean =
        spansOf(textView).any {
            (it as? android.text.style.StyleSpan)?.style == android.graphics.Typeface.ITALIC ||
                it.javaClass.simpleName == "EmphasisSpan"
        }

    fun hasBackgroundSpan(textView: TextView): Boolean = spansOf(textView).any { it is android.text.style.BackgroundColorSpan }

    /** Inclusive ranges of every background-colour span (search highlighting). */
    fun backgroundSpanRanges(textView: TextView): List<IntRange> {
        val spanned = textView.text as? android.text.Spanned ?: return emptyList()
        val out = ArrayList<IntRange>()
        for (span in spanned.getSpans(0, spanned.length, android.text.style.BackgroundColorSpan::class.java)) {
            out.add(spanned.getSpanStart(span)..(spanned.getSpanEnd(span) - 1))
        }
        return out
    }

    /** Inclusive ranges of every foreground-colour span (current match). */
    fun foregroundSpanRanges(textView: TextView): List<IntRange> {
        val spanned = textView.text as? android.text.Spanned ?: return emptyList()
        val out = ArrayList<IntRange>()
        for (span in spanned.getSpans(0, spanned.length, android.text.style.ForegroundColorSpan::class.java)) {
            out.add(spanned.getSpanStart(span)..(spanned.getSpanEnd(span) - 1))
        }
        return out
    }

    /**
     * Span styles of the composed `Text` nodes whose content is exactly
     * [text] — table cells and code bodies are Compose `Text`, invisible to
     * the TextView walk, but their `SemanticsProperties.Text` keeps the
     * `AnnotatedString` span styles the entry applied (R2 DEF-003: this is how
     * "only the selected cell carries the current-match style" is asserted on
     * the real page).
     */
    fun composedTextSpans(
        rule: ComposeContentTestRule,
        text: String,
    ): List<List<androidx.compose.ui.text.SpanStyle>> =
        rule.onAllNodesWithText(text).fetchSemanticsNodes().map { node ->
            val annotated: List<androidx.compose.ui.text.AnnotatedString> =
                if (node.config.contains(androidx.compose.ui.semantics.SemanticsProperties.Text)) {
                    node.config[androidx.compose.ui.semantics.SemanticsProperties.Text]
                } else {
                    emptyList()
                }
            annotated.flatMap { it.spanStyles }.map { it.item }
        }

    /** Simple class names of every span applied to a TextView (production view). */
    fun spanClassNames(textView: TextView): Set<String> = spansOf(textView).map { it.javaClass.simpleName }.toSet()

    fun spansOf(textView: TextView): List<Any> {
        val spanned = textView.text as? android.text.Spanned ?: return emptyList()
        if (spanned.length == 0) return emptyList()
        return spanned.getSpans(0, spanned.length, Any::class.java).toList()
    }

    // -- measurement ---------------------------------------------------------

    /**
     * Root-relative top Y of every TextView whose text starts with
     * [textPrefix], in view-tree order (R3-05 page-movement measurement).
     *
     * Coordinates are relative to the composition root, the same frame the
     * Compose test input APIs use, so a delta between two reads is the real
     * scroll distance applied to that view.
     */
    fun textTopsByPrefix(
        rule: ComposeContentTestRule,
        textPrefix: String,
    ): List<Pair<String, Float>> {
        val root = rootView(rule)
        val views = allTextViews(rule).filter { it.text?.startsWith(textPrefix) == true }
        return onMain {
            val rootLoc = IntArray(2).also { root.getLocationOnScreen(it) }
            views.map { view ->
                val loc = IntArray(2).also { view.getLocationOnScreen(it) }
                view.text.toString() to (loc[1] - rootLoc[1]).toFloat()
            }
        }
    }

    /** Root-relative rect of every TextView whose text starts with [textPrefix]. */
    fun textRectsByPrefix(
        rule: ComposeContentTestRule,
        textPrefix: String,
    ): List<Pair<String, android.graphics.RectF>> {
        val root = rootView(rule)
        val views = allTextViews(rule).filter { it.text?.startsWith(textPrefix) == true }
        return onMain {
            val rootLoc = IntArray(2).also { root.getLocationOnScreen(it) }
            views.map { view ->
                val loc = IntArray(2).also { view.getLocationOnScreen(it) }
                val left = (loc[0] - rootLoc[0]).toFloat()
                val top = (loc[1] - rootLoc[1]).toFloat()
                view.text.toString() to
                    android.graphics.RectF(left, top, left + view.width, top + view.height)
            }
        }
    }

    /** Every Compose `Text` value in the tree (Compose-side text, e.g. progress). */
    fun composeTexts(rule: ComposeContentTestRule): List<String> {
        val key = androidx.compose.ui.semantics.SemanticsProperties.Text
        return rule.onAllNodes(androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(key))
            .fetchSemanticsNodes()
            .flatMap { node ->
                if (node.config.contains(key)) node.config[key].map { it.text } else emptyList()
            }
    }

    /**
     * `(value, maxValue)` of every horizontal scroller on the page
     * (R3-05: a table/code gesture scrolls its container to the end; reading
     * the scroll range proves the hidden tail is reachable instead of trusting
     * a clipped node's bounds).
     */
    fun horizontalScrollOffsets(rule: ComposeContentTestRule): List<Pair<Float, Float>> =
        rule.onAllNodes(
            androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(
                androidx.compose.ui.semantics.SemanticsProperties.HorizontalScrollAxisRange,
            ),
        ).fetchSemanticsNodes().map { node ->
            val range = node.config[androidx.compose.ui.semantics.SemanticsProperties.HorizontalScrollAxisRange]
            range.value() to range.maxValue()
        }

    /**
     * Root-relative bounds of the first vertical/horizontal scrollable node
     * (R3-05: the tap-zone detector lives on the reader's LazyColumn, so taps
     * must be aimed inside exactly this rect; the same rect drives the table /
     * code horizontal swipes).
     */
    fun scrollerBounds(
        rule: ComposeContentTestRule,
        horizontal: Boolean,
    ): androidx.compose.ui.geometry.Rect? {
        val key =
            if (horizontal) {
                androidx.compose.ui.semantics.SemanticsProperties.HorizontalScrollAxisRange
            } else {
                androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange
            }
        val node =
            rule.onAllNodes(androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(key))
                .fetchSemanticsNodes()
                .firstOrNull()
                ?: return null
        return node.boundsInRoot
    }

    // -- activity / rotation -------------------------------------------------

    /** Live `AndroidComposeView` behind the composition (test thread only). */
    fun rootView(rule: ComposeContentTestRule): View {
        val root = rule.onRoot().fetchSemanticsNode().root
        return root as? View ?: error("composition root is not a View: $root")
    }

    /**
     * The host activity of the composed tree (`createComposeRule` launches an
     * empty ComponentActivity from the debug manifest / `ui-test-manifest`).
     */
    fun activityOf(rule: ComposeContentTestRule): Activity {
        val root = rootView(rule)
        return onMain {
            var context: Context? = root.context
            while (context is ContextWrapper) {
                if (context is Activity) return@onMain context
                context = context.baseContext
            }
            error("no Activity above the composition root")
        }
    }

    /**
     * Requests an orientation change ON THE MAIN THREAD, then waits (off the
     * main thread) until the Activity reports the new configuration. Returns
     * the orientation that was reached.
     */
    fun rotateTo(
        activity: Activity,
        requested: Int,
    ) {
        val expected =
            if (requested == android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) {
                android.content.res.Configuration.ORIENTATION_PORTRAIT
            } else {
                android.content.res.Configuration.ORIENTATION_LANDSCAPE
            }
        onMain { activity.requestedOrientation = requested }
        val deadline = System.currentTimeMillis() + 20_000
        while (System.currentTimeMillis() < deadline && orientationOf(activity) != expected) {
            Thread.sleep(50)
        }
        check(orientationOf(activity) == expected) {
            "activity did not reach orientation $expected (was ${orientationOf(activity)})"
        }
    }

    fun orientationOf(activity: Activity): Int = onMain { activity.resources.configuration.orientation }

    private fun collectTextViews(root: View): List<TextView> {
        val out = ArrayList<TextView>()

        fun walk(view: View) {
            if (view is TextView) out.add(view)
            if (view is ViewGroup) {
                for (i in 0 until view.childCount) walk(view.getChildAt(i))
            }
        }
        walk(root)
        return out
    }
}

/**
 * In-memory DataStore for page tests: the production [SettingsStore] runs
 * unchanged (real coercion/defaults/persistence semantics) with no disk I/O,
 * so a settings change on the page flows exactly like production.
 */
internal class MemoryPreferencesDataStore : DataStore<Preferences> {
    private val state = MutableStateFlow(emptyPreferences())

    override val data: Flow<Preferences> = state

    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
        val next = transform(state.value)
        state.value = next
        return next
    }
}

/**
 * Production [SettingsViewModel] over an in-memory store (no DataStore file).
 *
 * UX-06: the first-run tap-zone hint is marked SEEN by default, so the existing
 * page suites never compose the onboarding overlay; the dedicated onboarding
 * tests pass `tapZonesHintSeen = false` to drive the real first-open path.
 */
internal fun pageSettingsViewModel(
    defaultMode: DisplayMode = DisplayMode.STANDARD,
    tapZonesHintSeen: Boolean = true,
): SettingsViewModel {
    val store = SettingsStore(MemoryPreferencesDataStore(), defaultMode)
    runBlocking { store.setTapZonesHintSeen(tapZonesHintSeen) }
    return SettingsViewModel(store)
}

/** Reads the first value of a settings flow (suspending flows in a page test). */
internal fun <T> flowValue(flow: Flow<T>): T = runBlocking { flow.first() }
