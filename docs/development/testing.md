# Testing

English | [中文](testing.zh.md)

Three tiers (never substitute one for another):

1. **JVM unit tests** (`./gradlew test`): pure-Kotlin contracts — ReadingPosition
   versioning/anchor fallback, E-Ink off-list, motion-policy coupling,
   Generic-controller redraw rhythm, scanner rules, parser/title/encoding
   matrices. These run in CI. Baseline: 494 variant test records, 240 unique
   (classname+name dedup), 0 failures (2026-09-18, R2-08 gate).
2. **Instrumented tests**: `:core:storage:connectedDebugAndroidTest` (Room/SAF/Scanner, 16 tests),
   `:core:markdown:connectedDebugAndroidTest` (10MB large-file pass, 5 tests),
   `:app:connected{Full,Eink}DebugAndroidTest` (production-page suite, 12 tests each flavor — R2-07:
   `ReaderEntryTest` 3 + `ReaderRecoveryTest` 5 + `ReaderDisplayTest` 3 + `ReaderImageTest` 1, all composing the
   real `ReaderScreen` + session/Markwon parser over a fake storage seam via `createComposeRule`; TEST-process host
   activities are infra-blocked on this emulator image, so no activity is launched — see `PageHarness` and
   `app/build.gradle.kts`). Need a device/emulator; not in CI. An empty connected-app run proves nothing; do not
   record NO-SOURCE output as UI coverage.
3. **E-Ink device matrix** (`eink-test-matrix.md`, T13): ghosting/refresh verdicts
   come ONLY from real E-Ink hardware. Missing device rows stay NOT_RUN/BLOCKED.

Format: `ktlintCheck` covers `:app` AND all `:core:*` modules (R2-00:
previously app-only). Compose uppercase function names are exempt via
`app/build.gradle.kts` `additionalEditorconfig`.
