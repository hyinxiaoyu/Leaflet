package io.leaflet.reader.ui.library

import io.leaflet.model.Document
import io.leaflet.model.DocumentId
import io.leaflet.model.LibraryId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BrowseLevelTest {
    @Test
    fun rootListsDirectoriesBeforeFilesByName() {
        val rows =
            browseLevel(
                listOf(
                    doc("z.md"),
                    doc("a/b/c.md"),
                    doc("a/d.md"),
                    doc("M.md"),
                    doc("b/only.md"),
                ),
                prefix = "",
            )

        assertEquals(listOf("dir:a", "dir:b", "doc:M.md", "doc:z.md"), rows.map { it.key })
        assertEquals("a", (rows[0] as BrowseLevelRow.Directory).name)
        assertEquals("M.md", (rows[2] as BrowseLevelRow.File).document.displayName)
    }

    @Test
    fun nestedPrefixHidesDeeperFilesAndSiblingTrees() {
        val rows =
            browseLevel(
                listOf(
                    doc("a/b/c.md"),
                    doc("a/d.md"),
                    doc("a/b-extra/x.md"),
                    doc("z.md"),
                ),
                prefix = "a",
            )

        assertEquals(listOf("dir:a/b", "dir:a/b-extra", "doc:a/d.md"), rows.map { it.key })
    }

    @Test
    fun namesSortCaseInsensitivelyThenByRawName() {
        val rows =
            browseLevel(
                listOf(doc("dir/b.md"), doc("dir/B.md"), doc("Dir/nested/a.md"), doc("alpha/a.md")),
                prefix = "",
            )

        assertEquals(listOf("dir:alpha", "dir:Dir", "dir:dir"), rows.map { it.key })
        val files = browseLevel(listOf(doc("b.md"), doc("B.md")), prefix = "").map { it.key }
        assertEquals(listOf("doc:B.md", "doc:b.md"), files)
    }

    @Test
    fun chineseNamesStayInTheSameNameOrder() {
        val rows =
            browseLevel(
                listOf(doc("资料/说明.md"), doc("笔记.md"), doc("资料/图片/图.md")),
                prefix = "资料",
            )

        assertEquals(listOf("dir:资料/图片", "doc:资料/说明.md"), rows.map { it.key })
    }

    // -- LT-3 (REQ-0001 A1): indexed empty directories -------------------------

    @Test
    fun emptyDirectoriesAppearBesideDocumentDirectoriesAtTheRoot() {
        val rows =
            browseLevel(
                documents = listOf(doc("notes/a.md")),
                prefix = "",
                directories = listOf("Empty", "notes/sub", "untuched"),
            )

        assertEquals(listOf("dir:Empty", "dir:notes", "dir:untuched"), rows.map { it.key })
        assertEquals("Empty", (rows[0] as BrowseLevelRow.Directory).name)
    }

    @Test
    fun emptyDirectoryIsShownOnlyAtItsOwnParentLevel() {
        val rows =
            browseLevel(
                documents = emptyList(),
                prefix = "notes",
                directories = listOf("notes/drafts", "notes/drafts/deep", "other"),
            )

        assertEquals(listOf("dir:notes/drafts"), rows.map { it.key })
        // Inside that folder the next level shows only its own child, so the
        // screen's existing empty placeholder appears one level deeper.
        assertEquals(listOf("dir:notes/drafts/deep"), browseLevel(emptyList(), "notes/drafts", listOf("notes/drafts/deep")).map { it.key })
        assertEquals(emptyList<String>(), browseLevel(emptyList(), "notes/drafts/deep", listOf("notes/drafts/deep")).map { it.key })
    }

    @Test
    fun derivedAndIndexedDirectoriesDoNotDuplicate() {
        val rows =
            browseLevel(
                documents = listOf(doc("notes/a.md"), doc("notes/sub/b.md")),
                prefix = "",
                directories = listOf("notes", "notes/sub"),
            )

        assertEquals(listOf("dir:notes"), rows.map { it.key })
        assertEquals(
            listOf("dir:notes/sub", "doc:notes/a.md"),
            browseLevel(listOf(doc("notes/a.md"), doc("notes/sub/b.md")), "notes", listOf("notes", "notes/sub")).map { it.key },
        )
    }

    @Test
    fun indexedDirectoriesKeepTheNameOrder() {
        val rows =
            browseLevel(
                documents = listOf(doc("Beta/a.md")),
                prefix = "",
                directories = listOf("alpha", "Beta", "笔记"),
            )

        assertEquals(listOf("dir:alpha", "dir:Beta", "dir:笔记"), rows.map { it.key })
    }

    @Test
    fun prefixNavigationStepsOneSegment() {
        assertNull(parentPrefix(""))
        assertNull(parentPrefix("/"))
        assertEquals("", parentPrefix("notes"))
        assertEquals("notes", parentPrefix("notes/drafts"))
        assertEquals("notes/drafts", childPrefix("notes", "drafts"))
        assertEquals("drafts", childPrefix("", "drafts"))
    }

    private fun doc(path: String) =
        Document(
            id = DocumentId(path),
            libraryId = LibraryId("lib"),
            displayName = path.substringAfterLast('/'),
            relativePath = path,
            uri = "content://$path",
            extension = "md",
        )
}
