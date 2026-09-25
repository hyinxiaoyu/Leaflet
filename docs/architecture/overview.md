# Leaflet Architecture Overview

English | [中文](overview.zh.md)

Status: T01-frozen architecture (valid for M0/M1; pause dependent work and update an ADR before breaking changes).
Date: 2026-09-17
Requirements source: `docs/archive/init.md` §§28–29; `docs/plans/M0-M1-implementation-plan.md` §3.
Related ADRs: ADR-0001–0007. Details: `reader.md` / `storage.md` / `eink.md`.


## English Overview

This architecture is frozen for M0/M1 (T01). Material contract changes require pausing dependent tasks and updating an ADR. The six modules are `:app` (Compose shell, navigation, ViewModels, manual DI, flavors), `:core:model` (domain IDs/value types), `:core:storage` (SAF, scanning, Room, DataStore, repositories), `:core:markdown` (native parser adapter, titles, TOC, render blocks, position mapping), `:core:reader` (block-based UI, layout, scrolling, search navigation), and `:core:eink` (profiles, motion policy, controller). Dependencies are one-way: app may use all core modules; Reader uses model/markdown/eink; storage and markdown depend on model; eink uses Android base APIs only.

Data flows UI → ViewModel → Repository → Data Source. UI renders state; ViewModels expose StateFlow and cancellable Coroutines; Repository hides Room/SAF; data sources include DocumentSource, DAOs, and DataStore. MVP uses manual DI; Hilt/Koin requires an ADR. Reader does not directly access Room, SAF, ContentResolver, or vendor SDKs, and the Markdown module cannot depend on Reader or storage.

Frozen cross-module contracts include stable app-internal DocumentId (distinct from URI), LibraryRepository operations, read-only DocumentSource streams and tree-contained resource resolution, ReaderInput and Loading/Content/Degraded/Failed states, versioned ReadingPosition (block, offset, progress, anchor), ParsedDocument (title, TOC, blocks, PositionMap, degradation metadata, literal-text marker), DisplayProfile/MotionPolicy/AppCapabilities, and vendor-neutral EInkController intent methods. Removing a Library clears local references but never original files. Room stores metadata and reading state; DataStore stores preferences and lightweight UI state. Database corruption falls back to defaults.

Flavor directories may contain only real distribution differences; shared Reader logic stays shared. Display behavior is injected through centralized policy objects, not scattered brand checks or Boolean flags. Animations must be globally disableable, and MVP uses only generic/no-op E-Ink control. The detailed interfaces and frozen fields below are the source of truth for implementers.

## 1. Module Breakdown (six modules, starting from plan §3)

| Module | Responsibility | Allowed dependencies |
| --- | --- | --- |
| `:app` | Compose shell, navigation, ViewModels, manual DI, flavor configuration; packages for library/reader/settings | All core modules |
| `:core:model` | Library, Document, ReadingPosition, small IDs/value types | Kotlin base libraries only; no Android UI |
| `:core:storage` | SAF access, scanner, Room, DataStore, Repository | model |
| `:core:markdown` | Native parser adapter, title/TOC, render content and position mapping | model + audited Markdown dependency |
| `:core:reader` | View/RecyclerView components, layout, scrolling, search positioning, interaction | model, markdown, eink |
| `:core:eink` | DisplayProfile, MotionPolicy, EInkController, generic policy | Android base APIs only; no app/storage |

Initially keep design-system code in `:app` and pass styles to Reader. Keep database/storage in one module with separate packages; do not create empty `feature:*` modules or a large DI framework (`docs/archive/init.md` §28). Dependency direction is one-way: app → core modules; Reader → markdown/eink/model; storage → model; markdown → model; eink → Android base APIs. Reader does not access Room/SAF/vendor SDK (ADR-0002/0005).

## 2. Layers and Data Flow

```text
UI → ViewModel → Repository → DataSource
```

- UI (`:app` + `:core:reader` views) renders state and has no business logic. Read policy from DisplayProfile/MotionPolicy/AppCapabilities; no scattered `if (isEink)`.
- ViewModel (`:app`) holds StateFlow UI state and coordinates cancellable loading/scanning/search coroutines; handles cancellation and stale results.
- Repository (`:core:storage`) hides Room/SAF behind LibraryRepository/document metadata APIs; exposes Flow/suspend methods.
- Data source (`:core:storage`) contains SAF `DocumentSource`, Room DAO, and DataStore. Markdown may reuse parser AST but must not invent a complete parser/AST.

Use StateFlow for UI state and Kotlin Coroutines for cancellable background parse/scan/search. Room holds relational data; DataStore holds settings (see §4). MVP uses manual DI/AppContainer; Hilt/Koin requires an ADR (`docs/archive/init.md` §29).

## 3. Minimal Cross-module Contracts (T01 frozen)

These are handoff contracts. A breaking change pauses dependent tasks first (plan §3/§61).

### Contract 1 — Document ID

A `DocumentId` is a stable app-internal value type (e.g. `DocumentId(value: String)`, UUID/serialized key), not a URI. `uri: String` and `relativePath: String` are access/display data; URI stability after rename/move is not assumed. Treat rename as metadata change in the same Library and restore through anchor/progress.

### Contract 2 — LibraryRepository

```kotlin
interface LibraryRepository {
    suspend fun addLibrary(displayName: String, rootTreeUri: String): Library
    fun observeLibraries(): Flow<List<Library>>
    suspend fun removeLibrary(libraryId: LibraryId) // clear local reference/data only
    suspend fun refreshLibrary(libraryId: LibraryId): ScanJobId // observable manual scan
    fun observeScanState(libraryId: LibraryId): Flow<ScanState> // IDLE/SCANNING/SUCCEEDED/FAILED/CANCELLED
}
```

### Contract 3 — DocumentSource (Reader's only document boundary)

```kotlin
interface DocumentSource {
    suspend fun openStream(documentId: DocumentId): InputStream // read-only; caller closes
    suspend fun resolveRelative(
        fromDocumentId: DocumentId,
        relativeRef: String
    ): ResolvedResource? // in-tree resource; allow in-tree `..`, reject escape/unknown scheme
}
```

### Contract 4 — Reader input/state

```kotlin
data class ReaderInput(val documentId: DocumentId, val displayProfile: DisplayProfileId)

sealed interface ReaderState {
    data object Loading : ReaderState // static text/progress; no infinite animation
    data class Content(val document: ParsedDocument, val position: ReadingPosition) : ReaderState
    data class Degraded(val document: ParsedDocument, val notice: DegradedNotice) : ReaderState
    data class Failed(val reason: ReaderFailure) : ReaderState // recoverable
}
```

App supplies cancellable load work. A stale result from an earlier document must never overwrite a newer one.

### Contract 5 — Versioned ReadingPosition

```kotlin
data class ReadingPosition(
    val documentId: DocumentId,
    val version: Int = 1, // contract version; future reads stay forward-compatible
    val blockIndex: Int = 0,
    val blockOffset: Int = 0,
    val progress: Float = 0f, // 0..1 visible progress
    val anchor: String? = null // heading anchor or text quote for fallback
)
```

Do not rely only on pixels. Restore priority: same-version block/offset → anchor → progress.

### Contract 6 — ParsedDocument

```kotlin
data class ParsedDocument(
    val documentId: DocumentId,
    val title: String, // frontmatter → first H1 → filename without extension
    val toc: List<TocEntry>, // hierarchy + unique anchors for duplicates
    val blocks: List<RenderBlock>, // Reader navigates by block, not one file/one TextView
    val positionMap: PositionMap, // blocks ↔ visible text ↔ source positions
    val degraded: Boolean = false,
    val degradedNotice: String? = null,
    val blockTexts: List<String> = emptyList(), // visible-text coordinates for search
    val positionOps: PositionMapOps? = null,
    val literalText: Boolean = false // TXT/fallback must not be parsed as Markdown again
)
```

`RenderBlock` carries index/kind/text/source slice/language/links/images. `literal=true` marks raw TXT chunks or `PLAIN_FALLBACK`; entries show `#`, `**`, `[label](target)` literally.

### Contract 7 — DisplayProfile / MotionPolicy / AppCapabilities

```kotlin
data class DisplayProfile(
    val animationsEnabled: Boolean,
    val smoothScrollingEnabled: Boolean,
    val rippleEnabled: Boolean,
    val highContrastEnabled: Boolean,
    val defaultBackground: Int,
    val defaultForeground: Int,
    val scrollBehavior: ScrollBehavior,
    val pageTurnBehavior: PageTurnBehavior, // viewport turn; overlap default 5%, range 3–8%
    val refreshPolicy: RefreshPolicy,
    val readerDecorations: ReaderDecorations
)
data class MotionPolicy(val animationsAllowed: Boolean)
data class AppCapabilities(
    val supportsStandardMode: Boolean,
    val supportsAnimations: Boolean,
    val supportsDynamicColor: Boolean,
    val supportsOnyxIntegration: Boolean,
    val supportsAdvancedSharing: Boolean
)
```

DisplayProfile/MotionPolicy own visual/motion/scroll/contrast/refresh behavior. AppCapabilities owns availability from flavor/runtime. Keep them distinct; no scattered Boolean logic.

### Contract 8 — EInkController

```kotlin
interface EInkController {
    fun onReaderEnter(view: View?)
    fun onReaderExit(view: View?)
    fun onPageTurn(view: View?)
    fun onScrollStart(view: View?)
    fun onScrollEnd(view: View?)
    fun requestPartialRefresh(view: View?)
    fun requestFullRefresh(view: View?) // MVP: request system redraw, not hardware GC
}
```

MVP implements useful `NoOpEInkController` / `GenericEInkController` only; no fake Onyx implementation (ADR-0005/0007).

## 4. Room / DataStore Split

- Room: Library, Document/foreign keys, versioned ReadingPosition, recent items. Keep future FTS schema extensible but do not create FTS table (archive §25).
- DataStore: user display/typography settings and lightweight UI state (last Library/menu visibility).
- Corrupt settings return empty/default values and do not prevent app startup.

## 5. Architecture Guardrails for Implementers

1. `:core:reader` must not import storage implementations (Room DAO/direct SAF/ContentResolver) or Onyx packages.
2. `:core:markdown` must not depend on Reader/storage; inject E-Ink policy by parameters.
3. Flavor source sets must not duplicate business behavior. Express real differences through DisplayProfile/MotionPolicy/AppCapabilities/EInkController (ADR-0003).
4. Any animation must be globally disableable through DisplayProfile/MotionPolicy; Reader has no brand/model special cases, use capability detection instead.

## Open Questions

- Final minSdk 29 lower-bound compatibility with Room 2.8.5 / proposed activity-compose 1.13.0 (T02 lint/sync).
- Performance thresholds are unknown until T03 samples exist; do not fabricate.
