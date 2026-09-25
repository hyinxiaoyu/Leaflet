# 性能基线 — emulator-5554，2026-09-18

[English](baseline-emulator-5554-2026-09-18.md) | 中文

这是 T13 的首次实测。依据模板规则，未测字段继续标记 `NOT_RUN`；单次运行不足以设定性能阈值（REQ-0011：先记录，再设门禁）。

### 设备和样本

设备为 `emulator-5554`（`sdk_gphone64_arm64`），Android 15 / API 35，Emulator 37.1.11 arm64。Leaflet 版本为 Full `0.1.0` / E-Ink `0.1.0-eink`，versionCode 1；Full 运行 STANDARD，E-Ink flavor 默认 EINK 已单独验证。大文件样本生成器通过校验：13 个文件，共 51,687,424 B，大小准确且结果确定性；测试日期 2026-09-18，Heap 为 576m。

### 结果解读

- 冷启动：Full 为 785–902 ms（5 次，中位数 851 ms）；E-Ink 为 1570–1816 ms（中位数 1592 ms），首次启动较慢与 profile 安装有关，稳定后约 1.6 秒。R2-07 Full Debug 三次为 815 / 1013 / 830 ms（中位数 830 ms）；E-Ink Debug 新装为 782 ms。
- 生产页面打开 1 MB / 12,149 块文档约需 8 秒（点击至可读，Full Debug、STANDARD）。资料库打开、翻页、滚动和扫描耗时仍未测量。
- 内存：稳定态 Full PSS 为 71,491 KB；10 MB 解析期间约 158–171 MB PSS、240–254 MB RSS、120–134 MB Java Heap。R2-07 的 1 MB 生产页面稳定态为 122,563 KB PSS / 233,424 KB RSS / 36,428 KB heap；10 MB 解析中途约 260,000 KB PSS / 371,704 KB RSS / 90,032 KB heap，取消后 PSS 为 235,348 KB。解析时显示 Loading 为 6 秒；无 ANR，取消回浏览页，logcat 无 ANR/FATAL。
- Release APK：Full 22,566,877 字节，E-Ink 22,566,617 字节；R2-08 重建基线已取代 T13 §24 数值，详见 `apk-size.md`。

### 大文档结果

100 KB：无 OOM/ANR/crash，内容完整；1 MB 同样通过，取消走 `staleParse`；5 MB 的 JVM 探测通过，但 ANR 未在设备验证；10 MB 多块文档通过，解析约 599 秒（205,972 块，512 块一次协作 `yield`），`parseCancellationDropsCleanly` 通过并能找到 `needle-42`。10 MB 单巨型段落约 1.2 秒、单巨型代码约 0.6 秒，均显示降级且内容覆盖正确。

599 秒结果仅记录此模拟器表现，不能作为真实设备性能基线或门禁。完整解析不能放在 `runTest` 内（虚拟时间预算 60 秒）；instrumented 测试使用普通 `@Test(timeout=600000)` + `Dispatchers.Default`。

### 原文件不变性

Room 移除 Library 级联和 SAF/Scanner 前后 SHA-256 探针在设备测试通过：`:core:storage:connectedDebugAndroidTest` 共 16/16（Room 5 + SAF 6 + Scanner 5）。真实用户目录的卸载/清除/移除后哈希比较仍是手动步骤，未运行，必须保持 `NOT_RUN`。

### 方法

冷启动使用 `am start -W` 的 TotalTime，每次运行间 force-stop；内存使用 `dumpsys meminfo`；扫描基线必须记录 Provider（真实 SAF 或测试替身）、文件数及元数据扫描属性。不同设备的峰值不可直接比较。
