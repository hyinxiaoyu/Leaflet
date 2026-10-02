package io.leaflet.reader.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.leaflet.eink.capabilitiesForFlavor
import io.leaflet.model.DisplayMode
import io.leaflet.model.DocumentId
import io.leaflet.model.Library
import io.leaflet.model.LibraryId
import io.leaflet.model.RecentRead
import io.leaflet.reader.R
import io.leaflet.reader.SettingsStore
import io.leaflet.reader.SettingsViewModel
import io.leaflet.reader.ui.library.HomeTags
import io.leaflet.reader.ui.library.LibraryHomeContent
import io.leaflet.reader.ui.library.LibraryListUiState
import io.leaflet.reader.ui.reader.RecentHealth
import io.leaflet.reader.ui.reader.RecentRow
import io.leaflet.reader.ui.theme.LeafletTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Production content and settings; only metadata/storage inputs are controlled. */
@RunWith(AndroidJUnit4::class)
class DailyReadingPageTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private var added = 0
    private var cleared = 0
    private var opened: String? = null

    private fun home(
        libraries: Int = 0,
        recent: Int = 0,
        health: RecentHealth = RecentHealth.OK,
        loading: Boolean = false,
    ) {
        val state =
            LibraryListUiState(
                loading = loading,
                libraries = (1..libraries).map { Library(LibraryId("l$it"), "Folder $it", "content://tree/$it") },
            )
        val rows =
            (1..recent).map {
                RecentRow(RecentRead(DocumentId("d$it"), "Document $it", LibraryId("l1"), System.currentTimeMillis()), "Folder 1", health)
            }
        compose.setContent {
            LeafletTheme(DisplayMode.EINK) {
                LibraryHomeContent(
                    state, rows, null,
                    onOpenLibrary = {}, onAddLibrary = { added++ }, onSettings = {}, onAbout = {},
                    onOpenDocument = { opened = it }, onDismissNotice = {}, onRequestRemove = {},
                    onDismissRemove = {}, onConfirmRemove = {}, onDismissRecentNotice = {},
                    onRemoveRecent = {}, onClearRecent = { cleared++ }, onRetryLibraries = {},
                )
            }
        }
    }

    @Test
    fun loadingHomeDoesNotOfferFalseFirstUseGuidance() {
        home(loading = true)
        compose.onNodeWithTag(HomeTags.EMPTY).assertDoesNotExist()
        compose.onAllNodesWithText(context.getString(R.string.home_loading)).onLast().assertIsDisplayed()
    }

    @Test
    fun emptyHomeHasAWorkingFirstStepAndReachableSettings() {
        home()
        compose.onNodeWithTag(HomeTags.EMPTY).assertIsDisplayed()
        compose.onAllNodesWithText(context.getString(R.string.library_add)).onLast().performClick()
        assertEquals(1, added)
        compose.onNodeWithTag(HomeTags.LIST).performScrollToNode(hasTestTag(HomeTags.SETTINGS))
        compose.onNodeWithTag(HomeTags.SETTINGS).assertIsDisplayed()
    }

    @Test
    fun largeHomeKeepsContinuationAndFooterReachable() {
        home(libraries = 20, recent = 30)
        compose.onNodeWithTag(HomeTags.CONTINUE).performClick()
        assertEquals("d1", opened)
        compose.onNodeWithTag(HomeTags.LIST).performScrollToNode(hasTestTag(HomeTags.recent("d30")))
        compose.onNodeWithTag(HomeTags.recent("d30")).assertIsDisplayed()
        compose.onNodeWithTag(HomeTags.LIST).performScrollToNode(hasTestTag(HomeTags.SETTINGS))
        compose.onNodeWithTag(HomeTags.SETTINGS).assertIsDisplayed()
    }

    @Test
    fun clearingHistoryRequiresConfirmation() {
        home(recent = 1)
        compose.onNodeWithTag(HomeTags.LIST).performScrollToNode(hasTestTag(HomeTags.CLEAR))
        compose.onNodeWithTag(HomeTags.CLEAR).performClick()
        assertEquals(0, cleared)
        compose.onNodeWithTag(HomeTags.CLEAR_CANCEL).performClick()
        assertEquals(0, cleared)
        compose.onNodeWithTag(HomeTags.CLEAR).performClick()
        compose.onNodeWithTag(HomeTags.CLEAR_CONFIRM).performClick()
        assertEquals(1, cleared)
    }

    @Test
    fun unavailableRecentDocumentKeepsTheRecoveryHandoff() {
        home(recent = 1, health = RecentHealth.AUTH_LOST)
        compose.onNodeWithTag(HomeTags.CONTINUE).performClick()
        assertEquals("d1", opened)
    }

    @Test
    fun typographyPreviewTracksThePersistedFontSetting() {
        val store = SettingsStore(MemoryPreferencesDataStore(), DisplayMode.EINK)
        val vm = SettingsViewModel(store)
        compose.setContent {
            LeafletTheme(DisplayMode.EINK) {
                SettingsScreen(store, capabilitiesForFlavor("eink"), vm, onAbout = {}, onBack = {})
            }
        }

        fun previewSize(): Float {
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithTag(SettingsTags.PREVIEW_TEXT).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            return layouts.single().layoutInput.style.fontSize.value
        }
        val before = previewSize()
        compose.runOnIdle { vm.setFontSizeStep(4) }
        compose.waitUntil(5_000) { previewSize() > before }
        assertTrue(previewSize() > before)
    }
}
