package io.leaflet.reader.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.leaflet.eink.AppCapabilities
import io.leaflet.model.DisplayMode
import io.leaflet.model.LibraryId
import io.leaflet.reader.BuildConfig
import io.leaflet.reader.R
import io.leaflet.reader.SettingsStore
import io.leaflet.reader.SettingsViewModel
import io.leaflet.reader.ui.library.LibraryBrowseScreen
import io.leaflet.reader.ui.library.LibraryListScreen
import io.leaflet.reader.ui.library.LibraryViewModel
import io.leaflet.reader.ui.library.ReaderPlaceholderScreen

object Routes {
    const val LIBRARY = "library"
    const val LIBRARY_BROWSE = "library/{libraryId}"
    const val READER = "reader/{documentId}"
    const val SETTINGS = "settings"
    const val ABOUT = "about"

    fun browse(libraryId: String): String = "library/$libraryId"

    fun reader(documentId: String): String = "reader/$documentId"
}

/**
 * Five-route shell (T05, Reader bound T07): library list → per-library
 * browse → reader body, plus settings / about.
 *
 * The document picker (`ACTION_OPEN_DOCUMENT_TREE`) is launched by the host
 * Activity via [onAddLibrary]; the returned tree URI flows into
 * [LibraryViewModel.onTreePicked]. Back-compat: the legacy [LibraryScreen]
 * empty-state composable is kept for tests/greps and now delegates to the
 * real list screen when a [LibraryViewModel] is available.
 */
@Composable
fun LeafletNav(
    settingsStore: SettingsStore,
    capabilities: AppCapabilities,
    viewModel: SettingsViewModel,
    libraryViewModel: LibraryViewModel? = null,
    readerViewModel: io.leaflet.reader.ui.reader.ReaderViewModel? = null,
    recentViewModel: io.leaflet.reader.ui.reader.RecentViewModel? = null,
    onAddLibrary: () -> Unit = {},
    onReauthorizeLibrary: () -> Unit = {},
    imageLoader: io.leaflet.reader.ui.reader.ImageLoader? = null,
    onOpenExternal: (String) -> Unit = {},
    onOpenThirdParty: () -> Unit = {},
) {
    val nav = rememberNavController()
    // T12: navigation-compose 2.7.7 applies no enter/exit transitions by
    // default, so routes swap instantly — no crossfade/transition to disable
    // (E-Ink rule, REQ-0004 FR-4).
    NavHost(navController = nav, startDestination = Routes.LIBRARY) {
        composable(Routes.LIBRARY) {
            if (libraryViewModel == null) {
                LibraryScreen(
                    onSettings = { nav.navigate(Routes.SETTINGS) },
                    onAbout = { nav.navigate(Routes.ABOUT) },
                )
            } else {
                LibraryListScreen(
                    viewModel = libraryViewModel,
                    onOpenLibrary = { id -> nav.navigate(Routes.browse(id.value)) },
                    onAddLibrary = onAddLibrary,
                    onSettings = { nav.navigate(Routes.SETTINGS) },
                    onAbout = { nav.navigate(Routes.ABOUT) },
                    recentViewModel = recentViewModel,
                    onOpenDocument = { documentId -> nav.navigate(Routes.reader(documentId)) },
                )
            }
        }
        composable(
            Routes.LIBRARY_BROWSE,
            arguments = listOf(navArgument("libraryId") { type = NavType.StringType }),
        ) { entry ->
            val libraryId = entry.arguments?.getString("libraryId").orEmpty()
            if (libraryViewModel == null) {
                LibraryScreen(
                    onSettings = { nav.navigate(Routes.SETTINGS) },
                    onAbout = { nav.navigate(Routes.ABOUT) },
                )
            } else {
                LaunchedEffect(libraryId) { libraryViewModel.openLibrary(LibraryId(libraryId)) }
                LibraryBrowseScreen(
                    viewModel = libraryViewModel,
                    libraryId = LibraryId(libraryId),
                    onOpenDocument = { documentId -> nav.navigate(Routes.reader(documentId)) },
                    onBack = { nav.popBackStack() },
                )
            }
        }
        composable(
            Routes.READER,
            arguments = listOf(navArgument("documentId") { type = NavType.StringType }),
        ) { entry ->
            val documentId = entry.arguments?.getString("documentId").orEmpty()
            if (readerViewModel == null) {
                ReaderPlaceholderScreen(documentId = documentId, onBack = { nav.popBackStack() })
            } else {
                io.leaflet.reader.ui.reader.ReaderScreen(
                    viewModel = readerViewModel,
                    documentId = documentId,
                    onBack = { nav.popBackStack() },
                    onReauthorize = onReauthorizeLibrary,
                    onRemoveDeadEntry = { deadId ->
                        recentViewModel?.removeEntry(io.leaflet.model.DocumentId(deadId))
                    },
                    settingsViewModel = viewModel,
                    onOpenDocument = { target -> nav.navigate(Routes.reader(target.value)) },
                    imageLoader = imageLoader,
                    onOpenExternal = onOpenExternal,
                )
            }
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                store = settingsStore,
                capabilities = capabilities,
                viewModel = viewModel,
                onAbout = { nav.navigate(Routes.ABOUT) },
                onBack = { nav.popBackStack() },
            )
        }
        composable(Routes.ABOUT) {
            AboutScreen(onBack = { nav.popBackStack() }, onOpenThirdParty = onOpenThirdParty)
        }
    }
}

/**
 * Empty-state only (T02; kept for back-compat — the live list is
 * `LibraryListScreen` in `ui/library`). Real browsing arrives in T05.
 */
@Composable
fun LibraryScreen(
    onSettings: () -> Unit,
    onAbout: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text(stringResource(R.string.library_title), style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.library_empty_body))
        Spacer(Modifier.height(16.dp))
        // Disabled placeholder: SAF folder import lands in T04/T05.
        Button(onClick = {}, enabled = false) {
            Text(stringResource(R.string.library_add_pending))
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onSettings) { Text(stringResource(R.string.go_settings)) }
        TextButton(onClick = onAbout) { Text(stringResource(R.string.go_about)) }
    }
}

/**
 * Display-mode switch is fully functional (DataStore-backed). Font/layout rows
 * are read-only placeholders pointing at T09/T10.
 */
@Composable
fun SettingsScreen(
    store: SettingsStore,
    capabilities: AppCapabilities,
    viewModel: SettingsViewModel,
    onAbout: () -> Unit,
    onBack: () -> Unit,
) {
    val mode by viewModel.displayMode.collectAsState(initial = store.defaultMode)
    // Scrollable: display + reading mode + typography + soft-wrap exceed one
    // viewport on small screens; LazyColumn keeps every row reachable.
    androidx.compose.foundation.lazy.LazyColumn(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        item {
            Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.settings_display_section))
            DisplayModeButton(
                label = stringResource(R.string.mode_standard),
                selected = mode == DisplayMode.STANDARD,
                onClick = { viewModel.setDisplayMode(DisplayMode.STANDARD) },
            )
            DisplayModeButton(
                label = stringResource(R.string.mode_eink),
                selected = mode == DisplayMode.EINK,
                onClick = { viewModel.setDisplayMode(DisplayMode.EINK) },
            )
            if (!capabilities.supportsAdvancedSharing) {
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.settings_trimmed_note))
            }
            Spacer(Modifier.height(16.dp))
            // Reading mode (T08/REQ-0005 FR-4): CONTINUOUS_SCROLL default,
            // E-Ink boots PAGE_TURN via ReadingMode.defaultForEink only as the
            // first-run value — the stored choice always wins afterwards.
            val readingMode by viewModel.readingMode.collectAsState(initial = "CONTINUOUS_SCROLL")
            Text(stringResource(R.string.settings_reading_section))
            DisplayModeButton(
                label = stringResource(R.string.reading_mode_continuous),
                selected = readingMode == "CONTINUOUS_SCROLL",
                onClick = { viewModel.setReadingMode("CONTINUOUS_SCROLL") },
            )
            DisplayModeButton(
                label = stringResource(R.string.reading_mode_page_turn),
                selected = readingMode == "PAGE_TURN",
                onClick = { viewModel.setReadingMode("PAGE_TURN") },
            )
            Spacer(Modifier.height(16.dp))
            // Typography (T09/REQ-0007 FR-1–FR-4/FR-7/FR-10): four persisted steps
            // + soft-wrap, all via SettingsStore → ReaderStyle (Reader never reads
            // storage). No WRAP/CARD table switch exists (FR-9); no bundled fonts.
            Text(stringResource(R.string.settings_typography_section))
            val fontSize by viewModel.fontSizeStep.collectAsState(initial = 2)
            StepRow(
                label = stringResource(R.string.typography_font_size),
                value = stringResource(R.string.typography_font_size_value, fontSize + 1),
                onDecrease = { viewModel.setFontSizeStep(fontSize - 1) },
                onIncrease = { viewModel.setFontSizeStep(fontSize + 1) },
                canDecrease = fontSize > 0,
                canIncrease = fontSize < 4,
            )
            val fontFamily by viewModel.fontFamily.collectAsState(initial = "system")
            FontFamilyRow(selected = fontFamily, onSelect = { viewModel.setFontFamily(it) })
            val lineSpacing by viewModel.lineSpacingStep.collectAsState(initial = 1)
            StepRow(
                label = stringResource(R.string.typography_line_spacing),
                value = stringResource(R.string.typography_line_spacing_value, lineSpacing + 1),
                onDecrease = { viewModel.setLineSpacingStep(lineSpacing - 1) },
                onIncrease = { viewModel.setLineSpacingStep(lineSpacing + 1) },
                canDecrease = lineSpacing > 0,
                canIncrease = lineSpacing < 2,
            )
            val pageMargin by viewModel.pageMarginStep.collectAsState(initial = 1)
            StepRow(
                label = stringResource(R.string.typography_page_margin),
                value = stringResource(R.string.typography_page_margin_value, pageMargin + 1),
                onDecrease = { viewModel.setPageMarginStep(pageMargin - 1) },
                onIncrease = { viewModel.setPageMarginStep(pageMargin + 1) },
                canDecrease = pageMargin > 0,
                canIncrease = pageMargin < 2,
            )
            // Soft-wrap: null (never chosen) + E-Ink → ON is resolved in the
            // Reader via effectiveCodeSoftWrap; the row shows that resolved value.
            val softWrapStored by viewModel.codeSoftWrap.collectAsState(initial = null)
            val softWrapOn = viewModel.effectiveCodeSoftWrap(softWrapStored, mode == DisplayMode.EINK)
            DisplayModeButton(
                label =
                    if (softWrapOn) {
                        stringResource(R.string.typography_soft_wrap_on)
                    } else {
                        stringResource(R.string.typography_soft_wrap_off)
                    },
                selected = softWrapOn,
                onClick = { viewModel.setCodeSoftWrap(!softWrapOn) },
            )
            Spacer(Modifier.height(16.dp))
            TextButton(onClick = onAbout) { Text(stringResource(R.string.go_about)) }
            TextButton(onClick = onBack) { Text(stringResource(R.string.go_back)) }
        }
    }
}

@Composable
private fun DisplayModeButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    if (selected) {
        Button(onClick = onClick, modifier = Modifier.padding(vertical = 4.dp)) { Text("● $label") }
    } else {
        TextButton(onClick = onClick, modifier = Modifier.padding(vertical = 4.dp)) { Text("○ $label") }
    }
}

/**
 * Stepper row for the persisted typography steps (T09/REQ-0007 FR-1/FR-3/FR-4):
 * bounds come from `ReaderStyle` bands; clamping lives in `SettingsStore`.
 */
@Composable
private fun StepRow(
    label: String,
    value: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    canDecrease: Boolean,
    canIncrease: Boolean,
) {
    Text(label, style = MaterialTheme.typography.bodyLarge)
    androidx.compose.foundation.layout.Row {
        TextButton(onClick = onDecrease, enabled = canDecrease) { Text("−") }
        Text(value, modifier = Modifier.padding(vertical = 12.dp))
        TextButton(onClick = onIncrease, enabled = canIncrease) { Text("+") }
    }
}

/**
 * System-font selector (T09/REQ-0007 FR-2): the three `ReaderStyle`
 * whitelist families only — monospace is reserved for code blocks and never
 * offered as body text; no bundled or imported fonts.
 */
@Composable
private fun FontFamilyRow(
    selected: String,
    onSelect: (String) -> Unit,
) {
    Text(stringResource(R.string.typography_font_family), style = MaterialTheme.typography.bodyLarge)
    FontOption(
        label = stringResource(R.string.typography_font_system),
        value = "system",
        selected = selected,
        onSelect = onSelect,
    )
    FontOption(
        label = stringResource(R.string.typography_font_sans),
        value = "sans-serif",
        selected = selected,
        onSelect = onSelect,
    )
    FontOption(
        label = stringResource(R.string.typography_font_serif),
        value = "serif",
        selected = selected,
        onSelect = onSelect,
    )
}

@Composable
private fun FontOption(
    label: String,
    value: String,
    selected: String,
    onSelect: (String) -> Unit,
) {
    DisplayModeButton(label = label, selected = selected == value, onClick = { onSelect(value) })
}

/** About: brand, version, flavor identity, TBD license, temporary-ID notice. */
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    onOpenThirdParty: () -> Unit = {},
) {
    // T12 (REQ-0010 FR-5/FR-6/FR-7/FR-10): instant static content, no
    // animation; flavor identity from BuildConfig; license stays TBD until
    // the owner decides; third-party entry links to THIRD_PARTY_LICENSES.md
    // (text only, no copied license bodies); refresh semantics stay system
    // level — no hardware refresh claims appear here.
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text(stringResource(R.string.about_title), style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.app_name))
        Text(stringResource(R.string.about_version, BuildConfig.VERSION_NAME, BuildConfig.FLAVOR, BuildConfig.VERSION_CODE))
        Text(stringResource(R.string.about_flavor, BuildConfig.FLAVOR))
        Text(stringResource(R.string.about_app_id, BuildConfig.APPLICATION_ID))
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.about_license_pending))
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.about_refresh_note))
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onOpenThirdParty) { Text(stringResource(R.string.about_third_party)) }
        TextButton(onClick = onBack) { Text(stringResource(R.string.go_back)) }
    }
}
