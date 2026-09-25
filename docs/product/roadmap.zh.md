# 产品路线图

[English](roadmap.md) | 中文

> 来源：`docs/archive/init.md` §39（阶段划分与各阶段内容）、`docs/plans/M0-M1-implementation-plan.md` §1（目标与范围）、§8（无工期承诺）。
> 本路线图只做优先级与范围划分，**无日期承诺**：先完成 T00 与 T02，再依据工具链、渲染适配和设备条件评估剩余工作；任务数量不等于人日估算。

## M0 — Foundation（基础）

- 工程骨架：Gradle Wrapper、Kotlin DSL、统一版本目录、6 模块划分冻结（T01）、manual DI
- `AGENTS.md`、REQ / ADR 模板与索引、architecture 契约
- 双 flavor（`full` / `eink`）可构建、最小 Library / Settings、基础 DisplayProfile / MotionPolicy
- 基础闭环："添加目录 → 浏览 → 阅读 MD/TXT → 退出 → 恢复位置"（对应 T02–T07）
- 构建命令以 T02 对实际 Gradle tasks 验证后的结果为准，暂标"待 T02 验证"

## M1 — E-Ink MVP（可用的 E-Ink 发布候选）

- 可验收的 E-Ink MVP Alpha 发布候选（实际签名与对外发布独立于开发验收）
- MVP 必备能力（`docs/archive/init.md` §26）：目录 Library 管理与浏览、MD/TXT 阅读、标题提取、TOC、
  位置记忆与最近阅读、文内搜索、字号/行距/页边距/基础字体、双 DisplayProfile 与模式切换、
  viewport 翻页与连续滚动、手势区域、手动刷新、设置页、About
- 表格、代码块、相对图片/链接在 M1 提供**基础可用实现**（`docs/archive/init.md` §10/§15/§23/§24 已明确要求，
  见实施计划 §2）；M2 只改进其性能与体验，**不把 M1 的基础能力整体延期**
- E-Ink 策略全面接线：动画 / 平滑滚动 / overscroll 动效 / ripple / crossfade 关闭，
  通用刷新策略；不宣称做不到的硬件局刷/全刷能力，不接 Onyx SDK

## M2 — Reader Quality（阅读质量）

- 大文档体验、表格质量、代码块体验、相对图片、TOC 质量、搜索质量
- 这是 M1 基础能力的优化，**不替代 M1 基础能力**：M1 先达到"可用"，M2 再做到"好用"
- 性能基线与回归门禁在此阶段建立（可复测阈值确立后才设自动门禁）

## M3 — Full Android Edition（完整 Android 版打磨）

- Standard Android polish、可选动画、深色主题、常规 Android UX
- Full flavor 正式发行版
- E-Ink 约束不因 Standard 打磨而松动：动画代码仍须经 DisplayProfile / MotionPolicy 全局可关

## M4 — Search & Library Scale（搜索与资料库规模）

- 全库 FTS、大规模资料库、增量索引
- MVP 阶段不做全库搜索（见 `vision.md` 非目标）；M4 之前只做当前文档内搜索

## M5 — Advanced E-Ink Integration（深度 E-Ink 集成）

- Onyx EPD 集成、高级刷新控制、设备特定优化
- 以 ADR-0007（Proposed）为决策入口；厂商 SDK 证据不足前不集成、不写假实现
- 残影 / 刷新结论只能来自 E-Ink 实机，缺设备的矩阵行标 BLOCKED / NOT_RUN
