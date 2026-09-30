package io.leaflet.reader

import io.leaflet.markdown.BlockKind
import io.leaflet.markdown.DecodedText
import io.leaflet.markdown.EncodingState
import io.leaflet.markdown.LeafletDocumentParser
import io.leaflet.markdown.ParsedDocument
import io.leaflet.markdown.TextDecoder
import io.leaflet.model.DocumentId
import io.leaflet.model.LibraryId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream

/**
 * Reader-side document boundary used by [ReaderSession] (T06, evolved T07).
 *
 * Production implementations delegate to `:core:storage` (`DocumentSource`
 * for the stream plus the document index row for metadata — same method
 * shapes, storage-owned). The session never imports storage — the boundary
 * is injected, keeping the `reader → storage` ban intact (overview §5
 * rule 1).
 *
 * Stream ownership: the CALLER closes the stream returned by [openStream]
 * (the session does so via `use {}` on every path, including cancellation).
 * Cancellation is never swallowed: implementations MUST rethrow
 * [kotlinx.coroutines.CancellationException] untouched.
 *
 * Failure translation (T07 recovery mapping): lost grants surface as
 * [SecurityException] (→ `AUTHORIZATION_LOST`), vanished files as
 * [java.io.IOException] (→ `FILE_MISSING`). A missing index row surfaces as
 * a null [describe] result (→ `FILE_MISSING`).
 */
interface ReaderDocumentGateway {
    suspend fun openStream(documentId: DocumentId): InputStream

    /** Index metadata for one document, or null when the row is gone. */
    suspend fun describe(documentId: DocumentId): DocumentDescriptor?
}

/**
 * Index metadata snapshot feeding the open pipeline (T07).
 *
 * @property fileName display name with extension (markdown detection + title fallback).
 * @property fingerprint opaque document identity (`mtime:size`) observed at
 *   describe time; null means "unknown, never trust as fresh". Compared for
 *   equality only — never parsed here.
 * @property libraryId owning library, for recent-read grouping.
 * @property titleFallback display name used when parsing yields no title.
 */
data class DocumentDescriptor(
    val fileName: String,
    val fingerprint: String?,
    val libraryId: LibraryId,
    val titleFallback: String,
)

/**
 * Tri-state view model output for the T06 placeholder route (T07 binds).
 *
 * [generation] identifies the load attempt that produced the state (0 for
 * `Loading` before the first open). A caller waiting for ITS OWN reload
 * compares the state's generation against the one it recorded before
 * launching, so a failure left over from an earlier attempt is never
 * mistaken for this one (R2 DEF-005).
 */
sealed interface ReaderViewState {
    data object Loading : ReaderViewState

    data class Content(
        val document: ParsedDocument,
        val generation: Long = 0L,
    ) : ReaderViewState

    data class Degraded(
        val document: ParsedDocument,
        val notice: String,
        val generation: Long = 0L,
    ) : ReaderViewState

    data class Failed(
        val reason: ReaderFailure,
        val message: String,
        val generation: Long = 0L,
    ) : ReaderViewState
}

/**
 * Cancellable single-document load session (T06, extended T07).
 *
 * Protocol:
 * - [openDocument] cancels any in-flight load, bumps the generation, emits
 *   [ReaderViewState.Loading], snapshots the index row via
 *   [ReaderDocumentGateway.describe], then reads + parses on [ioDispatcher].
 * - A result (or failure) applies ONLY if its generation still matches —
 *   stale jobs (fast A→B switch, REQ-0002 AC-7) are dropped and their stream
 *   already closed via `use {}`.
 * - [CancellationException] never becomes [ReaderViewState.Failed]; the next
 *   document's Loading state already replaced it.
 * - Unchanged content (fingerprint match) reuses the cached parse without
 *   reopening the stream (REQ-0003 FR-7); changed content invalidates it.
 * - [close] cancels the session scope and releases everything.
 */
class ReaderSession(
    private val gateway: ReaderDocumentGateway,
    parser: LeafletDocumentParser,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    scope: CoroutineScope? = null,
    /**
     * Dispatcher for CPU-bound decode/parse work. Defaults to [ioDispatcher]
     * for backward compatibility, but production MUST pass a background
     * dispatcher (R2-01: decode+parse never run on Main even when [scope] is
     * the Main-bound `viewModelScope` — the old code relaunched in
     * `sessionScope` and silently dropped the caller's IO context).
     */
    private val parseDispatcher: CoroutineDispatcher = ioDispatcher,
) {
    private val ownedScope: CoroutineScope? =
        if (scope == null) {
            CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        } else {
            null
        }
    private val sessionScope: CoroutineScope = scope ?: ownedScope!!
    private var loadJob: kotlinx.coroutines.Job? = null
    private var generation: Long = 0
    private val parserRef: LeafletDocumentParser = parser

    private val mutableState =
        MutableStateFlow<ReaderViewState>(ReaderViewState.Loading)
    val state: StateFlow<ReaderViewState> = mutableState.asStateFlow()

    /** Single-entry parse cache (REQ-0003 FR-7): document id → fingerprint-guarded parse. */
    private val parseCache = mutableMapOf<String, CachedParse>()

    /** Last successful load (document + index snapshot); null before the first one. */
    @Volatile
    private var lastLoaded: LoadedDocument? = null

    /** Last successful load for [documentId], or null when it belongs elsewhere. */
    fun lastLoadedFor(documentId: DocumentId): LoadedDocument? = lastLoaded?.takeIf { it.document.documentId == documentId }

    /**
     * Generation a load started NOW will carry. A caller that wants proof its
     * own reload is the one that produced a result records this before
     * [openDocument] and ignores anything at or below it (R2 DEF-005: the
     * same-document freshness recheck must not accept the pre-check cache
     * entry as if it were the reloaded document).
     */
    fun currentGeneration(): Long = generation

    /** Generation of the load that produced the last successful result. */
    fun lastLoadedGeneration(): Long = lastLoaded?.generation ?: 0L

    fun openDocument(documentId: DocumentId) {
        loadJob?.cancel()
        generation += 1
        val myGeneration = generation
        mutableState.value = ReaderViewState.Loading
        loadJob =
            sessionScope.launch {
                try {
                    val loaded = loadOne(documentId, myGeneration)
                    lastLoaded = loaded
                    if (myGeneration == generation) {
                        val doc = loaded.document
                        mutableState.value =
                            if (doc.degraded) {
                                ReaderViewState.Degraded(
                                    doc,
                                    doc.degradedNotice ?: "本文档部分内容以纯文本显示",
                                    myGeneration,
                                )
                            } else {
                                ReaderViewState.Content(doc, myGeneration)
                            }
                    }
                } catch (e: CancellationException) {
                    // Stale or closed: drop silently, never an error state.
                    throw e
                } catch (e: ReaderOpenException) {
                    if (myGeneration == generation) {
                        mutableState.value =
                            ReaderViewState.Failed(e.reason, e.message ?: e.reason.name, myGeneration)
                    }
                } catch (e: Exception) {
                    if (myGeneration == generation) {
                        mutableState.value =
                            ReaderViewState.Failed(
                                ReaderFailure.PARSE_FAILED,
                                e.message ?: "解析失败",
                                myGeneration,
                            )
                    }
                }
            }
    }

    fun retry(documentId: DocumentId) = openDocument(documentId)

    /** Drops the cached parse for one document (e.g. after a rescan rewrote the index). */
    fun invalidateCache(documentId: DocumentId) {
        parseCache.remove(documentId.value)
    }

    fun close() {
        loadJob?.cancel()
        loadJob = null
        generation += 1
        ownedScope?.cancel()
    }

    /** Number of entries currently held by the parse cache (test probe). */
    fun cachedEntryCountForTest(): Int = parseCache.size

    private suspend fun loadOne(
        documentId: DocumentId,
        generation: Long,
    ): LoadedDocument {
        val descriptor =
            try {
                withContext(ioDispatcher) {
                    ensureActive()
                    gateway.describe(documentId)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: SecurityException) {
                throw ReaderOpenException(ReaderFailure.AUTHORIZATION_LOST, e.message, e)
            } catch (e: ReaderOpenException) {
                throw e
            } catch (e: Exception) {
                // R2-05: storage-typed failures map by NAME (DocumentSource
                // throws RuntimeException subtypes the reader module cannot
                // import — core:reader → core:storage is banned). Grant loss
                // (PermissionLost) → re-authorize; gone file (DocumentGone) →
                // remove/retry; anything else → missing (safe recovery).
                throw mapStorageFailure(e, documentId.value)
            } ?: throw ReaderOpenException(
                ReaderFailure.FILE_MISSING,
                "文档索引不存在（可能已被删除或移出资料库）",
                null,
            )
        currentCoroutineContext().ensureActive()
        // Fingerprint guard (REQ-0003 FR-7): an unchanged file reuses the
        // cached parse instead of re-parsing; a changed file invalidates it.
        // Keyed by document AND fingerprint so a stale entry from another
        // file can never be served.
        val cached = parseCache.get(documentId.value)
        if (cached != null && cached.fingerprint == descriptor.fingerprint) {
            return LoadedDocument(cached.document, descriptor, generation)
        }
        val fileName = descriptor.fileName
        val isMarkdown = isMarkdownName(fileName)
        val readOutcome =
            withContext(ioDispatcher) {
                ensureActive()
                try {
                    gateway.openStream(documentId).use { boundedRead(it, MAX_READ_BYTES) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: IOException) {
                    throw ReaderOpenException(ReaderFailure.FILE_MISSING, e.message, e)
                } catch (e: SecurityException) {
                    throw ReaderOpenException(ReaderFailure.AUTHORIZATION_LOST, e.message, e)
                } catch (e: ReaderOpenException) {
                    throw e
                } catch (e: Exception) {
                    throw mapStorageFailure(e, documentId.value)
                }
            }
        // CPU-bound decode+parse on parseDispatcher (R2-01): production
        // passes a background dispatcher so Main is never blocked; the old
        // code ran these on whatever thread resumed loadOne (Main when the
        // scope is viewModelScope). Cancellation still drops cleanly via the
        // parser's own yield points.
        val (parsed, truncated) =
            withContext(parseDispatcher) {
                ensureActive()
                val decoded = TextDecoder.decode(readOutcome.bytes)
                ensureActive()
                val result = parserRef.parse(documentId, decoded, fileName, isMarkdown)
                ensureActive()
                result to readOutcome.truncated
            }
        val doc =
            if (truncated) {
                parsed.copy(
                    degraded = true,
                    degradedNotice =
                        listOfNotNull(
                            parsed.degradedNotice,
                            "文件过大，仅显示前 ${MAX_READ_BYTES / 1024 / 1024}MB",
                        ).joinToString("；"),
                )
            } else {
                parsed
            }
        currentCoroutineContext().ensureActive()
        // Bounded cache (R2-01): at most MAX_CACHED_PARSES entries, oldest
        // evicted first. The old unbounded Map grew one entry per opened
        // document; the "single-entry" comment described intent, not code.
        // Unknown fingerprints never match (null only equals a stored null
        // that was itself produced by an unknown-fingerprint describe — and
        // such entries are never cached; see below).
        if (descriptor.fingerprint != null) {
            if (parseCache.size >= MAX_CACHED_PARSES && !parseCache.containsKey(documentId.value)) {
                val oldest = parseCache.keys.firstOrNull()
                if (oldest != null) parseCache.remove(oldest)
            }
            parseCache[documentId.value] = CachedParse(descriptor.fingerprint, doc)
        }
        return LoadedDocument(doc, descriptor, generation)
    }

    companion object {
        /** 16 MiB read cap: covers the 10MB sample tier with decode headroom. */
        const val MAX_READ_BYTES: Int = 16 * 1024 * 1024

        /** Parse-cache capacity (R2-01): small, documented, evicted oldest-first. */
        const val MAX_CACHED_PARSES: Int = 3

        /**
         * Copies at most [limit] bytes, reporting truncation. Never holds
         * more than limit + 8KiB. Cancellation-checked per chunk; the
         * caller's `use {}` closes the stream on every path.
         */
        internal suspend fun boundedRead(
            stream: java.io.InputStream,
            limit: Int,
        ): ReadOutcome {
            val out = java.io.ByteArrayOutputStream((limit + 8192).coerceAtMost(1024 * 1024))
            val buf = ByteArray(8192)
            var total = 0L
            var truncated = false
            while (true) {
                currentCoroutineContext().ensureActive()
                val n = stream.read(buf)
                if (n < 0) break
                if (total + n > limit) {
                    val room = (limit - total).toInt().coerceAtLeast(0)
                    if (room > 0) out.write(buf, 0, room)
                    truncated = true
                    break
                }
                out.write(buf, 0, n)
                total += n
            }
            return ReadOutcome(out.toByteArray(), truncated)
        }
    }
}

/**
 * Classifies a storage throwable for callers outside `:core:reader` (the app's
 * access probe, R2 DEF-005) without importing `:core:storage` types: matching
 * is by simple class name, so it stays obfuscation-safe and the
 * `reader → storage` dependency ban holds.
 */
fun readerFailureReasonOf(e: Throwable): ReaderFailure = mapStorageFailure(e, "").reason

/**
 * Maps storage-layer failures to reader recovery states WITHOUT importing
 * storage types (R2-05 F06: `core:reader` → `core:storage` is banned by
 * architecture overview §5 rule 1, and `DocumentSource` throws
 * `RuntimeException` subtypes). Matching is by simple class name so
 * obfuscation-safe names still resolve; unknown throwables map to
 * FILE_MISSING (recovery prompt, never a crash, never silent).
 */
internal fun mapStorageFailure(
    e: Throwable,
    documentId: String,
): ReaderOpenException {
    var cursor: Throwable? = e
    while (cursor != null) {
        when (cursor.javaClass.simpleName) {
            "PermissionLost" ->
                return ReaderOpenException(ReaderFailure.AUTHORIZATION_LOST, cursor.message, cursor)
            "DocumentGone" ->
                return ReaderOpenException(ReaderFailure.FILE_MISSING, cursor.message, cursor)
            "ReadFailed" ->
                return ReaderOpenException(ReaderFailure.FILE_MISSING, cursor.message, cursor)
            "PermissionDenied" ->
                return ReaderOpenException(ReaderFailure.AUTHORIZATION_LOST, cursor.message, cursor)
            "LibraryNotFound" ->
                return ReaderOpenException(ReaderFailure.FILE_MISSING, cursor.message, cursor)
        }
        cursor = cursor.cause
    }
    return ReaderOpenException(ReaderFailure.FILE_MISSING, e.message, e)
}

/** Bounded-read result: capped bytes + whether the stream had more. */
internal data class ReadOutcome(
    val bytes: ByteArray,
    val truncated: Boolean,
)

/**
 * One successful open: the parsed document plus the index snapshot that
 * produced it. `descriptor.fingerprint` is the content identity at open
 * time — the app layer compares it with the stored position fingerprint to
 * decide exact vs fallback restore (REQ-0003 FR-3), and records it on the
 * next position save.
 */
data class LoadedDocument(
    val document: ParsedDocument,
    val descriptor: DocumentDescriptor,
    /**
     * Session generation that produced this result (0 for rows built directly
     * by tests). Callers that trigger their own reload compare this against
     * the generation observed BEFORE launching it, so a cached result left
     * over from the previous open can never pass as the fresh one.
     */
    val generation: Long = 0L,
)

/** Fingerprint-guarded cache entry: reuse only on exact fingerprint match. */
private data class CachedParse(
    val fingerprint: String?,
    val document: ParsedDocument,
)

/** Typed open failure: maps to [ReaderViewState.Failed] with recovery action. */
class ReaderOpenException(
    val reason: ReaderFailure,
    message: String?,
    cause: Throwable?,
) : IOException(message, cause)

/** Extension → markdown detection shared by session and host view. */
internal fun isMarkdownName(fileName: String): Boolean = fileName.substringAfterLast('.', "").lowercase() in setOf("md", "markdown")

/** Encoding-damage banner text shared with the host view. */
internal fun DecodedText.bannerIfDamaged(): String? =
    if (encodingState == EncodingState.HAD_ENCODING_DAMAGE) {
        "编码无法识别，已按替换字符显示部分内容"
    } else {
        null
    }

/**
 * Entry-kind assignment for the RecyclerView host (reader.md §1):
 * one entry per [ParsedDocument.blocks] index — never a single whole-document
 * TextView (ADR-0006 decision 1).
 */
enum class ReaderEntryKind {
    /** Headings, paragraphs, lists, quotes, separators: plain TextView entry. */
    TEXT,

    /** Fenced/indented code: TextView in a horizontal-scroll container. */
    CODE_SCROLL,

    /** Tables: TextView in a horizontal-scroll container (NORMAL mode, T09). */
    TABLE_SCROLL,

    /** Oversized chunks / unknown syntax: bounded plain-text entry + banner. */
    PLAIN_FALLBACK,
}

internal fun entryKindFor(blockKind: BlockKind): ReaderEntryKind =
    when (blockKind) {
        BlockKind.CODE -> ReaderEntryKind.CODE_SCROLL
        BlockKind.TABLE -> ReaderEntryKind.TABLE_SCROLL
        BlockKind.PLAIN_FALLBACK -> ReaderEntryKind.PLAIN_FALLBACK
        BlockKind.HEADING, BlockKind.PARAGRAPH, BlockKind.LIST,
        BlockKind.QUOTE, BlockKind.SEPARATOR,
        -> ReaderEntryKind.TEXT
    }
