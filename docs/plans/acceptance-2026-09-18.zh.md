# Leaflet 完成状态验收 — 2026-09-18

[English](acceptance-2026-09-18.md) | 中文

## 结论

**工程基础通过本地构建门禁；M0 阅读闭环仅部分具备，M1 MVP 不通过验收。下一轮是缺陷收敛与集成验证，不进入 M2 新功能。**

T00–T13 均已有不同程度的文件或实现，但不能把“存在实现”“辅助函数单测通过”视为用户功能完成。正文富文本、本地图片显示、生产解析线程与扫描一致性存在阻塞问题。

本轮只检查代码、运行现有验证并编制计划；没有修改产品代码、测试或依赖。使用 verify-and-stop 工作流，验证集限于能够支撑本次验收的门禁与检查。

## 验证范围与证据

工作区：macOS，本地 Gradle Wrapper 8.13；唯一 adb 设备为 `emulator-5554`（Android 15 / API 35 ARM64），没有 E-Ink 实机。Git 仓库尚无提交，项目文件均未跟踪，不能提供 commit 对应的验收结论；以下只针对检查时的工作区。

| 检查 | 本轮结果 | 能证明什么 / 不能证明什么 |
| --- | --- | --- |
| `./gradlew :app:assembleFullDebug :app:assembleEinkDebug test lint ktlintCheck` | PASS；6s，430 tasks，5 executed / 425 up-to-date | 本地增量构建与已有检查通过；非干净环境构建，非全部测试重新执行 |
| JVM XML 报告 | 406 个 variant 测试记录，按 classname/name 去重为 204 个用例；failure/error/skipped 均 0 | 复用 Gradle 判定有效的当前结果；debug/release 重复不能算成不同用例 |
| `./gradlew :app:connectedFullDebugAndroidTest :app:connectedEinkDebugAndroidTest` | 命令成功，但无 app androidTest 源码、无用例报告 | **不能记为双 flavor UI 测试通过**；对应 Kotlin 编译 task 为 NO-SOURCE |
| `./gradlew :core:storage:connectedDebugAndroidTest` | PASS；设备 XML 15 tests / 0 failures / 0 errors / 0 skipped | Room/SAF/Scanner 的已有设备用例通过；不覆盖全部提交阶段故障或真实 App UI |
| Markdown 大文件设备测试 | 本轮未重跑；审阅历史报告及测试实现 | 使用 `createForJvmTests()` 与显式后台 dispatcher，不验证生产 Reader 页面、Markwon 样式或主线程行为 |
| Release/APK 分析 | 本轮未重跑；有 2026-09-18 历史体积报告 | 未签名候选包记录，不等于正式可发布产物 |
| E-Ink 残影、刷新、实机阅读 | NOT_RUN | 没有硬件，不作体验结论 |

构建输出还包含宿主 Gradle init 脚本注入仓库、SDK XML 版本和 Gradle 弃用警告；本轮构建成功，但干净 CI 的可复现性尚未证明。`ktlint` 只应用在 app，当前“格式检查通过”不覆盖 core 模块。

## 按原任务评估

| 原任务 | 验收判断 | 说明 |
| --- | --- | --- |
| T00–T01 | 文档交付存在；本轮未重新核验外部调研 | REQ、ADR、architecture、research 已建立；不可把历史版本论断视为本轮重新核实 |
| T02 | 本地工程门禁 PASS；跨环境验收未通过 | 双 flavor 可编译；仓库 JDK 绝对路径不适配 Ubuntu CI |
| T03 | 样本/生成器存在；生成一致性本轮未重跑 | 大文件历史报告不能替代生产页面验收 |
| T04–T05 | PARTIAL | SAF/数据库测试通过，扫描提交原子性和更新保留进度有缺口 |
| T06 | FAIL | 生产页面未接入富文本，解析线程与分块完整性存在问题 |
| T07 | PARTIAL | 位置/最近阅读已有实现，但历史指纹读取与缓存规则有问题 |
| T08 | PARTIAL | 翻页/TOC 有实现；锚点存在多套映射及文本匹配问题，缺页面级证据 |
| T09 | FAIL | 排版/代码交互存在，但表格仍是源码文本，不是原生表格渲染 |
| T10 | FAIL | 本地图片成功解码后只显示占位；错误恢复未贯通 |
| T11 | PARTIAL | 搜索辅助逻辑存在；实际渲染映射、取消与高密度匹配内存待验证 |
| T12 | PARTIAL | 策略与设置存在；完整生产页面/双 flavor 行为未有自动化证明 |
| T13 | 未完成 | 有 APK 与模拟器基线，缺真实 UI、可复现 CI、E-Ink 实机及发布条件 |

这些是验收判断，不直接把原 REQ 改成 DONE。原计划 `PLANNED`、README“无工程”、部分需求 READY 与实际实现已明显漂移。

## 主要发现

### F01 — P0：正文与表格没有接入原生 Markdown 渲染（已确认）

- `app/src/main/kotlin/io/leaflet/reader/ui/reader/ReaderScreen.kt:782`：ParagraphEntry 将 `block.text` 送入 Compose Text；`:640` 表格同样显示该字段。
- `core/markdown/src/main/kotlin/io/leaflet/markdown/DocumentParser.kt` 将源切片赋给块的 display/text。
- 全仓引用检查：`MarkdownRenderer.renderSpanned` 只有定义和测试调用，没有生产调用；`ReaderHostView` 也未挂到 App 阅读路径。
- 影响：解析出了 AST 不等于呈现了粗斜体、任务列表、表格等样式。architecture/reader.md 中“已升级 Spanned”的描述与实际路径不符。

### F02 — P0：生产解析仍运行于 Main scope，读取也没有真正内存上界（已确认结构；ANR/OOM 需运行复现）

- `ReaderViewModel.kt` 将 `viewModelScope` 传给 ReaderSession；`core/reader/.../ReaderSession.kt:132` 再次从该 scope launch，调用者外层的 IO launch 不会改变这个新协程的 dispatcher。
- 仅 describe/readBytes 使用 withContext(IO)；`:224` decode 与 `:226` parse 在退出 IO 后执行。parser 的同步 CommonMark parse 没有线程切换，yield 也不会把 Main 工作变为后台工作。
- `:208` 先 `readBytes()` 全量读入，再按 16 MiB 截取；这不是有界读取。解析缓存为 Map，未见容量淘汰，注释“single-entry”与实现不符。
- 历史 10 MB 多块解析约 599s；这只是历史模拟器 parser 测量，不能证明真实 Reader 无 ANR，更不能作为性能通过结论。

### F03 — P1：块切片与锚点映射不可靠（已确认算法缺陷，具体矩阵待回归）

- `MarkdownRenderer.kt:339` 通过可见文本子串反找源码，带 Markdown 格式的文本不一定在源码中连续出现。
- `:362` 在 4000 行后停止当前块源切片，没有为剩余内容创建明确的续块；超长多行单块存在内容遗漏路径。
- `DocumentParser.kt:285` 每个 TOC 项线性查找首个包含标题的块，同名标题映射到首项，且具有 O(H²) 路径。
- `TocNavigation` 另建 occurrence-aware 兼容逻辑，但仍用源码 contains 匹配；没有消除底层位置映射错误。不得再增第三套修补映射。

### F04 — P1：本地图片已解码但未显示（已确认）

- `ReaderScreen.kt:851` 的 DeviceImage.Ready 分支仍调用 `StaticImagePlaceholder`，没有绘制 bitmap。
- 图像占位说明不能算本地图片支持完成。需验证真实图片可见、旋转/重用后正确更新及 bitmap 生命周期。

### F05 — P1：扫描提交不原子，更新文档可能删除阅读位置

- 已确认：`LibraryScanner.kt:275` 的 commit 逐条 upsert/delete，未被整轮 Room transaction 包裹。中途取消/故障可留下部分提交，与“零写入、保留上次完整结果”声明冲突。
- 已确认：`DocumentDao` 使用 `@Insert(REPLACE)`，ReadingPosition 对 Document 为 CASCADE 外键；存在替换父行触发子行删除的风险，须用真实 Room 更新场景复现并修复，不能只依赖 FakeDao。
- 现有 15 个设备测试通过不覆盖这两个完整断言。

### F06 — P1：历史指纹、未知缓存与异常恢复契约未贯通

- 已确认：`ReaderWiring.kt:73` 读取位置后，从**当前 Document 行**构造 StoredPosition 指纹，未返回 ReadingPositionEntity 保存的历史 fingerprint。外部编辑后的 stale 判断失去依据。
- 已确认：`ReaderSession.kt:200` 允许 null == null 命中缓存，与“未知元数据不可信”契约冲突。
- 已确认：DocumentSourceImpl 将失权/消失映射为 StorageException（RuntimeException），ReaderSession 读取分支只细分 IOException/SecurityException；生产失权会落到 PARSE_FAILED，而非授权恢复状态。
- 待验证：重新授权同一已存在 Library 是否实际重新获取授权；更新后标题、recent 与位置是否一致。

### F07 — P1：解码与搜索边界未闭环

- 已确认：TextDecoder 手写 UTF-8 分支缺少过长编码、代理区及最大码点约束；`F4 90 80 80` 可生成超出 Unicode 上限的值并进入 `Character.toChars`，违背损坏编码降级要求。
- 已确认：`BlockPositionMap.locateQuery` 是无取消检查的同步循环，全部匹配一次性存入 List；外层 cancel 只能丢弃结果，不会终止正在执行的循环。
- 高重复内容下结果数与内存增长、真实 UI 高亮正确性需专项验证。

### F08 — P1：构建和验收证据不能移植到 CI

- `gradle.properties:6` 固定 macOS JDK 路径；GitHub Actions 使用 ubuntu-latest 且未覆盖该属性。配置不具备跨环境可执行性，本轮未实际运行远端 CI。
- app 没有单测/设备 UI 测试源码；空测试任务成功不能证明启动、模式切换、实际渲染与恢复。
- core 未应用 ktlint；“全项目格式门禁通过”的表述不成立。

### F09 — P2：交付文档与代码状态漂移

- README/AGENTS/旧计划仍含“无 Android 工程”；REQ 多为 READY/IN_PROGRESS，无验收证据总表。
- release.md 声称 release 使用 debug keystore，但实际构建配置未设置 release signingConfig，apk-size.md 记录为 UNSIGNED。
- ADR-0007 提到“仓库 Apache-2.0”，与许可证待定、无 LICENSE 冲突。须移除未经确认的项目许可断言。
- 设备矩阵中表格标 PASS 同时注明 walkthrough pending，不能接受为已验证。

## 后续动作

执行 [下一轮缺陷收敛计划](round-2-stabilization-plan.md)。先恢复真实阅读路径和数据一致性，再完成双 flavor UI 验证，最后更新功能状态与候选包报告。不得用本轮验收自动授权签名、提交、发布、依赖大升级或 Onyx 集成。
