# Release

English | [中文](release.zh.md)

```bash
./gradlew :app:assembleFullRelease :app:assembleEinkRelease
```

- Signing: **TBD — no production signing material is generated or committed.**
  Release builds declare NO `signingConfig`, so `apksigner verify` reports
  `DOES NOT VERIFY` (missing `META-INF/MANIFEST.MF`) — they are UNSIGNED
  candidates, not debug-key-signed (corrected R2-08 per F09; this file
  previously claimed the default debug keystore). Formal signing remains an
  owner decision (plan §8).
- The release merged manifest must contain no unapproved permissions or network
  capabilities (REQ-0011 FR-7). T02 declares zero permissions.
- APK size baselines (`docs/performance/apk-size.md`) start in T13.
- Formal applicationId, license and distribution channel are owner decisions
  (plan §8); current IDs (`io.leaflet.reader` / `io.leaflet.reader.eink`) are
  temporary and centrally configured in `app/build.gradle.kts`.
