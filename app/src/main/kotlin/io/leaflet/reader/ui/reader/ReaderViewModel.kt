package io.leaflet.reader.ui.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.leaflet.markdown.ParsedDocument
import io.leaflet.model.DocumentId
import io.leaflet.model.ReadingPosition
import io.leaflet.model.RecentRead
import io.leaflet.reader.DocumentDescriptor
import io.leaflet.reader.DocumentSearch
import io.leaflet.reader.LoadedDocument
import io.leaflet.reader.PositionRestore
import io.leaflet.reader.PositionThrottler
import io.leaflet.reader.ReaderDocumentGateway
import io.leaflet.reader.ReaderFailure
import io.leaflet.reader.ReaderSession
import io.leaflet.reader.ReaderViewState
import io.leaflet.reader.SearchUiState
import io.leaflet.reader.anchorForBlock
import io.leaflet.reader.locateAnchorInDoc
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Reader screen state (T07): the T06 tri-state plus the restore address and
 * the one-shot stale-content notice (REQ-0003 UX-2/AC-3).
 */
sealed interface ReaderUiState {
    data object Loading : ReaderUiState

    data class Content(
        val document: ParsedDocument,
        /** Block to scroll to after bind (instant scroll; E-Ink rule). */
        val restoreBlock: Int,
        val degradedNotice: String?,
        /** Shown once when exact restore was lost (content changed). */
        val staleNotice: String?,
        /** One-shot link/image notice (T10: missing/escaped/unsupported — static text, E-Ink safe). */
        val linkNotice: String? = null,
    ) : ReaderUiState

    data class Failed(
        val reason: ReaderFailure,
        val message: String,
    ) : ReaderUiState
}

/**
 * Narrow storage surface consumed by [ReaderViewModel].
 *
 * Production binds this to `:core:storage` ([LibraryRepositoryImpl] for
 * positions/recents + `DocumentSourceImpl` for streams); JVM tests bind a
 * fake. The ViewModel never imports Room, SAF, or `ContentResolver`.
 */
interface ReaderStore {
    suspend fun openStream(documentId: DocumentId): java.io.InputStream

    suspend fun describe(documentId: DocumentId): DocumentDescriptor?

    /**
     * Reachability probe for the same-document freshness check (R2 DEF-005):
     * opens the document and closes it again, so a revoked grant or a vanished
     * file is detected EVEN WHEN the index row — and therefore its fingerprint —
     * is unchanged. Returns null when the document is readable, otherwise the
     * failure the reader must surface.
     */
    suspend fun probeAccess(documentId: DocumentId): ReaderFailure? = null

    /** In-tree relative resolution only (DocumentSource rules; null = unreachable, never throws for policy). */
    suspend fun resolveRelative(
        fromId: DocumentId,
        ref: String,
    ): io.leaflet.storage.ResolvedResource?

    suspend fun loadPosition(documentId: DocumentId): StoredPosition?

    suspend fun savePosition(
        position: ReadingPosition,
        lastModified: Long,
        size: Long,
    )

    suspend fun recordRecent(entry: RecentRead)

    fun clockMs(): Long = System.currentTimeMillis()
}

/** One-shot T10 notices (static text; copy frozen here so tests pin exact strings). */
internal enum class LinkNoticeKind {
    UNREACHABLE,
    UNKNOWN_ANCHOR,
    UNSUPPORTED_MEDIA,
    UNSUPPORTED_SCHEME,
    NO_EXTERNAL_APP,
}

internal val T10_NOTICES: Map<LinkNoticeKind, String> =
    mapOf(
        LinkNoticeKind.UNREACHABLE to "目标不存在或超出资料库范围",
        LinkNoticeKind.UNKNOWN_ANCHOR to "本文中没有该锚点，位置保持不变",
        LinkNoticeKind.UNSUPPORTED_MEDIA to "该附件暂不支持在阅读器内打开",
        LinkNoticeKind.UNSUPPORTED_SCHEME to "不支持的链接类型，已忽略",
        LinkNoticeKind.NO_EXTERNAL_APP to "没有可打开该链接的应用",
    )

/** Position row plus the fingerprint observed at save time (storage-owned columns). */
data class StoredPosition(
    val position: ReadingPosition,
    val fingerprint: String?,
)

/**
 * Reader ViewModel (T07): binds [ReaderSession] to the Compose screen.
 *
 * Owns: open → restore → track → throttled-save → pause/exit flush, plus the
 * successful-open recent-write (REQ-0003 FR-2/FR-3/FR-5). Process-rebuild
 * recovery falls out of the same path: the constructor takes no in-memory
 * position, so a recreated ViewModel reloads the persisted row and replans
 * the restore — nothing is kept only in RAM.
 *
 * Failure mapping (T07 recovery actions): a `null` describe or a vanished
 * file becomes `FILE_MISSING` with the removed/re-authorize prompt
 * ("[移除该记录]" handled by the recent list, not by auto-deletion); a lost
 * grant (`SecurityException`) becomes `AUTHORIZATION_LOST` with the
 * re-authorize entry; anything else becomes `PARSE_FAILED` with retry.
 */
class ReaderViewModel(
    private val store: ReaderStore,
    val parser: io.leaflet.markdown.LeafletDocumentParser,
    private val ioDispatcher: kotlinx.coroutines.CoroutineDispatcher =
        kotlinx.coroutines.Dispatchers.IO,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val session =
        ReaderSession(
            gateway =
                object : ReaderDocumentGateway {
                    override suspend fun openStream(documentId: DocumentId) = store.openStream(documentId)

                    override suspend fun describe(documentId: DocumentId) = store.describe(documentId)
                },
            parser = parser,
            ioDispatcher = ioDispatcher,
            // R2-01: decode+parse off Main even though the scope is
            // Main-bound viewModelScope (session previously relaunched in
            // that scope and dropped the caller's IO context).
            parseDispatcher = ioDispatcher,
            scope = viewModelScope,
        )
    private val throttler = PositionThrottler(clock = clock)

    private val _ui = MutableStateFlow<ReaderUiState>(ReaderUiState.Loading)
    val ui: StateFlow<ReaderUiState> = _ui.asStateFlow()

    private var openJob: Job? = null
    private var currentId: DocumentId? = null
    private var currentDoc: ParsedDocument? = null
    private var currentDescriptor: DocumentDescriptor? = null

    /**
     * Document [currentDescriptor] describes (R2 DEF-012). A save must never
     * take the fingerprint of a descriptor that belongs to another document —
     * `currentDescriptor` used to be read unconditionally by [persist].
     */
    private var currentDescriptorId: DocumentId? = null
    private var currentStored: StoredPosition? = null
    private var lastVisibleBlock: Int = 0

    /** In-document search state (T11, REQ-0008): per-document, not persisted. */
    private val _search = MutableStateFlow(SearchUiState())
    val search: StateFlow<SearchUiState> = _search.asStateFlow()
    private var searchJob: Job? = null
    private var searchGeneration: Long = 0

    /**
     * Opens one document: loads (session, cancellable), plans the restore
     * (fingerprint-guarded), records the recent entry on success only.
     *
     * R2 DEF-005 — re-opening the SAME document is no longer an unconditional
     * no-op. A rotation rebuild re-runs the screen's `LaunchedEffect` with
     * Content already showing, so the ViewModel re-checks freshness cheaply:
     * it re-reads the index row and compares the fingerprint. Unchanged
     * content keeps the SAME [ParsedDocument] instance (no re-parse, position
     * untouched — REQ-0003 AC-7); a changed fingerprint, a vanished row or a
     * lost grant runs the full open path so the new content, the
     * missing-file prompt or the re-authorize prompt actually appears instead
     * of the stale cached view.
     */
    fun open(documentId: DocumentId) {
        if (currentId == documentId && _ui.value is ReaderUiState.Content && currentDoc != null) {
            checkFreshnessThenKeep(documentId)
            return
        }
        startOpen(documentId, minGeneration = session.currentGeneration())
    }

    /**
     * Freshness recheck for a same-document re-open (R2 DEF-005).
     *
     * The index row alone cannot prove freshness — it does not change when the
     * SAF grant is revoked or the file vanishes under it — so an unchanged
     * fingerprint is only accepted after [ReaderStore.probeAccess] opened the
     * real document. An UNKNOWN (null) fingerprint never proves freshness
     * either: null == null is not evidence that the file is the same one we
     * parsed. Heavy work stays on [ioDispatcher]; state writes stay on Main
     * (DEF-008).
     *
     * The full open is launched OUTSIDE this coroutine: [startOpen] cancels
     * the previous [openJob] first, and cancelling the job we are running in
     * would abort the reload it just started.
     */
    private fun checkFreshnessThenKeep(documentId: DocumentId) {
        val known = currentDescriptor
        // R2 DEF-013: do NOT emit Loading for a same-document recheck. Loading
        // removes ReaderContent from the composition, which disposes the
        // LazyListState; the remounted list then re-ran the one-shot restore
        // scroll to the OPEN-time `restoreBlock`, yanking a reader who had
        // scrolled on back to where the document was first opened. The content
        // now stays composed and the state is only replaced when the check
        // concludes that it must be (failure, or a real reload).
        openJob =
            // DEF-008: Compose-observed state (`ui`, `search`) MUST be written
            // on the main thread. A StateFlow write is a snapshot write; from a
            // background thread Compose's apply-notification machinery then
            // drives recomposition on that thread (no Looper → crash in the
            // Compose test harness; concurrent SlotTable mutation → crash on
            // dispose). Heavy work stays on [ioDispatcher] via `withContext`.
            viewModelScope.launch {
                val fresh = // null: keep, true: reload, false: surfaced a failure
                    try {
                        val live = withContext(ioDispatcher) { store.describe(documentId) }
                        val probed =
                            if (live != null && known?.fingerprint != null && known.fingerprint == live.fingerprint) {
                                withContext(ioDispatcher) { store.probeAccess(documentId) }
                            } else {
                                null
                            }
                        if (currentId != documentId) {
                            null
                        } else if (live != null && probed != null) {
                            // Row unchanged but the document is unreachable:
                            // the cached content must not mask the failure.
                            _ui.value =
                                ReaderUiState.Failed(
                                    probed,
                                    if (probed == ReaderFailure.AUTHORIZATION_LOST) "授权已失效" else "文档不可用",
                                )
                            false
                        } else if (live == null) {
                            _ui.value =
                                ReaderUiState.Failed(
                                    ReaderFailure.FILE_MISSING,
                                    "文档索引不存在（可能已被删除或移出资料库）",
                                )
                            false
                        } else if (
                            known?.fingerprint != null &&
                            known.fingerprint == live.fingerprint
                        ) {
                            // Same file AND still reachable: keep the content
                            // that is already on screen (no state write at all,
                            // so no recomposition/remount and no scroll reset).
                            null
                        } else {
                            true
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: SecurityException) {
                        if (currentId == documentId) {
                            _ui.value =
                                ReaderUiState.Failed(ReaderFailure.AUTHORIZATION_LOST, e.message ?: "授权已失效")
                        }
                        false
                    } catch (e: Exception) {
                        if (currentId == documentId) {
                            _ui.value =
                                ReaderUiState.Failed(ReaderFailure.FILE_MISSING, e.message ?: "文档不可用")
                        }
                        false
                    }
                if (fresh != true || currentId != documentId) return@launch
                // Drop the stale in-memory document, then run the normal open
                // path; its `minGeneration` is read from the session, so the
                // pre-reload cache entry can never pass as the fresh one.
                currentDoc = null
                currentDescriptor = null
                currentDescriptorId = null
                lastVisibleBlock = 0
                open(documentId)
            }
    }

    /**
     * Full open path shared by [open] and the freshness recheck: bumps the
     * search generation, resets per-document state, launches the session load
     * and awaits a result produced by a generation STRICTLY NEWER than
     * [minGeneration] (R2 DEF-005: the pre-reload cache entry must never be
     * mistaken for the reloaded document).
     */
    private fun startOpen(
        documentId: DocumentId,
        minGeneration: Long,
    ) {
        openJob?.cancel()
        cancelSearchLocked()
        throttler.reset()
        // R2 DEF-012: switching documents must not leave the PREVIOUS
        // document's snapshot (doc / descriptor / visible block) reachable
        // while the new document loads — `flushPosition` during Loading used
        // to persist the new id with the old document's address and
        // fingerprint. Same-document reloads keep the snapshot: the session
        // decides whether the cached parse is reusable, and `onLoaded`
        // replaces it.
        if (currentId != documentId) {
            currentDoc = null
            currentDescriptor = null
            currentDescriptorId = null
            currentStored = null
            lastVisibleBlock = 0
        }
        currentId = documentId
        _search.value = SearchUiState(generation = searchGeneration)
        _ui.value = ReaderUiState.Loading
        openJob =
            // DEF-008: state writes on Main (see checkFreshnessThenKeep).
            viewModelScope.launch {
                try {
                    val stored = withContext(ioDispatcher) { store.loadPosition(documentId) }
                    currentStored = stored
                    session.openDocument(documentId)
                    val loaded = awaitLoaded(documentId, minGeneration)
                    onLoaded(documentId, loaded, stored)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: ReaderBadEntryException) {
                    if (currentId == documentId) {
                        _ui.value = ReaderUiState.Failed(e.reason, e.message ?: e.reason.name)
                    }
                } catch (e: Exception) {
                    if (currentId == documentId) {
                        _ui.value =
                            ReaderUiState.Failed(
                                ReaderFailure.PARSE_FAILED,
                                e.message ?: "解析失败",
                            )
                    }
                }
            }
    }

    /**
     * Force a re-read after the user asks for it (menu “刷新”): invalidates
     * the session's parse cache for this document and runs the full open, so
     * an external edit shows up even when its fingerprint did not change
     * (same size + mtime, e.g. an overwrite within one mtime tick).
     */
    fun refresh(documentId: DocumentId) {
        session.invalidateCache(documentId)
        currentDoc = null
        currentDescriptor = null
        currentDescriptorId = null
        val min = session.currentGeneration()
        startOpen(documentId, min)
    }

    fun retry(documentId: DocumentId) {
        currentId = null
        open(documentId)
    }

    /**
     * Scroll observer: called by the screen with the first visible block.
     * Throttled (≥ 2s + real movement); the restore echo is seeded without
     * a write. Parse failures here never surface — the next flush retries.
     */
    fun onVisibleBlock(blockIndex: Int) {
        val id = currentId ?: return
        val doc = currentDoc ?: return
        if (_ui.value !is ReaderUiState.Content) return
        lastVisibleBlock = blockIndex.coerceIn(0, (doc.blocks.size - 1).coerceAtLeast(0))
        if (!throttler.shouldSave(lastVisibleBlock, 0)) return
        // Keep the restore address in step with the position we are about to
        // save (only on a real save — not on every scroll tick): a remount then
        // restores the latest address instead of the open-time one.
        syncRestoreAddress(id, lastVisibleBlock)
        viewModelScope.launch(ioDispatcher) {
            try {
                persist(id, doc, lastVisibleBlock)
                throttler.markSaved(lastVisibleBlock, 0)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Next scroll tick or the exit flush retries; never show an error.
            }
        }
    }

    /**
     * TOC jump landing (T08, REQ-0006 FR-4): records the target block
     * immediately (not throttled — the landing position must survive an
     * instant exit), bypassing the scroll throttle. The screen scrolled
     * instantly; this only persists.
     */
    fun jumpToBlock(blockIndex: Int) {
        val id = currentId ?: return
        val doc = currentDoc ?: return
        if (_ui.value !is ReaderUiState.Content) return
        lastVisibleBlock = blockIndex.coerceIn(0, (doc.blocks.size - 1).coerceAtLeast(0))
        throttler.markSaved(lastVisibleBlock, 0)
        syncRestoreAddress(id, lastVisibleBlock)
        viewModelScope.launch(ioDispatcher) {
            try {
                persist(id, doc, lastVisibleBlock)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Best effort: the next scroll tick or exit flush retries.
            }
        }
    }

    /**
     * Exit/pause flush (REQ-0003 FR-2): always persists the last visible
     * block, bypassing the throttle. Called from `onPause`/`onStop`/exit.
     * Synchronous from the UI's perspective — callers `join` when they need
     * the write before process death.
     */
    fun flushPosition(): Job {
        val id = currentId ?: return viewModelScope.launch {}
        // R2 DEF-012: an exit flush during a document switch (Loading) or a
        // failure has no consistent snapshot to write. Requiring Content plus a
        // document that belongs to [currentId] prevents writing the new
        // document's row with the previous document's address/anchor.
        val content = _ui.value as? ReaderUiState.Content ?: return viewModelScope.launch {}
        val doc = content.document
        if (doc.documentId != id) return viewModelScope.launch {}
        val block = lastVisibleBlock
        return viewModelScope.launch(ioDispatcher) {
            try {
                persist(id, doc, block)
                throttler.markSaved(block, 0)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Best effort: a failed flush must not crash exit.
            }
        }
    }

    /**
     * Mirrors a saved address into the visible [ReaderUiState.Content] so a
     * later remount (rotation, config change) restores the latest position
     * rather than the one recorded when the document was opened (DEF-013).
     * Called only on real saves, never per scroll tick.
     */
    private fun syncRestoreAddress(
        documentId: DocumentId,
        blockIndex: Int,
    ) {
        _ui.update { state ->
            if (state is ReaderUiState.Content && state.document.documentId == documentId && state.restoreBlock != blockIndex) {
                state.copy(restoreBlock = blockIndex)
            } else {
                state
            }
        }
    }

    fun dismissStaleNotice() {
        _ui.update { state ->
            if (state is ReaderUiState.Content && state.staleNotice != null) {
                state.copy(staleNotice = null)
            } else {
                state
            }
        }
    }

    /** Dismiss the one-shot T10 link/image notice (static text; no re-navigation). */
    fun dismissLinkNotice() {
        _ui.update { state ->
            if (state is ReaderUiState.Content && state.linkNotice != null) {
                state.copy(linkNotice = null)
            } else {
                state
            }
        }
    }

    /**
     * Follow one in-text document link (T10, REQ-0009 FR-1–FR-5/FR-10).
     *
     * Pure decision order: same-doc anchors scroll in place; library targets
     * resolve via [ReaderStore.resolveRelative] (in-tree `..` allowed, escape
     * + unknown schemes rejected there); external taps return their URL for
     * the screen to hand to the OS on this user gesture; everything else
     * (missing target, non-doc media, unsupported scheme, no external app)
     * shows a static one-shot notice and keeps the current document/state.
     */
    fun followLink(
        link: io.leaflet.markdown.BlockLink,
        onOpenDocument: (DocumentId) -> Unit,
        onOpenExternal: (String) -> Unit,
    ) {
        val doc = currentDoc ?: return notifyLink(T10_NOTICES[LinkNoticeKind.UNREACHABLE]!!)
        when (link.kind) {
            io.leaflet.markdown.DocLinkKind.EMPTY -> return
            io.leaflet.markdown.DocLinkKind.SAME_DOC_ANCHOR -> {
                val block = io.leaflet.reader.FragmentMatch.resolve(doc, link.fragment ?: "")
                if (block == null) {
                    notifyLink(T10_NOTICES[LinkNoticeKind.UNKNOWN_ANCHOR]!!)
                } else {
                    notifyLinkJump(block)
                }
            }
            io.leaflet.markdown.DocLinkKind.LIBRARY_DOC -> {
                val id = currentId ?: return notifyLink(T10_NOTICES[LinkNoticeKind.UNREACHABLE]!!)
                openJob?.cancel()
                // DEF-008: state writes on Main; resolution on IO.
                viewModelScope.launch {
                    try {
                        val target = withContext(ioDispatcher) { store.resolveRelative(id, link.destination) }
                        val targetId = target?.documentId
                        if (targetId == null) {
                            notifyLink(T10_NOTICES[LinkNoticeKind.UNREACHABLE]!!)
                            return@launch
                        }
                        val fragment = link.fragment
                        if (fragment.isNullOrEmpty()) {
                            onOpenDocument(targetId)
                            return@launch
                        }
                        pendingFragment = fragment
                        onOpenDocument(targetId)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        notifyLink(T10_NOTICES[LinkNoticeKind.UNREACHABLE]!!)
                    }
                }
            }
            io.leaflet.markdown.DocLinkKind.UNSUPPORTED_MEDIA -> notifyLink(T10_NOTICES[LinkNoticeKind.UNSUPPORTED_MEDIA]!!)
            io.leaflet.markdown.DocLinkKind.EXTERNAL -> {
                try {
                    onOpenExternal(link.destination)
                } catch (_: Exception) {
                    notifyLink(T10_NOTICES[LinkNoticeKind.NO_EXTERNAL_APP]!!)
                }
            }
            io.leaflet.markdown.DocLinkKind.UNSUPPORTED_SCHEME -> notifyLink(T10_NOTICES[LinkNoticeKind.UNSUPPORTED_SCHEME]!!)
            io.leaflet.markdown.DocLinkKind.UNREACHABLE -> notifyLink(T10_NOTICES[LinkNoticeKind.UNREACHABLE]!!)
        }
    }

    /**
     * Fragment carried across a library-document jump (`other.md#anchor`):
     * consumed once by the destination screen (see [takePendingFragment]).
     */
    private var pendingFragment: String? = null

    fun takePendingFragment(): String? {
        val pending = pendingFragment
        pendingFragment = null
        return pending
    }

    /** Pending same-doc jump set by [followLink]; consumed once by the screen. */
    private var pendingJump: Int? = null

    fun takePendingJump(): Int? {
        val pending = pendingJump
        pendingJump = null
        return pending
    }

    fun reportNoExternalApp() {
        notifyLink(T10_NOTICES[LinkNoticeKind.NO_EXTERNAL_APP]!!)
    }

    private fun notifyLinkJump(blockIndex: Int) {
        pendingJump = blockIndex
        jumpToBlock(blockIndex)
    }

    private fun notifyLink(text: String) {
        _ui.update { state ->
            if (state is ReaderUiState.Content) state.copy(linkNotice = text) else state
        }
    }

    /**
     * In-document literal search (T11, REQ-0008 FR-1/FR-4/FR-6/FR-7; R2-06
     * cap + cooperative cancellation, finding F07).
     *
     * Rules: blank queries never search (empty result, no error); each call
     * bumps [searchGeneration] and cancels the previous job (expired results
     * are dropped by the generation guard, never applied — FR-4/AC-4);
     * matching reuses the already-loaded document via
     * [DocumentSearch.searchCapped] (never a re-parse — FR-6), whose
     * per-block `ensureActive` keeps a 10 MB scan killable; the result is
     * capped at first-[DocumentSearch.MAX_SEARCH_MATCHES] in document order
     * and [SearchUiState.capped] tells the bar to show "n / 5000+ · refine"
     * instead of a bare count. Clearing or a document switch resets state
     * (FR-5). Case-sensitive literal semantics are frozen in [DocumentSearch].
     */
    fun onSearchQuery(query: String) {
        val doc = currentDoc
        searchGeneration += 1
        val generation = searchGeneration
        searchJob?.cancel()
        if (doc == null || _ui.value !is ReaderUiState.Content) {
            _search.value = SearchUiState(query = query, generation = generation)
            return
        }
        if (query.isBlank()) {
            _search.value = SearchUiState(query = query, generation = generation)
            return
        }
        _search.value = SearchUiState(query = query, searching = true, generation = generation)
        searchJob =
            // DEF-008: `_search` feeds `collectAsState` in the screen, so its
            // writes stay on Main; the capped scan is CPU-bound → IO.
            viewModelScope.launch {
                delay(DocumentSearch.DEBOUNCE_MS)
                val result =
                    try {
                        withContext(ioDispatcher) { DocumentSearch.searchCapped(doc, query) }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        io.leaflet.reader.SearchResult(emptyList(), capped = false)
                    }
                if (DocumentSearch.isStale(generation, searchGeneration)) return@launch
                _search.value =
                    SearchUiState(
                        query = query,
                        matches = result.matches,
                        currentIndex = 0,
                        searching = false,
                        generation = generation,
                        jumpToken = if (result.matches.isNotEmpty()) generation else 0,
                        capped = result.capped,
                    )
            }
    }

    /** Next/previous match with wrap-around; no-op when there are no matches. */
    fun stepSearch(forward: Boolean) {
        val state = _search.value
        if (state.matches.isEmpty()) return
        val next = DocumentSearch.stepIndex(state.currentIndex, state.matches.size, forward)
        _search.value = state.copy(currentIndex = next, jumpToken = state.jumpToken + 1)
    }

    /** Closes the search bar: cancels the job and clears all search state. */
    fun closeSearch() {
        cancelSearchLocked()
        _search.value = SearchUiState()
    }

    private fun cancelSearchLocked() {
        searchJob?.cancel()
        searchJob = null
        searchGeneration += 1
    }

    override fun onCleared() {
        // Best-effort synchronous flush is impossible here (no scope left);
        // the screen flushes on pause/stop, so onCleared only releases.
        session.close()
        super.onCleared()
    }

    private suspend fun awaitLoaded(
        documentId: DocumentId,
        minGeneration: Long,
    ): LoadedDocument {
        // The session owns the parse + generation guard; this ViewModel only
        // needs the successful result (document + index snapshot) to plan
        // the restore and the recent-write. Failures arrive as the session's
        // Failed state and surface here as typed exceptions.
        //
        // R2 DEF-005: `minGeneration` is the session generation observed
        // BEFORE this reload was launched; a result from that generation or
        // older is the pre-reload cache entry and is never accepted as the
        // fresh document. Failures from the same generation fail fast
        // instead of waiting out the timeout.
        val start = clock()
        while (true) {
            session.lastLoadedFor(documentId)
                ?.takeIf { it.generation > minGeneration }
                ?.let { return it }
            when (val s = session.state.value) {
                is ReaderViewState.Failed -> {
                    if (s.generation > minGeneration) {
                        throw ReaderBadEntryException(s.reason, s.message)
                    }
                }
                is ReaderViewState.Content,
                is ReaderViewState.Degraded,
                is ReaderViewState.Loading,
                -> Unit
            }
            if (clock() - start > OPEN_TIMEOUT_MS) {
                throw ReaderBadEntryException(ReaderFailure.PARSE_FAILED, "打开超时，请重试")
            }
            kotlinx.coroutines.delay(16)
        }
    }

    private suspend fun onLoaded(
        documentId: DocumentId,
        loaded: LoadedDocument,
        stored: StoredPosition?,
    ) {
        val doc = loaded.document
        currentDoc = doc
        currentDescriptor = loaded.descriptor
        currentDescriptorId = documentId
        val savedFp = stored?.fingerprint
        val liveFp = loaded.descriptor.fingerprint
        val restore =
            PositionRestore.restore(
                saved = stored?.position?.takeIf { it.version == PositionRestore.CONTRACT_VERSION },
                savedFingerprint = savedFp,
                liveFingerprint = liveFp,
                blockCount = doc.blocks.size,
                locateAnchor = { anchor -> locateAnchorInDoc(doc, anchor) },
            )
        lastVisibleBlock = restore.blockIndex
        // Seed the throttle so the restore scroll itself never writes.
        throttler.markSaved(restore.blockIndex, restore.blockOffset)
        val degradedNotice =
            if (doc.degraded) {
                doc.degradedNotice ?: "本文档部分内容以纯文本显示"
            } else {
                null
            }
        _ui.value =
            ReaderUiState.Content(
                document = doc,
                restoreBlock = restore.blockIndex,
                degradedNotice = degradedNotice,
                staleNotice =
                    if (restore.showStaleNotice) {
                        "文档内容有变化，已定位到最接近的位置"
                    } else {
                        null
                    },
            )
        // MRU on successful open only (REQ-0003 FR-5): failures never pollute
        // recents. The write is IO, the state updates above stay on Main.
        try {
            withContext(ioDispatcher) {
                store.recordRecent(
                    RecentRead(
                        documentId = documentId,
                        titleSnapshot = doc.title.ifBlank { loaded.descriptor.titleFallback },
                        libraryId = loaded.descriptor.libraryId,
                        lastOpenedAtEpochMs = store.clockMs(),
                    ),
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // A failed recent-write must not fail the open.
        }
    }

    private suspend fun persist(
        documentId: DocumentId,
        doc: ParsedDocument,
        blockIndex: Int,
    ) {
        // R2 DEF-012: only trust the cached descriptor when it describes THIS
        // document; otherwise fetch it. Re-cache only while this document is
        // still the open one, so a delayed save for a document the user left
        // cannot poison the descriptor of the current one.
        val descriptor =
            if (currentDescriptorId == documentId) {
                currentDescriptor
            } else {
                store.describe(documentId)
            }
        if (documentId == currentId) {
            currentDescriptor = descriptor
            currentDescriptorId = documentId
        }
        val fp = descriptor?.fingerprint
        val (mtime, size) = splitFingerprint(fp)
        val position =
            ReadingPosition(
                documentId = documentId,
                version = PositionRestore.CONTRACT_VERSION,
                blockIndex = blockIndex.coerceIn(0, (doc.blocks.size - 1).coerceAtLeast(0)),
                blockOffset = 0,
                progress = PositionRestore.progressForBlock(blockIndex, doc.blocks.size),
                anchor = anchorForBlock(doc, blockIndex),
            )
        store.savePosition(position, mtime, size)
        currentStored = StoredPosition(position, fp)
    }

    private fun splitFingerprint(fp: String?): Pair<Long, Long> {
        // Storage fingerprints are opaque `mtime:size` strings; the save path
        // needs the numeric halves back. Unknown/malformed forms degrade to
        // 0:0 (sentinels that never hit the incremental skip, REQ-0001 FR-4).
        if (fp == null) return 0L to 0L
        val parts = fp.split(':')
        if (parts.size != 2) return 0L to 0L
        return (parts[0].toLongOrNull() ?: 0L) to (parts[1].toLongOrNull() ?: 0L)
    }

    private companion object {
        const val OPEN_TIMEOUT_MS = 30_000L
    }
}

/** Typed open failure surfaced to the Failed state with a recovery action. */
class ReaderBadEntryException(
    val reason: ReaderFailure,
    message: String?,
) : RuntimeException(message)
