# Leaflet Completion Acceptance — 2026-09-18

English | [中文](acceptance-2026-09-18.zh.md)

## Conclusion

**The local engineering build gate passes. The M0 reading flow is only partial; M1 MVP does not pass acceptance. The next round is defect convergence and integration verification, not M2 feature work.**

T00–T13 have files or partial implementation, but implementation presence and helper unit tests do not prove user-facing completion. Rich text, local-image display, production parser threading, and scan consistency have blockers.

This review inspected code, ran existing checks, and prepared a plan. It changed no product code, tests, or dependencies. Verification was limited to checks supporting this acceptance.

## Scope and Evidence

Environment: macOS, Gradle Wrapper 8.13; only adb device is `emulator-5554` (Android 15/API 35 ARM64), with no E-Ink hardware. Git has no commits and project files are untracked, so results apply only to the reviewed workspace, not a commit.

| Check | Result | What it proves / does not prove |
| --- | --- | --- |
| `./gradlew :app:assembleFullDebug :app:assembleEinkDebug test lint ktlintCheck` | PASS; 6 s, 430 tasks, 5 executed / 425 up-to-date | Local incremental build/checks passed; not a clean environment and not every test reran |
| JVM XML | 406 variant records, 204 unique by classname/name; no failures/errors/skips | Current Gradle-valid results; repeated debug/release tests are not unique cases |
| App connected tests for both flavors | Command succeeds but no app androidTest source/report | **Not evidence of UI tests passing**; Kotlin test compile tasks are NO-SOURCE |
| Storage connected tests | PASS; device XML 15 tests, zero failures/errors/skips | Existing Room/SAF/Scanner tests pass; not all submission-stage failures or real App UI |
| Markdown large-file device tests | Not rerun; historical report/test implementation reviewed | Uses `createForJvmTests()` and explicit background dispatcher; does not validate production Reader, Markwon styles, or main-thread behavior |
| Release/APK analysis | Not rerun; historical size report from 2026-09-18 exists | Unsigned-candidate record, not a formally releasable artifact |
| E-Ink ghosting/refresh/device reading | NOT_RUN | No hardware; no experience conclusion |

Build output also showed host Gradle init repository injection, SDK XML-version, and Gradle deprecation warnings. Build succeeded, but clean-CI reproducibility is unproven. ktlint applied only to app, so “whole-project formatting passed” is not accurate.

## Assessment by Original Task

| Task | Acceptance | Notes |
| --- | --- | --- |
| T00–T01 | Documents exist; external research not revalidated here | REQ/ADR/architecture/research are present, but historical version claims were not rechecked |
| T02 | Local gate PASS; cross-environment gate not passed | Both flavors compile; repository's absolute macOS JDK path is not portable to Ubuntu CI |
| T03 | Samples/generator exist; determinism not rerun here | Historical large-file report does not replace production-page verification |
| T04–T05 | PARTIAL | SAF/database tests pass; scan atomicity and preserving position on update have gaps |
| T06 | FAIL | Production page is not wired to rich text; parser-thread and block-completeness issues remain |
| T07 | PARTIAL | Position/recent-reading implementation exists; historical fingerprint/cache rules have problems |
| T08 | PARTIAL | Page turns/TOC exist; anchor mapping has multiple implementations and text-match problems; page evidence missing |
| T09 | FAIL | Typography/code interactions exist, but tables still show source text rather than native table rendering |
| T10 | FAIL | Decoded local images render only placeholders; error recovery is incomplete |
| T11 | PARTIAL | Search helpers exist; production mapping, cancellation, and dense-match memory remain unverified |
| T12 | PARTIAL | Policy/settings exist; production-page/two-flavor behavior lacks automated proof |
| T13 | Incomplete | APK/emulator baseline exists; real UI, reproducible CI, E-Ink device, and release prerequisites are missing |

These are acceptance judgments, not automatic REQ=DONE updates. The `PLANNED` plan, README “no project,” READY requirements, and actual implementation have drifted.

## Main Findings

### F01 — P0: Native Markdown rendering is not connected to production

`ReaderScreen.kt` sends `block.text` to Compose Text for paragraphs and tables. `DocumentParser.kt` sets block display/text from source slices. Repository search found `MarkdownRenderer.renderSpanned` only in its definition and tests, not production; `ReaderHostView` is not on the App reading path. Parsing an AST is not equivalent to rendering bold/italic/task lists/tables. `architecture/reader.md` claims the Spanned upgrade is wired, which conflicts with the production path.

### F02 — P0: Production parsing uses Main scope and reading is not truly bounded

`ReaderViewModel` passes `viewModelScope` to ReaderSession; ReaderSession launches again from that scope, so an outer IO launch does not change this new coroutine's dispatcher. Only describe/readBytes use IO; decode and parse run after returning from IO. Synchronous CommonMark parsing has no dispatcher switch; `yield` does not make Main work background. The session reads the full stream into `readBytes()` before truncating to 16 MiB, so this is not bounded reading. The parse cache is an unbounded Map despite a “single-entry” comment. A historical 10 MB parse took about 599 seconds on an emulator; it does not prove the production Reader avoids ANR or that performance passed.

### F03 — P1: Block slicing and anchor mapping are unreliable

`MarkdownRenderer.kt` guesses source ranges by searching for visible text; formatted content may not occur contiguously in source. After 4,000 lines it stops the current block slice without an explicit continuation, risking loss in long multiline blocks. `DocumentParser.kt` maps each TOC entry by linear search for the first block containing its title, so duplicate headings map to the first and work can be O(H²). `TocNavigation` adds a separate occurrence-aware compatibility path but still uses source `contains`; it does not fix the underlying mapping. Do not add a third mapping workaround.

### F04 — P1: Local image is decoded but not displayed

In `ReaderScreen.kt`, `DeviceImage.Ready` still renders `StaticImagePlaceholder`; it does not draw the bitmap. A placeholder is not completed local-image support. Verify visible pixels, update after rotation/reuse, and bitmap lifecycle.

### F05 — P1: Scan commit is non-atomic and updates may delete reading position

`LibraryScanner` commits per-row upsert/delete without one transaction. Cancellation/failure can partially commit, contradicting “zero writes; retain previous complete index.” `DocumentDao` uses `@Insert(REPLACE)` while ReadingPosition cascades from Document, risking child-row deletion on replacement. Reproduce/update with real Room; FakeDao is insufficient. Existing 15 device tests do not cover both full assertions.

### F06 — P1: Historical fingerprint and recoverable-error contracts are not connected

`ReaderWiring.kt` builds StoredPosition fingerprint from the current Document row rather than returning the fingerprint saved with ReadingPosition; stale detection after external edits loses its historical basis. `ReaderSession.kt` can hit cache when `null == null`, contrary to the rule that unknown metadata is not trusted. `DocumentSourceImpl` raises RuntimeException-based StorageException for revoked/missing files, but ReaderSession only distinguishes IOException/SecurityException; production permission loss becomes PARSE_FAILED instead of a recovery state. Still verify reauthorization of an existing Library and title/recent/position consistency after update.

### F07 — P1: Decode and search bounds are incomplete

Handwritten UTF-8 decoder lacks checks for overlong encoding, surrogates, and maximum code point; `F4 90 80 80` can create an out-of-range value and reach `Character.toChars`, violating damaged-encoding fallback. `BlockPositionMap.locateQuery` is a synchronous loop without cancellation checks and materializes every match in a List; outer cancellation can discard results but cannot stop the loop. Validate memory/result growth for dense matches and actual UI highlighting.

### F08 — P1: Build/acceptance evidence is not portable to CI

`gradle.properties` pins a machine-specific macOS JDK path; GitHub Actions uses Ubuntu and does not override it. Remote CI was not run. App has no unit/device UI test sources; a successful empty task proves none of startup, mode switching, rendering, or restore. Core does not apply ktlint; “whole-project formatting passed” is inaccurate.

### F09 — P2: Delivery documents drift from implementation

README/AGENTS/old plan still say no Android project. REQs are mostly READY/IN_PROGRESS with no acceptance evidence map. `release.md` says debug-keystore signed while configuration has no release signing and `apk-size.md` says UNSIGNED. ADR-0007 says the project is Apache-2.0, conflicting with TBD/no LICENSE. Device matrix marks PASS while saying walkthrough pending. Remove unsupported claims.

## Next Actions

Follow the [R2 stabilization plan](round-2-stabilization-plan.md). Restore real reading paths and data consistency first, then verify both flavors on pages, then update feature statuses and candidate report. This acceptance does not authorize signing, commit, release, major dependency upgrade, or Onyx integration.
