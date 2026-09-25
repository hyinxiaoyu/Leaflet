# REQ-0010 — 设置、显示模式与 About

[English](REQ-0010-settings-about.md) | 中文


Status: IN_PROGRESS
Priority: P0
Milestone: M0/M1

## 问题

Leaflet 有双模式（STANDARD/EINK）与双 flavor（full/eink）架构（`docs/archive/init.md` §3–6），排版、刷新、动效策略都挂在显示模式下。若没有统一的设置页与 About，用户无法切换模式、调整排版、了解版本与许可状态；若设置中出现无法兑现的硬件档位（如 Onyx UpdateOption 五档），则构成虚假承诺（`docs/research/onyx-epd.md` §5 明确禁止）。

来源：`docs/archive/init.md` §3（DisplayProfile）、§6（显示模式切换）、§26（MVP 必做含显示模式切换/设置页/About）、§47（License Policy）；`docs/plans/M0-M1-implementation-plan.md` T02/T12。

## 用户故事

作为需要在 Standard 与 E-Ink 间切换的读者，我希望在设置中切换显示模式、调整排版并在 About 查看版本和许可证状态，以便控制 App 行为并了解其真实能力。

## 范围

包含：

- 显示模式切换：STANDARD / EINK，经统一 DisplayProfile 生效，原则上不要求重启 App（`docs/archive/init.md` §3）。
- 排版设置入口：承载 REQ-0007 的字号/字体/行距/页边距/soft-wrap 开关（本 REQ 提供入口与持久化框架，具体行为见 REQ-0007）。
- 阅读模式切换入口：CONTINUOUS_SCROLL / PAGE_TURN（行为见 REQ-0005，本 REQ 提供入口）。
- About 页：应用名、版本、flavor 身份区分、第三方许可入口（`THIRD_PARTY_LICENSES`）、许可证状态声明。
- 许可证声明：项目许可证由所有者选定前，About/文档明确标注"许可证待定"，不擅自填写 MIT/Apache 等许可。
- applicationId：开发期使用临时 namespace（`io.leaflet.reader` / eink flavor 后缀，集中配置易替换）；正式 applicationId 待确认，设置/文档中不得宣称为最终值。
- AppCapabilities：由 flavor + 运行时环境决定功能可见性（显示/禁用/隐藏），设置页只展示可兑现能力。

## 非目标

明确不包含：

- Onyx 硬件刷新档位（UpdateOption 五档、GC 间隔、Fast Mode 开关）：MVP 不集成 SDK（`onyx-epd.md` §5），设置中不得出现。
- 普通 Android 专属动画调优、dynamic color、复杂主题编辑器。
- 账号、云同步、统计开关（MVP 明确不做，`docs/archive/init.md` §27）。
- tap-zone 可配置编辑器、完整手势系统（T08 停止条件）。
- 擅自选定开源许可证或生成/提交生产签名材料（实施计划 T13 明确禁止）。

## 用户体验

- 设置页分 Section：显示（模式切换）、排版（四项 + soft-wrap）、阅读（连续滚动/翻页）、关于（版本/flavor/许可）。
- full 与 eink 两个 flavor 均可热切换显示模式；eink flavor 默认 EINK，full flavor 默认 STANDARD（实施计划 T02）。
- 切换后即时生效；恢复（进程重建）后保持用户选择。
- E-Ink 下：动画、smooth scroll、overscroll 动效、ripple、crossfade 均符合关闭策略；无无限 loading 动画（静态文字/百分比/简单进度条）。
- 不可用能力（如 Onyx 硬件档位）不出现在 UI 中，而非灰色占位误导。

## 功能需求

- FR-1：设置页提供 STANDARD/EINK 切换，经 DisplayProfile/MotionPolicy 全局生效，Reader 不散落 `if (isEink)` 分支（架构约束）。
- FR-2：模式选择持久化（DataStore），进程重建后恢复；两 flavor 默认值不同但均可切换。
- FR-3：设置页提供排版入口，值语义与持久化满足 REQ-0007 FR-1–FR-4、FR-7。
- FR-4：设置页提供阅读模式切换（CONTINUOUS_SCROLL/PAGE_TURN），行为语义见 REQ-0005；切换保持当前位置。
- FR-5：About 页展示应用品牌名 `Leaflet`、版本、flavor 身份（Full/E-Ink 可区分）、第三方许可入口。
- FR-6：许可证未定期间，About 与 README/CHANGELOG 相关位置明确标注"许可证待定（TBD，待项目所有者确认）"，不得出现未经批准的许可名称。
- FR-7：临时 applicationId 集中配置、可替换；文档与 About 不得将其宣称为正式 ID。
- FR-8：设置页条目受 AppCapabilities 门控：flavor 不支持的能力不展示；Generic/NoOp EInkController 不得对应任何硬件刷新档位 UI。
- FR-9：E-Ink 策略接线：animationsEnabled、smoothScrollingEnabled、ripple、overscroll、crossfade、无限 loading 均受 DisplayProfile/MotionPolicy 控制并可验证关闭。
- FR-10：`requestFullRefresh`/`requestPartialRefresh` 在 MVP 的语义仅为"向系统请求重绘"，文档与 UI 不得宣称为硬件局刷/全刷（REGAL/GU/GC）。

## 验收标准

- AC-1：两 flavor 分别安装启动，About 显示不同应用身份；eink 默认 EINK，full 默认 STANDARD。
- AC-2：任一 flavor 内切换显示模式后即时生效（动效/滚动行为变化可观察），杀进程重启后选择保持。
- AC-3：E-Ink 模式下验证清单全过：无 smooth scroll、无 overscroll 动画、无 ripple（或极弱可声明）、无 crossfade、无限 loading 不存在（静态 loading 允许）。
- AC-4：设置页与全局文案搜索无 "UpdateOption"、"REGAL"、"Fast Mode"、"GC 间隔" 等硬件档位字样。
- AC-5：About 含"许可证待定"声明；全仓搜索无擅自填写的 `MIT`/`Apache-2.0` 作为本项目许可证的声明（第三方依赖许可清单除外）。
- AC-6：排版入口调整值与 REQ-0007 AC-1 一致（持久化）；阅读模式切换后位置保持（与 REQ-0005 联合断言）。
- AC-7：flavor 不支持的能力项不出现在设置页（以 AppCapabilities 矩阵断言，而非人工目检）。

## 边界情况

- 模式切换时正位于长文档中部：切换后位置保持，不跳回顶部。
- DataStore 损坏或缺失：回退到 flavor 默认值，不 crash，覆盖写回默认值。
- eink flavor 运行于非 E-Ink 手机：EINK 策略仍可用（通用系统刷新语义），不得检测机型白名单分支。
- 旋转、resume、前后台切换：模式与排版选择不变。

## E-Ink 考量

- EinkDisplayProfile：纯色背景、高对比文字、无半透明/阴影/gradient/blur、无 animated elevation、无 shimmer、无限 loading 禁止（`docs/archive/init.md` §21）。
- Loading 优先静态文字/百分比/简单进度条。
- MotionPolicy 全局可关闭动画；动画代码不散落 Screen（`docs/archive/init.md` §2）。

## Standard Android 考量

- StandardDisplayProfile 基础实现：Material 3 简洁风，允许轻微 ripple、少量 transition、正常滚动、dark theme；dynamic color 可后置。
- Standard 下同样不得出现硬件刷新档位 UI。
- Full flavor 完善是 M1 后的事，M1 只要求基础阅读正常（`docs/archive/init.md` §6）。

## 性能考量

- 模式切换不重建全文解析结果（复用已解析 block，仅重绑样式/策略）。
- 设置读写为异步 DataStore，不得阻塞主线程；切换过程无 ANR。

## 存储与数据影响

- 显示模式、排版值、阅读模式存 DataStore；卸载/清数据行为见 REQ-0011（原文不受影响）。
- 设置 schema 版本化、可迁移；不以清库掩盖设置丢失。
- 不存原文、不存图片副本（`docs/archive/init.md` §12 约束）。

## 测试计划

- 单测：AppCapabilities 门控矩阵；DataStore 默认值/读写/损坏回退；许可证待定文案存在性（防擅自填许可的文本断言）。
- instrumented：AC-1 双 flavor 身份与默认值；AC-2 切换+重启保持；AC-3 E-Ink 关闭策略清单；AC-6 排版/阅读模式联动。
- 文案扫描测试：AC-4/AC-5 的全仓关键字断言（硬件档位词、项目许可证误填）。
- E-Ink 实机：模式切换、菜单显示、resume、旋转记入 `eink-test-matrix.md`；无设备标 NOT_RUN。

## 依赖

- REQ-0004（显示策略与 flavor 架构：DisplayProfile/MotionPolicy/AppCapabilities/EInkController 定义方）。
- REQ-0005（阅读/翻页模式行为）、REQ-0007（排版行为）。
- T02（最小工程与双 flavor：设置/About 初版载体）、T12（本 REQ 验收任务）。

## 待确认问题

- 正式 applicationId：待确认（`docs/archive/init.md` §1、实施计划 §2），当前临时值（`io.leaflet.reader` / eink 后缀 `.eink`）不得视为最终；About 明确标注 temporary（T12 `about_app_id` 文案）。
- 项目许可证：待项目所有者选择（`docs/archive/init.md` §47、实施计划 §2/§8）；About + README + CHANGELOG 均标 TBD（T12 `about_license_pending` 文案 + `AboutScreen` 第三方许可入口），TBD 状态不阻塞开发。
- eink flavor 是否长期保留 Standard 切换：当前保留（实施计划 §2 要求两 flavor 均有基础切换，`AppCapabilities.supportsStandardMode = true` 双 flavor，T12 单测 `capabilitiesForFlavorDefaultsStandardModesBothFlavors` 锁定），锁定 E-Ink-only 需单独变更需求。
- Standard 下 soft-wrap 默认值与 dynamic color 引入时机：`supportsDynamicColor = false` 双 flavor（M0）；soft-wrap 未选值时 E-Ink 默认 ON、Standard 取存值（`effectiveCodeSoftWrap`，T09 行为，T12 未改）。
