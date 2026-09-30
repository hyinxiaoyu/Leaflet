# R3-07 交付 — 大文档、搜索与大目录

[English](r3-07-delivery.md) | 中文

日期：2026-09-28
包：R3-07，见[初始 MVP 的剩余任务](remaining-mvp-tasks.md) (Remaining Tasks for the Initial MVP)
需求：REQ-0002（阅读器）、REQ-0006（TOC）、REQ-0008（搜索）、REQ-0011（性能）
状态：所有计划中的形态均已在模拟器上测量；provider 层读取时间与 >16 MB 上限路径仍为 NOT_RUN。
独立评审：未执行（执行者自述）。

## 变更文件

| 文件 | 变更 |
| --- | --- |
| `app/src/androidTest/kotlin/io/leaflet/reader/ui/ReaderLargeDocumentPageTest.kt` | 新增：9 个应用层测试（3 种形态 × 尺寸、1,200 个标题的 TOC、密集/快速搜索、跨文档重置） |

本包未改动生产代码；一旦测量框架（harness）把带 Markdown 扩展名的文档喂给解析器，
就没有确认到任何缺陷（见「框架说明」）。

## 验证

```bash
# fast batch
./gradlew :app:connectedFullDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=io.leaflet.reader.ui.ReaderLargeDocumentPageTest#manySmallBlocksOpenAt100kAnd1m,io.leaflet.reader.ui.ReaderLargeDocumentPageTest#giantParagraphOpensAt100kAnd1m,io.leaflet.reader.ui.ReaderLargeDocumentPageTest#giantCodeBlockOpensAt100k,io.leaflet.reader.ui.ReaderLargeDocumentPageTest#thousandHeadingTocIsParsedAndLateHeadingsAreReachable,io.leaflet.reader.ui.ReaderLargeDocumentPageTest#denseSearchShowsTheCapNoticeAndRapidInputKeepsTheLatestQuery,io.leaflet.reader.ui.ReaderLargeDocumentPageTest#searchResetsAcrossDocumentSwitch
# slow batch
./gradlew :app:connectedFullDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=io.leaflet.reader.ui.ReaderLargeDocumentPageTest#manySmallBlocksOpenAt5m,io.leaflet.reader.ui.ReaderLargeDocumentPageTest#giantParagraphOpensAt5m,io.leaflet.reader.ui.ReaderLargeDocumentPageTest#giantParagraphOpensAt10m,io.leaflet.reader.ui.ReaderLargeDocumentPageTest#giantCodeBlockOpensAt1m,io.leaflet.reader.ui.ReaderLargeDocumentPageTest#giantCodeBlockOpensAt5m,io.leaflet.reader.ui.ReaderLargeDocumentPageTest#giantCodeBlockOpensAt10m,io.leaflet.reader.ui.ReaderLargeDocumentPageTest#oversizedDocumentShowsTheExplicitReadCapNotice
```

结果：fast batch `6/6 passed`（15.0 s），slow batch `3/3 passed`（17.2 s），再加上四个
收尾用例（巨型代码块 1/5/10 MB 与 17 MB 读取上限）`4/4 passed`，退出码 0
（emulator-5554，`dev_arm64_api35`，Android 15）。样本在进程内生成，并通过 `FakePageStore`
喂入；生产环境的读取 → 解析 → 分页路径、搜索栏与 TOC 均为真实实现。

### 测量结果（本进程；峰值 = 采样的 `totalPss`）

| 形态 | 尺寸 | 块数 | open → Content | → 首段文字 | → 页面上的结束标记 | 峰值 PSS |
| --- | --- | --- | --- | --- | --- | --- |
| 多小块 (many small blocks) | 100 KB | 1,645 | 648 ms | 649 ms | 922 ms | 86,237 KB |
| 多小块 (many small blocks) | 1 MB | 16,560 | 1,756 ms | 1,757 ms | 2,033 ms | 96,964 KB |
| 多小块 (many small blocks) | 5 MB | 82,096 | 7,947 ms | 7,950 ms | 8,340 ms | 148,746 KB |
| 巨型段落 (giant paragraph) | 100 KB | 6 | 225 ms | 228 ms | 480 ms | 78,860 KB |
| 巨型段落 (giant paragraph) | 1 MB | 34 | 347 ms | 348 ms | 616 ms | 93,796 KB |
| 巨型段落 (giant paragraph) | 5 MB | 162 | 1,265 ms | 1,266 ms | 1,558 ms | 208,469 KB |
| 巨型段落 (giant paragraph) | 10 MB | 322 | 2,483 ms | 2,484 ms | 2,824 ms | 244,728 KB |
| 巨型代码块 (giant code block) | 100 KB | 6 | 181 ms | 183 ms | 424 ms | 91,285 KB |
| 巨型代码块 (giant code block) | 1 MB | 34 | 311 ms | 313 ms | 593 ms | 199,814 KB |
| 巨型代码块 (giant code block) | 5 MB | 162 | 725 ms | 727 ms | 1,048 ms | 190,354 KB |
| 巨型代码块 (giant code block) | 10 MB | 323 | 1,596 ms | 1,597 ms | 1,971 ms | 236,205 KB |
| 超出读取上限的巨型段落 | 17 MB | 513 | — | — | — | — |

没有 OOM、没有 ANR、没有崩溃，也没有静默截断：每个文档都解析到了其 `END-MARKER-*`，
并且该标记是通过一次真实搜索跳转在页面上到达的。

### 逐条标准结果

| 标准 | 结果 | 证据 |
| --- | --- | --- |
| 在实际应用中验证 100 KB / 1 MB / 5 MB / 10 MB | PASS：many-small（100 KB/1 MB/5 MB）、巨型段落与巨型代码块（均为全部四种尺寸） | 上表 |
| 读取 / 解析 / 首次可读 / 峰值内存分别报告 | PARTIAL | open→Content 与 →首段文字 是分开的；在此接缝处读取与解析无法分离（内存字节，`ReaderSession` 一步完成读取 + 解析），provider 读取延迟 NOT_RUN；峰值 PSS 为采样值 |
| 大文档上的取消 / 快速切换 / 陈旧结果隔离 | PARTIAL | `searchResetsAcrossDocumentSwitch` 覆盖了切换重置；大文档取消由解析器层面的 `core:markdown` `LargeFileInstrumentedTest` 覆盖（2 MB 取消，陈旧结果永不覆盖），而非页面层面 |
| 1,000+ 标题的 TOC 可到达 | PASS | `thousandHeadingTocIsParsedAndLateHeadingsAreReachable`：解析出 1,200 个标题，`Section 1200` 唯一，其正文在页面上到达 |
| 搜索：快速输入、跨文档重置、密集匹配、上限 | PASS | `denseSearchShowsTheCapNoticeAndRapidInputKeepsTheLatestQuery`（6,000 个匹配 → 上限提示；紧随其后的无匹配查询占据状态）、`searchResetsAcrossDocumentSwitch` |
| 文件末尾标记可到达 | PASS | 表中每一行都在页面上到达了 `END-MARKER-*` |
| 读取上限显式且超过 16 MiB 时可观测 | PASS | `oversizedDocumentShowsTheExplicitReadCapNotice`：17 MB 文档以 degraded 状态打开，显示 `文件过大，仅显示前 16MB`，保留可读的开头（513 块），且超出上限的末尾标记不存在——截断是显式的，绝不静默 |

## 框架说明（已发现并修正）

该测试的第一个版本在生成文档时没有加 `.md` 扩展名（`many-100KB`）。解析器正确地把它们
当作 TXT，并渲染为字面量 Compose `Text`，而基于 `TextView` 的 `viewTexts` 探针看不到它，
于是每一步都在 180 s 处超时。现在文档在样本及其 id 中都带有 `.md`。中间那条
「1 MB 代码块卡住」的观察是该框架缺陷造成的假象，予以撤回——
`giant code block 100 KB` 以及所有段落/小块尺寸都在数秒内完成。

## 未运行 / 限制

- 真实 provider（SAF）的文档读取时间与冷缓存行为：NOT_RUN（样本在内存中，未测量
  provider 延迟）。
- 82,096 块的 5 MB many-small 用例是已测量的最慢形态（到首段内容约 8 s）；
  本包未更改阈值。
- 峰值内存是本进程采样得到的 `totalPss`，不是硬件计数器意义上的峰值。

## 停止条件

可重复的基线以及通过/失败矩阵记录在上文；过程中发现的失败属于框架侧，已记录在案而非
隐藏。没有添加库级 FTS，也没有添加 M2 性能重构。接下来已启动 R3-08。
