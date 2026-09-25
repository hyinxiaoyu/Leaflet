# REQ-0011 — 性能基线、样本与发布验证

[English](REQ-0011-performance-release.md) | 中文


Status: IN_PROGRESS
Priority: P0
Milestone: M0/M1

## 问题

E-Ink 阅读器处理 AI 生成的大文档时，未经验证的性能假设等于没有承诺：整篇装入单 TextView 会 ANR/OOM（`markdown-renderers.md` R3），无基线的"流畅"无法验收，无体积记录的 flavor 会膨胀，无实机矩阵的刷新结论不可信。M1 必须建立可复测的性能基线、样本体系、双包体积记录与发布门禁，否则 Alpha 候选不能放行。

来源：`docs/archive/init.md` §7（APK 大小）、§48（Testing Strategy）、§49（E-Ink 测试）、§50（Performance Budget）、§51（大文档）、§62–63（交付）；`docs/plans/M0-M1-implementation-plan.md` T03/T13。

## 用户故事

作为需要在 E-Ink 上打开 10 MB 资料的读者，我希望大文档可打开、可取消且不会崩溃，同时 App 体积和启动耗时可接受，以便阅读大型资料时不丢进度或原文件。

## 范围

包含：

- 测试样本规格：100 KB / 1 MB / 5 MB / 10 MB Markdown 样本，覆盖"多小块"（大量小 block）与"单个巨大块"（单个巨大段落/代码块）两种形状；配套空文件、大 TXT、重复标题、BOM、损坏编码、中日英/emoji、超长行（`docs/archive/init.md` §48 样本清单）。
- 性能基线项：cold start、Library open、Document open、翻页/page turn 延迟、滚动延迟、大文档内存峰值、Library 扫描耗时、APK 体积（`docs/archive/init.md` §50）。
- 双包体积记录：`fullRelease` 与 `einkRelease` 字节数、构建参数、R8 配置、签名状态、依赖/native/font/image 组成（`docs/performance/apk-size.md`）。
- 实机矩阵：`docs/development/eink-test-matrix.md`（设备/Android 版本/屏幕尺寸/分辨率/固件/测试日期/Leaflet 版本 + 翻页残影/菜单/连续翻页/长按/返回/字体/TOC/长文档/代码/表格/图片/对比/resume/旋转）。
- release merged manifest 检查：无未经需求批准的权限或网络能力。
- 数据安全：卸载 App / 清数据库 / 移除 Library 后原文件不变。
- 无 E-Ink 设备时的诚实标记：相关行标 NOT_RUN/BLOCKED，保留发布风险，不得标通过。

## 非目标

明确不包含：

- 过早微优化：建立基线优先，不追逐未经证实的毫秒级优化（`docs/archive/init.md` §50"不必过早微优化"）。
- 以拒绝所有大文件代替阅读路径；以静默截断假装读取完成（实施计划 T06/T13 明确禁止）。
- 自动性能回归门禁：在"建立可复测阈值后才设门禁"之前，不设硬性 CI 失败阈值（实施计划 T13）。
- Onyx SDK 集成与硬件波形调优（M5 前不复议）。
- 为制造双包体积差异加入无用依赖（实施计划 T13 明确禁止）。

## 用户体验

- 大文档打开允许 loading/增量处理/降级横幅（如"文档过大，已切换纯文本模式"），用户可取消加载。
- 10 MB 文档不承诺完整富文本体验，但保证：不 OOM、不 ANR、不 crash（`docs/archive/init.md` §51、实施计划 §2）。
- 低端行为诚实：降级必须有说明，加载可取消，取消后回到稳定状态。

## 功能需求

- FR-1：`testdata/markdown/` 提供确定性样本生成脚本，可生成 100 KB / 1 MB / 5 MB / 10 MB 样本并校验大小；形状覆盖多小块与单个巨大块；测试资源不进入正式 APK。
- FR-2：样本覆盖 `docs/archive/init.md` §48 全清单：simple/headings/lists/tables/code/long/unicode/chinese/japanese/relative-links/relative-images/malformed/huge + 空文件、大 TXT、重复标题、BOM、损坏编码、超长单段/单代码块、嵌套列表。
- FR-3：性能基线记录 cold start、Library open、Document open、翻页延迟、滚动延迟、内存峰值、扫描耗时，每项注明设备/系统/固件/版本与样本，存 `docs/performance/` 下可复测模板。
- FR-4：双包体积分别记录 fullRelease/einkRelease 字节数、构建参数、R8 配置、签名状态、依赖/native/font/image 组成；初版大小接近是允许结果。
- FR-5：引入明显大型依赖时说明理由并记录体积影响（`docs/archive/init.md` §7、§52）。
- FR-6：实机矩阵覆盖普通手机 + 6"/7"/10.3" 目标类别；尺寸自适应可用模拟器辅助，残影/刷新结论只来自 E-Ink 实机；缺设备的行标 NOT_RUN/BLOCKED。
- FR-7：release merged manifest 检查通过：无未经需求批准的权限（尤其 WRITE_SETTINGS 类）或网络能力；远程图片不自动联网（与 REQ-0009 FR-9 联动）。
- FR-8：10 MB 样本打开：不 OOM、不 ANR、不 crash；允许 loading/增量/降级；加载任务可取消，取消后释放资源。
- FR-9：卸载 App / 清数据库 / 移除 Library 后，原 Markdown/TXT 文件内容不变（操作前后校验）。
- FR-10：交付报告（`docs/archive/init.md` §63）随 Alpha 候选提交；证据不足的 REQ 不得标 DONE；签名/许可证/正式 ID/实机欠缺时明确为候选包，不宣称正式发布。

## 验收标准

- AC-1：运行样本生成脚本，四档样本大小校验通过；"单个巨大块"样本存在且被 Reader 降级路径消费（单测/instrumented 断言降级横幅出现）。
- AC-2：四档样本在测试设备上逐个打开：无 OOM/ANR/crash；10 MB 允许降级，但必须有说明且内容可读（非静默截断）。
- AC-3：性能基线文档存在，每项含设备/系统/固件/版本/样本/三次以上可复测记录；无编造数字（无实测则标 NOT_RUN）。
- AC-4：`docs/performance/apk-size.md` 存在，含双包字节数、构建参数、R8、签名状态、组成分析。
- AC-5：`docs/development/eink-test-matrix.md` 存在，字段含设备/Android 版本/尺寸/分辨率/固件/日期/版本；测试项覆盖 §49 全清单（翻页残影/菜单/连续翻页/长按/返回/字体/TOC/长文档/代码/表格/图片/对比/resume/旋转）；无设备行标 NOT_RUN。
- AC-6：release merged manifest 检查记录存在：权限与网络能力逐项有需求依据；新增网络/权限无依据则门禁失败。
- AC-7：原文件不变性验证：对样本库执行移除 Library + 清数据库前后校验（hash 比对），文件一致；记录校验结果。
- AC-8：双 flavor release 构建成功（命令以 T02 验证为准）；单测/lint/格式检查通过；instrumented 按设备条件分别报告（JVM/真机/实机不互相替代）。

## 边界情况

- 损坏编码/BOM/空文件/超大 TXT：有明确提示或降级，不虚报自动识别（与 REQ-0002 编码策略一致）。
- 扫描中取消、权限丢失、文件消失：基线记录中注明，不把未扫描条目当删除（与 REQ-0001 规则一致）。
- 实机不可得：所有实机结论标 NOT_RUN，发布风险保留，候选包不升级为正式发布。
- 内存峰值测试设备差异：基线注明设备型号与内存，不跨设备直接比较。

## E-Ink 考量

- 实机矩阵是 E-Ink 结论唯一来源；Emulator 不能替代（`docs/archive/init.md` §49）。
- 翻页残影、快速连续翻页、黑白对比、旋转、resume 为必测项。
- Generic/NoOp 刷新语义诚实：只报系统重绘行为，不报硬件波形结论。

## Standard Android 考量

- Standard 侧同样记录 cold start 与体积；Full 包体积接近 E-Ink 包是允许结果，不得注水。
- Standard 动画存在时，性能基线注明模式（STANDARD/EINK）以便对比。

## 性能考量

- 本 REQ 即性能基线载体：阈值先记录、后设门禁；禁止 O(N²) 文档处理（`docs/archive/init.md` §50）。
- 大文档策略：loading + 增量处理 + fallback（`docs/archive/init.md` §51）；单 TextView 整篇装载禁止用于大文件（`markdown-renderers.md` R3）。
- 测试资源不进 APK，避免体积污染。

## 存储与数据影响

- 样本与基线文档存仓库 `testdata/` 与 `docs/performance/`，不进 release artifact。
- 数据库删除后原文不受影响是本 REQ 的硬验收（AC-7）；实现不得默认复制原文到内部存储（`docs/archive/init.md` §12）。
- 性能记录本身不写用户数据库。

## 测试计划

- 样本生成脚本自测：四档大小断言、形状断言（块数/最大块字节）、清单完整性断言。
- 单测/instrumented：AC-2 大文件三不（no OOM/ANR/crash）+ 取消释放；AC-7 文件不变性 hash 比对；AC-6 manifest 断言。
- 双包构建：`assembleFullRelease` / `assembleEinkRelease`（任务名以 T02 验证为准）+ APK 分析。
- 实机：`eink-test-matrix.md` 全项；缺设备 NOT_RUN。

## 依赖

- REQ-0001/0003/0010（T04 持久化：清库/移除语义的被测对象）。
- REQ-0002（T06 大文件降级路径：本 REQ 的被测行为）。
- T02（构建命令验证）、T03（样本交付）、T13（本 REQ 验收任务，复核全部 MVP REQ）。
- `docs/research/onyx-epd.md` §5（刷新语义边界）、`markdown-renderers.md` R1–R5（风险输入）。

## 待确认问题

- 性能阈值数字（如 Document open 上限 ms）：未验证，待 T13 在目标设备实测后设定，设定前不设自动回归门禁。
- 目标 BOOX Android 版本基线：未验证（待真机矩阵）。
- Markwon/APK 体积实测值：未验证（待 T13）。
- 大文件分块阈值与帧率/内存数字：未验证（待 T03 样本 + T13 基线）。
- 正式签名、许可证、正式 applicationId：待项目所有者确认，不阻塞基线工作。
