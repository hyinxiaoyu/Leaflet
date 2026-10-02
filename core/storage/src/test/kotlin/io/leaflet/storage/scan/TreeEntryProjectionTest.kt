package io.leaflet.storage.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Third independent-acceptance counterexample for CR-20261001-05 (the
 * unknown-type flag was lost in the parser's row → entry projection).
 *
 * The scanner acts on the entry the PRODUCTION cursor parser emits, not on
 * `classifyTreeRow`'s result directly. `readTreeEntry` is exactly that
 * projection — the parser calls it once per cursor row — and it derives the
 * emitted entry from the classifier, so a field added to [TreeEntry] cannot be
 * dropped on the way to the scanner. Rebuilding the entry field by field there
 * (which dropped `typeUnknown`) was the rejected behavior.
 *
 * The private cursor loop itself is exercised by the review's retained
 * `RepairAndProjectionAcceptanceTest`, which allocates the lister with
 * `sun.misc.Unsafe`; that class is not on this module's unit-test compile
 * classpath, so this test pins the projection with plain row values.
 */
class TreeEntryProjectionTest {
    private fun project(
        documentId: String?,
        displayName: String?,
        mimeType: String?,
    ): TreeEntry = readTreeEntry(documentId, displayName, mimeType, null, null) { id -> "content://doc/$id" }

    @Test
    fun theProjectionKeepsTheClassifiersUnknownTypeFlag() {
        val classified = classifyTreeRow("dir-id", "folder", null, null, null)
        assertTrue("the classifier knows the type is unknown", classified.typeUnknown)

        val emitted = project("dir-id", "folder", null)

        assertTrue("the emitted entry must keep the unknown-type flag", emitted.typeUnknown)
        assertFalse("an unknown type is not a directory", emitted.isDirectory)
        assertEquals("the known id survives", "dir-id", emitted.documentId)
        assertEquals("the known name survives", "folder", emitted.displayName)
    }

    @Test
    fun theProjectionKeepsAStatedType() {
        val file = project("file-id", "keep.md", "text/markdown")
        assertFalse("a stated file type is not unknown", file.typeUnknown)
        assertFalse(file.isDirectory)
        assertEquals("content://doc/file-id", file.documentUri)

        val directory = project("dir-id", "folder", "vnd.android.document/directory")
        assertTrue("a stated directory MIME stays a directory", directory.isDirectory)
        assertFalse("a stated type is not unknown", directory.typeUnknown)
    }

    @Test
    fun theProjectionDropsTheUriForARowWithoutAnIdentity() {
        val emitted = project(null, "folder", "vnd.android.document/directory")
        assertEquals("no id means no document URI", "", emitted.documentUri)
        assertTrue("the stated directory type survives", emitted.isDirectory)
    }
}
