# R3-01–04 Independent Acceptance

English | [中文](r3-01-04-independent-review.zh.md)

Review started 2026-09-19; conclusion confirmed 2026-09-20. Scope: execution report, related source/tests, and current two-flavor gates. No product code or tests were changed.

## Conclusion

**Partially passed. Independent work without these gaps may continue; R3-01–04 must not be marked DONE as a group.**

| Task | Result | Reason / handoff |
| --- | --- | --- |
| R3-01 | PARTIAL | Regression/lifecycle work progressed; DEF-008 remains pending. New destruction-flush test cannot distinguish earlier save from exit flush; see DEF-010. |
| R3-02 | PARTIAL | Scan matrix and capacity-boundary assertions help, but full multi-Library UI/provider-scale verification is missing. Testing a constant's boundary does not establish performance evidence. |
| R3-03 | Accepted for syntax scenarios covered this round | DEF-009 production task-list slicing fix is present; tests inspect actual TextView text/spans, not only AST. This does not prove every syntax combination or E-Ink appearance. |
| R3-04 | PARTIAL | Disk DataStore/Room tests and edit-fallback scenario are useful. Cross-process report proves test data can be read, not that production App restores its page/settings after restart; see DEF-011. |

Next: R3-06 may proceed on the rendering contract already handed off. R3-05 may prepare gesture scenarios, but position/settings-dependent work waits for the R3-04 gap. Do not reimplement R3-03; add evidence for R3-01/04 and fix product code only if a real fault is found.

## Evidence Audit

- MarkdownRenderer and ReaderLifecycleTest SHA-256 values match the execution report.
- Current ReaderPersistenceTest SHA-256 is `215dfa8786efa5fba40257b9549c24e4efe6c7819127d5e42ad4da8dbd7a38bc`, different from `686b294f...` in the execution report. Do not bind the historical two-stage run to current source without explaining it. This review did not independently rerun adb seed → force-stop → after-restart.
- Persistence tests use `r3-persist.db` / `r3-persist.preferences_pb`; production uses `leaflet.db` / `settings` DataStore. Useful tests, but not proof of production wiring/page restore.
- Lifecycle test calls jumpToBlock (which saves immediately), then checks `savedPositions.contains(documentId)` for exit/recreation flush. That may already be true before exit. `activity.recreate` does not remount the production Reader page; it checks only a test-held ViewModel.
- R3-02 matrix asserts 21 documents, not the original 25-document AC. Testing 500/5000 cap behavior does not measure real-provider performance.

## Minimum Evidence to Add

1. Create an unsaved position before exit; record prior save address/count; assert a new write and final address after exit. Test must fail if exit flush is disabled.
2. Through production AppContainer/Settings, change a value, read normally, kill process, and assert settings and visible position in a new production page. Do not inspect only a dedicated test table.
3. In two-stage persistence, use an independent seed marker. Do not decide whether to skip based on the value being tested, or a lost setting may be reported as skip instead of failure.
4. Keep real scan/multi-Library UI and capacity evidence open. If capacity performance moves to R3-08, document the handoff; do not mark PASS early.

This review did not independently verify release, standalone core device suites, clean Linux build, or E-Ink hardware. Do not widen existing claims.

## Verification Run This Review

```bash
./gradlew :app:assembleFullDebug :app:assembleEinkDebug test lint ktlintCheck \
  :app:connectedFullDebugAndroidTest :app:connectedEinkDebugAndroidTest --console=plain -q
```

Completed, exit 0, with incremental reuse. Current JVM XML: 588 variant / 276 unique, zero failures/errors.

- Full: 40 tests, 0 failures/errors, 0 skipped.
- E-Ink: 40 tests, 0 failures/errors, 1 skipped (`ReaderPersistenceAfterRestartTest`); 39 non-skipped passed. Do not say all 40 ran.
- XML timestamps: 2026-09-19T15:08:00 / 15:08:54 UTC. Counts differ from the executor report's 41; use this run's report.
- DEF-008 was not reproduced this time and remains pending; one green suite does not prove the historical root cause.
- Gaps are recorded as DEF-010/011; they are not newly observed product crashes or data loss.

R3-03 syntax scenarios may hand off to R3-06. R3-01/02/04 retain the gaps above and cannot be closed. A combined delivery report is acceptable; do not split reports only for naming.
