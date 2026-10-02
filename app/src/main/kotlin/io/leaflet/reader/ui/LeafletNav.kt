package io.leaflet.reader.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import io.leaflet.eink.pick
import io.leaflet.model.LibraryId
import io.leaflet.reader.BuildConfig
import io.leaflet.reader.R
import io.leaflet.reader.SettingsStore
import io.leaflet.reader.SettingsViewModel
import io.leaflet.reader.ui.library.LibraryBrowseScreen
import io.leaflet.reader.ui.library.LibraryListScreen
import io.leaflet.reader.ui.library.LibraryViewModel
import io.leaflet.reader.ui.library.ReaderPlaceholderScreen
import io.leaflet.reader.ui.theme.LocalMotionPolicy

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
    // REQ-0004 FR-2/FR-4 + §Standard: every transition decision goes through the
    // resolved MotionPolicy, and M0/M1 adds NO page transitions in either mode
    // (E-Ink must never crossfade; Standard has no page transitions either).
    // navigation-compose 2.7.7's NavHost defaults are fadeIn/fadeOut(tween(700)),
    // so all four transitions must be supplied explicitly — relying on the
    // defaults silently animated every route swap (CR-20261001-02).
    val motion = LocalMotionPolicy.current
    val instantEnter: EnterTransition = motion.pick(EnterTransition.None, EnterTransition.None)
    val instantExit: ExitTransition = motion.pick(ExitTransition.None, ExitTransition.None)
    NavHost(
        navController = nav,
        startDestination = Routes.LIBRARY,
        modifier =
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
                .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout)),
        enterTransition = { instantEnter },
        exitTransition = { instantExit },
        popEnterTransition = { instantEnter },
        popExitTransition = { instantExit },
    ) {
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
