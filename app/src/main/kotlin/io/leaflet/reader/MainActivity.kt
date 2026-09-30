package io.leaflet.reader

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import io.leaflet.model.DisplayMode
import io.leaflet.reader.ui.LeafletNav
import io.leaflet.reader.ui.library.LibraryViewModel
import io.leaflet.reader.ui.theme.LeafletTheme
import io.leaflet.storage.scan.defaultDisplayNameForTree
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val pickTree =
        registerForActivityResult(
            ActivityResultContracts.OpenDocumentTree(),
        ) { uri: Uri? ->
            val container = (application as LeafletApp).container
            val vm = container.libraryViewModel
            if (uri != null) {
                // R2-05: persisting here is best-effort (the repository
                // re-takes the grant in addLibrary anyway). A throw must
                // surface as the "无法添加" notice, never a crash — e.g. a
                // re-picked tree after revocation on a flaky provider.
                try {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                } catch (_: Exception) {
                    vm.onTreeGrantFailed()
                    return@registerForActivityResult
                }
                vm.onTreePicked(uri.toString(), defaultDisplayNameForTree(uri.toString()))
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as LeafletApp).container
        val factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    when (modelClass) {
                        SettingsViewModel::class.java ->
                            SettingsViewModel(container.settingsStore) as T
                        LibraryViewModel::class.java ->
                            container.libraryViewModel as T
                        io.leaflet.reader.ui.reader.ReaderViewModel::class.java ->
                            io.leaflet.reader.ui.reader.ReaderViewModel(
                                store = container.readerWiring.readerStore(),
                                parser = container.documentParser,
                            ) as T
                        io.leaflet.reader.ui.reader.RecentViewModel::class.java ->
                            io.leaflet.reader.ui.reader.RecentViewModel(
                                store = container.readerWiring.recentStore(),
                            ) as T
                        else -> throw IllegalArgumentException("unknown ${modelClass.name}")
                    }
            }
        setContent {
            // T12 (REQ-0004 FR-3, REQ-0010 FR-1/FR-2): the single collected
            // mode flows into LeafletTheme AND every screen below; changing
            // it in Settings re-composes in place (hot switch, no restart)
            // and DataStore persistence restores it after process death.
            val mode by container.settingsStore.displayMode.collectAsState(
                initial = container.settingsStore.defaultMode,
            )
            LeafletTheme(displayMode = mode) {
                val settingsViewModel: SettingsViewModel = viewModel(factory = factory)
                val libraryViewModel: LibraryViewModel = viewModel(factory = factory)
                val readerViewModel: io.leaflet.reader.ui.reader.ReaderViewModel =
                    viewModel(factory = factory)
                val recentViewModel: io.leaflet.reader.ui.reader.RecentViewModel =
                    viewModel(factory = factory)
                // Mode hot-switch flows from the single `mode` collection above.
                LeafletNav(
                    settingsStore = container.settingsStore,
                    capabilities = container.capabilities,
                    viewModel = settingsViewModel,
                    libraryViewModel = libraryViewModel,
                    readerViewModel = readerViewModel,
                    recentViewModel = recentViewModel,
                    onAddLibrary = { pickTree.launch(null) },
                    onReauthorizeLibrary = { pickTree.launch(null) },
                    imageLoader = container.imageLoader,
                    onOpenExternal = { url ->
                        // R3-06 fix: the old `runCatching` swallowed
                        // ActivityNotFoundException, so the REQ-0009
                        // "no external app" notice could never appear in
                        // production. Probe resolution first and report a
                        // missing handler through the same ViewModel notice
                        // the page already renders.
                        if (!launchExternalUrl(url)) {
                            readerViewModel.reportNoExternalApp()
                        }
                    },
                )
            }
        }
    }

    /**
     * Hands [url] to another app (REQ-0009 FR-10) and reports whether a
     * handler actually took it. Returns false when nothing can resolve the
     * ACTION_VIEW Intent or the launch raises
     * [android.content.ActivityNotFoundException], so the caller can surface
     * the recoverable "no external app" notice instead of failing silently.
     */
    private fun launchExternalUrl(url: String): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        if (intent.resolveActivity(packageManager) == null) return false
        return try {
            startActivity(intent)
            true
        } catch (_: android.content.ActivityNotFoundException) {
            false
        }
    }
}

/** Settings surface (T09/REQ-0007 + T08/REQ-0005): display + typography + reading entries. */
class SettingsViewModel(private val store: SettingsStore) : ViewModel() {
    val displayMode = store.displayMode

    fun setDisplayMode(mode: DisplayMode) {
        viewModelScope.launch { store.setDisplayMode(mode) }
    }

    /** Font-size step (5 steps, default 2); Reader binds via [io.leaflet.markdown.ReaderStyle]. */
    val fontSizeStep = store.fontSizeStep

    fun setFontSizeStep(step: Int) {
        viewModelScope.launch { store.setFontSizeStep(step) }
    }

    /** System font family (`"system"` default; whitelist in `ReaderStyle`). */
    val fontFamily = store.fontFamily

    fun setFontFamily(family: String) {
        viewModelScope.launch { store.setFontFamily(family) }
    }

    /** Line-spacing step (3 steps, default 1). */
    val lineSpacingStep = store.lineSpacingStep

    fun setLineSpacingStep(step: Int) {
        viewModelScope.launch { store.setLineSpacingStep(step) }
    }

    /** Page-margin step (3 steps, default 1). */
    val pageMarginStep = store.pageMarginStep

    fun setPageMarginStep(step: Int) {
        viewModelScope.launch { store.setPageMarginStep(step) }
    }

    /**
     * Code soft-wrap override (REQ-0007 FR-7). E-Ink default ON: a fresh
     * E-Ink install with no stored value reports `true` via
     * [effectiveCodeSoftWrap]; Standard honors the stored value as-is.
     */
    val codeSoftWrap = store.codeSoftWrap

    fun setCodeSoftWrap(enabled: Boolean) {
        viewModelScope.launch { store.setCodeSoftWrap(enabled) }
    }

    /**
     * Soft-wrap value the Reader binds. Unset (null) + E-Ink → ON (FR-7);
     * explicit user choice always wins on either mode.
     */
    fun effectiveCodeSoftWrap(
        stored: Boolean?,
        isEink: Boolean,
    ): Boolean = stored ?: isEink

    /** Reading mode (`CONTINUOUS_SCROLL`/`PAGE_TURN`, T08/REQ-0005 FR-4); behavior in ReaderScreen. */
    val readingMode = store.readingMode

    fun setReadingMode(mode: String) {
        viewModelScope.launch { store.setReadingMode(mode) }
    }

    /** Page-turn overlap 3%–8% (default 5%); geometry via `:core:reader` PageTurn. */
    val pageOverlap = store.pageOverlap

    fun setPageOverlap(overlap: Float) {
        viewModelScope.launch { store.setPageOverlap(overlap) }
    }

    /**
     * First-run tap-zone onboarding (UX-06): the Reader shows the hint while
     * this is `false` and records the dismissal here, so it never returns.
     */
    val tapZonesHintSeen = store.tapZonesHintSeen

    fun dismissTapZonesHint() {
        viewModelScope.launch { store.setTapZonesHintSeen(true) }
    }
}
