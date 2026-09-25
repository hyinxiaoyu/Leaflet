# ADR-0001 — 文件夹优先存储

[English](ADR-0001-folder-first-storage.md) | 中文

## Context

Leaflet 的核心定位是 local-first、folder-first 的 Android 阅读器（`docs/archive/init.md` §1、§11–§13）：用户通过 AI 工具、脚本、同步工具持续更新的 Markdown / TXT 文件已经按目录组织好，Leaflet 不要求用户"导入成书籍"，而是直接把一个 Android 目录作为 Library 阅读。

与之对立的方案是 import-copy 模型：把用户文件复制进应用私有存储再阅读。该模型会造成两份事实来源、占用双倍空间（E-Ink 设备存储宝贵，`docs/archive/init.md` §5、§7）、且外部更新不可见。

`docs/research/eink-readers.md` §3（Markor）证实了"目录即库、原文唯一事实来源"工作流的可行性；`docs/plans/M0-M1-implementation-plan.md` T05 把该语义落为扫描/浏览验收标准。

## Decision

1. 原始文件是唯一事实来源（single source of truth）。Leaflet 数据库只保存索引、阅读进度、收藏（远期）、最近阅读、用户设置、搜索索引、UI 状态（`docs/archive/init.md` §1）。
2. 默认禁止 `import → copy into app storage`（`docs/archive/init.md` §12）。Leaflet 直接读取 SAF 指向的原文件；不把原文写入数据库，不默认复制为内部文档。
3. 删库不删文：删除 Leaflet 数据库、移除 Library 引用、清除应用数据或卸载应用，都必须完全不影响原始 Markdown / TXT 文件。移除 Library 只清除本地引用与辅助数据（实施计划 T04 验收、T05 删除规则）。
4. 换设备/换工具可用外部手段（文件管理器、Git、Syncthing）验证原文内容不变；T13 发布验证包含"卸载/清数据库/移除 Library 后原文件不变"的校验项。

## Alternatives Considered

- Import-copy（复制进私有存储再读）：读写性能最可控、离线最彻底，但违背 folder-first 定位、双倍存储、外部更新不可见。否决（`docs/archive/init.md` §12 明确默认禁止）。
- 混合模式（小文件复制、大文件引用）：两套一致性语义，心智负担与 Bug 面翻倍，MVP 不采用；如未来有离线缓存需求，另立 ADR，以"引用 + 可失效缓存"而非"复制即拥有"为前提。

## Consequences

- `:core:storage` 必须实现只读访问、持久授权、失权/文件消失可恢复状态（见 ADR-0002）。
- 扫描只能做元数据索引 + 按需解析（`docs/archive/init.md` §13），首扫不解析全文；位置/缓存必须按文档变化失效（实施计划 T07）。
- T13 必须实测"原文件操作前后校验"（T04 验证要求），把删库不删文从声明变为证据。
- 代价：阅读稳定性依赖外部文件与授权状态，Reader 必须处理文件消失、授权丢失等错误态（见 `docs/architecture/reader.md` 三态）。

## Follow-up

- 如未来引入私有缓存/离线快照，需新 ADR 说明失效策略与存储上限，并重申删库不删文不受影响。
- Room schema 演进不得以清库掩盖位置/设置丢失（实施计划 §8 回退规则）。
