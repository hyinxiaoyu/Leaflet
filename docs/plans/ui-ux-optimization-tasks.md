# UI/UX Optimization Tasks

English | [中文](ui-ux-optimization-tasks.zh.md)

Date: 2026-09-29  
Status: Work package breakdown for the UI/UX optimization phase.  
Plan: [Leaflet UI/UX Audit and Optimization Plan](ui-ux-audit-and-optimization-plan.md).  
Basis: Walkthrough evidence from emulator-5554 (Android 15), codebase audit of `app` module, and R3-08 candidate baseline.

---

## 1. Execution Order and Dependency Graph

The 10 tasks are organized into three phases. Tasks within Phase 1 have no cross-dependencies and establish the system foundation. Phase 2 refines the home screen and file navigation. Phase 3 tackles reader stability and core rendering.

| ID | Task Package | Priority | Dependencies | Deliverables & Scope |
|---|---|---|---|---|
| **UX-01** | Window Insets & System Scaffolding | P1 | None | Fix edge-to-edge status bar / navigation bar overlap across all screens; add `ACTION_VIEW` intent-filter in `AndroidManifest.xml`. |
| **UX-02** | E-Ink Color Tokens & High-Contrast Search Highlights | P1 | None | Complete monochrome palette in `Theme.kt`; replace washed-out `#FFFF00` search highlight on E-Ink with high-contrast grayscale/boxed spans. |
| **UX-03** | Selection Controls & Settings Preview Card | P2 | UX-02 | Replace `"●"` / `"○"` text buttons with standard segmented controls; add real-time typography preview card in `SettingsScreen`. |
| **UX-04** | Unified Library Screen & Recent Reads Progress | P1 | UX-01, UX-02 | Unify Library and Recent Reads into a single non-clipping `LazyColumn`; add reading percentage badges, folder/file icons, and touch affordance. |
| **UX-05** | Browse Breadcrumbs & Ergonomic Touch Targets | P2 | UX-01, UX-04 | Add interactive breadcrumb navigation; eliminate scan progress jitter; enforce 48 dp minimum touch height for file list entries. |
| **UX-06** | Non-Reflowing Reader Overlay Architecture | P1 | UX-01, UX-02 | Move reading controls (TopBar, BottomBar, SearchBar) into floating overlay layers; freeze reader `LazyColumn` dimensions to stop E-Ink redraw flashing. |
| **UX-07** | Synchronized Table Layout & Bounded Image Scaling | P1 | None | Implement global column width synchronization and borders for tables; bound natural image dimensions to prevent small icon blowup. |
| **UX-08** | In-Paragraph Inline Link Rendering | P2 | None | Render Markdown links inline using clickable text spans; remove trailing stacked button blocks below paragraphs. |
| **UX-09** | Modal TOC Outline Drawer & Compact Search Bar | P2 | UX-06 | Convert TOC into a proper modal drawer with H1–H6 hierarchy tags; streamline SearchBar into a compact single-row design to reduce keyboard occlusion. |
| **UX-10** | Comprehensive String Extraction & Dual-Locale Clean-up | P1 | None | Extract all hardcoded Chinese strings to `strings.xml`; create `values-zh/strings.xml` for complete English/Chinese locale parity. |

```
[UX-01 Insets & Scaffolding] ──┬──> [UX-04 Unified Library] ──> [UX-05 Breadcrumbs]
                               │
[UX-02 E-Ink Palette/Search] ─┼──> [UX-03 Controls/Preview]
                               │
                               └──> [UX-06 Non-Reflowing Overlay] ──> [UX-09 Modal TOC & Search]

[UX-07 Tables & Images] ─────────── (Independent rendering track)
[UX-08 Inline Links] ────────────── (Independent markdown presentation track)
[UX-10 Strings & Localization] ──── (Independent i18n resource track)
```

---

## 2. Detailed Task Cards

### UX-01 — Window Insets & System Scaffolding

- **Target**: `MainActivity.kt`, `AndroidManifest.xml`, `LeafletNav.kt`, `Theme.kt`.
- **Problem**: Calling `enableEdgeToEdge()` causes status bar clock, battery, and camera notch to overlap directly onto `Library`, `Settings`, and `Reader` titles (ISS-001, ISS-002). The app cannot be launched via "Open with..." for Markdown/TXT files (ISS-003).
- **Change**:
  1. Wrap top-level navigation routes in a standard `Scaffold` or apply `Modifier.windowInsetsPadding(WindowInsets.safeDrawing)` across all top-level screen containers.
  2. Ensure bottom navigation bars or gesture pills do not occlude bottom buttons or reading progress indicators.
  3. In `AndroidManifest.xml`, add an `<intent-filter>` to `MainActivity` supporting `ACTION_VIEW` for `text/markdown`, `text/plain`, and file/content schemes.
- **Acceptance Criteria**:
  - In Android 15 emulator, status bar icons and time never overlap with screen titles or header buttons.
  - Bottom navigation bar gesture pill never covers bottom progress text (`1 / 9 · 0%`).
  - Selecting a `.md` file in Android's Files app lists Leaflet in the "Open with..." dialog.
- **Stop Condition**: Verified on emulator across portrait and landscape orientations with zero visual clipping.

---

### UX-02 — E-Ink Color Tokens & High-Contrast Search Highlights

- **Target**: `Theme.kt`, `EntryBindings.kt`, `ReaderScreen.kt`.
- **Problem**: Search highlights use saturated yellow `#FFFF00`, which washes out to near-white on 16-level grayscale E-Ink screens (ISS-004). Unassigned Material 3 tokens in `LeafletTheme` fall back to pastel purples (ISS-005).
- **Change**:
  1. Complete the `DisplayMode.EINK` color palette in `Theme.kt`: explicitly assign `surfaceVariant = Color(0xFFF4F4F4)`, `onSurfaceVariant = Color.Black`, `outline = Color.Black`, `outlineVariant = Color(0xFFCCCCCC)`, `secondary = Color(0xFF555555)`.
  2. Update `EntryBindings.annotated` and `applyStaticSpans`:
     - In E-Ink mode: render unselected search matches with a crisp `#CCCCCC` (30% gray) background or high-contrast border; render the current match with inverted solid black background (`#000000`) and white text (`#FFFFFF`).
     - In Standard mode: use clear, theme-aware accessible contrast colors.
- **Acceptance Criteria**:
  - In E-Ink display mode, all inactive search hits are clearly visible and distinguishable from normal body text on a 16-level grayscale display.
  - Zero pastel purple or lavender tints appear in any Material 3 component under E-Ink mode.
- **Stop Condition**: Tested against `chineseFiveMatchesAcrossBlocks` and visual emulator inspection.

---

### UX-03 — Selection Controls & Settings Preview Card

- **Target**: `LeafletNav.kt` (`SettingsScreen`, `DisplayModeButton`, `StepRow`, `FontFamilyRow`).
- **Problem**: Option switches use massive filled buttons for selected states and plain text for unselected states (ISS-007). Steppers show obscure `"Step 2 of 3"` with no visual feedback. Users cannot see typography changes without leaving settings and opening a document.
- **Change**:
  1. Standardize radio and binary switches to Material 3 `SingleChoiceSegmentedButtonRow` or styled outlined chips with uniform borders.
  2. Replace bare `"−"` and `"+"` text steppers with structured stepper controls showing actual units (e.g., `"16 sp"`, `"1.4×"`, `"24 dp"`).
  3. Add a live "Typography Preview" card at the top of the typography section in `SettingsScreen` showing a sample paragraph that updates immediately when font size, family, line spacing, or margins change.
- **Acceptance Criteria**:
  - Settings screen controls have consistent visual weight between selected and unselected options.
  - The live preview card reflects size, typeface, line spacing, and margin adjustments in real time without screen exit.
- **Stop Condition**: Unit and page tests pass; settings persistence remains fully functional.

---

### UX-04 — Unified Library Screen & Recent Reads Progress

- **Target**: `LibraryScreens.kt` (`LibraryListScreen`), `RecentSection.kt`, `RecentViewModel.kt`.
- **Problem**: `LibraryListScreen` nests an unweighted `LazyColumn` inside a non-scrollable `Column`, pushing recent files and settings off-screen when the library list grows (ISS-008). `RecentSection` uses an unbounded `for` loop (ISS-009). Rows lack folder/file icons and reading progress percentages (ISS-010, ISS-011).
- **Change**:
  1. Unify `LibraryListScreen` into a single root `LazyColumn` containing:
     - Header item with title and "Add folder" action.
     - Section item for "Folders / Libraries", with folder icons and item counts.
     - Section item for "Recently Read", rendering each recent file as a structured row with document icon, title, library name, relative time, and reading progress badge (`35%`).
     - Section item for "Settings" and "About" quick navigation.
  2. Compact dead-file warnings into a clean inline chip (`"File moved [Remove]"`) rather than multi-line explanatory paragraphs.
  3. Ensure all clickable list rows have a minimum height of 48 dp.
- **Acceptance Criteria**:
  - Adding 10+ libraries or reading 20+ documents never clips the screen; the entire page scrolls smoothly.
  - Recent documents clearly display their last reading progress percentage.
  - Interactive rows meet the 48 dp minimum touch target guideline.
- **Stop Condition**: Tested with 0, 1, 5, and 20 libraries/recent entries.

---

### UX-05 — Browse Breadcrumbs & Ergonomic Touch Targets

- **Target**: `LibraryScreens.kt` (`LibraryBrowseScreen`), `LibraryViewModel.kt`.
- **Problem**: Deep folder navigation lacks breadcrumbs (ISS-014). Scan progress labels cause vertical list jitter (ISS-015). File list items have cramped 4 dp vertical padding (~28 dp height) (ISS-016). Files and directories are differentiated only by trailing slashes (ISS-017).
- **Change**:
  1. Add an interactive breadcrumb row at the top of `LibraryBrowseScreen` (e.g., `Library > Docs > Notes`), allowing users to tap any ancestor segment to navigate directly upward.
  2. Reserve a fixed-height status slot for the scan/refresh progress bar and state text, preventing list jumps when scans begin or complete.
  3. Increase list row padding to achieve a minimum touch target height of 48 dp per item.
  4. Prepend distinct icons or glyphs for directories (folder glyph/icon) versus documents (text document glyph/icon with `.md` / `.txt` tag).
- **Acceptance Criteria**:
  - Tapping an ancestor segment in the breadcrumb immediately navigates to that directory level.
  - Initiating or cancelling a library scan does not shift the vertical scroll position of the file list.
  - All file rows measure at least 48 dp in clickable height.
- **Stop Condition**: Tested against multi-level nested folders.

**Status (2026-10-01): DELIVERED — SOFTWARE VERIFIED, DEVICE PENDING.** The browsing slice is implemented as NB-00–NB-05: a validated breadcrumb, a reserved scan-status region that no longer moves the file list, and ≥48 dp rows with folder/document type cues. `test --rerun-tasks` (820 cases) and `lint` pass and both debug flavors build; the page regressions are NOT_RUN without a device, and UX-01/UX-02/UX-04 stay undelivered with a documented dependency narrowing. See [library-navigation-ux-delivery.md](library-navigation-ux-delivery.md).

---

### UX-06 — Non-Reflowing Reader Overlay Architecture

- **Target**: `ReaderScreen.kt` (`ReaderContent`, `ReaderScreen`).
- **Problem**: Placing document title, search bar, and bottom menu in-flow inside the root `Column` causes `LazyColumn` height changes whenever controls appear or disappear, triggering content reflow and severe E-Ink screen redraws/ghosting (ISS-018, ISS-019). Tap zones lack discoverability (ISS-020).
- **Change**:
  1. Restructure `ReaderContent` layout:
     - The reading `LazyColumn` occupies the full screen viewport (`Modifier.fillMaxSize()`) with fixed padding.
     - Document title is rendered inside the first block of the scrollable list (or as a subtle transient top header), never permanently taking 64 dp of reading space.
     - Reading menu and search bar render in an **overlay layer** (`Box(Modifier.fillMaxSize())`) on top of the reading content. Toggling menus changes overlay visibility without modifying the geometry of the `LazyColumn`.
  2. Add an onboarding hint overlay on first open, visually illustrating the Prev Page (left 33%), Menu (center 34%), and Next Page (right 33%) tap zones with a "Got it" dismissal.
  3. Clean up the bottom reading progress indicator into a discreet, anchored footer bar.
- **Acceptance Criteria**:
  - Toggling the menu or opening the search bar produces zero resize events or layout passes on the reading `LazyColumn`.
  - On E-Ink devices/emulators, opening the menu shows no content reflow or text jumping.
  - Tap zone onboarding appears once on first launch and dismisses cleanly.
- **Stop Condition**: Verified with layout inspector and emulator screen capture.

**Status (2026-09-30): DELIVERED, DEVICE EVIDENCE PENDING.** The list geometry is frozen and all
chrome is an overlay, page turns use the measured visible height, the title is transient, and the
first-run tap-zone hint is persisted. `test lint ktlintCheck` and both flavor debug builds pass; the
new instrumented acceptance tests compile but are NOT RUN (no device/emulator on this machine).
See [ux-06-delivery.md](ux-06-delivery.md).

---

### UX-07 — Synchronized Table Layout & Bounded Image Scaling

- **Target**: `ReaderScreen.kt` (`TableBlockEntry`, `ImageEntry`, `CodeBlockEntry`), `TableGrid.kt`, `ImageDimensions.kt`.
- **Problem**: Table rows calculate column widths independently, resulting in misaligned columns and smashed header text (ISS-023). Small images (e.g., 16×16 px) are stretched across the full screen width to 1080 px (ISS-024). Code block copy feedback text never disappears (ISS-025).
- **Change**:
  1. In `TableBlockEntry`:
     - Pre-calculate maximum column widths across all rows in the table.
     - Render cells in a synchronized grid layout where every cell in column $i$ has identical width $W_i$.
     - Add 1 dp solid border dividers between cells and a 2 dp dividing line below the header row.
  2. In `ImageEntry`:
     - Remove unconditional `fillMaxWidth()` from the bitmap.
     - Keep the full-width decode container, but give the bitmap explicit density-correct dimensions. Center images without upscaling, and proportionally cap them at viewport width and 320 dp height. Preserve tall/panoramic aspect ratios.
  3. In `CodeBlockEntry`:
     - Show feedback inside the existing copy action and reset `copied = false` 2000 ms after the latest tap. Repeated taps restart the timer; feedback never inserts a row or moves the document.
- **Acceptance Criteria**:
  - Markdown table columns align vertically across all rows, with clear dividing lines between cells.
  - Small images (e.g., 16×16 px) render at their natural size without being stretched across the viewport.
  - "Code copied" indicator automatically resets 2 seconds after the latest tap, without shifting code or following blocks.
- **Stop Condition**: Tested with `tables.md` and `relative-images.md` sample fixtures.

**Status (2026-09-30): IMPLEMENTED — SOFTWARE VERIFIED, HARDWARE PENDING.** Shared styled column widths and grid dividers, density-correct bounded centered images, and two-second copy feedback are delivered. All JVM/static gates and both debug builds pass; both flavors pass 57 page cases, with 9 follow-up cases per flavor after the concurrent TOC adjustment. The six new page cases failed against the old renderer. See [ux-07-delivery.md](ux-07-delivery.md).

---

### UX-08 — In-Paragraph Inline Link Rendering

- **Target**: `ReaderScreen.kt` (`ParagraphEntry`), `MarkdownRenderer.kt`.
- **Problem**: `ParagraphEntry` strips Markdown links from text and renders them as stacked full-width `TextButton` items below the paragraph, severely disrupting reading continuity (ISS-021).
- **Change**:
  1. Render links directly inline within the paragraph body text using Compose `AnnotatedString` with URL/link annotations or clickable text spans.
  2. Apply subtle styling (e.g., single underline in E-Ink mode; primary accent in Standard mode) to indicate interactive links.
  3. Tap gestures directly on the inline link trigger `onLinkTap` without invoking tap-zone page turns.
- **Acceptance Criteria**:
  - Links appear inline within the paragraph text; no trailing stacked buttons appear below paragraphs.
  - Tapping an inline link opens the target document or external browser without page-turn misfires.
- **Stop Condition**: Verified on documents containing multiple links in single paragraphs.

---

### UX-09 — Modal TOC Outline Drawer & Compact Search Bar

- **Target**: `ReaderScreen.kt` (`TocDrawer`, `SearchBar`).
- **Problem**: Opening the TOC completely unmounts and replaces `ReaderContent` (ISS-027). Headings lack H1–H6 level tags and progress metadata (ISS-028). The double-row search bar leaves less than 25% of viewport visible when the keyboard is open (ISS-029).
- **Change**:
  1. Refactor `TocDrawer` into a slide-over modal drawer or elevated side sheet over the reading content, with a clear surface background and scrim.
  2. Display heading level tags (e.g., `H1`, `H2`) alongside indented headings, with the current chapter highlighted.
  3. Consolidate `SearchBar` into a compact single-row header: input field, match count badge (`3/12`), Prev/Next arrows, and Close icon, maximizing remaining screen area above the soft keyboard.
  4. Add a case-sensitivity toggle button (`Aa`) to the search bar.
- **Acceptance Criteria**:
  - Opening TOC does not unmount the reader; tapping the scrim closes the drawer cleanly.
  - Search bar occupies a single compact row; document readability is maintained with the keyboard open.
- **Stop Condition**: Tested against documents with 50+ headings and long search queries.

---

### UX-10 — Comprehensive String Extraction & Dual-Locale Clean-up

- **Target**: `res/values/strings.xml`, `res/values-zh/strings.xml`, `LibraryScreens.kt`, `LibraryViewModel.kt`, `ReaderScreen.kt`, `ReaderViewModel.kt`, `RecentSection.kt`, `RecentViewModel.kt`.
- **Problem**: Hardcoded Chinese strings in Kotlin code bypass Android localization (ISS-031). Default `strings.xml` mixes English and Chinese; no `values-zh/` exists (ISS-032).
- **Change**:
  1. Move all user-visible strings out of Kotlin source files into XML resources.
  2. Clean `res/values/strings.xml` to be 100% English.
  3. Create `res/values-zh/strings.xml` providing 100% complete, polished Simplified Chinese translations.
  4. Ensure relative time formatters and status labels use localized string formatters (`%1$d minutes ago`, `%1$d 分钟前`).
- **Acceptance Criteria**:
  - Zero user-visible strings are hardcoded in Kotlin files.
  - Switching device language between English and Simplified Chinese changes all UI labels and messages seamlessly.
  - `./gradlew lint` passes with zero missing translation warnings.
- **Stop Condition**: Verified by grepping Kotlin source for CJK characters and running lint checks.

---

## 3. Verification & Acceptance Standard

Every work package must satisfy:
1. **Compilation & Analysis**: `./gradlew test lint ktlintCheck` passes without error.
2. **Dual-Flavor Build**: Both `app-full-debug.apk` and `app-eink-debug.apk` build cleanly.
3. **No Regressions**: Existing reading position persistence, SAF tree authorization, and offline security remain 100% green.
4. **Visual Proof**: Verified against actual emulator screenshots with explicit before/after evidence.
