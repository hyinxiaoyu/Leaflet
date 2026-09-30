# R3-08 Delivery — Software Candidate Evidence

English | [中文](r3-08-delivery.zh.md)

Date: 2026-09-28
Package: R3-08 in [Remaining Tasks for the Initial MVP](remaining-mvp-tasks.md)
Requirement: REQ-0011 (performance/release), with the AC map below
Status: Limited software candidate evidence assembled. No release: license, application ID, signing, distribution, and real-device rows are out of scope here (R3-09/R3-10).
Independent review: not performed (executor self-report).

## Source identity

Working tree of `/Users/clover/source_dev/Leaflet` at 2026-09-28 (no commit was created; the
workspace is the user's). Verified sources are the files listed in the R3-05/06/07 delivery
reports. Artifact hashes below bind the candidate binaries to this source state.

## Commands actually run

```bash
./gradlew test lint ktlintCheck :app:assembleFullDebug :app:assembleEinkDebug \
  :app:assembleFullRelease :app:assembleEinkRelease :core:storage:assembleDebugAndroidTest
./gradlew :app:connectedFullDebugAndroidTest :app:connectedEinkDebugAndroidTest \
  :core:storage:connectedDebugAndroidTest :core:markdown:connectedDebugAndroidTest
./gradlew :app:dependencies --configuration fullReleaseRuntimeClasspath
```

All three exited 0. Host: macOS (no Linux build available → clean-Linux evidence **NOT_RUN**).
Device: emulator-5554, `dev_arm64_api35` (Android 15).

## Test results

| Suite | Result |
| --- | --- |
| JVM (`test`, all variants) | 654 variant records / **296 unique** (class+name dedup), 0 failures |
| `:app:connectedFullDebugAndroidTest` | 85 tests, **0 failures**, 3 skipped |
| `:app:connectedEinkDebugAndroidTest` | 75-test suite green earlier in this round, and the 27 new interaction/resource/image-pipeline tests `27/27` — the combined 85-test Eink rerun was stopped by the operator after the Full/storage/Markdown suites had already passed, so it is not claimed |
| `:core:storage:connectedDebugAndroidTest` | 17 tests, 0 failures (includes the v1→v2 migration test) |
| `:core:markdown:connectedDebugAndroidTest` | 5 tests, 0 failures |
| `lint` / `ktlintCheck` | green (`:app` and every `:core:*` module) |

The 3 skips per flavor are `assumeTrue`-guarded two-phase persistence tests
(`ReaderProductionPersistenceTest`, `ReaderPersistenceTest`) that require their seed phase
to have run; they are skipped, not passed.

New in this round: `ReaderInteractionTest` 12, `ReaderResourceTest` 9,
`ReaderLargeDocumentPageTest` 13, `ReaderImagePipelineTest` 6 (R3-05/06/07), plus one
resolver case in `core:storage`'s JVM suite. The image pipeline test uses a debug-only
real `ContentProvider` (`app/src/debug`) that is verified absent from release manifests.

## Artifacts

| Artifact | Size (bytes) | SHA-256 |
| --- | --- | --- |
| `app-full-debug.apk` | 28,485,321 | `520dc5c3e4698c76238a71e898f252f73a8b114aea37dcfada2d1f9d4fde4109` |
| `app-eink-debug.apk` | 28,378,941 | `d73829ff29a1171d186254053cac5e5564b3e80241fddeac0e039ca9445ac706` |
| `app-full-release-unsigned.apk` | 22,618,101 | `ab51769a52a1cf473421442514dfe634eea70708463106ed3eeec93b1fb805bd` |
| `app-eink-release-unsigned.apk` | 22,617,845 | `4ce7b1d2fb3e71ca5c50d17dc9500073ef44eec74693f02d0b9862c1c13c4bd5` |

- The release APKs are **unsigned**; no signing material exists or was created.
- Merged release manifests declare no runtime permission other than the
  framework-generated `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` — in particular **no
  `INTERNET`**, which is the offline-by-default guarantee (REQ-0009/0011).
- Test-only dependency isolation: `fullReleaseRuntimeClasspath` contains **no**
  `androidx.room:room-testing`, `androidx.compose.ui:ui-test-junit4`, or
  `ui-test-manifest` entry; release size is not inflated by test frameworks.
- The debug-only image fixture provider (`app/src/debug`) is present in both **debug** dexes
  (3 references each) and absent from both **release** dexes and merged release manifests
  (0 references) — test infrastructure never ships.
- Debug APKs are ~5.8–6.1 MB larger than release; the difference is debug runtime plus the
  debug-only test manifests/provider, consistent with the existing APK records.

## Performance evidence

Recorded per shape in [r3-07-delivery.md](r3-07-delivery.md): 100 KB → 10 MB documents open
in 0.2–8.0 s to first readable content with peak PSS 79–245 MB, and every end-of-file
marker is reachable on the page. The slowest measured shape is the 5 MB many-small-block
document (82,096 blocks, 7.9 s). The older manual numbers in
`docs/performance/baseline-emulator-5554-2026-09-18.md` remain historical.

## AC → evidence map

| Area | Evidence | Verdict |
| --- | --- | --- |
| Library scan/browse/removal (REQ-0001) | LT delivery reports; storage device suite; JVM scanner/repository tests | PASS (software) |
| Markdown/TXT rendering incl. tables/code (REQ-0002/0007) | `ReaderSyntaxPageTest`, `ReaderEntryTest`, R3-05 interaction suite | PASS (software) |
| Position/settings/mode persistence (REQ-0003/0004/0010) | `ReaderPersistenceTest`, `ReaderProductionPersistenceTest` (2-phase, 3 skipped without seed), `ZzActivityRecreationTest` | PASS (software, with the skips noted) |
| Page turns/TOC/gestures (REQ-0005/0006) | `ReaderInteractionTest` 12/12 + `ReaderDisplayTest` | PASS (software); E-Ink hardware NOT_RUN |
| Links/resources/system boundaries (REQ-0009) | `ReaderResourceTest` 9/9; `ReaderImagePipelineTest` 6/6 (real provider, sampling, decode budget, cancellation); resolver JVM tests | PASS (software); image entry recycle on rotation/destruction NOT_RUN |
| Large documents/search/TOC (REQ-0008/0011) | `ReaderLargeDocumentPageTest` 13/13 with measurements, including giant code 100 KB–10 MB and the 17 MB read-cap notice | PASS (software); provider read timing NOT_RUN |
| Reproducible build / candidate package (REQ-0011) | this report: gate commands, hashes, permissions, isolation | PARTIAL: Linux build NOT_RUN, unsigned |
| Real-device E-Ink + phone matrix | none | NOT_RUN (R3-09) |
| License/ID/signing/distribution | none | BLOCKED (R3-10) |

## Status drift resolved

- `README.md`: the "Alpha candidate" paragraph and the verification paragraph now carry the
  current test counts and link the R3 delivery reports; the stale 12-page-tests/240-JVM
  numbers are marked as the R2-07 snapshot.
- `AGENTS.md`: the current-stage paragraph now states the R3-05–08 software status and that
  R3-09/R3-10 are blocked on hardware and owner decisions.
- `docs/development/testing.md`: suite counts updated (JVM 652/295; device app 75 per
  flavor with 3 assumption skips; storage 17; markdown 5).

## Residual blockers before any release

1. A real E-Ink device and an ordinary phone for the R3-09 matrix (ghosting, contrast,
   refresh, resume) — no emulator substitute.
2. Owner decisions for R3-10 (license, application ID, signing custody, distribution).
3. Open software items recorded honestly: image-entry recycle under rotation/destruction,
   provider-level read timing, and a page-level tap of a CJK/space relative link. The
   earlier open items (real-provider image pipeline, giant code above 100 KB, the >16 MiB
   read-cap path, CJK/space resolution through the real DAO) are now covered.

## Stop condition

A limited software candidate is evidenced; nothing here authorizes a release, and no
defect was closed without evidence. R3-09/R3-10 remain open with their smallest owner
resolution recorded in [r3-09-10-readiness.md](r3-09-10-readiness.md).
