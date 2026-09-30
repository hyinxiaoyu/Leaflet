package io.leaflet.storage.saf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pure path-confinement tests (no Android framework): tree-internal `..`
 * allowed, escapes rejected, schemes/absolute/backslash rejected,
 * percent-encoding round-trips, encoded navigation rejected.
 */
class RelativePathsTest {
    @Test
    fun siblingAndNestedResolution() {
        assertEquals("notes/b.md", RelativePaths.resolveSibling("notes/a.md", "./b.md"))
        assertEquals("notes/b.md", RelativePaths.resolveSibling("notes/a.md", "b.md"))
        assertEquals("notes/sub/c.md", RelativePaths.resolveSibling("notes/a.md", "sub/c.md"))
        assertEquals("top.md", RelativePaths.resolveSibling("a/b/c.md", "../../top.md"))
    }

    @Test
    fun rootLevelBase() {
        assertEquals("b.md", RelativePaths.resolveSibling("a.md", "./b.md"))
        assertNull(RelativePaths.resolveSibling("a.md", "../escape.md"))
    }

    @Test
    fun escapesRejected() {
        assertNull(RelativePaths.resolveSibling("a.md", ".."))
        assertNull(RelativePaths.resolveSibling("a/b.md", "../../x.md"))
        assertNull(RelativePaths.resolveSibling("a.md", "sub/../../.."))
        assertNull(RelativePaths.resolveSibling("a.md", "."))
    }

    @Test
    fun schemesAbsoluteAndBackslashRejected() {
        assertNull(RelativePaths.resolveSibling("a.md", "https://example.com/x.md"))
        assertNull(RelativePaths.resolveSibling("a.md", "content://tree/doc"))
        assertNull(RelativePaths.resolveSibling("a.md", "foo://bar"))
        assertNull(RelativePaths.resolveSibling("a.md", "/absolute.md"))
        assertNull(RelativePaths.resolveSibling("a.md", "sub\\file.md"))
    }

    @Test
    fun fragmentsAndBlanksIgnored() {
        assertNull(RelativePaths.resolveSibling("a.md", ""))
        assertNull(RelativePaths.resolveSibling("a.md", "   "))
        // A ref that is only a fragment resolves to nothing here;
        // same-document anchors belong to the Reader (T08/T10).
        assertNull(RelativePaths.resolveSibling("a.md", "#section"))
        assertEquals("notes/b.md", RelativePaths.resolveSibling("notes/a.md", "b.md#frag"))
    }

    @Test
    fun unicodeAndEncodingRoundTrip() {
        assertEquals("笔记/空 格.md", RelativePaths.resolveSibling("笔记/a.md", "./空 格.md"))
        assertEquals("笔记/安.md", RelativePaths.resolveSibling("笔记/a.md", "./%E5%AE%89.md"))
        assertEquals("emoji/🎉.md", RelativePaths.resolveSibling("emoji/a.md", "./🎉.md"))
    }

    @Test
    fun encodedNavigationRejected() {
        assertNull(RelativePaths.resolveSibling("a.md", "%2E%2E/escape.md"))
        assertNull(RelativePaths.resolveSibling("a.md", "%2fabsolute.md"))
        assertNull(RelativePaths.resolveSibling("a.md", "%00.md"))
        assertNull(RelativePaths.resolveSibling("a.md", "%ZZ.md"))
    }

    @Test
    fun duplicateSlashesAndDotsNormalized() {
        assertEquals("c.md", RelativePaths.resolveSibling("a/b.md", "./sub/.././../c.md"))
        // From base dir "a/": a/sub -> a -> (root) -> c.md; the second ".."
        // reaches the tree root but does not escape it, so it resolves.
        assertEquals("c.md", RelativePaths.resolveSibling("a/b.md", "sub//..//../c.md"))
        // One more ".." WOULD escape the tree -> rejected.
        assertNull(RelativePaths.resolveSibling("a/b.md", "sub/../../../c.md"))
    }

    @Test
    fun treeUrisNormalize() {
        assertEquals("content://tree/docs", TreeUris.normalize("content://tree/docs"))
        assertEquals("content://tree/docs", TreeUris.normalize("content://tree/docs/"))
        assertEquals("content://tree/docs", TreeUris.normalize("  content://tree/docs// "))
    }
}
