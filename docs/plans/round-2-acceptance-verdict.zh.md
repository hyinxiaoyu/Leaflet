# Leaflet R2 Alpha 验收结论 — 2026-09-18

[English](round-2-acceptance-verdict.md) | 中文

> **2026-09-19 表格单元格修复后重汇总：** 重复单元格同时被标为当前匹配的问题已修复。高亮现在按位置映射（块范围与单元格范围求交，再转换为单元格局部坐标），不再猜字符串；块级坐标统一使用块可见文本。新增 JVM 5 项、生产设备视图 1 项回归，修复前全红（JVM 19 项/5 失败；设备 `expected:<1> but was:<0>`）。REQ-0008 AC-3 恢复为 PASS。DEF-003/006/007 CLOSED；见[表格单元格修复轮](round-2-open-defects.md)。DEF-008 仍为 FIXED_PENDING_VERIFICATION。当前门禁：JVM 576 variant / 270 unique，Full 30/30、E-Ink 30/30、Markdown 5/5、storage 16/16；模拟器 emulator-5554 上 `test lint ktlintCheck` 与四个 assemble 均通过（模拟器不是 E-Ink 实机）。

> **2026-09-19 历史独立复验说明：** 早期复验双 debug、JVM、lint、格式门禁通过，双 flavor 页测各 29/29，但因相同表格单元格高亮问题把 REQ-0008 AC-3 判为 PARTIAL。该 PARTIAL 已由上方修复取代。当时 DEF-006/007 重开、DEF-008 仍 FIXED_PENDING_VERIFICATION。保留历史证据，但单次复跑不能证明根因或全量验收完成。

> **2026-09-18 第二轮修订：** 独立复验重开 DEF-003～007 并新增 DEF-008（Activity 销毁时 Compose 越界崩溃）。通过生产路径证据复核并修复：真实 Room + SAF 错误态（DEF-005/006）、保序搜索高亮（DEF-003）、代码围栏显示/复制（DEF-004）、Compose snapshot 主线程门禁（DEF-008）。当时设备套件双 flavor 各 29 项，Markdown 5、storage 16；JVM 552 variant / 264 unique。原 DEF-008 崩溃未再出现，按偶发问题保留风险，不宣称完全关闭。详见 `round-2-open-defects.md`。

> **2026-09-18 早期修订（DEF-007）：** 部分 PASS 曾以模型断言或不完整走查作为生产路径证据。逐条复核后，将确认有缺陷的 AC 收窄为 PARTIAL/NOT_RUN，补充真实 TextView span/排版、真实旋转、TOC 点击、搜索输入/高亮和生产 Markwon 大文件路径。历史命令保留，但收窄其证明范围；相关行标为“R2-DEF revision”。E-Ink 残影/刷新无硬件证据，仍为 NOT_RUN。

R2-08 交付时，本文件是唯一 AC 级 verdict。每条 AC 必须映射到测试/命令/人工记录，并且只能是 PASS、PARTIAL、NOT_RUN；无覆盖不得判 PASS。环境为 emulator-5554（sdk_gphone64_arm64，Android 15/API 35）。R2-08 快照中四包通过，JVM 494 variant / 240 unique；app 双 flavor 页测各 12，storage 16、Markdown 5。当前数字见上方更新。没有 E-Ink 实机，残影/刷新一律 NOT_RUN。

**模拟器证据边界：** 只能证明代码路径按设计执行，不能证明 E-Ink 残影、刷新波形或真实灰度对比。

## 图例

- PASS：当前源码快照有测试/命令/记录支撑。
- PARTIAL：部分子项有证据，缺失项列出。
- NOT_RUN：未执行（通常缺硬件/手段），不代表通过。

## REQ-0001 — Folder-first Library

| AC | 判定 | 证据 / 缺口 |
| --- | --- | --- |
| AC-1 层级、空目录、不解析全文 | 小样本 PASS；精确 25 行矩阵 PARTIAL | JVM 发现/排序/过滤、空树成功等用例；设备嵌套树重扫 2 文件 + 大小写 `B.MD` + 空目录；走查 8 文档层级。20+5+3+1 精确矩阵未重跑。 |
| AC-2 重复添加 | PASS | Repository 去重与不重复申请授权两项 JVM 用例。 |
| AC-3 刷新反映增删改 | PASS | JVM 刷新规则；设备新增 c.txt、修改 B.MD、删除 a.md；另走查 long.md 删除。 |
| AC-4 mtime/size 规则 | PASS | 可靠元数据跳过、不可靠元数据重读 JVM 用例。 |
| AC-5 取消 ≤1s 且保旧索引 | PASS | `cancelCommitsNothingAndReportsCancelled`：1 秒内无写入、旧索引保留、状态 CANCELLED。 |
| AC-6 中途权限错误 | PASS | 设备真实 lister 中途失权不清旧索引；JVM 失败零写入/不清理用例。 |
| AC-7 移除只清本地行、原文不变、共享授权 | 设备 PASS | 真实 Room 级联删除 + checksum；共享/非共享授权释放 JVM 用例。 |
| AC-8 可观察标准 | PASS | 本表即判据。 |

## REQ-0002 — Markdown / TXT Reader

| AC | 判定 | 证据 / 缺口 |
| --- | --- | --- |
| AC-1 基础语法 | PARTIAL，R3-03 更新 | 原模型断言未证明源码切片语义；DEF-004 暴露转义/删除线/引用/列表问题。往返 JVM + 生产 TextView span 已补；第二轮补围栏/复制；R3-03 覆盖 H1–H6、行内样式、列表/引用/分隔线、复制/表格和降级。嵌套列表、任务复选框外观仍未审查。 |
| AC-2 未知语法降级 | PASS | `unknownSyntaxStaysReadable`、`malformedDegradesNeverCrashes`。 |
| AC-3 标题/frontmatter 回退 | PASS | `titleRuleFrontmatterH1Filename`、`brokenFrontmatterNeverFailsBody`。 |
| AC-4 BOM/空文件/多语言 TXT | 修订后 PASS | JVM BOM/空文件/字面文本/多语言；生产页确认 `# Not a heading` 与 `[not-a-link](nowhere) **literal**` 保持原文且不创建 Markwon AndroidView（DEF-001）。 |
| AC-5 损坏编码 | PASS | `brokenEncodingDegradesWithNotice`、`invalidUtf8SequencesBecomeReplacement`。 |
| AC-6 10 MB 取消/降级/无静默截断 | 修订后 PASS | `LargeFileInstrumentedTest` 5/5 改用生产 Markwon；10 MB 页面 Loading 6 秒后 Back 取消，无 ANR，报告 PSS 260 MB。 |
| AC-7 快切 A→B 与关闭流 | PASS | 快切 generation 守卫及取消释放流 JVM 用例。 |
| AC-8 可衡量 | PASS | 本表即判据。 |

## REQ-0003 — Reading Position

| AC | 判定 | 证据 / 缺口 |
| --- | --- | --- |
| AC-1 同块恢复（含杀进程） | PASS | JVM 块/偏移恢复；页测持久化/重开 block 3；force-stop 走查恢复到 26/56、45%（`r207-restore.png`）。 |
| AC-2 2 秒节流 + 退出补存 | PASS | `PositionThrottlerTest` 覆盖连续滚动、重复地址、恢复回显、首次观察和间隔；R3 后续增加可区分退出补存的页测。 |
| AC-3 外部修改回退/提示 | PARTIAL | JVM 指纹/anchor/progress 矩阵通过；缺真实设备外部修改后重开提示（走查覆盖删除）。 |
| AC-4 字号变化保上下文 | PARTIAL | JVM anchor 映射通过；缺设备字号变化后重开定位。 |
| AC-5 最近列表 | PASS | JVM MRU；走查倒序、相对时间、移除、清空和确认。 |
| AC-6 文件丢失/失权 | 第二轮后 PASS | JVM 保留死记录；生产页提示 gone/reauthorize；真实 Room + Reader gateway 边界测试覆盖。 |
| AC-7 旋转不重解析 | 修订后 PASS | 真实 Activity 配置变化断言同一解析文档/恢复块；未变化重开和横竖屏走查通过。 |
| AC-8 可衡量 | PASS | 本表即判据。 |

同一 ViewModel 重开会重读索引和指纹：未变复用 ParsedDocument；变更重载；缺失/失权显示正确恢复状态。生产页覆盖外部修改、删除、授权丢失和未变重开。

## REQ-0004 — Display Profiles

| AC | 判定 | 证据 / 缺口 |
| --- | --- | --- |
| AC-1 flavor 默认/切换/重启 | PASS | 双 flavor 安装，E-Ink 默认 EINK、Full 默认 STANDARD；即时切换和 DataStore 落盘；Profile JVM 用例。 |
| AC-2 E-Ink 关闭清单 | PARTIAL | 静态黑白页面与策略测试通过；缺 smooth/overscroll/ripple/crossfade/无限 loading 逐项检查表。 |
| AC-3 不直引 Onyx、策略集中 | PASS | main 无 Onyx 实现引用；控制器生命周期/能力分离用例。 |
| AC-4 无硬件档位 | PASS | 源码/资源无 UpdateOption、REGAL、Fast Mode、GC interval，除否定性注释；About 说明系统重绘。 |
| AC-5 双包身份和构建 | PASS | 两 flavor build 通过，badging 的 ID/名称不同。 |
| AC-6 可衡量 | PASS | 本表即判据。 |

## REQ-0005 — Page Turn

| AC | 判定 | 证据 / 缺口 |
| --- | --- | --- |
| AC-1 五次位移/overlap/像素误差 | PARTIAL | JVM 公式/范围/clamp（1000 px → 950 px）通过；缺设备尺测 ≤2 px。 |
| AC-2 首尾/十连翻 | PARTIAL | JVM clamp；TOC 落尾未越界；缺真实设备首屏左区、尾屏右区和十连翻记录。 |
| AC-3 手势仲裁 | PARTIAL | JVM 仲裁表、宽表截图；缺真实触摸、横滑和 500 ms 长按记录。 |
| AC-4 旋转/菜单遮挡 | PARTIAL | JVM 可见高/遮挡；旋转保位置；缺旋转位移和菜单落点测量。 |
| AC-5 模式切换保块 | PASS | ReadingMode/PageTurn 和页测用例；`keepBlock` clamp。 |
| AC-6 无 smooth scroll | PASS | 生产调用无 smooth API；`turnPage` 使用即时 `scrollBy`。 |
| AC-7 可衡量 | PASS | 未测项如实列为 PARTIAL。 |

## REQ-0006 — TOC

| AC | 判定 | 证据 / 缺口 |
| --- | --- | --- |
| AC-1 H1–H6/层级/frontmatter 与代码排除 | PASS | JVM 标题/连续标题/格式标题/子串标题用例；走查 25 行抽屉。 |
| AC-2 重复 anchor 稳定且分别落点 | PASS | `总结`、`总结-1`、`总结-2` 双跑稳定；真实抽屉点击并保存落点。 |
| AC-3 目标可见、≤4 px、保存 | PARTIAL | Section-10 走查到 20/56、34%，instant `jumpToBlock`；缺像素尺测。 |
| AC-4 无标题/TXT 占位 | PASS | JVM 空 TOC；sample.txt 走查；TXT 字面页测。 |
| AC-5 1000+ 标题 | NOT_RUN | 无大标题样本轮；映射单测不是性能证据。 |
| AC-6 可衡量 | PASS | 本表即判据。 |

## REQ-0007 — Typography, Tables, and Code

| AC | 判定 | 证据 / 缺口 |
| --- | --- | --- |
| AC-1 四设置跨进程保持 | PARTIAL | JVM 默认值/范围/回退通过；无设备修改四项→杀进程→恢复；现有页测使用内存 DataStore。 |
| AC-2 排版作用于已有视图 | 修订后 PARTIAL | DEF-002 后新增真实 TextView `textSize`、`lineSpacingExtra`、统一字号和 SERIF 断言；未逐档走完所有组合。 |
| AC-3 语言标签/精确复制 | PASS | JVM 标签与逐字复制测试。 |
| AC-4 E-Ink 默认换行/行尾可达 | PARTIAL | 默认策略/设置截图；缺长代码行真实横滚验证。 |
| AC-5 宽表末列/横滑仲裁 | PARTIAL | JVM 可达性、E-Ink 宽表截图；缺设备末列内容与仲裁记录。 |
| AC-6 无 WRAP/CARD 控件 | PASS | 源码/UI 无开关，仅枚举/注释。 |
| AC-7 灰度人工对比/截图 | NOT_RUN | 有黑白截图，但无人作出可读性结论。 |

## REQ-0008 — In-document Search

| AC | 判定 | 证据 / 缺口 |
| --- | --- | --- |
| AC-1 五处中文匹配/遍历 | 修复后 PASS | JVM 五匹配顺序；生产输入框 debounce 页测 `1 / 3` → `2 / 3`。 |
| AC-2 语法标记不命中、可见文本命中 | PASS | JVM 标记排除/可见文本测试。 |
| AC-3 代码/表格匹配与正确高亮 | 表格修复后 PASS | JVM 代码/表格搜索；设备断言真实 span 子串等于 `page-search-needle` 且步进后当前 span 正确。保序映射；表格按位置范围求交。5 个 JVM + 1 个设备回归修复前失败。 |
| AC-4 快速输入取消旧任务 | PARTIAL | JVM 过期 generation/取消通过；缺 200 ms、5 字输入的设备 profile。 |
| AC-5 切文档清状态 | PARTIAL | JVM generation 守卫；缺真实 A→B 页面清理轮。 |
| AC-6 无匹配 | PASS | JVM 空查询/无匹配不崩溃；Section 10 走查。 |
| AC-7 多语言/重复/不跨块 | PASS | JVM 日文、emoji、重复和不跨块矩阵。 |
| AC-8 10 MB 搜索 | PARTIAL | JVM cap 5000、稀疏完整、fallback 可搜；无设备 10 MB 搜索。 |

## REQ-0009 — Local Resources and Links

| AC | 判定 | 证据 / 缺口 |
| --- | --- | --- |
| AC-1 空格/中文/相对路径 | PASS | JVM 路径与 provider；走查空格、中文图片。 |
| AC-2 越树 `../../escape` | PASS | JVM escape/scheme/missing/library 用例；越界目标显示 MISSING。 |
| AC-3 跨文档重复 fragment | PARTIAL | JVM 归一化/首个重复标题；无真实跨文档点击。 |
| AC-4 同文档锚点 | PARTIAL | JVM missing/null 与归一化；无设备点击。 |
| AC-5 大图可见/内存有界 | PARTIAL | 页面真实绘制，JVM downsample；走查 16/256 PNG；数十 MB 峰值未测，12 MiB cap 未验证。 |
| AC-6 缺失/损坏/远程占位且无网 | PASS | 页测远程 loader 零调用，缺失提示；JVM 图片种类；走查截图。 |
| AC-7 HTTPS Intent/未知 scheme | PARTIAL | JVM scheme 分类；无真实 Intent stub。 |
| AC-8 无外部 handler | PARTIAL | 提示代码存在；无设备验证。 |

## REQ-0010 — Settings / About

| AC | 判定 | 证据 / 缺口 |
| --- | --- | --- |
| AC-1 flavor 身份/默认值 | PASS | 双 flavor 安装/badging 与默认模式；能力测试。 |
| AC-2 即时切换/持久化 | PASS | Full→EINK 视觉变化和 DataStore 落盘；E-Ink 页测。 |
| AC-3 E-Ink 关闭清单 | PARTIAL | 同 REQ-0004 AC-2；Settings 截图有排版/模式控件。 |
| AC-4 无硬件档位文案 | PASS | main 源/资源扫描零命中。 |
| AC-5 许可 TBD/无误填 | PASS | TBD 文案存在；main 源无项目 MIT/Apache 声明。 |
| AC-6 排版持久化/模式保位置 | PARTIAL | JVM 语义通过；缺设备修改后重启及位置联合验证。 |
| AC-7 能力矩阵页面断言 | PARTIAL | JVM capability/profile 分离；无完整页面级矩阵。 |

## REQ-0011 — Performance and Release Verification

| AC | 判定 | 证据 / 缺口 |
| --- | --- | --- |
| AC-1 样本/巨块消费 | PASS | 生成器 13 文件/51 MB 确定性；JVM 巨型段/代码边界；设备降级 5/5。 |
| AC-2 四档大文件“三不” | PASS | 设备 5/5；1 MB 生产页约 8 秒/PSS 122 MB；10 MB 6 秒 Loading 后可取消/PSS 260 MB，无 ANR。 |
| AC-3 可复测基线 | PARTIAL | 基线存在，冷启动 3–5 次；Library open/翻页/滚动/扫描为 NOT_RUN。 |
| AC-4 APK 组成 | PASS | R2-08 Full 22,566,877 B、E-Ink 22,566,617 B；dex 20.86 MB/96.9%，arsc 约 441 KB，so 约 73 KB，res 约 42 KB；无测试资源、零用户权限。 |
| AC-5 设备矩阵 | PARTIAL | 字段/项目齐，模拟器若干行；所有实机类别 NOT_RUN。 |
| AC-6 manifest 权限 | PASS | 双包零用户权限，只有 AndroidX signature 级 receiver permission。 |
| AC-7 原文件 hash 不变 | PASS | 设备 Room/SAF checksum；30 个样本前后 hash 一致。 |
| AC-8 双 release 与分层门禁 | 重汇总后 PASS | 双 release 新构建、test/lint/ktlint 通过；JVM 576 variant/270 unique，Full/E-Ink 30 each、storage 16、Markdown 5。早期数字保留为历史。 |

## 仍阻塞正式发布

1. E-Ink 实机残影/刷新/对比（仅硬件可测）。
2. 项目许可证、正式 applicationId、生产签名、分发渠道等所有者决策；本轮未加 LICENSE/signingConfig/密钥。
3. NOT_RUN：逐屏翻页尺测、1000+ TOC、大图峰值、10 MB 搜索手感、TalkBack。
4. 同 VM 删除后重开显示缓存的问题已由 DEF-005 修复，不再是 blocker。

## 候选条件

已由页测覆盖的真实路径包括 MD/TXT、已有视图重排、真实旋转、TOC 点击、真实搜索/高亮、删除/失权/外部修改后重开、点击区和图片占位/本地绘制，共 Full 30 + E-Ink 30。未覆盖路径仍按项 PARTIAL/NOT_RUN；不得概括为“全部通过”。页测检查真实 view tree、TextView 文本/spans/范围/字号/行距/typeface，不只查模型。数据安全有只读原文、移除 Library 不删文件、共享授权处理和 hash 证据。Release 明确标记未签名候选。

## 第二轮修订附录（2026-09-18）

环境 emulator-5554 / Android 15 API 35；app Full/E-Ink 各 29/29、Markdown 5/5、storage 16/16；JVM 552 variant/264 unique；test/lint/ktlint 与四个 assemble 通过。

| 缺陷 | 影响 AC | 修复/证据 |
| --- | --- | --- |
| DEF-009 任务项空行 | REQ-0002 AC-1 / 0007 | Markwon TaskListItem 是 CustomBlock；不再只过滤 ListItem，按类识别并补 `[ ]`/`[x]`。JVM 2 + 设备 1，修复前红。 |
| DEF-008 Compose 越界崩溃 | REQ-0011 AC-2/8 | 线程门禁观测 snapshot apply：修复前 3 次非主线程，后 0；两轮设备组合通过。原崩溃未复现，保留 FIXED_PENDING_VERIFICATION 风险。 |
| DEF-004 围栏/复制 | REQ-0002 AC-1 / 0007 AC-3 | 正文独立保存 + 围栏长度自适应；4 JVM + 1 页测，修复前失败。 |
| DEF-003 重复匹配映射 | REQ-0008 AC-1/3 | 保序映射；5 JVM 修复前全红 + 页级 span 断言。 |
| DEF-005 可达性 | REQ-0001 AC-3/6、REQ-0003 AC-7 | 增加 `probeAccess`，未知 fingerprint 不算新鲜；真实 Room/Reader 两例修复前失败。 |
| DEF-006 测试替身/边界 | 多份 REQ | Fake 覆盖四边界模式 + 真实 storage boundary + 线程门禁；页测 21→29。 |
| DEF-007 证据可信度 | REQ-0011 AC-8 | 本节重核范围；失败报告/复验探针/修复前红灯留在 defect log。 |

## DEF-007 证据范围修订（2026-09-18）

| 缺陷 | 影响 AC | 修订 |
| --- | --- | --- |
| DEF-001 TXT/fallback 二次解析 | REQ-0002 AC-4 | 页测断言生产页面字面文本且没有 Markwon view；旧模型级 PASS 收窄。 |
| DEF-002 已有 TextView 不更新排版 | REQ-0007 AC-2 | 新增真实 TextView 字号/行距/字体断言；逐档未走完，PARTIAL。 |
| DEF-003 高亮坐标错位 | REQ-0008 AC-1/3 | 真实 span 范围断言；JVM 锁定保序块映射、定位失败整块回退、表格位置区间求交。 |
| DEF-004 AST 往返语义变化 | REQ-0002 AC-1 | 修复转义/删除线/引用/列表/可见文本；页测仅标题/粗体，PARTIAL。 |
| DEF-005 重开绕过刷新/失权 | REQ-0003 AC-6/7 | 4 个页测覆盖编辑/删除/失权/不变；旧“已知取舍”作废。 |
| DEF-006 页测断言不足 | 多份 REQ | 页测双 flavor 12→21，覆盖真实 view/旋转/TOC/搜索；大文件改用生产 Markwon。 |

修订后仍未关闭：真实 E-Ink 残影/刷新/灰度、1000+ TOC 性能、翻页像素尺测、大图峰值、10 MB 设备搜索、跨进程排版持久化、TalkBack。

<!-- R2-08 验证日志：2026-09-18 test/lint/ktlint、双 flavor 设备页测、storage/markdown 及 release 构建结果详见英文主文档；未签名状态与 APK 数据见 apk-size.md。 -->
