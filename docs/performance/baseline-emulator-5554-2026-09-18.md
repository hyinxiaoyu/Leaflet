# Performance Baseline — emulator-5554, 2026-09-18

English | [中文](baseline-emulator-5554-2026-09-18.zh.md)

> First measured run (T13). Unmeasured fields stay `NOT_RUN` per template rules.
> Thresholds are NOT set from this single run (REQ-0011: record first, gate later).

## Run context

| Field | Value |
| --- | --- |
| Device | emulator-5554 (sdk_gphone64_arm64) |
| Android version | 15 (API 35, dev_arm64_api35 AVD) |
| Firmware / build | Emulator 37.1.11, arm64 |
| Leaflet version | 0.1.0 (full) / 0.1.0-eink (eink), versionCode 1 |
| Display mode | STANDARD on full (EINK default on eink flavor verified separately) |
| Sample files | `testdata/generate_large_samples.py verify OK` (13 files, 51687424 B, exact sizes, deterministic) |
| Date | 2026-09-18 |
| Heap | 576m |

## Results

| Metric | Run 1 | Run 2 | Run 3 | Median (range) | Notes |
| --- | --- | --- | --- | --- | --- |
| Cold start full (ms, `am start -W` TotalTime) | 862 | 851 | 785 | 851 (785–902 over 5 runs: 862/851/785/787/902) | launcher → library visible |
| Cold start eink (ms) | 1816 | 1592 | 1570 | 1592 (1570–1816) | first launch slower (profile install); settles ~1.6s |
| Cold start full R2-07 (ms) | 815 | 1013 | 830 | 830 | same method, debug build, after reinstall (grant + 8-file tree) |
| Cold start eink R2-07 (ms) | 782 | — | — | 782 | debug build, fresh install, no library |
| Library open | NOT_RUN | — | — | NOT_RUN | no harness; record library size when measured |
| Document open (1MB/12149 blocks, production page) | ≈8000 wall tap→readable | — | — | — | R2-07 walkthrough, full debug, `large-many-1m.md`, STANDARD |
| Page turn latency | NOT_RUN | — | — | NOT_RUN | E-Ink: settled, not first flash |
| Scroll latency | NOT_RUN | — | — | NOT_RUN | E-Ink mode: scrolling discouraged |
| Peak memory (settled full PSS) | 71491 KB | — | — | — | `dumpsys meminfo`, settled (not during parse) |
| Peak memory (10MB parse) | ~158–171 MB PSS / ~240–254 MB RSS / ~120–134 MB Java Heap | — | — | — | no OOM on emulator; device logcat evidence |
| Peak memory (R2-07 production page: 1MB open settled) | 122563 KB PSS / 233424 KB RSS / 36428 KB heap | — | — | — | `dumpsys meminfo io.leaflet.reader.debug`, post-open settled |
| Peak memory (R2-07 production page: 10MB mid-parse + Back-cancel) | 260000 KB PSS / 371704 KB RSS / 90032 KB heap mid-parse; 235348 KB PSS post-cancel | — | — | — | Loading at 6s, no ANR, cancel returns to browse; logcat ANR/FATAL clean |
| Library scan timing | NOT_RUN | — | — | NOT_RUN | record file count + provider when measured |
| APK size fullRelease | 22566877 | — | — | — | see `apk-size.md` (R2-08 rebuild; T13 §24 numbers superseded) |
| APK size einkRelease | 22566617 | — | — | — | see `apk-size.md` (R2-08 rebuild; T13 §24 numbers superseded) |

## Large-document checklist (device evidence 2026-09-18)

| Tier (many-small-blocks) | No OOM | No ANR | No crash | Degradation notice | Cancellable | Readable |
| --- | --- | --- | --- | --- | --- | --- |
| 100 KB | PASS (JVM + device shapes) | PASS | PASS | N/A (clean parse) | PASS (JVM) | PASS |
| 1 MB | PASS | PASS | PASS | N/A (clean parse) | PASS (`staleParse`) | PASS |
| 5 MB | PASS (JVM probe) | NOT_RUN | PASS | shape-dependent | PASS (JVM) | PASS |
| 10 MB many-blocks | PASS (~599s parse, see note) | PASS | PASS | N/A (clean parse) | PASS (`parseCancellationDropsCleanly`, 2MB cooperative path) | PASS (needle-42 found) |
| 10 MB single-giant-para | PASS (~1.2s) | PASS | PASS | PASS (plain-text notice) | N/A | PASS (coverage ≥ len − nblocks) |
| 10 MB single-giant-code | PASS (~0.6s) | PASS | PASS | PASS | N/A | PASS |

Note: 10MB many-blocks parse ≈ 599s on this emulator (205,972 blocks,
cooperative `yield` every 512 blocks). Emulator CPU is not a device
baseline — record only, do not gate on it. The full parse must not live
inside `runTest` (60s virtual budget); the instrumented test uses plain
`@Test(timeout=600000)` + `Dispatchers.Default`.

## File-invariance check (REQ-0011 AC-7)

| Step | Result |
| --- | --- |
| Instrumented acidic probes (Room remove-library cascade, SAF/Scanner sha256 before==after) | PASS on device (`:core:storage:connectedDebugAndroidTest` 16/16: Room 5 + SAF 6 + Scanner 5) |
| Real-user-dir uninstall/clear/remove hash diff (`find testdata/markdown … \| sha256sum`) | NOT_RUN — manual step, must be marked as such |

## Method notes

- Cold start: `am start -W` TotalTime, force-stop between runs.
- Memory: `dumpsys meminfo`; never compare peaks across device models.
- Scan: record provider (real SAF vs test double) + file count + metadata-only.
