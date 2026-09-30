# APK Size (R2-08 release baseline; supersedes the T13 §24 numbers)

English | [中文](apk-size.zh.md)

Date: 2026-09-18 (R2-08 rebuild). Builds: `:app:assembleFullRelease` / `:app:assembleEinkRelease`
(unsigned candidate builds — no production signing, per plan §8; `apksigner verify`
reports `DOES NOT VERIFY`, missing `META-INF/MANIFEST.MF`).
R8: `isMinifyEnabled=false` (`app/build.gradle.kts`), so release ≈ debug-minus-debugger.
Growth vs T13 (~+49.6KB both flavors) is R2-05–R2-07 main-source growth
(`SearchLimits`/`SearchResult`/capped UI, cell search highlight, search-bar count,
grant-failure notice, KMP writer transaction); no new shipped dependency.

| Artifact | Bytes | MD5 | applicationId | versionName |
| --- | --- | --- | --- | --- |
| `app/build/outputs/apk/full/release/app-full-release-unsigned.apk` | 22566877 | c442f1521330d1fd9e525e6b7e2e4543 | `io.leaflet.reader` | 0.1.0 |
| `app/build/outputs/apk/eink/release/app-eink-release-unsigned.apk` | 22566617 | b8d0ce3d8f9de1f9824d4982863b872f | `io.leaflet.reader.eink` | 0.1.0-eink |
| Delta (full − eink) | 260 | — | — | — |

Debug (reference): fullDebug 28376921 / einkDebug 28376869 (delta 52).

## R3-08 rebuild (2026-09-28)

The R3-05/06 defect fixes (page-turn overlap key, same-document anchor jump, external
handler report) and the R3 test suites were rebuilt between the R2-08 baseline above and
this record. Release size is unchanged in practice (~+1.2 KB per flavor); no shipped
dependency was added (test-only `room-testing` stays out of `fullReleaseRuntimeClasspath`).

| Artifact | Bytes | MD5 | applicationId | versionName |
| --- | --- | --- | --- | --- |
| `app/build/outputs/apk/full/release/app-full-release-unsigned.apk` | 22618101 | a860df58f270b6b3ce28e01bae302bee | `io.leaflet.reader` | 0.1.0 |
| `app/build/outputs/apk/eink/release/app-eink-release-unsigned.apk` | 22617845 | e3feeeb605ec18b59f30161ed1b6582c | `io.leaflet.reader.eink` | 0.1.0-eink |
| Delta (full − eink) | 256 | — | — | — |

Debug (reference): fullDebug 28485321 (586a6f3b5a012d69e47c85dfe2048b19) / einkDebug
28378941 (0e023c47e869de7c386c53f187fd59cf). The Full debug APK grew because the R3-06
image test adds a debug-only fixture provider; that provider is verified absent from both
release dexes and merged release manifests. SHA-256 values and the permission /
test-resource-isolation checks are recorded in [r3-08-delivery.md](../plans/r3-08-delivery.md).

## Composition (fullRelease, zip entry scan)

- dex 21869260 B (20.86MB; `classes.dex` 13552164 + `classes2.dex` 8317096) ≈ 96.9% of APK.
- `resources.arsc` 451176 B (~441KB); native `.so` 73584 B total (8 files, ×4 ABIs:
  `libandroidx.graphics.path` + `libdatastore_shared_counter`); `res/` 42049 B
  (85 files, launcher icons only); `assets/dexopt/baseline.prof` 7557 +
  `baseline.profm` 438 B; no bundled fonts/content images.
- Shipped license owners in APK: `androidx` only (zip entry scan).
- Zero testdata / test code in APK: no `sourceSets`/`assets.srcDirs`/`res.srcDirs`
  override (default `app/src` packaging only, verified by grep) and zero zip entries
  matching `testdata|androidTest|Test.class` (checked 2026-09-18).
- Permissions (aapt dump badging, both flavors): zero user permissions.
  Only auto-added `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` (signature
  protectionLevel, from androidx). Components: `MainActivity` (exported,
  launchable) + androidx receivers/services. Remote images: static placeholder,
  zero loader calls (T10 design; page test asserts no `http` loader call).

## Notes

- The 260-byte delta is expected: the two flavors share ~100% of code
  (ADR-0003); only the app name, IDs, and `AppCapabilities` differ.
  Do NOT add useless dependencies to manufacture a size difference (plan T13).
- Debug apksigner: v2-signed dev key (Verifies). Release: UNSIGNED
  (`apksigner DOES NOT VERIFY`, no `META-INF` `.SF`/`.RSA`).
- First parallel release invocation hit transient `mergeExtDex`/`lintVital`
  failures; solo retries succeeded — recorded as flake, not regression
  (same transient class as the T13 DexMerging GC-thrash flake).
