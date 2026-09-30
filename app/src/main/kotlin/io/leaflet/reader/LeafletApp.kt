package io.leaflet.reader

import android.app.Application
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import io.leaflet.eink.AppCapabilities
import io.leaflet.eink.DisplayModeId
import io.leaflet.eink.EInkController
import io.leaflet.eink.capabilitiesForFlavor
import io.leaflet.eink.controllerForMode
import io.leaflet.model.DisplayMode
import io.leaflet.model.Document
import io.leaflet.model.Library
import io.leaflet.model.LibraryId
import io.leaflet.reader.ui.library.LibraryListSource
import io.leaflet.reader.ui.library.LibraryViewModel
import io.leaflet.storage.ScanState
import io.leaflet.storage.db.LeafletDatabase
import io.leaflet.storage.repo.LibraryRepositoryImpl
import io.leaflet.storage.saf.ContentResolverAccess
import io.leaflet.storage.scan.ContentResolverTreeLister
import io.leaflet.storage.scan.LibraryScanner
import kotlinx.coroutines.flow.Flow

private val Application.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * Manual DI container (MVP, no Hilt/Koin per architecture overview §2).
 * Owns DataStore, DisplayMode repository, capabilities, EInkController, and
 * (T05) the Room database + repository + scanner + library ViewModel.
 */
class AppContainer(app: Application, flavor: WidgetFlavor) {
    private val appContext: Application = app

    val settingsStore: SettingsStore =
        SettingsStore(
            dataStore = app.settingsDataStore,
            defaultMode = flavor.defaultDisplayMode,
        )

    // T12: capabilities resolve from the flavor name via the `:core:eink`
    // seam (REQ-0004 FR-5); DisplayProfile/MotionPolicy come from the stored
    // mode via profileFor/MotionPolicy.from — never a local motion branch.
    val capabilities: AppCapabilities = capabilitiesForFlavor(flavor.name.lowercase())

    /**
     * E-Ink path uses the generic system-refresh adapter (system redraw
     * semantics only — never hardware waveforms); Standard is a no-op.
     */
    fun einkControllerFor(mode: DisplayMode): EInkController =
        controllerForMode(
            if (mode == DisplayMode.EINK) DisplayModeId.EINK else DisplayModeId.STANDARD,
        )

    private val database: LeafletDatabase by lazy {
        Room.databaseBuilder(appContext, LeafletDatabase::class.java, "leaflet.db")
            // Lossless, additive migrations only (never destructive): the
            // v1 -> v2 directory table keeps every existing index row.
            .addMigrations(LeafletDatabase.MIGRATION_1_2)
            .build()
    }

    private val repository: LibraryRepositoryImpl by lazy {
        LibraryRepositoryImpl(
            db = database,
            access = ContentResolverAccess(appContext.contentResolver),
        )
    }

    private val documentSource: io.leaflet.storage.saf.DocumentSourceImpl by lazy {
        io.leaflet.storage.saf.DocumentSourceImpl(
            documentDao = database.documentDao(),
            access = ContentResolverAccess(appContext.contentResolver),
        )
    }

    /** In-text image loader (T10, REQ-0009 FR-6–FR-9): SAF tree walk + sampled decode. */
    val imageLoader: io.leaflet.reader.ui.reader.ImageLoader by lazy {
        io.leaflet.reader.ui.reader.LibraryImageLoader(
            documents = database.documentDao(),
            libraries = database.libraryDao(),
            lister = ContentResolverTreeLister(appContext.contentResolver),
            contentResolver = appContext.contentResolver,
        )
    }

    /** Reader gateway over storage (T07): read-only streams, caller closes. */
    private val readerGateway: io.leaflet.reader.ReaderDocumentGateway by lazy {
        StorageReaderGateway(
            documents = database.documentDao(),
            source = documentSource,
        )
    }

    val documentParser: io.leaflet.markdown.LeafletDocumentParser by lazy {
        io.leaflet.markdown.LeafletDocumentParser.create(appContext)
    }

    /** Reader wiring bound once here so flavor dirs never duplicate logic. */
    val readerWiring: ReaderWiring by lazy {
        ReaderWiring(
            gateway = readerGateway,
            parser = documentParser,
            repository = repository,
            source = documentSource,
        )
    }

    private val scanner: LibraryScanner by lazy {
        LibraryScanner(
            db = database,
            lister = ContentResolverTreeLister(appContext.contentResolver),
            repository = repository,
        )
    }

    private val librarySource: LibraryListSource by lazy {
        object : LibraryListSource {
            override suspend fun addLibrary(
                displayName: String,
                rootTreeUri: String,
            ): Library =
                repository.addLibrary(
                    displayName.ifBlank {
                        io.leaflet.storage.scan.defaultDisplayNameForTree(rootTreeUri)
                    },
                    rootTreeUri,
                )

            override suspend fun observeDocumentsSnapshot(libraryId: LibraryId): List<Document> = repository.listDocuments(libraryId)

            override suspend fun observeDirectoriesSnapshot(libraryId: LibraryId): List<String> = repository.listDirectories(libraryId)

            override fun observeLibraries(): Flow<List<Library>> = repository.observeLibraries()

            override fun observeScanState(libraryId: LibraryId): Flow<ScanState> = repository.observeScanState(libraryId)

            override suspend fun removeLibrary(libraryId: LibraryId) {
                repository.removeLibrary(libraryId)
            }
        }
    }

    val libraryViewModel: LibraryViewModel by lazy {
        LibraryViewModel(libraries = librarySource, scanner = scanner)
    }
}

/** Build-flavor identity, resolved once at startup from BuildConfig. */
enum class WidgetFlavor(val defaultDisplayMode: DisplayMode) {
    FULL(DisplayMode.STANDARD),
    EINK(DisplayMode.EINK),
}

class LeafletApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        val flavor = if (BuildConfig.FLAVOR == "eink") WidgetFlavor.EINK else WidgetFlavor.FULL
        container = AppContainer(this, flavor)
    }
}
