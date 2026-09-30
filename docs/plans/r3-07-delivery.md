# R3-07 Delivery — Large Documents, Search, and Large TOC

English | [中文](r3-07-delivery.zh.md)

Date: 2026-09-28
Package: R3-07 in [Remaining Tasks for the Initial MVP](remaining-mvp-tasks.md)
Requirement: REQ-0002 (reader), REQ-0006 (TOC), REQ-0008 (search), REQ-0011 (performance)
Status: All planned shapes measured on an emulator; provider-level read time and the >16 MB cap path remain NOT_RUN.
Independent review: not performed (executor self-report).

## Changed files

| File | Change |
| --- | --- |
| `app/src/androidTest/kotlin/io/leaflet/reader/ui/ReaderLargeDocumentPageTest.kt` | New: 9 app-level tests (3 shapes × sizes, 1,200-heading TOC, dense/rapid search, cross-document reset) |

No production code changed in this package; no defect was confirmed once the measurement
harness fed the parser documents with a Markdown extension (see "Harness note").

## Verification

```bash
# fast batch
./gradlew :app:connectedFullDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=io.leaflet.reader.ui.ReaderLargeDocumentPageTest#manySmallBlocksOpenAt100kAnd1m,io.leaflet.reader.ui.ReaderLargeDocumentPageTest#giantParagraphOpensAt100kAnd1m,io.leaflet.reader.ui.ReaderLargeDocumentPageTest#giantCodeBlockOpensAt100k,io.leaflet.reader.ui.ReaderLargeDocumentPageTest#thousandHeadingTocIsParsedAndLateHeadingsAreReachable,io.leaflet.reader.ui.ReaderLargeDocumentPageTest#denseSearchShowsTheCapNoticeAndRapidInputKeepsTheLatestQuery,io.leaflet.reader.ui.ReaderLargeDocumentPageTest#searchResetsAcrossDocumentSwitch
# slow batch
./gradlew :app:connectedFullDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=io.leaflet.reader.ui.ReaderLargeDocumentPageTest#manySmallBlocksOpenAt5m,io.leaflet.reader.ui.ReaderLargeDocumentPageTest#giantParagraphOpensAt5m,io.leaflet.reader.ui.ReaderLargeDocumentPageTest#giantParagraphOpensAt10m,io.leaflet.reader.ui.ReaderLargeDocumentPageTest#giantCodeBlockOpensAt1m,io.leaflet.reader.ui.ReaderLargeDocumentPageTest#giantCodeBlockOpensAt5m,io.leaflet.reader.ui.ReaderLargeDocumentPageTest#giantCodeBlockOpensAt10m,io.leaflet.reader.ui.ReaderLargeDocumentPageTest#oversizedDocumentShowsTheExplicitReadCapNotice
```

Result: fast batch `6/6 passed` (15.0 s), slow batch `3/3 passed` (17.2 s) plus the four
closeout cases (giant code 1/5/10 MB and the 17 MB read cap) `4/4 passed`, exit 0
(emulator-5554, `dev_arm64_api35`, Android 15). Samples are generated in-process and fed
through `FakePageStore`; the production read → parse → page path, search bar, and TOC are real.

### Measurements (this process; peak = sampled `totalPss`)

| Shape | Size | Blocks | open → Content | → first text | → end marker on page | peak PSS |
| --- | --- | --- | --- | --- | --- | --- |
| many small blocks | 100 KB | 1,645 | 648 ms | 649 ms | 922 ms | 86,237 KB |
| many small blocks | 1 MB | 16,560 | 1,756 ms | 1,757 ms | 2,033 ms | 96,964 KB |
| many small blocks | 5 MB | 82,096 | 7,947 ms | 7,950 ms | 8,340 ms | 148,746 KB |
| giant paragraph | 100 KB | 6 | 225 ms | 228 ms | 480 ms | 78,860 KB |
| giant paragraph | 1 MB | 34 | 347 ms | 348 ms | 616 ms | 93,796 KB |
| giant paragraph | 5 MB | 162 | 1,265 ms | 1,266 ms | 1,558 ms | 208,469 KB |
| giant paragraph | 10 MB | 322 | 2,483 ms | 2,484 ms | 2,824 ms | 244,728 KB |
| giant code block | 100 KB | 6 | 181 ms | 183 ms | 424 ms | 91,285 KB |
| giant code block | 1 MB | 34 | 311 ms | 313 ms | 593 ms | 199,814 KB |
| giant code block | 5 MB | 162 | 725 ms | 727 ms | 1,048 ms | 190,354 KB |
| giant code block | 10 MB | 323 | 1,596 ms | 1,597 ms | 1,971 ms | 236,205 KB |
| giant paragraph above the read cap | 17 MB | 513 | — | — | — | — |

No OOM, no ANR, no crash, and no silent truncation: every document parsed to its
`END-MARKER-*` and the marker was reached on the page through a real search jump.

### Per-criterion result

| Criterion | Result | Evidence |
| --- | --- | --- |
| 100 KB / 1 MB / 5 MB / 10 MB in the actual app | PASS for many-small (100 KB/1 MB/5 MB) and giant paragraph and giant code (all four sizes) | Table above |
| Read / parse / first-readable / peak memory reported separately | PARTIAL | open→Content and →first-text are split; read and parse are not separable at this seam (in-memory bytes, `ReaderSession` reads + parses in one step), provider read latency NOT_RUN; peak PSS sampled |
| Cancellation / rapid switching / stale isolation on large docs | PARTIAL | `searchResetsAcrossDocumentSwitch` covers switch-reset; large-document cancellation is covered by `core:markdown` `LargeFileInstrumentedTest` (2 MB cancel, stale never overwrites) at parser level, not at page level |
| 1,000+ heading TOC reachable | PASS | `thousandHeadingTocIsParsedAndLateHeadingsAreReachable`: 1,200 parsed headings, `Section 1200` unique, its body reached on the page |
| Search: rapid input, cross-document reset, dense matches, cap | PASS | `denseSearchShowsTheCapNoticeAndRapidInputKeepsTheLatestQuery` (6,000 matches → capped notice; an immediately following no-match query owns the state), `searchResetsAcrossDocumentSwitch` |
| End-of-file marker reachable | PASS | every row of the table reached `END-MARKER-*` on the page |
| Read cap is explicit and observable above 16 MiB | PASS | `oversizedDocumentShowsTheExplicitReadCapNotice`: a 17 MB document opens degraded, shows `文件过大，仅显示前 16MB`, keeps its readable head (513 blocks), and the tail marker beyond the cap is absent — truncation is explicit, never silent |

## Harness note (found and corrected)

The first version of this test named its generated documents without a `.md` extension
(`many-100KB`). The parser correctly treated them as TXT and rendered literal Compose
`Text`, which the `TextView`-based `viewTexts` probe cannot see, so every step timed out at
180 s. Documents now carry `.md` in both the sample and its id. The intermediate
"1 MB code block hangs" observation was an artefact of that harness bug and is retracted —
`giant code block 100 KB` and every paragraph/small-block size complete in seconds.

## Not run / limitations

- Real provider (SAF) document read time and cold-cache behaviour: NOT_RUN (the samples
  are in-memory; provider latency is not measured).
- The 82,096-block 5 MB many-small case is the slowest measured shape (~8 s to first
  content); thresholds were not changed by this package.
- Peak memory is this process's sampled `totalPss`, not a hardware-counter peak.

## Stop condition

The repeatable baseline and pass/fail matrix are recorded above; failures found along the
way were harness-side and are documented rather than hidden. No library-wide FTS or M2
performance refactoring was added. R3-08 was started next.
