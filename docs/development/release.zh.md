# 发布

[English](release.md) | 中文

```bash
./gradlew :app:assembleFullRelease :app:assembleEinkRelease
```

- **签名：待定；目前没有生成或提交生产签名材料。** Release 构建未声明 `signingConfig`，因此 `apksigner verify` 会因缺少 `META-INF/MANIFEST.MF` 报告 `DOES NOT VERIFY`。这些是未签名候选包，不是使用 debug key 签名的包（依 F09 在 R2-08 更正；本文件此前曾误称使用默认 debug keystore）。正式签名由项目所有者决定（计划 §8）。
- Release 合并后的 Manifest 不得包含未经批准的权限或网络能力（REQ-0011 FR-7）。T02 声明的权限数为零。
- APK 体积基线从 T13 开始记录，见 `docs/performance/apk-size.md`。
- 正式 applicationId、许可证和分发渠道由项目所有者决定（计划 §8）。当前 ID（`io.leaflet.reader` / `io.leaflet.reader.eink`）为临时值，集中配置在 `app/build.gradle.kts`。
