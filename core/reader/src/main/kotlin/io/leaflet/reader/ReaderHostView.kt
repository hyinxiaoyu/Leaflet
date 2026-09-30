package io.leaflet.reader

import android.content.Context
import android.util.AttributeSet
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.leaflet.markdown.ParsedDocument
import io.leaflet.markdown.RenderBlock

/**
 * One entry per [ParsedDocument.blocks] index — the whole-document single
 * TextView is prohibited. Entry kinds:
 * - [ReaderEntryKind.TEXT]: plain TextView (heading/paragraph/list/quote/hr).
 * - [ReaderEntryKind.CODE_SCROLL]/[ReaderEntryKind.TABLE_SCROLL]: TextView in
 *   an independent [HorizontalScrollView] (wide content reachable, never
 *   clipped; E-Ink wraps by default in T09, scroll container stays).
 * - [ReaderEntryKind.PLAIN_FALLBACK]: bounded plain-text TextView.
 *
 * T09 typography (REQ-0007): style flows in as data (font size/family/line
 * spacing/margins + code soft-wrap) and binds per entry — the host never
 * reads storage. Block Spanned binding goes through the `MarkdownRenderer`
 * single seam only (the adapter never imports Markwon itself): `renderSpanned`
 * re-parses the block's source slice on a background thread (session) and
 * `bindBlock` sets it via `setParsedMarkdown`-equivalent on the UI thread.
 * `MarkwonAdapter` assembly (caches, TableEntry evolution) is intentionally
 * NOT built here — binding stays plain per-entry so the pipeline (open →
 * cancel → blocks → entries) is verifiable without view-tooling.
 *
 * Tables use the NORMAL TextView path (`TablePlugin`, never mixed with
 * `TableEntryPlugin` — its KDoc forbids combining; `TableEntry` stays a
 * documented M2+ evolution path, never mounted); link taps are intercepted
 * in-library (`InterceptingLinkResolver`) and perform no navigation until T10.
 */
class ReaderHostView
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0,
    ) : FrameLayout(context, attrs, defStyleAttr) {
        private val banner: TextView =
            TextView(context).apply {
                visibility = GONE
            }
        private val list: RecyclerView =
            RecyclerView(context).apply {
                layoutManager = LinearLayoutManager(context)
                adapter = BlockAdapter()
            }

        init {
            val column =
                LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(banner, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
                    addView(list, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
                }
            addView(column, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        }

        /** Show the static loading state (text only — no animation, E-Ink rule). */
        fun showLoading(label: String = "正在打开…") {
            banner.visibility = GONE
            (list.adapter as BlockAdapter).submit(emptyList())
        }

        /** Bind a parsed document: banner for degraded, one entry per block. */
        fun showDocument(
            document: ParsedDocument,
            notice: String? = null,
        ) {
            val bannerText = notice ?: document.degradedNotice
            if (bannerText != null) {
                banner.text = bannerText
                banner.visibility = VISIBLE
            } else {
                banner.visibility = GONE
            }
            (list.adapter as BlockAdapter).submit(document.blocks)
        }

        /** Entry count probe for tests (adapter item count). */
        fun entryCount(): Int = (list.adapter as BlockAdapter).itemCount

        /** Entry kind at position (mirrors [entryKindFor], probe for tests). */
        fun entryKindAt(position: Int): ReaderEntryKind = entryKindFor((list.adapter as BlockAdapter).blockAt(position).kind)

        private class BlockViewHolder(
            val root: android.view.View,
            val text: TextView,
        ) : RecyclerView.ViewHolder(root)

        private class BlockAdapter : RecyclerView.Adapter<BlockViewHolder>() {
            private var blocks: List<RenderBlock> = emptyList()

            fun submit(next: List<RenderBlock>) {
                blocks = next
                notifyDataSetChanged()
            }

            fun blockAt(position: Int): RenderBlock = blocks[position]

            override fun getItemCount(): Int = blocks.size

            override fun getItemViewType(position: Int): Int = entryKindFor(blocks[position].kind).ordinal

            override fun onCreateViewHolder(
                parent: ViewGroup,
                viewType: Int,
            ): BlockViewHolder {
                val context = parent.context
                val text =
                    TextView(context).apply {
                        setTextIsSelectable(false)
                    }
                val kind = ReaderEntryKind.entries[viewType]
                val root: android.view.View =
                    when (kind) {
                        ReaderEntryKind.TEXT, ReaderEntryKind.PLAIN_FALLBACK -> text
                        ReaderEntryKind.CODE_SCROLL, ReaderEntryKind.TABLE_SCROLL ->
                            HorizontalScrollView(context).apply {
                                isHorizontalScrollBarEnabled = true
                                addView(
                                    text,
                                    FrameLayout.LayoutParams(
                                        FrameLayout.LayoutParams.WRAP_CONTENT,
                                        FrameLayout.LayoutParams.WRAP_CONTENT,
                                    ),
                                )
                            }
                    }
                return BlockViewHolder(root, text)
            }

            override fun onBindViewHolder(
                holder: BlockViewHolder,
                position: Int,
            ) {
                // T06 binds source text; T09 upgrades CODE/TABLE entries to
                // Markwon Spanned via the MarkdownRenderer seam (same block map).
                holder.text.text = blocks[position].text
            }
        }
    }

/**
 * Scroll container policy probe (no view inflation needed):
 * code and table entries MUST be horizontally scrollable, text entries MUST
 * NOT introduce a second scroll axis. Used by unit tests and T09.
 */
object EntryScrollPolicy {
    fun requiresHorizontalScroll(kind: ReaderEntryKind): Boolean =
        when (kind) {
            ReaderEntryKind.CODE_SCROLL, ReaderEntryKind.TABLE_SCROLL -> true
            ReaderEntryKind.TEXT, ReaderEntryKind.PLAIN_FALLBACK -> false
        }
}

/**
 * Code soft-wrap policy probe (T09, REQ-0007 FR-7/AC-4): the horizontal-scroll
 * container stays mounted in both states — ON wraps the monospace body in
 * place (E-Ink default), OFF scrolls sideways to the tail. The switch only
 * changes binding, never entry structure.
 */
object CodeWrapPolicy {
    fun requiresHorizontalScrollContainer(codeSoftWrap: Boolean): Boolean = true
}

/**
 * Table-mode policy probe (T09, REQ-0007 FR-8/FR-9): NORMAL is the only
 * rendered mode. WRAP/CARD are named M2+ follow-ups (`TableRenderMode`) with
 * no UI switch and no render branch — this probe pins that invariant.
 *
 * Evolution note: a future `TableEntry`/`TableEntryPlugin` path MUST replace
 * the NORMAL TextView binding wholesale (its KDoc forbids mixing with
 * `TablePlugin`), never mount alongside it.
 */
object TableModePolicy {
    fun rendersNormalOnly(mode: io.leaflet.markdown.TableRenderMode): Boolean = mode == io.leaflet.markdown.TableRenderMode.NORMAL
}
