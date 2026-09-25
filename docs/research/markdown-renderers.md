# Markdown Renderer Research (T00)

English | [中文](markdown-renderers.zh.md)

- Date: 2026-09-17
- Scope: `docs/research/markdown-renderers.md` only
- Constraint: read-only research; do not copy reference code. Record URL, access date, tag/commit, license, reusable ideas, and rejection rationale. Version claims come from Maven Central, official docs, or repository snapshots; label anything uncertain as unverified.

## 1. Sources and Version Snapshots (all accessed 2026-09-17)

| ID | Source | Version / tag / commit | License (repository LICENSE checked) |
| --- | --- | --- | --- |
| S1 | https://github.com/noties/Markwon | Latest release `v4.6.2`; Maven Central `io.noties.markwon:core:4.6.2`, last updated 2021-02-08; visible master head 2021-03-15, “sample, add copy code block” | Apache License 2.0 (`/LICENSE` checked in full) |
| S2 | https://github.com/noties/Markwon/blob/master/build.gradle | `commonMarkVersion='0.13.0'`; min SDK 16; CommonMark/GFM strike/table transitives all 0.13.0 | Build facts |
| S3 | https://github.com/noties/Markwon/blob/master/CHANGELOG.md | 4.6.2 adds `DefaultDownScalingMediaDecoder`; 4.6.0 changes tasklist to GFM postprocessing and adds `TableAwareMovementMethod`; 4.2.0 ties CommonMark 0.13.0 to spec 0.29 | — |
| S4 | https://noties.github.io/Markwon/docs/v4/install.html | Official artifact list: core/editor/latex/strikethrough/tables/tasklist/html/image/image-coil/image-glide/image-picasso/inline-parser/linkify/recycler/recycler-table/simple-ext/syntax-highlight; artifacts share version | — |
| S5 | https://noties.io/Markwon/docs/v4/ext-tables/ | Default table fills TextView width, uses equal-width columns, cannot copy cell content, and needs `TableAwareMovementMethod` for links; full table path uses `recycler-table` / `TableLayout`, optionally wrapped by `HorizontalScrollView` | — |
| S6 | https://noties.io/Markwon/docs/v4/ext-tasklist/ | `TaskListPlugin.create(context/color/drawable)`; toggling is visual only and persistence is app-owned; docs show ClickableSpan example | — |
| S7 | https://noties.io/Markwon/docs/v4/recycler/ | `MarkwonAdapter`: one entry per block, `SimpleEntry` parse cache, custom horizontal-scroll entry for `FencedCodeBlock` | — |
| S8 | https://noties.io/Markwon/docs/v4/recycler-table/ | `TableEntry` + `TableEntryPlugin` maps TableBlock to native TableLayout; do not mix with `TablePlugin` | — |
| S9 | https://noties.io/Markwon/docs/v4/image/ | `ImagesPlugin`: scheme handlers (File/DataUri/Network/OkHttp/custom) + decoders (GIF/SVG/Default); asset handler uses `file:///android_asset/` | — |
| S10 | https://noties.io/Markwon/docs/v4/core/configuration.html | Default `LinkResolverDef` starts Activity and silently ignores missing handler; may intercept in-library links. Relative-to-absolute/assets image processors only handle URLs without schemes. `ImageSizeResolverDef` supports %/em/px and fits canvas width; height does not support %. | — |
| S11 | https://github.com/markedjs/marked (`package.json`, `LICENSE`) | Master snapshot 18.0.13; official docs warn output HTML is **not sanitized by default**; pair with DOMPurify etc. | MIT |
| S12 | https://github.com/mikepenz/multiplatform-markdown-renderer (release feed + version catalog) | Latest `v0.45.0` (2026-08-28), parser `org.jetbrains:markdown:0.7.9`; M2/M3 modules, async `rememberMarkdownState`, GFM table/task/alerts | Apache-2.0 (LICENSE also contains Erik Hellman MIT portion) |
| S13 | https://github.com/jeziellago/compose-markdown (`markdowntext/build.gradle`, release feed) | Latest 0.7.2 (2026-04-27); depends on Markwon 4.6.2 core/strike/tables/html/linkify/tasklist, a Markwon fork (`58aa5aba6a`), Coil3; min SDK 21; JitPack | MIT |
| S14 | https://github.com/commonmark/commonmark-java (`README.md`, `LICENSE.txt`) | Latest core 0.30.0 under `org.commonmark`; legacy `com.atlassian.commonmark` ends at 0.17.x. Android min API 19 best-effort; Parser/Renderer are reusable across threads. | BSD 2-Clause style (Robin Stocker LICENSE.txt) |

## 2. Options

### A — Markwon (native Spannable, no WebView): preferred

Android-native Markdown. CommonMark parses to a Node, then renders to system Spannable usable by TextView/Button/Toast. Candidate modules: `core`, `ext-tables` (`TablePlugin`), `ext-tasklist`, `recycler` (`MarkwonAdapter`), `recycler-table` (`TableEntry`), and optionally `image` only for custom scheme/decoder extension points; do not include Glide/Picasso/Coil modules.

`parse(String) → Node → render(Node) → Spanned` allows parse/render in a background thread, with `setParsedMarkdown` on main. A project-owned splitter feeds top-level blocks to `MarkwonAdapter`, one RecyclerView item per block; wide tables/code use independent `HorizontalScrollView` entries. Split oversized blocks into readable plain-text items with a notice.

Evidence against acceptance dimensions:

- Native: yes, Spannable and no WebView (S1).
- Block/recycler support: official recycler artifact provides entries and `SimpleEntry` cache (S7).
- Large documents: default full-document TextView and giant block can cost main-thread measure/layout. Use project splitting, giant-block plain-text fallback, and cancellable background parsing. Do not promise complete rich text at 10 MB (T06 gate).
- Tables: default limitations are documented: full width, equal columns, no copy; links require `TableAwareMovementMethod` (S5). M1 uses NORMAL + horizontal scroll for narrow screens; `TableEntry` is an evolution path and must not be mixed with `TablePlugin` (S8).
- Task lists: GFM plugin; checkbox toggle is visual only and persistence is app-owned (S6). This matches a read-only reader.
- Relative resources: override `LinkResolver` for in-library `.md/.txt` + fragment (default silently ignores missing handlers). Map relative images through `ImageDestinationProcessor` + custom `SchemeHandler` to SAF content URIs and reject tree escape. No GIF/SVG decoder in MVP.
- E-Ink fit: high—no WebView/animation dependency; colors follow TextView textColor; Reader/MotionPolicy owns page/scroll behavior.
- Maintenance: factual stagnation—no release after 4.6.2 (2021-02-08), visible master head from 2021-03. Whether GitHub labels it archived is unverified. Mitigate under R1 in §5.
- License Apache-2.0. APK-size impact unverified; use minimal artifact set without image network library and measure in T13.

Reusable ideas: MarkwonAdapter block entries/`SimpleEntry` cache, TableEntry mapping, relative-resource processor pattern, and `TableAwareMovementMethod`. Exclude image-glide/image-coil/image-picasso and renderer-side okhttp; choose TablePlugin or TableEntryPlugin, never both.

### B — WebView + marked.js: rejected

A local HTML template and marked UMD package inject Markdown through `evaluateJavascript` and render a full HTML page. marked 18.0.13 is MIT. Tables/task lists work through HTML/CSS, but SAF images need a JS bridge/custom scheme; relative links need `shouldOverrideUrlLoading`; search must map source offsets into DOM.

Reasons: WebView compositing/scroll/animation is not finely controlled by EInkController/MotionPolicy; ghosting/refresh policy becomes unreliable. Full-page DOM conflicts with block navigation/search mapping (REQ-0003/0008). marked does not sanitize by default and would require DOMPurify. Browser/JS/network surface conflicts with MVP's offline boundary. Reconsider only if native rendering is disproved; prefer read-only text fallback over WebView.

### C — Compose Markdown libraries: rejected

mikepenz `multiplatform-markdown-renderer` v0.45.0 (Apache-2.0) uses JetBrains Markdown AST and composables, with per-node `MarkdownComponents`, async `rememberMarkdownState`, and Coil image transformers. jeziellago 0.7.2 (MIT) wraps TextView rendering in Compose `MarkdownText`, but directly depends on Markwon 4.6.2, a private Markwon fork, Coil3, and JitPack.

Leaflet's `:core:reader` is View/RecyclerView; adding Compose runtime for rendering reverses the dependency and may require Reader rewrite without measured benefit. jeziellago adds an indirection/private fork/supply-chain risk without capability beyond direct Markwon. mikepenz image loading defaults to Coil network behavior; even if configurable, it adds work and E-Ink recomposition/refresh behavior is unverified.

Ideas only: per-node override model (map to Markwon span factories/entries), hoisted parse state, and pluggable image transformer design (MVP does not use network images).

### D — Custom parser: rejected

Hand-write CommonMark/GFM (tables/task lists/strike) plus spans, or build on `org.commonmark:0.30.0` (BSD). A custom path is Parse → custom AST → Span/Composable. CommonMark provides source spans, TextContentRenderer, and reusable thread-safe parser/renderer.

Cost is spec conformance, table/task/HTML semantics, and large-file tuning; benefit duplicates Markwon. T00/T06 prohibit a full custom parser/AST. Only reuse parser AST for block splitting, and only if Markwon `parse/render(Node)` is insufficient; that premise remains unverified for T06 spike.

## 3. Comparison

| Dimension | A Markwon | B WebView | C Compose | D Custom |
| --- | --- | --- | --- | --- |
| Native rendering | Yes, Spannable | Full-page HTML | mikepenz Compose; jeziellago TextView wrapper | Native, built from scratch |
| Recycler/blocking | Official per-block recycler/cache | None, full DOM | No equivalent; recomposes AST and adds Compose runtime | Build it |
| Large files | Split/fallback required; preferred plan includes it | Full DOM/layout concentration | Full AST/recomposition; app owns strategy | App owns all |
| Tables | Equal-width/full-width/no-copy default; TableEntry evolution | HTML/CSS full | mikepenz GFM; jeziellago via Markwon | Build it |
| Task lists | GFM, read-only sufficient | CSS/JS | Built-in | Build it |
| Local resources | Custom resolver/handler to SAF | JS bridge + URL interception | Custom transformer/interceptor | Build all |
| E-Ink | High fit | Low control | Medium-low; new runtime/recomposition | High, but cost disqualifies |
| Maintenance | Stagnant since 2021 | marked active; WebView is issue | mikepenz active; jeziellago wraps Markwon | No upstream burden, high internal burden |
| License | Apache-2.0 | MIT + sanitizer | Apache-2.0 / MIT | CommonMark BSD if reused |
| APK impact | Unverified; minimal set, measure T13 | No APK payload claimed, runtime cost | Likely largest, exact KB unverified | Smallest, highest engineering cost |

## 4. Preferred Option and Pinned Dependencies

**Choose Markwon `core + ext-tables + ext-tasklist` plus project-owned block/RecyclerView adapter. Do not build a parser or add WebView/Compose/network-image libraries.**

```kotlin
val markwonVersion = "4.6.2"
implementation("io.noties.markwon:core:$markwonVersion")
implementation("io.noties.markwon:ext-tables:$markwonVersion")
implementation("io.noties.markwon:ext-tasklist:$markwonVersion")
implementation("io.noties.markwon:recycler:$markwonVersion")
implementation("io.noties.markwon:recycler-table:$markwonVersion") // evolution path; do not mix with TablePlugin
// Accept transitives: com.atlassian.commonmark {commonmark, commonmark-ext-gfm-tables,
// commonmark-ext-gfm-strikethrough}:0.13.0; do not override manually.
// Exclude image-glide/image-coil/image-picasso, renderer-side okhttp, WebView, Compose Markdown.
```

AGP/Kotlin/min/target SDK compatibility remained unverified until T02; Markwon min SDK 16 does not determine Leaflet's min SDK. All direct use in `:core:markdown` must be behind `MarkdownRenderer` for a possible R1 swap.

Pipeline: SAF/content stream → cancellable background Markwon/CommonMark parse → project top-level block splitter (oversized block to plain text) → RecyclerView (`MarkwonAdapter`, normal TextView blocks, separate horizontal code/table entries) → custom link/image resolvers into in-tree SAF resources, rejecting escapes. Inject E-Ink policy through DisplayProfile/MotionPolicy/EInkController, not into renderer.

## 5. Risks

| ID | Risk | Impact | Mitigation |
| --- | --- | --- | --- |
| R1 | Markwon has no release after 2021 | New AGP/target compatibility is owned by Leaflet | Minimal artifacts, `MarkdownRenderer` seam, T02 dual-flavor build |
| R2 | CommonMark 0.13.0/spec 0.29 is old | Missing newer spec fixes; safe override to new `org.commonmark` unverified | Accept version coupled to Markwon internals; do not override; record license |
| R3 | One giant paragraph/code/table remains one item | Main-thread measure/layout stalls/ANR/OOM | Project split threshold + plain-text notice + cancellable parse; T03 shapes/T13 measurements, no fabricated threshold |
| R4 | Default table limitations | Poor wide-table reachability | M1 NORMAL + horizontal scrolling; TableEntry is evolution path |
| R5 | Relative SAF resources require custom bridge | Broken links or tree escape | Custom SchemeHandler/LinkResolver + grant-root check; test spaces/Chinese/`../`/missing target |
| R6 | WebView/Compose proposals may recur | Repeated architecture debate | Reuse rejection rationale in §2 and ADR-0004 |

## 6. Unverified Items

APK/AAR size (T13); block thresholds/frame-rate/memory (T03/T13); Markwon 4.6.2 build compatibility with chosen AGP/Kotlin/target (T02); BOOX Android baseline (hardware matrix); exact archived flag/open-issue count; use of new `org.commonmark` for splitting/override (T06 spike). mikepenz E-Ink ghosting was not measured because it was rejected.
