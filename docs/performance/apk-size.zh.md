# APK 体积（R2-08 Release 基线；取代 T13 §24 数值）

[English](apk-size.md) | 中文

### 基线说明

本基线来自 2026-09-18 的 R2-08 重建。构建目标为 `:app:assembleFullRelease` / `:app:assembleEinkRelease`，产物为未签名候选包；没有生产签名，`apksigner verify` 因缺少 `META-INF/MANIFEST.MF` 返回 `DOES NOT VERIFY`。`app/build.gradle.kts` 中 `isMinifyEnabled=false`，因此 Release 体积约等于去掉调试器后的 Debug 体积。

相较 T13，两种 flavor 各增加约 49.6 KB，来自 R2-05～R2-07 主源码增长：`SearchLimits` / `SearchResult` / 有上限的 UI、单元格搜索高亮、搜索栏计数、授权失败提示，以及 KMP 写入事务；没有新增随包依赖。

| 产物 | 字节数 | MD5 | applicationId | versionName |
| --- | ---: | --- | --- | --- |
| Full Release APK | 22,566,877 | `c442f1521330d1fd9e525e6b7e2e4543` | `io.leaflet.reader` | `0.1.0` |
| E-Ink Release APK | 22,566,617 | `b8d0ce3d8f9de1f9824d4982863b872f` | `io.leaflet.reader.eink` | `0.1.0-eink` |
| 差值（full − eink） | 260 | — | — | — |

Debug 参考：fullDebug 为 28,376,921 字节，einkDebug 为 28,376,869 字节，差值 52 字节。

### 组成与边界

- Full Release APK 的 dex 为 21,869,260 B（20.86 MB，约占 APK 的 96.9%）；`resources.arsc` 为 451,176 B（约 441 KB）；8 个 ABI 对应的 native `.so` 共 73,584 B；`res/` 为 42,049 B（85 个文件，仅启动图标）。另有 `assets/dexopt/baseline.prof` 7,557 B 和 `baseline.profm` 438 B；未打包字体或正文图片。
- APK 中列出的许可证所有者只有 `androidx`（zip 条目扫描）。测试资源未进入 APK：打包配置使用默认 `app/src`，zip 条目中没有匹配 `testdata|androidTest|Test.class` 的内容（2026-09-18 核验）。
- 两种 flavor 的权限均为零。唯一自动添加的权限是 AndroidX 的 `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`（signature 保护级别）。组件包括可启动的 `MainActivity` 及 AndroidX receivers/services。远程图片显示静态占位符，加载器调用数为零（T10 设计，页面测试断言无 `http` 加载调用）。
- 260 字节差值符合 ADR-0003：两种 flavor 共享约 100% 代码，差异只有应用名、ID 和 `AppCapabilities`。不要为了制造体积差异而加入无用依赖。
- Debug APK 由开发密钥进行 v2 签名（验证通过）；Release APK 未签名（`apksigner` 不通过，且没有 `META-INF` `.SF` / `.RSA`）。
- 首次并行 Release 构建遇到短暂的 `mergeExtDex` / `lintVital` 失败，单独重试成功。该项记录为构建波动，不是回归；与 T13 的 DexMerging GC 压力波动属于同类。
