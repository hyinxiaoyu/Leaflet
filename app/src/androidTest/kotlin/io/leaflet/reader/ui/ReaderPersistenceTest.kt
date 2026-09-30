package io.leaflet.reader.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.leaflet.markdown.LeafletDocumentParser
import io.leaflet.model.DisplayMode
import io.leaflet.model.DocumentId
import io.leaflet.model.ReadingPosition
import io.leaflet.reader.ReaderWiring
import io.leaflet.reader.SettingsStore
import io.leaflet.reader.StorageReaderGateway
import io.leaflet.reader.ui.reader.ReaderUiState
import io.leaflet.reader.ui.reader.ReaderViewModel
import io.leaflet.storage.db.DocumentEntity
import io.leaflet.storage.db.LeafletDatabase
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
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream

/**
 * R3-04 package (阅读位置、设置与模式持久化, §15/17/26): real on-disk
 * `DataStore` + real Room database — the in-memory stand-ins used by the page
 * suite cannot prove persistence.
 *
 * Cross-process evidence recipe (run from the repo root):
 * ```bash
 * ./gradlew :app:assembleFullDebug :app:assembleFullDebugAndroidTest
 * adb install -r app/build/outputs/apk/full/debug/app-full-debug.apk
 * adb install -r app/build/outputs/apk/androidTest/full/debug/app-full-debug-androidTest.apk
 * R=androidx.test.runner.AndroidJUnitRunner
 * adb shell am instrument -w -e class io.leaflet.reader.ui.ReaderPersistenceSeedTest \
 *   io.leaflet.reader.debug.test/$R                       # phase 1: writes real files
 * adb shell am force-stop io.leaflet.reader.debug          # real process kill
 * adb shell am instrument -w -e class io.leaflet.reader.ui.ReaderPersistenceAfterRestartTest \
 *   io.leaflet.reader.debug.test/$R                       # phase 2: NEW process reads them
 * ```
 * Installed with `adb install -r` (NOT the Gradle device task, which uninstalls
 * the app and wipes its data between runs).
 */
@RunWith(AndroidJUnit4::class)
class ReaderPersistenceSeedTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private companion object {
        const val DB_NAME = "r3-persist.db"
        const val DS_DIR = "r3-persist-datastore"
        const val DS_FILE = "r3-persist.preferences_pb"
        const val LIBRARY_TREE = "content://r3.persist.tree"
        const val DOC_ID = "r3-persist-doc"
        const val BLOCK_INDEX = 3
        const val ANCHOR = "Anchor Heading"
        const val SEED_MARKER = "r3-persist-seed.marker"
    }

    private fun dataStoreFile(): File {
        val dir = File(context.filesDir, DS_DIR).apply { mkdirs() }
        return File(dir, DS_FILE)
    }

    @Test
    fun seedSettingsAndPosition() {
        context.deleteDatabase(DB_NAME)
        File(context.filesDir, DS_DIR).deleteRecursively()
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val settings =
            SettingsStore(
                PreferenceDataStoreFactory.create(scope = scope, produceFile = { dataStoreFile() }),
                DisplayMode.STANDARD,
            )
        val db =
            Room.databaseBuilder(context, LeafletDatabase::class.java, DB_NAME)
                .allowMainThreadQueries()
                .build()
        try {
            runBlocking {
                settings.setFontSizeStep(ReaderSettings.FONT_SIZE_MAX)
                settings.setLineSpacingStep(ReaderSettings.LINE_SPACING_MAX)
                settings.setFontFamily("serif")
                settings.setPageMarginStep(ReaderSettings.PAGE_MARGIN_MIN)
                settings.setDisplayMode(DisplayMode.EINK)
                settings.setReadingMode(ReaderSettings.READING_MODE_PAGE_TURN)
                settings.setCodeSoftWrap(false)

                val repo = LibraryRepositoryImpl(db, NoopContentAccess, Dispatchers.IO)
                val library = repo.addLibrary("Persist Library", LIBRARY_TREE)
                db.documentDao().upsert(
                    DocumentEntity(
                        documentId = DOC_ID,
                        libraryId = library.id.value,
                        relativePath = "persist.md",
                        uri = "content://r3.persist.tree/persist.md",
                        displayName = "persist.md",
                        title = null,
                        extension = "md",
                        size = 42L,
                        lastModified = 7L,
                        contentHash = null,
                    ),
                )
                repo.savePosition(
                    ReadingPosition(
                        documentId = DocumentId(DOC_ID),
                        version = 1,
                        blockIndex = BLOCK_INDEX,
                        blockOffset = 0,
                        progress = 0.5f,
                        anchor = ANCHOR,
                    ),
                    lastModified = 7L,
                    size = 42L,
                )
            }
            // Independent seed marker (DEF-011): the phase-2 gate must not key
            // off a value that is itself under test.
            File(context.filesDir, SEED_MARKER).writeText("seeded")
        } finally {
            db.close()
            scope.coroutineContext[Job]?.cancel()
        }
    }
}

/**
 * Phase 2 of the R3-04 persistence proof: run this class in a NEW process (see
 * [ReaderPersistenceSeedTest]). When the seed has not run yet the test is
 * SKIPPED (JUnit assumption) instead of failed, so a plain suite run stays
 * deterministic — the adb-driven two-phase run above is what proves
 * cross-process persistence.
 */
@RunWith(AndroidJUnit4::class)
class ReaderPersistenceAfterRestartTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private companion object {
        const val DB_NAME = "r3-persist.db"
        const val DS_DIR = "r3-persist-datastore"
        const val DS_FILE = "r3-persist.preferences_pb"
        const val DOC_ID = "r3-persist-doc"
        const val FONT_SIZE = ReaderSettings.FONT_SIZE_MAX
        const val LINE_SPACING = ReaderSettings.LINE_SPACING_MAX
        const val PAGE_MARGIN = ReaderSettings.PAGE_MARGIN_MIN
        const val BLOCK_INDEX = 3
        const val ANCHOR = "Anchor Heading"
        const val SEED_MARKER = "r3-persist-seed.marker"
    }

    private fun dataStoreFile(): File {
        val dir = File(context.filesDir, DS_DIR).apply { mkdirs() }
        return File(dir, DS_FILE)
    }

    @Test
    fun settingsAndPositionSurviveProcessKill() {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val settings =
            SettingsStore(
                PreferenceDataStoreFactory.create(scope = scope, produceFile = { dataStoreFile() }),
                DisplayMode.STANDARD,
            )
        val db =
            Room.databaseBuilder(context, LeafletDatabase::class.java, DB_NAME)
                .allowMainThreadQueries()
                .build()
        try {
            // DEF-011: gate on an INDEPENDENT marker, never on a value under
            // test; a dedicated two-phase run (`-e leaflet.requireSeed true`)
            // turns a missing seed into a FAILURE instead of a skip.
            val marker = File(context.filesDir, SEED_MARKER)
            val requireSeed =
                InstrumentationRegistry.getArguments().getString("leaflet.requireSeed") == "true"
            if (requireSeed) {
                assertTrue(
                    "two-phase run requires the seed phase (marker missing)",
                    marker.exists(),
                )
            } else {
                assumeTrue(
                    "phase 1 (ReaderPersistenceSeedTest) must run first",
                    marker.exists(),
                )
            }
            runBlocking {
                assertEquals("font size", FONT_SIZE, settings.fontSizeStep.first())
                assertEquals("line spacing", LINE_SPACING, settings.lineSpacingStep.first())
                assertEquals("font family", "serif", settings.fontFamily.first())
                assertEquals("page margin", PAGE_MARGIN, settings.pageMarginStep.first())
                assertEquals("display mode", DisplayMode.EINK, settings.displayMode.first())
                assertEquals("reading mode", ReaderSettings.READING_MODE_PAGE_TURN, settings.readingMode.first())
                assertEquals("code soft wrap", false, settings.codeSoftWrap.first())

                val repo = LibraryRepositoryImpl(db, NoopContentAccess, Dispatchers.IO)
                val row = repo.loadPositionWithFingerprint(DocumentId(DOC_ID))
                assertEquals("position block", BLOCK_INDEX, row?.position?.blockIndex)
                assertEquals("position anchor", ANCHOR, row?.position?.anchor)
            }
        } finally {
            db.close()
            scope.coroutineContext[Job]?.cancel()
        }
    }
}

/**
 * External edit → refresh: real Room + real reader chain, SAF faked only at
 * [ContentAccess]. The stored position belongs to an OLD fingerprint, so the
 * reader must fall back (anchor/progress) with the stale notice, and an
 * explicit refresh must show the edited text.
 */
@RunWith(AndroidJUnit4::class)
class ReaderExternalEditTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun externalEditThenRefreshShowsNewContentAndReplansPosition() {
        val dbName = "r3-edit.db"
        context.deleteDatabase(dbName)
        val access = SwitchableBytesAccess()
        val db =
            Room.databaseBuilder(context, LeafletDatabase::class.java, dbName)
                .allowMainThreadQueries()
                .build()
        try {
            val documentId = "r3-edit-doc"
            val uri = "content://r3.edit.tree/$documentId.md"
            access.put(uri, "# Anchor Heading\n\nAlpha\n\nBeta\n\nGamma\n\nDelta\n".toByteArray())
            val repo = LibraryRepositoryImpl(db, access, Dispatchers.IO)
            val source = DocumentSourceImpl(db.documentDao(), access)
            val gateway = StorageReaderGateway(db.documentDao(), source)
            val parser = LeafletDocumentParser.create(context)
            runBlocking {
                val library = repo.addLibrary("Edit Library", "content://r3.edit.tree")
                db.documentDao().upsert(
                    DocumentEntity(
                        documentId = documentId,
                        libraryId = library.id.value,
                        relativePath = "edit.md",
                        uri = uri,
                        displayName = "edit.md",
                        title = null,
                        extension = "md",
                        size = access.sizeOf(uri).toLong(),
                        lastModified = 1L,
                        contentHash = null,
                    ),
                )
                // Position saved against a DIFFERENT fingerprint (stale).
                repo.savePosition(
                    ReadingPosition(
                        documentId = DocumentId(documentId),
                        version = 1,
                        blockIndex = 3,
                        blockOffset = 0,
                        progress = 0.5f,
                        anchor = "Anchor Heading",
                    ),
                    lastModified = 999L,
                    size = 999L,
                )
            }
            val viewModel =
                ReaderViewModel(
                    store = ReaderWiring(gateway, parser, repo, source).readerStore(),
                    parser = parser,
                    ioDispatcher = Dispatchers.IO,
                    clock = System::currentTimeMillis,
                )
            compose.setContent { ReaderScreenShim(documentId = documentId, vm = viewModel) }
            compose.waitUntil(timeoutMillis = 20_000) { viewModel.ui.value is ReaderUiState.Content }
            val stale = viewModel.ui.value as ReaderUiState.Content
            assertEquals("Anchor Heading", stale.document.title)
            assertTrue("stale notice shown", stale.staleNotice != null)
            assertEquals("anchor fallback lands on the heading", 0, stale.restoreBlock)

            // External edit (new size ⇒ new fingerprint), then an explicit refresh.
            access.put(uri, "# Anchor Heading\n\nAlpha\n\nBeta edited\n\nGamma\n\nDelta\n".toByteArray())
            compose.runOnIdle { viewModel.refresh(DocumentId(documentId)) }
            compose.waitUntil(timeoutMillis = 20_000) {
                (viewModel.ui.value as? ReaderUiState.Content)
                    ?.document?.blockTexts?.any { it.contains("Beta edited") } == true
            }
            assertTrue(
                "edited text is on the page",
                PageViews.viewTexts(compose).any { it.contains("Beta edited") },
            )
        } finally {
            db.close()
        }
    }
}

/** `ContentAccess` whose bytes can be replaced (models an external edit). */
private class SwitchableBytesAccess : ContentAccess {
    private val contents = LinkedHashMap<String, ByteArray>()

    fun put(
        uri: String,
        bytes: ByteArray,
    ) {
        contents[uri] = bytes
    }

    fun sizeOf(uri: String): Int = contents[uri]?.size ?: 0

    override fun openInput(uriString: String): InputStream {
        val bytes = contents[uriString] ?: throw java.io.FileNotFoundException("gone: $uriString")
        return ByteArrayInputStream(bytes)
    }

    override fun persistedReadUris(): Set<String> = contents.keys

    override fun takeReadPermission(treeUriString: String) = Unit

    override fun releaseReadPermission(treeUriString: String) = Unit
}

/** No-op access for the settings/position phases (no document is ever read). */
private object NoopContentAccess : ContentAccess {
    override fun openInput(uriString: String): InputStream? = null

    override fun persistedReadUris(): Set<String> = emptySet()

    override fun takeReadPermission(treeUriString: String) = Unit

    override fun releaseReadPermission(treeUriString: String) = Unit
}
