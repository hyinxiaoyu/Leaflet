# Build

English | [中文](build.zh.md)

Environment (frozen toolchain; libraries adjusted per evidence below):

- JDK 17 is REQUIRED to run the Gradle daemon (AGP 8.13.x). The repo sets
  NO `org.gradle.java.home` (R2-00: machine-specific pins are not portable;
  the old macOS absolute path was removed). Before invoking Gradle, point
  `JAVA_HOME` at a local JDK 17, e.g.
  `export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home`
  (or set `org.gradle.java.home` in user-local `~/.gradle/gradle.properties`,
  never committed). Module compilation is pinned independently via
  `kotlin jvmToolchain(17)`. CI (`ubuntu-latest`, `setup-java` temurin 17)
  relies on `JAVA_HOME` the same way.
- Android SDK with `platforms;android-35` and `build-tools;35.0.0/36.0.0`
  (via `ANDROID_HOME`/`ANDROID_SDK_ROOT` env or `local.properties`,
  both uncommitted).
- Gradle Wrapper 8.13 (`./gradlew`), AGP 8.13.2, Kotlin 2.0.21.
- Host `~/.gradle/init.gradle` repository injections are tolerated but
  never required: `settings.gradle.kts` uses `PREFER_SETTINGS`, and
  dependency resolution prefers `google()`/`mavenCentral()`.

Verified commands (run from the repo root, verified 2026-09-17):

```bash
./gradlew :app:assembleFullDebug :app:assembleEinkDebug
./gradlew test lint ktlintCheck
./gradlew :app:assembleFullRelease :app:assembleEinkRelease
./gradlew :app:connectedFullDebugAndroidTest
./gradlew :app:connectedEinkDebugAndroidTest
```

APKs land under `app/build/outputs/apk/<full|eink>/<debug|release>/`.
Debug builds add an `.debug` applicationId suffix on top of the flavor ID
(full debug `io.leaflet.reader.debug`, eink debug `io.leaflet.reader.eink.debug`).

## T02 version evidence (why four libraries differ from the T00 proposal)

The T00-proposed `activity-compose 1.13.0` / `lifecycle 2.11.0` /
`navigation-compose 2.10.1` / `compose-bom 2026.09.00` fail
`:app:assembleFullDebug` under the frozen AGP 8.13.2 + compileSdk 35: their
transitives (`core-ktx 1.18.0`, compose `1.12.x`, `navigationevent 1.1.2`)
require compileSdk 36 minimum AND AGP 9.1.0 (Gradle error text, 2026-09-17).
Per Main decision (option A: toolchain stays frozen), T02 uses the newest
SDK-35-compatible set that builds green:

| Artifact | T02 version | T00 proposal |
| --- | --- | --- |
| androidx.activity:activity-compose | 1.9.3 | 1.13.0 |
| androidx.lifecycle (viewmodel-compose/runtime) | 2.8.7 | 2.11.0 |
| androidx.navigation:navigation-compose | 2.7.7 | 2.10.1 |
| androidx.compose:compose-bom | 2024.10.01 | 2026.09.00 |

Unchanged: AGP 8.13.2, Gradle 8.13, Kotlin 2.0.21, KSP 2.0.21-1.0.27,
compileSdk/targetSdk 35, minSdk 29, Room 2.8.5 (frozen for T04, unused in T02),
datastore-preferences 1.2.1, coroutines 1.10.2, junit4 4.13.2.
Added minimal `androidx.annotation:annotation 1.9.1` for `@ColorInt` in
`:core:eink` (Apache-2.0, recorded in THIRD_PARTY_LICENSES.md).
