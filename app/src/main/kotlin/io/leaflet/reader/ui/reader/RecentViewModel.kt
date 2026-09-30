package io.leaflet.reader.ui.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.leaflet.model.LibraryId
import io.leaflet.model.RecentRead
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
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

    val rows: StateFlow<List<RecentRow>> =
        _entries.map { list ->
            list.map { entry ->
                RecentRow(
                    entry = entry,
                    libraryName = nameCache[entry.libraryId.value],
                    health = healthCache[entry.documentId.value] ?: RecentHealth.UNKNOWN,
                )
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val nameCache = mutableMapOf<String, String?>()
    private val healthCache = mutableMapOf<String, RecentHealth>()

    init {
        viewModelScope.launch {
            try {
                store.observeRecent().collect { list ->
                    _entries.value = list
                    refreshMeta(list)
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
                healthCache.remove(documentId.value)
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
                healthCache.clear()
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

    private suspend fun refreshMeta(list: List<RecentRead>) {
        for (entry in list) {
            val libKey = entry.libraryId.value
            if (!nameCache.containsKey(libKey)) {
                nameCache[libKey] =
                    try {
                        store.resolveLibraryName(entry.libraryId) ?: libraryNames(entry.libraryId)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        null
                    }
            }
            val docKey = entry.documentId.value
            if (healthCache[docKey] == null) {
                healthCache[docKey] =
                    try {
                        store.checkHealth(entry)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        RecentHealth.UNKNOWN
                    }
            }
        }
    }
}
