package io.leaflet.reader

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import io.leaflet.model.DisplayMode
import io.leaflet.storage.prefs.ReaderSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val DISPLAY_MODE_KEY = stringPreferencesKey("display_mode")
private val FONT_SIZE_KEY = intPreferencesKey("font_size_step")
private val FONT_FAMILY_KEY = stringPreferencesKey("font_family")
private val LINE_SPACING_KEY = intPreferencesKey("line_spacing_step")
private val PAGE_MARGIN_KEY = intPreferencesKey("page_margin_step")
private val CODE_SOFT_WRAP_KEY = booleanPreferencesKey("code_soft_wrap")
private val PAGE_OVERLAP_KEY = floatPreferencesKey("page_overlap")
private val READING_MODE_KEY = stringPreferencesKey("reading_mode")
private val TAP_ZONES_HINT_SEEN_KEY = booleanPreferencesKey("tap_zones_hint_seen")

/**
 * Persists the STANDARD/EINK choice (REQ-0004 FR-3, REQ-0010 FR-2) plus the
 * typography/reading keys owned by REQ-0007 (defaults in
 * [ReaderSettings]; T09 wires the UI, this class only stores/retrieves).
 *
 * Corrupt/missing data falls back to defaults, never crashes (REQ-0010 edge
 * case). The `display_mode` key and its behavior are unchanged by T04.
 */
class SettingsStore(
    private val dataStore: DataStore<Preferences>,
    val defaultMode: DisplayMode,
) {
    val displayMode: Flow<DisplayMode> =
        dataStore.data
            .catch { e ->
                // Corrupt DataStore: emit empty prefs so the default applies (REQ-0010 edge case).
                if (e is IOException) emit(androidx.datastore.preferences.core.preferencesOf()) else throw e
            }
            .map { prefs ->
                when (prefs[DISPLAY_MODE_KEY]) {
                    DisplayMode.EINK.name -> DisplayMode.EINK
                    DisplayMode.STANDARD.name -> DisplayMode.STANDARD
                    else -> defaultMode
                }
            }

    suspend fun setDisplayMode(mode: DisplayMode) {
        dataStore.edit { prefs -> prefs[DISPLAY_MODE_KEY] = mode.name }
    }

    /** Font-size step in `FONT_SIZE_MIN..FONT_SIZE_MAX` (default [ReaderSettings.FONT_SIZE_DEFAULT]). */
    val fontSizeStep: Flow<Int> =
        dataStore.data.catchIo().map { prefs ->
            ReaderSettings.coerceFontSize(prefs[FONT_SIZE_KEY] ?: ReaderSettings.FONT_SIZE_DEFAULT)
        }

    suspend fun setFontSizeStep(step: Int) {
        dataStore.edit { prefs -> prefs[FONT_SIZE_KEY] = ReaderSettings.coerceFontSize(step) }
    }

    /** System font family name, `"system"` = platform default (no bundled fonts, REQ-0007). */
    val fontFamily: Flow<String> =
        dataStore.data.catchIo().map { prefs ->
            ReaderSettings.sanitizeFontFamily(prefs[FONT_FAMILY_KEY] ?: ReaderSettings.FONT_FAMILY_DEFAULT)
        }

    suspend fun setFontFamily(family: String) {
        dataStore.edit { prefs -> prefs[FONT_FAMILY_KEY] = ReaderSettings.sanitizeFontFamily(family) }
    }

    /** Line-spacing step in `LINE_SPACING_MIN..LINE_SPACING_MAX`. */
    val lineSpacingStep: Flow<Int> =
        dataStore.data.catchIo().map { prefs ->
            ReaderSettings.coerceLineSpacing(prefs[LINE_SPACING_KEY] ?: ReaderSettings.LINE_SPACING_DEFAULT)
        }

    suspend fun setLineSpacingStep(step: Int) {
        dataStore.edit { prefs -> prefs[LINE_SPACING_KEY] = ReaderSettings.coerceLineSpacing(step) }
    }

    /** Page-margin step in `PAGE_MARGIN_MIN..PAGE_MARGIN_MAX`. */
    val pageMarginStep: Flow<Int> =
        dataStore.data.catchIo().map { prefs ->
            ReaderSettings.coercePageMargin(prefs[PAGE_MARGIN_KEY] ?: ReaderSettings.PAGE_MARGIN_DEFAULT)
        }

    suspend fun setPageMarginStep(step: Int) {
        dataStore.edit { prefs -> prefs[PAGE_MARGIN_KEY] = ReaderSettings.coercePageMargin(step) }
    }

    /**
     * Code-block soft-wrap override (REQ-0007 FR-7): null when the user never
     * chose, so the Reader can apply the per-mode default (E-Ink ON). An
     * explicit choice always wins on either mode.
     */
    val codeSoftWrap: Flow<Boolean?> = dataStore.data.catchIo().map { prefs -> prefs[CODE_SOFT_WRAP_KEY] }

    suspend fun setCodeSoftWrap(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[CODE_SOFT_WRAP_KEY] = enabled }
    }

    /** Page-turn overlap fraction, clamped to 3%–8% (default 5%). */
    val pageOverlap: Flow<Float> =
        dataStore.data.catchIo().map { prefs ->
            ReaderSettings.coercePageOverlap(prefs[PAGE_OVERLAP_KEY] ?: ReaderSettings.PAGE_OVERLAP_DEFAULT)
        }

    suspend fun setPageOverlap(overlap: Float) {
        dataStore.edit { prefs -> prefs[PAGE_OVERLAP_KEY] = ReaderSettings.coercePageOverlap(overlap) }
    }

    /**
     * `CONTINUOUS_SCROLL` or `PAGE_TURN` (behavior owned by T08/REQ-0005; stored
     * here only).
     *
     * CR-20260930-05: an ABSENT value falls back to the default for this
     * install's display mode (both now default to page turns). An explicitly stored value always wins, including
     * after a display-mode switch.
     */
    val readingMode: Flow<String> =
        dataStore.data.catchIo().map { prefs ->
            prefs[READING_MODE_KEY]?.let { ReaderSettings.sanitizeReadingMode(it) }
                ?: ReaderSettings.defaultReadingModeFor(defaultMode)
        }

    suspend fun setReadingMode(mode: String) {
        dataStore.edit { prefs -> prefs[READING_MODE_KEY] = ReaderSettings.sanitizeReadingMode(mode) }
    }

    /**
     * First-run tap-zone onboarding (UX-06): `false` until the reader hint is
     * dismissed once. A missing/corrupt value reads as the default (`false`),
     * so the hint is shown at most once per install.
     */
    val tapZonesHintSeen: Flow<Boolean> =
        dataStore.data.catchIo().map { prefs ->
            prefs[TAP_ZONES_HINT_SEEN_KEY] ?: ReaderSettings.TAP_ZONES_HINT_SEEN_DEFAULT
        }

    suspend fun setTapZonesHintSeen(seen: Boolean) {
        dataStore.edit { prefs -> prefs[TAP_ZONES_HINT_SEEN_KEY] = seen }
    }

    private fun Flow<Preferences>.catchIo(): Flow<Preferences> =
        catch { e ->
            if (e is IOException) emit(androidx.datastore.preferences.core.preferencesOf()) else throw e
        }
}
