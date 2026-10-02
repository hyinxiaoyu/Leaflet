# Ebook Page-Turn Interaction Follow-up

English | [中文](page-turn-interaction-followup.zh.md)

Review ID: PAGE-TURN-20261001. Scope: the user's request for ebook-style left/right taps or swipes. State: IMPLEMENTED; device acceptance pending. This is a separate delivery/proof record, not confirmation of earlier acceptance reports.

## Outcome

Unconfigured readers now default to PAGE_TURN in both editions and display modes. Explicit continuous-scroll preferences remain stored and selectable. Left/right thirds (including page margins) go backward/forward; the center toggles the reading menu. A horizontal body swipe left/right goes forward/backward once on release. The body remains still throughout the drag and the turn is immediate without animation. Each turn uses the existing visible-height-minus-overlap algorithm (5% default), not a typeset pagination engine.

Wide table/code gestures, links, buttons and copy controls take priority. Short drags, vertical intent, long presses and additional pointers cannot become page turns. Reading positions, original files, dependencies and storage formats are preserved. Generic EInkController remains a system-default/NoOp seam, with no hardware partial-refresh guarantee.

## Diagnosis and work split

| Task | Evidence and resulting change | State |
| --- | --- | --- |
| PT-01 Recognition | The old reader had only a tap detector; no body-swipe recognizer. Added the tested PageGesture state machine and one release-triggered Compose detector with child consumption priority | Implemented |
| PT-02 Hit area and current settings | Tap detector excluded horizontal padding. Moved recognition to the Reader container before page padding; rememberUpdatedState supplies current callbacks/overlap/controller and turns are serialized with a Mutex | Implemented |
| PT-03 First-use behavior | Standard defaulted to continuous scroll, whose side taps were inert. ReaderSettings/ReadingMode now default to PAGE_TURN; valid explicit preferences remain intact; the bilingual first-run hint explains swipe directions | Implemented |
| PT-04 Proof | Fresh JVM contracts, dual-flavor Reader/interaction component compilation, changed resource compilation and formatting; full build and device execution explicitly separated | Scoped checks pass; device gate pending |

The existing READY REQ-0005 was updated before the gesture implementation, then its default policy before that implementation. It is now IN_PROGRESS, with FR-9/AC-8 recording the requested gesture behavior. Its dated September default evidence is preserved and explicitly superseded. Unrelated uncommitted Home, library, position, table and image work is retained.

## Gesture contract

An ordinary tap must stay inside platform touch slop and finish before the platform long-press timeout. Page zones use equal thirds of the whole Reader container width. A swipe first establishes horizontal intent beyond touch slop; its final horizontal displacement must reach `max(48 dp, 2 × touchSlop)` and dominate its vertical displacement. At most one action is emitted when the finger lifts. Child consumption, vertical intent, long-press timeout before horizontal intent or multi-touch cancels the gesture permanently. An established horizontal swipe may finish slowly without becoming a long press. Reversing a drag back near its origin does not fall through to a tap.

The same LazyListState and logical-position persistence path are retained. The body list remains non-draggable in PAGE_TURN. CONTINUOUS_SCROLL still supports vertical scrolling and center-menu taps. A valid stored continuous-scroll choice is not silently rewritten: users retaining it can select Page turn in Settings or the center reading menu. An invalid value now falls back to PAGE_TURN without rewriting storage; no migration is needed, and older builds still understand both stored mode strings.

## Actual verification

Environment: macOS ARM64; JDK 17.0.9; frozen Kotlin/Compose compiler 2.0.21, ktlint 1.0.1, build-tools 35.0.0. The standalone checker uses cached resolved dependency JARs and existing app/resource artifacts only as link dependencies. Core paging/settings and nine Reader source files are freshly compiled; it does not rebuild a complete APK.

| Check | Result |
| --- | --- |
| JVM gesture, distance, zones, arbitration, TOC and mode contracts | PASS — 29 tests, including 10 new gesture tests |
| ReaderSettings and real SettingsStore default/persistence tests | PASS — 6 + 3; 38 JVM tests total |
| Reader components and interaction suite, Full/Eink | PASS — four fresh Kotlin/Compose component compilations |
| ReaderInteractionTest production-page suite | 22 methods compiled (19 existing + 3 new); NOT_RUN on Android |
| English/Chinese strings via AAPT2 | PASS — both resource files compile; resource linking and APK assembly not claimed |
| Changed Kotlin files via ktlint | PASS — project Compose naming exemption retained |
| Full/Eink APK build via Gradle 8.13 | BLOCKED before task execution — FileLockContentionHandler fails with java.net.SocketException: Operation not permitted |
| Actual touch behavior, phone/E-Ink, rotation, ghosting, TalkBack | NOT_RUN — no rebuilt installable app or device execution in this sandbox |

The three new page methods use the real ReaderScreen/session/parser route: body stays still until release and swipe distance equals tap distance with a 2 px tolerance; both page margins accept taps; short/vertical/held body gestures do not move the reader. Existing page cases cover horizontal table/code reachability, links/copy, overlap, menu occlusion, rapid-turn boundaries and position-preserving mode switches. A short-drag fixture uses density-scaled distance so it exceeds touch slop without reaching the swipe threshold.

Machine-readable outcomes, source hashes, compiler/test logs and a self-cleaning checker are retained in [review-evidence/2026-10-01-page-turn/](review-evidence/2026-10-01-page-turn/). Test/resource/compiler/Gradle scratch outputs are created only in `/tmp/leaflet-page-turn-*` and removed by TemporaryDirectory on exit. This report and its evidence are intentional deliverables; no screenshots, installable APK, commit or release are claimed.

## Next acceptance gate

Reproduce the scoped checks with the local frozen cache:

```bash
python3 docs/plans/review-evidence/2026-10-01-page-turn/run_checks.py \
  --java /Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home/bin/java
```

When Gradle/ADB sockets are permitted, use JDK 17 and run:

```bash
./gradlew :app:assembleFullDebug :app:assembleEinkDebug
./gradlew :app:connectedFullDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=io.leaflet.reader.ui.ReaderInteractionTest
./gradlew :app:connectedEinkDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=io.leaflet.reader.ui.ReaderInteractionTest
```

Accept on a phone and a real E-Ink device only after verifying both directions, no motion before release, page-margin taps, preserved explicit continuous-scroll choices, wide table/code ownership, an open menu, the final line and repeated rapid turns. The pending device evidence prevents a DONE or release claim.
