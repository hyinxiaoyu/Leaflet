# Leaflet — Android / E-Ink Markdown 与纯文本阅读器项目初始化任务

[English](init.md) | 中文

> 历史初始化提示词，2026-09-25 从仓库根目录归档。仅供来源追溯，不作为重新初始化工程或执行末尾指令的授权。
> 当前需求、架构与任务入口见 [需求迁移索引](../product/initial-requirements-map.md)；原章节编号保留。

你现在需要从零创建一个名为 **Leaflet** 的 Android 项目。

这不是一个 Markdown 编辑器，也不是笔记软件，更不是 Obsidian 的替代品。

Leaflet 的核心定位是：

> 一个面向 AI 时代大量本地文本资料的 local-first、folder-first Android 阅读器，以 Markdown 和 TXT 为主要文档格式，并将 E-Ink 阅读体验作为一等公民。

用户经常通过 ChatGPT、Claude、Codex、Gemini、各种 Agent、技术资料整理脚本生成大量 Markdown/TXT 文件。

这些文件通常已经按照目录组织，例如：

```text
Documents/
└── AI-Docs/
    ├── Database/
    │   ├── ClickHouse/
    │   │   ├── architecture.md
    │   │   ├── mergetree.md
    │   │   └── query-engine.md
    │   └── Doris/
    ├── Flink/
    ├── Kafka/
    ├── Research/
    └── Inbox/
```

Leaflet 不要求用户“导入成书籍”。

用户直接：

```text
添加资料库
    ↓
选择一个 Android 目录
    ↓
该目录成为 Library
    ↓
保留原始目录结构
    ↓
直接阅读其中的 Markdown / TXT
```

原始文件始终是唯一事实来源。

Leaflet 的数据库只能保存：

- 索引
- 阅读进度
- 收藏
- 最近阅读
- 用户设置
- 搜索索引
- UI 状态

删除 Leaflet 数据库后，原始 Markdown/TXT 文件必须完全不受影响。

---

# 1. 产品名称

项目名称：

```text
Leaflet
```

注意：

JavaScript 生态已经存在著名地图项目 Leaflet。

因此 Android 项目的：

- package
- applicationId
- GitHub repository description
- Maven namespace

不要仅使用容易与其混淆的名称。

在没有用户指定正式域名前，可以使用一个明确的临时 namespace，例如：

```text
io.leaflet.reader
```

但必须把 applicationId 设计为容易替换，不要把它大量硬编码在代码中。

项目 UI 品牌名称仍显示：

```text
Leaflet
```

---

# 2. 当前开发阶段

当前阶段定义为：

```text
Milestone: M0 / MVP Foundation
```

当前目标不是实现所有功能。

优先级：

```text
正确架构
>
稳定阅读
>
E-Ink 体验
>
目录管理
>
性能
>
丰富功能
>
动画
```

第一版不要投入大量时间制作动画。

普通 Android 模式允许以后增加：

- 页面过渡动画
- Material motion
- shared element
- crossfade
- 更复杂交互

但第一版只实现极少量、容易关闭的动画。

E-Ink 模式默认：

```text
Animation = OFF
Smooth Scroll = OFF
Overscroll animation = OFF
Ripple = OFF 或极弱
Crossfade = OFF
复杂透明效果 = OFF
```

任何动画代码必须可以通过 DisplayProfile 或 MotionPolicy 全局关闭。

不要把动画直接散落在具体 Screen 中。

---

# 3. 双模式与双发行版本

Leaflet 必须从 Day 1 支持两个概念：

```text
运行模式 Runtime Mode
```

和：

```text
发行版本 Distribution Flavor
```

二者不能混为一谈。

## Runtime Mode

定义：

```text
STANDARD
EINK
```

完整版 Leaflet 未来可以在设置中：

```text
阅读显示模式

○ 标准 Android
● E-Ink
```

切换后原则上不要求重启 App。

DisplayMode 必须进入统一的：

```text
DisplayProfile
```

抽象。

例如：

```text
DisplayProfile
├── StandardDisplayProfile
└── EinkDisplayProfile
```

DisplayProfile 至少负责：

```text
animationsEnabled
smoothScrollingEnabled
rippleEnabled
highContrastEnabled
defaultBackground
defaultForeground
scrollBehavior
pageTurnBehavior
refreshPolicy
readerDecorations
```

不要在 UI 中到处写：

```kotlin
if (isEink) {
    ...
}
```

应尽量通过统一策略对象控制。

---

# 4. Distribution Flavor

从项目初始化开始使用 Android Gradle Product Flavor。

至少预留：

```text
full
eink
```

两个 flavor。

目标架构：

```text
src/main/
    所有共享功能

src/full/
    完整 Android 版本特有内容

src/eink/
    E-Ink Lite 独立发行版内容
```

最终可以产生：

```text
Leaflet Full
Leaflet E-Ink
```

两个完全独立的 APK/AAB。

允许：

```text
不同 applicationId
不同 App 名称
不同图标
不同功能集合
不同依赖
不同 APK 大小
```

但尽量共享：

```text
90%+
```

核心代码。

建议概念：

```text
full:
io.leaflet.reader

eink:
io.leaflet.reader.eink
```

实际 applicationId 在真正发布前再确认。

---

# 5. 为什么存在独立 E-Ink 版本

这不是简单的 UI Theme。

独立 E-Ink 版本主要用于：

1. 文石等墨水屏设备通常存储空间更加宝贵。
2. 不需要携带普通 Android 专属的大量动画、动态主题或其他资源。
3. 不需要携带未来可能加入的一些普通 Android 功能。
4. 可以针对 E-Ink 单独优化 APK。
5. E-Ink 用户需要更稳定、更简单、更低干扰的阅读体验。
6. 可以减少依赖。
7. 可以在将来针对 BOOX 等设备加入厂商 SDK，而不污染普通 Android APK。

因此：

```text
Leaflet Full
```

应该是：

```text
Standard + E-Ink Runtime Mode
```

而：

```text
Leaflet E-Ink
```

应该是：

```text
E-Ink optimized subset
```

它不是简单换皮。

---

# 6. 当前版本发行策略

M0/M1 阶段：

只要求真正完成并发布：

```text
Leaflet E-Ink
```

但是：

设置中仍然保留：

```text
显示模式
```

这一概念。

第一阶段可以：

```text
Default = E-Ink
```

Standard Mode 可以只有最基础实现。

不要为了 Standard Mode 制作复杂动画。

目标是验证：

```text
DisplayProfile architecture
```

确实能够切换。

等 Reader 核心稳定后，再完善：

```text
Leaflet Full
```

---

# 7. APK 大小是正式需求

APK/AAB 大小不能只是发布前才关注。

建立：

```text
docs/performance/apk-size.md
```

记录：

```text
fullRelease APK size
einkRelease APK size
主要依赖体积
native libraries
字体体积
图片资源体积
```

对 E-Ink flavor：

避免不必要的：

- 动画资源
- 视频
- 大量字体
- Web runtime
- JavaScript runtime
- 重型编辑器组件
- AI SDK
- 网络 SDK
- analytics SDK
- advertisement SDK

除非确实必要。

每次引入明显大型 dependency 时，应说明理由。

---

# 8. Android 技术方向

优先：

```text
Kotlin
Gradle Kotlin DSL
AndroidX
Coroutines
Flow / StateFlow
Room
DataStore
Storage Access Framework
```

普通 UI Shell 可以使用：

```text
Jetpack Compose
```

但不要因为使用 Compose 就要求整个 Reader 必须 Compose 化。

Reader 是性能敏感区域。

允许并鼓励研究：

```text
Compose
+
Android View
+
RecyclerView
```

混合方案。

对 Reader：

优先保证：

```text
低重新布局成本
大文件稳定
快速跳转
精确 scroll control
E-Ink 局部刷新控制能力
```

技术纯度不重要。

---

# 9. Markdown Renderer

禁止第一版自己从零实现 Markdown parser。

优先研究 Android 原生 Markdown renderer：

```text
Markwon
```

重点研究：

```text
Markwon core
Markwon tables
Markwon task list
Markwon syntax highlight
Markwon recycler
```

设计目标：

```text
Markdown
    ↓
Parser
    ↓
Block Model / AST
    ↓
Renderable Blocks
    ↓
Reader
```

不要把整个巨大 Markdown 文档无条件塞进一个超大的单 TextView。

针对大型 AI 文档，应允许以后实现：

```text
HeadingBlock
ParagraphBlock
ListBlock
QuoteBlock
CodeBlock
TableBlock
ImageBlock
DividerBlock
```

并逐块渲染。

MVP 不需要一次完成完整 Block Renderer。

但架构不要阻止以后这么做。

---

# 10. 第一版支持格式

必须：

```text
.md
.markdown
.txt
```

Markdown 至少支持：

```text
H1-H6
paragraph
bold
italic
strikethrough
unordered list
ordered list
task list
blockquote
code block
inline code
link
image
horizontal rule
table
```

可以后置：

```text
LaTeX
Mermaid
复杂 HTML
脚注
数学公式
PlantUML
```

对于暂不支持语法：

必须：

```text
graceful degradation
```

即：

不能让整个文档渲染失败。

---

# 11. Folder-first Library

Library 是 Leaflet 最重要的数据模型。

使用 Android：

```text
Storage Access Framework
ACTION_OPEN_DOCUMENT_TREE
```

添加资料库。

使用持久化 URI permission。

一个 Library 对应：

```text
Library
--------
id
displayName
rootTreeUri
createdAt
lastScannedAt
```

文件：

```text
Document
--------
id
libraryId
relativePath
uri
displayName
title
extension
size
lastModified
contentHash(optional)
```

用户必须能够添加多个 Library。

例如：

```text
AI Documents
Work Notes
Books
Research
```

---

# 12. 不复制原文件

默认禁止：

```text
import → copy into app storage
```

Leaflet 应直接读取 SAF 指向的原文件。

原因：

```text
用户可能通过
Syncthing
Git
Termux
PC
Agent
脚本
其他 App
```

持续更新这些文件。

Leaflet 必须能看到修改。

---

# 13. 目录扫描

不要每次启动都全文 Parse 所有 Markdown。

首次扫描：

```text
Directory Tree
    ↓
metadata scan
    ↓
建立 Document index
```

优先读取：

```text
path
filename
size
mtime
uri
```

然后按需：

```text
parse Markdown
extract title
extract headings
FTS indexing
```

后续扫描至少能够基于：

```text
mtime
size
```

跳过明显没有变化的文件。

以后可增加 hash。

提供手动：

```text
刷新资料库
```

第一版不要求实时 FileObserver。

---

# 14. 文档标题规则

展示标题按：

```text
YAML frontmatter title
    ↓
第一个 H1
    ↓
filename without extension
```

例如：

```text
2026-09-17-flink-runtime-final-v4.md
```

内容：

```markdown
# Flink Runtime Architecture
```

UI 应显示：

```text
Flink Runtime Architecture
```

---

# 15. Reader

Reader 是最高优先级模块。

第一版必须支持：

```text
Markdown 阅读
TXT 阅读
字号
字体
行距
页边距
阅读位置保存
文档内搜索
TOC
点击 TOC 跳转
链接点击
本地 Markdown 相对链接
本地图片相对路径
```

Reader 必须保存阅读位置。

不要只保存：

```text
scrollY
```

应预留更稳定的数据：

```text
documentId
blockIndex
blockOffset
progress
anchor
```

MVP 可以先实现：

```text
scroll position + progress
```

以后迁移。

---

# 16. E-Ink 翻页

第一版不要实现复杂排版引擎。

使用：

```text
Viewport Page Turn
```

概念：

```text
pageHeight = viewportHeight

next:
scrollBy(viewportHeight - overlap)

previous:
scrollBy(-(viewportHeight - overlap))
```

建议 overlap：

```text
3% ~ 8%
```

用于帮助眼睛保持阅读位置。

E-Ink 模式：

```text
instant scroll
```

禁止：

```text
smoothScroll
```

用户操作区域：

```text
左侧区域 → 上一页

中央区域 → 显示/隐藏阅读菜单

右侧区域 → 下一页
```

以后允许用户调整点击区域。

---

# 17. Reader 模式

预留：

```text
CONTINUOUS_SCROLL
PAGE_TURN
```

E-Ink 默认：

```text
PAGE_TURN
```

Standard 默认：

```text
CONTINUOUS_SCROLL
```

但两种模式理论上都可以切换。

---

# 18. E-Ink Controller

建立厂商无关接口。

例如：

```kotlin
interface EInkController {

    fun onReaderEnter(view: View?)

    fun onReaderExit(view: View?)

    fun onPageTurn(view: View?)

    fun onScrollStart(view: View?)

    fun onScrollEnd(view: View?)

    fun requestPartialRefresh(view: View?)

    fun requestFullRefresh(view: View?)
}
```

实现：

```text
NoOpEInkController
GenericEInkController
OnyxEInkController
```

核心 Reader 不应该直接引用：

```text
Onyx SDK
```

---

# 19. Onyx / BOOX

研究官方：

```text
onyx-intl/OnyxAndroidDemo
```

重点：

```text
EpdController
EpdDeviceManager
UpdateMode.GU
UpdateMode.REGAL
UpdateMode.GC
Fast Mode
```

但是：

MVP 阶段不要让整个项目强绑定 Onyx SDK。

第一阶段可以：

```text
E-Ink UI / interaction optimization
+
System-controlled refresh
```

第二阶段：

```text
OnyxEInkController
```

再正式集成 SDK。

尤其注意：

厂商 SDK 依赖可能影响：

```text
APK size
Play compatibility
ABI
native library
future Android requirements
```

必须隔离。

---

# 20. 响应式布局

目标设备至少包括：

```text
普通 Android 手机
6" BOOX
7" BOOX
10.3" BOOX
```

不要依赖具体机型。

使用：

```text
Window Size / available width
```

做自适应布局。

小屏：

```text
Library
   ↓
Folder
   ↓
Reader
```

全屏导航。

大屏：

```text
┌────────────────────┬───────────────────────────┐
│ Folder / Documents │ Reader                    │
│                    │                           │
│                    │                           │
└────────────────────┴───────────────────────────┘
```

第一版大屏双栏可以暂缓。

但架构不要假设永远只有单栏。

---

# 21. E-Ink Design System

不要简单把 Material Theme 变成黑白。

定义：

```text
LeafletTheme
DisplayProfile
ReaderTheme
MotionPolicy
```

E-Ink：

```text
纯色背景
高对比文字
尽量避免半透明
尽量避免阴影
避免 gradient
避免 blur
避免 animated elevation
避免 shimmer
避免无限 loading animation
减少 UI 频繁更新
```

Loading 优先：

```text
静态文字
进度百分比
简单 progress bar
```

而不是旋转动画。

---

# 22. Standard Design System

第一版 Standard Mode 同样保持简洁。

允许：

```text
Material 3
轻微 ripple
少量 transition
正常 scrolling
dark theme
dynamic color（后置也可以）
```

不要为了第一版专门制作大量动画。

目标：

```text
E-Ink 架构先稳定
```

---

# 23. 表格

Markdown 表格在 6 寸屏幕非常重要。

第一阶段：

支持：

```text
horizontal scroll
```

但架构预留：

```text
TableRenderMode.NORMAL
TableRenderMode.WRAP
TableRenderMode.CARD
```

以后 E-Ink 可以把宽表格转成纵向字段阅读模式。

---

# 24. Code Block

支持：

```text
等宽字体
代码复制
语言标签
soft-wrap 开关
```

E-Ink 默认建议：

```text
soft wrap = ON
```

但用户可以关闭。

不要强制语法高亮。

语法高亮在 E-Ink 中必须考虑：

```text
grayscale readability
```

---

# 25. 搜索

区分：

```text
Document Search
```

和：

```text
Library Full Text Search
```

MVP：

必须完成：

```text
当前文档搜索
```

可以延后：

```text
全库 FTS
```

但 Room schema 和 Repository 层需要允许以后添加：

```text
Room FTS
```

---

# 26. MVP 功能范围

MVP 必做：

```text
添加目录 Library
删除 Library 引用
目录浏览
MD/TXT 文件发现
Markdown 阅读
TXT 阅读
标题提取
TOC
阅读位置记忆
最近阅读
文内搜索
字号
行距
页边距
字体选择基础能力
E-Ink DisplayProfile
Standard DisplayProfile 基础实现
显示模式切换
Viewport Page Turn
连续滚动
左右区域翻页
中央区域菜单
手动刷新 Library
设置页
About
```

---

# 27. MVP 明确不做

不要实现：

```text
Markdown 编辑器
账号系统
云同步
自己的云存储
AI Chat
RAG
向量数据库
在线 AI
PDF
EPUB
CBZ
手写笔记
OCR
复杂 annotation
双链知识库
Obsidian clone
插件系统
WebDAV
Git client
Syncthing client
完整标签系统
在线商店
```

除非用户后续明确新增需求。

---

# 28. 项目模块划分

避免一开始过度模块化。

建议：

```text
:app

:core:model
:core:storage
:core:database
:core:markdown
:core:reader
:core:eink
:core:designsystem

:feature:library
:feature:reader
:feature:settings
```

如果认为 MVP 阶段模块过多，可以适当合并。

但必须保证：

```text
storage
reader
eink
```

边界清楚。

绝对不要为了 Clean Architecture 而创建大量只有一个接口一个实现的无意义层。

---

# 29. 推荐基本架构

使用：

```text
UI
↓
ViewModel
↓
Repository
↓
Data Source
```

状态：

```text
StateFlow
```

异步：

```text
Coroutines
```

数据库：

```text
Room
```

设置：

```text
DataStore
```

不要在 MVP 引入大型 DI framework，除非明显有价值。

可以先：

```text
manual DI
```

或轻量方案。

如果决定 Hilt/Koin，必须在 ADR 解释原因。

---

# 30. Repository 目录结构

至少建立：

```text
Leaflet/
├── AGENTS.md
├── README.md
├── CHANGELOG.md
├── LICENSE
├── app/
├── core/
├── feature/
├── docs/
│   ├── product/
│   │   ├── vision.md
│   │   ├── principles.md
│   │   └── roadmap.md
│   │
│   ├── requirements/
│   │   ├── README.md
│   │   └── REQ-0001-folder-library.md
│   │
│   ├── adr/
│   │   ├── README.md
│   │   └── ADR-0001-folder-first-storage.md
│   │
│   ├── architecture/
│   │   ├── overview.md
│   │   ├── reader.md
│   │   ├── storage.md
│   │   └── eink.md
│   │
│   ├── research/
│   │   ├── README.md
│   │   ├── markdown-renderers.md
│   │   └── eink-readers.md
│   │
│   ├── performance/
│   │   └── apk-size.md
│   │
│   └── development/
│       ├── build.md
│       ├── testing.md
│       └── release.md
│
└── .github/
```

可以根据实际 Gradle module 结构微调。

---

# 31. AGENTS.md

必须生成：

```text
/AGENTS.md
```

它不是项目介绍。

它是：

> 所有 coding agents 在修改 Leaflet 前必须读取的操作规约。

AGENTS.md 应保持短小、稳定、可执行。

不要复制整个 README。

必须包含：

```text
Project purpose
Current milestone
Architecture boundaries
Build commands
Test commands
Formatting commands
Requirement workflow
ADR workflow
Flavor rules
E-Ink constraints
Storage constraints
Dependency policy
Definition of Done
Files agents should read before major work
```

---

# 32. AGENTS.md 建议内容

AGENTS.md 应明确告诉 Agent：

在实现功能之前：

```text
1. 阅读对应 REQ
2. 阅读相关 architecture 文档
3. 检查是否存在 ADR
4. 不确定架构时不要自行大范围重构
```

修改功能后：

```text
1. 更新 tests
2. 更新对应 requirement status
3. 必要时更新 architecture docs
4. 用户可见行为改变则更新 CHANGELOG
5. dependency 改变要检查 APK size
```

明确禁止 Agent：

```text
把 Leaflet 变成编辑器
擅自加入账号系统
擅自加入网络服务
擅自加入 analytics
把 SAF 文件复制到内部存储作为默认模型
让 Reader 直接依赖 Onyx SDK
在 E-Ink 模式大量加入动画
把 flavor 分叉成两套重复代码
为了架构洁癖做大规模无收益重构
无需求 ID 添加大型功能
```

---

# 33. 局部 AGENTS.md

第一阶段只创建：

```text
/AGENTS.md
```

不要到处创建 AGENTS.md。

以后当某个目录出现非常明确的局部规则时，可以增加：

```text
core/reader/AGENTS.md
core/eink/AGENTS.md
```

例如 Reader 的性能限制已经复杂到 root AGENTS.md 放不下时再增加。

局部 AGENTS.md 只写：

```text
该模块额外规则
```

不要重复 root 内容。

---

# 34. Requirements 管理

Leaflet 使用：

```text
Requirement as Files
```

而不是把所有需求只放在聊天历史。

目录：

```text
docs/requirements/
```

每一个明显功能都拥有：

```text
REQ-XXXX-short-name.md
```

例如：

```text
REQ-0001-folder-library.md
REQ-0002-markdown-reader.md
REQ-0003-reading-progress.md
REQ-0004-eink-display-profile.md
REQ-0005-page-turn.md
```

---

# 35. Requirement 模板

每份 REQ 使用统一格式：

```markdown
# REQ-XXXX — Title

Status:
Priority:
Milestone:

## Problem

为什么需要这个功能。

## User Story

As a ...
I want ...
So that ...

## Scope

包含什么。

## Non-goals

明确不包含什么。

## User Experience

用户看到什么，怎么操作。

## Functional Requirements

FR-1
FR-2
FR-3

## Acceptance Criteria

AC-1
AC-2
AC-3

## Edge Cases

## E-Ink Considerations

## Standard Android Considerations

## Performance Considerations

## Storage / Data Impact

## Test Plan

## Dependencies

## Open Questions
```

---

# 36. Requirement 状态

统一：

```text
DRAFT
READY
IN_PROGRESS
BLOCKED
DONE
DEFERRED
```

只有：

```text
READY
```

的 REQ 才应该直接进入开发。

Agent 不应自行把重大 DRAFT 需求变成 DONE。

---

# 37. Requirement 与实现计划分离

不要把：

```text
需求是什么
```

和：

```text
怎么写代码
```

混到一起。

REQ 负责：

```text
WHAT / WHY
```

Architecture / ADR / implementation plan 负责：

```text
HOW
```

---

# 38. ADR

重要架构决策记录在：

```text
docs/adr/
```

格式：

```text
ADR-XXXX-title.md
```

例如：

```text
ADR-0001-folder-first-storage.md
ADR-0002-saf-document-access.md
ADR-0003-product-flavors.md
ADR-0004-native-markdown-rendering.md
ADR-0005-eink-controller-abstraction.md
```

模板：

```markdown
# ADR-XXXX — Title

Status:
Date:

## Context

## Decision

## Alternatives Considered

## Consequences

## Follow-up
```

ADR 一旦 Accepted：

不要直接重写历史。

重大变化：

新增一个 ADR supersede 旧 ADR。

---

# 39. Roadmap

建立：

```text
docs/product/roadmap.md
```

建议：

```text
M0 Foundation
M1 E-Ink MVP
M2 Reader Quality
M3 Full Android Edition
M4 Search & Library Scale
M5 Advanced E-Ink Integration
```

M0：

```text
project skeleton
AGENTS
requirements
ADR
flavors
basic Library
basic Reader
DisplayProfile
```

M1：

```text
usable E-Ink release
```

M2：

```text
large documents
tables
code blocks
relative images
TOC quality
search quality
```

M3：

```text
Standard Android polish
optional animations
dark theme
normal Android UX
Full flavor release
```

M4：

```text
FTS
large libraries
incremental indexing
```

M5：

```text
Onyx EPD integration
advanced refresh control
device-specific optimizations
```

---

# 40. README.md

README 不是需求文档。

README 面向：

```text
用户
潜在贡献者
第一次进入仓库的开发者
```

README 必须简洁说明：

```text
Leaflet 是什么
为什么存在
目前状态
核心特性
截图占位
支持格式
两个版本区别
如何构建
技术栈
项目结构概览
Roadmap
Contributing
License
```

---

# 41. README 开头建议

README 第一屏应该能够让人马上理解：

```markdown
# Leaflet

A local-first Android reader for Markdown and plain-text document libraries,
designed with E-Ink as a first-class platform.

Leaflet treats a folder as a library.

Point it at a directory containing Markdown and TXT files and start reading.
No import database, no account, no cloud required.
```

然后说明：

```text
Designed for:

AI-generated documentation
technical notes
research archives
local knowledge collections
E-Ink devices
```

---

# 42. README 双版本说明

README 必须明确：

```text
Leaflet
```

长期有两个发行方向：

```text
Leaflet
Full Android edition

Leaflet E-Ink
Lightweight E-Ink focused edition
```

并说明：

```text
Full:
Standard + E-Ink Mode

E-Ink:
E-Ink Mode only / reduced feature set
smaller distribution
```

当前：

```text
E-Ink edition is the primary development target.
```

---

# 43. README 不要写什么

不要：

```text
列几百个未来功能
承诺不存在的功能
把 README 写成 PRD
把 README 写成 AGENTS.md
把所有技术决策塞进去
```

详细内容链接到：

```text
docs/
```

---

# 44. CHANGELOG

从 Day 1 使用：

```text
CHANGELOG.md
```

格式接近：

```text
Keep a Changelog
```

至少：

```text
Added
Changed
Fixed
Performance
E-Ink
```

用户不可见的纯 refactor 不必全部记录。

---

# 45. Research

创建：

```text
docs/research/
```

研究开源项目时：

不要复制代码作为第一反应。

先记录：

```text
What problem does it solve?
What architecture does it use?
What can Leaflet learn?
What should Leaflet NOT copy?
License implications?
```

---

# 46. 必须研究的项目

## PilcrowMD

Repository：

```text
pilcrowmd/pilcrow
```

重点研究：

```text
native Markdown rendering
Markwon
large Markdown file handling
reader typography
TOC
search
reading position
visual regression testing
dependency/license management
large-file tests
```

不要直接复制 GPL 代码。

重点学习设计和测试思路。

---

## Markdown Reader

Repository：

```text
zero-made/mdreader-android
```

重点研究：

```text
ACTION_OPEN_DOCUMENT_TREE
persisted SAF access
recursive folder browsing
.md/.txt discovery
recent documents
TOC
scroll memory
relative resources
```

尤其研究：

```text
Folder → Document
```

UX。

其 WebView 渲染方案可以作为对照，不必照搬。

---

## Markor

Repository：

```text
gsantner/markor
```

重点研究：

```text
local filesystem philosophy
plaintext interoperability
folder-oriented workflow
Android file handling
offline-first approach
```

Leaflet 不应该成为 Markor 那样的编辑器。

研究：

```text
文件管理
```

而不是复制它的产品定位。

---

## KOReader

Repository：

```text
koreader/koreader
```

重点不是 Markdown。

重点研究：

```text
E-Ink interaction
page turn
tap zones
reader menu
font configuration
margin
line spacing
reading progress
screen refresh thinking
low-distraction UI
large document navigation
```

把它视为：

```text
E-Ink UX reference
```

而不是 Android 架构参考。

---

## OnyxAndroidDemo

Repository：

```text
onyx-intl/OnyxAndroidDemo
```

这是 BOOX / Onyx 相关开发必须长期跟踪的参考项目。

研究：

```text
EpdController
EpdDeviceManager
screen update modes
REGAL
GU
GC
Fast Mode
device detection
```

把研究结果记录到：

```text
docs/research/onyx-epd.md
```

不要第一天就把 SDK 深度绑定到 Reader。

---

## Markwon

研究：

```text
Markwon
```

重点：

```text
native Spannable rendering
CommonMark
tables
task list
syntax highlighting
RecyclerView support
extension points
```

创建：

```text
docs/research/markdown-renderers.md
```

对比至少：

```text
Markwon
WebView + marked
Compose markdown libraries
custom parser
```

最终记录为什么 Leaflet 选择当前方案。

---

# 47. License Policy

创建：

```text
THIRD_PARTY_LICENSES.md
```

任何引入 dependency 前：

确认：

```text
license
是否进入 release artifact
是否包含 native code
是否显著增加 APK size
```

尤其不要直接从：

```text
GPL / AGPL
```

项目复制实现代码到一个未来许可证尚未决定的项目中。

研究：

允许。

复制：

必须先确认许可证影响。

---

# 48. Testing Strategy

至少建立：

```text
unit tests
instrumented tests
reader sample fixtures
```

建立：

```text
testdata/
markdown/
```

包含：

```text
simple.md
headings.md
lists.md
tables.md
code.md
long.md
unicode.md
chinese.md
japanese.md
relative-links.md
relative-images.md
malformed.md
huge.md
```

重点测试：

```text
中文
日文
英文
emoji
超长段落
超长代码行
大型 Markdown
异常 Markdown
空文件
大 TXT
```

---

# 49. E-Ink 测试

不要认为 Android Emulator 能覆盖 E-Ink。

建立：

```text
docs/development/eink-test-matrix.md
```

至少记录：

```text
设备
Android version
屏幕尺寸
分辨率
固件
测试日期
Leaflet version
```

测试：

```text
翻页残影
菜单显示
快速连续翻页
长按
返回
字体调整
TOC
长文档
代码块
表格
图片
黑白对比
App resume
设备旋转
```

---

# 50. Performance Budget

从早期就建立性能基线。

至少关注：

```text
cold start
Library open
Document open
page turn latency
scroll latency
large document memory
Library scan time
APK size
```

不必过早微优化。

但禁止明显 O(N²) 的文档处理。

---

# 51. 大文档测试目标

至少准备：

```text
100 KB
1 MB
5 MB
10 MB
```

Markdown 样本。

第一版不要求 10 MB 文档体验完美。

但：

```text
不能直接 OOM
不能 ANR
不能 crash
```

应允许：

```text
loading
incremental processing
fallback
```

---

# 52. Dependency Policy

优先：

```text
AndroidX
Kotlin official libraries
成熟小型库
```

减少：

```text
巨大 framework
包含大量 native libraries 的依赖
不维护的库
仅为了一个小功能引入的大包
```

引入新依赖时检查：

```text
是否真的比自己实现更合理
APK size
maintenance
license
E-Ink flavor 是否需要
```

---

# 53. Coding Agent Workflow

AGENTS.md 应要求所有 Agent：

接到需求后首先找到对应：

```text
REQ-XXXX
```

如果没有：

小 bug 可以直接处理。

明显新功能必须：

先创建或补充 REQ。

实现过程中：

```text
不要扩大 Scope
```

例如：

任务是：

```text
增加 TXT Reader
```

不要顺便：

```text
重构整个 storage layer
换掉 Markdown library
新增 Hilt
改所有 UI
```

除非确实阻塞实现。

---

# 54. Definition of Done

一个 Feature 只有满足以下条件才算完成：

```text
代码完成
能编译
核心测试通过
acceptance criteria 满足
E-Ink 行为检查
无明显 Standard Mode 回归
文档必要更新
没有引入无解释的大 dependency
没有破坏 flavor isolation
```

---

# 55. Commit Strategy

建议 Conventional Commits：

```text
feat:
fix:
perf:
refactor:
docs:
test:
build:
chore:
```

例如：

```text
feat(reader): add viewport page turning
fix(storage): restore persisted tree permission
perf(reader): avoid reparsing unchanged document
docs(req): define library refresh behavior
```

不要生成大量没有意义的小 commit。

但不同逻辑任务不要混在一个超大 commit。

---

# 56. CI

建立 GitHub Actions 基础 CI。

至少运行：

```text
compile
unit tests
lint
```

分别确保：

```text
einkDebug
fullDebug
```

能够构建。

即使 Full 版本当前不发布：

也不能让它长期 compile broken。

---

# 57. Build Commands

AGENTS.md 和 README 必须提供实际可执行命令。

例如：

```bash
./gradlew assembleEinkDebug
./gradlew assembleFullDebug
./gradlew test
./gradlew lint
```

命令必须以最终 Gradle configuration 实际生成的任务名为准。

不要文档写一套、项目实际另一套。

---

# 58. Security / Privacy

Leaflet 默认：

```text
offline
```

阅读本地文件不应该要求：

```text
账号
网络
analytics
tracking
```

如果将来为了远程图片等功能加入网络能力：

必须明确：

```text
为什么
什么时候触发
E-Ink flavor 是否需要
```

默认本地 Markdown 阅读路径：

```text
不依赖网络
```

---

# 59. Feature Flags

不要大量使用散落 Boolean。

建立明确：

```text
AppCapabilities
```

例如：

```text
supportsStandardMode
supportsAnimations
supportsDynamicColor
supportsOnyxIntegration
supportsAdvancedSharing
```

Capabilities 由：

```text
build flavor
+
runtime environment
```

决定。

UI 根据 Capabilities 决定是否：

```text
显示
禁用
隐藏
```

功能。

---

# 60. Flavor 不要复制代码

禁止：

```text
src/full/ReaderScreen.kt
src/eink/ReaderScreen.kt
```

各写一套巨大实现。

应该：

```text
src/main/Reader
```

通过：

```text
DisplayProfile
MotionPolicy
AppCapabilities
EInkController
```

实现差异。

Flavor 只负责真正不同的：

```text
dependency
resource
platform adapter
feature availability
manifest
applicationId
```

---

# 61. M0 实现顺序

按以下顺序实现。

### Phase 0 — Repository Foundation

完成：

```text
Gradle project
flavors
README
AGENTS
requirements
ADR
architecture docs
CI skeleton
```

### Phase 1 — Storage

完成：

```text
ACTION_OPEN_DOCUMENT_TREE
persist permission
Library
recursive document discovery
MD/TXT filter
```

### Phase 2 — Basic Reader

完成：

```text
open document
Markdown rendering
TXT rendering
reading position
```

### Phase 3 — Reader Navigation

完成：

```text
TOC
page turn
continuous scroll
tap zones
```

### Phase 4 — Display Profiles

完成：

```text
EInkDisplayProfile
StandardDisplayProfile
settings switch
```

### Phase 5 — MVP Polish

完成：

```text
recent documents
font size
line spacing
margin
document search
basic tests
APK size check
```

然后：

```text
M1 E-Ink Alpha
```

---

# 62. 第一次执行本 Prompt 时不要过度实现

第一次执行任务时：

不要试图一次完成整个 Leaflet。

首先：

1. 初始化 Android 项目。
2. 建立合理模块。
3. 配置 full/eink flavor。
4. 创建 AGENTS.md。
5. 创建 README.md。
6. 创建 docs 基础结构。
7. 创建 Requirements/ADR 模板。
8. 写出 M0/M1 Roadmap。
9. 创建最小 Library screen。
10. 创建最小 Settings screen。
11. 创建 DisplayProfile 抽象。
12. 确保 full/eink 都能编译。
13. 加最基础 CI。
14. 提交初始架构说明。

如果时间允许：

再实现 SAF Folder Picker。

不要为了展示进度而塞入大量假的 placeholder architecture。

---

# 63. 第一次执行完成后必须输出报告

完成后向用户输出：

```text
1. 创建了什么
2. 项目目录树
3. 架构关键点
4. flavor 如何工作
5. AGENTS.md 中最重要的规则
6. 当前 Requirements
7. 当前 ADR
8. 当前可以运行的 Gradle commands
9. 测试结果
10. 尚未实现的内容
11. 建议下一步
```

如果存在技术决策没有足够依据：

不要假装确定。

记录到：

```text
Open Questions
```

或创建：

```text
DRAFT ADR
```

---

# 64. 第一批 Requirements

初始化时至少建立：

```text
REQ-0001 Folder-based Library
REQ-0002 Markdown and TXT Reader
REQ-0003 Reading Progress
REQ-0004 Display Profiles
REQ-0005 E-Ink Page Turn
REQ-0006 Reader Preferences
REQ-0007 Table of Contents
REQ-0008 In-document Search
REQ-0009 Full and E-Ink Distribution Flavors
```

第一阶段只有必要项进入：

```text
READY
```

其他可以：

```text
DRAFT
```

---

# 65. 第一批 ADR

至少创建并初步决定：

```text
ADR-0001 Folder-first Local Storage
ADR-0002 Android SAF
ADR-0003 Full/E-Ink Product Flavors
ADR-0004 DisplayProfile Abstraction
```

以下可以先保持 Draft：

```text
ADR-0005 Markdown Rendering Engine
ADR-0006 Reader Rendering Architecture
ADR-0007 Onyx SDK Integration
```

研究完成后再 Accepted。

---

# 66. 产品设计原则

把这些写入：

```text
docs/product/principles.md
```

原则一：

```text
Files belong to the user.
```

原则二：

```text
Folders are libraries.
```

原则三：

```text
Reading comes before editing.
```

原则四：

```text
E-Ink is a platform, not a theme.
```

原则五：

```text
Offline by default.
```

原则六：

```text
Fast enough for AI-sized documents.
```

原则七：

```text
Do not lock users into Leaflet.
```

原则八：

```text
Simple UI, powerful reading.
```

原则九：

```text
One codebase, multiple distributions.
```

原则十：

```text
Every feature must justify its cost on E-Ink.
```

---

# 67. 最终判断标准

如果某项设计让 Leaflet 越来越像：

```text
Obsidian
Notion
Markdown IDE
文件管理器
AI Chat App
```

说明方向可能偏了。

Leaflet 最核心的体验应该始终是：

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

复杂功能不能破坏这一条主路径。

---

现在开始执行。

执行前先检查当前目录是否已经存在项目。

如果是空目录：

按照以上要求初始化。

如果已经存在代码：

先阅读现有代码、README、AGENTS.md、docs 和 Gradle 配置，再决定如何安全迁移。

不要为了满足 Prompt 而删除已有有效实现。

最终目标不是“生成很多文件”，而是建立一个能够让人类开发者和多个 Coding Agent 长期稳定协作开发的 Leaflet 项目基础。
