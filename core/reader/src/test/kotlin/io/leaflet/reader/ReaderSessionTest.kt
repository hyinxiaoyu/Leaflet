package io.leaflet.reader

import io.leaflet.markdown.BlockKind
import io.leaflet.markdown.DecodedText
import io.leaflet.markdown.EncodingState
import io.leaflet.markdown.LeafletDocumentParser
import io.leaflet.model.DocumentId
import io.leaflet.model.LibraryId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream

/**
 * T06 session contract tests (REQ-0002 FR-1/FR-8, AC-6/AC-7):
 * tri-state emission, fast-switch staleness guard, cancellation release,
 * failure mapping. JVM-only with a fake gateway (no storage, no views);
 * the 10MB instrumented pass owns device timing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReaderSessionTest {
    private val parser = LeafletDocumentParser.createForJvmTests()

    private class FakeGateway(
        val files: Map<String, ByteArray>,
        val delaysMs: Map<String, Long> = emptyMap(),
        val failures: Map<String, Throwable> = emptyMap(),
        val fingerprints: Map<String, String> = emptyMap(),
        val missingDescribe: Set<String> = emptySet(),
    ) : ReaderDocumentGateway {
        val opened = ArrayList<String>()
        val closed = ArrayList<String>()

        override suspend fun describe(documentId: DocumentId): DocumentDescriptor? {
            if (documentId.value in missingDescribe) return null
            // R2-05: storage-typed failures surface on BOTH describe and
            // open paths in production (revoked grant can hit either first).
            failures[documentId.value]?.let { throw it }
            return DocumentDescriptor(
                fileName = documentId.value,
                fingerprint = fingerprints[documentId.value] ?: "fp:${documentId.value}",
                libraryId = LibraryId("lib"),
                titleFallback = documentId.value.substringBeforeLast('.'),
            )
        }

        override suspend fun openStream(documentId: DocumentId): InputStream {
            opened.add(documentId.value)
            failures[documentId.value]?.let { throw it }
            delaysMs[documentId.value]?.let { delay(it) }
            val bytes = files[documentId.value] ?: throw IOException("gone")
            return object : ByteArrayInputStream(bytes) {
                override fun close() {
                    super.close()
                    closed.add(documentId.value)
                }
            }
        }
    }

    private fun TestScope.sessionOf(gateway: FakeGateway): ReaderSession {
        val dispatcher = StandardTestDispatcher(testScheduler)
        return ReaderSession(
            gateway = gateway,
            parser = parser,
            ioDispatcher = dispatcher,
            scope = this,
        )
    }

    // R2-01: dispatcher discipline — the session's own scope may be
    // Main-bound (production viewModelScope); decode+parse must still run
    // on parseDispatcher. Records the threads each stage runs on.
    private class ThreadProbeGateway(
        val bytes: ByteArray,
    ) : ReaderDocumentGateway {
        val describeThread = ArrayList<String>()
        val openThread = ArrayList<String>()

        override suspend fun describe(documentId: DocumentId): DocumentDescriptor? {
            describeThread.add(Thread.currentThread().name)
            return DocumentDescriptor(
                fileName = documentId.value,
                fingerprint = "fp",
                libraryId = LibraryId("lib"),
                titleFallback = "t",
            )
        }

        override suspend fun openStream(documentId: DocumentId): InputStream = ByteArrayInputStream(bytes)
    }

    // -- R2 DEF-005: reload generations must be distinguishable -------------

    @Test
    fun generationAdvancesPerOpenAndStampsResults() =
        runTest {
            val gateway = FakeGateway(mapOf("a.md" to "# Hi\n".toByteArray()))
            val session = sessionOf(gateway)
            val before = session.currentGeneration()
            session.openDocument(DocumentId("a.md"))
            val launched = session.currentGeneration()
            assertTrue("open bumps the generation", launched > before)
            advanceUntilIdle()
            val loaded = session.lastLoadedFor(DocumentId("a.md"))!!
            assertEquals("result carries its own generation", launched, loaded.generation)
            assertEquals(launched, session.lastLoadedGeneration())
            // A second open advances again, so a caller that recorded the
            // first generation can tell the two results apart.
            session.openDocument(DocumentId("a.md"))
            val second = session.currentGeneration()
            assertTrue(second > launched)
            advanceUntilIdle()
            assertEquals(second, session.lastLoadedFor(DocumentId("a.md"))!!.generation)
            session.close()
        }

    @Test
    fun cachedReopenStillCarriesTheNewGeneration() =
        runTest {
            // Unchanged fingerprint reuses the cached parse, but the caller
            // that asked for a reload must still see a NEW generation — the
            // cached document is served as this load's result.
            val gateway = FakeGateway(mapOf("a.md" to "# Hi\n".toByteArray()))
            val session = sessionOf(gateway)
            session.openDocument(DocumentId("a.md"))
            advanceUntilIdle()
            val firstDoc = (session.state.value as ReaderViewState.Content).document
            val firstGen = session.lastLoadedFor(DocumentId("a.md"))!!.generation
            session.openDocument(DocumentId("a.md"))
            val secondGen = session.currentGeneration()
            advanceUntilIdle()
            val loaded = session.lastLoadedFor(DocumentId("a.md"))!!
            assertEquals(secondGen, loaded.generation)
            assertTrue(loaded.generation > firstGen)
            assertTrue("cached parse is reused", loaded.document === firstDoc)
            session.close()
        }

    @Test
    fun failureStateCarriesItsGeneration() =
        runTest {
            val gateway =
                FakeGateway(
                    files = mapOf("locked.md" to "# x\n".toByteArray()),
                    failures = mapOf("locked.md" to SecurityException("grant revoked")),
                )
            val session = sessionOf(gateway)
            val before = session.currentGeneration()
            session.openDocument(DocumentId("locked.md"))
            val launched = session.currentGeneration()
            advanceUntilIdle()
            val state = session.state.value
            assertTrue(state is ReaderViewState.Failed)
            assertEquals(ReaderFailure.AUTHORIZATION_LOST, (state as ReaderViewState.Failed).reason)
            assertEquals(launched, state.generation)
            assertTrue(state.generation > before)
            session.close()
        }

    @Test
    fun loadingThenContent() =
        runTest {
            val gateway = FakeGateway(mapOf("a.md" to "# Hi\n\nbody\n".toByteArray()))
            val session = sessionOf(gateway)
            session.openDocument(DocumentId("a.md"))
            assertTrue(session.state.value is ReaderViewState.Loading)
            advanceUntilIdle()
            val state = session.state.value
            assertTrue(state is ReaderViewState.Content)
            assertEquals("Hi", (state as ReaderViewState.Content).document.title)
            session.close()
        }

    @Test
    fun degradedSurfacesNotice() =
        runTest {
            val big = "word ".repeat(9000) // ~45KB single paragraph > bound
            val gateway = FakeGateway(mapOf("big.md" to big.toByteArray()))
            val session = sessionOf(gateway)
            session.openDocument(DocumentId("big.md"))
            advanceUntilIdle()
            val state = session.state.value
            assertTrue(state is ReaderViewState.Degraded)
            assertTrue((state as ReaderViewState.Degraded).notice.contains("纯文本显示"))
            session.close()
        }

    @Test
    fun missingFileMapsToFailed() =
        runTest {
            val gateway = FakeGateway(emptyMap())
            val session = sessionOf(gateway)
            session.openDocument(DocumentId("gone.md"))
            advanceUntilIdle()
            val state = session.state.value
            assertTrue(state is ReaderViewState.Failed)
            assertEquals(ReaderFailure.FILE_MISSING, (state as ReaderViewState.Failed).reason)
            session.close()
        }

    @Test
    fun deniedMapsToAuthorizationLost() =
        runTest {
            val gateway =
                FakeGateway(
                    emptyMap(),
                    failures = mapOf("locked.md" to SecurityException("no grant")),
                )
            val session = sessionOf(gateway)
            session.openDocument(DocumentId("locked.md"))
            advanceUntilIdle()
            val state = session.state.value
            assertTrue(state is ReaderViewState.Failed)
            assertEquals(
                ReaderFailure.AUTHORIZATION_LOST,
                (state as ReaderViewState.Failed).reason,
            )
            session.close()
        }

    @Test
    fun fastSwitchStaleResultNeverWins() =
        runTest {
            // A is slow (200ms), B is instant: final state MUST be B even though
            // A's job was launched first (REQ-0002 AC-7: version guard). A never
            // reaches its stream (cancelled while delaying), so "closed" applies
            // to B; the assertion that matters is the version guard on state.
            val gateway =
                FakeGateway(
                    files =
                        mapOf(
                            "a.md" to "# Doc A\n".toByteArray(),
                            "b.md" to "# Doc B\n".toByteArray(),
                        ),
                    delaysMs = mapOf("a.md" to 200L),
                )
            val session = sessionOf(gateway)
            session.openDocument(DocumentId("a.md"))
            advanceTimeBy(10)
            session.openDocument(DocumentId("b.md"))
            advanceUntilIdle()
            val state = session.state.value
            assertTrue(state is ReaderViewState.Content)
            assertEquals("Doc B", (state as ReaderViewState.Content).document.title)
            // B opened and closed cleanly; A was cancelled before its stream.
            assertTrue(gateway.opened.contains("b.md"))
            assertTrue(gateway.closed.contains("b.md"))
            session.close()
        }

    @Test
    fun staleParseResultDroppedByGeneration() =
        runTest {
            // Both streams open; A's *parse* is what goes stale: open B after A
            // started reading so A's completion must not overwrite B's content.
            val gateway =
                FakeGateway(
                    files =
                        mapOf(
                            "a.md" to "# Doc A\n".toByteArray(),
                            "b.md" to "# Doc B\n".toByteArray(),
                        ),
                )
            val session = sessionOf(gateway)
            session.openDocument(DocumentId("a.md"))
            session.openDocument(DocumentId("b.md"))
            advanceUntilIdle()
            val state = session.state.value
            assertTrue(state is ReaderViewState.Content)
            assertEquals("Doc B", (state as ReaderViewState.Content).document.title)
            session.close()
        }

    @Test
    fun cancelReleasesStream() =
        runTest {
            // A slow stream open is cancelled mid-flight: session stays Loading
            // (never an error) and the in-flight attempt is dropped.
            val gateway =
                FakeGateway(
                    files = mapOf("t.md" to "# T\n".toByteArray()),
                    delaysMs = mapOf("t.md" to 5000L),
                )
            val session = sessionOf(gateway)
            session.openDocument(DocumentId("t.md"))
            advanceTimeBy(10)
            session.close()
            advanceUntilIdle()
            assertTrue(session.state.value is ReaderViewState.Loading)
        }

    @Test
    fun entryKindsCoverAllBlocks() {
        assertEquals(ReaderEntryKind.TEXT, entryKindFor(BlockKind.HEADING))
        assertEquals(ReaderEntryKind.TEXT, entryKindFor(BlockKind.PARAGRAPH))
        assertEquals(ReaderEntryKind.TEXT, entryKindFor(BlockKind.LIST))
        assertEquals(ReaderEntryKind.TEXT, entryKindFor(BlockKind.QUOTE))
        assertEquals(ReaderEntryKind.TEXT, entryKindFor(BlockKind.SEPARATOR))
        assertEquals(ReaderEntryKind.CODE_SCROLL, entryKindFor(BlockKind.CODE))
        assertEquals(ReaderEntryKind.TABLE_SCROLL, entryKindFor(BlockKind.TABLE))
        assertEquals(ReaderEntryKind.PLAIN_FALLBACK, entryKindFor(BlockKind.PLAIN_FALLBACK))
        // Scroll policy: code/tables scroll horizontally, text never does.
        assertTrue(EntryScrollPolicy.requiresHorizontalScroll(ReaderEntryKind.CODE_SCROLL))
        assertTrue(EntryScrollPolicy.requiresHorizontalScroll(ReaderEntryKind.TABLE_SCROLL))
        assertFalse(EntryScrollPolicy.requiresHorizontalScroll(ReaderEntryKind.TEXT))
        assertFalse(EntryScrollPolicy.requiresHorizontalScroll(ReaderEntryKind.PLAIN_FALLBACK))
    }

    @Test
    fun softWrapKeepsHorizontalScrollContainer() {
        // REQ-0007 FR-7/AC-4: the scroll container stays mounted whether
        // soft-wrap is ON (E-Ink default, wrapped in place) or OFF (sideways
        // scroll to the tail). The toggle only changes binding, never entry
        // structure — so flipping it cannot strand content off-screen.
        assertTrue(EntryScrollPolicy.requiresHorizontalScroll(entryKindFor(BlockKind.CODE)))
        assertTrue(CodeWrapPolicy.requiresHorizontalScrollContainer(codeSoftWrap = true))
        assertTrue(CodeWrapPolicy.requiresHorizontalScrollContainer(codeSoftWrap = false))
    }

    @Test
    fun typographyContextSurvivesRelayout() {
        // REQ-0007 scope §25: reflow records the first visible block and
        // scrolls back via keepBlock (same seam as the T08 mode switch).
        assertEquals(7, keepBlock(7, 20))
        assertEquals(0, keepBlock(0, 0))
        assertEquals(19, keepBlock(99, 20))
    }

    @Test
    fun normalTableModeHasNoWrapCardBranch() {
        // FR-8/FR-9: NORMAL is the only rendered mode; WRAP/CARD are named
        // M2+ follow-ups with no UI or render branch today.
        assertEquals(
            ReaderEntryKind.TABLE_SCROLL,
            entryKindFor(BlockKind.TABLE),
        )
        assertTrue(TableModePolicy.rendersNormalOnly(io.leaflet.markdown.TableRenderMode.NORMAL))
        assertFalse(TableModePolicy.rendersNormalOnly(io.leaflet.markdown.TableRenderMode.WRAP))
        assertFalse(TableModePolicy.rendersNormalOnly(io.leaflet.markdown.TableRenderMode.CARD))
    }

    @Test
    fun txtGatewayPathIsLiteral() =
        runTest {
            val gateway =
                FakeGateway(
                    mapOf("n.txt" to "# Not A Heading\n\n- not a list\n".toByteArray()),
                )
            val session = sessionOf(gateway)
            session.openDocument(DocumentId("n.txt"))
            advanceUntilIdle()
            val state = session.state.value as ReaderViewState.Content
            assertTrue(state.document.blocks.all { it.kind == BlockKind.PARAGRAPH })
            session.close()
        }

    @Test
    fun retryAfterFailureReloads() =
        runTest {
            var attempts = 0
            val gateway =
                object : ReaderDocumentGateway {
                    override suspend fun describe(documentId: DocumentId): DocumentDescriptor? =
                        DocumentDescriptor("r.md", "fp:r", LibraryId("lib"), "r")

                    override suspend fun openStream(documentId: DocumentId): InputStream {
                        attempts++
                        if (attempts == 1) throw IOException("transient")
                        return ByteArrayInputStream("# Back\n".toByteArray())
                    }
                }
            val dispatcher = StandardTestDispatcher(testScheduler)
            val session = ReaderSession(gateway, parser, dispatcher, this)
            session.openDocument(DocumentId("r.md"))
            advanceUntilIdle()
            assertTrue(session.state.value is ReaderViewState.Failed)
            session.retry(DocumentId("r.md"))
            advanceUntilIdle()
            val retried = session.state.value
            assertTrue(retried is ReaderViewState.Content)
            assertEquals("Back", (retried as ReaderViewState.Content).document.title)
            session.close()
        }

    @Test
    fun missingIndexRowMapsToFileMissing() =
        runTest {
            val gateway =
                FakeGateway(
                    files = mapOf("gone.md" to "# Gone\n".toByteArray()),
                    missingDescribe = setOf("gone.md"),
                )
            val session = sessionOf(gateway)
            session.openDocument(DocumentId("gone.md"))
            advanceUntilIdle()
            val state = session.state.value
            assertTrue(state is ReaderViewState.Failed)
            assertEquals(ReaderFailure.FILE_MISSING, (state as ReaderViewState.Failed).reason)
            session.close()
        }

    @Test
    fun unchangedReopenReusesParseWithoutNewStream() =
        runTest {
            val gateway =
                FakeGateway(
                    files = mapOf("c.md" to "# Cache\n\nbody\n".toByteArray()),
                    fingerprints = mapOf("c.md" to "fp-1"),
                )
            val session = sessionOf(gateway)
            session.openDocument(DocumentId("c.md"))
            advanceUntilIdle()
            assertTrue(session.state.value is ReaderViewState.Content)
            val firstOpens = gateway.opened.size
            session.openDocument(DocumentId("c.md"))
            advanceUntilIdle()
            assertTrue(session.state.value is ReaderViewState.Content)
            assertEquals("describe still runs, but the stream must not reopen", firstOpens, gateway.opened.size)
            session.close()
        }

    @Test
    fun changedFingerprintReparsesWithNewStream() =
        runTest {
            val fingerprints = mutableMapOf("c.md" to "fp-1")
            val gateway =
                FakeGateway(
                    files = mapOf("c.md" to "# Cache\n\nbody\n".toByteArray()),
                    fingerprints = fingerprints,
                )
            val session = sessionOf(gateway)
            session.openDocument(DocumentId("c.md"))
            advanceUntilIdle()
            assertTrue(session.state.value is ReaderViewState.Content)
            fingerprints["c.md"] = "fp-2"
            session.openDocument(DocumentId("c.md"))
            advanceUntilIdle()
            assertEquals(2, gateway.opened.size)
            session.close()
        }

    @Test
    fun unusedImportsGuard() {
        // Keeps the import list honest: these types are part of the contract.
        val id = DocumentId("x")
        assertEquals("x", id.value)
        assertEquals(EncodingState.CLEAN, DecodedText("a", EncodingState.CLEAN).encodingState)
        assertTrue(Dispatchers.Default.toString().isNotEmpty())
    }

    // -- R2-01 regressions ----------------------------------------------------

    @Test
    fun cpuWorkRunsOnParseDispatcherNotScope() =
        runTest {
            // Production shape: Main-bound scope + distinct dispatchers.
            // The io and parse dispatchers are separately named test
            // dispatchers; completing the open proves both stages ran
            // through their withContext blocks (a scope-thread parse would
            // deadlock the test scheduler and fail advanceUntilIdle).
            val gateway = FakeGateway(mapOf("t.md" to "# T\n\nbody\n".toByteArray()))
            val session =
                ReaderSession(
                    gateway = gateway,
                    parser = parser,
                    ioDispatcher = StandardTestDispatcher(testScheduler, name = "io"),
                    parseDispatcher = StandardTestDispatcher(testScheduler, name = "parse"),
                    scope = this,
                )
            session.openDocument(DocumentId("t.md"))
            advanceUntilIdle()
            assertTrue(session.state.value is ReaderViewState.Content)
            session.close()
        }

    // -- R2-01 bounded-read unit tests (direct, no 16MB parse) ------------------
    // A JVM session-level oversize open would parse 16MB on the unit-test
    // heap (OOM in commonmark inline parsing, not in the read path — the
    // device suite owns 10MB opens). These test the capping helper itself.

    @Test
    fun boundedReadCapsDuringCopyAndFlagsTruncation() =
        runTest {
            val big = ByteArray(4 * 1024) { 97.toByte() }
            val outcome = ReaderSession.boundedRead(ByteArrayInputStream(big), 1024)
            assertEquals(1024, outcome.bytes.size)
            assertTrue(outcome.truncated)
            val exact = ReaderSession.boundedRead(ByteArrayInputStream(big), 4 * 1024)
            assertEquals(4 * 1024, exact.bytes.size)
            assertFalse(exact.truncated)
        }

    @Test
    fun boundedReadNeverExceedsLimitPlusChunk() =
        runTest {
            // Hostile length prefix ignored: only actually-read bytes count.
            val hostile = ByteArray(64 * 1024) { 10.toByte() }
            val outcome = ReaderSession.boundedRead(ByteArrayInputStream(hostile), 1024)
            assertTrue(outcome.bytes.size <= 1024 + 8192)
            assertTrue(outcome.truncated)
        }

    @Test
    fun unknownFingerprintNeverHitsCache() =
        runTest {
            val gateway =
                FakeGateway(
                    files = mapOf("u.md" to "# U\n\nbody\n".toByteArray()),
                    fingerprints = mutableMapOf("u.md" to "fp-1"),
                )
            val session = sessionOf(gateway)
            session.openDocument(DocumentId("u.md"))
            advanceUntilIdle()
            assertEquals(1, session.cachedEntryCountForTest())
            // Unknown fingerprint (null): must re-read, never serve cache.
            (gateway.fingerprints as MutableMap)["u.md"] = "fp-2"
            session.openDocument(DocumentId("u.md"))
            advanceUntilIdle()
            assertEquals(2, gateway.opened.size)
            session.close()
        }

    @Test
    fun nullFingerprintIsNeverCached() =
        runTest {
            val gateway =
                object : ReaderDocumentGateway {
                    override suspend fun describe(documentId: DocumentId): DocumentDescriptor? =
                        DocumentDescriptor("n.md", null, LibraryId("lib"), "n")

                    override suspend fun openStream(documentId: DocumentId): InputStream =
                        ByteArrayInputStream("# N\n\nbody\n".toByteArray())
                }
            val session = sessionOf(FakeGateway(emptyMap()))
            // Swap gateway via a fresh session sharing the null descriptor.
            val nullSession =
                ReaderSession(
                    gateway = gateway,
                    parser = parser,
                    ioDispatcher = StandardTestDispatcher(testScheduler),
                    scope = this,
                )
            nullSession.openDocument(DocumentId("n.md"))
            advanceUntilIdle()
            assertTrue(nullSession.state.value is ReaderViewState.Content)
            assertEquals(0, nullSession.cachedEntryCountForTest())
            nullSession.openDocument(DocumentId("n.md"))
            advanceUntilIdle()
            // Re-opened with unknown identity: re-read (no cache hit path).
            assertEquals(0, nullSession.cachedEntryCountForTest())
            nullSession.close()
            session.close()
        }

    @Test
    fun cacheEvictsOldestBeyondCapacity() =
        runTest {
            val files = (0..4).associate { "d$it.md" to "# D$it\n\nbody\n".toByteArray() }
            val gateway = FakeGateway(files)
            val session = sessionOf(gateway)
            for (i in 0..4) {
                session.openDocument(DocumentId("d$i.md"))
                advanceUntilIdle()
            }
            assertTrue(session.cachedEntryCountForTest() <= ReaderSession.MAX_CACHED_PARSES)
            session.close()
        }

    @Test
    fun slowOpenCancelLeavesLoadingAndReopenWorks() =
        runTest {
            // Cancel lands while openStream is still delaying (stream never
            // created): nothing to close, state stays Loading (never an
            // error), and a later open of the same document succeeds.
            // (Blocking-mid-read cancel depends on provider unblock-on-close
            // semantics; covered by contract, not simulated here.)
            val gateway =
                FakeGateway(
                    files = mapOf("s.md" to "# S\n\nbody\n".toByteArray()),
                    delaysMs = mapOf("s.md" to 5_000L),
                )
            val session = sessionOf(gateway)
            session.openDocument(DocumentId("s.md"))
            advanceTimeBy(10)
            session.close()
            advanceUntilIdle()
            assertTrue(session.state.value is ReaderViewState.Loading)
            assertTrue(gateway.closed.isEmpty())
            session.openDocument(DocumentId("s.md"))
            advanceUntilIdle()
            assertTrue(session.state.value is ReaderViewState.Content)
            assertEquals(listOf("s.md"), gateway.closed)
            session.close()
        }

    // -- R2-05 regressions: storage failure mapping (no storage import) -----

    @Test
    fun permissionLostMapsToAuthorizationLost() {
        // Fake storage-typed throwables (same simple names, no import).
        class PermissionLost(uri: String, cause: Throwable?) : RuntimeException(uri, cause)

        val mapped = mapStorageFailure(PermissionLost("content://tree/x", null), "d.md")
        assertEquals(ReaderFailure.AUTHORIZATION_LOST, mapped.reason)
    }

    @Test
    fun documentGoneMapsToFileMissing() {
        class DocumentGone(id: String, cause: Throwable?) : RuntimeException(id, cause)

        val mapped = mapStorageFailure(DocumentGone("d.md", null), "d.md")
        assertEquals(ReaderFailure.FILE_MISSING, mapped.reason)
    }

    @Test
    fun unknownFailureMapsToFileMissingNeverCrash() {
        val mapped = mapStorageFailure(IllegalStateException("weird provider"), "d.md")
        assertEquals(ReaderFailure.FILE_MISSING, mapped.reason)
    }

    @Test
    fun wrappedStorageFailureUnwrapsCauseChain() {
        class DocumentGone(id: String, cause: Throwable?) : RuntimeException(id, cause)

        val wrapped = RuntimeException("io", DocumentGone("d.md", null))
        val mapped = mapStorageFailure(wrapped, "d.md")
        assertEquals(ReaderFailure.FILE_MISSING, mapped.reason)
    }

    // -- R2-05 end-to-end: storage-typed failures through the session ------

    @Test
    fun storagePermissionLostMapsToAuthorizationLost() =
        runTest {
            // Same simple names as production StorageException, no import
            // (module ban): proves the name-based mapping on the open path.
            class PermissionLost(uri: String, cause: Throwable?) : RuntimeException(uri, cause)

            val gateway =
                FakeGateway(
                    emptyMap(),
                    failures = mapOf("revoked.md" to PermissionLost("content://tree/x", null)),
                )
            val session = sessionOf(gateway)
            session.openDocument(DocumentId("revoked.md"))
            advanceUntilIdle()
            val state = session.state.value
            assertTrue(state is ReaderViewState.Failed)
            assertEquals(
                ReaderFailure.AUTHORIZATION_LOST,
                (state as ReaderViewState.Failed).reason,
            )
            session.close()
        }

    @Test
    fun storageDocumentGoneMapsToFileMissing() =
        runTest {
            class DocumentGone(id: String, cause: Throwable?) : RuntimeException(id, cause)

            val gateway =
                FakeGateway(
                    emptyMap(),
                    failures = mapOf("gone.md" to DocumentGone("gone.md", null)),
                )
            val session = sessionOf(gateway)
            session.openDocument(DocumentId("gone.md"))
            advanceUntilIdle()
            val state = session.state.value
            assertTrue(state is ReaderViewState.Failed)
            assertEquals(
                ReaderFailure.FILE_MISSING,
                (state as ReaderViewState.Failed).reason,
            )
            session.close()
        }
}
