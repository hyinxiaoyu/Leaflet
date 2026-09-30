package io.leaflet.markdown

import android.content.Context
import io.noties.markwon.Markwon
import io.noties.markwon.MarkwonConfiguration
import io.noties.markwon.core.CorePlugin
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import org.commonmark.ext.gfm.strikethrough.Strikethrough
import org.commonmark.node.BlockQuote
import org.commonmark.node.BulletList
import org.commonmark.node.Code
import org.commonmark.node.Emphasis
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.Heading
import org.commonmark.node.HtmlBlock
import org.commonmark.node.HtmlInline
import org.commonmark.node.Image
import org.commonmark.node.IndentedCodeBlock
import org.commonmark.node.Link
import org.commonmark.node.ListItem
import org.commonmark.node.Node
import org.commonmark.node.OrderedList
import org.commonmark.node.Paragraph
import org.commonmark.node.StrongEmphasis
import org.commonmark.node.Text
import org.commonmark.node.ThematicBreak
import org.commonmark.parser.Parser

/**
 * The ONLY place in the codebase that touches Markwon / commonmark-java
 * directly (ADR-0004 decision 3, research §4).
 *
 * Everything else in `:core:markdown` (title rules, chunking, position map)
 * works on plain Kotlin data ([ParsedDocument]/[RenderBlock]); only this file
 * imports `io.noties.markwon.*` / `org.commonmark.*`. Replacing the renderer
 * (research R1) means replacing this file, not the Reader.
 *
 * Plugin chain (frozen, audited 2026-09-18): core + ext-strikethrough +
 * ext-tables ([TablePlugin], NORMAL TextView path) + ext-tasklist.
 * NEVER mixed in: `TableEntryPlugin` (its KDoc forbids combining with
 * [TablePlugin]), image-glide/coil/picasso, okhttp, WebView.
 */
class MarkdownRenderer internal constructor(
    private val parser: Parser,
    /**
     * Block Spanned renderer (T09, REQ-0007 Markwon upgrade). Null on the
     * pure-JVM test path where Android `Markwon` cannot be constructed;
     * non-null on device via [create]. [renderSpanned] falls back to plain
     * text when null, so JVM tests exercise block shape/copy/scroll policy
     * while device binding renders styled Spanned (tables, task lists).
     */
    private val spannedRenderer: ((String) -> CharSequence)? = null,
) {
    companion object {
        /**
         * Android factory: builds a Markwon-backed parser with the frozen
         * plugin chain. `context` is used only for theme/plugin construction;
         * parsing itself allocates no views and is safe on a background thread.
         *
         * Link handling: the default Markwon `LinkResolverDef` silently ignores
         * links it cannot handle — T06 overrides it with [InterceptingLinkResolver],
         * which records taps in-library for T10 and never fires an Intent here.
         */
        fun create(context: Context): MarkdownRenderer {
            val appContext = context.applicationContext
            val markwon =
                Markwon.builder(appContext)
                    .usePlugin(CorePlugin.create())
                    .usePlugin(StrikethroughPlugin.create())
                    .usePlugin(TablePlugin.create(appContext))
                    .usePlugin(TaskListPlugin.create(appContext))
                    // Library-internal interception placeholder (T10 implements).
                    .usePlugin(LinkInterceptPlugin())
                    .build()
            // Spanned rendering reuses the live parsing pipeline: re-parse the
            // exact per-block source slice (T09 seam) on the caller thread and
            // render to Spanned. Callers MUST invoke off the UI thread
            // (session/parse threads); binding only sets text on the UI thread.
            // Unknown syntax degrades to plain text (never throws).
            val render: (String) -> CharSequence = { source ->
                try {
                    val node = markwon.parse(source)
                    markwon.render(node)
                } catch (_: Exception) {
                    source
                }
            }
            return MarkdownRenderer(parserWith(markwon), render)
        }

        /**
         * Pure-JVM factory for unit tests: same parser extensions as [create]
         * but without Android theme/plugin construction (task-list drawables,
         * table themes need a Context). Tables and `~~deletion~~` still parse
         * because the extensions live on the commonmark parser, not the theme.
         */
        fun createForJvmTests(): MarkdownRenderer {
            val extensions =
                listOf(
                    org.commonmark.ext.gfm.strikethrough.StrikethroughExtension.create(),
                    org.commonmark.ext.gfm.tables.TablesExtension.create(),
                )
            val builder = Parser.builder().extensions(extensions)
            // Same post-processor Markwon's TaskListPlugin contributes via
            // configureParser, so JVM block shapes match device rendering.
            TaskListPostProcessorShim.install(builder)
            return MarkdownRenderer(builder.build())
        }

        private fun parserWith(markwon: Markwon): Parser {
            // Rebuild the commonmark parser from the live Markwon plugin chain
            // so block splitting sees exactly the extensions Markwon renders
            // with (tables, strikethrough, task-list post-processor).
            val builder = Parser.builder()
            for (plugin in markwon.plugins) {
                plugin.configureParser(builder)
            }
            return builder.build()
        }
    }

    /** Parse-only entry (background thread). Never throws for malformed input. */
    fun parseDocument(markdown: String): Node = parser.parse(markdown)

    /**
     * Top-level block split: direct children of the Document node. Suspends:
     * one `ensureActive` per block so cancelling a 200k-block parse drops
     * inside this walk, not after it (same T13 gate fix as [BlockSlicer]).
     */
    suspend fun topLevelBlocks(root: Any): List<Any> {
        root as Node
        val out = ArrayList<Any>()
        var node = root.firstChild ?: return out
        while (node != null) {
            kotlinx.coroutines.yield()
            val next = node.next
            // Skip link-reference definitions (would render as empty entries).
            if (node !is org.commonmark.node.LinkReferenceDefinition) {
                node.unlink()
                out.add(node)
            }
            node = next
        }
        return out
    }

    /**
     * Plain visible text of one block (no syntax markers) for search/TOC
     * (R2 DEF-004 fix): walked off the AST instead of
     * Markwon's `TextContentRenderer` core renderer, which emits
     * placeholder glyphs for containers like `BlockQuote` (`«…»`) and drops
     * `Strikethrough` content. The walker keeps exactly the characters a
     * reader sees: literals, line breaks, code bodies, image alt text; link
     * destinations and syntax markers never appear.
     */
    fun visibleTextOf(node: Any): String = visibleTextOfNode(node as Node).trim()

    /**
     * @param keepSpaces true when the caller separates children itself (a
     *   part's leading/trailing spaces are word boundaries, not noise).
     */
    private fun visibleTextOfNode(
        node: Node,
        keepSpaces: Boolean = false,
    ): String {
        val containerChars = visibleContainerText(node)
        if (containerChars != null) return containerChars
        val source = node.sourceLiteral()
        if (source != null) return if (keepSpaces) source else source.trim()
        val out = StringBuilder()
        var child = node.firstChild
        while (child != null) {
            out.append(visibleTextOfNode(child, keepSpaces))
            child = child.next
        }
        return out.toString()
    }

    /** Visible text of containers whose child layout matters; null = plain recursion. */
    private fun visibleContainerText(node: Node): String? =
        when (node) {
            // Inline containers concatenate their children unchanged: a
            // `Text` sibling's trailing space is part of the visible line.
            is org.commonmark.node.Paragraph,
            is org.commonmark.node.Heading,
            -> childrenText(node, "", keepSpaces = true)
            is org.commonmark.node.ListItem,
            -> childrenText(node, "", keepSpaces = true)
            // A quote's blocks sit on separate lines: joining them without a
            // break made two paragraphs read as one run (and desynchronized
            // the rebuilt source slice from the visible text).
            is BlockQuote -> childrenText(node, "\n\n")
            is BulletList, is OrderedList -> childrenText(node, "\n")
            // TableBlock → TableHead/TableBody → TableRow → TableCell:
            // rows stack vertically, cells inside one row sit side by side as
            // separate search-visible strings.
            is org.commonmark.ext.gfm.tables.TableBlock -> childrenText(node, "\n")
            is org.commonmark.ext.gfm.tables.TableRow -> childrenText(node, "\t")
            is org.commonmark.ext.gfm.tables.TableCell -> childrenText(node, "", keepSpaces = true)
            // Wrapper nodes (task markers, custom inline containers) are text
            // transparent; their children carry the visible characters.
            is org.commonmark.node.CustomBlock,
            is org.commonmark.node.CustomNode,
            -> childrenText(node, "")
            else -> null
        }

    private fun childrenText(
        node: Node,
        separator: String,
        keepSpaces: Boolean = false,
    ): String {
        val parts = ArrayList<String>()
        var child = node.firstChild
        while (child != null) {
            // Per-part trimming drops only surrounding whitespace that the
            // separator replaces — inner spaces stay word boundaries.
            val part = visibleTextOfNode(child, keepSpaces)
            parts.add(if (keepSpaces) part else part.trim())
            child = child.next
        }
        return parts.joinToString(separator)
    }

    /** Leaf literal of leaf nodes whose text IS the node's content. */
    private fun Node.sourceLiteral(): String? =
        when (this) {
            is Text -> literal ?: ""
            is Code -> literal ?: ""
            is FencedCodeBlock -> literal ?: ""
            is IndentedCodeBlock -> literal ?: ""
            is HtmlInline -> literal ?: ""
            is HtmlBlock -> literal ?: ""
            is org.commonmark.node.HardLineBreak -> "\n"
            is org.commonmark.node.SoftLineBreak -> "\n"
            is ThematicBreak -> "---"
            // Images contribute their alt text; links contribute only their
            // label (children), never the destination.
            is Image -> if (firstChild == null) title ?: "" else null
            else -> null
        }

    /**
     * Render one block's exact source slice to styled text (T09 Spanned
     * upgrade, REQ-0007): Markwon Spanned on device (tables/task lists),
     * plain-text fallback on the JVM path or for unknown syntax (never throws,
     * never empty when the source is non-blank — blank sources stay blank so
     * separators keep their `"---"` display).
     *
     * R2 DEF-001: a [RenderBlock.literal] block (TXT chunk or plain fallback)
     * is returned verbatim — it was never Markdown, so it must never be
     * re-parsed into spans.
     */
    fun renderSpanned(block: RenderBlock): CharSequence {
        val source = block.source.ifEmpty { block.text }
        if (source.isBlank()) return source
        // R2 DEF-001: literal blocks (TXT, plain fallback) are never Markdown;
        // re-parsing them would turn visible `#`/`**`/`[x](y)` into syntax.
        if (block.literal) return source
        val renderer = spannedRenderer ?: return source
        return try {
            renderer(source)
        } catch (_: Exception) {
            source
        }
    }

    /** Classify one top-level block into a [BlockKind]. */
    fun kindOf(node: Any): BlockKind = kindOfNode(node as Node)

    private fun kindOfNode(node: Node): BlockKind =
        when (node) {
            is Heading -> BlockKind.HEADING
            is FencedCodeBlock, is IndentedCodeBlock -> BlockKind.CODE
            is BulletList, is OrderedList -> BlockKind.LIST
            is BlockQuote -> BlockKind.QUOTE
            is ThematicBreak -> BlockKind.SEPARATOR
            is HtmlBlock -> BlockKind.PLAIN_FALLBACK
            is TaskListPostProcessorShim.TaskMarker -> BlockKind.LIST
            is Paragraph, is Text, is Emphasis, is StrongEmphasis, is Strikethrough,
            is Link, is Image, is Code, is ListItem,
            -> BlockKind.PARAGRAPH
            else ->
                when (node.javaClass.name) {
                    // Matched by name: `TableBlock` comes from the accepted commonmark
                    // tables extension; `TaskListItem` from Markwon's Android
                    // task-list post-processor (unloadable on the JVM).
                    "org.commonmark.ext.gfm.tables.TableBlock" -> BlockKind.TABLE
                    "io.noties.markwon.ext.tasklist.TaskListItem" -> BlockKind.LIST
                    else -> BlockKind.PARAGRAPH
                }
        }

    /**
     * Headings in document order: (level, visible text). First H1 feeds the
     * title rule; all feed the TOC (anchors assigned by [Headings]). Suspends
     * for the same T13 cancellation reason as [topLevelBlocks].
     */
    suspend fun extractHeadings(root: Any): List<Pair<Int, String>> {
        root as Node
        val out = ArrayList<Pair<Int, String>>()

        suspend fun walk(parent: Node) {
            var child: Node? = parent.firstChild
            while (child != null) {
                kotlinx.coroutines.yield()
                if (child is Heading) {
                    val text = visibleTextOf(child)
                    if (text.isNotEmpty()) out.add(child.level to text)
                } else {
                    // Headings are leaf blocks in commonmark; only custom
                    // wrapper nodes need a look inside.
                    if (child.firstChild != null) walk(child)
                }
                child = child.next
            }
        }
        walk(root)
        return out
    }

    /**
     * In-text links and images inside one top-level block (T10, REQ-0009):
     * walks the block subtree (reference definitions already skipped by
     * [topLevelBlocks], so ref-style links report their resolved URL, never
     * the `[label][ref]` label text). Labels are visible text; destinations
     * are classified by [ResourceLinks]. Never throws (unknown shapes
     * degrade to empty lists, never a failed block).
     */
    fun extractBlockLinks(node: Any): List<BlockLink> {
        node as Node
        val out = ArrayList<BlockLink>()
        node.accept(
            object : org.commonmark.node.AbstractVisitor() {
                override fun visit(link: Link) {
                    out.add(classifyLinkNode(link.destination ?: "", link))
                    visitChildren(link)
                }
            },
        )
        return out
    }

    fun extractBlockImages(node: Any): List<BlockImage> {
        node as Node
        val out = ArrayList<BlockImage>()
        node.accept(
            object : org.commonmark.node.AbstractVisitor() {
                override fun visit(image: Image) {
                    out.add(classifyImageNode(image.destination ?: "", image))
                    visitChildren(image)
                }
            },
        )
        return out
    }

    private fun classifyLinkNode(
        destination: String,
        node: Node,
    ): BlockLink {
        val label =
            try {
                visibleTextOf(node)
            } catch (_: Exception) {
                ""
            }
        val ref = ResourceLinks.classifyDocLink(destination, label)
        return BlockLink(ref.label, destination, ref.kind, ref.pathPart, ref.fragment)
    }

    private fun classifyImageNode(
        destination: String,
        node: Node,
    ): BlockImage {
        val alt =
            try {
                visibleTextOf(node)
            } catch (_: Exception) {
                ""
            }
        val ref = ResourceLinks.classifyImage(destination, alt)
        return BlockImage(ref.alt, destination, ref.kind, ref.pathPart)
    }
}

/**
 * Fence info-string extraction (T09, REQ-0007 FR-5): the language label shown
 * in the code header and the copy path both key off this single function.
 *
 * Returns the raw info word lowercased (` ```Kotlin ` → `kotlin`); empty when
 * the fence declares nothing, when the block is indented code (no fence), or
 * when the node shape is unknown. Unknown names are returned as-is (never
 * invented, never mapped) — the UI shows them verbatim, never an error name.
 */
object CodeLanguage {
    fun of(node: Any): String {
        val info = (node as? FencedCodeBlock)?.info
        val word = info?.trim()?.split(Regex("\\s+"))?.firstOrNull().orEmpty()
        return word.lowercase()
    }
}

/**
 * Exact per-block source slices from AST structure (R2-02).
 *
 * commonmark 0.13.0 exposes no source spans on this classpath (verified
 * 2026-09-18: `includeSourceSpans` absent from `Parser$Builder`, and
 * `Node` carries no line info — checked by javap), so ranges CANNOT be
 * read off the tree. Instead of guessing ranges from visible text (the old
 * `contains` search merged `# A\n# B\n# C`, split formatted headings
 * wrongly, and truncated past 4000 lines), each block is REBUILT from its
 * own AST children: literals come from the tree (`Text/Code/Html` literals,
 * fenced `literal` + fence markers, table cells), never from searching the
 * body. The slice therefore covers exactly the block's content — no drops,
 * no duplicates, no 4000-line cap — at the cost that re-emitted markdown
 * is normalized (e.g. `#A` without space gains one; emphasis markers are
 * `*`/`**`, not the original `_`/`__`). Display, copy, and search all read
 * this slice or the visible text; the original spacing variant is not a
 * contract (no test pins it).
 *
 * Oversized policy is unchanged: a slice longer than
 * [BlockLimits.MAX_BLOCK_CHARS] is split into bounded `PLAIN_FALLBACK`
 * chunks downstream in `DocumentParser` (the slicer never truncates).
 */
internal object BlockSlicer {
    /**
     * Per-block source slices, in block order. Suspends: yields every 512
     * blocks so a cancelled 10MB parse drops promptly on the test
     * dispatcher (T13 gate fix). Pure CPU cost is unchanged.
     */
    suspend fun slice(
        body: String,
        blocks: List<Any>,
        renderer: MarkdownRenderer,
    ): List<String> {
        return blocks.mapIndexed { index, node ->
            if (index % 512 == 0) kotlinx.coroutines.yield()
            emit(node as org.commonmark.node.Node, renderer)
        }
    }

    /**
     * Rebuilds one block's markdown source from its AST subtree.
     *
     * R2 DEF-004: the rebuild must be RE-PARSABLE to the same semantics, so
     * every literal (`Text`/`Code`/`Html` content) is escaped for the exact
     * character set that can start inline syntax, and structural nodes are
     * re-emitted with their own markers. `~~strike~~` gains its own branch
     * (it used to be flattened to bare text, losing the decoration). The body
     * string is used solely for fenced-code bodies that carry no literal (kept
     * verbatim) — never searched by visible text.
     */
    internal fun emit(
        node: org.commonmark.node.Node,
        renderer: MarkdownRenderer,
    ): String =
        when (node) {
            is org.commonmark.node.Heading -> {
                val level = "#".repeat(node.level.coerceIn(1, 6))
                "$level ${emitChildren(node, renderer)}".trimEnd()
            }
            is org.commonmark.node.Paragraph -> emitChildren(node, renderer)
            is org.commonmark.node.BulletList ->
                listItems(node, ordered = false, renderer)
            is org.commonmark.node.OrderedList ->
                listItems(node, ordered = true, renderer)
            // Every line is emitted explicitly as `> <content>` (a blank
            // continuation line is a lone `>`), so several paragraphs survive
            // as several paragraphs while all of them stay inside the quote —
            // a bare blank line would end the quote and flatten the structure
            // on re-parse.
            is org.commonmark.node.BlockQuote -> {
                val parts = ArrayList<String>()
                var child = node.firstChild
                while (child != null) {
                    parts.add(emitLines(child, renderer))
                    child = child.next
                }
                parts.joinToString("\n\n").lineSequence().joinToString("\n") { line ->
                    if (line.isEmpty()) ">" else "> $line"
                }
            }
            is org.commonmark.node.FencedCodeBlock -> {
                val info = node.info?.trim().orEmpty()
                // Literal is exact (javap: getLiteral); blank lines inside
                // are content, never terminators — only the closing fence
                // ends the block, however long the body is (no 4000 cap).
                val body = node.literal ?: ""
                // R2 DEF-004: the fence must be LONGER than any backtick run
                // in the body. The previous fixed three-to-backticks fence
                // closed early whenever the source used four backticks or
                // `~~~` around a literal ``` line, truncating the block on
                // the next parse (display lost content too).
                val longest = Regex("`+").findAll(body).maxOfOrNull { it.value.length } ?: 0
                val fence = "`".repeat(maxOf(3, longest + 1))
                val infoPart = if (info.isEmpty()) "" else " $info"
                val bodyWithNewline = if (body.isEmpty() || body.endsWith("\n")) body else "$body\n"
                "$fence$infoPart\n$bodyWithNewline$fence"
            }
            is org.commonmark.node.IndentedCodeBlock ->
                (node.literal ?: "").lineSequence().joinToString("\n") { "    $it" }
            is org.commonmark.node.ThematicBreak -> "---"
            is org.commonmark.node.HtmlBlock -> node.literal ?: ""
            // Task-list checkbox state (T06 shim + Markwon device path share
            // the marker concept): keep the `[ ]`/`[x]` marker in the source
            // slice so task sections stay recognizable as tasks.
            is TaskListPostProcessorShim.TaskMarker ->
                if (node.isDone) "[x] " else "[ ] "
            // Markwon's Android task item (`TaskListItem extends CustomBlock`):
            // matched by NAME because the JVM path must not load the class; its
            // `isDone()` is read reflectively with a safe fallback.
            is org.commonmark.node.CustomBlock -> tableOrFallback(node, renderer)
            else -> tableOrFallback(node, renderer)
        }

    /**
     * Inline markdown of one subtree. `Text` and `HtmlInline` content is
     * ESCAPED (R2 DEF-004) so a literal `*`, `_`, `~`, backtick, bracket,
     * `<` or `&` can never be re-read as syntax; block-node children fall
     * back to their own block emit (escaping every structural character
     * would break lists and quotes instead of preserving them).
     */
    private fun emitChildren(
        node: org.commonmark.node.Node,
        renderer: MarkdownRenderer,
    ): String {
        val out = StringBuilder()
        var child = node.firstChild
        while (child != null) {
            inlineInto(child, out, renderer)
            child = child.next
        }
        return out.toString()
    }

    private fun inlineInto(
        node: org.commonmark.node.Node,
        out: StringBuilder,
        renderer: MarkdownRenderer,
    ) {
        when (node) {
            is org.commonmark.node.Text -> out.append(escapeText(node.literal ?: ""))
            is org.commonmark.node.Code -> {
                val literal = node.literal ?: ""
                // A code span cannot contain a run of backticks as long as its
                // delimiter; widen the delimiter when it does (spec rule).
                val longest = Regex("`+").findAll(literal).maxOfOrNull { it.value.length } ?: 0
                val ticks = "`".repeat(longest + 1)
                val pad = if (literal.startsWith('`') || literal.endsWith('`')) " " else ""
                out.append(ticks).append(pad).append(literal).append(pad).append(ticks)
            }
            is org.commonmark.node.Emphasis -> {
                out.append("*")
                inlineChildren(node, out, renderer)
                out.append("*")
            }
            is org.commonmark.node.StrongEmphasis -> {
                out.append("**")
                inlineChildren(node, out, renderer)
                out.append("**")
            }
            is org.commonmark.ext.gfm.strikethrough.Strikethrough -> {
                out.append("~~")
                inlineChildren(node, out, renderer)
                out.append("~~")
            }
            is org.commonmark.node.Link -> {
                out.append("[")
                inlineChildren(node, out, renderer)
                out.append("](").append(node.destination ?: "").append(")")
            }
            is org.commonmark.node.Image -> {
                out.append("![")
                inlineChildren(node, out, renderer)
                out.append("](").append(node.destination ?: "").append(")")
            }
            is org.commonmark.node.HtmlInline -> out.append(node.literal ?: "")
            is org.commonmark.node.SoftLineBreak -> out.append("\n")
            is org.commonmark.node.HardLineBreak -> out.append("  \n")
            else -> {
                if (node.firstChild != null) {
                    inlineChildren(node, out, renderer)
                } else {
                    // Custom inline node without children: only `Text` has a
                    // literal; anything else contributes nothing.
                    out.append(escapeText((node as? org.commonmark.node.Text)?.literal ?: ""))
                }
            }
        }
    }

    /**
     * Re-emits a block child from inside inlines/blockquotes: `Text` content
     * is escaped so it cannot re-open a block, but genuine block nodes keep
     * their own structural emit (indented under a quote by the caller).
     */
    private fun emitLines(
        node: org.commonmark.node.Node,
        renderer: MarkdownRenderer,
    ): String =
        when (node) {
            // A paragraph inside a quote is INLINE content whose lines are
            // lazy quote continuations: escape every line start (a literal
            // `>` there would close the quote) and never re-emit it as a
            // paragraph (`> a` then `b` on the next line already continues
            // the same paragraph).
            is org.commonmark.node.Paragraph -> escapeLines(inlineText(node, renderer))
            is org.commonmark.node.Text,
            is org.commonmark.node.Code,
            is org.commonmark.node.HtmlInline,
            is org.commonmark.node.SoftLineBreak,
            is org.commonmark.node.HardLineBreak,
            -> escapeLines(inlineText(node, renderer))
            else -> emit(node, renderer)
        }

    /**
     * Inline markdown of one subtree (literals escaped). Used for heading and
     * table-cell content, which can only hold inlines.
     */
    private fun inlineText(
        node: org.commonmark.node.Node,
        renderer: MarkdownRenderer,
    ): String = emitChildren(node, renderer)

    private fun inlineChildren(
        node: org.commonmark.node.Node,
        out: StringBuilder,
        renderer: MarkdownRenderer,
    ) {
        var child = node.firstChild
        while (child != null) {
            inlineInto(child, out, renderer)
            child = child.next
        }
    }

    private fun org.commonmark.node.Node.children(): Sequence<org.commonmark.node.Node> =
        sequence {
            var child = firstChild
            while (child != null) {
                yield(child)
                child = child.next
            }
        }

    /**
     * `[ ] `/`[x] ` prefix for a Markwon task item, empty for every other node.
     * Matched by CLASS NAME (the JVM path must not load Markwon's Android
     * classes) with a reflective `isDone()` and a safe fallback.
     */
    private fun taskPrefixOf(item: org.commonmark.node.Node): String {
        if (item.javaClass.name != "io.noties.markwon.ext.tasklist.TaskListItem") return ""
        return when (runCatching { item.javaClass.getMethod("isDone").invoke(item) as? Boolean }.getOrNull()) {
            true -> "[x] "
            false -> "[ ] "
            null -> ""
        }
    }

    /** Prefix every line of [text] (multi-line blocks keep their structure). */
    private fun indentText(
        text: String,
        indent: String,
    ): String = text.replace("\n", "\n$indent")

    private fun tableOrFallback(
        node: org.commonmark.node.Node,
        renderer: MarkdownRenderer,
    ): String {
        if (node.javaClass.name == "org.commonmark.ext.gfm.tables.TableBlock") {
            // Real shape is TableBlock → TableHead/TableBody → TableRow →
            // TableCell (verified against commonmark-ext-gfm-tables 0.13.0),
            // so the rows are the GRANDchildren of the block.
            val rendered =
                node.children().flatMap { section -> section.children().toList() }
                    .toList()
                    .map { row ->
                        "| " +
                            row.children().joinToString(" | ") { cell ->
                                inlineText(cell, renderer).replace("|", "\\|")
                            } + " |"
                    }
            if (rendered.isEmpty()) return renderer.visibleTextOf(node)
            if (rendered.size >= 2) {
                val cols = rendered[0].count { it == '|' } - 1
                val delim = "| " + (1..cols.coerceAtLeast(1)).joinToString(" | ") { "---" } + " |"
                return (listOf(rendered[0], delim) + rendered.drop(1)).joinToString("\n")
            }
            return rendered.joinToString("\n")
        }
        // Unknown custom node: visible text (never empty when the block
        // parsed from non-blank input; separators keep their display).
        return renderer.visibleTextOf(node)
    }

    /**
     * List rebuild (R2 DEF-004): one marker per item, continuation blocks and
     * nested lists indented so they stay INSIDE their item instead of
     * flattening to top level. Blank lines between an item's own blocks are
     * content (multi-paragraph / task items), so only the following lines —
     * never the first — receive the continuation indent.
     */
    private fun listItems(
        node: org.commonmark.node.Node,
        ordered: Boolean,
        renderer: MarkdownRenderer,
    ): String {
        var n = 1
        // Ordered start number lives on the OrderedList node; keep it when
        // present so `3. a` round-trips instead of renumbering to `1.`.
        if (ordered && node is org.commonmark.node.OrderedList && node.startNumber != 1) n = node.startNumber
        val indent = if (ordered) "   " else "  "
        // ANY child is an item: Markwon's task-list plugin replaces items with
        // `TaskListItem extends CustomBlock`, so filtering to commonmark
        // `ListItem`s dropped every task item and produced an empty slice
        // (empty TextView on device — R3-03 finding DEF-009).
        return node.children().toList().joinToString("\n") { item ->
            val marker = if (ordered) "${n++}. " else "- "
            // Markwon's task item (`TaskListItem extends CustomBlock`, so not a
            // commonmark `ListItem`) carries its checkbox state on the ITEM:
            // keep the marker next to the item text (R3-03 finding DEF-009).
            val taskPrefix = taskPrefixOf(item)
            val parts = item.children().map { child -> emit(child, renderer) }
            // Only the item's own first line follows the marker; every later
            // line gets the continuation indent. The nested-list indentation
            // emit() already produced stays untouched, so the sublist keeps
            // its parent instead of flattening to top level.
            val body = escapeLines(parts.joinToString("\n"))
            val newline = body.indexOf('\n')
            val head = if (newline < 0) body else body.substring(0, newline)
            val tail = if (newline < 0) "" else body.substring(newline)
            marker + taskPrefix + head + indentText(tail, indent)
        }
    }

    // -- Text re-escaping (R2 DEF-004) ---------------------------------------
    //
    // A `Text` literal is raw content as commonmark parsed it: escapes were
    // consumed and entity references resolved. Re-emitting it verbatim can
    // turn visible characters back into syntax (`\*literal\*` showed as
    // `*literal*`, then re-parsed as emphasis). Only characters that can
    // START inline syntax are escaped; ordinary text stays byte-identical.

    /** Inline syntax starters that are never legitimate literal text. */
    private val INLINE_ESCAPES: Set<Char> =
        setOf('\\', '`', '*', '_', '~', '[', ']', '<', '&')

    private fun escapeText(text: String): String {
        if (text.isEmpty()) return text
        val out = StringBuilder(text.length)
        var lineStart = 0
        for (i in text.indices) {
            val c = text[i]
            when {
                c == '\n' -> {
                    out.append(c)
                    lineStart = i + 1
                }
                lineStart == i && needsEscapeAtLineStart(text, i) -> out.append('\\').append(c)
                c in INLINE_ESCAPES -> out.append('\\').append(c)
                else -> out.append(c)
            }
        }
        return out.toString()
    }

    /** Escape only the line starts of [text] (used for block bodies). */
    private fun escapeLines(text: String): String {
        if (text.isEmpty()) return text
        val out = StringBuilder(text.length)
        var lineStart = 0
        for (i in text.indices) {
            val c = text[i]
            when {
                c == '\n' -> {
                    out.append(c)
                    lineStart = i + 1
                }
                lineStart == i && needsEscapeAtLineStart(text, i) -> out.append('\\').append(c)
                else -> out.append(c)
            }
        }
        return out.toString()
    }

    /**
     * Line-start chars that would open a block: `#` heading, `>` quote,
     * `-`/`+`/`*` bullet (and `-`/`=` setext underline), digit+`.`/`)`
     * ordered item. Mid-line occurrences are not syntax and stay untouched.
     */
    private fun needsEscapeAtLineStart(
        text: String,
        start: Int,
    ): Boolean {
        val c = text[start]
        if (c == '#' || c == '>') return true
        if (c == '-' || c == '+' || c == '*') return atLineStartDelimiter(text, start + 1)
        if (c.isDigit()) {
            var i = start
            while (i < text.length && text[i].isDigit()) i++
            return (i < text.length && (text[i] == '.' || text[i] == ')')) &&
                atLineStartDelimiter(text, i + 1)
        }
        return false
    }

    /** First non-space character; a delimiter qualifies when only spaces lead to it. */
    private fun atLineStartDelimiter(
        text: String,
        from: Int,
    ): Boolean {
        var i = from
        while (i < text.length && text[i] == ' ') i++
        return i >= text.length || text[i] == '\n' || text[i] == '\t'
    }
}

/**
 * T10 seam: records link taps in-process instead of firing an Intent.
 * The default `LinkResolverDef` would silently ignore unhandled links;
 * this resolver keeps every tap observable for the T10 library-link/image
 * work and performs zero navigation today.
 */
internal class InterceptingLinkResolver : io.noties.markwon.LinkResolver {
    val intercepted = ArrayList<String>()

    override fun resolve(
        view: android.view.View,
        link: String,
    ) {
        synchronized(intercepted) { intercepted.add(link) }
        // Intentionally no navigation: T10 implements library resolution.
    }
}

internal class LinkInterceptPlugin : io.noties.markwon.AbstractMarkwonPlugin() {
    val resolver = InterceptingLinkResolver()

    override fun configureConfiguration(builder: MarkwonConfiguration.Builder) {
        builder.linkResolver(resolver)
    }
}

/**
 * Applies Markwon's task-list post-processing to a JVM-built parser without
 * Android classes: `[ ]`/`[x]` list items gain a marker node
 * ([TaskMarker]), which [MarkdownRenderer.kindOf] classifies as
 * [BlockKind.LIST]. `io.noties.markwon` classes stay unloaded here.
 */
internal object TaskListPostProcessorShim {
    fun install(builder: Parser.Builder) {
        builder.postProcessor { root ->
            root.accept(TaskListVisitor())
            root
        }
    }

    private class TaskListVisitor : org.commonmark.node.AbstractVisitor() {
        override fun visit(listItem: ListItem) {
            visitChildren(listItem)
            val paragraph = listItem.firstChild as? Paragraph ?: return
            val text = paragraph.firstChild as? Text ?: return
            val match =
                Regex("""^\[([xX ])]\s+(.*)""", RegexOption.DOT_MATCHES_ALL)
                    .matchEntire(text.literal) ?: return
            val done = match.groupValues[1].equals("x", ignoreCase = true)
            text.setLiteral(match.groupValues[2])
            listItem.prependChild(TaskMarker(done))
        }
    }

    /** JVM-only stand-in for Markwon's Android `TaskListItem` node. */
    class TaskMarker(val isDone: Boolean) : org.commonmark.node.CustomBlock()
}
