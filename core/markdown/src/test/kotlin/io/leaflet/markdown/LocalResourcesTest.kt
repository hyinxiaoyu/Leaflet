package io.leaflet.markdown

import io.leaflet.model.DocumentId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * T10 link/image JVM tests (REQ-0009 AC-1–AC-4/AC-6/AC-7, Test Plan):
 * destination classification, tree-confined normalization, TOC-frozen
 * fragment rules, block extraction, and the relative-links/images fixtures.
 *
 * Pure JVM via [LeafletDocumentParser.createForJvmTests]; `:core:storage`
 * owns the authoritative `RelativePaths` tests — the
 * [ResourceLinks.resolveDocPath] cases here pin the mirror's consistency
 * (same allowed shapes, same rejections).
 */
class LocalResourcesTest {
    private val parser = LeafletDocumentParser.createForJvmTests()
    private val renderer = MarkdownRenderer.createForJvmTests()
    private val root: File = File(System.getProperty("user.dir"), "../..")

    private fun fixture(name: String): ByteArray = File(root, "testdata/markdown/$name").readBytes()

    private suspend fun parseMd(name: String): ParsedDocument =
        parser.parse(
            DocumentId("t"),
            TextDecoder.decode(fixture(name)),
            name,
            true,
        )

    @Test
    fun docLinkKindsCoverSchemeWhitelist() {
        assertEquals(DocLinkKind.EXTERNAL, ResourceLinks.classifyDocLink("https://example.com/x").kind)
        assertEquals(DocLinkKind.EXTERNAL, ResourceLinks.classifyDocLink("mailto:a@b.c").kind)
        assertEquals(DocLinkKind.EXTERNAL, ResourceLinks.classifyDocLink("tel:+123").kind)
        assertEquals(DocLinkKind.UNSUPPORTED_SCHEME, ResourceLinks.classifyDocLink("foobar:x").kind)
        assertEquals(DocLinkKind.UNSUPPORTED_SCHEME, ResourceLinks.classifyDocLink("content://tree/doc").kind)
        assertEquals(DocLinkKind.UNSUPPORTED_SCHEME, ResourceLinks.classifyDocLink("data:text/plain,x").kind)
        assertEquals(DocLinkKind.EMPTY, ResourceLinks.classifyDocLink("").kind)
        assertEquals(DocLinkKind.EMPTY, ResourceLinks.classifyDocLink("   ").kind)
        assertEquals(DocLinkKind.SAME_DOC_ANCHOR, ResourceLinks.classifyDocLink("#section").kind)
        assertEquals(DocLinkKind.UNSUPPORTED_MEDIA, ResourceLinks.classifyDocLink("pic.pdf").kind)
        assertEquals(DocLinkKind.UNSUPPORTED_MEDIA, ResourceLinks.classifyDocLink("pic.png").kind)
        assertEquals(DocLinkKind.UNREACHABLE, ResourceLinks.classifyDocLink("/etc/passwd.md").kind)
        assertEquals(DocLinkKind.UNREACHABLE, ResourceLinks.classifyDocLink("sub\\file.md").kind)
        assertEquals(DocLinkKind.LIBRARY_DOC, ResourceLinks.classifyDocLink("sibling.md").kind)
        assertEquals(DocLinkKind.LIBRARY_DOC, ResourceLinks.classifyDocLink("notes.md").kind)
        assertEquals(DocLinkKind.LIBRARY_DOC, ResourceLinks.classifyDocLink("no-extension").kind)
    }

    @Test
    fun docLinkSplitsPathAndFragment() {
        val ref = ResourceLinks.classifyDocLink("other.md#Some Heading")
        assertEquals(DocLinkKind.LIBRARY_DOC, ref.kind)
        assertEquals("other.md", ref.pathPart)
        assertEquals("Some Heading", ref.fragment)
        val query = ResourceLinks.classifyDocLink("other.md?x=1#frag")
        assertEquals("other.md", query.pathPart)
        assertEquals("frag", query.fragment)
    }

    @Test
    fun imageKindsCoverRemoteSvgAndUnknown() {
        assertEquals(ImageKind.REMOTE, ResourceLinks.classifyImage("https://example.com/a.png").kind)
        assertEquals(ImageKind.REMOTE, ResourceLinks.classifyImage("http://example.com/a.png").kind)
        assertEquals(ImageKind.REMOTE, ResourceLinks.classifyImage("//example.com/a.png").kind)
        assertEquals(ImageKind.UNSUPPORTED_FORMAT, ResourceLinks.classifyImage("assets/vector.svg").kind)
        assertEquals(ImageKind.UNSUPPORTED_SCHEME, ResourceLinks.classifyImage("foobar:image.png").kind)
        assertEquals(ImageKind.UNSUPPORTED_SCHEME, ResourceLinks.classifyImage("data:image/png;base64,x").kind)
        assertEquals(ImageKind.EMPTY, ResourceLinks.classifyImage("").kind)
        assertEquals(ImageKind.UNREACHABLE, ResourceLinks.classifyImage("/abs.png").kind)
        assertEquals(ImageKind.LOCAL, ResourceLinks.classifyImage("assets/leaf-16.png").kind)
    }

    @Test
    fun treeResolutionAllowsClimbRejectsEscape() {
        assertEquals("top.md", ResourceLinks.resolveDocPath("a/b/c.md", "../../top.md"))
        assertEquals("b.md", ResourceLinks.resolveDocPath("a.md", "./b.md"))
        assertEquals("笔记/空 格.md", ResourceLinks.resolveDocPath("笔记/a.md", "./空 格.md"))
        assertEquals("笔记/安.md", ResourceLinks.resolveDocPath("笔记/a.md", "./%E5%AE%89.md"))
        assertNull(ResourceLinks.resolveDocPath("a.md", "../escape.md"))
        assertNull(ResourceLinks.resolveDocPath("a/b.md", "../../x.md"))
        assertNull(ResourceLinks.resolveDocPath("a.md", "%2E%2E/escape.md"))
        assertNull(ResourceLinks.resolveDocPath("a.md", "%2fabsolute.md"))
        assertNull(ResourceLinks.resolveDocPath("a.md", "https://example.com/x.md"))
        assertNull(ResourceLinks.resolveDocPath("a.md", "/absolute.md"))
        assertEquals(
            "notes/b.png",
            ResourceLinks.resolveImagePath("notes/a.md", "sub/../b.png"),
        )
        assertNull(ResourceLinks.resolveImagePath("a.md", "../../escape.png"))
    }

    @Test
    fun fragmentNormalizationMatchesTocRule() {
        assertEquals("overview", ResourceLinks.normalizeFragment("Overview"))
        assertEquals("overview", ResourceLinks.normalizeFragment("overview"))
        assertEquals("overview-1", ResourceLinks.normalizeFragment("overview-1"))
        // Non-Latin headings pass through slugify unchanged: the slug keeps
        // CJK letters (only whitespace/underscores and punctuation fold).
        assertEquals("概述", ResourceLinks.normalizeFragment("概述"))
        assertEquals("概述", ResourceLinks.normalizeFragment("%E6%A6%82%E8%BF%B0"))
        assertEquals(Headings.slugify("安装步骤"), ResourceLinks.normalizeFragment("安装步骤"))
        assertEquals(Headings.slugify("安装步骤"), ResourceLinks.normalizeFragment("%E5%AE%89%E8%A3%85%E6%AD%A5%E9%AA%A4"))
    }

    @Test
    fun relativeLinksFixtureExtractsEveryRow() =
        runTest {
            val doc = parseMd("relative-links.md")
            val links = doc.blocks.flatMap { it.links }
            val byDestination = links.associateBy { it.destination }
            assertEquals(DocLinkKind.LIBRARY_DOC, byDestination["sibling.md"]!!.kind)
            assertEquals("sibling.md", byDestination["sibling.md"]!!.path)
            assertEquals(DocLinkKind.LIBRARY_DOC, byDestination["guides/advanced/deep-target.md"]!!.kind)
            assertEquals(DocLinkKind.LIBRARY_DOC, byDestination["../testdata-anchor.md"]!!.kind)
            assertEquals(DocLinkKind.SAME_DOC_ANCHOR, byDestination["#relative-links"]!!.kind)
            assertEquals(DocLinkKind.LIBRARY_DOC, byDestination["duplicate-headings.md#overview"]!!.kind)
            assertEquals(DocLinkKind.LIBRARY_DOC, byDestination["sibling.md#no-such-heading"]!!.kind)
            // Destinations stay percent-encoded end to end (commonmark passes the
            // raw destination through; decoding happens at resolve time in
            // `RelativePaths`/`resolveDocPath`, mirroring `:core:storage`).
            assertEquals(DocLinkKind.LIBRARY_DOC, byDestination["my%20docs/notes%20with%20spaces.md"]!!.kind)
            assertEquals("my%20docs/notes%20with%20spaces.md", byDestination["my%20docs/notes%20with%20spaces.md"]!!.path)
            assertEquals(DocLinkKind.LIBRARY_DOC, byDestination["资料/说明.md"]!!.kind)
            assertEquals(DocLinkKind.LIBRARY_DOC, byDestination["does-not-exist.md"]!!.kind)
            // Classification is base-agnostic: `..` shape alone cannot decide
            // escape (needs the referring dir), so `../../outside-root.md`
            // classifies LIBRARY_DOC and the resolve layer rejects it (next test).
            assertEquals(DocLinkKind.LIBRARY_DOC, byDestination["../../outside-root.md"]!!.kind)
            assertEquals(DocLinkKind.UNREACHABLE, byDestination["/etc/passwd.md"]!!.kind)
            assertEquals(DocLinkKind.EXTERNAL, byDestination["https://example.com/some/page"]!!.kind)
            assertEquals(DocLinkKind.UNSUPPORTED_SCHEME, byDestination["foobar:not-a-known-scheme"]!!.kind)
            assertEquals(DocLinkKind.EMPTY, byDestination[""]!!.kind)
            assertEquals(DocLinkKind.EXTERNAL, byDestination["mailto:someone@example.com"]!!.kind)
        }

    @Test
    fun relativeLinksResolveAgainstTreeRules() {
        // Base `testdata/markdown/relative-links.md`'s library-relative path.
        val base = "markdown/relative-links.md"
        assertEquals("markdown/sibling.md", ResourceLinks.resolveDocPath(base, "sibling.md"))
        assertEquals("markdown/guides/advanced/deep-target.md", ResourceLinks.resolveDocPath(base, "guides/advanced/deep-target.md"))
        assertEquals("testdata-anchor.md", ResourceLinks.resolveDocPath(base, "../testdata-anchor.md"))
        assertEquals("markdown/my docs/notes with spaces.md", ResourceLinks.resolveDocPath(base, "my%20docs/notes%20with%20spaces.md"))
        assertEquals("markdown/资料/说明.md", ResourceLinks.resolveDocPath(base, "资料/说明.md"))
        assertNull(ResourceLinks.resolveDocPath(base, "../../outside-root.md"))
    }

    @Test
    fun relativeImagesFixtureExtractsEveryRow() =
        runTest {
            val doc = parseMd("relative-images.md")
            val images = doc.blocks.flatMap { it.images }
            val byDestination = images.associateBy { it.destination }
            assertEquals(ImageKind.LOCAL, byDestination["assets/leaf-16.png"]!!.kind)
            assertEquals(ImageKind.LOCAL, byDestination["assets/leaf-256.png"]!!.kind)
            assertEquals(ImageKind.LOCAL, byDestination["my%20docs/spaced%20image.png"]!!.kind)
            assertEquals("my%20docs/spaced%20image.png", byDestination["my%20docs/spaced%20image.png"]!!.path)
            assertEquals(ImageKind.LOCAL, byDestination["../testdata-anchor.png"]!!.kind)
            // Same base-agnostic rule as links: shape classifies LOCAL, the
            // resolve layer rejects the escape (asserted in the resolve test).
            assertEquals(ImageKind.LOCAL, byDestination["../../outside-root.png"]!!.kind)
            assertEquals(ImageKind.LOCAL, byDestination["assets/missing.png"]!!.kind)
            assertEquals(ImageKind.REMOTE, byDestination["https://example.com/leaf.png"]!!.kind)
            assertEquals(ImageKind.REMOTE, byDestination["http://example.com/leaf.png"]!!.kind)
            assertEquals(ImageKind.UNSUPPORTED_FORMAT, byDestination["assets/vector.svg"]!!.kind)
            assertEquals(ImageKind.EMPTY, byDestination[""]!!.kind)
            assertEquals(ImageKind.UNSUPPORTED_SCHEME, byDestination["foobar:image.png"]!!.kind)
            assertTrue(images.none { it.kind == ImageKind.REMOTE && it.destination.startsWith("http").not() })
        }

    @Test
    fun referenceStyleImageResolvesDefinition() =
        runTest {
            val doc = parseMd("relative-images.md")
            val images = doc.blocks.flatMap { it.images }
            // Reference-style image: commonmark resolves `[leaf-ref]` against
            // the definition URL. Accept either the alt-carrying node or the
            // definition URL — the assertion pins that the definition survives
            // extraction (alt text may ride on the child Text node).
            val byDestination = images.associateBy { it.destination }
            val ref = byDestination["assets/leaf-16.png"]
            assertTrue("ref-style image extracted (found ${byDestination.keys})", ref != null)
            assertEquals(ImageKind.LOCAL, ref!!.kind)
        }

    @Test
    fun duplicateFragmentAnchorsAreUniqueAndFirstWins() =
        runTest {
            val doc = parseMd("duplicate-headings.md")
            val overviews = doc.toc.filter { it.text == "Overview" }.map { it.anchor }
            assertEquals(3, overviews.size)
            assertEquals(3, overviews.toSet().size)
            val ops = doc.positionOps!!
            for (entry in doc.toc) {
                assertTrue(ops.locateAnchor(entry.anchor) != null)
            }
            // Best-effort duplicate rule (REQ-0009 FR-4): bare `#overview`
            // normalizes to the first duplicate's anchor.
            assertEquals("overview", ResourceLinks.normalizeFragment("Overview"))
            assertEquals(overviews.first(), ResourceLinks.normalizeFragment("Overview"))
        }

    @Test
    fun codeBlocksCarryNoLinkPayload() =
        runTest {
            val doc = parseMd("code.md")
            val codeBlocks = doc.blocks.filter { it.kind == BlockKind.CODE }
            assertTrue(codeBlocks.isNotEmpty())
            assertTrue(codeBlocks.all { it.links.isEmpty() && it.images.isEmpty() })
        }
}
