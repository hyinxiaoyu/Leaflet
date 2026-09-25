# 性能基线模板（每次测量复制一份）

[English](baseline-template.md) | 中文

每次测量都将本文件复制为 `docs/performance/baseline-<设备>-<日期>.md`。不得编造数字，未测项目保持 `NOT_RUN`。只有取得可重复的实测结果后才能设阈值（REQ-0011：先记录，再设门禁；本模板不会建立自动 CI 性能门禁）。

### 运行环境字段

| 字段 | 填写说明 |
| --- | --- |
| Device | 设备型号（例如 Pixel 6a / BOOX 型号） |
| Android version | Android 版本 |
| Firmware / build | 固件或系统构建版本 |
| Leaflet version | `versionName + versionCode + flavor` |
| Display mode | 运行模式：`STANDARD` / `EINK` |
| Sample file + size | 样本文件及字节数，例如 `testdata/generated/large-many-1m.md`、1048576 B |
| Date / Tester | 测量日期 / 测试者 |
| Notes | 充电状态、后台应用、重复次数等环境备注 |

### 结果字段

每项至少重复 3 次，报告中位数和范围。记录冷启动（点击启动图标至资料库可见）、资料库打开（应用启动至列表填充，并记录条目数）、文档打开（点击至首屏可见）、翻页延迟（点击至页面稳定；E-Ink 以稳定画面而非第一次闪烁为准）、滚动延迟或卡顿、峰值内存、资料库扫描耗时及文件数/Provider，以及 Full/E-Ink Release APK 体积。内存结果需注明使用的工具（Android Studio profiler / dumpsys）。E-Ink 模式不鼓励连续滚动，应记录实际表现。

### 大文档检查表

针对每个已打开的档位（100 KB、1 MB、5 MB、10 MB）记录：无 OOM、无 ANR、无崩溃、是否显示降级提示、是否可取消且释放资源、是否可读且没有静默截断。未测内容保持 `NOT_RUN`。

### 原文件不变性检查（REQ-0011 AC-7）

移除 Library 并清库前，对 `testdata/markdown` 执行 SHA-256 清单；操作后再次生成清单并用 `diff` 比较。只有前后匹配才能记为通过；命令和输出字段见英文表格。

### 测量方法

- 冷启动：用 `am force-stop` 结束 App，再点启动图标；从点击开始计时，到首个资料库画面停止。至少重复 3 次；如使用飞行模式需注明。
- 文档打开：清除最近状态后重复打开同一文件；注明超大块文本是否走增量或降级路径。
- 内存：记录打开并稳定后的峰值和工具名；不同设备型号之间不得直接比较峰值。
- 扫描：记录真实 SAF Provider 或测试替身、文件数，以及是否仅扫描元数据（首次扫描不得解析全文）。
- 因缺少硬件而无法执行的项目需在 Notes 写明原因并保持 `NOT_RUN`。它们是发布风险，不是通过项。
