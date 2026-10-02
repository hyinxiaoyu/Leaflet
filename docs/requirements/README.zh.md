# 需求规格（REQ）

[English](README.md) | 中文

> 来源：`docs/archive/init.md` §§34–37（需求管理、模板、状态及与实施计划分离）、§64（首批需求），以及实施计划 T01（固定编号和验收字段）。REQ 描述 WHAT / WHY；HOW 见 `docs/architecture/`、`docs/adr/` 和实施计划。

### REQ 模板

新增需求时复制下面模板，保留归档提示 §35 的全部字段，文件名使用 `REQ-XXXX-topic.md`。

```markdown
# REQ-XXXX — Title

Status:
Priority:
Milestone:

## Problem
## User Story
## Scope
## Non-goals
## User Experience
## Functional Requirements
## Acceptance Criteria
## Edge Cases
## E-Ink Considerations
## Standard Android Considerations
## Performance Considerations
## Storage / Data Impact
## Test Plan
## Dependencies
## Open Questions
```

在 `Problem` 中说明原因，使用“As a … / I want … / So that …”描述用户价值。明确范围、非目标、用户行为、编号 FR/AC、边界、平台/性能/数据影响、测试、依赖和未决问题。

### 状态值

只使用 `DRAFT`、`READY`、`IN_PROGRESS`、`BLOCKED`、`DONE` 或 `DEFERRED`。

- 只有 READY 的 REQ 才直接开始实现。
- 代理不得自行把重大 DRAFT 需求推进到 DONE。
- T01 交付的 11 个 REQ 最初均为 READY。证据缺口放在 `Open Questions`，不得通过降低状态逃避，也不得假装已经验证。

### REQ 索引

| ID | 标题 | 文件 | 状态 |
| --- | --- | --- | --- |
| REQ-0001 | Folder-first Library | `REQ-0001-folder-library.md` | IN_PROGRESS |
| REQ-0002 | Markdown / TXT Reader | `REQ-0002-markdown-txt-reader.md` | IN_PROGRESS |
| REQ-0003 | Reading Position | `REQ-0003-reading-position.md` | IN_PROGRESS |
| REQ-0004 | Display Profiles | `REQ-0004-display-profiles.md` | IN_PROGRESS |
| REQ-0005 | Page Turn | `REQ-0005-page-turn.md` | IN_PROGRESS |
| REQ-0006 | Table of Contents | `REQ-0006-toc.md` | READY |
| REQ-0007 | Typography, Tables, and Code Blocks | `REQ-0007-typography-tables-code.md` | READY |
| REQ-0008 | In-document Search | `REQ-0008-in-document-search.md` | READY |
| REQ-0009 | Local Resources and Links | `REQ-0009-local-resources-links.md` | READY |
| REQ-0010 | Settings / About | `REQ-0010-settings-about.md` | IN_PROGRESS |
| REQ-0011 | Performance and Release Verification | `REQ-0011-performance-release.md` | IN_PROGRESS |
| REQ-0012 | Library Navigation (Breadcrumbs, Stable Scan Status, Row Targets) | `REQ-0012-library-navigation.md` | READY |
| REQ-0013 | Daily Reading Home and Settings | `REQ-0013-daily-reading-home.md` | IN_PROGRESS |
| REQ-0014 | Discoverable Reader Controls | `REQ-0014-reader-controls.md` | IN_PROGRESS |

T01 固定了编号与所有权（实施计划 §5），并确认全部 11 个文件名及分配。每项 MVP 需求都映射到 REQ 和实施任务。需求与实现步骤保持分离；本目录不规定代码层面实现。

REQ-0012 于 2026-10-01 作为 REQ-0001/0004 的 READY 局部补充加入，覆盖 UX-05 浏览切片（NB-00–NB-05）；不改变仍为 IN_PROGRESS 的 REQ-0001 状态。

REQ-0013 限定日常阅读首页/设置这一轮；实现状态为 IN_PROGRESS，Android/设备验收单独处理。

REQ-0014 限定可见阅读导航、限高菜单、可重复帮助和指针边界安全；Android/设备验收仍待执行。
