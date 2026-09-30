# R3-06 交付 — 链接、图片与系统边界

[English](r3-06-delivery.md) | 中文

日期：2026-09-28
包：R3-06，见[初始 MVP 的剩余任务](remaining-mvp-tasks.md) (Remaining Tasks for the Initial MVP)
需求：REQ-0009（本地资源、链接、外部交接）
状态：可在模拟器上以软件方式验证的标准已满足，包括真实 provider 的图片管线；条目在旋转/销毁时的回收仍未测量。
独立评审：未执行（执行者自述）。

## 变更文件

| 文件 | 变更 |
| --- | --- |
| `app/src/androidTest/kotlin/io/leaflet/reader/ui/ReaderResourceTest.kt` | 新增：真实页面上的 9 个链接/外部/图片验收测试 |
| `app/src/androidTest/kotlin/io/leaflet/reader/ui/ReaderImagePipelineTest.kt` | 新增：在真实 provider 之上验证 `LibraryImageLoader` 的 6 个测试 |
| `app/src/debug/kotlin/io/leaflet/reader/ui/StubImageProvider.kt` + `app/src/debug/AndroidManifest.xml` | 仅 debug 的真实 `ContentProvider`，提供生成的 PNG 字节（绝不进入 release） |
| `core/storage/src/test/kotlin/io/leaflet/storage/saf/DocumentSourceImplTest.kt` | 新增用例：经真实 DAO 解析中日韩/空格/百分号编码/`../` 路径 |
| `app/src/androidTest/kotlin/io/leaflet/reader/ui/PageHarness.kt` | `FakePageStore.relativeResolution`（在存储接缝处模拟树内解析） |
| `app/src/main/kotlin/io/leaflet/reader/MainActivity.kt` | 缺陷修复 DEF-R3-06-01（`launchExternalUrl`：启动前先解析，报告缺少处理程序） |
| `app/src/main/kotlin/io/leaflet/reader/ui/reader/ReaderScreen.kt` | 缺陷修复 DEF-R3-06-02（同文档锚点跳转的 effect 键） |

## 验证

```bash
./gradlew :app:connectedFullDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=io.leaflet.reader.ui.ReaderResourceTest,io.leaflet.reader.ui.ReaderImagePipelineTest
./gradlew :core:storage:testDebugUnitTest
```

结果：`ReaderResourceTest` `9/9 passed`、`ReaderImagePipelineTest` `6/6 passed`，退出码 0
（emulator-5554，`dev_arm64_api35`）；解析器用例在 JVM 套件中通过。

### 逐条标准结果

| 标准 | 结果 | 证据 |
| --- | --- | --- |
| 外部 HTTPS 交接（真实点击） | PASS (page) | `externalLinkTapHandsTheHttpsUrlToTheOs`：生产链接按钮 → `onOpenExternal("https://example.com/page?a=1")`，文档不变 |
| 无处理程序时的行为 | PASS (page) | `missingExternalAppShowsTheRecoverableNotice`：可恢复提示 `没有可打开该链接的应用` 渲染出来，且文档得以保留 |
| 跨文档片段（`other.md#anchor`） | PASS | `crossDocumentFragmentLinkOpensTheTargetAndLandsOnTheAnchor`：生产环境的 `followLink` 解析原始目标，调用 `onOpenDocument(other.md)`，待处理片段被消费，锚点正文出现在页面上 |
| 同文档片段 | PASS | `duplicateHeadingAnchorLandsOnTheFirstMatch`：`#overview` 跳转落在两个重复标题中的第一个（落点块位于第一与第二个标题之间） |
| 未知锚点 / 目标缺失时保留上下文 | PASS | `unknownAnchorShowsNoticeAndKeepsTheContext`（`本文中没有该锚点，位置保持不变`）以及 `outOfTreeRelativeLinkIsRejectedWithNotice`（`目标不存在或超出资料库范围`）；两者中 `ParsedDocument` 实例均不变 |
| 含中日韩字符的相对路径到达资源层 | PASS (page) | `relativeImageWithCjkPathReachesTheLoader`：`资料/图片名.png` 未经改动地到达加载器 |
| 空格 / `./` / 树内 `../` / 百分号编码路径 | PASS（解析器） | `DocumentSourceImplTest.resolveCjkSpaceAndEncodedReferencesThroughTheRealDao` 经真实 `DocumentSourceImpl` + DAO 解析 `空 格.md`、`./%E7%A9%BA%20%E6%A0%BC.md`、`../顶层.md`，并拒绝越树的 `../../顶.md`。页面级点击此类链接仍未覆盖 |
| 通过真实 provider 读取/采样/绘制本地图片 | PASS | `ReaderImagePipelineTest`：真实 provider（`StubImageProvider`，仅 debug）经实时 `ContentResolver` 提供生成的 3000×2000 PNG；加载器执行一次 bounds 读取 + 一次采样解码，返回 375×250，解码像素与 provider 颜色一致 |
| 超大图片保持在解码预算内 | PASS | 解码结果 375×250 = 375,000 B，位于 `ImageSample.MAX_DECODE_BYTES`（12 MiB）之内；加载期间进程峰值 PSS 为 49,455–49,644 KB |
| provider 缺失 / 授权被撤销 | PASS | `missingAndRevokedStreamsFailAsMissingWithoutCrashing`（两者都映射为 `MISSING`，不崩溃） |
| 取消加载（未开始 / provider 打开途中） | PASS | `cancelledLoadNeverTouchesTheProvider`（provider 打开次数为零）与 `cancellationDuringTheProviderOpenPropagates`（provider 仍在移交流时抛出 `CancellationException`） |
| 本地图片生命周期（旋转/销毁时回收） | NOT_RUN | `ImageEntry` 在 dispose 时回收（`ReaderScreen.kt`），但没有测试在解码出位图后驱动旋转/销毁；加载期间的峰值内存已测量 |
| 远程图片绝不加载并显示占位符 | PASS | `remoteImageShowsStaticPlaceholderWithoutLoading`：页面上出现占位符说明文字，加载器调用次数为零 |
| 缺失本地图片占位符 | PASS | `missingLocalImageShowsPlaceholder`（`图片缺失`） |

## 发现并修复的缺陷

**DEF-R3-06-01 — “没有外部应用”的提示在生产环境中无法到达。**

- 复现：`MainActivity` 将 `startActivity(ACTION_VIEW)` 包在 `runCatching` 中，
  这会吞掉 `ActivityNotFoundException`；页面只有在启动器抛出异常或缺失时才
  报告该提示，因此没有浏览器的设备会静默丢弃这次点击。
- 修复：`launchExternalUrl(url)` 先探测 `resolveActivity(packageManager)`，
  当没有应用能处理时（或启动仍然失败时）返回 `false`，调用方报告
  `reportNoExternalApp()`。
- 回归：`missingExternalAppShowsTheRecoverableNotice` 用会报告失败的
  启动器驱动真实页面，并断言提示 + 文档不变。Activity 侧的分支本身
  是代码评审而非仪器测试覆盖的（它需要一个拥有可控 `PackageManager` 的宿主
  Activity）。

**DEF-R3-06-02 — 同文档锚点链接从不滚动。**

- 复现：`duplicateHeadingAnchorLandsOnTheFirstMatch` 在点击 `#overview`
  链接（`followLink` → `notifyLinkJump` → 待处理跳转）后落在块 1。
- 原因：屏幕在
  `LaunchedEffect(docId, state.linkNotice)` 中消费待处理跳转。成功跳转不会改变任何键（只有
  被拒绝时才会设置 `linkNotice`），因此该 effect 从不重新运行。
- 修复：同时以 `state.restoreBlock` 作为该 effect 的键——`jumpToBlock` 会移动恢复
  地址，而后者正是跳转时发生变化的可观察量。
- 回归：锚点测试现在断言落点块。

## 未运行 / 限制

- 已解码位图下旋转/销毁时的图片条目生命周期：NOT_RUN（仅对条目的 `onDispose`
  回收路径做了代码审查）。
- 页面级点击中日韩/空格相对链接：NOT_RUN（上面的证据在解析器层）。
- 仅 debug 的 `StubImageProvider` 位于 `app/src/debug`，并已验证不出现在合并后的
  release 清单中；它是测试基础设施，不是随包行为。
- 新测试类的 Eink flavor 运行：属于 R3-08 门禁的一部分。

## 停止条件

在伪造存储接缝所允许的范围内，本地资源与系统边界已验证；provider 层面的图片工作
记录为待办事项，而非被宣称完成。
接下来开始了 R3-07。
