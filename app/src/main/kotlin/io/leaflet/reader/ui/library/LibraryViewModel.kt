package io.leaflet.reader.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.leaflet.model.Document
import io.leaflet.model.Library
import io.leaflet.model.LibraryId
import io.leaflet.storage.ScanState
import io.leaflet.storage.scan.LibraryScanner
import io.leaflet.storage.scan.ScanProgress
import io.leaflet.storage.scan.ScanProgressState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Library list state (REQ-0001 UX-1/UX-2): empty vs populated, duplicate-add
 * notice, picker/grant failure notice. Static text only — no loading
 * animation (E-Ink constraint).
 */
data class PendingLibraryRemoval(
    val id: String,
    val displayName: String,
)

data class LibraryListUiState(
    val libraries: List<Library> = emptyList(),
    val scanStates: Map<String, ScanState> = emptyMap(),
    val notice: String? = null,
    val pendingRemoval: PendingLibraryRemoval? = null,
)

/**
 * Single-library browse state (REQ-0001 A1/UX-5): documents for one library,
 * the relative paths of its indexed directories (LT-3), the relative prefix
 * of the directory currently open, refresh progress.
 */
data class LibraryBrowseUiState(
    val libraryId: String = "",
    val displayName: String = "",
    val documents: List<Document> = emptyList(),
    val directories: List<String> = emptyList(),
    val prefix: String = "",
    val progress: ScanProgress = ScanProgress(""),
    val notice: String? = null,
)

/**
 * Library ViewModel (T05): owns add-via-picker-result, manual refresh with
 * cancel, and Room-backed browse lists. Navigation target contract for T07:
 * document rows expose [onOpenDocument] via the screens — T07 binds the
 * Reader route without touching this ViewModel's state shapes.
 *
 * No background file watching, no full-library search (REQ-0001 Non-goals).
 * UI callbacks are plain function parameters so the ViewModel stays
 * JVM-testable without Compose or an Activity.
 */
class LibraryViewModel(
    private val libraries: LibraryListSource,
    private val scanner: LibraryScanner?,
) : ViewModel() {
    private val _list = MutableStateFlow(LibraryListUiState())
    val list: StateFlow<LibraryListUiState> = _list.asStateFlow()

    private val _browse = MutableStateFlow(LibraryBrowseUiState())
    val browse: StateFlow<LibraryBrowseUiState> = _browse.asStateFlow()

    private var refreshJob: Job? = null
    private var progressJob: Job? = null

    init {
        viewModelScope.launch {
            libraries.observeLibraries().collect { rows ->
                _list.update { it.copy(libraries = rows) }
                for (lib in rows) {
                    launch {
                        libraries.observeScanState(lib.id).collect { state ->
                            _list.update { cur ->
                                cur.copy(scanStates = cur.scanStates + (lib.id.value to state))
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Handles the `ACTION_OPEN_DOCUMENT_TREE` result: persists the grant and
     * inserts the row through [LibraryListSource.addLibrary] (which dedupes
     * on the normalized tree URI — a repeat pick returns the existing
     * library with a "已存在" notice instead of a second row, REQ-0001
     * FR-1/AC-2), then runs the first metadata-only scan.
     */
    fun onTreePicked(
        treeUri: String,
        displayName: String?,
    ) {
        viewModelScope.launch {
            try {
                val existingIds = _list.value.libraries.map { it.id.value }.toSet()
                val lib = libraries.addLibrary(displayName ?: "", treeUri)
                if (lib.id.value in existingIds) {
                    _list.update { it.copy(notice = "该目录已在资料库中（已定位到“${lib.displayName}”）") }
                } else {
                    _list.update { it.copy(notice = null) }
                }
                openLibrary(lib.id)
                refresh(lib.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _list.update {
                    it.copy(notice = "无法添加该目录（授权未授予或目录不可读），原文不受影响")
                }
            }
        }
    }

    fun dismissNotice() {
        _list.update { it.copy(notice = null) }
        _browse.update { it.copy(notice = null) }
    }

    /** Grant persistence failed before the repository ran (R2-05): same notice, no row. */
    fun onTreeGrantFailed() {
        _list.update {
            it.copy(notice = "无法添加该目录（授权未授予或目录不可读），原文不受影响")
        }
    }

    /** Loads one library's Room-backed document list into [browse]. */
    fun openLibrary(libraryId: LibraryId) {
        viewModelScope.launch {
            val detail = scanner?.libraryDetail(libraryId)
            val docs = libraries.observeDocumentsSnapshot(libraryId)
            val dirs = libraries.observeDirectoriesSnapshot(libraryId)
            _browse.update {
                it.copy(
                    libraryId = libraryId.value,
                    displayName = detail?.displayName ?: it.displayName,
                    documents = docs,
                    directories = dirs,
                    prefix = "",
                    notice = null,
                )
            }
            progressJob?.cancel()
            val progressScanner = scanner ?: return@launch
            progressJob =
                launch {
                    progressScanner.observeProgress(libraryId).collect { progress ->
                        _browse.update { cur -> cur.copy(progress = progress) }
                    }
                }
        }
    }

    /** Manual refresh (REQ-0001 FR-6/UX-5): serialized per library, cancellable. */
    fun refresh(libraryId: LibraryId) {
        val activeScanner = scanner ?: return
        refreshJob?.cancel()
        refreshJob =
            viewModelScope.launch {
                _browse.update {
                    it.copy(
                        progress = ScanProgress(libraryId.value, ScanProgressState.SCANNING),
                        notice = null,
                    )
                }
                try {
                    activeScanner.scan(libraryId)
                    val detail = activeScanner.libraryDetail(libraryId)
                    val docs = libraries.observeDocumentsSnapshot(libraryId)
                    val dirs = libraries.observeDirectoriesSnapshot(libraryId)
                    _browse.update {
                        it.copy(
                            displayName = detail?.displayName ?: it.displayName,
                            documents = docs,
                            directories = dirs,
                            notice = null,
                        )
                    }
                } catch (e: CancellationException) {
                    _browse.update {
                        it.copy(
                            progress =
                                ScanProgress(
                                    libraryId.value,
                                    ScanProgressState.CANCELLED,
                                    message = "已取消，显示上次完整结果",
                                ),
                            notice = "已取消，显示上次完整结果",
                        )
                    }
                } catch (e: Exception) {
                    _browse.update {
                        it.copy(notice = "本次扫描未完成，已保留上次完整结果，未删除任何条目。[重试]")
                    }
                }
            }
    }

    /** Opens the direct child directory [name] under the current prefix. */
    fun enterDirectory(name: String) {
        if (name.isBlank()) return
        _browse.update { it.copy(prefix = childPrefix(it.prefix, name)) }
    }

    /**
     * Moves up one directory. Returns false at the library root so the
     * caller can leave the browse screen.
     */
    fun leaveDirectory(): Boolean {
        val parent = parentPrefix(_browse.value.prefix) ?: return false
        _browse.update { it.copy(prefix = parent) }
        return true
    }

    /** Asks to remove one Library. The row stays until [confirmRemove]. */
    fun requestRemove(libraryId: LibraryId) {
        val name = _list.value.libraries.firstOrNull { it.id == libraryId }?.displayName ?: return
        _list.update { it.copy(pendingRemoval = PendingLibraryRemoval(libraryId.value, name)) }
    }

    fun dismissRemove() {
        _list.update { it.copy(pendingRemoval = null) }
    }

    /** Deletes the local index for the pending Library. Original files stay. */
    fun confirmRemove() {
        val pending = _list.value.pendingRemoval ?: return
        viewModelScope.launch {
            try {
                libraries.removeLibrary(LibraryId(pending.id))
                _list.update { current ->
                    if (current.pendingRemoval?.id == pending.id) {
                        current.copy(pendingRemoval = null)
                    } else {
                        current
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _list.update {
                    it.copy(
                        pendingRemoval = null,
                        notice = "无法移除该资料库，原文不受影响",
                    )
                }
            }
        }
    }

    /** Cancels the in-flight refresh; the last complete index stays visible. */
    fun cancelRefresh() {
        refreshJob?.cancel()
        _browse.update {
            it.copy(
                progress = it.progress.copy(state = ScanProgressState.CANCELLING),
            )
        }
    }
}

/**
 * Narrow UI-facing source: add/list/observe plus a one-shot document
 * snapshot. Production binds this to `LibraryRepositoryImpl`
 * (+ `observeDocuments` first emission); tests bind a fake.
 */
interface LibraryListSource {
    suspend fun addLibrary(
        displayName: String,
        rootTreeUri: String,
    ): Library

    suspend fun observeDocumentsSnapshot(libraryId: LibraryId): List<Document>

    /**
     * Relative paths of the library's indexed directories (LT-3/REQ-0001 A1).
     * Path-only metadata; a folder with no indexed document is included so the
     * browse level can show it.
     */
    suspend fun observeDirectoriesSnapshot(libraryId: LibraryId): List<String>

    fun observeLibraries(): kotlinx.coroutines.flow.Flow<List<Library>>

    fun observeScanState(libraryId: LibraryId): kotlinx.coroutines.flow.Flow<ScanState>

    suspend fun removeLibrary(libraryId: LibraryId)
}
