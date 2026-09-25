# Leaflet 产品设计原则

[English](principles.md) | 中文

> 来源：`docs/archive/init.md` §66 的十条原则原文。这些原则是需求评审与架构决策的上游依据；与原则冲突的设计必须给出证据和理由，并在相关 REQ / ADR 中记录。

## 原则一

```text
Files belong to the user.
```

文件属于用户：原始 Markdown / TXT 是唯一事实来源；App 只保存辅助数据，不得损坏、锁定或绑架用户文件。

## 原则二

```text
Folders are libraries.
```

目录即资料库：保留用户原始目录结构用于浏览和组织，不另建一套“书架”覆盖它。

## 原则三

```text
Reading comes before editing.
```

阅读优先于编辑：Leaflet 是阅读器，不是编辑器；任何功能都不得以牺牲阅读体验为代价。

## 原则四

```text
E-Ink is a platform, not a theme.
```

E-Ink 是一个平台，而非一套主题：显示策略集中到 DisplayProfile / MotionPolicy 等统一抽象，不散落 `if (isEink)` 判断，也不把 E-Ink 当作黑白皮肤。

## 原则五

```text
Offline by default.
```

默认离线：不自动联网、不加载远程资源（远程图片只显示占位符），不加入账号或云服务。

## 原则六

```text
Fast enough for AI-sized documents.
```

对 AI 时代的大型文档足够快：尽早建立性能基线；大文档操作必须可取消，不得 OOM、ANR 或崩溃。

## 原则七

```text
Do not lock users into Leaflet.
```

不把用户锁定在 Leaflet：删除数据库、移除 Library 或卸载 App 都不得影响原始文件；不使用私有格式绑定用户。

## 原则八

```text
Simple UI, powerful reading.
```

界面简洁、阅读强大：保持核心路径（打开 → 目录 → 阅读 → 退出）极简；复杂度只放在更深入的阅读能力中。

## 原则九

```text
One codebase, multiple distributions.
```

一个代码库、多个发行版：`full` / `eink` 共享 90% 以上核心代码；通过 flavor 配置表达差异，不复制 Reader 实现。

## 原则十

```text
Every feature must justify its cost on E-Ink.
```

每个功能都必须证明其在 E-Ink 上的成本合理：动画、高频刷新和复杂效果默认关闭，除非其收益大于残影与耗电代价。
