package io.leaflet.markdown

import io.leaflet.model.DocumentId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/**
 * Cancellable markdown/TXT parse pipeline (T06, REQ-0002 FR-1/FR-3/FR-4/FR-7).
 *
 * Owns: frontmatter title rule, top-level block split, oversized-block
 * bounding, TOC anchors, and the [BlockPositionMap]. Rendering on Android
 *
 * Cancellation: every stage checks the coroutine context (`ensureActive`) and
 * propagates [CancellationException] unwrapped — a cancelled parse never
 * surfaces as [ParsedDocument] or as failure, so callers (ReaderSession) can
 * drop it without showing an error state.
 */
class LeafletDocumentParser internal constructor(
    val renderer: MarkdownRenderer,
) {
    companion object {
        /** Android entry: Markwon-backed renderer (Context only for themes). */
        fun create(context: android.content.Context): LeafletDocumentParser = LeafletDocumentParser(MarkdownRenderer.create(context))

        /** Pure-JVM entry for unit tests. */
        fun createForJvmTests(): LeafletDocumentParser = LeafletDocumentParser(MarkdownRenderer.createForJvmTests())
    }

    /**
     * Parse one document's decoded text into a [ParsedDocument].
     *
     * @param isMarkdown false forces the TXT path: whole text is literal
     *   paragraphs, `#`/`-`/etc. never parsed (REQ-0002 FR-5).
     * @param fallbackTitle file name without extension (title rule last resort).
     */
    suspend fun parse(
        documentId: DocumentId,
        decoded: DecodedText,
        fileName: String,
        isMarkdown: Boolean,
    ): ParsedDocument {
        val fallbackTitle = fileName.substringBeforeLast('.').ifEmpty { fileName }
        if (decoded.text.isEmpty()) {
            return ParsedDocument(
                documentId = documentId,
                title = fallbackTitle,
                toc = emptyList(),
                blocks = emptyList(),
                positionMap = PositionMap(0),
                degraded = decoded.encodingState == EncodingState.HAD_ENCODING_DAMAGE,
                degradedNotice = noticeFor(decoded.encodingState, oversized = false),
                blockTexts = emptyList(),
                positionOps = BlockPositionMap(emptyList()),
            )
        }
        if (!isMarkdown) {
            coroutineContext.ensureActive()
            return parseTxt(documentId, decoded, fallbackTitle)
        }
        return parseMarkdown(documentId, decoded, fallbackTitle)
    }

    private suspend fun parseTxt(
        documentId: DocumentId,
        decoded: DecodedText,
        fallbackTitle: String,
    ): ParsedDocument {
        // TXT chunks on blank-line runs; single huge lines split bounded.
        // Every block here is LITERAL (R2 DEF-001): TXT never carries
        // Markdown syntax, so the entries must not re-parse these chunks.
        val rawChunks = decoded.text.split(Regex("\n\\s*\n"))
        val blocks = ArrayList<RenderBlock>()
        val visible = ArrayList<String>()
        for (chunk in rawChunks) {
            coroutineContext.ensureActive()
            val text = chunk.trimEnd('\n')
            if (text.isBlank()) continue
            if (text.length > BlockLimits.MAX_BLOCK_CHARS) {
                var rest = text
                while (rest.length > BlockLimits.MAX_BLOCK_CHARS) {
                    coroutineContext.ensureActive()
                    val part = rest.substring(0, BlockLimits.MAX_BLOCK_CHARS)
                    blocks.add(RenderBlock(blocks.size, BlockKind.PLAIN_FALLBACK, part, literal = true))
                    visible.add(part)
                    rest = rest.substring(BlockLimits.MAX_BLOCK_CHARS)
                }
                if (rest.isNotEmpty()) {
                    blocks.add(RenderBlock(blocks.size, BlockKind.PLAIN_FALLBACK, rest, literal = true))
                    visible.add(rest)
                }
            } else {
                blocks.add(RenderBlock(blocks.size, BlockKind.PARAGRAPH, text, literal = true))
                visible.add(text)
            }
        }
        val oversized = blocks.any { it.kind == BlockKind.PLAIN_FALLBACK }
        val ops = BlockPositionMap(visible)
        return ParsedDocument(
            documentId = documentId,
            title = fallbackTitle,
            toc = emptyList(),
            blocks = blocks,
            positionMap = PositionMap(blocks.size),
            degraded = oversized || decoded.encodingState == EncodingState.HAD_ENCODING_DAMAGE,
            degradedNotice = noticeFor(decoded.encodingState, oversized),
            blockTexts = visible.toList(),
            positionOps = ops.withAnchors(emptyList(), blocks),
            literalText = true,
        )
    }

    private suspend fun parseMarkdown(
        documentId: DocumentId,
        decoded: DecodedText,
        fallbackTitle: String,
    ): ParsedDocument {
        coroutineContext.ensureActive()
        val frontmatter = Frontmatter.extract(decoded.text)
        val body =
            if (frontmatter.bodyStartLine > 0) {
                decoded.text.lineSequence().drop(frontmatter.bodyStartLine).joinToString("\n")
            } else {
                decoded.text
            }
        coroutineContext.ensureActive()
        val root = renderer.parseDocument(body)
        coroutineContext.ensureActive()
        val headings = renderer.extractHeadings(root)
        val anchors = Headings.uniqueAnchors(headings.map { it.second })
        val toc =
            headings.mapIndexed { i, (level, text) ->
                TocEntry(level.coerceIn(1, 6), text, anchors[i])
            }
        coroutineContext.ensureActive()
        val title =
            frontmatter.title
                ?: headings.firstOrNull { it.first == 1 }?.second
                ?: fallbackTitle
        coroutineContext.ensureActive()
        val nodes: List<Any> = renderer.topLevelBlocks(root)
        coroutineContext.ensureActive()
        val sources = BlockSlicer.slice(body, nodes, renderer)
        val blocks = ArrayList<RenderBlock>()
        val visible = ArrayList<String>()
        var oversized = false
        for (i in nodes.indices) {
            // Same T13 yield rationale as BlockSlicer.slice: without a real
            // suspension point the runTest dispatcher starves and cancellation
            // (or the 60s test timeout) never fires on a 200k-block document.
            if (i % 512 == 0) kotlinx.coroutines.yield()
            coroutineContext.ensureActive()
            val node = nodes[i]
            val source = sources.getOrElse(i) { "" }
            val kind = renderer.kindOf(node)
            val visibleText =
                try {
                    renderer.visibleTextOf(node)
                } catch (_: Exception) {
                    // Unknown node shape: degrade this block only, keep the rest.
                    oversized = true
                    ""
                }
            if (source.length > BlockLimits.MAX_BLOCK_CHARS) {
                // Oversized single block: bounded plain-text chunks + notice.
                // These chunks are LITERAL (R2 DEF-001): they are raw slices,
                // so entries must not re-parse them as Markdown.
                oversized = true
                var rest = source
                while (rest.length > BlockLimits.MAX_BLOCK_CHARS) {
                    coroutineContext.ensureActive()
                    val part = rest.substring(0, BlockLimits.MAX_BLOCK_CHARS)
                    blocks.add(RenderBlock(blocks.size, BlockKind.PLAIN_FALLBACK, part, literal = true))
                    visible.add(part)
                    rest = rest.substring(BlockLimits.MAX_BLOCK_CHARS)
                }
                if (rest.isNotEmpty()) {
                    blocks.add(RenderBlock(blocks.size, BlockKind.PLAIN_FALLBACK, rest, literal = true))
                    visible.add(rest)
                }
            } else {
                // Tables render empty via TextContentRenderer (cell text lives
                // in child TableCell nodes the core text renderer ignores) —
                // fall back to the source slice instead of a false fallback.
                val visibleOrSource =
                    visibleText.ifEmpty { if (kind == BlockKind.TABLE) source else "" }
                val effectiveKind =
                    if (visibleOrSource.isEmpty() && kind != BlockKind.SEPARATOR) {
                        BlockKind.PLAIN_FALLBACK
                    } else {
                        kind
                    }
                if (effectiveKind == BlockKind.PLAIN_FALLBACK && kind != BlockKind.SEPARATOR) {
                    oversized = true
                }
                val display = if (kind == BlockKind.SEPARATOR) "---" else source
                val language = if (kind == BlockKind.CODE) CodeLanguage.of(node) else ""
                val links =
                    if (kind == BlockKind.CODE) {
                        emptyList()
                    } else {
                        runCatching {
                            renderer.extractBlockLinks(
                                node,
                            )
                        }.getOrDefault(emptyList())
                    }
                val images =
                    if (kind == BlockKind.CODE) {
                        emptyList()
                    } else {
                        runCatching {
                            renderer.extractBlockImages(
                                node,
                            )
                        }.getOrDefault(emptyList())
                    }
                blocks.add(
                    RenderBlock(
                        blocks.size,
                        effectiveKind,
                        display,
                        source,
                        language,
                        links,
                        images,
                        // Degraded to plain text (DEF-001): the slice is raw
                        // source, so entries must not re-parse it.
                        literal = effectiveKind == BlockKind.PLAIN_FALLBACK,
                        // R2 DEF-004: the code body travels as data, so copy
                        // never has to guess fence boundaries from the slice.
                        codeBody = if (kind == BlockKind.CODE) codeBodyOf(node) else null,
                    ),
                )
                visible.add(visibleOrSource.ifEmpty { display })
            }
        }
        val damaged = decoded.encodingState == EncodingState.HAD_ENCODING_DAMAGE
        val ops = BlockPositionMap(visible).withAnchors(toc, blocks)
        return ParsedDocument(
            documentId = documentId,
            title = title,
            toc = toc,
            blocks = blocks,
            positionMap = PositionMap(blocks.size),
            degraded = oversized || damaged,
            degradedNotice = noticeFor(decoded.encodingState, oversized),
            blockTexts = visible.toList(),
            positionOps = ops,
        )
    }

    /**
     * Verbatim code body of a code block node (R2 DEF-004): fenced blocks
     * carry `info` + `literal`; indented blocks carry only `literal`. Unknown
     * shapes yield null (the block then falls back to the slice scan).
     */
    private fun codeBodyOf(node: Any): String? =
        when (node) {
            is org.commonmark.node.FencedCodeBlock -> node.literal ?: ""
            is org.commonmark.node.IndentedCodeBlock -> node.literal ?: ""
            else -> null
        }

    private fun noticeFor(
        state: EncodingState,
        oversized: Boolean,
    ): String? {
        if (!oversized && state != EncodingState.HAD_ENCODING_DAMAGE) return null
        val parts = ArrayList<String>(2)
        if (oversized) {
            parts.add("本文档部分内容以纯文本显示")
        }
        if (state == EncodingState.HAD_ENCODING_DAMAGE) {
            parts.add("编码无法识别，已按替换字符显示部分内容")
        }
        return parts.joinToString("；")
    }

    /**
     * File-open helper used by the Reader session: read [stream] fully on
     * [ioDispatcher], decode, then parse. The caller owns [stream] (use {}).
     * Throwing here is reserved for I/O failures; parse problems degrade.
     */
    suspend fun openAndParse(
        documentId: DocumentId,
        fileName: String,
        isMarkdown: Boolean,
        stream: java.io.InputStream,
        ioDispatcher: kotlin.coroutines.CoroutineContext,
    ): ParsedDocument {
        val bytes =
            withContext(ioDispatcher) {
                coroutineContext.ensureActive()
                stream.use { it.readBytes() }
            }
        coroutineContext.ensureActive()
        return parse(documentId, TextDecoder.decode(bytes), fileName, isMarkdown)
    }
}

/**
 * Visible-text position map: block count, per-block visible text, anchor and
 * literal-query lookup (reader.md §4 + ADR-0006 decision 5).
 *
 * Search matches literal substrings of visible text — syntax markers are
 * never matched because [visible] holds rendered text, not source.
 */
class BlockPositionMap(
    private val visible: List<String>,
    private val anchors: Map<String, Int> = emptyMap(),
) : PositionMapOps {
    override fun blockCount(): Int = visible.size

    override fun visibleTextOf(blockIndex: Int): String = if (blockIndex in visible.indices) visible[blockIndex] else ""

    override fun locateAnchor(anchor: String): BlockLocation? {
        val index = anchors[anchor] ?: return null
        return BlockLocation(index, 0)
    }

    override fun locateQuery(query: String): List<MatchLocation> {
        // R2-06: blank never searches (REQ-0008 edge rule, same as
        // DocumentSearch); the shared capped scan keeps order/cap identical
        // on every search path (see SearchLimits/collectBlockMatches).
        if (query.isBlank()) return emptyList()
        val out = ArrayList<MatchLocation>()
        for (b in visible.indices) {
            if (collectBlockMatches(visible[b], query, b, out)) break
        }
        return out
    }

    /**
     * Rebind TOC anchors after [ParsedDocument] assembly (same order).
     *
     * R2-02: anchors bind by TOC ORDER (k-th TOC entry → k-th heading
     * block), never by visible-text search. The old
     * `headingBlocks.indexOfFirst { it.text.contains(entry.text) }` mapped
     * every duplicate to the first heading and cost O(H²); order binding is
     * exact (the TOC is built from the same heading walk) and O(H).
     */
    fun withAnchors(
        toc: List<TocEntry>,
        blocks: List<RenderBlock>,
    ): BlockPositionMap {
        val map = HashMap<String, Int>()
        val headingBlocks = blocks.filter { it.kind == BlockKind.HEADING }
        // TOC entries are emitted in document order by extractHeadings, so
        // zipping by position binds each anchor to its own heading block —
        // including duplicates (`overview`, `overview-1`, …) and substring
        // titles (`Over` vs `Overview`), which text search conflated.
        for ((entry, block) in toc.zip(headingBlocks)) {
            map.putIfAbsent(entry.anchor, block.index)
        }
        return BlockPositionMap(visible, map)
    }
}
