# REQ-0011 — Performance Baselines, Fixtures, and Release Verification

English | [中文](REQ-0011-performance-release.zh.md)

Status: IN_PROGRESS
Priority: P0
Milestone: M0/M1

## Problem

Unverified performance assumptions are not promises. Loading a whole large document into one TextView can cause ANR/OOM (`markdown-renderers.md` R3); “smooth” is not verifiable without a baseline; flavors can grow without size records; and refresh claims without real-device tests are not credible. M1 needs repeatable performance baselines, deterministic samples, per-flavor package records, and release gates before an Alpha candidate can be accepted.

Source: `docs/archive/init.md` §§7, 48–51, 62–63; implementation-plan T03/T13.

## User Story

As a reader who needs to open a 10 MB document on E-Ink, I want large documents to open cancellably without crashing and the app's size/startup to be measured, so that large collections remain readable and original files safe.

## Scope

- Deterministic Markdown samples at 100 KB, 1 MB, 5 MB, and 10 MB, with many-small-block and one-giant-block shapes, plus empty files, large TXT, duplicate headings, BOM, damaged encoding, multilingual/emoji, and long-line samples.
- Baselines for cold start, Library open, document open, page-turn/scroll latency, large-document peak memory, Library scan, and APK size.
- Record `fullRelease` and `einkRelease` bytes, build parameters, R8/signing state, dependency/native/font/image composition in `docs/performance/apk-size.md`.
- Real-device matrix in `docs/development/eink-test-matrix.md`: device/Android/screen/resolution/firmware/date/app version and ghosting, menu, repeated turns, long-press, Back, font, TOC, large docs, code, table, images, contrast, resume, rotation.
- Inspect merged release manifest for unapproved permissions/network capabilities.
- Prove originals remain unchanged after uninstall, database clear, or Library removal.
- Mark unavailable E-Ink device rows NOT_RUN/BLOCKED and retain release risk; never report a pass without hardware.

## Non-goals

No premature micro-optimization, rejecting all large documents, silent truncation, hard CI regression gate before repeatable thresholds exist, Onyx SDK/waveform tuning before M5, or useless dependencies added to force flavor-size differences.

## User Experience

Large-document loading may show progress, incremental rendering, or an explained fallback such as “Document is large; plain-text mode is in use.” Users can cancel. A 10 MB document is not promised full rich-text fidelity, but must avoid OOM/ANR/crash. Any degraded behavior must be disclosed and leave the app stable after cancellation.

## Functional Requirements

- **FR-1:** `testdata/markdown/` has a deterministic generator and exact size checks for all four tiers; include both shapes and keep test resources out of release APKs.
- **FR-2:** Cover the full §48 fixture inventory: simple/headings/lists/tables/code/long/unicode/Chinese/Japanese/relative links/images/malformed/huge, plus empty, large TXT, duplicate headings, BOM, damaged encoding, giant paragraph/code, nested lists.
- **FR-3:** Record cold start, Library/document open, turn/scroll latency, peak memory, and scan time; include device/system/firmware/version/sample and a repeatable template in `docs/performance/`.
- **FR-4:** For both release flavors record bytes, build parameters, R8, signing state, and dependency/native/font/image composition. Similar initial sizes are acceptable.
- **FR-5:** Explain and record package impact of any large dependency.
- **FR-6:** Test ordinary phones plus 6/7/10.3-inch E-Ink categories. Emulator may help with responsive layout, but ghosting/refresh claims require E-Ink hardware. Missing devices remain NOT_RUN/BLOCKED.
- **FR-7:** Confirm merged release manifest has no unapproved permissions (especially WRITE_SETTINGS-like) or network capability; remote images do not auto-load (REQ-0009 FR-9).
- **FR-8:** Opening 10 MB must avoid OOM/ANR/crash; loading/incremental/fallback are allowed. Cancellation releases resources.
- **FR-9:** Original Markdown/TXT bytes remain unchanged after uninstall, clearing database, or removing Library; compare before/after.
- **FR-10:** Include a delivery report with Alpha candidate. Evidence gaps prevent REQ=DONE. If signing/license/formal ID/device evidence is missing, call it an unsigned candidate, not a formal release.

## Acceptance Criteria

- **AC-1:** Run sample generator; all four exact-size checks pass. The giant-block sample is exercised by Reader fallback, and the fallback notice is asserted.
- **AC-2:** Open each tier on a test device without OOM/ANR/crash. 10 MB may degrade only with an explanation and readable content; no silent truncation.
- **AC-3:** Performance baseline records device/system/firmware/version/sample and at least three repeatable runs for measured fields. Do not invent numbers; mark unmeasured items NOT_RUN.
- **AC-4:** `docs/performance/apk-size.md` records both APKs, build parameters, R8, signing, and composition.
- **AC-5:** E-Ink matrix contains all required device and version fields and the §49 test list. Unavailable rows remain NOT_RUN.
- **AC-6:** Release manifest review records requirement basis for each permission/network capability. An unapproved addition fails the gate.
- **AC-7:** Hash sample files before/after Library removal + database clear; originals match and result is recorded.
- **AC-8:** Both release flavors build with T02-verified commands; unit tests/lint/format pass; report instrumented results by environment without substituting JVM/emulator/real-device evidence.

## Edge Cases

Damaged encoding/BOM/empty/large TXT has an explicit fallback and no false auto-detection claims (REQ-0002). Cancellation, permission loss, and missing files are recorded without treating unscanned items as deleted (REQ-0001). Missing real hardware remains NOT_RUN and retains release risk. Never compare peak memory across device models without recording them.

## E-Ink Considerations

The real-device matrix is the only source for E-Ink conclusions; emulator is not a substitute (`docs/archive/init.md` §49). Ghosting, rapid turns, contrast, rotation, and resume are required. Generic/NoOp reports only system redraw intent, not hardware waveform results.

## Standard Android Considerations

Record cold start and APK size for Standard too. Similar Full/E-Ink size is acceptable; do not pad dependencies. Note STANDARD/EINK mode for measurements involving animation.

## Performance Considerations

This REQ owns the baseline: record thresholds first, set gates later. Avoid O(N²) processing. Large-file behavior allows loading/incremental/fallback, but whole-file single TextView is prohibited. Test fixtures must not pollute APK size.

## Storage / Data Impact

Samples and baseline docs live in `testdata/` and `docs/performance/`, not release artifacts. AC-7 is a hard requirement; never default to copying originals into private storage. Performance records do not write to the user database.

## Test Plan

Self-test exact sample sizes, shape rules (block count/max bytes), and inventory. Unit/instrumented tests cover large-file “three no” (no OOM/ANR/crash), cancellation release, file-invariance hashes, and manifest. Build/analyze both release APKs. Run the full real-device matrix; missing hardware remains NOT_RUN.

## Dependencies

Requires REQ-0001/0003/0010 persistence semantics, REQ-0002 large-file fallback, T02 verified build tasks, T03 fixtures, and T13 acceptance for all MVP REQs. Risk inputs include `docs/research/onyx-epd.md` §5 and renderer risks R1–R5.

## Open Questions

Performance limits (e.g. document-open milliseconds), target BOOX Android baseline, Markwon/APK measurements, block-size/frame-rate/memory values remain unverified until T03/T13 evidence. Formal signing, project license, and application ID are owner decisions and do not block baseline work.
