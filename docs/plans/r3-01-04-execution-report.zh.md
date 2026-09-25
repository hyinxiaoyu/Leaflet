# R3-01～04 执行报告

[English](r3-01-04-execution-report.md) | 中文

日期：2026-09-19　执行 agent：执行 agent（补测试/最小修复角色）
范围依据：[remaining-mvp-tasks.md](remaining-mvp-tasks.md)「下一轮只派发 R3-01～04」；本轮**不**派发 R3-05～10。
状态：**待验收**（执行 agent 自述不替代独立验收）。

环境：emulator-5554 / AVD dev_arm64_api35 / Android 15 (API 35)，**模拟器，非 E-Ink 实机**；
JDK 17、AGP 8.13.2、Gradle 8.13、Kotlin 2.0.21。

门禁汇总（同一次调用，全部通过）：

| 项 | 结果 |
| --- | --- |
| `./gradlew test lint ktlintCheck` | 绿 |
| `:app:assembleFullDebug :app:assembleEinkDebug` | 绿 |
| `:app:connectedFullDebugAndroidTest` | 该轮 41 项（40 executed + 1 skipped，设计内跳过）；**数量口径以 XML 为准，不写成 41/41 全通过** |
| `:app:connectedEinkDebugAndroidTest` | 该轮 41 项（40 executed + 1 skipped） |
| JVM 单测 | 588 variant / 276 unique，0 failure/error |
| `:core:markdown:connectedDebugAndroidTest` | 5/5 |
| `:core:storage:connectedDebugAndroidTest` | 16/16 |

环境备注：`:app:assembleEinkRelease` / `assembleFullRelease` 在机器负载高时会出现
`D8: java.lang.OutOfMemoryError`（daemon 堆），用
`./gradlew ... -Dorg.gradle.jvmargs="-Xmx4g -XX:MaxMetaspaceSize=1g"` 后四包全绿。
未改 `gradle.properties`（仓库明确要求不写入机器相关配置）。

---

## R3-01 — 最新缺陷独立复核与生命周期稳定性

**复核（复用有效证据，不重复实现）**

| 上轮结论 | 复核方式 | 结果 |
| --- | --- | --- |
| DEF-003 表格子项已修 | 重跑反例：JVM `EntryBindingsTest.identicalCellsOnlyTheSelectedOneIsCurrent`（等）+ 设备生产视图 `ReaderEntryTest.duplicateTableCellOnlyTheSelectedOneIsCurrent`（4 个同文本格中恰好 1 格当前样式，步进后移动） | PASS |
| DEF-004 围栏/复制 | `ReaderEntryTest.codeFenceWithInnerFenceShowsWholeBody` + JVM 四反引号/波浪线/缩进/逐字复制用例 | PASS |
| DEF-005 真实可达性 | `ReaderStorageBoundaryTest`（真实 Room + 真实 reader 链路，provider 失权/条目消失）3 例 | PASS |
| DEF-006 / DEF-007 | 页测已含真实 view tree / 生产视图断言；本报告与 verdict 重汇总 | PASS |
| DEF-008 | 见下 | FIXED_PENDING_VERIFICATION |

**本轮新增：生命周期稳定性**（`app/src/androidTest/.../ReaderLifecycleTest.kt`，2 例）

1. `leavingTheRouteFlushesPositionAndKeepsTheViewModelUsable`：阅读页离开组合（等价返回目录）
   → `DisposableEffect` 落盘 flush 到达 store；同一 ViewModel 重开同文档保持同一 `ParsedDocument`。
2. `activityRecreationKeepsTheDocumentAndRestoreAddress`：对宿主 Activity 执行**真实** `recreate()`
   → 解析结果实例与 `restoreBlock` 均保持，被销毁的组合在 dispose 时完成位置落盘。

**DEF-008 残余风险（不宣称根因已证实）**：原始 `SlotWriter.removeGroup` AIOOBE 在本轮（含旋转、
返回、recreate、双 flavor 各 41 例、联合调用）**未再复现**；已落地的证据是
(a) 常驻门禁 `ReaderThreadingTest`（修复前 3 次非主线程 snapshot apply，修复后 0）、
(b) 测试侧主线程纪律。这两项是**已确认的行为改动**，但“IO 线程写 StateFlow 即为原崩溃根因”仍是
基于调用栈的推断。关闭条件（可解释根因 + 针对性验证）未全部满足 → 维持 FIXED_PENDING_VERIFICATION。

---

## R3-02 — 资料库完整场景验收

**新增（`core/storage/src/test/.../LibraryScannerTest.kt`，4 例）**

- `preciseDiscoveryMatrixCounts`：5 个目录 × 4 文档 + 3 个空目录 + 1 个嵌套目录文档
  → 成功、文档数 21、层级保留（`folder1/doc1-1..4.md`）、空目录不产生行、嵌套文档入库。
- `multipleLibrariesStayIsolated`：两资料库同相对路径 → 各自一行；移除 A 后 B 的行与授权保留。
- `capsAcceptTheBoundaryAndRejectOnePastIt`：恰好 `MAX_INDEXED_DOCUMENTS`(=500) 成功；
  501 显式 FAILED + `ScanFailureKind.OVERFLOW` + 零写入；同时钉住 `MAX_VISITED_ENTRIES`(=5000)。
  （测量依据即这两个常量；越界行为由该用例与既有 `visitedOverflowFailsExplicitlyWithOldIndexKept` 覆盖。）
- `scanIsMetadataOnlyAndNeverReadsFileContent`：25 文件扫描不打开任何流（`ContentAccess` 完全未被调用）、
  只取一次授权、`contentHash` 全空——首次扫描只索引元数据。

**复用证据**：既有 JVM 20+（增删改刷新、mtime/size 跳过、不可靠元数据、部分失败零删除、取消、
并发串行、malformed 容忍）；设备 `ScannerInstrumentedTest` 5（真实 DocumentsContract 走树/中途失权/
空游标/缺列容忍/进度）+ `SafInstrumentedTest` 6（读取/失权/消失/取消/拒绝/未知 scheme）。

**结论**：PASS（含上述矩阵与上限）。未验证：真机 25 行大矩阵与多资料库 UI 走查（本轮为 JVM 规则级 +
既有设备级证据）；上限值是规则常量，未做真实 500 文档 provider 的端到端性能测量。

---

## R3-03 — Markdown/TXT 全语法显示验收

**新增（`app/src/androidTest/.../ReaderSyntaxPageTest.kt`，5 例）**

- `headingsAndInlineStylesRenderAsSpansWithoutMarkers`：真实视图上断言 H1–H6 均为 `HeadingSpan`
  且文本无 `#`；段落含 `StrongEmphasisSpan`/`EmphasisSpan`/`StrikethroughSpan`/`CodeSpan` 且无 `**`/`~~`；
  `BulletListItemSpan`/`OrderedListItemSpan`/`BlockQuoteSpan`/`ThematicBreakSpan` 齐备；
  代码块 `codeForCopy()` == `val x = 1`；表格单元格逐个渲染（Compose 语义）。
- `taskListItemsKeepMarkerAndTextOnDevice`：**DEF-009 回归**（见下）。
- `unknownSyntaxKeepsTheBodyReadable`：`$$x^2$$`、mermaid 围栏、`[^note]` 均保留文本，页面可读。
- `brokenEncodingDegradesWithNoticeAndKeepsValidText`：损坏编码 → 降级 + 提示条 + 有效文本保留。
- `oversizedDocumentDegradesWithNoticeAndKeepsTheText`：>32KiB 单段 → `PLAIN_FALLBACK` 分块 + 静态提示。

**本轮发现的缺陷：DEF-009（P1，已修复）**

- 现象：任务列表条目生产页面渲染为**空条目**（`kind=LIST text=[] src=[]`），右侧可见文本却正常。
- 根因：`BlockSlicer.listItems` 以 `filterIsInstance<ListItem>()` 取列表项；Markwon 的任务项是
  `TaskListItem extends CustomBlock`，被过滤掉 → 整块切片为空 → 空 TextView（JVM shim 路径不受影响，
  因此此前只在设备路径暴露）。
- 最小修复：`listItems` 遍历任意子节点；按**类名**识别 Markwon 任务项并反射读 `isDone()` 加
  `[ ] `/`[x] ` 前缀（读不到不加，不猜测）；shim 的 `TaskMarker` 分支保持不变，两者不叠加。
- 证据：修复前 JVM 2 例红、设备 1 例红；修复后 JVM（`core:markdown`）全绿、设备 5/5 绿。
- 详见 [round-2-open-defects.md](round-2-open-defects.md) DEF-009。

**结论**：PASS（生产视图覆盖 §10/14/24 列出的语法；TXT/降级字面显示沿用 DEF-001 用例）。
未验证：代码高亮/表格内换行等观感层人工判定；任务项复选框 drawable 未做截图判定。

---

## R3-04 — 阅读位置、设置与模式持久化

**新增（`app/src/androidTest/.../ReaderPersistenceTest.kt`，3 类）**

- `ReaderPersistenceSeedTest.seedSettingsAndPosition`：用**真实磁盘 DataStore**（
  `files/r3-persist-datastore/r3-persist.preferences_pb`）写入字号/行距/字体/边距/显示模式/阅读模式/
  soft-wrap，并用**真实 Room**（`r3-persist.db`）写库与位置行（block 3 + anchor）。
- `ReaderPersistenceAfterRestartTest.settingsAndPositionSurviveProcessKill`：逐项精确比对上述值 +
  位置行；若本进程未先播种则 `assumeTrue` **跳过**（保证套件确定性，不被误判为通过）。
- `ReaderExternalEditTest.externalEditThenRefreshShowsNewContentAndReplansPosition`：真实 Room +
  真实 `DocumentSourceImpl`/`StorageReaderGateway`（仅 SAF `ContentAccess` 用替身）；位置行指纹过期
  → 回退锚点（`restoreBlock == 0`）并显示变化提示；外部编辑后 `refresh()` → 新正文出现在页面上。

**跨进程证据（真实杀进程，不是同进程重建）**

```text
adb install -r <app-full-debug.apk>; adb install -r <app-full-debug-androidTest.apk>
adb shell am instrument -w -e class io.leaflet.reader.ui.ReaderPersistenceSeedTest        → OK (1 test)
adb shell am force-stop io.leaflet.reader.debug
adb shell am instrument -w -e class io.leaflet.reader.ui.ReaderPersistenceAfterRestartTest → OK (1 test)
```

杀进程前落盘实证：`files/r3-persist-datastore/r3-persist.preferences_pb`（170 B）、
`databases/r3-persist.db`（65536 B）。注意：**必须用 `adb install -r`**；Gradle 的
`connectedAndroidTest` 任务会在两次运行间卸载应用并清空数据，用它做两阶段会导致假失败（本轮踩到并记录）。

**复用证据**：`ReaderStorageBoundaryTest` 的失权/消失错误态；`RoomInstrumentedTest` 的最近阅读与级联
删除（REQ-0003 AC-5/AC-6）；JVM `capabilitiesForFlavorDefaultsStandardModesBothFlavors` 与页测
`einkModeOpensSameDocument`（双 flavor 默认值/热切模式）。

**结论**：PASS（持久化用真实磁盘存储 + 真实进程重启）。未验证：真机手动改设置→杀进程→恢复的人工走查
（本轮为自动化等价路径）、TalkBack。

---

## 变更文件与源码固定（供独立验收核对）

```text
036501d26e2ba4e0893107db9ec5a943e5eaf56b181adc9a4f60d947d2ca07bf  core/markdown/src/main/kotlin/io/leaflet/markdown/MarkdownRenderer.kt
dd8d110d01b4c56bdf503bbc5ca8a0e901348ad76d2ce7a61fbca27aa618d079  core/markdown/src/test/kotlin/io/leaflet/markdown/MarkdownSamplesTest.kt
623e0089b68fe3d3863a4ee67d63dc1521285a5306d99a6dfe8c49d6e5c21ee8  core/storage/src/test/kotlin/io/leaflet/storage/scan/LibraryScannerTest.kt
3cd8dd1ae1603525bbb34a83964b78488148c2326f639c4668428279eaf1f555  app/src/androidTest/kotlin/io/leaflet/reader/ui/ReaderSyntaxPageTest.kt
（已作废，见下方「独立验收整改」的新 pin）
c4b34592c88feb7fddee9e4c023baa8fc4163d63de217916071b5db3d8f5c1c1  app/src/androidTest/kotlin/io/leaflet/reader/ui/ReaderLifecycleTest.kt
```

新增测试：R3-01 2 例（生命周期）、R3-02 4 例（JVM 资料库矩阵）、R3-03 5 例（语法页）、R3-04 3 例
（持久化/编辑）。产品代码改动：仅 DEF-009 的最小修复（`MarkdownRenderer.kt`）。

## 停止条件与交接

- 本轮按计划在 **R3-04** 结束：R3-05～10 未执行、未领取；未提交、未发布、未改签名/许可证。
- 独立验收前状态为「待验收」；请按下表优先级复核：DEF-009 修复（本轮新缺陷）→ R3-03 生产视图断言 →
  R3-04 跨进程流程（注意 `adb install -r`）→ R3-02 上限与矩阵 → R3-01 生命周期与 DEF-008 残余风险。
- 需要所有者决策/硬件的事项（R3-09/10）仍不在本轮范围。

---

## 独立验收整改（2026-09-20，回应 [r3-01-04-independent-review.md](r3-01-04-independent-review.md)）

结论采纳：R3-01/02/04 之前为 PARTIAL，本轮补上 DEF-010/011 要求的最小证据；R3-03 结论不变。

### 先修正报告自身的两处错误

1. **源码 pin 过期**：报告里的 `ReaderPersistenceTest.kt = 686b294f…` 是在该文件被拆成
   seed/after-restart 两个类**之前**取的，复验指出的 `215dfa87…` 正确。教训与规则：pin 必须在
   文件定稿后重取，任何后续编辑都要重新固定（本轮新 pin 见文末）。
2. **用例数口径**：XML 的 `tests` 是“已执行”数，跳过单列 `skipped`。R3-01～04 首轮为
   Full 40 executed + 1 skipped、E-Ink 40 + 1；不得表述为「41 项全部通过」。

### DEF-010 — 退出补存的可判别证据（已补）

- 新用例 `ReaderLifecycleTest.leavingTheRouteFlushesAPositionThatWasNotSavedYet`：
  在 2s 节流窗口内制造**尚未保存**的位置（`onVisibleBlock(4)`），先断言历史写入中**没有**地址 4
  （只有退出补存能产生它），再离开路由，要求出现新写入且**最终地址 = 4**。
- 反向验证：临时注释掉 `ReaderScreen.onDispose { viewModel.flushPosition() }` 后该用例
  **失败**（`ComposeTimeoutException: Condition still not satisfied after 20000 ms`），恢复后通过。
- 排查记录：最初用裸 `Thread.sleep` 轮询读不到写入——测试环境会把主线程上的协程推迟到规则泵帧时执行，
  改为 `compose.waitUntil { … }` 后稳定；这不是产品问题，已写进用例注释。
- 重建场景：`activity.recreate()` 后 `createComposeRule` 不会把组合重新挂到新实例（规则只组合第一个
  Activity），因此该用例移入独立类 `ZzActivityRecreationTest`（名字排最后，避免污染后续用例——
  实测污染会让后续用例报 “No compose hierarchies found”），断言**销毁时的补存**与 ViewModel 状态；
  「新实例页面重新挂载」仍由 DEF-011 的跨进程生产页面证据承担，不再用辅助断言冒充。

### DEF-011 — 生产接线 + 跨进程页面恢复（已补）

- 新套件 `ReaderProductionPersistenceTest.kt`（两个类）使用**生产存储标识**：
  DataStore 文件 `files/datastore/settings.preferences_pb`（对应 `preferencesDataStore(name = "settings")`）、
  Room `leaflet.db`；Phase 2 组合**真实 `ReaderScreen`** 并接上 `SettingsViewModel`（同一 DataStore），断言
  页面级恢复：真实条目 `TextView.textSize == 16 × fontScaleForStep(4) × scaledDensity`（60.9px，默认档为 48.3px），
  且存储块 4 的内容真的渲染在页面上（`Delta`）。
- **跳过条件改为独立标识**：两个持久化套件都改用专门的 marker 文件判断前置，不再用被验证的字号值；
  并新增 `-e leaflet.requireSeed true`：两阶段验收中前置缺失时**必须失败**（已实测：
  删除 marker 后 phase 2 报 `two-phase run requires the seed phase (marker missing)`）。
- **两 flavor 均执行 adb 两阶段**（`adb install -r` → `am instrument` → `am force-stop` → `am instrument`）：

```text
Full:  phase1 OK (1 test) → force-stop → phase2 OK (1 test)
E-Ink: phase1 OK (1 test) → force-stop → phase2 OK (1 test)
```

- 生产套件默认在**普通套件运行中跳过**（`-e leaflet.productionStores true` 才运行）：它们会删改应用自己的
  `leaflet.db` / `datastore` 目录，若与套件共享进程会破坏后续用例（本轮实测出现过
  “No compose hierarchies found” 连锁失败，已通过该门控隔离）。
- 仍未覆盖（明确保留）：生产 `MainActivity` 路由与**真实 SAF 树/provider**；文档字节由 `ContentAccess`
  缝替身从磁盘文件提供。

### 本轮环境与门禁现状（如实记录）

- 宿主机上同时运行 **2 个模拟器**（emulator-5554 为本项目记录的 AVD `dev_arm64_api35`，另有 emulator-5556），
  负载均值 ~14.5、可用内存 ~80MB。此状态下整包设备套件出现**间歇性** `No compose hierarchies found`
  （活动启动失败，测试体未执行），同一批用例**定向运行 14/14 全绿**：

```text
./gradlew :app:connectedFullDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=io.leaflet.reader.ui.ReaderLifecycleTest,\
io.leaflet.reader.ui.ZzActivityRecreationTest,io.leaflet.reader.ui.ReaderStorageBoundaryTest,\
io.leaflet.reader.ui.ReaderThreadingTest,io.leaflet.reader.ui.ReaderSyntaxPageTest,\
io.leaflet.reader.ui.ReaderPersistenceSeedTest,io.leaflet.reader.ui.ReaderPersistenceAfterRestartTest
→ 14 tests, 0 failures, 0 skipped
```

- JVM 门禁不受影响：588 variant / 276 unique，0 failure；`lint` / `ktlintCheck` 绿。
  **整包设备套件（双 flavor 全量）在环境恢复后需重跑**，本报告不把定向绿灯写成全量通过。

### 本轮新 pin（文件定稿后重取）

```text
c33179bfa125d34fb630a67d570d0b7a6c6a7f08cbff2f83666fb221d72ccd0e  app/src/androidTest/kotlin/io/leaflet/reader/ui/ReaderPersistenceTest.kt
c092b07a428f1baae0bf7b6681032c6baf149149e73561776cf20164266e1d99  app/src/androidTest/kotlin/io/leaflet/reader/ui/ReaderProductionPersistenceTest.kt
f98a3717fcf6cac856440d45bc08b2939a61b6dc15fa49aa124f041e4f7f269d  app/src/androidTest/kotlin/io/leaflet/reader/ui/ReaderLifecycleTest.kt
7b99e7e05fb9c1311cdbef57d851e45cf1b486ad8e99818da53d2911f14cc8f5  app/src/androidTest/kotlin/io/leaflet/reader/ui/ZzActivityRecreationTest.kt
9ae2e1315293042b27abf412c4346b675b070f7295ad9ed0815b3e9fed401f91  app/src/androidTest/kotlin/io/leaflet/reader/ui/PageHarness.kt
```

### R3-02 交接修正

按复验意见，R3-02 的**真实 provider 规模/多库 UI 与容量性能依据**不在本轮冒充完成：本轮只证明规则矩阵与
容量边界行为（500/5000 常量），性能预算归 **R3-08**，在 R3-02 记 PARTIAL/未验收项。

---

## 代码审查（2026-09-20）响应 — DEF-012 / DEF-013

[codereview 报告](code-review-2026-09-20.md) 的两个 P1 静态结论**均已在设备上复现并修复**：

| 缺陷 | 复现（修复前红） | 修复要点 |
| --- | --- | --- |
| DEF-012 切文档加载中补存混用 | `ReaderStateConsistencyTest.…`：`B must not be written while it loads: [5]` | 描述符绑定 `currentDescriptorId`；切文档清空上一文档快照；`flushPosition` 要求 Content 且 doc 属于 currentId；`persist` 只信本文件的描述符 |
| DEF-013 重查把位置拉回打开时地址 | `…sameDocumentRecheckDoesNotRemountTheReader`：重查后捕获的 `TextView` 已非同一实例 | 同文档重查不再写 Loading（内容保持挂载）；保存时同步 `Content.restoreBlock`，重挂载恢复到最新地址 |

- 两个用例均满足「修复前失败、修复后通过」，且修复后定向全绿；连带修正了两处测试同步
  （等待探针而非状态转换；先推进地址再记录），属修复改变可观察时序所致，非产品问题。
- 受影响既有类单独运行全绿：`ReaderDisplayTest` 6/6、`ReaderRecoveryTest` 14/14、
  `ReaderStorageBoundaryTest` 3/3、`ZzActivityRecreationTest` 1/1。
- 新增源码 pin：

```text
07e69c8a13311db419cee139dfc46eecd1c8cd055973d64ea4e39c6bbe9b5bbf  app/src/main/kotlin/io/leaflet/reader/ui/reader/ReaderViewModel.kt
9f9587933a2677cce4169ddba54b6a086e4efbc7ab5e0327a2a33ddb59d4eeb4  app/src/androidTest/kotlin/io/leaflet/reader/ui/ReaderStateConsistencyTest.kt
043dc684845604cd3a564592923e28f2db77ffd7c7942a625e30ad6bc9298fe3  app/src/androidTest/kotlin/io/leaflet/reader/ui/PageHarness.kt
48154defde7f614facd14b13cb54e27670188e330cc5290217f117b650094248  app/src/androidTest/kotlin/io/leaflet/reader/ui/ReaderStorageBoundaryTest.kt
eb5e37ab0968f7d30d5fba635a7cab114bafcd1708bd320fe5934c156b758d0e  app/src/androidTest/kotlin/io/leaflet/reader/ui/ZzActivityRecreationTest.kt
```

- 环境：宿主双模拟器、负载 ~13.5–14.5 下**大合并运行**会间歇性出现活动启动失败
  （`No compose hierarchies found`）；单独/小批量运行全绿。整包双 flavor 门禁待环境恢复后重跑。
