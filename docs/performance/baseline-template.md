# Performance Baseline — template (copy per run)

English | [中文](baseline-template.zh.md)

> Copy this file to `docs/performance/baseline-<device>-<date>.md` per
> measurement run. No fabricated numbers: unmeasured fields stay `NOT_RUN`.
> Thresholds are set only after repeatable runs exist (REQ-0011: record
> first, gate later — no automatic CI regression gate from this template).

## Run context

| Field | Value |
| --- | --- |
| Device | NOT_RUN (e.g. Pixel 6a / BOOX model) |
| Android version | NOT_RUN |
| Firmware / build | NOT_RUN |
| Leaflet version (versionName + versionCode + flavor) | NOT_RUN |
| Display mode (STANDARD / EINK) | NOT_RUN |
| Sample file + size | NOT_RUN (e.g. `testdata/generated/large-many-1m.md`, 1048576 B) |
| Date | NOT_RUN |
| Tester | NOT_RUN |
| Notes (charger state, background apps, repeats) | NOT_RUN |

## Results (3+ repeats each; report median + range)

| Metric | Run 1 | Run 2 | Run 3 | Median (range) | Notes |
| --- | --- | --- | --- | --- | --- |
| Cold start (ms, launcher tap → library visible) | NOT_RUN | NOT_RUN | NOT_RUN | NOT_RUN | |
| Library open (ms, app start → list populated, N items: ___) | NOT_RUN | NOT_RUN | NOT_RUN | NOT_RUN | record library size |
| Document open (ms, tap → first content visible) | NOT_RUN | NOT_RUN | NOT_RUN | NOT_RUN | record sample + mode |
| Page turn latency (ms, tap → settled page) | NOT_RUN | NOT_RUN | NOT_RUN | NOT_RUN | E-Ink: settled, not first flash |
| Scroll latency (ms per frame or jank notes) | NOT_RUN | NOT_RUN | NOT_RUN | NOT_RUN | E-Ink mode: scrolling discouraged; note behavior |
| Peak memory (MB, during large-doc open) | NOT_RUN | NOT_RUN | NOT_RUN | NOT_RUN | state tool used (Android Studio profiler / dumpsys) |
| Library scan (ms, N files, cold/warm) | NOT_RUN | NOT_RUN | NOT_RUN | NOT_RUN | record file count + provider |
| APK size fullRelease (bytes) | NOT_RUN | — | — | — | see `apk-size.md` (T13) |
| APK size einkRelease (bytes) | NOT_RUN | — | — | — | see `apk-size.md` (T13) |

## Large-document checklist (per tier opened)

| Tier | No OOM | No ANR | No crash | Degradation notice shown | Cancellable, resources freed | Readable (no silent truncation) |
| --- | --- | --- | --- | --- | --- | --- |
| 100 KB | NOT_RUN | NOT_RUN | NOT_RUN | NOT_RUN | NOT_RUN | NOT_RUN |
| 1 MB | NOT_RUN | NOT_RUN | NOT_RUN | NOT_RUN | NOT_RUN | NOT_RUN |
| 5 MB | NOT_RUN | NOT_RUN | NOT_RUN | NOT_RUN | NOT_RUN | NOT_RUN |
| 10 MB | NOT_RUN | NOT_RUN | NOT_RUN | NOT_RUN | NOT_RUN | NOT_RUN |

## File-invariance check (REQ-0011 AC-7)

| Step | Command / action | SHA-256 (sample dir) | Match |
| --- | --- | --- | --- |
| Before (remove library + clear DB) | `find testdata/markdown -type f -exec sha256sum {} + \| sort > /tmp/before.txt` | NOT_RUN | — |
| After | same, to `/tmp/after.txt`; `diff /tmp/before.txt /tmp/after.txt` | NOT_RUN | NOT_RUN |

## Method notes

- Cold start: kill app (`am force-stop`), then launcher tap; start timing at
  tap, stop at first library frame. Repeat ≥3, airplane-mode note if relevant.
- Document open: same-file repeats after clearing recent state; note whether
  incremental/fallback path engaged for giant-block shapes.
- Memory: record peak during open + settle, plus tool name; never compare
  peaks across different device models.
- Scan: record provider (real SAF provider vs test double), file count, and
  whether metadata-only (first scan must not parse full text).
- Anything blocked on missing hardware stays `NOT_RUN` with the reason in
  Notes; `NOT_RUN` rows are release risks, not passes.
