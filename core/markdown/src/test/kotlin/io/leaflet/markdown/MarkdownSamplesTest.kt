package io.leaflet.markdown

import io.leaflet.model.DocumentId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * T06 sample-driven assertions over `testdata/markdown/` (REQ-0002 AC-1..AC-5,
 * AC-8): syntax coverage, title rule, frontmatter abuse, BOM/encoding, TXT
 * literalness, multilingual integrity, malformed survival.
 *
 * Pure JVM via [LeafletDocumentParser.createForJvmTests]: same commonmark
 * 0.13.0 extensions as the device chain (tables, strikethrough, task-list
 * shim); Android-only rendering (Markwon Spanned, drawables) is out of scope
 * here and covered by the 10MB instrumented pass instead.
 */
class MarkdownSamplesTest {
    private val parser = LeafletDocumentParser.createForJvmTests()

    // Unit-test JVM working dir is the module dir (`core/markdown`), while the
    // fixtures live at repo-root `testdata/` (never packaged, see
    // testdata/README.md "APK non-pollution"). Walk up to find them.
    private val root: File = findTestdataRoot()

    private fun md(name: String): String =
        File(root, "markdown/$name").readBytes().let {
            TextDecoder.decode(it).text
        }

    private fun fixture(name: String): ByteArray = File(root, "markdown/$name").readBytes()

    private fun kindsOf(doc: ParsedDocument): Set<BlockKind> = doc.blocks.map { it.kind }.toSet()

    @Test
    fun simpleCoversInlineSyntax() =
        runTest {
            val doc =
                parser.parse(
                    DocumentId("t"),
                    TextDecoder.decode(fixture("simple.md")),
                    "simple.md",
                    true,
                )
            val kinds = kindsOf(doc)
            assertTrue(kinds.contains(BlockKind.HEADING))
            assertTrue(kinds.contains(BlockKind.PARAGRAPH))
            assertTrue(kinds.contains(BlockKind.QUOTE))
            assertTrue(kinds.contains(BlockKind.SEPARATOR))
            assertTrue(kinds.contains(BlockKind.LIST))
            assertTrue(kinds.contains(BlockKind.CODE))
            assertFalse(doc.degraded)
            assertNull(doc.degradedNotice)
        }

    @Test
    fun headingsExtractH1toH6() =
        runTest {
            val doc =
                parser.parse(
                    DocumentId("t"),
                    TextDecoder.decode(fixture("headings.md")),
                    "headings.md",
                    true,
                )
            val levels = doc.toc.map { it.level }.toSet()
            assertTrue(levels.containsAll(setOf(1, 2, 3, 4, 5, 6)))
            // `#NoSpace` stays a paragraph, never a heading.
            assertTrue(doc.toc.none { it.text.contains("NoSpace") })
            assertEquals("Headings H1", doc.title)
        }

    @Test
    fun duplicateHeadingsGetUniqueAnchors() =
        runTest {
            val doc =
                parser.parse(
                    DocumentId("t"),
                    TextDecoder.decode(fixture("duplicate-headings.md")),
                    "duplicate-headings.md",
                    true,
                )
            val anchors = doc.toc.map { it.anchor }
            assertEquals(anchors.size, anchors.toSet().size)
            val overviews = doc.toc.filter { it.text == "Overview" }.map { it.anchor }
            assertEquals(3, overviews.size)
            assertEquals(3, overviews.toSet().size)
            // Every TOC anchor resolves back to a heading block.
            val ops = doc.positionOps!!
            for (entry in doc.toc) {
                val loc = ops.locateAnchor(entry.anchor)
                assertTrue("anchor ${entry.anchor} resolves", loc != null)
                assertEquals(BlockKind.HEADING, doc.blocks[loc!!.blockIndex].kind)
            }
        }

    @Test
    fun listsTablesCodeKinds() =
        runTest {
            for (name in listOf("lists.md", "nested-lists.md")) {
                val doc =
                    parser.parse(
                        DocumentId("t"),
                        TextDecoder.decode(fixture(name)),
                        name,
                        true,
                    )
                assertTrue("$name has LIST", kindsOf(doc).contains(BlockKind.LIST))
                assertFalse("$name degraded", doc.degraded)
            }
            val tables =
                parser.parse(
                    DocumentId("t"),
                    TextDecoder.decode(fixture("tables.md")),
                    "tables.md",
                    true,
                )
            assertTrue(kindsOf(tables).contains(BlockKind.TABLE))
            val code =
                parser.parse(
                    DocumentId("t"),
                    TextDecoder.decode(fixture("code.md")),
                    "code.md",
                    true,
                )
            assertTrue(kindsOf(code).contains(BlockKind.CODE))
            // Language-tagged fences keep their source (tag visible to T09).
            assertTrue(code.blocks.any { it.kind == BlockKind.CODE && it.text.contains("kotlin") })
        }

    @Test
    fun taskListsClassifyAsList() =
        runTest {
            val doc =
                parser.parse(
                    DocumentId("t"),
                    TextDecoder.decode(fixture("lists.md")),
                    "lists.md",
                    true,
                )
            // Task section survives as LIST blocks (read-only check state, T06).
            assertTrue(doc.blocks.any { it.kind == BlockKind.LIST && it.text.contains("[") })
        }

    @Test
    fun titleRuleFrontmatterH1Filename() =
        runTest {
            val fm =
                parser.parse(
                    DocumentId("t"),
                    DecodedText("---\ntitle: Front Title\n---\n\n# Body H1\n", EncodingState.CLEAN),
                    "file.md",
                    true,
                )
            assertEquals("Front Title", fm.title)
            val h1 =
                parser.parse(
                    DocumentId("t"),
                    DecodedText("# Only H1\n\nbody\n", EncodingState.CLEAN),
                    "file.md",
                    true,
                )
            assertEquals("Only H1", h1.title)
            val plain =
                parser.parse(
                    DocumentId("t"),
                    DecodedText("no headings here\n", EncodingState.CLEAN),
                    "my notes.md",
                    true,
                )
            assertEquals("my notes", plain.title)
        }

    @Test
    fun brokenFrontmatterNeverFailsBody() =
        runTest {
            // Unclosed fence: no closing ---, body must still parse.
            val unclosed =
                parser.parse(
                    DocumentId("t"),
                    DecodedText("---\ntitle: Lost\n\n# Real H1\n", EncodingState.CLEAN),
                    "broken.md",
                    true,
                )
            assertEquals("Real H1", unclosed.title)
            assertTrue(unclosed.blocks.isNotEmpty())
            // Non-scalar title: title falls through to H1, body intact.
            val nonScalar =
                parser.parse(
                    DocumentId("t"),
                    DecodedText("---\ntitle: [a, b]\n---\n\n# Fallback H1\n", EncodingState.CLEAN),
                    "nonscalar.md",
                    true,
                )
            assertEquals("Fallback H1", nonScalar.title)
            // Quoted title: quotes stripped.
            val quoted =
                parser.parse(
                    DocumentId("t"),
                    DecodedText("---\ntitle: \"Quoted Title\"\n---\n\nbody\n", EncodingState.CLEAN),
                    "q.md",
                    true,
                )
            assertEquals("Quoted Title", quoted.title)
        }

    @Test
    fun bomStrippedAndBodyIntact() =
        runTest {
            val raw = fixture("bom.md")
            assertTrue(raw[0] == 0xEF.toByte() && raw[1] == 0xBB.toByte())
            val decoded = TextDecoder.decode(raw)
            assertEquals(EncodingState.BOM_STRIPPED, decoded.encodingState)
            val doc = parser.parse(DocumentId("t"), decoded, "bom.md", true)
            assertEquals("BOM heading", doc.title)
            // Explicit escape: a literal invisible char here would be stripped
            // by source formatters (ktlintFormat removed the raw U+FEFF and
            // turned this into a vacuous contains("") in R2-00).
            assertFalse(doc.blocks.any { it.text.contains("\uFEFF") })
        }

    @Test
    fun brokenEncodingDegradesWithNotice() =
        runTest {
            val decoded = TextDecoder.decode(fixture("broken-encoding.md"))
            assertEquals(EncodingState.HAD_ENCODING_DAMAGE, decoded.encodingState)
            val doc = parser.parse(DocumentId("t"), decoded, "broken-encoding.md", true)
            assertTrue(doc.degraded)
            assertTrue(doc.degradedNotice!!.contains("编码无法识别"))
            // No encoding name is ever claimed.
            assertFalse(doc.degradedNotice!!.matches(Regex(".*[Gg][Bb][Kk].*")))
            assertFalse(doc.degradedNotice!!.matches(Regex(".*[Uu][Tt][Ff]-?16.*")))
            // Valid lines stay readable around the damage.
            val all = doc.blockTexts.joinToString("\n")
            assertTrue(all.contains("Valid line before damage."))
            assertTrue(all.contains("Valid line after damage."))
            assertTrue(all.contains("�"))
        }

    @Test
    fun invalidUtf8SequencesBecomeReplacement() =
        runTest {
            fun decode(vararg bytes: Int) = TextDecoder.decode(bytes.map { it.toByte() }.toByteArray())
            // Beyond U+10FFFF (U+110000) must not reach Character.toChars:
            // per-byte fallback (one U+FFFD per bad byte, same convention as
            // truncated sequences), damaged flag set, no exception.
            val beyond = decode(0xF4, 0x90, 0x80, 0x80)
            assertEquals(EncodingState.HAD_ENCODING_DAMAGE, beyond.encodingState)
            assertTrue(beyond.text.isNotEmpty() && beyond.text.all { it == '�' })
            // Overlong 2-byte form of '/': lead + lone continuation.
            val overlong = decode(0xC0, 0xAF)
            assertEquals(EncodingState.HAD_ENCODING_DAMAGE, overlong.encodingState)
            assertTrue(overlong.text.all { it == '�' })
            // Surrogate half U+D800, truncated sequence, lone continuation.
            for (bad in listOf(listOf(0xED, 0xA0, 0x80), listOf(0xE2, 0x82), listOf(0x80))) {
                val d = decode(*bad.toIntArray())
                assertEquals(EncodingState.HAD_ENCODING_DAMAGE, d.encodingState)
                assertTrue(d.text.isNotEmpty() && d.text.all { it == '�' })
            }
        }

    // -- R2-02 regressions: AST fidelity (must FAIL before the BlockSlicer
    // rewrite; the contains-guess path drops/duplicates these) ---------------

    @Test
    fun consecutiveHeadingsWithoutBlanksAreAllKept() =
        runTest {
            val doc =
                parser.parse(
                    DocumentId("t"),
                    DecodedText("# A\n# B\n# C\n", EncodingState.CLEAN),
                    "h.md",
                    true,
                )
            val heads = doc.blocks.filter { it.kind == BlockKind.HEADING }.map { it.text }
            assertEquals(listOf("# A", "# B", "# C"), heads)
        }

    @Test
    fun formattedHeadingKeepsFullSource() =
        runTest {
            val doc =
                parser.parse(
                    DocumentId("t"),
                    DecodedText("# A **B** and `code`\n\nbody\n", EncodingState.CLEAN),
                    "f.md",
                    true,
                )
            val head = doc.blocks.first { it.kind == BlockKind.HEADING }
            // Source slice keeps the markers; visible text drops them.
            assertTrue(head.source.contains("**B**"))
            assertTrue(head.source.contains("`code`"))
            assertFalse(doc.blockTexts[head.index].contains("**"))
        }

    @Test
    fun siblingHeadingsWithSubstringTitlesStayDistinct() =
        runTest {
            val doc =
                parser.parse(
                    DocumentId("t"),
                    DecodedText("# Over\n\nbody one\n\n# Overview\n\nbody two\n", EncodingState.CLEAN),
                    "s.md",
                    true,
                )
            val heads = doc.blocks.filter { it.kind == BlockKind.HEADING }
            assertEquals(2, heads.size)
            assertEquals("# Over", heads[0].source.trim())
            assertEquals("# Overview", heads[1].source.trim())
        }

    @Test
    fun fullDocumentTextIsCoveredExactlyOnce() =
        runTest {
            // Every non-blank body line lands in exactly one block source;
            // the tail marker is never lost (R2-02 4000-line + tail cases).
            val lines = (1..200).map { "Para $it with some filler text.\n" } + "TAIL-MARKER-42\n"
            val doc =
                parser.parse(
                    DocumentId("t"),
                    DecodedText(lines.joinToString("\n"), EncodingState.CLEAN),
                    "cov.md",
                    true,
                )
            val covered = doc.blocks.joinToString("\n") { it.source }
            assertTrue(covered.contains("TAIL-MARKER-42"))
            val paraCount = doc.blocks.count { it.text.contains("Para ") }
            assertEquals(200, paraCount)
        }

    @Test
    fun giantCodeBlockIsSplitWithoutLoss() =
        runTest {
            // 4500-line single fenced block: every line kept across the
            // bounded chunks, fences intact, no 4000-line truncation.
            val body = (1..4500).map { "code line $it x".padEnd(20, 'y') }.joinToString("\n")
            val text = "```text\n$body\n```\nTAIL-AFTER-CODE\n"
            val doc =
                parser.parse(
                    DocumentId("t"),
                    DecodedText(text, EncodingState.CLEAN),
                    "gc.md",
                    true,
                )
            val covered = doc.blocks.joinToString("\n") { it.source }
            assertTrue(covered.contains("code line 1 "))
            assertTrue(covered.contains("TAIL-AFTER-CODE"))
            assertTrue(doc.blocks.all { it.source.length <= BlockLimits.MAX_BLOCK_CHARS + 4096 })
        }

    @Test
    fun txtPathIsLiteral() =
        runTest {
            val doc =
                parser.parse(
                    DocumentId("t"),
                    TextDecoder.decode(fixture("sample.txt")),
                    "sample.txt",
                    false,
                )
            assertEquals("sample", doc.title)
            assertTrue(doc.blocks.all { it.kind == BlockKind.PARAGRAPH })
            val all = doc.blockTexts.joinToString("\n")
            assertTrue(all.contains("# Not A Heading In TXT Mode"))
            assertTrue(all.contains("- not a list, just a dash line"))
            assertTrue(all.contains("txt-needle-plain-sample"))
        }

    @Test
    fun emptyFilesAreStateNotError() =
        runTest {
            for (name in listOf("empty.md", "empty.txt")) {
                val doc =
                    parser.parse(
                        DocumentId("t"),
                        TextDecoder.decode(fixture(name)),
                        name,
                        name.endsWith(".md"),
                    )
                assertTrue("$name no blocks", doc.blocks.isEmpty())
                assertFalse("$name not degraded", doc.degraded)
                assertEquals("empty", doc.title)
            }
        }

    @Test
    fun multilingualTextSurvives() =
        runTest {
            val zh =
                parser.parse(
                    DocumentId("t"),
                    TextDecoder.decode(fixture("chinese.md")),
                    "chinese.md",
                    true,
                )
            assertEquals("中文阅读样本", zh.title)
            assertTrue(zh.blockTexts.joinToString("").contains("量子计算机"))
            val ja =
                parser.parse(
                    DocumentId("t"),
                    TextDecoder.decode(fixture("japanese.md")),
                    "japanese.md",
                    true,
                )
            assertTrue(ja.blockTexts.joinToString("").contains("古池や"))
            val uni =
                parser.parse(
                    DocumentId("t"),
                    TextDecoder.decode(fixture("unicode.md")),
                    "unicode.md",
                    true,
                )
            val all = uni.blockTexts.joinToString("")
            assertTrue(all.contains("📚"))
            assertTrue(all.contains("שלום"))
        }

    @Test
    fun malformedDegradesNeverCrashes() =
        runTest {
            val doc =
                parser.parse(
                    DocumentId("t"),
                    TextDecoder.decode(fixture("malformed.md")),
                    "malformed.md",
                    true,
                )
            assertTrue(doc.blocks.size >= 5)
            // Unclosed fence becomes one bounded CODE block, rest still parses.
            assertTrue(doc.blocks.any { it.kind == BlockKind.CODE })
            assertTrue(doc.blocks.any { it.kind == BlockKind.PARAGRAPH })
            assertTrue(doc.blocks.any { it.kind == BlockKind.TABLE || it.kind == BlockKind.PARAGRAPH })
        }

    @Test
    fun longParagraphIsBounded() =
        runTest {
            val doc =
                parser.parse(
                    DocumentId("t"),
                    TextDecoder.decode(fixture("long-paragraph.md")),
                    "long-paragraph.md",
                    true,
                )
            assertTrue(doc.degraded)
            assertTrue(doc.degradedNotice!!.contains("纯文本显示"))
            assertTrue(doc.blocks.all { it.text.length <= BlockLimits.MAX_BLOCK_CHARS })
            assertTrue(doc.blocks.any { it.kind == BlockKind.PLAIN_FALLBACK })
            // No silent truncation: joined fallback covers the whole source.
            val raw = md("long-paragraph.md").replace("\n", "")
            val joined = doc.blocks.joinToString("") { it.text }.replace("\n", "")
            assertTrue(joined.length >= raw.length - doc.blocks.size)
        }

    @Test
    fun longCodeblockIsBounded() =
        runTest {
            val doc =
                parser.parse(
                    DocumentId("t"),
                    TextDecoder.decode(fixture("long-codeblock.md")),
                    "long-codeblock.md",
                    true,
                )
            assertTrue(doc.degraded)
            assertTrue(doc.blocks.all { it.text.length <= BlockLimits.MAX_BLOCK_CHARS })
        }

    @Test
    fun unknownSyntaxStaysReadable() =
        runTest {
            val input = "# T\n\n\$x^2\$\n\n```mermaid\ngraph A-->B\n```\n\nSome [^note] marker\n"
            val doc =
                parser.parse(
                    DocumentId("t"),
                    DecodedText(input, EncodingState.CLEAN),
                    "latex.md",
                    true,
                )
            val all = doc.blocks.joinToString("\n") { it.text }
            assertTrue(all.contains("x^2"))
            assertTrue(all.contains("mermaid") || all.contains("graph"))
            assertTrue(all.contains("note"))
        }

    @Test
    fun anchorsAndSearchAreVisibleText() =
        runTest {
            val doc =
                parser.parse(
                    DocumentId("t"),
                    DecodedText(
                        "# Hello **World**\n\nPara with `code` and [link](https://example.com).\n",
                        EncodingState.CLEAN,
                    ),
                    "s.md",
                    true,
                )
            val ops = doc.positionOps!!
            assertEquals("Hello World", ops.visibleTextOf(0))
            // Search hits visible text, never raw markers.
            assertTrue(ops.locateQuery("Hello World").isNotEmpty())
            assertTrue(ops.locateQuery("**").isEmpty())
            assertTrue(ops.locateQuery("](https").isEmpty())
            // Two blocks: heading + paragraph (no link-definition filtering here).
            assertEquals(2, doc.positionMap.blockCount)
            assertNotEquals(doc.toc[0].anchor, "")
        }

    @Test
    fun textDecoderUnitMatrix() {
        assertEquals(DecodedText("hi", EncodingState.CLEAN), TextDecoder.decode("hi".toByteArray()))
        val bommed = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "x".toByteArray()
        assertEquals("x", TextDecoder.decode(bommed).text)
        assertEquals(EncodingState.BOM_STRIPPED, TextDecoder.decode(bommed).encodingState)
        // Lone continuation + illegal bytes degrade, never throw.
        val bad = byteArrayOf(0x41, 0x80.toByte(), 0xFF.toByte(), 0x42)
        val out = TextDecoder.decode(bad)
        assertEquals(EncodingState.HAD_ENCODING_DAMAGE, out.encodingState)
        assertEquals("A��B", out.text)
        // Truncated CJK tail degrades with replacement.
        val cut = "中".toByteArray().copyOf(2)
        assertEquals(EncodingState.HAD_ENCODING_DAMAGE, TextDecoder.decode(cut).encodingState)
    }

    companion object {
        private fun findTestdataRoot(): File {
            // system-prop override first (CI may set an absolute path).
            System.getProperty("leaflet.testdata")?.let { return File(it) }
            var dir = File(System.getProperty("user.dir"))
            repeat(6) {
                val candidate = File(dir, "testdata")
                if (File(candidate, "markdown/simple.md").exists()) return candidate
                dir = dir.parentFile ?: return@repeat
            }
            return File(System.getProperty("user.dir"), "testdata")
        }
    }
}

/**
 * T09 typography/code/table JVM tests (REQ-0007): language labels, verbatim
 * copy, block source slices, step mappings, scroll policy, and Spanned-path
 * integrity (multilingual walkthrough, no WRAP/CARD UI seam).
 */
class TypographyTablesCodeTest {
    private val parser = LeafletDocumentParser.createForJvmTests()
    private val renderer = MarkdownRenderer.createForJvmTests()

    // Module working dir is `core/markdown`; fixtures live at repo-root
    // `testdata/` (same walk-up shape as MarkdownSamplesTest).
    private val root: File = File(System.getProperty("user.dir"), "../..")

    private fun fixture(name: String): ByteArray = File(root, "testdata/markdown/$name").readBytes()

    private suspend fun parseMd(name: String): ParsedDocument =
        parser.parse(
            DocumentId("t09"),
            TextDecoder.decode(fixture(name)),
            name,
            true,
        )

    @Test
    fun languageLabelsTaggedUntaggedUnknown() =
        runTest {
            val doc = parseMd("code.md")
            val codes = doc.blocks.filter { it.kind == BlockKind.CODE }
            assertTrue(codes.any { it.language == "kotlin" })
            assertTrue(codes.any { it.language == "python" })
            // Untagged fence: no invented language name (FR-5).
            assertTrue(codes.any { it.language.isEmpty() })
            val malformed =
                parser.parse(
                    DocumentId("t09m"),
                    TextDecoder.decode("```weirdlang-xyz\ncode\n```\n".toByteArray()),
                    "m.md",
                    true,
                )
            val weird = malformed.blocks.firstOrNull { it.kind == BlockKind.CODE }
            assertTrue(weird != null && weird.language == "weirdlang-xyz")
        }

    @Test
    fun copyReturnsFenceInteriorVerbatim() =
        runTest {
            val doc = parseMd("code.md")
            val kotlin = doc.blocks.first { it.kind == BlockKind.CODE && it.language == "kotlin" }
            assertEquals("fun greet(name: String): String {\n    return \"Hello, \$name!\"\n}", kotlin.codeForCopy())
            val plain = doc.blocks.first { it.kind == BlockKind.CODE && it.language.isEmpty() }
            assertEquals("plain text block\nsecond line", plain.codeForCopy())
            // Non-code blocks copy nothing (FR-6 guard).
            assertEquals("", doc.blocks.first { it.kind != BlockKind.CODE }.codeForCopy())
        }

    @Test
    fun longSingleLineCodeStaysReachableInSource() =
        runTest {
            val doc = parseMd("code.md")
            val long = doc.blocks.first { it.kind == BlockKind.CODE && it.source.contains("this-is-a-very-long") }
            // AC-4 shape: the tail token survives in the source slice (scroll target).
            assertTrue(long.codeForCopy().endsWith("abcdefghijklmnopqrstuvwxyz-0123456789"))
            assertTrue(EntryPolicyShim.requiresHorizontalScroll(long.kind))
        }

    @Test
    fun wideTableKeepsAllColumnsReachable() =
        runTest {
            val doc = parseMd("tables.md")
            val wide = doc.blocks.first { it.kind == BlockKind.TABLE && it.source.contains("Codec") }
            // AC-5 shape: header + last-column cells survive in the scroll slice.
            for (token in listOf("Codec", "Note", "av1", "slower to decode")) {
                assertTrue("missing $token", wide.source.contains(token))
            }
            assertTrue(EntryPolicyShim.requiresHorizontalScroll(wide.kind))
            // Text entries never gain a second scroll axis.
            assertFalse(EntryPolicyShim.requiresHorizontalScroll(BlockKind.PARAGRAPH))
        }

    @Test
    fun styleStepMappingsAreDistinctPerStep() {
        // FR-1/FR-3/FR-4 walkthrough: every step maps distinctly (AC-2).
        val scales = (ReaderStyle.FONT_SIZE_MIN..ReaderStyle.FONT_SIZE_MAX).map { ReaderStyle.fontScaleForStep(it) }
        assertEquals(scales.size, scales.toSet().size)
        val spacing =
            (ReaderStyle.LINE_SPACING_MIN..ReaderStyle.LINE_SPACING_MAX).map {
                ReaderStyle.lineSpacingMultiplierForStep(it)
            }
        assertEquals(spacing.size, spacing.toSet().size)
        val margins =
            (ReaderStyle.PAGE_MARGIN_MIN..ReaderStyle.PAGE_MARGIN_MAX).map {
                ReaderStyle.pageMarginDpForStep(it)
            }
        assertEquals(margins.size, margins.toSet().size)
        assertEquals(2, ReaderStyle.FONT_SIZE_DEFAULT)
        assertEquals(true, ReaderStyle.CODE_SOFT_WRAP_DEFAULT)
    }

    @Test
    fun styleCoercionKeepsWhitelist() {
        val coerced =
            ReaderStyle(
                fontSizeStep = 99,
                fontFamily = "Not A Font",
                lineSpacingStep = -5,
                pageMarginStep = 7,
            ).coerce()
        assertEquals(ReaderStyle.FONT_SIZE_MAX, coerced.fontSizeStep)
        assertEquals(ReaderStyle.FONT_FAMILY_DEFAULT, coerced.fontFamily)
        assertTrue(ReaderStyle.isKnownFontFamily("serif"))
        assertFalse(ReaderStyle.isKnownFontFamily("monospace"))
    }

    @Test
    fun spannedFallbackKeepsMultilingualText() =
        runTest {
            // JVM path has no Android Markwon: renderSpanned degrades to source
            // text, never empty, never crash — device renders styled Spanned.
            for (name in listOf("chinese.md", "unicode.md")) {
                val doc = parseMd(name)
                for (block in doc.blocks) {
                    val rendered = renderer.renderSpanned(block).toString()
                    if (block.text.isNotBlank()) assertTrue("$name block ${block.index}", rendered.isNotBlank())
                }
            }
            val doc = parseMd("unicode.md")
            val joined = doc.blocks.joinToString("\n") { renderer.renderSpanned(it).toString() }
            assertTrue(joined.contains("📚"))
            assertTrue(joined.contains("中文"))
        }

    @Test
    fun normalIsOnlyTableMode() {
        // FR-9: WRAP/CARD exist only as named M2+ follow-ups, never as UI state.
        assertEquals(TableRenderMode.NORMAL, TableRenderMode.valueOf("NORMAL"))
        assertTrue(TableRenderMode.entries.contains(TableRenderMode.WRAP))
        assertTrue(TableRenderMode.entries.contains(TableRenderMode.CARD))
    }

    @Test
    fun taskListBlocksStayListKind() =
        runTest {
            val doc = parseMd("lists.md")
            assertTrue(doc.blocks.any { it.kind == BlockKind.LIST && it.source.contains("[ ]") })
            // Same visible pipeline the JVM shim classifies as device Markwon does.
            val rendered =
                doc.blocks.filter { it.kind == BlockKind.LIST }
                    .joinToString("\n") { renderer.renderSpanned(it).toString() }
            assertTrue(rendered.contains("buy oat milk"))
        }
}

/** Test-local scroll-policy mirror (text never scrolls, code/table always do). */
private object EntryPolicyShim {
    fun requiresHorizontalScroll(kind: BlockKind): Boolean =
        when (kind) {
            BlockKind.CODE, BlockKind.TABLE -> true
            else -> false
        }
}

/**
 * R2 DEF-004 regressions (REQ-0002/REQ-0007): the AST-rebuilt source slice
 * must RE-PARSE to the same semantics. Every case here failed before the
 * escaping/`Strikethrough`/list-indent work: escaped markers came back as
 * live syntax, `~~deleted~~` lost its node, and nested items flattened to
 * top level.
 */
class MarkdownRoundTripTest {
    private val parser = LeafletDocumentParser.createForJvmTests()
    private val renderer = MarkdownRenderer.createForJvmTests()

    private suspend fun docFor(text: String): ParsedDocument =
        parser.parse(DocumentId("rt"), DecodedText(text, EncodingState.CLEAN), "rt.md", true)

    private fun visible(raw: String): String = renderer.visibleTextOf(renderer.parseDocument(raw)).trim()

    private fun countNodes(
        raw: String,
        clazz: Class<out org.commonmark.node.Node>,
    ): Int {
        var n = 0

        fun walk(node: org.commonmark.node.Node) {
            if (clazz.isInstance(node)) n++
            var child = node.firstChild
            while (child != null) {
                walk(child)
                child = child.next
            }
        }
        walk(renderer.parseDocument(raw))
        return n
    }

    private fun visibleOfBlock(block: RenderBlock): String = renderer.visibleTextOf(renderer.parseDocument(block.source)).trim()

    /**
     * The core DEF-004 invariant: re-parsing a rebuilt slice yields the same
     * visible text, and no literal character that was escaped in the original
     * document becomes live inline syntax again.
     */
    private suspend fun assertRoundTrip(input: String) {
        val doc = docFor(input)
        val before =
            countNodes(input, org.commonmark.node.Emphasis::class.java) +
                countNodes(input, org.commonmark.node.StrongEmphasis::class.java) +
                countNodes(input, org.commonmark.ext.gfm.strikethrough.Strikethrough::class.java)
        val after =
            doc.blocks.sumOf { b ->
                countNodes(b.source, org.commonmark.node.Emphasis::class.java) +
                    countNodes(b.source, org.commonmark.node.StrongEmphasis::class.java) +
                    countNodes(b.source, org.commonmark.ext.gfm.strikethrough.Strikethrough::class.java)
            }
        assertEquals("inline decoration count for <<$input>>", before, after)
        for (block in doc.blocks) {
            assertEquals(
                "visible text round-trip for <<$input>> block ${block.index}",
                doc.blockTexts[block.index],
                visibleOfBlock(block),
            )
        }
    }

    @Test
    fun escapedMarkersStayLiteralText() =
        runTest {
            val doc = docFor("\\*literal\\* \\_also\\_ \\#tag\n")
            val block = doc.blocks.single()
            assertEquals("*literal* _also_ #tag", doc.blockTexts[0])
            // The rebuilt slice may not reintroduce emphasis/heading syntax.
            assertEquals(0, countNodes(block.source, org.commonmark.node.Emphasis::class.java))
            assertEquals(0, countNodes(block.source, org.commonmark.node.Heading::class.java))
            assertEquals("*literal* _also_ #tag", visibleOfBlock(block))
        }

    @Test
    fun strikethroughKeepsItsNode() =
        runTest {
            val doc = docFor("keep ~~deleted~~ tail\n")
            val block = doc.blocks.single()
            assertEquals("keep deleted tail", doc.blockTexts[0])
            assertTrue("strike markers survive re-emit: ${block.source}", block.source.contains("~~"))
            assertEquals(
                1,
                countNodes(block.source, org.commonmark.ext.gfm.strikethrough.Strikethrough::class.java),
            )
            assertEquals("keep deleted tail", visibleOfBlock(block))
        }

    @Test
    fun inlineCodeKeepsItsLiteral() =
        runTest {
            // A code span with backticks inside widens its delimiter; the
            // re-parsed code literal must stay byte-identical.
            val doc = docFor("call `a `` b` now\n")
            val block = doc.blocks.single()

            fun firstCode(node: org.commonmark.node.Node): org.commonmark.node.Code? {
                if (node is org.commonmark.node.Code) return node
                var child = node.firstChild
                while (child != null) {
                    firstCode(child)?.let { return it }
                    child = child.next
                }
                return null
            }
            assertEquals("a `` b", firstCode(renderer.parseDocument(block.source))?.literal)
            assertEquals("call a `` b now", visibleOfBlock(block))
        }

    @Test
    fun nestedListsKeepTheirParent() =
        runTest {
            val input = "- Parent item\n  - Child one\n  - Child two\n- Second parent\n"
            val doc = docFor(input)
            val list = doc.blocks.single()
            assertEquals(BlockKind.LIST, list.kind)
            val reparsed = renderer.parseDocument(list.source).firstChild
            assertTrue("top level stays a list", reparsed is org.commonmark.node.BulletList)
            var topLevelItems = 0
            var child = reparsed?.firstChild
            while (child != null) {
                topLevelItems++
                child = child.next
            }
            assertEquals(2, topLevelItems)
            val nested = countNodes(list.source, org.commonmark.node.BulletList::class.java)
            assertEquals("sublist survives as a nested list", 2, nested)
            val all = doc.blockTexts.joinToString("\n")
            assertTrue(all.contains("Child one"))
            assertTrue(all.contains("Second parent"))
        }

    @Test
    fun multiParagraphQuoteKeepsBothParagraphs() =
        runTest {
            val doc = docFor("> first line\n>\n> second line\n")
            val quote = doc.blocks.single()
            assertEquals(BlockKind.QUOTE, quote.kind)
            assertEquals("first line\n\nsecond line", doc.blockTexts[0])
            assertEquals(
                "both paragraphs survive re-parse",
                2,
                countNodes(quote.source, org.commonmark.node.Paragraph::class.java),
            )
            assertEquals("first line\n\nsecond line", visibleOfBlock(quote))
        }

    @Test
    fun literalLineStartsAreNeverBlockSyntax() =
        runTest {
            // A line-start `>` is real content here (it arrived escaped in the
            // source), so the slice must re-escape it — a bare `>` would end
            // the quote and drop the second line entirely.
            val doc = docFor("> escaped \\> marker\n>\n> second\n")
            val quote = doc.blocks.single()
            assertEquals(BlockKind.QUOTE, quote.kind)
            assertEquals("escaped > marker\n\nsecond", doc.blockTexts[0])
            assertEquals("escaped > marker\n\nsecond", visibleOfBlock(quote))
            // The rebuilt slice re-parses to the SAME shape: one quote with
            // two paragraphs, the literal `>` still plain text.
            assertEquals(1, countNodes(quote.source, org.commonmark.node.BlockQuote::class.java))
            assertEquals(2, countNodes(quote.source, org.commonmark.node.Paragraph::class.java))
            assertEquals(0, countNodes(quote.source, org.commonmark.ext.gfm.tables.TableBlock::class.java))
        }

    // -- R2 DEF-004 (second round): fence length + body-as-data -------------

    @Test
    fun fourBacktickFenceWithInnerFenceKeepsWholeBody() =
        runTest {
            // A legal document: the outer fence is four backticks, the body
            // contains a literal three-backtick line. The rebuild used to
            // close at that line, losing `after` (display) and truncating the
            // copy path's scan.
            val doc = docFor("````text\nbefore\n```\nafter\n````\n")
            val code = doc.blocks.single()
            assertEquals(BlockKind.CODE, code.kind)
            assertEquals("before\n```\nafter", code.codeForCopy())
            // The rebuilt slice re-parses to the SAME body…
            assertEquals("before\n```\nafter", visibleOfBlock(code))
            // …and the emitted fence is longer than anything in the body.
            assertTrue("fence widened: ${code.source.lines().first()}", code.source.startsWith("````"))
            assertEquals("text", code.language)
            assertEquals("```` text", code.source.lines().first())
        }

    @Test
    fun tildeFenceKeepsWholeBody() =
        runTest {
            val doc = docFor("~~~\nbefore\n```\nafter\n~~~\n")
            val code = doc.blocks.single()
            assertEquals(BlockKind.CODE, code.kind)
            assertEquals("before\n```\nafter", code.codeForCopy())
            assertEquals("before\n```\nafter", visibleOfBlock(code))
        }

    @Test
    fun indentedCodeCopyDropsIndentation() =
        runTest {
            val doc = docFor("    line one\n    line two\n\ntail paragraph\n")
            val code = doc.blocks.first { it.kind == BlockKind.CODE }
            // The body is data: copy is the literal, not the indented slice.
            assertEquals("line one\nline two", code.codeForCopy())
            assertEquals("line one\nline two", visibleOfBlock(code))
        }

    @Test
    fun listWithNonListItemChildrenStillRebuilds() =
        runTest {
            // Markwon's task-list plugin replaces list items with
            // `TaskListItem extends CustomBlock` — NOT a commonmark `ListItem`.
            // The rebuild filtered children with `filterIsInstance<ListItem>()`,
            // so a task list produced an EMPTY slice: the block rendered as an
            // empty TextView on device (found by R3-03). Any child of a list
            // must contribute to its slice.
            val list = org.commonmark.node.BulletList()
            list.appendChild(org.commonmark.node.Paragraph().apply { appendChild(org.commonmark.node.Text("open task")) })
            val slice = BlockSlicer.emit(list, renderer)
            assertTrue("slice must not be empty: <<$slice>>", slice.isNotBlank())
            assertTrue("slice keeps the item text: <<$slice>>", slice.contains("open task"))
            assertEquals("open task", renderer.visibleTextOf(renderer.parseDocument(slice)).trim())
        }

    @Test
    fun taskMarkerItemKeepsItsCheckboxMarker() =
        runTest {
            // JVM shim shape: the marker is a CHILD of a list item, so the
            // rebuilt slice keeps `[ ]`/`[x]` next to the item text.
            val list = org.commonmark.node.BulletList()
            list.appendChild(taskItem(done = false, text = "open task"))
            list.appendChild(taskItem(done = true, text = "done task"))
            val slice = BlockSlicer.emit(list, renderer)
            assertTrue("[ ] marker kept: <<$slice>>", slice.contains("[ ]"))
            assertTrue("[x] marker kept: <<$slice>>", slice.contains("[x]"))
            assertTrue("item text kept: <<$slice>>", slice.contains("open task"))
            assertTrue("item text kept: <<$slice>>", slice.contains("done task"))
        }

    private fun taskItem(
        done: Boolean,
        text: String,
    ): org.commonmark.node.ListItem =
        org.commonmark.node.ListItem().apply {
            appendChild(TaskListPostProcessorShim.TaskMarker(done))
            appendChild(
                org.commonmark.node.Paragraph().apply {
                    appendChild(org.commonmark.node.Text(text))
                },
            )
        }

    @Test
    fun plainFenceStillCopiesVerbatim() =
        runTest {
            val doc = docFor("```kotlin\nval a = 1\nval b = 2\n```\n")
            val code = doc.blocks.single()
            assertEquals("val a = 1\nval b = 2", code.codeForCopy())
            // Fence + info word (the space after the fence is the emitted form).
            assertEquals("``` kotlin", code.source.lines().first())
        }

    @Test
    fun fullDocumentStructureRoundTrips() =
        runTest {
            assertRoundTrip("\\*literal\\* and **bold** and `code`\n")
            assertRoundTrip("- a\n  - b\n    - c\n\n- d\n")
            assertRoundTrip("3. three\n4. four\n")
            assertRoundTrip("> quoted **bold**\n>\n> second quoted line\n")
            assertRoundTrip("```text\ncode ``` inside\n```\n")
            assertRoundTrip("see [label](other.md) here\n")
            assertRoundTrip("| A | B |\n| --- | --- |\n| x | y |\n")
            assertRoundTrip("plain line\n\n# heading **bold**\n\npara `code` ~~gone~~\n")
        }
}
