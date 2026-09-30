# 架构决策记录（ADR）

[English](README.md) | 中文

> 来源：`docs/archive/init.md` §38（ADR 目录、命名与模板）、实施计划 T01（架构决策清单）。
> REQ 描述 WHAT / WHY，本目录与 `docs/architecture/`、实施计划描述 HOW。

## ADR 模板

新增 ADR 时复制以下模板（`docs/archive/init.md` §38 全字段），文件名格式为 `ADR-XXXX-title.md`：

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

字段说明：

- `Status`：`Accepted`（已决定，实施以此为准）或 `Proposed`（证据不足，待研究后定案）。
- `Date`：决策或提案日期。
- `Context`：触发决策的背景与约束。
- `Decision`：决定的内容。
- `Alternatives Considered`：考虑过的替代方案及不采用原因。
- `Consequences`：决定的后果（收益与代价）。
- `Follow-up`：后续待验证事项。

## Accepted / Proposed 规则

- 证据不足的 ADR 保持 `Proposed`，不能伪装 `Accepted`（实施计划 T01 验收项）。
- ADR 一旦 `Accepted`，不要直接重写历史；重大变化新增一个 ADR supersede 旧 ADR。
- research 完成后再把相关 `Proposed` 转为 `Accepted`（`docs/archive/init.md` §65）。

## ADR 索引

| 编号 | 标题 | 文件 | 状态 |
| --- | --- | --- | --- |
| ADR-0001 | Folder-first Local Storage（文件夹优先本地存储） | `ADR-0001-folder-first-storage.md` | Accepted |
| ADR-0002 | SAF Document Access（SAF 只读文档访问） | `ADR-0002-saf-document-access.md` | Accepted |
| ADR-0003 | Product Flavors（full / eink 发行版本） | `ADR-0003-product-flavors.md` | Accepted |
| ADR-0004 | Native Markdown Rendering（原生 Markdown 渲染） | `ADR-0004-native-markdown-rendering.md` | Accepted |
| ADR-0005 | EInkController Abstraction（E-Ink 控制器抽象） | `ADR-0005-eink-controller-abstraction.md` | Accepted |
| ADR-0006 | Block Rendering, Position and Large Files（块级渲染、位置与大文件） | `ADR-0006-block-rendering-position-large-files.md` | Accepted |
| ADR-0007 | Onyx SDK Integration（Onyx SDK 集成） | `ADR-0007-onyx-sdk-integration.md` | Proposed |
| ADR-0008 | Indexed Directory Metadata for Browsing（为浏览索引目录元数据） | `ADR-0008-directory-index.md` | Accepted |

说明：

- ADR-0001–0006 与 ADR-0008 为 `Accepted`：folder-first、SAF 只读访问、flavors / capabilities 分离、
  原生渲染路线、EInkController 抽象、分块渲染与位置恢复/大文件降级，以及浏览用的纯路径目录元数据，
  均已有充分依据，实施以此为准（文件名与状态与已落地的 8 份 ADR 文件一致）。
- ADR-0007 为 `Proposed`：Onyx / 厂商 SDK 集成是 M5 事项，证据不足前不集成、
  不写假实现；待 M5 前置研究完成后定案。
