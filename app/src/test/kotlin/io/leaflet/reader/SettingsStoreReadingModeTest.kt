package io.leaflet.reader

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import io.leaflet.model.DisplayMode
import io.leaflet.storage.prefs.ReaderSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * CR-20260930-05 (REQ-0005 FR-4): the reading mode an install uses before the
 * user ever chooses one follows the display mode — E-Ink opens in page turns,
 * Standard in page turns — and an explicit choice is never rewritten,
 * neither by a later read nor by a display-mode switch.
 *
 * The production [SettingsStore] runs unchanged over an in-memory DataStore (no
 * disk I/O), so this exercises the real default/coercion path.
 */
class SettingsStoreReadingModeTest {
    /** In-memory DataStore: same seam the page tests use, no Android APIs. */
    private class MemoryPreferencesDataStore : DataStore<Preferences> {
        private val state = MutableStateFlow(emptyPreferences())

        override val data: Flow<Preferences> = state

        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
            val next = transform(state.value)
            state.value = next
            return next
        }
    }

    @Test
    fun freshEinkInstallDefaultsToPageTurns() =
        runTest {
            val store = SettingsStore(MemoryPreferencesDataStore(), DisplayMode.EINK)
            assertEquals(ReaderSettings.READING_MODE_PAGE_TURN, store.readingMode.first())
        }

    @Test
    fun freshStandardInstallDefaultsToPageTurns() =
        runTest {
            val store = SettingsStore(MemoryPreferencesDataStore(), DisplayMode.STANDARD)
            assertEquals(ReaderSettings.READING_MODE_PAGE_TURN, store.readingMode.first())
        }

    @Test
    fun anExplicitChoiceWinsOverTheDisplayDefaultAndSurvives() =
        runTest {
            val dataStore = MemoryPreferencesDataStore()
            val eink = SettingsStore(dataStore, DisplayMode.EINK)
            // A user on an E-Ink install may still choose continuous scrolling.
            eink.setReadingMode(ReaderSettings.READING_MODE_CONTINUOUS)
            assertEquals(ReaderSettings.READING_MODE_CONTINUOUS, eink.readingMode.first())

            // That stored choice — not the display default — is what every later
            // read sees, including from a store configured for another mode.
            val standard = SettingsStore(dataStore, DisplayMode.STANDARD)
            assertEquals(ReaderSettings.READING_MODE_CONTINUOUS, standard.readingMode.first())

            standard.setReadingMode(ReaderSettings.READING_MODE_PAGE_TURN)
            assertEquals(ReaderSettings.READING_MODE_PAGE_TURN, eink.readingMode.first())
        }
}
