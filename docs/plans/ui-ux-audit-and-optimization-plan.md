# Leaflet UI/UX Audit and Optimization Plan

English | [中文](ui-ux-audit-and-optimization-plan.zh.md)

Date: 2026-09-29  
Status: Proposal / Audit & Optimization Plan.  
Scope: Entire UI/UX surface across both `full` and `eink` distribution flavors, spanning Library, Browse, Reader, TOC, Search, Settings, and About screens.  
Prerequisites: M1 Alpha candidate baseline (`r3-08-delivery.md`, 295 unique JVM tests, 75 instrumented tests per flavor).  
Companion task breakdown: [UI/UX Optimization Tasks](ui-ux-optimization-tasks.md).

---

## 1. Executive Summary

Leaflet is a local-first, folder-first Android Markdown and TXT reader designed with E-Ink devices as a first-class citizen. Its core technical foundation is exceptionally solid: zero-allocation parsing pipelines, cancellable SAF tree scanners, deterministic state machines, and rigorous data-safety guarantees (original files remain strictly read-only and byte-for-byte untouched).

However, during real-device emulator walkthroughs and codebase audits of the current M1 Alpha candidate, the user interface (UI) and user experience (UX) exhibit significant usability gaps. The interface currently behaves as an early functional prototype rather than a cohesive reading product. Critical regressions and polish gaps were identified, including:

1. **System Insets Clipping**: Status bar and clock icons overlap directly onto screen titles (`enableEdgeToEdge()` without inset padding).
2. **Layout Sizing & Scroll Clipping Bugs**: Unweighted `LazyColumn` inside unscrollable `Column` containers leads to clipped recent files, settings, and navigation buttons.
3. **Table Formatting Breakdown**: Markdown table columns render independently per row without unified column widths, causing severe cell misalignment and illegible text smashing (`NameTypeNotes`).
4. **Distorted Image Scaling**: Small icons (e.g., 16×16 px) are stretched across the full viewport width into giant 320 dp pixelated blocks due to `fillMaxWidth()` constraints.
5. **E-Ink Contrast & Shading Defects**: Search highlights rely on saturated `#FFFF00` (yellow), which washes out to near-white on 16-level grayscale E-Ink screens; unstyled Material 3 tokens introduce muddy lavender/purple tones into the monochrome theme.
6. **Reader Viewport Instability**: Search bars, headers, and bottom toolbars are placed in-flow rather than in overlays, forcing LazyColumn resize, content reflow, and full-screen flashing/ghosting on E-Ink displays.
7. **Disruptive Link Extraction**: In-text Markdown links are extracted and rendered as separate full-width buttons stacked below the paragraph rather than inline clickable spans.
8. **Localization & String Hardcoding**: User-visible strings are a fragmented mix of English and Chinese in `strings.xml`, while dozens of critical error messages, status labels, and relative timestamps are hardcoded directly in Kotlin code.

This document records the complete evidence-backed audit findings, defines target UX design specifications, and sets up a phased roadmap for resolution.

---

## 2. Invariants and Design Constraints

All UI/UX optimizations must strictly adhere to Leaflet's core architectural invariants:

1. **Original File Immutability**: All original Markdown/TXT files on storage are read-only. UI changes must never add destructive file actions or write-back mutations.
2. **E-Ink First-Class Rules**:
   - Zero animations, crossfades, overscroll glows, or smooth scrolls. All transitions must be instantaneous.
   - High-contrast pure black (`#000000`) and pure white (`#FFFFFF`), with deliberate 2-level or 4-level discrete grays (`#EEEEEE`, `#777777`) for structural boundaries.
   - Minimal screen redraws: avoid in-flow layout resizing that forces content relayout and E-Ink ghosting.
   - Static feedback: loading states, copy confirmations, and notices must use static indicators without recomposition loops or ticking timers.
3. **Touch Targets & Ergonomics**: Minimum touch target of 48×48 dp for interactive elements on touchscreens.
4. **Offline & Privacy**: No network requests, remote font downloads, webview embeds, or third-party telemetry.
5. **Durable Position Preservation**: UI reflows or mode switches must preserve the user's reading position across configuration changes and process recreations.

---

## 3. Comprehensive Finding Inventory

The audit evaluated the entire user journey and categorized 32 distinct findings across 8 core functional modules, classified by severity:
- **P1 (Blocker / High)**: Direct visual corruption, content illegibility, clipping/unreachability, or severe contrast failure.
- **P2 (Medium / Usability)**: Flow friction, layout instability, missing affordance, or misleading indicators.
- **P3 (Polish / Low)**: Aesthetic inconsistency, spacing irregularity, or missing secondary metadata.

```
+---------------------------------------------------------------------------------------+
|                               LEAFLET UI/UX MODULE AUDIT                              |
+--------------------+--------------------+--------------------+------------------------+
| 01. Platform/Insets| 02. Theming/E-Ink  | 03. Library/Recent | 04. Browse/Directory   |
| [P1] Inset overlap | [P1] Yellow wash   | [P1] Layout clip   | [P2] Breadcrumb missing|
| [P2] Intent filter | [P2] Incomplete M3 | [P2] No % progress | [P2] 24dp touch target |
+--------------------+--------------------+--------------------+------------------------+
| 05. Reader/Viewport| 06. Content Render | 07. TOC/Search     | 08. Localization       |
| [P1] In-flow reflow| [P1] Table align   | [P2] TOC replaces  | [P1] Hardcoded strings |
| [P2] Tap zone hint | [P1] Image blowup  | [P2] Search layout | [P1] Mixed strings.xml |
| [P2] Stacked links | [P2] Copy state    | [P3] Case toggle   | [P2] Missing values-zh |
+--------------------+--------------------+--------------------+------------------------+
```

### Module 1: Platform Integration & Window Insets

| ID | Sev | Screen / Component | Issue Description | Root Cause & Runtime Evidence |
|---|---|---|---|---|
| ISS-001 | P1 | All screens (`MainActivity`) | Status bar clock, battery, and notification icons directly overlap screen headlines (`Library`, `Settings`, `About`, `Reader`). | `MainActivity.kt:50` invokes `enableEdgeToEdge()`, but root layouts (`Column(modifier = Modifier.padding(24.dp))`) omit `Modifier.statusBarsPadding()`, `WindowInsets.safeDrawing`, or standard `Scaffold`. Confirmed on Android 15 emulator (`/tmp/screen_library.png`, `/tmp/screen_settings.png`). |
| ISS-002 | P1 | Reader & Library Navigation | The Android system navigation bar gesture pill overlaps bottom controls and progress text (`1 / 9 · 0%`). | Root layouts lack `Modifier.navigationBarsPadding()`. Content renders behind the 3-button or gesture bar. |
| ISS-003 | P2 | App Manifest (`MainActivity`) | Leaflet cannot be selected from "Open with..." when tapping `.md` or `.txt` in file managers. | `AndroidManifest.xml` registers only `ACTION_MAIN` / `CATEGORY_LAUNCHER`; missing `ACTION_VIEW` intent-filter with `text/markdown`, `text/plain`, and file/content schemes. |

### Module 2: Design System, Theming & E-Ink Contrast

| ID | Sev | Screen / Component | Issue Description | Root Cause & Runtime Evidence |
|---|---|---|---|---|
| ISS-004 | P1 | `EntryBindings.kt`, `ReaderScreen.kt` | Search match highlight (`#FFFF00` bright yellow) is almost completely invisible on E-Ink displays. | Bright yellow has ~0.89 luminance, rendering as pale white/light gray on 16-level grayscale E-Ink screens. Unselected matches wash out against white document backgrounds. Only the current match (`#000000` with white text) has sufficient contrast. |
| ISS-005 | P2 | `Theme.kt` (`LeafletTheme`) | Incomplete E-Ink color palette leaves unassigned Material 3 tokens defaulting to standard lavender/purple shades. | `LeafletTheme` overrides only `primary`, `onPrimary`, `background`, `onBackground`, `surface`, `onSurface`. Tokens like `surfaceVariant`, `outline`, and `secondary` default to M3 pastel purples, creating muddy low-contrast dithering on E-Ink screens. |
| ISS-006 | P2 | `Theme.kt` (`Standard` mode) | Standard Android mode lacks brand identity and dynamic color support. | Standard mode falls back to default unbranded purple M3 `lightColorScheme()` / `darkColorScheme()`. No tailored typography scale, reader tint, or wallpaper-derived dynamic colors. |
| ISS-007 | P2 | `DisplayModeButton` (`LeafletNav.kt`) | Selection controls display an unbalanced visual hierarchy between selected and unselected states. | Selected options render as massive solid filled `Button` with `"● $label"`, while unselected options render as flat `TextButton` with `"○ $label"`. Violates standard segmented button, chip, or radio group design patterns. |

### Module 3: Library & Recent Reading Screen

| ID | Sev | Screen / Component | Issue Description | Root Cause & Runtime Evidence |
|---|---|---|---|---|
| ISS-008 | P1 | `LibraryListScreen` | Adding multiple folders or reading entries causes bottom buttons ("Settings", "About") and recent files to be pushed off-screen and permanently unreachable. | `LibraryListScreen` wraps an unweighted `LazyColumn` inside a non-scrollable `Column(fillMaxSize)`. If `state.libraries` grows, `LazyColumn` expands and pushes subsequent elements past the viewport fold. |
| ISS-009 | P1 | `RecentSection.kt` | Recent documents list uses a standard `for` loop inside a non-lazy `Column`, risking layout overflow and frame drops. | `RecentSection` iterates `for (row in rows)` directly inside a vertical Column. If the recent database contains 20+ items, the list overflows the screen without scrolling capability. |
| ISS-010 | P2 | `LibraryListScreen`, `RecentSection` | Interactive items lack visual affordances, category icons, or touch feedback on E-Ink. | Library rows have no folder icons or chevron indicators. With ripple disabled on E-Ink (`LocalIndication provides null`), long-press removal provides zero feedback until the alert dialog suddenly mounts. |
| ISS-011 | P2 | `RecentSection.kt` | Recent reading rows do not display reading progress percentage or chapter title. | Rows only display `titleSnapshot`, `libraryName`, and `relativeTime`. Reading position percentage (`ReadingPosition.progressPercent`) is available in the database but omitted from the UI. |
| ISS-012 | P2 | `RecentSection.kt` | Missing/dead file warnings display cluttered, redundant explanatory paragraphs per item. | Dead entries display a multi-line explanation `stringResource(R.string.recent_gone_hint)` plus a full button per row, overwhelming the screen when multiple files are moved. |
| ISS-013 | P3 | `LibraryListScreen` | Empty state is visually stark with zero guidance. | Empty library shows only a single text string `"No libraries yet. Add a folder to start reading."` and a button, with 80% blank white space. |

### Module 4: Folder Browsing & Directory Management

| ID | Sev | Screen / Component | Issue Description | Root Cause & Runtime Evidence |
|---|---|---|---|---|
| ISS-014 | P2 | `LibraryBrowseScreen` | Deep directory navigation lacks breadcrumbs; returning to ancestor folders requires repetitive hardware Back presses. | Subdirectories display only raw string `state.prefix` (e.g., `books/science/physics`). Tapping ancestor segments is unsupported. |
| ISS-015 | P2 | `LibraryBrowseScreen` | Starting or finishing a scan causes the file list to jitter vertically. | Progress text and action buttons ("Refresh", "Cancel", "Retry") are stacked in-flow directly between the title and file list, altering content height during scan state transitions. |
| ISS-016 | P2 | `LibraryBrowseScreen` | File list rows are too tightly packed (`vertical = 4.dp`), violating touch ergonomics. | File rows measure ~28–32 dp in total height, significantly below the 48 dp Android accessibility guideline, causing frequent mis-taps on handheld e-readers. |
| ISS-017 | P3 | `LibraryBrowseScreen` | Directories and files are distinguished solely by a plain trailing slash (`/`). | No folder or document type icons (e.g., Markdown `.md` vs plain text `.txt`). Rows are plain text strings. |

### Module 5: Reader Immersive Experience & Viewport Stability

| ID | Sev | Screen / Component | Issue Description | Root Cause & Runtime Evidence |
|---|---|---|---|---|
| ISS-018 | P1 | `ReaderScreen.kt` | Toggling reading controls or search bar causes severe content reflow and E-Ink screen flashing. | Title text, degraded notices, search bar, and bottom menu are all in-flow children of the root `Column`. Toggling `menuVisible` or `searchOpen` shrinks the `Box(weight = 1f)` containing `LazyColumn`, forcing complete relayout of text blocks. On E-Ink, this triggers full-screen waveform flashes. |
| ISS-019 | P2 | `ReaderScreen.kt` | Document title is permanently visible at the top, wasting valuable reading space. | `Text(state.document.title)` sits outside the `LazyColumn` and remains anchored permanently, consuming ~64 dp of reading viewport even on page 200 of a book. |
| ISS-020 | P2 | `ReaderScreen.kt` | Tap zones (Prev / Menu / Next) have zero visual discoverability for new users. | Left 33%, Center 34%, and Right 33% tap zones are completely invisible. In `CONTINUOUS_SCROLL` mode, left/right taps are silent no-ops, confusing users expecting page turns. |
| ISS-021 | P2 | `ParagraphEntry` (`ReaderScreen.kt`) | Markdown links are stripped from text and appended as giant stacked buttons below paragraphs. | `ParagraphEntry` iterates `for (link in block.links)` and renders them as separate full-width `TextButton` items. A paragraph with three links results in three full-width buttons below the text, breaking typographical flow and paragraph structure. |
| ISS-022 | P3 | `ReaderScreen.kt` | Bottom reading progress (`1 / 56 · 2%`) floats loosely without container styling. | Plain text placed at the bottom without padding, separator line, or standard reading status bar layout. |

### Module 6: Markdown Typography, Tables, Images & Code

| ID | Sev | Screen / Component | Issue Description | Root Cause & Runtime Evidence |
|---|---|---|---|---|
| ISS-023 | P1 | `TableBlockEntry` (`ReaderScreen.kt`) | Markdown table columns are completely misaligned across rows; cells smash together into unreadable strings. | Each row in `TableBlockEntry` is rendered as an independent Compose `Row`. Column widths are computed locally per row rather than globally across the table. In `r207-eink-table.png`, header cells merge into `NameTypeNotes` without visible column boundaries. |
| ISS-024 | P1 | `ImageEntry` (`ReaderScreen.kt`) | Small icons and thumbnail images are stretched to full screen width into giant pixelated blocks. | `ImageEntry` applies `Modifier.fillMaxWidth().heightIn(max = 320.dp)` unconditionally. As verified in `r207-img3.png`, a 16×16 px leaf icon is scaled to 1080 px wide, dominating the entire screen. |
| ISS-025 | P2 | `CodeBlockEntry` (`ReaderScreen.kt`) | "Code copied" feedback text persists indefinitely after tapping copy. | `var copied by remember { mutableStateOf(false) }` is set to `true` upon tap but never reset by a coroutine timer or dismissed, remaining on screen permanently. |
| ISS-026 | P2 | `TableBlockEntry` (`ReaderScreen.kt`) | Tables lack borders, dividers, or alternating row shading. | Cells float in empty space without grid borders or header dividers, making wide tabular data difficult to parse. |

### Module 7: TOC Drawer, Search & Navigation Ergonomics

| ID | Sev | Screen / Component | Issue Description | Root Cause & Runtime Evidence |
|---|---|---|---|---|
| ISS-027 | P2 | `TocDrawer` (`ReaderScreen.kt`) | Opening the table of contents completely replaces the reading screen rather than sliding over as a modal drawer. | `if (tocOpen) { TocDrawer(...) return }` unmounts `ReaderContent`. When closed, the reader re-renders. Furthermore, the 280 dp sidebar has an unstyled background and lacks elevation or border. |
| ISS-028 | P2 | `TocDrawer` (`ReaderScreen.kt`) | TOC entries lack heading level indicators (H1–H6 tags) and chapter position metadata. | Hierarchy is conveyed solely via `(12 * indent).dp` padding. Long headings wrap awkwardly, and no reading progress or block numbers are shown. |
| ISS-029 | P2 | `SearchBar` (`ReaderScreen.kt`) | Search interface occupies two full rows and causes severe keyboard occlusion. | Row 1 contains input and Close; Row 2 contains match counter and Prev/Next buttons. When the IME soft keyboard opens, less than 25% of the viewport remains for document content. |
| ISS-030 | P3 | `SearchBar` (`ReaderScreen.kt`) | In-document search enforces case-sensitive matching without a toggle option. | Search query matching defaults to case-sensitive without an affordance to switch to case-insensitive mode. |

### Module 8: Internationalization & String Cleanliness

| ID | Sev | Screen / Component | Issue Description | Root Cause & Runtime Evidence |
|---|---|---|---|---|
| ISS-031 | P1 | Entire app codebase | Extensive hardcoded Chinese strings in Kotlin files bypass Android's localization engine. | Grep confirmed hardcoded Chinese strings in: `LibraryScreens.kt` (`"空闲"`, `"扫描中"`), `LibraryViewModel.kt` (`"该目录已在资料库中"`), `RecentSection.kt` (`"刚刚"`, `"$diffMin 分钟前"`), `ReaderScreen.kt` (`"图片缺失："`, `"图片：$alt（加载中）"`), and `ReaderViewModel.kt` (`"目标不存在或超出资料库范围"`). |
| ISS-032 | P1 | `res/values/strings.xml` | Default `strings.xml` is an inconsistent bilingual hybrid; no `values-zh/` exists. | `strings.xml` mixes English strings (`library_title` = "Library") with Chinese strings (`reader_search_hint` = "在文档中搜索（区分大小写）", `reader_toc_empty` = "本文档无章节标题"). There is no dedicated Chinese translation resource file. |

---

## 4. UI/UX Optimization Architecture & Design Specifications

To resolve these findings systematically without destabilizing core reading logic, the optimization follows five architectural pillars:

### 4.1 System Window Insets & Scaffolding
- Replace raw top-level `Column` layouts with `Scaffold` or apply `Modifier.windowInsetsPadding(WindowInsets.safeDrawing)`.
- Ensure all screens handle status bar insets at the top and navigation bar insets at the bottom.
- Add an `ACTION_VIEW` intent-filter to `MainActivity` to allow opening Markdown and TXT files directly from external file managers.

### 4.2 Comprehensive E-Ink Monochrome Color System
- Expand `LeafletTheme` color palette:
  - `primary = Color.Black`, `onPrimary = Color.White`
  - `surface = Color.White`, `onSurface = Color.Black`
  - `surfaceVariant = Color(0xFFF4F4F4)`, `onSurfaceVariant = Color.Black`
  - `outline = Color.Black`, `outlineVariant = Color(0xFFCCCCCC)`
- Fix search highlight for E-Ink:
  - On E-Ink mode: render unselected search matches with a crisp `#CCCCCC` (30% gray) or dashed border box, and the current match with inverted solid black background (`#000000`) and white text (`#FFFFFF`).
  - On Standard mode: use clear, high-contrast, theme-aware highlight colors.

### 4.3 Unified Component & Navigation Patterns
- Standardize all screen headers using a lightweight `LeafletTopBar`: title, navigation icon (Back/Drawer), and optional action icons.
- Replace raw `●` / `○` text buttons with standard Material 3 `SingleChoiceSegmentedButtonRow` or discrete outlined chips.
- Wrap Library and Recent Reads in a single unified `LazyColumn`:
  - Item 1: TopAppBar & Folder Actions
  - Item 2: Library Section Header & Folder Cards
  - Items 3..N: Library Rows
  - Item N+1: Recent Reads Section Header
  - Items N+2..M: Recent Read Cards
  - Item M+1: Settings & About Quick Access
- Display reading percentage badge (`progressPercent`) on recent read cards.

### 4.4 Immersive Non-Reflowing Reader Viewport
- Position reading chrome (TopBar, BottomBar, SearchBar) in an **overlay layer** (`Box(Modifier.fillMaxSize())`) floating above the reading `LazyColumn`.
- Keep the `LazyColumn` viewport dimensions constant whether the menu is visible or hidden. This completely eliminates viewport reflow and E-Ink screen flashing when menus toggle.
- Provide a subtle visual onboarding overlay showing tap zones (Previous Page, Menu, Next Page) on the user's first launch.

### 4.5 Typography & Markdown Rendering Fixes
- **Table Layout**: Calculate column maximum widths across all rows prior to composition; render cells with synchronized column widths and thin border dividers (`1.dp` solid black/gray).
- **Image Bounding**: Remove `fillMaxWidth()` on small images. Scale images to their natural dimensions, capped at viewport width and 320 dp height (`Modifier.wrapContentWidth().heightIn(max = 320.dp)`).
- **Inline Links**: Utilize Compose `AnnotatedString` with `LinkAnnotation` or clickable spans within `ParagraphEntry` to render links inline with paragraph text, eliminating awkward trailing button stacks.
- **Copy Feedback**: Automatically reset code block copy status after 2 seconds via a launched coroutine effect.

### 4.6 Complete String Resource Extraction
- Extract every user-visible string into `res/values/strings.xml` (pure English default).
- Create `res/values-zh/strings.xml` containing complete, polished Simplified Chinese translations.
- Refactor ViewModels and Screen components to reference string resource IDs or parameterized string formatters rather than hardcoded literals.

---

## 5. Phased Delivery Roadmap & Acceptance Matrix

Implementation is decomposed into 10 decoupled task packages detailed in [UI/UX Optimization Tasks](ui-ux-optimization-tasks.md):

```
Phase 1: Foundation & System Shell
  ├── UX-01: Window Insets, Edge-to-Edge Scaffolding & Intent Filters [P1]
  ├── UX-02: E-Ink High-Contrast Color Palette & Monochrome Search Spans [P1]
  └── UX-10: String Extraction, Bilingual Localization & Locale Cleanliness [P1]

Phase 2: Library & Navigation Ergonomics
  ├── UX-03: Selection Controls, Steppers & Settings Preview [P2]
  ├── UX-04: Library List Unification, Recent Reading Cards & Touch Targets [P1]
  └── UX-05: Browse Breadcrumbs, Status Stability & 48dp Touch Targets [P2]

Phase 3: Reader Polish & Content Rendering
  ├── UX-06: Immersive Overlay Reader Chrome & Stable Viewport Architecture [P1]
  ├── UX-07: Synchronized Table Grid & Natural Image Aspect Bounding [P1]
  ├── UX-08: Inline Markdown Link Rendering [P2]
  └── UX-09: Modal TOC Outline Drawer & Streamlined Search Bar [P2]
```

---

## 6. Stop Conditions & Verification Criteria

1. **Build & Test Baseline**: `./gradlew test lint ktlintCheck` must remain completely green.
2. **Dual-Flavor Compatibility**: Both `assembleFullDebug` and `assembleEinkDebug` must build with zero compiler or lint warnings.
3. **Zero Inset Overlap**: No screen headline or interactive control may overlap with the system status bar, notch, or navigation bar on standard Android devices.
4. **No E-Ink Viewport Flashing**: Toggling menus or opening search in the Reader must not resize the underlying reading `LazyColumn`.
5. **No Regressions on Core Guarantees**: SAF read-only guarantees, reading position restoration, and zero-network privacy must remain completely intact.
