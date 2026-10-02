package io.leaflet.reader.ui.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * NB-01 pure mapping (REQ-0012 FR-1/FR-2, AC-1/AC-2/AC-3): breadcrumb
 * segments and the guarded target predicate. No ViewModel, no Compose.
 */
class BreadcrumbTest {
    @Test
    fun rootPrefixYieldsOnlyTheLibraryRoot() {
        val segments = breadcrumbSegments("", "Library")

        assertEquals(listOf(BreadcrumbSegment("Library", "", true)), segments)
    }

    @Test
    fun threeLevelPrefixYieldsTheRootAndEveryAncestor() {
        val segments = breadcrumbSegments("Docs/Notes/2026", "Library")

        assertEquals(
            listOf(
                "Library" to "",
                "Docs" to "Docs",
                "Notes" to "Docs/Notes",
                "2026" to "Docs/Notes/2026",
            ),
            segments.map { it.label to it.target },
        )
        assertEquals(listOf(false, false, false, true), segments.map { it.isCurrent })
    }

    @Test
    fun surroundingSlashesDoNotChangeTheResult() {
        assertEquals(
            breadcrumbSegments("Docs/Notes", "Library"),
            breadcrumbSegments("/Docs/Notes/", "Library"),
        )
    }

    @Test
    fun tenLevelPathKeepsEveryAncestorReachable() {
        val prefix = (1..10).joinToString("/") { "L$it" }

        val segments = breadcrumbSegments(prefix, "Library")

        assertEquals(11, segments.size)
        assertEquals("", segments.first().target)
        assertEquals(prefix, segments.last().target)
        assertTrue(segments.last().isCurrent)
        assertEquals(
            (1..10).map { (1..it).joinToString("/") { n -> "L$n" } },
            segments.drop(1).map { it.target },
        )
    }

    @Test
    fun namesKeepCaseSpacesChineseAndLiteralPercent() {
        val segments = breadcrumbSegments("工作 笔记/50% 覆盖率/Draft A", "资料库")

        assertEquals(
            listOf("工作 笔记", "50% 覆盖率", "Draft A"),
            segments.drop(1).map { it.label },
        )
        assertEquals("工作 笔记/50% 覆盖率/Draft A", segments.last().target)
        // No URI decoding: the literal percent sequence survives verbatim.
        assertTrue(segments.any { it.label == "50% 覆盖率" })
    }

    @Test
    fun rootAndEveryAncestorOfTheCurrentPrefixAreAccepted() {
        assertTrue(isAncestorTarget("Docs/Notes/2026", ""))
        assertTrue(isAncestorTarget("Docs/Notes/2026", "Docs"))
        assertTrue(isAncestorTarget("Docs/Notes/2026", "Docs/Notes"))
        assertTrue(isAncestorTarget("Docs/Notes/2026", "Docs/Notes/2026"))
        assertTrue(isAncestorTarget("", ""))
    }

    @Test
    fun siblingEscapeAndUnrelatedTargetsAreRejected() {
        assertFalse(isAncestorTarget("Docs/Notes", "Docs/Other"))
        assertFalse(isAncestorTarget("Docs/Notes", "Other"))
        assertFalse(isAncestorTarget("Docs/Notes", ".."))
        assertFalse(isAncestorTarget("Docs/Notes", "../Docs"))
        assertFalse(isAncestorTarget("Docs/Notes", "Docs/Notes/2026"))
        assertFalse(
            isAncestorTarget(
                "Docs",
                "content://com.android.externalstorage.documents/tree/primary%3ADocs",
            ),
        )
    }

    @Test
    fun aIsNotAnAncestorOfAb() {
        assertFalse(isAncestorTarget("ab", "a"))
        assertFalse(isAncestorTarget("ab/c", "a"))
        assertTrue(isAncestorTarget("a/b", "a"))
    }
}
