# R3-06 Delivery — Links, Images, and System Boundaries

English | [中文](r3-06-delivery.zh.md)

Date: 2026-09-28
Package: R3-06 in [Remaining Tasks for the Initial MVP](remaining-mvp-tasks.md)
Requirement: REQ-0009 (local resources, links, external handoff)
Status: Software-verifiable criteria met on an emulator, including the real-provider image pipeline; entry-lifecycle recycle on rotation/destruction remains unmeasured.
Independent review: not performed (executor self-report).

## Changed files

| File | Change |
| --- | --- |
| `app/src/androidTest/kotlin/io/leaflet/reader/ui/ReaderResourceTest.kt` | New: 9 link/external/image acceptance tests on the real page |
| `app/src/androidTest/kotlin/io/leaflet/reader/ui/ReaderImagePipelineTest.kt` | New: 6 tests of the real `LibraryImageLoader` over a live provider |
| `app/src/debug/kotlin/io/leaflet/reader/ui/StubImageProvider.kt` + `app/src/debug/AndroidManifest.xml` | Debug-only real `ContentProvider` serving generated PNG bytes (never in release) |
| `core/storage/src/test/kotlin/io/leaflet/storage/saf/DocumentSourceImplTest.kt` | New case: CJK/space/percent-encoded/`../` resolution through the real DAO |
| `app/src/androidTest/kotlin/io/leaflet/reader/ui/PageHarness.kt` | `FakePageStore.relativeResolution` (models in-tree resolution at the storage seam) |
| `app/src/main/kotlin/io/leaflet/reader/MainActivity.kt` | Defect fix DEF-R3-06-01 (`launchExternalUrl`: resolve before launch, report a missing handler) |
| `app/src/main/kotlin/io/leaflet/reader/ui/reader/ReaderScreen.kt` | Defect fix DEF-R3-06-02 (same-document anchor jump effect keys) |

## Verification

```bash
./gradlew :app:connectedFullDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=io.leaflet.reader.ui.ReaderResourceTest,io.leaflet.reader.ui.ReaderImagePipelineTest
./gradlew :core:storage:testDebugUnitTest
```

Result: `ReaderResourceTest` `9/9 passed`, `ReaderImagePipelineTest` `6/6 passed`, exit 0
(emulator-5554, `dev_arm64_api35`); the resolver case passes in the JVM suite.

### Per-criterion result

| Criterion | Result | Evidence |
| --- | --- | --- |
| External HTTPS handoff (real tap) | PASS (page) | `externalLinkTapHandsTheHttpsUrlToTheOs`: production link button → `onOpenExternal("https://example.com/page?a=1")`, document unchanged |
| No-handler behaviour | PASS (page) | `missingExternalAppShowsTheRecoverableNotice`: the recoverable notice `没有可打开该链接的应用` renders and the document survives |
| Cross-document fragment (`other.md#anchor`) | PASS | `crossDocumentFragmentLinkOpensTheTargetAndLandsOnTheAnchor`: production `followLink` resolves the raw destination, calls `onOpenDocument(other.md)`, the pending fragment is consumed and the anchor body is on the page |
| Same-document fragment | PASS | `duplicateHeadingAnchorLandsOnTheFirstMatch`: a `#overview` jump lands at the FIRST of two duplicate headings (landing block between first and second heading) |
| Unknown anchor / missing target keeps context | PASS | `unknownAnchorShowsNoticeAndKeepsTheContext` (`本文中没有该锚点，位置保持不变`) and `outOfTreeRelativeLinkIsRejectedWithNotice` (`目标不存在或超出资料库范围`); the `ParsedDocument` instance is unchanged in both |
| Relative path with CJK characters reaches the resource layer | PASS (page) | `relativeImageWithCjkPathReachesTheLoader`: `资料/图片名.png` arrives at the loader unmangled |
| Space / `./` / in-tree `../` / percent-encoded paths | PASS (resolver) | `DocumentSourceImplTest.resolveCjkSpaceAndEncodedReferencesThroughTheRealDao` resolves `空 格.md`, `./%E7%A9%BA%20%E6%A0%BC.md`, `../顶层.md` through the real `DocumentSourceImpl` + DAO and rejects an escaping `../../顶.md`. A page-level tap of such a link is not covered |
| Local image read/sample/draw through a real provider | PASS | `ReaderImagePipelineTest`: a real provider (`StubImageProvider`, debug-only) behind the live `ContentResolver` serves a generated 3000×2000 PNG; the loader performs one bounds pass + one sampled decode, returns 375×250, and the decoded pixel matches the provider's colour |
| Oversized image stays inside the decode budget | PASS | decoded 375×250 = 375,000 B, inside `ImageSample.MAX_DECODE_BYTES` (12 MiB); peak process PSS during the load 49,455–49,644 KB |
| Missing / revoked provider stream | PASS | `missingAndRevokedStreamsFailAsMissingWithoutCrashing` (both map to `MISSING`, no crash) |
| Cancel a load (never started / mid provider open) | PASS | `cancelledLoadNeverTouchesTheProvider` (zero provider opens) and `cancellationDuringTheProviderOpenPropagates` (`CancellationException` while the provider is still handing over the stream) |
| Local image lifecycle (rotation/destruction recycle) | NOT_RUN | `ImageEntry` recycles on dispose (`ReaderScreen.kt`), but no test drives rotation/destruction with a decoded bitmap; peak memory during a load is measured |
| Remote image never loads and shows a placeholder | PASS | `remoteImageShowsStaticPlaceholderWithoutLoading`: placeholder caption on the page, zero loader calls |
| Missing local image placeholder | PASS | `missingLocalImageShowsPlaceholder` (`图片缺失`) |

## Defects found and fixed

**DEF-R3-06-01 — the "no external app" notice was unreachable in production.**

- Reproduce: `MainActivity` wrapped `startActivity(ACTION_VIEW)` in `runCatching`, which
  swallowed `ActivityNotFoundException`; the page only reports the notice when the
  launcher throws or is absent, so a device with no browser silently dropped the tap.
- Fix: `launchExternalUrl(url)` probes `resolveActivity(packageManager)` first, returns
  `false` when nothing can handle it (or the launch still fails), and the caller reports
  `reportNoExternalApp()`.
- Regression: `missingExternalAppShowsTheRecoverableNotice` drives the real page with a
  launcher that reports failure and asserts the notice + unchanged document. The
  Activity-side branch itself is code-reviewed, not instrumented (it needs a host
  Activity with a controllable `PackageManager`).

**DEF-R3-06-02 — same-document anchor links never scrolled.**

- Reproduce: `duplicateHeadingAnchorLandsOnTheFirstMatch` landed on block 1 after a
  `#overview` link (`followLink` → `notifyLinkJump` → pending jump).
- Cause: the screen consumed the pending jump in
  `LaunchedEffect(docId, state.linkNotice)`. A successful jump changes neither key (only
  a rejection sets `linkNotice`), so the effect never re-ran.
- Fix: key the effect on `state.restoreBlock` as well — `jumpToBlock` moves the restore
  address, which is the observable that changes on a jump.
- Regression: the anchor tests now assert the landing block.

## Not run / limitations

- Image entry lifecycle under rotation/destruction with a decoded bitmap: NOT_RUN (the
  entry's `onDispose` recycle path is code-reviewed only).
- A page-level tap of a CJK/space relative link: NOT_RUN (resolver-level evidence above).
- The debug-only `StubImageProvider` lives in `app/src/debug` and is verified absent from
  the merged release manifests; it is test infrastructure, not shipped behaviour.
- Eink-flavor run of the new classes: part of the R3-08 gate.

## Stop condition

Local resources and system boundaries are verified as far as the fake storage seam
allows; provider-level image work is recorded as an open item instead of being claimed.
R3-07 was started next.
