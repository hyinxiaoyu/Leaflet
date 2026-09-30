package io.leaflet.reader.ui

import androidx.compose.runtime.Composable
import io.leaflet.model.DisplayMode
import io.leaflet.reader.SettingsViewModel
import io.leaflet.reader.ui.reader.ReaderScreen
import io.leaflet.reader.ui.reader.ReaderViewModel

/**
 * R2-07 page shim: hosts the production [ReaderScreen] with a caller-built
 * [ReaderViewModel] (production session + Markwon parser over a fake
 * storage seam — see [FakePageStore]). No navigation, no settings: the
 * smallest frame that still composes the real reader route.
 *
 * R2 DEF-006: [settingsViewModel] is optional and drives the production
 * typography/mode wiring the same way `MainActivity` does, so page tests can
 * change a setting and assert the REAL `TextView`s, not just model state.
 */
@Composable
fun ReaderScreenShim(
    documentId: String,
    vm: ReaderViewModel,
    settingsViewModel: SettingsViewModel? = null,
) {
    ReaderScreen(
        viewModel = vm,
        documentId = documentId,
        onBack = {},
        settingsViewModel = settingsViewModel,
        defaultDisplayMode = DisplayMode.STANDARD,
    )
}
