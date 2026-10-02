package io.leaflet.reader.review

import android.database.Cursor
import android.net.Uri
import androidx.lifecycle.ViewModelStore
import io.leaflet.markdown.LeafletDocumentParser
import io.leaflet.model.DocumentId
import io.leaflet.model.LibraryId
import io.leaflet.model.ReadingPosition
import io.leaflet.model.RecentRead
import io.leaflet.reader.DocumentDescriptor
import io.leaflet.reader.ui.reader.*
import io.leaflet.storage.scan.ContentResolverTreeLister
import io.leaflet.storage.scan.TreeEntry
import io.leaflet.storage.scan.classifyTreeRow
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.lang.reflect.Proxy
import sun.misc.Unsafe

@OptIn(ExperimentalCoroutinesApi::class)
class RepairAndProjectionAcceptanceTest {
    private class Store : ReaderStore {
        val olderGate = CompletableDeferred<Unit>()
        val repairGate = CompletableDeferred<Unit>()
        var repairStarted = false
        var block5Calls = 0
        val writes = mutableListOf<ReadingPosition>()
        private val body = (1..8).joinToString("\n\n") { "Paragraph $it" }.toByteArray()
        override suspend fun openStream(documentId: DocumentId) = ByteArrayInputStream(body)
        override suspend fun describe(documentId: DocumentId) = DocumentDescriptor("a.txt", "42:${body.size}", LibraryId("lib"), "a")
        override suspend fun loadPosition(documentId: DocumentId): StoredPosition? = null
        override suspend fun resolveRelative(fromId: DocumentId, ref: String): io.leaflet.storage.ResolvedResource? = null
        override suspend fun recordRecent(entry: RecentRead) = Unit
        override suspend fun savePosition(position: ReadingPosition, lastModified: Long, size: Long) {
            if (position.blockIndex == 2) olderGate.await()
            if (position.blockIndex == 5 && ++block5Calls == 2) {
                repairStarted = true
                repairGate.await()
            }
            writes.add(position)
        }
    }

    @Test
    fun aCompensatingWriteMustNotOverwriteAnEvenNewerJumpAndFlush() {
        val main = TestCoroutineScheduler(); val io = TestCoroutineScheduler()
        val owner = ViewModelStore()
        Dispatchers.setMain(StandardTestDispatcher(main))
        try {
            val store = Store()
            val vm = ReaderViewModel(store, LeafletDocumentParser.createForJvmTests(), StandardTestDispatcher(io))
            owner.put("repair-acceptance", vm)
            vm.open(DocumentId("a"))
            repeat(20) { main.runCurrent(); io.runCurrent(); main.advanceTimeBy(16) }
            assertTrue(vm.ui.value is ReaderUiState.Content)
            vm.jumpToBlock(2); io.runCurrent()
            vm.jumpToBlock(5); io.runCurrent()
            store.olderGate.complete(Unit); io.runCurrent()
            assertTrue("the repair of block 5 is held in the store", store.repairStarted)
            vm.jumpToBlock(6); io.runCurrent()
            val flush = vm.flushPosition(); io.runCurrent()
            assertTrue("newest exit flush completed", flush.isCompleted)
            assertEquals(6, store.writes.last().blockIndex)
            store.repairGate.complete(Unit); io.runCurrent()
            assertEquals("repair must obey the same latest-request rule; writes=${store.writes.map { it.blockIndex }}", 6, store.writes.last().blockIndex)
        } finally {
            owner.clear(); main.runCurrent(); io.runCurrent(); Dispatchers.resetMain()
        }
    }

    @Test
    fun productionCursorProjectionMustPreserveUnknownType() {
        // Missing ID avoids Android URI construction; the final row-construction
        // expression is shared with valid-ID rows. This checks real projection,
        // not only the pure classifier which already passes its tests.
        val columns = listOf("document_id", "_display_name", "mime_type")
        val values = listOf(null, "folder", null)
        var moved = false
        val cursor = Proxy.newProxyInstance(Cursor::class.java.classLoader, arrayOf(Cursor::class.java)) { _, method, args ->
            when (method.name) {
                "getColumnIndex" -> columns.indexOf(args!![0] as String)
                "getString" -> values[args!![0] as Int]
                "moveToNext" -> (!moved).also { moved = true }
                else -> error("Unexpected cursor call: ${method.name}")
            }
        } as Cursor
        val classified = classifyTreeRow(null, "folder", null, null, null)
        assertTrue("the classifier knows the type is unknown", classified.typeUnknown)
        val field = Unsafe::class.java.getDeclaredField("theUnsafe").apply { isAccessible = true }
        val reader = (field.get(null) as Unsafe).allocateInstance(ContentResolverTreeLister::class.java)
        val method = ContentResolverTreeLister::class.java.getDeclaredMethod("readEntries", Uri::class.java, Cursor::class.java).apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val parsed = (method.invoke(reader, null, cursor) as List<TreeEntry>).single()
        assertEquals("cursor projection must preserve the classifier's unknown-type flag", classified.typeUnknown, parsed.typeUnknown)
    }
}
