package io.leaflet.reader.ui.library

import io.leaflet.model.Document
import io.leaflet.model.DocumentId
import io.leaflet.model.Library
import io.leaflet.model.LibraryId
import io.leaflet.storage.ScanState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * NB-01 guarded transition (REQ-0012 FR-2, AC-1/AC-2): the real
 * [LibraryViewModel] with a fake list source, no Compose.
 *
 * Every case goes through [selectAncestor], which builds the request exactly
 * the way the production breadcrumb does — from the state that is on screen —
 * so the provenance checks are exercised rather than bypassed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LibraryAncestorNavigationTest {
    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun openAncestorMovesToTheSelectedPrefix() {
        val viewModel = LibraryViewModel(FakeBrowseSource(), scanner = null)
        viewModel.openLibrary(LibraryId("docs"))
        viewModel.enterDirectory("Docs")
        viewModel.enterDirectory("Notes")
        viewModel.enterDirectory("2026")
        assertEquals("Docs/Notes/2026", viewModel.browse.value.prefix)

        viewModel.selectAncestor("Docs")

        assertEquals("Docs", viewModel.browse.value.prefix)
        assertEquals("docs", viewModel.browse.value.libraryId)
    }

    @Test
    fun openAncestorOnTheRootReturnsToTheLibraryRoot() {
        val viewModel = LibraryViewModel(FakeBrowseSource(), scanner = null)
        viewModel.openLibrary(LibraryId("docs"))
        viewModel.enterDirectory("Docs")
        viewModel.enterDirectory("Notes")

        viewModel.selectAncestor("")

        assertEquals("", viewModel.browse.value.prefix)
    }

    @Test
    fun selectingTheCurrentSegmentLeavesStateUnchanged() {
        val viewModel = LibraryViewModel(FakeBrowseSource(), scanner = null)
        viewModel.openLibrary(LibraryId("docs"))
        viewModel.enterDirectory("Docs")
        viewModel.enterDirectory("Notes")
        val before = viewModel.browse.value

        viewModel.selectAncestor("Docs/Notes")

        assertEquals(before, viewModel.browse.value)
    }

    @Test
    fun rejectedTargetsLeaveStateUnchanged() {
        val viewModel = LibraryViewModel(FakeBrowseSource(), scanner = null)
        viewModel.openLibrary(LibraryId("docs"))
        viewModel.enterDirectory("Docs")
        viewModel.enterDirectory("Notes")
        val before = viewModel.browse.value

        // Sibling, unrelated prefix, tree escape, deeper-than-open directory,
        // and something that is not a relative path at all.
        listOf(
            "Docs/Other",
            "Other",
            "..",
            "Docs/Notes/2026",
            "content://tree/primary%3ADocs",
        ).forEach { target ->
            viewModel.selectAncestor(target)
            assertEquals("target=$target", before, viewModel.browse.value)
        }
    }

    @Test
    fun aIsNotAcceptedAsAnAncestorOfAb() {
        val viewModel = LibraryViewModel(FakeBrowseSource(), scanner = null)
        viewModel.openLibrary(LibraryId("docs"))
        viewModel.enterDirectory("ab")
        val before = viewModel.browse.value

        viewModel.selectAncestor("a")

        assertEquals(before, viewModel.browse.value)
        assertEquals("ab", viewModel.browse.value.prefix)
    }

    @Test
    fun aNonAncestorTargetFromAReplacedLibraryIsIgnored() {
        val viewModel = LibraryViewModel(FakeBrowseSource(), scanner = null)
        viewModel.openLibrary(LibraryId("docs"))
        viewModel.enterDirectory("Docs")
        // A request generated while "docs" was open at Docs.
        val stale = requestFor("Docs", "docs", "Docs")

        // Switching Libraries resets the browse prefix to the new root.
        viewModel.openLibrary(LibraryId("other"))
        val afterSwitch = viewModel.browse.value
        assertEquals("", afterSwitch.prefix)
        assertEquals("other", afterSwitch.libraryId)

        viewModel.openAncestor(stale)

        assertEquals(afterSwitch, viewModel.browse.value)
    }

    /**
     * NAV-ACC-01: the Library ROOT prefix is valid in every Library, so a
     * request generated on Library A's breadcrumb and delivered after the user
     * switched to Library B must not reset B to its own root. Provenance, not
     * the path alone, is what has to be checked.
     */
    @Test
    fun aRootTargetFromAReplacedLibraryIsIgnored() {
        val viewModel = LibraryViewModel(FakeBrowseSource(), scanner = null)
        viewModel.openLibrary(LibraryId("a"))
        // A's root breadcrumb entry: target "", generated for library "a" at "".
        val staleRoot = requestFor("", "a", "")

        viewModel.openLibrary(LibraryId("b"))
        viewModel.enterDirectory("Docs")
        viewModel.enterDirectory("Notes")
        val before = viewModel.browse.value
        assertEquals("Docs/Notes", before.prefix)
        assertEquals("b", before.libraryId)

        viewModel.openAncestor(staleRoot)

        assertEquals(before, viewModel.browse.value)
        assertEquals("Docs/Notes", viewModel.browse.value.prefix)
    }

    /**
     * NAV-ACC-01, same-path variant: Library B really is at `Docs/Notes`, so the
     * stale `Docs` target from Library A is a valid ancestor *on paper*. It must
     * still be ignored, because it was generated for another Library.
     */
    @Test
    fun aTargetFromTheSamePathInAnotherLibraryIsIgnored() {
        val viewModel = LibraryViewModel(FakeBrowseSource(), scanner = null)
        viewModel.openLibrary(LibraryId("a"))
        viewModel.enterDirectory("Docs")
        viewModel.enterDirectory("Notes")
        val stale = requestFor("Docs", "a", "Docs/Notes")

        viewModel.openLibrary(LibraryId("b"))
        viewModel.enterDirectory("Docs")
        viewModel.enterDirectory("Notes")
        val before = viewModel.browse.value

        viewModel.openAncestor(stale)

        assertEquals(before, viewModel.browse.value)
        assertEquals("Docs/Notes", viewModel.browse.value.prefix)
    }

    /**
     * The same Library, but a request rendered at a prefix the user has since
     * left: it cannot be replayed after Back, even though the target is still a
     * valid ancestor.
     */
    @Test
    fun aTargetRenderedAtAnOlderPrefixIsIgnored() {
        val viewModel = LibraryViewModel(FakeBrowseSource(), scanner = null)
        viewModel.openLibrary(LibraryId("docs"))
        viewModel.enterDirectory("Docs")
        viewModel.enterDirectory("Notes")
        viewModel.enterDirectory("2026")
        val rendered = requestFor("Docs", "docs", "Docs/Notes/2026")

        viewModel.leaveDirectory()
        val before = viewModel.browse.value
        assertEquals("Docs/Notes", before.prefix)

        viewModel.openAncestor(rendered)

        assertEquals(before, viewModel.browse.value)
        assertEquals("Docs/Notes", viewModel.browse.value.prefix)
    }

    @Test
    fun switchingLibrariesStartsAtTheNewRootAndBackStopsThere() {
        val viewModel = LibraryViewModel(FakeBrowseSource(), scanner = null)
        viewModel.openLibrary(LibraryId("docs"))
        viewModel.enterDirectory("Docs")
        viewModel.enterDirectory("Notes")

        viewModel.openLibrary(LibraryId("other"))

        assertEquals("", viewModel.browse.value.prefix)
        assertTrue(!viewModel.leaveDirectory())
    }

    @Test
    fun backStillMovesUpExactlyOneSegment() {
        val viewModel = LibraryViewModel(FakeBrowseSource(), scanner = null)
        viewModel.openLibrary(LibraryId("docs"))
        viewModel.enterDirectory("Docs")
        viewModel.enterDirectory("Notes")

        assertTrue(viewModel.leaveDirectory())

        assertEquals("Docs", viewModel.browse.value.prefix)
        assertTrue(viewModel.leaveDirectory())
        assertEquals("", viewModel.browse.value.prefix)
    }
}

/** The request the production breadcrumb builds from the state on screen. */
private fun requestFor(
    target: String,
    libraryId: String,
    prefix: String,
): AncestorRequest = AncestorRequest(target = target, libraryId = libraryId, prefix = prefix)

/** Taps an entry rendered from the CURRENT state, like the production screen. */
private fun LibraryViewModel.selectAncestor(target: String) {
    val state = browse.value
    openAncestor(requestFor(target, state.libraryId, state.prefix))
}

private class FakeBrowseSource : LibraryListSource {
    private val libraries = MutableStateFlow<List<Library>>(emptyList())

    private val documents =
        listOf(
            Document(
                id = DocumentId("doc-1"),
                libraryId = LibraryId("docs"),
                displayName = "readme.md",
                relativePath = "Docs/readme.md",
                uri = "content://tree/Docs/readme.md",
                extension = "md",
            ),
        )

    override suspend fun addLibrary(
        displayName: String,
        rootTreeUri: String,
    ): Library = error("unused")

    override suspend fun observeDocumentsSnapshot(libraryId: LibraryId): List<Document> = documents

    override suspend fun observeDirectoriesSnapshot(libraryId: LibraryId): List<String> = listOf("Docs/Notes")

    override fun observeLibraries(): Flow<List<Library>> = libraries

    override fun observeScanState(libraryId: LibraryId): Flow<ScanState> = MutableStateFlow(ScanState.IDLE)

    override suspend fun removeLibrary(libraryId: LibraryId) = Unit
}
