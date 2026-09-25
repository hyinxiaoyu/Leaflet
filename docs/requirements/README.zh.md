# 需求规格（REQ）

[English](README.md) | 中文

> 来源：`docs/archive/init.md` §34–§37（REQ 管理、模板、状态、与实现计划分离）、§64（第一批 REQ）、
> 实施计划 T01（需求编号固定、字段验收）。
> REQ 描述 WHAT / WHY；HOW 见 `docs/architecture/`、 `docs/adr/` 与实施计划。

## REQ 模板

新增 REQ 时复制以下模板（`docs/archive/init.md` §35 全字段），文件名格式为 `REQ-XXXX-topic.md`：

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

## 状态枚举

统一使用以下状态（`docs/archive/init.md` §36）：

```text
DRAFT
READY
IN_PROGRESS
BLOCKED
DONE
DEFERRED
```

规则：

- 只有 `READY` 的 REQ 才应该直接进入开发。
- Agent 不应自行把重大 `DRAFT` 需求变成 `DONE`。
- 本次 T01 交付的 11 份 REQ 全部标为 `READY`；证据不足导致无法定案的内容写入
  `Open Questions`，不得以降低状态回避，也不得伪装已验证。

## REQ 索引

| 编号 | 标题 | 文件 | 状态 |
| --- | --- | --- | --- |
| REQ-0001 | Folder-first Library | `REQ-0001-folder-library.md` | IN_PROGRESS |
| REQ-0002 | Markdown-TXT Reader | `REQ-0002-markdown-txt-reader.md` | IN_PROGRESS |
| REQ-0003 | Reading Position | `REQ-0003-reading-position.md` | IN_PROGRESS |
| REQ-0004 | Display Profiles | `REQ-0004-display-profiles.md` | IN_PROGRESS |
| REQ-0005 | Page Turn | `REQ-0005-page-turn.md` | READY |
| REQ-0006 | TOC | `REQ-0006-toc.md` | READY |
| REQ-0007 | 排版、表格与代码块 | `REQ-0007-typography-tables-code.md` | READY |
| REQ-0008 | 文内搜索 | `REQ-0008-in-document-search.md` | READY |
| REQ-0009 | 本地资源与链接 | `REQ-0009-local-resources-links.md` | READY |
| REQ-0010 | 设置 / About | `REQ-0010-settings-about.md` | IN_PROGRESS |
| REQ-0011 | 性能与发布验证 | `REQ-0011-performance-release.md` | IN_PROGRESS |

说明：

- 编号与标题对应 T01 固定分工（实施计划 §5 T01）；全部 11 个文件名与负责 agent 确认一致。
- 每条 MVP 要求都能映射到上述 REQ 与实施计划任务；需求与实现计划分离，
  本目录不写代码实现步骤。
