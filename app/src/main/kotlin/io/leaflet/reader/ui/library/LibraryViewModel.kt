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
    val loading: Boolean = false,
    val loadFailed: Boolean = false,
)

/**
 * Single-library browse state (REQ-0001 A1/UX-5): documents for one library,
 * the relative paths of its indexed directories (LT-3), the relative prefix
 * of the directory currently open, refresh progress.
 *
 * There is no separate `notice` here: every recoverable browse message is
 * carried by [progress] and rendered inside the reserved status region
 * (REQ-0012 FR-4), which is what keeps the list geometry stable.
 */
data class LibraryBrowseUiState(
    val libraryId: String = "",
    val displayName: String = "",
    val documents: List<Document> = emptyList(),
    val directories: List<String> = emptyList(),
    val prefix: String = "",
    val progress: ScanProgress = ScanProgress(""),
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
    private val _list = MutableStateFlow(LibraryListUiState(loading = true))
    val list: StateFlow<LibraryListUiState> = _list.asStateFlow()

    private val _browse = MutableStateFlow(LibraryBrowseUiState())
    val browse: StateFlow<LibraryBrowseUiState> = _browse.asStateFlow()

    private var refreshJob: Job? = null
    private var progressJob: Job? = null

    /**
     * Browse ownership (CR-20261001-06): `openLibrary` bumps a generation and
     * keeps its load job, so a slower earlier load can neither be published over
     * a later selection nor replace the progress subscription of the library the
     * user actually opened. [refresh] completions check the same selection.
     */
    private var browseGeneration = 0L
    private var browseLoadJob: Job? = null

    /** True while [libraryId] is still the selected browse library. */
    private fun isCurrentBrowse(libraryId: LibraryId): Boolean = _browse.value.libraryId == libraryId.value

    private var libraryListJob: Job? = null
    private val scanStateJobs = mutableMapOf<String, Job>()

    init {
        retryLibraries()
    }

    /** Keep first-use guidance separate from an index still loading or failing. */
    fun retryLibraries() {
        libraryListJob?.cancel()
        scanStateJobs.values.forEach { it.cancel() }
        scanStateJobs.clear()
        _list.update { it.copy(loading = true, loadFailed = false) }
        libraryListJob =
            viewModelScope.launch {
                try {
                    libraries.observeLibraries().collect { rows ->
                        val ids = rows.mapTo(mutableSetOf()) { it.id.value }
                        scanStateJobs.keys.filterNot { it in ids }.forEach { id -> scanStateJobs.remove(id)?.cancel() }
                        _list.update {
                            it.copy(
                                libraries = rows, loading = false, loadFailed = false,
                                scanStates =
                                    it.scanStates.filterKeys {
                                            key ->
                                        key in ids
                                    },
                            )
                        }
                        for (library in rows) {
                            if (scanStateJobs[library.id.value]?.isActive == true) continue
                            scanStateJobs[library.id.value] =
                                launch {
                                    try {
                                        libraries.observeScanState(library.id).collect { state ->
                                            _list.update { current ->
                                                if (current.libraries.any { it.id == library.id }) {
                                                    current.copy(scanStates = current.scanStates + (library.id.value to state))
                                                } else {
                                                    current
                                                }
                                            }
                                        }
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (_: Exception) {
                                        _list.update { current ->
                                            if (current.libraries.any { it.id == library.id }) {
                                                current.copy(scanStates = current.scanStates + (library.id.value to ScanState.FAILED))
                                            } else {
                                                current
                                            }
                                        }
                                    }
                                }
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    _list.update { it.copy(loading = false, loadFailed = true) }
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
    }

    /** Grant persistence failed before the repository ran (R2-05): same notice, no row. */
    fun onTreeGrantFailed() {
        _list.update {
            it.copy(notice = "无法添加该目录（授权未授予或目录不可读），原文不受影响")
        }
    }

    /** Loads one library's Room-backed document list into [browse]. */
    fun openLibrary(libraryId: LibraryId) {
        // A newer selection supersedes both the load and any scan refresh that is
        // still running for the previous library (CR-20261001-06).
        browseLoadJob?.cancel()
        refreshJob?.cancel()
        val generation = ++browseGeneration
        browseLoadJob =
            viewModelScope.launch {
                val detail = scanner?.libraryDetail(libraryId)
                val docs = libraries.observeDocumentsSnapshot(libraryId)
                val dirs = libraries.observeDirectoriesSnapshot(libraryId)
                // The user may have selected another library while this one was
                // loading: its rows and path state must not replace that selection,
                // and its progress must not replace the newer subscription.
                if (generation != browseGeneration) return@launch
                _browse.update {
                    it.copy(
                        libraryId = libraryId.value,
                        displayName = detail?.displayName ?: it.displayName,
                        documents = docs,
                        directories = dirs,
                        prefix = "",
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

    /**
     * Manual refresh (REQ-0001 FR-6/UX-5): serialized per library, cancellable.
     *
     * Failure and cancellation are reported through [LibraryBrowseUiState.progress]
     * only: the browse screen's reserved status region renders that one value, so
     * the recoverable text can never insert a second block above the file list
     * (REQ-0012 FR-4/AC-4).
     */
    fun refresh(libraryId: LibraryId) {
        val activeScanner = scanner ?: return
        refreshJob?.cancel()
        refreshJob =
            viewModelScope.launch {
                // CR-20261001-06: every publication below is owned by the library
                // that was selected when the refresh started. A refresh completion
                // that arrives after the user opened another library must not
                // replace its rows, path state, or progress.
                if (isCurrentBrowse(libraryId)) {
                    _browse.update {
                        it.copy(progress = ScanProgress(libraryId.value, ScanProgressState.SCANNING))
                    }
                }
                try {
                    activeScanner.scan(libraryId)
                    val detail = activeScanner.libraryDetail(libraryId)
                    val docs = libraries.observeDocumentsSnapshot(libraryId)
                    val dirs = libraries.observeDirectoriesSnapshot(libraryId)
                    if (isCurrentBrowse(libraryId)) {
                        _browse.update {
                            it.copy(
                                displayName = detail?.displayName ?: it.displayName,
                                documents = docs,
                                directories = dirs,
                            )
                        }
                    }
                } catch (e: CancellationException) {
                    if (isCurrentBrowse(libraryId)) {
                        _browse.update {
                            it.copy(
                                progress =
                                    ScanProgress(
                                        libraryId.value,
                                        ScanProgressState.CANCELLED,
                                        message = "已取消，显示上次完整结果",
                                    ),
                            )
                        }
                    }
                } catch (e: Exception) {
                    if (isCurrentBrowse(libraryId)) {
                        _browse.update {
                            it.copy(
                                progress =
                                    it.progress.copy(
                                        state = ScanProgressState.FAILED,
                                        message = "本次扫描未完成，已保留上次完整结果，未删除任何条目。",
                                    ),
                            )
                        }
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

    /**
     * Opens a breadcrumb entry (REQ-0012 FR-2): the Library root, or an
     * ancestor of the open directory at a segment boundary.
     *
     * The [request] carries the Library and prefix the entry was generated
     * from, and both must still be the current ones. A bare path is not
     * enough: the root prefix is valid in every Library, so a tap queued
     * behind a Library switch would otherwise move the newly opened Library to
     * its own root (NAV-ACC-01, reproduced by
     * `LibraryAncestorNavigationTest.aRootTargetFromAReplacedLibraryIsIgnored`).
     *
     * A sibling, a tree escape, an externally supplied path, a deeper-than-open
     * prefix, a target from another Library, and the current directory are all
     * ignored without changing state (AC-2). No SAF query or file read happens
     * here.
     */
    fun openAncestor(request: AncestorRequest) {
        val current = _browse.value
        if (current.libraryId != request.libraryId || current.prefix != request.prefix) return
        if (!isAncestorTarget(current.prefix, request.target)) return
        val normalized = normalizePrefix(request.target)
        if (normalized == normalizePrefix(current.prefix)) return
        _browse.update { state ->
            // Re-validate against the state being replaced: an asynchronous
            // Library switch or a Back may have landed since the tap.
            if (state.libraryId != request.libraryId || state.prefix != request.prefix) {
                state
            } else if (!isAncestorTarget(state.prefix, normalized)) {
                state
            } else {
                state.copy(prefix = normalized)
            }
        }
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

    /**
     * Cancels the in-flight refresh; the last complete index stays visible.
     *
     * Only a scan that is still running may be cancelled. With no refresh job,
     * with a job that has already settled, or in a terminal state this is a
     * no-op: publishing CANCELLING for a scan that does not exist would leave a
     * "cancelling" display with no owner left to publish a terminal state
     * (NAV-ACC-02, reproduced by
     * `LibraryScanCancelTest.cancelWithoutAnActiveScanKeepsTheCurrentState`).
     */
    fun cancelRefresh() {
        val job = refreshJob ?: return
        if (!job.isActive) return
        val state = _browse.value.progress.state
        if (state != ScanProgressState.SCANNING && state != ScanProgressState.CANCELLING) return
        job.cancel()
        _browse.update {
            if (it.progress.state == ScanProgressState.SCANNING) {
                it.copy(progress = it.progress.copy(state = ScanProgressState.CANCELLING))
            } else {
                it
            }
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
