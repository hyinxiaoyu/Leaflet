package io.leaflet.reader.ui.reader

import androidx.lifecycle.ViewModelStore
import io.leaflet.markdown.BlockLink
import io.leaflet.markdown.DocLinkKind
import io.leaflet.markdown.LeafletDocumentParser
import io.leaflet.model.DocumentId
import io.leaflet.model.LibraryId
import io.leaflet.model.ReadingPosition
import io.leaflet.model.RecentRead
import io.leaflet.reader.DocumentDescriptor
import io.leaflet.storage.ResolvedResource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderContinuationFixTest {
    private class Store : ReaderStore {
        val body = (1..8).joinToString("\n\n") { "Paragraph $it ${"x".repeat(700)}" }.toByteArray()
        val fingerprint = "42:${body.size}"
        val rows = mutableMapOf<String, StoredPosition>()
        val oldGate = CompletableDeferred<Unit>()
        val linkGate = CompletableDeferred<Unit>()
        var holdOldA = false
        var resolutionStarted = false

        override suspend fun openStream(documentId: DocumentId) = ByteArrayInputStream(body)

        override suspend fun describe(documentId: DocumentId) =
            DocumentDescriptor(
                "${documentId.value}.txt",
                fingerprint,
                LibraryId("lib"),
                documentId.value,
            )

        override suspend fun loadPosition(documentId: DocumentId) = rows[documentId.value]

        override suspend fun recordRecent(entry: RecentRead) = Unit

        override suspend fun savePosition(
            position: ReadingPosition,
            lastModified: Long,
            size: Long,
        ) {
            if (holdOldA && position.documentId.value == "a" && position.blockIndex == 2) oldGate.await()
            rows[position.documentId.value] = StoredPosition(position, "$lastModified:$size")
        }

        override suspend fun resolveRelative(
            fromId: DocumentId,
            ref: String,
        ): ResolvedResource {
            resolutionStarted = true
            linkGate.await()
            return ResolvedResource(DocumentId("target"), "target.txt")
        }
    }

    private fun withVm(test: (Store, ReaderViewModel, TestCoroutineScheduler, TestCoroutineScheduler) -> Unit) {
        val main = TestCoroutineScheduler()
        val io = TestCoroutineScheduler()
        val owner = ViewModelStore()
        Dispatchers.setMain(StandardTestDispatcher(main))
        val store = Store()
        val vm = ReaderViewModel(store, LeafletDocumentParser.createForJvmTests(), StandardTestDispatcher(io))
        owner.put("continuation-review", vm)
        try {
            test(store, vm, main, io)
        } finally {
            owner.clear()
            main.runCurrent()
            io.runCurrent()
            Dispatchers.resetMain()
        }
    }

    private fun open(
        vm: ReaderViewModel,
        id: String,
        main: TestCoroutineScheduler,
        io: TestCoroutineScheduler,
    ) {
        vm.open(DocumentId(id))
        repeat(20) {
            main.runCurrent()
            io.runCurrent()
            main.advanceTimeBy(16)
        }
        val state = vm.ui.value as ReaderUiState.Content
        assertEquals(DocumentId(id), state.document.documentId)
    }

    @Test
    fun aNoMovementFlushMustPreserveTheStoredInBlockOffset() =
        withVm { store, vm, main, io ->
            store.rows["a"] =
                StoredPosition(
                    ReadingPosition(DocumentId("a"), blockIndex = 2, blockOffset = 300, progress = 2f / 7),
                    store.fingerprint,
                )
            open(vm, "a", main, io)
            assertEquals(2, (vm.ui.value as ReaderUiState.Content).restoreBlock)
            assertEquals(300, store.rows.getValue("a").position.blockOffset)
            val flush = vm.flushPosition()
            io.runCurrent()
            assertTrue(flush.isCompleted)
            assertEquals(
                "opening and flushing without movement must not erase a valid offset",
                300,
                store.rows.getValue("a").position.blockOffset,
            )
        }

    @Test
    fun aLinkResolvedAfterADocumentSwitchMustNotNavigateFromTheOldDocument() =
        withVm { store, vm, main, io ->
            open(vm, "a", main, io)
            val opened = mutableListOf<DocumentId>()
            vm.followLink(BlockLink("target", "target.txt", DocLinkKind.LIBRARY_DOC, "target.txt"), { opened.add(it) }, {})
            main.runCurrent()
            io.runCurrent()
            assertTrue("A's link is resolving", store.resolutionStarted)
            open(vm, "b", main, io)
            store.linkGate.complete(Unit)
            io.runCurrent()
            main.runCurrent()
            assertEquals("A's delayed navigation must not override B", emptyList<String>(), opened.map { it.value })
        }

    @Test
    fun sameBlockObservationAndFlushKeepTheFullLogicalAddress() =
        withVm { store, vm, main, io ->
            store.rows["a"] =
                StoredPosition(
                    ReadingPosition(DocumentId("a"), blockIndex = 2, blockOffset = 300),
                    store.fingerprint,
                )
            open(vm, "a", main, io)
            vm.onVisibleBlock(2)
            val unchanged = vm.flushPosition()
            io.runCurrent()
            assertTrue(unchanged.isCompleted)
            assertEquals(300, store.rows.getValue("a").position.blockOffset)
            vm.onVisiblePosition(2, 500)
            assertEquals(500, vm.readingLocationFor(DocumentId("a"))?.blockOffset)
            assertEquals(null, vm.readingLocationFor(DocumentId("other")))
            val moved = vm.flushPosition()
            io.runCurrent()
            assertTrue(moved.isCompleted)
            assertEquals(500, store.rows.getValue("a").position.blockOffset)
            vm.jumpToBlock(3)
            io.runCurrent()
            assertEquals(0, store.rows.getValue("a").position.blockOffset)
        }

    @Test
    fun aDisposedDocumentsObservationCannotChangeTheNewDocumentPosition() =
        withVm { store, vm, main, io ->
            open(vm, "a", main, io)
            open(vm, "b", main, io)
            vm.onVisiblePosition(7, 600, DocumentId("a"))
            val flush = vm.flushPosition()
            io.runCurrent()
            assertTrue(flush.isCompleted)
            assertEquals(0, store.rows.getValue("b").position.blockIndex)
            assertEquals(0, store.rows.getValue("b").position.blockOffset)
        }

    @Test
    fun leavingTheReaderInvalidatesPendingLinkNavigation() =
        withVm { store, vm, main, io ->
            open(vm, "a", main, io)
            val opened = mutableListOf<DocumentId>()
            vm.followLink(BlockLink("target", "target.txt", DocLinkKind.LIBRARY_DOC, "target.txt"), { opened.add(it) }, {})
            main.runCurrent()
            io.runCurrent()
            assertTrue(store.resolutionStarted)
            vm.onReaderExit(DocumentId("a"))
            store.linkGate.complete(Unit)
            io.runCurrent()
            main.runCurrent()
            assertTrue(opened.isEmpty())
        }

    @Test
    fun aLegitimateFragmentBelongsToItsDestinationOnly() =
        withVm { store, vm, main, io ->
            open(vm, "a", main, io)
            val opened = mutableListOf<DocumentId>()
            store.linkGate.complete(Unit)
            vm.followLink(
                BlockLink("target", "target.txt#section", DocLinkKind.LIBRARY_DOC, "target.txt", "section"),
                { opened.add(it) },
                {},
            )
            main.runCurrent()
            io.runCurrent()
            main.runCurrent()
            assertEquals(listOf(DocumentId("target")), opened)
            assertEquals(null, vm.takePendingFragment())
            vm.onReaderExit(DocumentId("a"))
            open(vm, "target", main, io)
            assertEquals(null, vm.takePendingFragment(DocumentId("a")))
            assertEquals("section", vm.takePendingFragment())
            assertEquals(null, vm.takePendingFragment())
        }

    @Test
    fun pendingOlderWritesMustNotLoseTheirRepairTargetAfterEightOtherDocuments() =
        withVm { store, vm, main, io ->
            open(vm, "a", main, io)
            store.holdOldA = true
            vm.jumpToBlock(2)
            io.runCurrent()
            vm.jumpToBlock(5)
            io.runCurrent()
            assertEquals(5, store.rows.getValue("a").position.blockIndex)
            for (i in 1..8) {
                open(vm, "later-$i", main, io)
                vm.jumpToBlock(3)
                io.runCurrent()
            }
            assertEquals("A's newer address is still stored before release", 5, store.rows.getValue("a").position.blockIndex)
            store.oldGate.complete(Unit)
            io.runCurrent()
            assertEquals("eviction must not discard ordering for an in-flight document", 5, store.rows.getValue("a").position.blockIndex)
        }
}
