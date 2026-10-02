package io.leaflet.reader.ui.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.leaflet.model.LibraryId
import io.leaflet.model.RecentRead
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * One recent-read row for the Library screen (T07, REQ-0003 UX-3/UX-4).
 *
 * @property entry the stored entry (title snapshot + library + timestamp).
 * @property libraryName owning library display name, or null when unknown.
 * @property health liveness of the backing file, resolved lazily by the VM.
 */
data class RecentRow(
    val entry: RecentRead,
    val libraryName: String?,
    val health: RecentHealth,
)

enum class RecentHealth {
    UNKNOWN,
    OK,
    GONE,
    AUTH_LOST,
}

/** Narrow recent-read surface: observe + user-confirmed removal (never auto-wipe). */
interface RecentStore {
    fun observeRecent(limit: Int = RECENT_CAP): kotlinx.coroutines.flow.Flow<List<RecentRead>>

    suspend fun resolveLibraryName(libraryId: LibraryId): String?

    suspend fun checkHealth(entry: RecentRead): RecentHealth

    suspend fun removeRecent(documentId: io.leaflet.model.DocumentId)

    suspend fun clearRecent()

    companion object {
        const val RECENT_CAP = 30
    }
}

/**
 * Recent-reads ViewModel (T07): library-screen section (title snapshot +
 * time + library + health hint), single-item removal, clear-all.
 *
 * Bad entries are NEVER auto-deleted (REQ-0003 FR-6): a gone/unauthorized
 * file keeps its row with a prompt; removal happens only via
 * [removeEntry] (explicit user action, e.g. the "[移除该记录]" button on the
 * row or on the Reader failed screen).
 */
class RecentViewModel(
    private val store: RecentStore,
    private val libraryNames: suspend (LibraryId) -> String? = { null },
) : ViewModel() {
    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice

    private val _entries = MutableStateFlow<List<RecentRead>>(emptyList())
    val entries: StateFlow<List<RecentRead>> = _entries

    /**
     * Lazy per-row metadata (library name + file health), held as IMMUTABLE
     * observable state so [rows] is recomputed when a lookup finishes
     * (CR-20261001-07). Plain mutable maps made a completed lookup invisible:
     * the list kept showing an unknown library and omitted the
     * authorization-lost/missing-file hint until some unrelated entry change
     * happened to rebuild the rows.
     */
    private data class RecentMeta(
        /** A present key means "lookup attempted"; a null value resolved to nothing. */
        val names: Map<String, String?> = emptyMap(),
        val health: Map<String, RecentHealth> = emptyMap(),
    )

    private val metaState = MutableStateFlow(RecentMeta())

    val rows: StateFlow<List<RecentRow>> =
        combine(_entries, metaState) { list, meta ->
            list.map { entry ->
                RecentRow(
                    entry = entry,
                    libraryName = meta.names[entry.libraryId.value],
                    health = meta.health[entry.documentId.value] ?: RecentHealth.UNKNOWN,
                )
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** In-flight metadata lookups for the current entry list. */
    private var metaJob: Job? = null

    init {
        viewModelScope.launch {
            try {
                store.observeRecent().collect { list ->
                    // Publish the list immediately: metadata lookups can be slow
                    // (provider health probes) and the entry stream must not wait
                    // for them (CR-20261001-07).
                    _entries.value = list
                    // Cache invalidation: metadata is kept only for entries that
                    // are still listed, so a removed document cannot keep a stale
                    // hint and a re-added one is looked up again.
                    pruneMeta(list)
                    // A newer list supersedes the previous lookups, so a result
                    // that arrives after its entry changed cannot be applied.
                    metaJob?.cancel()
                    metaJob = launch { refreshMeta(list) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _notice.value = "最近阅读加载失败"
            }
        }
    }

    fun removeEntry(documentId: io.leaflet.model.DocumentId) {
        viewModelScope.launch {
            try {
                store.removeRecent(documentId)
                metaState.value = metaState.value.copy(health = metaState.value.health - documentId.value)
                _entries.value = _entries.value.filterNot { it.documentId == documentId }
                _notice.value = "已移除该记录"
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _notice.value = "移除失败，请重试"
            }
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            try {
                store.clearRecent()
                metaState.value = RecentMeta()
                _entries.value = emptyList()
                _notice.value = "已清空最近阅读"
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _notice.value = "清空失败，请重试"
            }
        }
    }

    fun dismissNotice() {
        _notice.value = null
    }

    /** Drops cached metadata for keys no longer present in [list]. */
    private fun pruneMeta(list: List<RecentRead>) {
        val libs = list.mapTo(mutableSetOf()) { it.libraryId.value }
        val docs = list.mapTo(mutableSetOf()) { it.documentId.value }
        val current = metaState.value
        val names = current.names.filterKeys { it in libs }
        val health = current.health.filterKeys { it in docs }
        if (names.size != current.names.size || health.size != current.health.size) {
            metaState.value = RecentMeta(names, health)
        }
    }

    private suspend fun refreshMeta(list: List<RecentRead>) {
        for (entry in list) {
            val libKey = entry.libraryId.value
            val docKey = entry.documentId.value
            if (!metaState.value.names.containsKey(libKey)) {
                val resolved =
                    try {
                        store.resolveLibraryName(entry.libraryId) ?: libraryNames(entry.libraryId)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        null
                    }
                publishName(libKey, resolved)
            }
            if (metaState.value.health[docKey] == null) {
                val health =
                    try {
                        store.checkHealth(entry)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        RecentHealth.UNKNOWN
                    }
                publishHealth(docKey, health)
            }
        }
    }

    /** Publishes a name lookup, but only while its library is still listed. */
    private fun publishName(
        libraryKey: String,
        value: String?,
    ) {
        if (_entries.value.none { it.libraryId.value == libraryKey }) return
        metaState.value = metaState.value.let { it.copy(names = it.names + (libraryKey to value)) }
    }

    /** Publishes a health lookup, but only while its document is still listed. */
    private fun publishHealth(
        documentKey: String,
        value: RecentHealth,
    ) {
        if (_entries.value.none { it.documentId.value == documentKey }) return
        metaState.value = metaState.value.let { it.copy(health = it.health + (documentKey to value)) }
    }
}
