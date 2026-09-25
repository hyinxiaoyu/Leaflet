# 产品愿景

[English](vision.md) | 中文

> 来源：`docs/archive/init.md` §1–§2（产品定位与阶段优先级）、§26–§27（MVP 范围与非目标）、§67（最终判断标准）；
> 范围解释：`docs/plans/M0-M1-implementation-plan.md` §1–§2。
> 本文档描述 WHAT / WHY，不描述 HOW（实现方式见 `docs/architecture/` 与 `docs/adr/`）。

## 产品定位

Leaflet 是一个面向 AI 时代大量本地文本资料的 **local-first、folder-first Android 阅读器**，
以 **Markdown 和 TXT** 为主要文档格式，并将 **E-Ink 阅读体验作为一等公民**。

展开说明：

- **Local-first**：原始文件始终是唯一事实来源。用户通过 ChatGPT、Claude、Codex、Gemini、
  各种 Agent、技术资料整理脚本生成的 Markdown / TXT 文件已经存在于用户设备目录中，
  Leaflet 直接阅读这些文件，不要求"导入成书籍"。数据库只保存索引、阅读进度、最近阅读、
  用户设置、搜索索引、UI 状态等辅助数据；删除 Leaflet 数据库后原始文件必须完全不受影响。
- **Folder-first**：用户"添加资料库"即选择一个 Android 目录，该目录成为 Library，
  保留原始目录结构，直接阅读其中的 Markdown / TXT。目录层级即资料库结构，
  不引入第二套"书架/书库"概念覆盖它。
- **MD + TXT 为主要格式**：第一版必须支持 Markdown 与 TXT 阅读（见 REQ-0002）。
  PDF / EPUB / CBZ 等版式或图书格式不在范围内。
- **E-Ink 一等公民**：E-Ink 不是一套黑白主题，而是一个平台（见 `principles.md` 原则四）。
  运行模式（STANDARD / EINK）与发行版本（full / eink）从 Day 1 分开设计，
  显示策略收敛到统一的 DisplayProfile / MotionPolicy 抽象，而不是在 UI 中散落
  `if (isEink)` 判断。

## 非目标（Non-goals）

以下方向明确不做，除非用户后续明确新增需求（`docs/archive/init.md` §27，实施计划 §1 重申）：

- Markdown 编辑器、笔记编辑能力
- 账号系统、云同步、自有云存储
- AI Chat、RAG、向量数据库、在线 AI
- PDF、EPUB、CBZ、手写笔记、OCR、复杂标注
- 双链知识库、Obsidian clone、插件系统
- WebDAV / Git client / Syncthing client、完整标签系统、在线商店
- 第一阶段的全库 FTS（全文搜索）：MVP 只做当前文档内文内搜索（REQ-0008），
  跨库全文检索是 M4 事项（见 `roadmap.md`）
- 复杂动画与富交互：第一版动画极少且可全局关闭（E-Ink 模式默认全关）

判断标准（`docs/archive/init.md` §67）：如果某项设计让 Leaflet 越来越像 Obsidian、Notion、
Markdown IDE、文件管理器或 AI Chat App，说明方向可能偏了。

## 核心主路径

Leaflet 最核心的体验必须始终是（`docs/archive/init.md` §67）：

```text
打开 App
    ↓
看到自己的资料目录
    ↓
点击文档
    ↓
舒服地阅读
    ↓
退出
```

复杂功能不能破坏这一条主路径。MVP 阶段（M0/M1）的全部工作都服务于让这条路径
稳定、快速、在 E-Ink 上舒适；主路径之外的任何功能都必须证明其在 E-Ink 上的成本
是合理的（见 `principles.md` 原则十）。

## 范围边界（M0/M1）

- M0：双 flavor 可构建工程 + "添加目录 → 浏览 → 阅读 → 恢复位置"基础闭环。
- M1：可验收的 E-Ink MVP Alpha 发布候选（实际签名与对外发布独立于开发验收）。
- 明确不以"架构预留"为由创建上述非目标功能的空壳实现。
- 表格、代码块、相对资源在 M1 提供基础可用实现，M2 改进性能与体验
  （实施计划 §2；路线图只做优先级划分，不把 M1 必备能力整体延期）。
