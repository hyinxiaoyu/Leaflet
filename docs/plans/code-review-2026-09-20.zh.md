# 阅读状态代码审查 — 2026-09-20

[English](code-review-2026-09-20.md) | 中文

结论：发现两个 P1 生产代码缺陷路径，尚不能认为阅读状态保存/恢复完整通过。均为静态调用链确认，设备端覆盖写入/跳动尚未独立复现。

本轮不是对全仓正确性的证明，也不是对正在并行补证的 DEF-010/011 的最终验收。记录期间其他执行者更新了缺陷文档，因此保留其改动，本报告单独承载新发现。

## DEF-012 — 切文档加载期间补存混用新 ID 与旧内容

状态 OPEN；P1；对应 R3-04 / REQ-0003。

- ReaderViewModel.startOpen（约 302 行）更新 currentId 为 B，却保留 A 的 currentDoc、currentDescriptor、lastVisibleBlock。
- flushPosition（约 408 行）没有 Loading/Failed 或 doc.documentId 与 currentId 一致性检查；persist（约 730 行）继续从全局 currentDescriptor 取指纹。
- ReaderScreen 的 ON_PAUSE/ON_STOP/onDispose 调用 flushPosition，生产导航共用 ReaderViewModel。A→B 加载未完成时退到后台，可构造出 B 的 ID + A 的位置/锚点/指纹，覆盖 B 的本地进度行。原始文档不受该路径修改。
- 另一个时序：旧保存任务已捕获 A 的 ID/doc，延迟执行 persist 时 currentDescriptor 已变成 B，指纹也可能混用。

回归要求：A 阅读至非零块，B 预存不同位置；阻塞 B 的 loadPosition/解析，open(B) 后触发 flush；比较 B 的位置行和指纹，不能接受 A 的数据。再测加载失败、快速切换、延迟保存。

最小修复方向：保存操作使用一致的 documentId/doc/descriptor/block 快照；加载中不得补存不完整快照；旧文档保存仍归属旧文档。修复前失败、修复后真实 Room 回归通过才能关闭。

## DEF-013 — 同文档新鲜度检查可能恢复到旧初始位置

状态 OPEN；P1；对应 R3-01/04 / REQ-0003。

- checkFreshnessThenKeep（ReaderViewModel 约 214–257 行）先保存旧 Content，再发送 Loading，指纹未变后恢复旧 Content。
- ReaderScreen 根据状态移除/重建 ReaderContent；列表状态在 ReaderContent 内 remember。Loading 至少显示一帧时，重新挂载后的 LaunchedEffect(docId) 按 state.restoreBlock 定位。
- restoreBlock 仅在 onLoaded 时初始化，后续滚动/TOC 保存更新 lastVisibleBlock/数据库，但不更新 Content 中该字段。
- 因此从第 2 块打开、实际已读到第 20 块时，一次较慢的同文档重查可能重新落在第 2 块。只断言 ParsedDocument 实例未变，无法发现该问题。

回归要求：实际页面滚动至与初始 restoreBlock 不同的位置；延迟 describe/probeAccess 让 Loading 可见；重开/重建后断言实际首可见块与偏移。文件失权/变更路径仍需正确。

最小修复方向：后台重查期间保留当前内容和滚动状态，或使用最新一致位置恢复；不要通过强制全文重解析解决。

## 验证与边界

运行 `./gradlew :app:testFullDebugUnitTest :app:testEinkDebugUnitTest :core:reader:testDebugUnitTest --console=plain -q` 成功，exit 0，包含增量复用。已有单测通过未覆盖上述时序与视图反例。

本轮未运行新设备场景，未修改产品代码或测试。后续优先补上述失败回归再修复；不要把静态确认的风险写成已经发生的用户数据丢失。

---

执行方响应（2026-09-20）：DEF-012/013 均已在设备上复现并修复，证据与源码 pin 见
[r3-01-04-execution-report.md](r3-01-04-execution-report.md)「代码审查（2026-09-20）响应」与
[round-2-open-defects.md](round-2-open-defects.md) 对应条目；本报告的原始结论与边界说明保留。

## 修复后独立复核 — 2026-09-20

结论：DEF-013 原始重挂载路径已修复；DEF-012 加载中补存已拦截，但异步保存快照子项仍未闭环，应保持 OPEN。

- ReaderViewModel SHA-256：`07e69c8a13311db419cee139dfc46eecd1c8cd055973d64ea4e39c6bbe9b5bbf`，与执行报告一致。
- 定向命令：`./gradlew :app:connectedFullDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=io.leaflet.reader.ui.ReaderStateConsistencyTest --console=plain -q`，exit 0。
- 设备 emulator-5556（verdant_t068_sit，API 35）；Full XML：2 tests / 0 failures / 0 errors / 0 skipped，2026-09-20T05:03:14 UTC。本轮不是全套或 E-Ink 验收。
- 已修复路径：切文档清空旧快照；flushPosition 要求 Content 且 doc ID 一致；同文档重查不再发送 Loading，原两个定向测试通过。
- **剩余 P1**：onVisibleBlock/jumpToBlock（约 396/421 行）在 IO 协程内部才读取全局 lastVisibleBlock。合法调度顺序：A 的保存入队 → open(B) 将 lastVisibleBlock 清零 → A 的保存开始，最终把 A 的位置写成第 0 块，而非发起保存时的位置。
- persist 仍在执行时读取或查询 descriptor，并在完成后写 currentStored；尚未形成完整不可变快照。保存任务应捕获对应 documentId/doc/descriptor/block，完成时不能污染后来文档的当前状态或节流器。
- 新回归先等待 A 保存成功再切换 B，所以没有覆盖延迟保存子项。需暂停保存任务开始执行，切换后释放，断言 A/B 的最终位置及指纹各自正确。
- 证据边界：剩余问题为静态代码时序确认，尚未独立设备复现。无须新增重复编号；DEF-013 不因该子项重复打开。

本轮仅更新审查记录；缺陷清单同期被执行者编辑，未覆盖其并发改动。独立验收结论以本追加记录为准，待执行方同步 DEF-012 状态。
