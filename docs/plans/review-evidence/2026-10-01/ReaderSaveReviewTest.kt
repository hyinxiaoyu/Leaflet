package io.leaflet.reader.review

import androidx.lifecycle.ViewModelStore
import io.leaflet.markdown.LeafletDocumentParser
import io.leaflet.model.DocumentId
import io.leaflet.model.LibraryId
import io.leaflet.model.ReadingPosition
import io.leaflet.model.RecentRead
import io.leaflet.reader.DocumentDescriptor
import io.leaflet.reader.ui.reader.ReaderStore
import io.leaflet.reader.ui.reader.ReaderUiState
import io.leaflet.reader.ui.reader.ReaderViewModel
import io.leaflet.reader.ui.reader.StoredPosition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderSaveReviewTest {
    private class Store : ReaderStore {
        val saved = mutableListOf<ReadingPosition>()
        val body = (1..8).joinToString("\n\n") { "Paragraph $it alpha beta" }.toByteArray()
        override suspend fun openStream(documentId: DocumentId) = ByteArrayInputStream(body)
        override suspend fun describe(documentId: DocumentId) =
            DocumentDescriptor("${documentId.value}.txt", "42:${body.size}", LibraryId("review"), documentId.value)
        override suspend fun loadPosition(documentId: DocumentId): StoredPosition? = null
        override suspend fun resolveRelative(fromId: DocumentId, ref: String): io.leaflet.storage.ResolvedResource? = null
        override suspend fun savePosition(position: ReadingPosition, lastModified: Long, size: Long) { saved.add(position) }
        override suspend fun recordRecent(entry: RecentRead) = Unit
    }

    @Test
    fun queuedSaveMustKeepTheInvocationTimeBlockAfterDocumentSwitch() {
        val mainScheduler = TestCoroutineScheduler()
        val main = StandardTestDispatcher(mainScheduler)
        val ioScheduler = TestCoroutineScheduler()
        val io = StandardTestDispatcher(ioScheduler)
        Dispatchers.setMain(main)
        val store = Store()
        val vm = ReaderViewModel(store, LeafletDocumentParser.createForJvmTests(), io)
        val owner = ViewModelStore().apply { put("review", vm) }
        try {
            runTest(main) {
                vm.open(DocumentId("a"))
                repeat(20) {
                    mainScheduler.runCurrent()
                    ioScheduler.runCurrent()
                    mainScheduler.advanceTimeBy(16)
                }
                assertTrue("A must be fully loaded before the race", vm.ui.value is ReaderUiState.Content)
                vm.jumpToBlock(5)
                // Deliberately do not run IO: its save is now queued.
                vm.open(DocumentId("b"))
                assertEquals(ReaderUiState.Loading, vm.ui.value)
                ioScheduler.runCurrent()
                val a = store.saved.single { it.documentId == DocumentId("a") }
                assertEquals("A save must keep block 5 captured when it was requested", 5, a.blockIndex)
            }
        } finally {
            owner.clear()
            mainScheduler.runCurrent()
            ioScheduler.runCurrent()
            Dispatchers.resetMain()
        }
    }
}
