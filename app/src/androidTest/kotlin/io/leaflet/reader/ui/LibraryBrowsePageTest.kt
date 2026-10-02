package io.leaflet.reader.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.leaflet.model.DisplayMode
import io.leaflet.model.Document
import io.leaflet.model.Library
import io.leaflet.model.LibraryId
import io.leaflet.reader.R
import io.leaflet.reader.ui.library.BrowseTags
import io.leaflet.reader.ui.library.LibraryBrowseScreen
import io.leaflet.reader.ui.library.LibraryListSource
import io.leaflet.reader.ui.library.LibraryViewModel
import io.leaflet.reader.ui.theme.LeafletTheme
import io.leaflet.storage.ScanState
import io.leaflet.storage.db.DirectoryEntity
import io.leaflet.storage.db.DocumentEntity
import io.leaflet.storage.db.LeafletDatabase
import io.leaflet.storage.repo.LibraryRepositoryImpl
import io.leaflet.storage.saf.ContentAccess
import io.leaflet.storage.scan.LibraryScanner
import io.leaflet.storage.scan.ScanProgress
import io.leaflet.storage.scan.ScanProgressState
import io.leaflet.storage.scan.TreeEntry
import io.leaflet.storage.scan.TreeLister
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.io.InputStream
import kotlin.math.abs

private const val TREE_URI = "content://leaflet.browse.tree"
private const val FILE_SIZE = 12L
private const val FILE_MTIME = 42L
private const val TIMEOUT_MS = 20_000L

/** Root children of the fake SAF tree, in the order the scanner reports them. */
private val ROOT_FILES: List<String> = (1..12).map { "doc%02d.md".format(it) } + listOf("guide.txt")

/** Long Chinese directory/file names (200% font-scale coverage). */
private const val LONG_DIR = "很长的中文文件夹名称用于验证百分之二百字号下的换行"
private val LONG_FILE = "$LONG_DIR/一个同样很长的中文文档名称.md"

/** Ten nested levels, for narrow-screen breadcrumb reachability (AC-3). */
private val DEEP_PATH: String = (1..10).joinToString("/") { "L$it" }
private val DEEP_DIRECTORIES: List<String> = (1..10).map { level -> (1..level).joinToString("/") { "L$it" } }
private val DEEP_FILE: String = "$DEEP_PATH/note.md"

private val DIRECTORIES: List<String> = listOf("Notes", "Notes/2026", "Empty", LONG_DIR) + DEEP_DIRECTORIES
private val FILES: List<String> = ROOT_FILES + listOf("Notes/2026/todo.txt", LONG_FILE, DEEP_FILE)

private fun parentPath(path: String): String = path.substringBeforeLast('/', "")

/** Stable, path-derived document id so a row tag is deterministic in a test. */
private fun documentIdOf(path: String): String = path.replace('/', '-')

private fun fileRow(path: String): String = BrowseTags.row("doc:${documentIdOf(path)}")

private fun directoryRow(path: String): String = BrowseTags.row("dir:$path")

/**
 * NB-02/NB-03/NB-04 page acceptance on the REAL `LibraryBrowseScreen`: a real
 * [LibraryViewModel] over a real [LibraryScanner] and an in-memory Room index,
 * with SAF metadata supplied by a controllable [TreeLister]. Only the tree
 * contents are fake — the production screen, its state binding, the breadcrumb
 * request (including its provenance), and the scan state machine are the
 * objects under test (REQ-0012 AC-1/AC-2/AC-3/AC-4/AC-5/AC-6).
 *
 * Addressed to the independent acceptance's page-readiness findings: every
 * type-cue assertion is scoped to one row (never a bare
 * `onNodeWithContentDescription`/`onNodeWithText` over many matching rows), the
 * lister gate is per-scan and uninterruptible so SCANNING and CANCELLING are
 * separately measurable, and the fixture covers a ten-level path on a narrow
 * screen, long Chinese names at 200% font scale, an empty folder, both runtime
 * display modes, and the different-prefix scroll reset.
 *
 * NOT_RUN on this machine (no device or emulator attached); the class only
 * compiles here. Every claim below is a page measurement, not a model-only
 * assertion.
 */
@RunWith(AndroidJUnit4::class)
class LibraryBrowsePageTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: LeafletDatabase
    private lateinit var lister: GatedTreeLister
    private lateinit var viewModel: LibraryViewModel
    private var libraryId: LibraryId = LibraryId("")
    private val opened = mutableListOf<String>()
    private var backCount = 0

    @Before
    fun setUp() {
        db =
            Room.inMemoryDatabaseBuilder(context, LeafletDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        lister = GatedTreeLister()
        val repo = LibraryRepositoryImpl(db, NoGrantContentAccess, Dispatchers.IO)
        val scanner = LibraryScanner(db, lister, repo, Dispatchers.IO)
        viewModel = LibraryViewModel(RepositoryListSource(repo), scanner)
        runBlocking {
            libraryId = repo.addLibrary("Docs", TREE_URI).id
            FILES.forEach { path ->
                db.documentDao().upsert(
                    DocumentEntity(
                        documentId = documentIdOf(path),
                        libraryId = libraryId.value,
                        relativePath = path,
                        uri = "$TREE_URI/$path",
                        displayName = path.substringAfterLast('/'),
                        title = path.substringAfterLast('/').substringBeforeLast('.'),
                        extension = path.substringAfterLast('.'),
                        size = FILE_SIZE,
                        lastModified = FILE_MTIME,
                        contentHash = null,
                    ),
                )
            }
            db.directoryDao().insertAll(DIRECTORIES.map { DirectoryEntity(libraryId.value, it) })
        }
    }

    @After
    fun tearDown() {
        runCatching { lister.release() }
        runCatching { compose.runOnIdle { viewModel.cancelRefresh() } }
        if (::db.isInitialized) db.close()
    }

    // -- NB-02 breadcrumbs ---------------------------------------------------

    @Test
    fun ancestorBreadcrumbOpensThatPrefixAndBackMovesOneSegment() {
        composeBrowse()
        openDirectory("Notes")
        openDirectory("Notes/2026")

        compose.onNodeWithTag(BrowseTags.breadcrumb("")).assertIsDisplayed()
        compose.onNodeWithTag(BrowseTags.breadcrumb("Notes")).assertIsDisplayed()
        compose.onNodeWithTag(BrowseTags.breadcrumb("Notes/2026")).assertIsDisplayed()

        compose.onNodeWithTag(BrowseTags.breadcrumb("Notes")).performClick()

        compose.waitUntil(TIMEOUT_MS) { viewModel.browse.value.prefix == "Notes" }
        compose.onNodeWithTag(directoryRow("Notes/2026")).assertIsDisplayed()
        compose.runOnIdle { viewModel.leaveDirectory() }
        compose.waitUntil(TIMEOUT_MS) { viewModel.browse.value.prefix == "" }
        compose.runOnIdle { viewModel.leaveDirectory() }
        compose.runOnIdle { }
        assertTrue(viewModel.browse.value.prefix == "")
    }

    @Test
    fun theCurrentBreadcrumbSegmentHasNoNavigationAction() {
        composeBrowse()
        openDirectory("Notes")

        compose.onNodeWithTag(BrowseTags.breadcrumb("Notes")).assertHasNoClickAction()
        compose.onNodeWithTag(BrowseTags.breadcrumb("Notes")).performClick()
        compose.runOnIdle { }

        assertTrue(viewModel.browse.value.prefix == "Notes")
    }

    @Test
    fun aQueuedRootTapKeepsThePrefixItWasRenderedFor() {
        composeBrowse()
        navigateTo("Notes/2026")
        val queuedRootTap =
            compose.onNodeWithTag(BrowseTags.breadcrumb(""))
                .fetchSemanticsNode().config[SemanticsActions.OnClick].action
                ?: error("root breadcrumb has no click action")

        compose.runOnIdle { viewModel.leaveDirectory() }
        compose.waitUntil(TIMEOUT_MS) { viewModel.browse.value.prefix == "Notes" }
        compose.waitForIdle()
        val before = viewModel.browse.value

        // Invoke the actual old action after the live State has advanced.
        compose.runOnIdle { queuedRootTap() }
        assertEquals(before, viewModel.browse.value)
    }

    @Test
    fun everyBreadcrumbEntryAndRowMeetsTheMinimumTouchTarget() {
        composeBrowse()

        compose.onNodeWithTag(BrowseTags.breadcrumb("")).assertHeightIsAtLeast(48.dp)
        compose.onNodeWithTag(directoryRow("Notes")).assertHeightIsAtLeast(48.dp)
        compose.onNodeWithTag(fileRow("doc01.md")).assertHeightIsAtLeast(48.dp)
        compose.onNodeWithTag(fileRow("guide.txt")).assertHeightIsAtLeast(48.dp)

        openDirectory("Notes")
        compose.onNodeWithTag(BrowseTags.breadcrumb("Notes")).assertHeightIsAtLeast(48.dp)
        compose.onNodeWithTag(BrowseTags.breadcrumb("")).assertHeightIsAtLeast(48.dp)
    }

    /**
     * AC-3: a ten-level path stays reachable on a narrow screen because the
     * breadcrumb is one bounded, horizontally scrollable row rather than a
     * wrapping block. Off-screen entries are asserted to exist (they are
     * composed inside the scroller) and the scroller is asserted to have travel.
     */
    @Test
    fun tenLevelPathKeepsEveryAncestorReachableOnANarrowScreen() {
        composeBrowse(narrow = true)
        navigateTo(DEEP_PATH)

        val expected = listOf("") + DEEP_DIRECTORIES
        expected.forEach { prefix ->
            compose.onNodeWithTag(BrowseTags.breadcrumb(prefix)).assertExists()
            compose.onNodeWithTag(BrowseTags.breadcrumb(prefix)).assertHeightIsAtLeast(48.dp)
        }
        val currentLabel = context.getString(R.string.library_breadcrumb_current)
        compose.onAllNodes(
            hasTestTag(BrowseTags.breadcrumb(DEEP_PATH)) and
                SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, currentLabel),
        ).assertCountEquals(1)

        val travel = horizontalScrollTravel()
        assertTrue("breadcrumb row does not scroll on a narrow screen (travel=$travel)", travel > 0f)
    }

    /**
     * FR-6/AC-6: a scrolled folder list starts at the top again when a different
     * prefix is opened, including when returning to a prefix that was scrolled
     * before.
     */
    @Test
    fun aDifferentPrefixStartsItsListAtTheTop() {
        composeBrowse()
        compose.onNodeWithTag(BrowseTags.LIST).performTouchInput { swipeUp() }
        compose.waitForIdle()
        assertTrue("expected the root list to scroll", scrollOffset() > 0f)

        openDirectory("Notes")
        assertEquals(0f, scrollOffset(), 0.5f)

        compose.runOnIdle { viewModel.leaveDirectory() }
        compose.waitUntil(TIMEOUT_MS) { viewModel.browse.value.prefix == "" }
        assertEquals(0f, scrollOffset(), 0.5f)
    }

    // -- NB-04 rows ----------------------------------------------------------

    @Test
    fun rowsCarryRowScopedTypeCuesAndHandTheDocumentIdToTheReader() {
        composeBrowse()
        val folderLabel = context.getString(R.string.library_row_folder)
        val documentLabel = context.getString(R.string.library_row_document)

        // Scoped to ONE row: the fixture has many document/MD rows, so a global
        // query would be ambiguous (independent-acceptance finding).
        assertRowCue(directoryRow("Notes"), folderLabel)
        assertRowCue(fileRow("doc01.md"), documentLabel)
        assertRowCue(fileRow("guide.txt"), documentLabel)
        assertRowTagText(fileRow("doc01.md"), "MD")
        assertRowTagText(fileRow("guide.txt"), "TXT")

        compose.onNodeWithTag(fileRow("doc01.md")).performClick()

        compose.runOnIdle { }
        assertTrue("opened=$opened", opened.contains(documentIdOf("doc01.md")))
    }

    @Test
    fun longChineseNamesAtTwoHundredPercentFontScaleKeepTheRowUsable() {
        composeBrowse(fontScale = 2f)
        navigateTo(LONG_DIR)

        compose.onNodeWithTag(fileRow(LONG_FILE)).assertIsDisplayed()
        compose.onNodeWithTag(fileRow(LONG_FILE)).assertHeightIsAtLeast(48.dp)
        assertRowTagText(fileRow(LONG_FILE), LONG_FILE.substringAfterLast('/'))
        assertRowCue(fileRow(LONG_FILE), context.getString(R.string.library_row_document))
    }

    @Test
    fun standardRuntimeModeKeepsTheBrowseControlsUsable() {
        composeBrowse(displayMode = DisplayMode.STANDARD)
        assertBrowseControlsUsable()
    }

    @Test
    fun einkRuntimeModeKeepsTheBrowseControlsUsable() {
        composeBrowse(displayMode = DisplayMode.EINK)
        assertBrowseControlsUsable()
    }

    // -- NB-03 scan-status region -------------------------------------------

    @Test
    fun everyScanStateTransitionKeepsTheListGeometryStable() {
        composeBrowse()
        compose.onNodeWithTag(BrowseTags.LIST).performTouchInput { swipeUp() }
        compose.waitForIdle()
        val settled = geometry()

        // IDLE → SCANNING (the gated lister keeps the scan in progress).
        startGatedScan()
        assertSameGeometry("IDLE→SCANNING", settled)

        // SCANNING → CANCELLING: the gate is uninterruptible, so this phase is
        // deterministic instead of a race.
        compose.runOnIdle { viewModel.cancelRefresh() }
        awaitProgress("CANCELLING") { it.state == ScanProgressState.CANCELLING }
        assertSameGeometry("SCANNING→CANCELLING", settled)

        // CANCELLING → CANCELLED.
        lister.release()
        awaitProgress("CANCELLED") { it.state == ScanProgressState.CANCELLED }
        assertSameGeometry("CANCELLING→CANCELLED", settled)

        // SCANNING → SUCCEEDED over unchanged indexed rows.
        startGatedScan()
        lister.release()
        awaitProgress("SUCCEEDED") { it.state == ScanProgressState.SUCCEEDED }
        assertSameGeometry("SCANNING→SUCCEEDED", settled)

        // NAV-ACC-02 on the real scanner: a Cancel that arrives after the scan
        // settled must not publish CANCELLING or disturb the list.
        compose.runOnIdle { viewModel.cancelRefresh() }
        compose.runOnIdle { }
        assertEquals(ScanProgressState.SUCCEEDED, viewModel.browse.value.progress.state)
        assertSameGeometry("late Cancel after SUCCEEDED", settled)

        // SCANNING → FAILED → Retry through the production action button.
        lister.failNext = true
        compose.runOnIdle { viewModel.refresh(libraryId) }
        awaitProgress("FAILED") { it.state == ScanProgressState.FAILED }
        assertSameGeometry("SCANNING→FAILED", settled)
        compose.onNodeWithTag(BrowseTags.SCAN_STATUS).assertIsDisplayed()

        compose.onNodeWithTag(BrowseTags.SCAN_ACTION).assertIsDisplayed()
        lister.holdNextScan()
        compose.onNodeWithTag(BrowseTags.SCAN_ACTION).performClick()
        awaitProgress("retry SCANNING") { it.state == ScanProgressState.SCANNING }
        assertSameGeometry("FAILED→Retry", settled)
        lister.release()
        awaitProgress("retry terminal") { it.state == ScanProgressState.SUCCEEDED }
    }

    @Test
    fun scanTransitionsInAnEmptyFolderKeepTheStatusRegionStable() {
        composeBrowse()
        navigateTo("Empty")
        compose.onNodeWithText(context.getString(R.string.library_browse_empty)).assertIsDisplayed()
        val settled = statusBounds()

        startGatedScan()
        assertSameStatusBounds("IDLE→SCANNING in an empty folder", settled)
        compose.runOnIdle { viewModel.cancelRefresh() }
        awaitProgress("CANCELLING") { it.state == ScanProgressState.CANCELLING }
        assertSameStatusBounds("SCANNING→CANCELLING in an empty folder", settled)
        lister.release()
        awaitProgress("CANCELLED") { it.state == ScanProgressState.CANCELLED }
        assertSameStatusBounds("CANCELLING→CANCELLED in an empty folder", settled)
    }

    // -- assertions and harness ---------------------------------------------

    private fun assertBrowseControlsUsable() {
        compose.onNodeWithTag(BrowseTags.breadcrumb("")).assertHeightIsAtLeast(48.dp)
        compose.onNodeWithTag(directoryRow("Notes")).assertIsDisplayed()
        compose.onNodeWithTag(fileRow("doc01.md")).assertIsDisplayed()
        compose.onNodeWithTag(fileRow("doc01.md")).performClick()
        compose.runOnIdle { }
        assertTrue("opened=$opened", opened.contains(documentIdOf("doc01.md")))
    }

    /** Exactly one node with [description] inside the row tagged [rowTag]. */
    private fun assertRowCue(
        rowTag: String,
        description: String,
    ) {
        // Clickable rows merge their children; inspect the icon beneath the row in the unmerged tree.
        compose.onAllNodes(
            hasContentDescription(description) and hasAnyAncestor(hasTestTag(rowTag)),
            useUnmergedTree = true,
        ).assertCountEquals(1)
    }

    /** Exactly one node with [text] inside the row tagged [rowTag]. */
    private fun assertRowTagText(
        rowTag: String,
        text: String,
    ) {
        compose.onAllNodes(
            hasText(text) and hasAnyAncestor(hasTestTag(rowTag)),
            useUnmergedTree = true,
        ).assertCountEquals(1)
    }

    private fun startGatedScan() {
        lister.holdNextScan()
        compose.runOnIdle { viewModel.refresh(libraryId) }
        awaitProgress("SCANNING") { it.state == ScanProgressState.SCANNING }
    }

    private fun composeBrowse(
        fontScale: Float = 1f,
        displayMode: DisplayMode? = null,
        narrow: Boolean = false,
    ) {
        compose.setContent {
            val base = LocalDensity.current
            val screen: @Composable () -> Unit = {
                LibraryBrowseScreen(
                    viewModel = viewModel,
                    libraryId = libraryId,
                    onOpenDocument = { opened += it },
                    onBack = { backCount++ },
                )
            }
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale)) {
                val themed: @Composable () -> Unit = {
                    if (displayMode == null) screen() else LeafletTheme(displayMode) { screen() }
                }
                if (narrow) Box(Modifier.width(320.dp)) { themed() } else themed()
            }
        }
        compose.runOnIdle { viewModel.openLibrary(libraryId) }
        compose.waitUntil(TIMEOUT_MS) { viewModel.browse.value.documents.isNotEmpty() }
        compose.onNodeWithTag(BrowseTags.LIST).assertIsDisplayed()
    }

    /**
     * Walks to [prefix] through the same ViewModel action the rows call. Used
     * where the target row is outside the viewport (200% font scale, ten levels,
     * an empty folder), which a tap-based walk could not reach deterministically.
     */
    private fun navigateTo(prefix: String) {
        val segments = prefix.split('/').filter { it.isNotEmpty() }
        compose.runOnIdle {
            segments.forEach { viewModel.enterDirectory(it) }
        }
        compose.waitUntil(TIMEOUT_MS) { viewModel.browse.value.prefix == prefix }
    }

    private fun openDirectory(prefix: String) {
        compose.onNodeWithTag(directoryRow(prefix)).performClick()
        compose.waitUntil(TIMEOUT_MS) { viewModel.browse.value.prefix == prefix }
    }

    private fun awaitProgress(
        label: String,
        predicate: (ScanProgress) -> Boolean,
    ) {
        compose.waitUntil(TIMEOUT_MS) { predicate(viewModel.browse.value.progress) }
        assertTrue("$label not reached: ${viewModel.browse.value.progress}", predicate(viewModel.browse.value.progress))
    }

    private fun listNode() =
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
            .fetchSemanticsNodes()
            .firstOrNull() ?: error("no scrollable list on the browse page")

    private fun scrollOffset(): Float {
        val range = listNode().config[SemanticsProperties.VerticalScrollAxisRange]
        return range.value()
    }

    private fun horizontalScrollTravel(): Float {
        val node =
            compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.HorizontalScrollAxisRange))
                .fetchSemanticsNodes()
                .firstOrNull() ?: return 0f
        val range = node.config[SemanticsProperties.HorizontalScrollAxisRange]
        return range.maxValue()
    }

    private fun statusBounds(): Rect = compose.onNodeWithTag(BrowseTags.SCAN_STATUS).fetchSemanticsNode().boundsInRoot

    private data class BrowseGeometry(
        val list: Rect,
        val offset: Float,
        val rows: List<Pair<String, Float>>,
    )

    private fun geometry(): BrowseGeometry {
        val list = listNode()
        val range = list.config[SemanticsProperties.VerticalScrollAxisRange]
        val rowPrefix = BrowseTags.row("")
        val rows =
            compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.TestTag))
                .fetchSemanticsNodes()
                .mapNotNull { node ->
                    if (!node.config.contains(SemanticsProperties.TestTag)) return@mapNotNull null
                    val tag = node.config[SemanticsProperties.TestTag]
                    if (!tag.startsWith(rowPrefix)) return@mapNotNull null
                    tag to node.boundsInRoot.top
                }.sortedBy { it.first }
        return BrowseGeometry(list = list.boundsInRoot, offset = range.value(), rows = rows)
    }

    private fun assertSameGeometry(
        label: String,
        before: BrowseGeometry,
    ) {
        val after = geometry()
        val moved =
            !withinOnePixel(before.list.top, after.list.top) ||
                !withinOnePixel(before.list.bottom, after.list.bottom) ||
                !withinOnePixel(before.offset, after.offset) ||
                before.rows.size != after.rows.size ||
                before.rows.zip(after.rows).any { (left, right) ->
                    left.first != right.first || !withinOnePixel(left.second, right.second)
                }
        assertTrue("$label moved the list: $before -> $after", !moved)
    }

    private fun assertSameStatusBounds(
        label: String,
        before: Rect,
    ) {
        val after = statusBounds()
        assertTrue(
            "$label moved the status region: $before -> $after",
            withinOnePixel(before.top, after.top) &&
                withinOnePixel(before.bottom, after.bottom) &&
                withinOnePixel(before.left, after.left),
        )
    }

    private fun withinOnePixel(
        before: Float,
        after: Float,
    ): Boolean = abs(before - after) <= 1f
}

/**
 * SAF metadata for the browse page: the same tree the seed rows describe, so a
 * successful scan re-reads identical rows and cannot itself move the list.
 *
 * [holdNextScan] gates the NEXT scan only, so a later scan (for example the
 * Retry after a failure) runs normally; the gate is entered under
 * [NonCancellable] so it models a provider query that cannot be interrupted
 * mid-flight. That is what makes the real CANCELLING phase observable instead
 * of a race (independent-acceptance page-readiness finding).
 */
private class GatedTreeLister : TreeLister {
    @Volatile
    private var gate: CompletableDeferred<Unit>? = null

    /** The next [listChildren] call fails like a provider error. */
    @Volatile
    var failNext: Boolean = false

    fun holdNextScan() {
        gate = CompletableDeferred()
    }

    fun release() {
        gate?.complete(Unit)
        gate = null
    }

    override suspend fun treeDocumentId(treeUri: String): String = "root"

    override suspend fun listChildren(
        treeUri: String,
        parentDocumentId: String,
    ): List<TreeEntry> {
        withContext(NonCancellable) { gate?.await() }
        if (failNext) {
            failNext = false
            throw IOException("stub provider failure")
        }
        val parent = if (parentDocumentId == "root") "" else parentDocumentId.removePrefix("dir:")
        val directories = DIRECTORIES.filter { parentPath(it) == parent }.map { directoryEntry(it) }
        val files = FILES.filter { parentPath(it) == parent }.map { fileEntry(it) }
        return directories + files
    }

    private fun directoryEntry(path: String): TreeEntry =
        TreeEntry(
            documentId = "dir:$path",
            displayName = path.substringAfterLast('/'),
            isDirectory = true,
            size = null,
            lastModified = null,
            documentUri = "$TREE_URI/$path",
        )

    private fun fileEntry(path: String): TreeEntry =
        TreeEntry(
            documentId = "file:$path",
            displayName = path.substringAfterLast('/'),
            isDirectory = false,
            size = FILE_SIZE,
            lastModified = FILE_MTIME,
            documentUri = "$TREE_URI/$path",
        )
}

/** The production binding shape (see `LeafletApp`), over a test repository. */
private class RepositoryListSource(
    private val repository: LibraryRepositoryImpl,
) : LibraryListSource {
    override suspend fun addLibrary(
        displayName: String,
        rootTreeUri: String,
    ): Library = repository.addLibrary(displayName, rootTreeUri)

    override suspend fun observeDocumentsSnapshot(libraryId: LibraryId): List<Document> = repository.listDocuments(libraryId)

    override suspend fun observeDirectoriesSnapshot(libraryId: LibraryId): List<String> = repository.listDirectories(libraryId)

    override fun observeLibraries(): Flow<List<Library>> = repository.observeLibraries()

    override fun observeScanState(libraryId: LibraryId): Flow<ScanState> = repository.observeScanState(libraryId)

    override suspend fun removeLibrary(libraryId: LibraryId) {
        repository.removeLibrary(libraryId)
    }
}

/** This harness never opens a stream; scans are metadata-only. */
private object NoGrantContentAccess : ContentAccess {
    override fun openInput(uriString: String): InputStream? = null

    override fun persistedReadUris(): Set<String> = emptySet()

    override fun takeReadPermission(treeUriString: String) = Unit

    override fun releaseReadPermission(treeUriString: String) = Unit
}
