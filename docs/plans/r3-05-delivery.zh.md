# R3-05 交付 — 翻页、目录跳转、手势仲裁

[English](r3-05-delivery.md) | 中文

日期：2026-09-28
包：R3-05，见[初始 MVP 的剩余任务](remaining-mvp-tasks.md) (Remaining Tasks for the Initial MVP)
需求：REQ-0005（翻页/点击区域/模式）、REQ-0006（目录跳转）、REQ-0007（表格/代码可达性）、REQ-0004（模式切换）
状态：可在模拟器上以软件方式验证的标准已满足。硬件刷新/残影仍属于 R3-09。
独立评审：未执行（执行者自述）。

## 前置条件与入口

R3-03 的渲染以及 R3-04 的持久化/模式契约已由 R3-01–04 轮次交付
（`r3-01-04-execution-report.md`、`r3-01-04-independent-review.md`）。本包开始前，
共享阅读器路由已在无改动的情况下重新验证。

## 变更文件

| 文件 | 变更 |
| --- | --- |
| `app/src/androidTest/kotlin/io/leaflet/reader/ui/ReaderInteractionTest.kt` | 新增：12 个页面交互验收测试 |
| `app/src/androidTest/kotlin/io/leaflet/reader/ui/PageViews.kt` | 测量辅助函数：`textTopsByPrefix`、`textRectsByPrefix`、`composeTexts`、`horizontalScrollOffsets`、`scrollerBounds` |
| `app/src/main/kotlin/io/leaflet/reader/ui/reader/ReaderScreen.kt` | 缺陷修复 DEF-R3-05-01（一行：在点击处理器的 `pointerInput` 键中加入 `overlap`） |

## 验证

命令（emulator-5554，`dev_arm64_api35`，Android 15）：

```bash
./gradlew :app:connectedFullDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=io.leaflet.reader.ui.ReaderInteractionTest
```

结果：`Starting 12 tests` → `12/12 passed`，退出码 0。这些测试在 `ReaderStore`
接缝（`FakePageStore`）之上组合生产环境的 `ReaderScreen` + 会话/Markwon 解析器；
点击/滚动坐标通过真实触摸输入注入，并从组合后的树中读回（进度行、段落位置、
滚动轴范围）。

### 逐条标准结果

| 标准 | 结果 | 证据 |
| --- | --- | --- |
| 可见高度/重叠确实生效 | PASS | `turnDistanceMatchesTheConfiguredOverlapOnTheRealViewport`：在真实视口上实测 3% 与 8%；反推视口 `(d3-d8)/0.05`；`d3/V = 0.97`、`d8/V = 0.92`，均在 ±0.02 内 |
| 每次移动 / 连续五次翻页 | PASS | `fiveConsecutiveTurnsAdvanceWithoutLosingTheDocument`：五次翻页，可见块非递减，无文档丢失 |
| 十次重复操作 + 尾部钳制 | PASS | `tailClampStopsAtTheLastBlockAndFurtherTapsAreNoOps`（20 次向前点击，随后两次空操作点击，最后一块已组合） |
| 头部边界钳制 | PASS | `headClampStopsAtTheFirstBlock`（10 次向后点击到达块 1；再点一次为空操作） |
| 菜单/中央区域仲裁 | PASS | `centreTapTogglesTheMenuWithoutTurning`（真实中央点击打开/关闭生产菜单，进度不变） |
| 长按绝不翻页 | PASS | `longPressNeverTurnsThePage`（在下一页区域内的真实长按） |
| 连续模式侧边区域不响应 | PASS | `continuousModeSideZonesDoNotTurn` |
| 链接点击绝不翻页 | PASS | `linkButtonTapDoesNotTurnThePage`（真实 Compose 链接按钮点击） |
| 最终宽表格列可达 | PARTIAL | `wideTableHorizontalGestureReachesLastColumnWithoutTurning`：水平容器滚动到其 `maxValue`（屏幕上内容的末尾）且页面不翻页。一旦祖先滚动裁剪裁掉某个单元格，逐单元格的 `boundsInRoot` 探测就会报告为空，因此“最后一个单元格已绘制”的断言被替换为范围末尾测量 |
| 未换行的长代码行可达 | PASS | `longCodeLineHorizontalGestureReachesTheTailWithoutTurning`：滚动轴值走到 `maxValue`，并从 0 开始停止 |
| 目录目标落点 + 其后翻页 | PASS | `tocJumpThenTurnContinuesFromTheLandingBlock`（真实抽屉点击，随后一次翻页从目标处继续推进） |
| 旋转后重新计算 | PARTIAL | `rotationKeepsTheDocumentWithoutReparse` 断言文档实例与恢复块在旋转后仍然存在。`createComposeRule` 的宿主 Activity 会因方向变更而被销毁/重建，这会在该测试剩余时间内拆除组合树，因此无法就地测量旋转后的距离；该距离在每次翻页时从 `listState.layoutInfo` 读取，且 `ReaderDisplayTest.realRotationKeepsPositionWithoutReparse` 覆盖了该回归 |
| E-Ink 关闭动效清单 | PASS (JVM) | `core:eink` 的 `DisplayProfileTest` / REQ-0004 清单；本包未新增任何动画 |

## 发现并修复的缺陷

**DEF-R3-05-01 — 修改翻页重叠量对点击没有影响。**

- 复现：在页面处于 PAGE_TURN 模式时，分别测量 3% 和 8% 下的一次翻页 →
  两者都恰好移动了 2083 px（`turnDistanceMatchesTheConfiguredOverlapOnTheRealViewport`）。
- 原因：点击处理器以
  `Modifier.pointerInput(mode, tapWidthPx)` 安装，并捕获了闭包了
  `overlap` 的 `turnPage`。键集合相等的重组不会重启 `pointerInput`
  块，因此该处理器一直调用过期的闭包。
- 修复：将 `overlap` 加入键（`pointerInput(mode, tapWidthPx, overlap)`），
  并在 `ReaderScreen.kt` 中加注释说明。
- 回归：同一测试现在测得 3% > 8%，且两者都与 `viewport × (1 - overlap)` 相符。

## 未运行 / 限制

- 真实 E-Ink 设备：电子纸上的残影、对比度、硬件刷新以及快速翻页行为
  仍为 NOT_RUN（R3-09，需要物理设备）。
- 新测试类的 Eink flavor 模拟器运行属于 R3-08 门禁的一部分；该类与
  flavor 无关，且共享屏幕是同一份代码。
- 表格单元格“已在屏幕上绘制”的断言被替换为滚动范围末尾
  测量（原因见上述基础设施限制）。

## 下游影响

无加速器，无新依赖，无契约变更。这一行修复仅在页面打开时修改重叠量
的情况下改变点击行为（此前：在旋转/模式变更之前从不改变）。

## 停止条件

针对软件标准的页面交互证据已完整；硬件事项移交
给 R3-09 并保持未验证。按包顺序，接下来开始了 R3-06。
