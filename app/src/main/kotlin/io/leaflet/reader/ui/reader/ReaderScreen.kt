package io.leaflet.reader.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import io.leaflet.eink.DisplayModeId
import io.leaflet.eink.EInkController
import io.leaflet.eink.controllerForMode
import io.leaflet.eink.profileFor
import io.leaflet.eink.progressMinIntervalMs
import io.leaflet.eink.progressUpdateDelayMs
import io.leaflet.markdown.BlockKind
import io.leaflet.markdown.ReaderStyle
import io.leaflet.model.DisplayMode
import io.leaflet.model.DocumentId
import io.leaflet.reader.PageTurn
import io.leaflet.reader.PositionRestore
import io.leaflet.reader.R
import io.leaflet.reader.ReaderFailure
import io.leaflet.reader.ReadingLayoutResolver
import io.leaflet.reader.ReadingMode
import io.leaflet.reader.SettingsViewModel
import io.leaflet.reader.TapZones
import io.leaflet.reader.TocNavigation
import io.leaflet.reader.TurnIntent
import io.leaflet.reader.keepBlock
import io.leaflet.storage.prefs.ReaderSettings
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Reader screen (T07 body, T08 navigation): loading / content / failed+retry /
 * degraded banner, plus the TOC drawer, the reading-mode switch and the
 * three tap zones (REQ-0005/REQ-0006).
 *
 * Restore (REQ-0003 FR-3): the list measures the restored block, then projects
 * its character address through the current line layout — instant, never smooth.
 * Scroll
 * observations feed [ReaderViewModel.onVisibleBlock] (throttled save); the
 * lifecycle observer flushes on pause/stop (exit flush), and `DisposableEffect`
 * flushes on dispose (back navigation / route pop).
 *
 * T08 scrolling rule: every programmatic move is instant — `scrollToItem`
 * for restore/TOC jumps, `scroll { scrollBy(...) }` for page turns. No
 * `smoothScroll`/`animateScrollToItem` family call exists on this path
 * (E-Ink rule, REQ-0005 FR-6).
 */
@Composable
fun ReaderScreen(
    viewModel: ReaderViewModel,
    documentId: String,
    onBack: () -> Unit,
    onReauthorize: () -> Unit = {},
    onRemoveDeadEntry: ((String) -> Unit)? = null,
    settingsViewModel: SettingsViewModel? = null,
    defaultDisplayMode: DisplayMode = DisplayMode.STANDARD,
    onOpenDocument: (DocumentId) -> Unit = {},
    imageLoader: ImageLoader? = null,
    onOpenExternal: (String) -> Unit = { url -> openExternal(url, viewModel) },
) {
    val id = DocumentId(documentId)
    LaunchedEffect(documentId) { viewModel.open(id) }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, documentId) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                    viewModel.flushPosition()
                }
            }
        lifecycle.addObserver(observer)
        onDispose {
            viewModel.flushPosition()
            viewModel.onReaderExit(id)
            lifecycle.removeObserver(observer)
        }
    }

    val ui by viewModel.ui.collectAsState()
    when (val state = ui) {
        is ReaderUiState.Loading -> ReaderLoading(onBack = onBack)
        is ReaderUiState.Content ->
            ReaderContent(
                viewModel = viewModel,
                settingsViewModel = settingsViewModel,
                defaultDisplayMode = defaultDisplayMode,
                state = state,
                onBack = onBack,
                onOpenDocument = onOpenDocument,
                imageLoader = imageLoader,
                onOpenExternal = onOpenExternal,
            )
        is ReaderUiState.Failed ->
            ReaderFailed(
                reason = state.reason,
                message = state.message,
                onRetry = { viewModel.retry(id) },
                onBack = onBack,
                onReauthorize = onReauthorize,
                onRemoveDeadEntry = onRemoveDeadEntry?.let { { it(documentId) } },
            )
    }
}

@Composable
private fun ReaderLoading(onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text(stringResource(R.string.reader_loading), style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = onBack) { Text(stringResource(R.string.go_back)) }
    }
}

@Composable
private fun ReaderContent(
    viewModel: ReaderViewModel,
    settingsViewModel: SettingsViewModel?,
    defaultDisplayMode: DisplayMode,
    state: ReaderUiState.Content,
    onBack: () -> Unit,
    onOpenDocument: (DocumentId) -> Unit = {},
    imageLoader: ImageLoader? = null,
    onOpenExternal: (String) -> Unit = { url -> openExternal(url, viewModel) },
) {
    val docId = state.document.documentId.value
    val listState = key(docId) { rememberLazyListState() }
    val scope = rememberCoroutineScope()
    // Scope the list to its document, not the reading mode. A remount projects
    // the latest logical address through the new layout without reparsing;
    // a CONTINUOUS↔PAGE_TURN switch keeps the existing list (REQ-0005 AC-5).
    val positionGeometry = remember(docId) { BlockPositionGeometry() }
    var positionRestored by remember(docId) { mutableStateOf(false) }
    var restoreEcho by remember(docId) { mutableStateOf<Pair<Int, Int>?>(null) }
    // UX-06 overlay chrome, measured in pixels:
    // - `topChromePx` is the whole top overlay (title + notices + search bar);
    //   it is what a page turn subtracts as occlusion.
    // - `topInsetPx` is the part of that overlay which is reserved as list
    //   headroom (notices + search bar, never the title). The body is inset by
    //   it, so a notice or the search bar can never hide content — including a
    //   match in the very first block, which no scroll can reveal at the head
    //   (CR-20260930-03). The title is deliberately NOT part of the inset: it
    //   only covers the body while the reading menu is open, so toggling that
    //   menu still cannot move the body.
    // - `bottomChromePx` sizes the tail gap (CR-20260930-01).
    // None of them is read during the list's own composition, so writing them
    // never changes the list's measured geometry.
    var topChromePx by remember(docId) { mutableStateOf(0) }
    var topInsetPx by remember(docId) { mutableStateOf(0) }
    var bottomChromePx by remember(docId) { mutableStateOf(0) }

    /**
     * Instant jump that lands [target] below the chrome covering the top of the
     * body (UX-06, CR-20260930-02/03). `scrollToItem`'s documented offset is
     * positive = forward = item further up, so a negative offset of the chrome
     * pushes the landing block down by exactly that much. At the document head no
     * negative scroll is possible, which is why the notices/search bar also
     * reserve their own [topInsetPx] headroom.
     *
     * [revealPx] defaults to the measured top chrome. A caller that changes the
     * chrome in the same action (the TOC jump closes the reading menu) passes the
     * chrome that will actually remain, so the landing is not left too low.
     */
    suspend fun jumpToBlockRevealed(
        target: Int,
        revealPx: Int = topChromePx,
    ) {
        listState.scrollToItem(target, if (revealPx > 0) -revealPx else 0)
    }

    LaunchedEffect(docId) {
        // A handed-off fragment owns the initial landing; a simultaneous saved
        // offset restore must not move the list back after that anchor jump.
        val fragment = viewModel.takePendingFragment(state.document.documentId)
        val fragmentTarget = fragment?.let { io.leaflet.reader.FragmentMatch.resolve(state.document, it) }
        val currentLocation = viewModel.readingLocationFor(state.document.documentId)
        val target =
            (fragmentTarget ?: currentLocation?.blockIndex ?: state.restoreBlock)
                .coerceIn(0, (state.document.blocks.size - 1).coerceAtLeast(0))
        val restoredOffset = if (fragmentTarget == null) currentLocation?.blockOffset ?: state.restoreBlockOffset else 0
        if (target > 0 || restoredOffset > 0 || fragmentTarget != null) listState.scrollToItem(target)
        if (restoredOffset > 0 && state.document.blocks.isNotEmpty()) {
            snapshotFlow { listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == target } }
                .filterNotNull().first()
            // Placement/text layout follows the initial item measurement. Wait
            // for it before projecting the saved character into new pixels.
            withFrameNanos { }
            val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == target }
            if (item != null) {
                val pixels =
                    positionGeometry.scrollOffset(
                        target,
                        restoredOffset,
                        item.size,
                        state.document.blockTexts.getOrElse(target) { "" }.length,
                    )
                listState.scrollToItem(target, pixels)
            }
        }
        if (fragmentTarget != null) viewModel.jumpToBlock(target)
        restoreEcho = listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        positionRestored = true
    }
    // Same-document anchor jumps (T10): the ViewModel planned the scroll;
    // the screen executes it instantly (E-Ink rule) exactly once.
    //
    // R3-06 DEF: this effect used to be keyed on (docId, linkNotice) only. A
    // successful `#anchor` jump changes neither (only the rejection path sets a
    // notice), so the pending jump was never consumed and the page did not
    // move. The restore-address change is the observable that must re-run it.
    LaunchedEffect(docId, state.linkNotice, state.restoreBlock) {
        val target = viewModel.takePendingJump() ?: return@LaunchedEffect
        if (target in state.document.blocks.indices) {
            jumpToBlockRevealed(target)
        }
    }
    // Scroll observer → throttled save (REQ-0003 FR-2). Distinct + non-zero
    // progress guard: the initial restore jump is seeded in the ViewModel and
    // never writes.
    LaunchedEffect(docId) {
        snapshotFlow {
            if (positionRestored) listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset else null
        }
            .filterNotNull()
            .distinctUntilChanged()
            .collectLatest { address ->
                // A character can land midway through a rendered line. Keep the
                // original address until real movement, rather than saving the
                // line start merely because restoration was observed.
                if (address == restoreEcho) return@collectLatest
                restoreEcho = null
                withFrameNanos { }
                val index = listState.firstVisibleItemIndex
                val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index } ?: return@collectLatest
                val offset =
                    positionGeometry.characterOffset(
                        index,
                        listState.firstVisibleItemScrollOffset,
                        item.size,
                        state.document.blockTexts.getOrElse(index) { "" }.length,
                    )
                viewModel.onVisiblePosition(index, offset, state.document.documentId)
            }
    }

    val modeStored: String =
        if (settingsViewModel != null) {
            // CR-20260930-05: the pre-read default follows the display mode, so a
            // fresh E-Ink install does not show one continuous-scroll frame.
            settingsViewModel.readingMode.collectAsState(
                initial = ReaderSettings.defaultReadingModeFor(defaultDisplayMode),
            ).value
        } else {
            ReaderSettings.defaultReadingModeFor(defaultDisplayMode)
        }
    val mode = ReadingMode.of(modeStored)
    val overlap: Float =
        if (settingsViewModel != null) {
            settingsViewModel.pageOverlap.collectAsState(initial = ReaderSettings.PAGE_OVERLAP_DEFAULT).value
        } else {
            ReaderSettings.PAGE_OVERLAP_DEFAULT
        }
    val displayMode: DisplayMode =
        if (settingsViewModel != null) {
            settingsViewModel.displayMode.collectAsState(initial = defaultDisplayMode).value
        } else {
            defaultDisplayMode
        }
    // Typography (T09/REQ-0007 FR-1–FR-4/FR-7/FR-10): persisted steps flow one
    // way from SettingsStore into the entry binding below. Reflow never
    // re-parses (blocks are reused); context is kept via keepBlock + the
    // instant scroll-back further down (no pixels-as-position).
    val fontSizeStep =
        if (settingsViewModel != null) {
            settingsViewModel.fontSizeStep.collectAsState(initial = ReaderSettings.FONT_SIZE_DEFAULT).value
        } else {
            ReaderSettings.FONT_SIZE_DEFAULT
        }
    val fontFamily =
        if (settingsViewModel != null) {
            settingsViewModel.fontFamily.collectAsState(initial = ReaderSettings.FONT_FAMILY_DEFAULT).value
        } else {
            ReaderSettings.FONT_FAMILY_DEFAULT
        }
    val lineSpacingStep =
        if (settingsViewModel != null) {
            settingsViewModel.lineSpacingStep.collectAsState(initial = ReaderSettings.LINE_SPACING_DEFAULT).value
        } else {
            ReaderSettings.LINE_SPACING_DEFAULT
        }
    val pageMarginStep =
        if (settingsViewModel != null) {
            settingsViewModel.pageMarginStep.collectAsState(initial = ReaderSettings.PAGE_MARGIN_DEFAULT).value
        } else {
            ReaderSettings.PAGE_MARGIN_DEFAULT
        }
    val softWrapStored =
        if (settingsViewModel != null) {
            settingsViewModel.codeSoftWrap.collectAsState(initial = null).value
        } else {
            null
        }
    val isEink = displayMode == DisplayMode.EINK
    // One display-profile seam (REQ-0004 FR-1): the typography numbers and the progress
    // cadence below come from the profile, never from a local `if (isEink)` branch.
    val displayProfile =
        remember(displayMode) {
            profileFor(if (isEink) DisplayModeId.EINK else DisplayModeId.STANDARD)
        }
    val softWrapOn = settingsViewModel?.effectiveCodeSoftWrap(softWrapStored, isEink) ?: isEink
    val style =
        ReaderStyle(
            fontSizeStep = fontSizeStep,
            fontFamily = fontFamily,
            lineSpacingStep = lineSpacingStep,
            pageMarginStep = pageMarginStep,
            codeSoftWrap = softWrapOn,
        ).coerce()
    // Relayout context (T09/REQ-0007 scope §25): any style change records the
    // pre-change first visible block and scrolls back to it instantly after
    // reflow (same keepBlock + scrollToItem seam as the T08 mode switch, and
    // the same jumpToBlock instant persist — never smooth, E-Ink rule).
    LaunchedEffect(style, docId) {
        val kept = keepBlock(listState.firstVisibleItemIndex, state.document.blocks.size)
        if (kept != listState.firstVisibleItemIndex) listState.scrollToItem(kept)
    }
    // T12 lifecycle pairing (REQ-0004 FR-7): controller comes from the
    // `:core:eink` seam so Theme/Reader/Container agree per mode; enter/exit
    // fire once per document via DisposableEffect below; turns fire per turn.
    val eink: EInkController =
        remember(displayMode) {
            controllerForMode(
                if (displayMode == DisplayMode.EINK) DisplayModeId.EINK else DisplayModeId.STANDARD,
            )
        }
    DisposableEffect(eink, docId) {
        eink.onReaderEnter(null)
        onDispose { eink.onReaderExit(null) }
    }
    // Throttled footer progress (REQ-0004 FR-4, REQ-0005 FR-8): under a conservative refresh
    // policy the footer text changes at most once per interval — the first value shows at once,
    // a burst of block crossings collapses to one trailing update. Standard has interval 0 and
    // reads the live block, exactly as before. The save path never reads this value.
    val progressIntervalMs = displayProfile.refreshPolicy.progressMinIntervalMs
    var shownFirstBlock by remember(docId) { mutableIntStateOf(listState.firstVisibleItemIndex) }
    LaunchedEffect(docId, progressIntervalMs) {
        if (progressIntervalMs <= 0L) return@LaunchedEffect
        var lastShownAtMs: Long? = null
        snapshotFlow { listState.firstVisibleItemIndex }.collectLatest { index ->
            val wait = progressUpdateDelayMs(android.os.SystemClock.elapsedRealtime(), lastShownAtMs, progressIntervalMs)
            if (wait > 0L) delay(wait)
            shownFirstBlock = index
            lastShownAtMs = android.os.SystemClock.elapsedRealtime()
        }
    }
    var menuVisible by remember(docId) { mutableStateOf(false) }
    var tocOpen by remember(docId) { mutableStateOf(false) }
    // Static arrival highlight (REQ-0006): the TOC-jump target block renders
    // bold until the next jump or document change. No animation (E-Ink safe).
    var jumpedBlock by remember(docId) { mutableStateOf<Int?>(null) }
    // UX-06 first-run tap-zone onboarding: the stored flag decides, and a tap
    // dismissal hides it immediately without waiting for the write.
    val hintSeen: Boolean? =
        if (settingsViewModel != null) {
            settingsViewModel.tapZonesHintSeen.collectAsState(initial = null).value
        } else {
            null
        }
    var hintDismissed by remember(docId) { mutableStateOf(false) }
    var hintRequested by remember(docId) { mutableStateOf(false) }
    val showTapZoneHint = hintRequested || TapZones.shouldShowHint(hintSeen, hintDismissed)
    // In-document search (T11/REQ-0008): bar + count + prev/next + close.
    // All search state resets on document switch via remember(docId);
    // the ViewModel additionally clears on open (FR-5).
    var searchOpen by remember(docId) { mutableStateOf(false) }
    var searchInput by remember(docId) { mutableStateOf("") }
    val searchState by viewModel.search.collectAsState()
    // Search-result jump (T11): one instant scrollToItem per jumpToken bump
    // (new hits, prev/next) — never smooth (E-Ink rule). Matches reuse the
    // loaded blocks via the position map; no re-parse (REQ-0008 FR-6).
    LaunchedEffect(searchState.jumpToken, docId) {
        val target = searchState.current ?: return@LaunchedEffect
        if (target.blockIndex in state.document.blocks.indices) {
            jumpedBlock = target.blockIndex
            // UX-06 + CR-20260930-03: the reserved search-bar headroom keeps the
            // match visible, including a match in block 0 at the document head.
            jumpToBlockRevealed(target.blockIndex)
            viewModel.jumpToBlock(target.blockIndex)
        }
    }

    // `scrollBy`, never smooth. The viewport is read fresh from layoutInfo
    // per turn, so rotation is picked up. UX-06: the chrome floats over the list
    // instead of shrinking it, so the distance uses the VISIBLE height — the
    // chrome that covers body content is subtracted via
    // [PageTurn.visibleHeightPx] (REQ-0005 FR-2) and a turn never skips content
    // hidden behind it. The head/tail clamp asks the list for its REAL scroll
    // boundary (`canScrollForward`/`canScrollBackward`, CR-20260930-01) rather
    // than inferring it from the last item's position: the tail gap reserves the
    // measured bottom chrome, so a forward turn stays legal while the last lines
    // still sit behind the footer/menu and stops only once they are revealed.
    val pageTurnMutex = remember(docId) { Mutex() }

    fun turnPage(forward: Boolean) {
        if (!positionRestored || showTapZoneHint) return
        scope.launch {
            pageTurnMutex.withLock {
                val info = listState.layoutInfo
                val viewport = info.viewportSize.height
                if (viewport <= 0) return@withLock
                val visible =
                    PageTurn.visibleHeightPx(
                        totalHeightPx = viewport,
                        // The whole top overlay is unavailable to the body: the
                        // reserved headroom (notices/search) plus the title that may
                        // protrude over it.
                        topObscuredPx = topChromePx,
                        bottomObscuredPx = bottomChromePx,
                    )
                val distance = PageTurn.turnDistancePx(visible, overlap)
                if (distance <= 0) return@withLock
                if (forward && !listState.canScrollForward) return@withLock
                if (!forward && !listState.canScrollBackward) return@withLock
                eink.onScrollStart(null)
                listState.scroll { scrollBy((if (forward) distance else -distance).toFloat()) }
                eink.onScrollEnd(null)
                eink.onPageTurn(null)
            }
        }
    }

    fun jumpToAnchor(anchor: String) {
        val target = TocNavigation.resolve(state.document, anchor) ?: return
        jumpedBlock = target
        tocOpen = false
        // CR-20260930-02: choosing a chapter returns to the body, so the
        // reading menu — and with it the title overlay — closes. Otherwise the
        // landing heading would sit behind the title.
        menuVisible = false
        scope.launch {
            // The title leaves with the menu, so only the reserved headroom still
            // covers the top when this landing settles.
            jumpToBlockRevealed(target, topInsetPx)
            viewModel.jumpToBlock(target)
            eink.onPageTurn(null)
        }
    }

    if (tocOpen) {
        TocDrawer(
            document = state.document,
            currentBlock = listState.firstVisibleItemIndex,
            onJump = ::jumpToAnchor,
            onClose = { tocOpen = false },
        )
        return
    }
    // Page margin (FR-4): horizontal padding from the margin step; the title
    // and body share it so reflow is uniform.
    // Typography, margins, measure and block rhythm come from the display profile's
    // reading-layout policy (REQ-0007); Standard resolves to the same numbers as before.
    val layout = ReadingLayoutResolver.resolve(style, displayProfile.readingLayout)
    val pageMarginDp = layout.pageMarginDp.dp
    val bodyFontSize = layout.fontSizeSp.sp
    val bodyLineHeight = layout.lineHeightSp.sp
    val bodyFontFamily = fontFamilyFor(style.fontFamily)
    val blockPadding = layout.blockPaddingDp.dp
    // The measure is an em count, so it follows the font size (and the system font scale);
    // Dp.Unspecified leaves the width unconstrained.
    val measureMax: Dp = layout.maxMeasureSp?.let { with(LocalDensity.current) { it.sp.toDp() } } ?: Dp.Unspecified
    val bodyTextColor = MaterialTheme.colorScheme.onSurface.toArgb()
    // High contrast: links keep the text colour and rely on their underline, instead of a
    // chromatic link blue that becomes a mid-gray on a grayscale panel.
    val bodyText =
        BodyTextPolicy(
            textColor = bodyTextColor,
            linkColor = if (displayProfile.highContrastEnabled) bodyTextColor else null,
            exactLineHeight = layout.exactLineHeight,
            justify = layout.justify,
            hyphenate = layout.hyphenate,
        )
    // List headroom/tail reserve (UX-06, CR-20260930-01/03, CR-20261001-03).
    // - The HEADROOM reserves the notices/search bar so they can never hide body
    //   content (a first-block match cannot be scrolled clear at the document
    //   head). The reading menu is deliberately NOT part of it, so toggling that
    //   menu never moves the body.
    // - The TAIL reserve must cover whatever the bottom chrome shows while the
    //   last line stays reachable (CR-20260930-01), and it must NOT shrink when
    //   the menu closes: at the tail a smaller reserve lowers the maximum scroll
    //   offset, so the list clamps backwards and the body moves
    //   (CR-20261001-03). It therefore starts from a floor that already covers
    //   the footer plus a menu row — footer ≈ 29 dp (1 dp divider + one
    //   bodySmall line + 8 dp padding) and the Material 3 TextButton row ≥ 40 dp
    //   — and only ever GROWS to the measured chrome at larger system font
    //   scales. Growing cannot move anything: the padding sits after the last
    //   item, so item positions are unchanged and only the scroll range extends.
    val chromeDensity = LocalDensity.current
    val tailGapPx = with(chromeDensity) { 8.dp.roundToPx() }
    val tailFloorPx = with(chromeDensity) { 96.dp.roundToPx() }
    var tailReservePx by remember { mutableStateOf(tailFloorPx) }
    LaunchedEffect(bottomChromePx, tailFloorPx) {
        val needed = maxOf(tailFloorPx, bottomChromePx + tailGapPx)
        if (needed > tailReservePx) tailReservePx = needed
    }
    val listContentPadding =
        PaddingValues(
            top = with(chromeDensity) { topInsetPx.toDp() },
            bottom = with(chromeDensity) { tailReservePx.toDp() },
        )
    // Keep the running detector's callbacks current without cancelling a finger
    // gesture when menu geometry or display policy changes. Page margins are
    // part of the hit area; only child controls can claim their own touches.
    val gestureAction by rememberUpdatedState(
        newValue = { intent: TurnIntent ->
            when {
                !positionRestored || showTapZoneHint -> Unit
                intent == TurnIntent.PREV_PAGE -> turnPage(false)
                intent == TurnIntent.NEXT_PAGE -> turnPage(true)
                intent == TurnIntent.TOGGLE_MENU -> menuVisible = !menuVisible
                else -> Unit
            }
        },
    )
    val swipeThresholdPx = with(chromeDensity) { 48.dp.toPx() }
    Column(
        modifier =
            Modifier.fillMaxSize()
                .testTag("reader.gestures")
                .pointerInput(mode, swipeThresholdPx, docId) {
                    detectReaderPageGestures(mode, swipeThresholdPx) { gestureAction(it) }
                }
                .padding(horizontal = pageMarginDp, vertical = 16.dp),
    ) {
        // UX-06 non-reflowing reader (ISS-018/ISS-019): the list is the only
        // measured child of this Column — full height — and all chrome
        // (document title, notices, search bar, reading menu, progress footer)
        // is an overlay layer inside this Box. The chrome is declared AFTER the
        // list so it draws on top; only its own controls consume touches, and
        // the measured overlay heights feed `turnPage` (visible-height
        // accounting), the list's headroom/tail gap and the jump reveal.
        BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
            val actionMenuMaxHeight = maxHeight * 0.4f
            LazyColumn(
                state = listState,
                // PAGE_TURN moves only on completed taps/swipes and TOC jumps:
                // finger drags never smear the E-Ink screen.
                userScrollEnabled = mode == ReadingMode.CONTINUOUS_SCROLL,
                // Body inset: headroom under the notices/search bar, and a tail
                // gap that covers the bottom chrome (CR-20260930-01/03). Both
                // only change when the chrome itself changes.
                contentPadding = listContentPadding,
                modifier = Modifier.fillMaxSize(),
            ) {
                itemsIndexed(state.document.blocks, key = { index, _ -> index }) { index, block ->
                    DisposableEffect(docId, index) {
                        onDispose { positionGeometry.remove(index) }
                    }
                    Column(
                        modifier =
                            Modifier.fillMaxWidth().onGloballyPositioned {
                                positionGeometry.blockTop(index, it.positionInRoot().y)
                            },
                        // Only measure-capped text entries are narrower than the item, and they
                        // are centred; code, tables and images fill the width as before.
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        // R2 DEF-003: the highlight is mapped against the string
                        // THIS entry renders. Markdown paragraphs render through
                        // Markwon (markers consumed), so the rendered text is the
                        // mapping source; Compose entries render their stripped
                        // text. Passing `block.text` (the source slice) is what
                        // shifted spans off the matched word.
                        val rendered =
                            if (block.kind == BlockKind.CODE || block.kind == BlockKind.TABLE || block.literal) {
                                null
                            } else {
                                // Same cache key as SpannedTextView's own remember,
                                // so highlight mapping and rendering agree on ONE
                                // instance per block. The SPANNED value is kept (it
                                // carries Markwon's bold/italic/code spans — a
                                // `.toString()` here silently dropped every
                                // character style); measurement uses its text.
                                remember(block.source, block.text) {
                                    viewModel.parser.renderer.renderSpanned(block)
                                }
                            }
                        val highlight =
                            searchHighlightFor(
                                document = state.document,
                                blockIndex = index,
                                block = block,
                                search = searchState,
                                // TABLE entries lay their cells out in the block's
                                // visible string, so that string is the coordinate
                                // base for them too (R2 DEF-003: measuring tables
                                // against the pipe slice drifted matches across
                                // rows).
                                displayed =
                                    rendered?.toString()?.trimEnd('\n')
                                        ?: if (block.kind == BlockKind.TABLE) {
                                            state.document.blockTexts.getOrElse(index) { "" }
                                        } else {
                                            null
                                        },
                            )
                        when (block.kind) {
                            BlockKind.CODE ->
                                CodeBlockEntry(
                                    block = block,
                                    softWrap = style.codeSoftWrap,
                                    baseFontSize = bodyFontSize,
                                    blockPadding = blockPadding,
                                    jumped = index == jumpedBlock || highlight.blockFallback,
                                    highlight = highlight,
                                    positionGeometry = positionGeometry,
                                )
                            BlockKind.TABLE ->
                                TableBlockEntry(
                                    block = block,
                                    baseFontSize = bodyFontSize,
                                    bodyFontFamily = bodyFontFamily,
                                    bodyLineHeight = bodyLineHeight,
                                    blockPadding = blockPadding,
                                    jumped = index == jumpedBlock || highlight.blockFallback,
                                    highlight = highlight,
                                    visibleText = state.document.blockTexts.getOrElse(index) { "" },
                                )
                            else ->
                                ParagraphEntry(
                                    block = block,
                                    baseFontSize = bodyFontSize,
                                    bodyLineHeight = bodyLineHeight,
                                    bodyFontFamily = bodyFontFamily,
                                    blockPadding = blockPadding,
                                    measureMax = measureMax,
                                    bodyText = bodyText,
                                    jumped = index == jumpedBlock || highlight.blockFallback,
                                    highlight = highlight,
                                    rendered = rendered,
                                    // Production Markwon seam (R2-03 F01): the
                                    // session parser's renderer; spans are built
                                    // once per block emission (remembered below).
                                    spanned = { b -> viewModel.parser.renderer.renderSpanned(b) },
                                    onLinkTap = { link ->
                                        viewModel.followLink(
                                            link,
                                            onOpenDocument = onOpenDocument,
                                            onOpenExternal = onOpenExternal,
                                        )
                                    },
                                    positionGeometry = positionGeometry,
                                )
                        }
                        // Local images render in place under their paragraph (T10
                        // FR-6–FR-9): sampled loads for LOCAL, static placeholders
                        // otherwise. Remote images never call the loader, so no
                        // network request can originate here.
                        for (image in block.images) {
                            key(block.index, image.destination) {
                                ImageEntry(
                                    image = image,
                                    documentId = state.document.documentId,
                                    imageLoader = imageLoader,
                                )
                            }
                        }
                    }
                }
            }

            // -- overlay chrome: drawn over the list, never measured by it ----

            // Top layer: the transient notices and the search bar first — the
            // body reserves their measured height as headroom, so they can never
            // hide content (CR-20260930-03) — then the document title, which is
            // drawn only while the reading menu is open (so it never permanently
            // consumes reading space — ISS-019) and is deliberately NOT part of
            // the headroom: the menu toggle must not move the body. Opaque and
            // static.
            Column(
                modifier =
                    Modifier.align(Alignment.TopCenter).fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .onSizeChanged { topChromePx = it.height }
                        .testTag(ReaderChromeTags.TOP_OVERLAY),
            ) {
                Column(
                    modifier =
                        Modifier.fillMaxWidth().onSizeChanged { topInsetPx = it.height },
                ) {
                    state.staleNotice?.let { notice ->
                        Text(
                            notice,
                            style = MaterialTheme.typography.bodySmall,
                            modifier =
                                Modifier.fillMaxWidth().padding(horizontal = pageMarginDp, vertical = 4.dp),
                        )
                        TextButton(onClick = { viewModel.dismissStaleNotice() }) {
                            Text(stringResource(R.string.dismiss))
                        }
                    }
                    state.degradedNotice?.let { notice ->
                        Text(
                            notice,
                            style = MaterialTheme.typography.bodySmall,
                            modifier =
                                Modifier.fillMaxWidth().padding(horizontal = pageMarginDp, vertical = 4.dp),
                        )
                    }
                    // T10 one-shot link/image notice: static text + dismiss, no
                    // animation (E-Ink safe). Never auto-navigates; position is
                    // untouched.
                    state.linkNotice?.let { notice ->
                        Text(
                            notice,
                            style = MaterialTheme.typography.bodySmall,
                            modifier =
                                Modifier.fillMaxWidth().padding(horizontal = pageMarginDp, vertical = 4.dp),
                        )
                        TextButton(onClick = { viewModel.dismissLinkNotice() }) {
                            Text(stringResource(R.string.dismiss))
                        }
                    }
                    if (searchOpen) {
                        SearchBar(
                            search = searchState,
                            input = searchInput,
                            onInput = {
                                searchInput = it
                                viewModel.onSearchQuery(it)
                            },
                            onStep = { forward -> viewModel.stepSearch(forward) },
                            onClose = {
                                searchOpen = false
                                searchInput = ""
                                viewModel.closeSearch()
                            },
                        )
                    }
                }
                // `menuVisible && !searchOpen`: searching closes the menu, and
                // the title must never sit above the reserved headroom band.
                if (menuVisible && !searchOpen) {
                    Text(
                        state.document.title,
                        style = MaterialTheme.typography.headlineSmall,
                        modifier =
                            Modifier.fillMaxWidth().padding(horizontal = pageMarginDp, vertical = 4.dp),
                    )
                }
            }

            // Bottom layer: the anchored static progress footer (REQ-0005
            // FR-8) and, when open, the reading menu. Opaque and static.
            Column(
                modifier =
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .onSizeChanged { bottomChromePx = it.height }
                        .testTag(ReaderChromeTags.BOTTOM_OVERLAY),
            ) {
                Box(
                    modifier =
                        Modifier.fillMaxWidth().height(1.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant),
                )
                // Static progress (REQ-0005 FR-8): block-granular text, no
                // animation, recomputed from the already-observed first visible
                // block.
                val total = state.document.blocks.size
                val first = if (progressIntervalMs > 0L) shownFirstBlock else listState.firstVisibleItemIndex
                val percent = (PositionRestore.progressForBlock(first, total) * 100).toInt()
                ReaderNavigationBar(
                    mode = mode,
                    progress = stringResource(R.string.reader_progress, first + 1, total.coerceAtLeast(1), percent),
                    menuVisible = menuVisible,
                    canPrevious = positionRestored && listState.canScrollBackward,
                    canNext = positionRestored && listState.canScrollForward,
                    onPrevious = { turnPage(false) },
                    onNext = { turnPage(true) },
                    onMenu = { if (positionRestored && !showTapZoneHint) menuVisible = !menuVisible },
                )
                if (menuVisible) {
                    ReaderActionMenu(
                        mode = mode,
                        maxHeight = actionMenuMaxHeight,
                        canChangeMode = settingsViewModel != null,
                        onToc = { tocOpen = true },
                        onSearch = {
                            menuVisible = false
                            searchOpen = true
                        },
                        onRefresh = { viewModel.refresh(state.document.documentId) },
                        onMode = {
                            val other = if (mode == ReadingMode.PAGE_TURN) ReadingMode.CONTINUOUS_SCROLL else ReadingMode.PAGE_TURN
                            settingsViewModel?.setReadingMode(other.stored)
                        },
                        onHelp = {
                            menuVisible = false
                            hintRequested = true
                        },
                        onBack = onBack,
                    )
                }
            }

            // First-run tap-zone onboarding (UX-06, ISS-020): topmost, consumes
            // the tap that dismisses it so no zone fires from under the hint.
            if (showTapZoneHint) {
                TapZoneHintOverlay(
                    isPageTurn = mode == ReadingMode.PAGE_TURN,
                    onDismiss = {
                        hintRequested = false
                        hintDismissed = true
                        settingsViewModel?.dismissTapZonesHint()
                    },
                )
            }
        }
    }
}

/**
 * Semantics tags for the reader's floating chrome layers (UX-06).
 *
 * The page regressions for the overlay findings
 * (CR-20260930-01/02/03) have to compare a block's real bounds against the
 * chrome's edges, and no text node exposes the overlay rectangles. Tagging the
 * two layers keeps those measurements exact instead of inferring the chrome
 * from paddings. Semantics only — no visual or behavioural effect.
 */
object ReaderChromeTags {
    /** The top overlay layer (notices, search bar, menu title). */
    const val TOP_OVERLAY = "reader-top-overlay"

    /** The bottom overlay layer (progress footer and reading menu). */
    const val BOTTOM_OVERLAY = "reader-bottom-overlay"
}

/**
 * First-run tap-zone onboarding (UX-06, ISS-020): a static, opaque
 * illustration of the three tap zones that the reader actually uses
 * ([TapZones], equal thirds of the reader width, including page margins) with one dismissal.
 *
 * It is shown at most once per install — the caller decides via the persisted
 * `tapZonesHintSeen` flag and hides it immediately on dismissal. The whole
 * surface dismisses and consumes the tap, so the first touch can never turn a
 * page from under the hint. No animation, no ripple, no transparency (E-Ink
 * rules).
 */
@Composable
private fun TapZoneHintOverlay(
    isPageTurn: Boolean,
    onDismiss: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier =
            Modifier.fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .clickable(interactionSource = interaction, indication = null, onClick = onDismiss),
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Text(
                stringResource(R.string.reader_hint_title),
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(
                    if (isPageTurn) {
                        R.string.reader_hint_page_turn
                    } else {
                        R.string.reader_hint_continuous
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                HintZone(stringResource(R.string.reader_hint_prev), Modifier.weight(1f))
                HintZone(stringResource(R.string.reader_hint_menu), Modifier.weight(1f))
                HintZone(stringResource(R.string.reader_hint_next), Modifier.weight(1f))
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text(stringResource(R.string.reader_hint_got_it))
            }
        }
    }
}

/** One outlined tap-zone column of [TapZoneHintOverlay] (equal thirds). */
@Composable
private fun HintZone(
    label: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier.fillMaxHeight()
                .border(1.dp, MaterialTheme.colorScheme.outline)
                .padding(8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Body-text font mapping (T09/REQ-0007 FR-2): the `ReaderStyle` whitelist only.
 * `"system"`/unknown → null (platform default); monospace is never a body
 * family (code entries use [androidx.compose.ui.text.font.FontFamily.Monospace]).
 */
@Composable
private fun fontFamilyFor(family: String): androidx.compose.ui.text.font.FontFamily? =
    when (family) {
        "serif" -> androidx.compose.ui.text.font.FontFamily.Serif
        "sans-serif", "system" -> androidx.compose.ui.text.font.FontFamily.SansSerif
        else -> null
    }

/**
 * Code-block entry (T09/REQ-0007 FR-5–FR-7): monospace body, language label,
 * copy button, soft-wrap switch. Soft-wrap ON (E-Ink default) wraps in place;
 * OFF puts the body in a horizontal-scroll container so long lines stay
 * reachable (AC-4), with instant (non-smooth) scrolling per the E-Ink rule.
 * No syntax highlighting (Non-goal). Copy feedback is static text, no
 * animation (E-Ink rule). Copy taps are consumed here and never reach the
 * tap-zone detector (T08 arbitration: content gestures win).
 */
@Composable
private fun CodeBlockEntry(
    block: io.leaflet.markdown.RenderBlock,
    softWrap: Boolean,
    baseFontSize: androidx.compose.ui.unit.TextUnit,
    blockPadding: Dp,
    jumped: Boolean,
    highlight: io.leaflet.reader.BlockHighlight,
    positionGeometry: BlockPositionGeometry? = null,
) {
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    var copied by remember(block) { mutableStateOf(false) }
    var copyCount by remember(block) { mutableIntStateOf(0) }
    LaunchedEffect(block, copyCount) {
        if (copyCount > 0) {
            delay(2000)
            copied = false
        }
    }
    val label = block.languageLabel.ifEmpty { stringResource(R.string.reader_code_language_unknown) }
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = blockPadding)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(label, style = MaterialTheme.typography.labelSmall)
            Spacer(modifier = Modifier.weight(1f))
            TextButton(
                onClick = {
                    clipboard.setText(androidx.compose.ui.text.AnnotatedString(block.codeForCopy()))
                    copied = true
                    copyCount++
                },
            ) {
                // Reuse the action row so feedback never shifts the code or following blocks.
                Text(
                    stringResource(if (copied) R.string.reader_code_copied else R.string.reader_code_copy),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        val bodyText = block.codeForCopy().ifBlank { " " }
        val body =
            @Composable {
                Text(
                    text = searchAnnotated(bodyText, highlight),
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    fontSize = baseFontSize,
                    fontWeight = if (jumped) FontWeight.Bold else null,
                    softWrap = softWrap,
                    onTextLayout = { result ->
                        val lines = result.blockTextLines()
                        positionGeometry?.textLines(block.index) { lines }
                    },
                    modifier =
                        Modifier.padding(vertical = 4.dp).onGloballyPositioned {
                            positionGeometry?.textTop(block.index, it.positionInRoot().y)
                        },
                )
            }
        if (softWrap) {
            body()
        } else {
            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) { body() }
        }
    }
}

/**
 * Table entry (T09/REQ-0007 FR-8): NORMAL mode only — the table sits in a
 * horizontal-scroll container so every column is reachable on narrow screens
 * (AC-5). Inner horizontal gestures are consumed by the scroller and never
 * trigger a page turn (T08 arbitration; REQ-0007 UX §47). WRAP/CARD are M2+
 * follow-ups with no UI switch (FR-9); this entry never branches on them.
 */
@Composable
private fun TableBlockEntry(
    block: io.leaflet.markdown.RenderBlock,
    baseFontSize: androidx.compose.ui.unit.TextUnit,
    bodyFontFamily: androidx.compose.ui.text.font.FontFamily?,
    bodyLineHeight: androidx.compose.ui.unit.TextUnit,
    blockPadding: Dp,
    jumped: Boolean,
    highlight: io.leaflet.reader.BlockHighlight,
    /** The block's visible text (`blockTexts`): the search coordinate base. */
    visibleText: String,
) {
    // R2-03 native table: the T10 table slice is a pipe-delimited grid
    // (header + `---` delimiter + rows); render it as a real cell grid so
    // columns are individually addressable. The grid sits in the NORMAL
    // horizontal-scroll container (T09: narrow screens reach every column;
    // inner gestures never trigger page turns). Delimiter row is structural
    // (never rendered). Cells reuse cellAnnotated so inline markers inside
    // cells render with body styles.
    val rows = EntryBindings.tableCells(block)
    // R2 DEF-003: the search coordinates live in the block's OWN visible
    // string (`blockTexts`), whose grid is exactly rows split on `\n` and
    // cells split on `\t`. Handing each cell its span in that string makes the
    // highlight positional — two cells with identical text no longer both
    // claim the current match. The spans are only used when they line up with
    // the rendered grid; otherwise null keeps the documented substring
    // fallback.
    val blockVisible = visibleText
    val cellSpans =
        EntryBindings.tableCellSpans(blockVisible).takeIf { grid ->
            grid.size == rows.size && grid.indices.all { r -> grid[r].size == rows[r].size }
        }
    // CR-20261001-01: validate the grid before it is measured as fixed
    // constraints. Cells do not wrap (`softWrap = false`), so a single long cell
    // asks for its full unwrapped line width — and Compose cannot represent an
    // arbitrary dimension (262142 max; `Constraints.fixed(262143, …)` throws
    // mid-layout). `chars × 2 em + cell padding` is a conservative upper bound on
    // that line: CJK advances ~1 em, wide emoji/fallbacks up to ~1.6 em, and the
    // bold/letter-spacing additions stay far inside the second em. A block above
    // the bound renders as its own scrollable text instead, where every character
    // stays reachable.
    val tableDensity = LocalDensity.current
    val glyphBudgetPx = with(tableDensity) { baseFontSize.toPx() }
    val cellPaddingPx = with(tableDensity) { 16.dp.toPx() }
    val widestCellChars = rows.maxOf { row -> row.maxOfOrNull { it.length } ?: 0 }
    val representableGrid = isTableCellWidthRepresentable(widestCellChars, glyphBudgetPx, cellPaddingPx)
    if (rows.isEmpty() || !representableGrid) {
        // Bounded fallback, shared with an unparseable table: the block's own text
        // in the normal horizontal-scroll container (`softWrap = false`), so no
        // character of a pathological table is dropped or clipped.
        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            Text(
                text = searchAnnotated(block.text.ifBlank { " " }, highlight),
                fontFamily = bodyFontFamily,
                fontSize = baseFontSize,
                lineHeight = bodyLineHeight,
                fontWeight = if (jumped) FontWeight.Bold else null,
                softWrap = false,
                modifier = Modifier.padding(vertical = blockPadding),
            )
        }
        return
    }
    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = blockPadding)) {
        TableGrid(rowCount = rows.size, columnCount = rows.maxOf { it.size }) { rowIndex, cellIndex ->
            val cell = rows[rowIndex].getOrElse(cellIndex) { "" }
            val stripped = InlineStyles.strip(cell)
            val cellStyles = InlineStyles.of(cell)
            val cellHighlight =
                cellSearchHighlight(stripped, highlight, blockVisible, cellSpans, rowIndex, cellIndex)
            Text(
                text = cellAnnotated(stripped, cellStyles, cellHighlight),
                fontFamily = bodyFontFamily,
                fontSize = baseFontSize,
                lineHeight = bodyLineHeight,
                fontWeight = if (jumped || rowIndex == 0) FontWeight.Bold else null,
                softWrap = false,
            )
        }
    }
}

/**
 * Cell-local search highlight (R2-06, REQ-0008 AC-3): pure mapping lives in
 * [EntryBindings.cellSearchHighlight] (JVM-tested); this is the thin
 * composable-adjacent delegate feeding it the block's visible string.
 */
private fun cellSearchHighlight(
    strippedCell: String,
    highlight: io.leaflet.reader.BlockHighlight,
    blockVisible: String,
    cellSpans: List<List<IntRange>>?,
    rowIndex: Int = 0,
    cellIndex: Int = 0,
): io.leaflet.reader.BlockHighlight? =
    EntryBindings.cellSearchHighlight(
        strippedCell = strippedCell,
        highlight = highlight,
        blockVisible = blockVisible,
        cellSpan = cellSpans?.getOrNull(rowIndex)?.getOrNull(cellIndex),
    )

/**
 * Cell text + cell-local spans (R2-03) + in-cell search highlighting (R2-06,
 * REQ-0008 AC-3): the stripped display string with its inline styles, then
 * the cell-local search spans ([cellSearchHighlight] output) applied on top.
 * Search wins over inline colors on overlap (same convention as
 * [cellAnnotated]); both are static spans, no animation (E-Ink safe).
 */
@Composable
private fun cellAnnotated(
    stripped: String,
    styles: List<InlineStyle>,
    search: io.leaflet.reader.BlockHighlight? = null,
): androidx.compose.ui.text.AnnotatedString {
    if (styles.isEmpty() && (search == null || (search.others.isEmpty() && search.current == null))) {
        return androidx.compose.ui.text.AnnotatedString(stripped.ifBlank { " " })
    }
    val display = stripped.ifBlank { " " }
    return androidx.compose.ui.text.buildAnnotatedString {
        append(display)
        for (s in styles) {
            val from = s.start.coerceIn(0, display.length)
            val to = s.end.coerceIn(0, display.length)
            if (from >= to) continue
            when (s.op) {
                InlineOp.BOLD ->
                    addStyle(
                        androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold),
                        from,
                        to,
                    )
                InlineOp.ITALIC ->
                    addStyle(
                        androidx.compose.ui.text.SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic),
                        from,
                        to,
                    )
                InlineOp.STRIKE ->
                    addStyle(
                        androidx.compose.ui.text.SpanStyle(textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough),
                        from,
                        to,
                    )
                InlineOp.CODE ->
                    addStyle(
                        androidx.compose.ui.text.SpanStyle(
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            background = androidx.compose.ui.graphics.Color(0xFFEEEEEE),
                        ),
                        from,
                        to,
                    )
            }
        }
        if (search != null) {
            val searchApplied = EntryBindings.annotated(display, search)
            for (span in searchApplied.spanStyles) {
                addStyle(span.item, span.start, span.end)
            }
        }
    }
}

/**
 * TOC drawer (REQ-0006): level-indented entries from the same ParsedDocument
 * the renderer binds (同源, no second parse). No open/close animation —
 * the panel swaps in instantly (E-Ink rule). Empty TOC shows the
 * "无章节标题" placeholder, never fake entries (FR-6). The current chapter
 * (nearest heading at/above the first visible block) renders bold — static,
 * no animation.
 */
@Composable
private fun TocDrawer(
    document: io.leaflet.markdown.ParsedDocument,
    currentBlock: Int,
    onJump: (String) -> Unit,
    onClose: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.width(280.dp).fillMaxHeight().padding(16.dp)) {
            Text(stringResource(R.string.reader_toc_title), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            if (document.toc.isEmpty()) {
                Text(
                    stringResource(R.string.reader_toc_empty),
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                val current = TocNavigation.currentChapter(document, currentBlock)
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(document.toc, key = { it.anchor }) { entry ->
                        val indent = (entry.level - 1).coerceIn(0, 5)
                        Text(
                            entry.text.ifBlank { " " },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight =
                                if (current != null && entry.text.isNotBlank() && entry.text == current) {
                                    FontWeight.Bold
                                } else {
                                    null
                                },
                            modifier =
                                Modifier.fillMaxWidth()
                                    .clickable { onJump(entry.anchor) }
                                    .padding(
                                        start = (12 * indent).dp,
                                        top = 6.dp,
                                        bottom = 6.dp,
                                        end = 8.dp,
                                    ),
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onClose) { Text(stringResource(R.string.go_back)) }
        }
        // Scrim: tap outside closes. No fade (E-Ink rule).
        Box(
            modifier =
                Modifier.weight(1f).fillMaxHeight()
                    .clickable { onClose },
        )
    }
}

@Composable
private fun ReaderFailed(
    reason: ReaderFailure,
    message: String,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onReauthorize: () -> Unit,
    onRemoveDeadEntry: (() -> Unit)?,
) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text(stringResource(R.string.reader_failed_title), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(failureText(reason, message), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(16.dp))
        when (reason) {
            ReaderFailure.AUTHORIZATION_LOST -> {
                Button(onClick = onReauthorize) {
                    Text(stringResource(R.string.reader_reauthorize))
                }
                Spacer(Modifier.height(8.dp))
            }
            ReaderFailure.FILE_MISSING -> {
                if (onRemoveDeadEntry != null) {
                    Button(onClick = onRemoveDeadEntry) {
                        Text(stringResource(R.string.reader_remove_dead_entry))
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
            ReaderFailure.PARSE_FAILED, ReaderFailure.CANCELLED -> Unit
        }
        Button(onClick = onRetry) { Text(stringResource(R.string.scan_retry)) }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onBack) { Text(stringResource(R.string.go_back)) }
    }
}

@Composable
private fun failureText(
    reason: ReaderFailure,
    message: String,
): String =
    when (reason) {
        ReaderFailure.AUTHORIZATION_LOST -> stringResource(R.string.reader_failed_auth_lost)
        ReaderFailure.FILE_MISSING -> stringResource(R.string.reader_failed_gone)
        else -> if (message.isNotBlank()) message else stringResource(R.string.reader_failed_parse)
    }

/**
 * Paragraph entry with tappable in-text links (T10/REQ-0009).
 *
 * Links render as appended tappable rows under the paragraph text (same
 * block scope, so taps stay unambiguous on E-Ink): tapping consumes the
 * gesture here (T08 `LINK_TAP` wins) and never reaches the page-turn zones.
 * External taps go straight to the OS with no confirm step (REQ-0009 UX).
 */
@Composable
private fun ParagraphEntry(
    block: io.leaflet.markdown.RenderBlock,
    baseFontSize: androidx.compose.ui.unit.TextUnit,
    bodyLineHeight: androidx.compose.ui.unit.TextUnit,
    bodyFontFamily: androidx.compose.ui.text.font.FontFamily?,
    blockPadding: Dp,
    measureMax: Dp,
    bodyText: BodyTextPolicy,
    jumped: Boolean,
    highlight: io.leaflet.reader.BlockHighlight,
    // R2 DEF-003: the rendered (marker-free) text this entry shows, trimmed
    // the same way the highlight mapping measured it. Null when the block is
    // literal (TXT / plain fallback) — those never go through Markwon.
    rendered: CharSequence?,
    // R2-03: null in previews/tests (plain Compose Text fallback); the
    // production route always passes the session parser's renderer, so
    // markers NEVER show on device (F01).
    spanned: ((io.leaflet.markdown.RenderBlock) -> CharSequence)?,
    onLinkTap: (io.leaflet.markdown.BlockLink) -> Unit,
    positionGeometry: BlockPositionGeometry? = null,
) {
    // `widthIn` before `fillMaxWidth`: the paragraph fills the item up to the measure cap
    // (REQ-0007: bounded line length on wide panels) and the item centres it.
    Column(modifier = Modifier.widthIn(max = measureMax).fillMaxWidth().padding(vertical = blockPadding)) {
        // R2 DEF-001: literal blocks (TXT chunks, PLAIN_FALLBACK degradation)
        // are shown verbatim — `# literal`, `**literal**`, `[label](target)`
        // stay visible characters, never Markdown syntax.
        if (rendered != null && spanned != null && !block.literal) {
            SpannedTextView(
                block = block,
                rendered = rendered,
                highlight = highlight,
                jumped = jumped,
                baseFontSize = baseFontSize,
                bodyLineHeight = bodyLineHeight,
                bodyFontFamily = bodyFontFamily,
                bodyText = bodyText,
                positionGeometry = positionGeometry,
            )
        } else {
            Text(
                text = searchAnnotated(block.text.ifBlank { " " }, highlight),
                style = MaterialTheme.typography.bodyMedium,
                fontSize = baseFontSize,
                lineHeight = bodyLineHeight,
                fontFamily = bodyFontFamily,
                fontWeight = if (jumped) FontWeight.Bold else null,
                onTextLayout = { result ->
                    val lines = result.blockTextLines()
                    positionGeometry?.textLines(block.index) { lines }
                },
                modifier =
                    Modifier.onGloballyPositioned {
                        positionGeometry?.textTop(block.index, it.positionInRoot().y)
                    },
            )
        }
        for (link in block.links) {
            if (link.kind == io.leaflet.markdown.DocLinkKind.EMPTY) continue
            TextButton(onClick = { onLinkTap(link) }) {
                Text(linkLabel(link), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/**
 * Native Markwon body (R2-03 F01): renders the block source slice to a
 * styled `Spanned` and binds it to an `AndroidView` `TextView`. Markers are
 * consumed by Markwon — they never reach the screen. Search/current/jump
 * highlights are applied post-render as static spans (E-Ink safe, no
 * animation).
 *
 * Typographic intent (size/family/line-height) flows from the T09 style;
 * spans from Markwon (bold/italic/strike/code/table) compose with them.
 *
 * R2 DEF-002: the SAME typography is applied in `update`, not only in
 * `factory` — a recycled/kept `TextView` used to keep the size, typeface and
 * line spacing it was created with, so two entries of one document could
 * disagree after a settings change. Line spacing is set in PIXELS
 * (`setLineSpacing` takes px, not sp), while the size stays in SP.
 *
 * R2 DEF-003: [rendered] is the marker-free text Markwon produced (what the
 * reader sees); highlight ranges were mapped against EXACTLY this string, so
 * spans land on the matched word.
 */
@Composable
private fun SpannedTextView(
    block: io.leaflet.markdown.RenderBlock,
    rendered: CharSequence,
    highlight: io.leaflet.reader.BlockHighlight,
    jumped: Boolean,
    baseFontSize: androidx.compose.ui.unit.TextUnit,
    bodyLineHeight: androidx.compose.ui.unit.TextUnit,
    bodyFontFamily: androidx.compose.ui.text.font.FontFamily?,
    bodyText: BodyTextPolicy,
    positionGeometry: BlockPositionGeometry? = null,
) {
    val base = remember(block.source, block.text) { rendered }
    val withMarks =
        remember(base, highlight, jumped) {
            applyStaticSpans(base, highlight, jumped)
        }
    val typeface = androidTypeface(bodyFontFamily)
    androidx.compose.ui.viewinterop.AndroidView(
        factory = { context ->
            ReaderTextView(context).apply {
                applyTypography(this, baseFontSize, bodyLineHeight, typeface, bodyText)
            }
        },
        update = { view ->
            // DEF-002: style first, then text — both paths run on every bind.
            applyTypography(view, baseFontSize, bodyLineHeight, typeface, bodyText)
            view.text = withMarks
            var cachedLayout: android.text.Layout? = null
            var cachedLines: BlockTextLines? = null
            positionGeometry?.textLines(block.index) {
                val layout = view.layout
                if (layout != null && layout !== cachedLayout) {
                    cachedLayout = layout
                    cachedLines =
                        BlockTextLines(
                            view.text.length,
                            IntArray(layout.lineCount) { layout.getLineStart(it) },
                            FloatArray(layout.lineCount) { layout.getLineTop(it).toFloat() + view.totalPaddingTop },
                        )
                }
                cachedLines
            }
        },
        modifier =
            Modifier.padding(vertical = 4.dp).onGloballyPositioned {
                positionGeometry?.textTop(block.index, it.positionInRoot().y)
            },
    )
}

private fun androidx.compose.ui.text.TextLayoutResult.blockTextLines(): BlockTextLines =
    BlockTextLines(
        layoutInput.text.length,
        IntArray(lineCount) { getLineStart(it) },
        FloatArray(lineCount) { getLineTop(it) },
    )

/**
 * Display-dependent text policy for the Markwon body (REQ-0007, REQ-0004 FR-1). The values
 * come from the display profile's reading layout, so this class carries no mode of its own.
 *
 * @property textColor body colour from the theme; the Material Light activity theme's own
 *   text colour is translucent black, which is not the pure black E-Ink needs.
 * @property linkColor link colour, or null to keep the platform's own link colour.
 * @property exactLineHeight true to set the real baseline distance instead of adding to the natural height.
 * @property justify inter-word justification of flowing paragraphs.
 * @property hyphenate automatic hyphenation with high-quality line breaking.
 */
private data class BodyTextPolicy(
    val textColor: Int,
    val linkColor: Int?,
    val exactLineHeight: Boolean,
    val justify: Boolean,
    val hyphenate: Boolean,
)

/**
 * A [android.widget.TextView] that remembers the platform's own wrapping and link-colour
 * defaults, so a mode switch from E-Ink back to Standard restores exactly what the
 * platform gave it (Standard stays byte-for-byte unchanged) instead of assuming them.
 */
private class ReaderTextView(context: android.content.Context) : android.widget.TextView(context) {
    val platformBreakStrategy: Int = breakStrategy
    val platformHyphenationFrequency: Int = hyphenationFrequency
    val platformJustificationMode: Int = justificationMode
    val platformLinkTextColors: android.content.res.ColorStateList = linkTextColors
}

/**
 * One place that applies the T09 typography to an existing [ReaderTextView]
 * (R2 DEF-002): factory and update must agree, otherwise a view created
 * before a settings change keeps the old size/family/spacing forever. The
 * same goes for the display policy: every setter below runs in both directions.
 */
private fun applyTypography(
    view: ReaderTextView,
    baseFontSize: androidx.compose.ui.unit.TextUnit,
    bodyLineHeight: androidx.compose.ui.unit.TextUnit,
    typeface: android.graphics.Typeface?,
    policy: BodyTextPolicy,
) {
    view.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, baseFontSize.value)
    view.typeface = typeface
    view.setTextColor(policy.textColor)
    if (policy.linkColor != null) {
        view.setLinkTextColor(policy.linkColor)
    } else {
        view.setLinkTextColor(view.platformLinkTextColors)
    }
    // Justified, hyphenated text needs high-quality breaking, otherwise the greedy breaker
    // leaves rivers; the setters relayout even for an unchanged value, so guard them.
    val justification =
        if (policy.justify) android.text.Layout.JUSTIFICATION_MODE_INTER_WORD else view.platformJustificationMode
    val hyphenation =
        if (policy.hyphenate) android.text.Layout.HYPHENATION_FREQUENCY_NORMAL else view.platformHyphenationFrequency
    val breaking =
        if (policy.hyphenate) android.text.Layout.BREAK_STRATEGY_HIGH_QUALITY else view.platformBreakStrategy
    if (view.justificationMode != justification) view.justificationMode = justification
    if (view.hyphenationFrequency != hyphenation) view.hyphenationFrequency = hyphenation
    if (view.breakStrategy != breaking) view.breakStrategy = breaking
    if (policy.exactLineHeight) {
        // Real baseline-to-baseline distance, like the Compose Text path (API 28+; minSdk is 29).
        // Size and typeface are set above because the platform derives the extra from the font height.
        val linePx =
            android.util.TypedValue.applyDimension(
                android.util.TypedValue.COMPLEX_UNIT_SP,
                bodyLineHeight.value,
                view.resources.displayMetrics,
            )
        view.setLineHeight(linePx.toInt())
        return
    }
    // setLineSpacing takes ADDITIONAL spacing and a multiplier in PIXELS;
    // the Compose Text used the same total height expressed in sp. Convert
    // via the view's own font scale so both paths agree at any density.
    val extraSp = (bodyLineHeight.value - baseFontSize.value).coerceAtLeast(0f)
    val extraPx =
        android.util.TypedValue.applyDimension(
            android.util.TypedValue.COMPLEX_UNIT_SP,
            extraSp,
            view.resources.displayMetrics,
        )
    view.setLineSpacing(extraPx, 1f)
}

private fun androidTypeface(family: androidx.compose.ui.text.font.FontFamily?): android.graphics.Typeface =
    when (family) {
        androidx.compose.ui.text.font.FontFamily.Serif -> android.graphics.Typeface.SERIF
        androidx.compose.ui.text.font.FontFamily.SansSerif -> android.graphics.Typeface.SANS_SERIF
        androidx.compose.ui.text.font.FontFamily.Monospace -> android.graphics.Typeface.MONOSPACE
        else -> android.graphics.Typeface.DEFAULT
    }

/** Applies search + jump highlights onto a rendered Spanned (static spans). */
private fun applyStaticSpans(
    base: CharSequence,
    highlight: io.leaflet.reader.BlockHighlight,
    jumped: Boolean,
): CharSequence {
    if (!jumped && highlight.others.isEmpty() && highlight.current == null) return base
    // ALWAYS work on a copy: `base` is the cached Markwon Spanned shared with
    // the entry, so setSpan-ing it directly would leave search spans behind
    // after the query was cleared (and could accumulate duplicates).
    val out = android.text.SpannableStringBuilder(base)
    if (jumped) {
        out.setSpan(
            android.text.style.StyleSpan(android.graphics.Typeface.BOLD),
            0,
            out.length,
            android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
    }
    for (range in highlight.others) {
        val from = range.first.coerceIn(0, out.length)
        val to = (range.last + 1).coerceIn(0, out.length)
        if (from < to) {
            out.setSpan(
                android.text.style.BackgroundColorSpan(0xFFFFFF00.toInt()),
                from,
                to,
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }
    }
    highlight.current?.let { range ->
        val from = range.first.coerceIn(0, out.length)
        val to = (range.last + 1).coerceIn(0, out.length)
        if (from < to) {
            out.setSpan(
                android.text.style.BackgroundColorSpan(0xFF000000.toInt()),
                from,
                to,
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
            out.setSpan(
                android.text.style.ForegroundColorSpan(0xFFFFFFFF.toInt()),
                from,
                to,
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }
    }
    return out
}

private fun linkLabel(link: io.leaflet.markdown.BlockLink): String {
    val label = link.label.ifBlank { link.destination.ifBlank { "#" + (link.fragment.orEmpty()) } }
    return when (link.kind) {
        io.leaflet.markdown.DocLinkKind.EXTERNAL -> "↗ $label"
        else -> label
    }
}

/**
 * In-text image entry (T10/REQ-0009 FR-6–FR-9): static gray placeholder box
 * with alt + size caption, no animation (E-Ink rule). LOCAL images decode
 * through [imageLoader] (viewport-sampled, cancellable via the composition
 * scope); REMOTE and other kinds placeholder without loading, so no network
 * request can originate here. Cancellation: leaving the composition cancels
 * the in-flight load; the bitmap is dropped on dispose.
 */
@Composable
private fun ImageEntry(
    image: io.leaflet.markdown.BlockImage,
    documentId: DocumentId,
    imageLoader: ImageLoader?,
) {
    val caption = imageCaption(image)
    val density = LocalDensity.current
    // Static placeholder frame (E-Ink safe): gray box + alt + size text.
    if (image.kind != io.leaflet.markdown.ImageKind.LOCAL || imageLoader == null) {
        StaticImagePlaceholder(caption = caption)
        return
    }
    var widthPx by remember(image.destination) { mutableStateOf(0) }
    var loaded by remember(image.destination) { mutableStateOf<DeviceImage?>(null) }
    LaunchedEffect(image.destination, widthPx) {
        if (widthPx <= 0) return@LaunchedEffect
        try {
            loaded = imageLoader.load(documentId, image, widthPx)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            loaded = DeviceImage.Failed(ImageFailure.BROKEN)
        }
    }
    DisposableEffect(image.destination) {
        onDispose {
            (loaded as? DeviceImage.Ready)?.bitmap?.recycle()
        }
    }
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .onSizeChanged { widthPx = it.width },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (val result = loaded) {
            // R2-03: a decoded bitmap actually draws (Image composable),
            // aspect-preserved, capped height so one image never fills the
            // viewport. The old code called StaticImagePlaceholder here too,
            // which is why local images "loaded" but never displayed (F04).
            is DeviceImage.Ready -> {
                val bitmap = result.bitmap
                val dimensions =
                    boundedImageDimensions(
                        bitmap.width,
                        bitmap.height,
                        widthPx.coerceAtLeast(1),
                        with(density) { 320.dp.roundToPx() },
                    )
                androidx.compose.foundation.Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = image.alt.ifBlank { image.destination },
                    modifier =
                        with(density) {
                            Modifier.size(dimensions.widthPx.toDp(), dimensions.heightPx.toDp())
                        },
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                )
                Text(
                    "${result.widthPx}×${result.heightPx}",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            is DeviceImage.Failed -> {
                val reason =
                    if (result.reason == ImageFailure.MISSING) {
                        "图片缺失：${image.alt.ifBlank { image.destination }}"
                    } else {
                        "图片损坏：${image.alt.ifBlank { image.destination }}"
                    }
                StaticImagePlaceholder(caption = reason)
            }
            null -> StaticImagePlaceholder(caption = caption)
        }
    }
}

/**
 * Static image placeholder (T10/REQ-0009 FR-6–FR-9): gray box + caption, no
 * animation (E-Ink rule). Used for REMOTE (never loaded), missing/broken, and
 * pre-load LOCAL states.
 */
@Composable
private fun StaticImagePlaceholder(caption: String) {
    androidx.compose.foundation.layout.Box(
        modifier =
            Modifier.fillMaxWidth()
                .heightIn(min = 64.dp)
                .background(androidx.compose.ui.graphics.Color(0xFFEEEEEE))
                .padding(12.dp),
    ) {
        Text(caption, style = MaterialTheme.typography.bodySmall)
    }
}

private fun imageCaption(image: io.leaflet.markdown.BlockImage): String {
    val alt = image.alt.ifBlank { image.destination.ifBlank { "图片" } }
    return when (image.kind) {
        io.leaflet.markdown.ImageKind.LOCAL -> "图片：$alt（加载中）"
        io.leaflet.markdown.ImageKind.REMOTE -> "远程图片不自动加载：$alt"
        io.leaflet.markdown.ImageKind.UNSUPPORTED_FORMAT -> "不支持的图片格式：$alt"
        io.leaflet.markdown.ImageKind.UNSUPPORTED_SCHEME -> "不支持的图片链接：$alt"
        io.leaflet.markdown.ImageKind.UNREACHABLE -> "图片不可达：$alt"
        io.leaflet.markdown.ImageKind.EMPTY -> "空图片：$alt"
    }
}

private fun openExternal(
    url: String,
    viewModel: ReaderViewModel,
) {
    // Production handoff hook: tests inject [externalOpener]; the Activity
    // wiring may later replace this with a direct ACTION_VIEW launch.
    // Unknown schemes never reach here (classified UNSUPPORTED_SCHEME), and
    // no call fires without the user's tap (REQ-0009 FR-10).
    try {
        externalOpener?.invoke(url) ?: viewModel.reportNoExternalApp()
    } catch (_: Exception) {
        viewModel.reportNoExternalApp()
    }
}

/**
 * Test/app seam for the external-URL handoff (production installs an
 * ACTION_VIEW launcher here; unit tests assert the URL without an intent).
 */
internal var externalOpener: ((String) -> Unit)? = null

/**
 * In-document search bar (T11, REQ-0008 UX/FR-7): query input, match count
 * (`n/m`), previous/next buttons, close button. Blank queries show no count
 * and never search; zero-hit queries show the static "无匹配" notice
 * (E-Ink safe: no animation, no scroll on empty).
 */
@Composable
private fun SearchBar(
    search: io.leaflet.reader.SearchUiState,
    input: String,
    onInput: (String) -> Unit,
    onStep: (Boolean) -> Unit,
    onClose: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth()) {
            TextField(
                value = input,
                onValueChange = onInput,
                placeholder = { Text(stringResource(R.string.reader_search_hint)) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onClose) { Text(stringResource(R.string.reader_search_close)) }
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            val status =
                when {
                    search.query.isBlank() -> ""
                    search.searching -> stringResource(R.string.reader_search_searching)
                    search.total == 0 -> stringResource(R.string.reader_search_empty)
                    search.capped ->
                        stringResource(
                            R.string.reader_search_capped,
                            search.currentIndex + 1,
                            io.leaflet.reader.DocumentSearch.MAX_SEARCH_MATCHES,
                        )
                    else -> stringResource(R.string.reader_search_count, search.currentIndex + 1, search.total)
                }
            // R2-07: the count/empty/searching status MUST be composed
            // (REQ-0008 FR-7) — it was computed but never emitted, so the
            // bar showed prev/next with a blank left side on device.
            Text(status, style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.weight(1f))
            TextButton(onClick = { onStep(false) }) { Text(stringResource(R.string.reader_search_prev)) }
            TextButton(onClick = { onStep(true) }) { Text(stringResource(R.string.reader_search_next)) }
        }
    }
}

/**
 * Visible→rendered highlight for one bound block (T11, REQ-0008 FR-2/FR-3).
 * Thin delegate over [EntryBindings] (R2-03: the mapping is page-tested
 * without composition); [displayed] is the exact string the entry renders
 * (R2 DEF-003), or null when the entry's string is the visible text itself.
 */
private fun searchHighlightFor(
    document: io.leaflet.markdown.ParsedDocument,
    blockIndex: Int,
    block: io.leaflet.markdown.RenderBlock,
    search: io.leaflet.reader.SearchUiState,
    displayed: String?,
): io.leaflet.reader.BlockHighlight =
    EntryBindings.highlightFor(
        document = document,
        blockIndex = blockIndex,
        displayed = displayed ?: EntryBindings.displayedText(block),
        search = search,
    )

/** Static search-highlight application (delegate; see [EntryBindings.annotated]). */
private fun searchAnnotated(
    displayed: String,
    highlight: io.leaflet.reader.BlockHighlight,
): androidx.compose.ui.text.AnnotatedString = EntryBindings.annotated(displayed, highlight)
