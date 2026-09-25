# ADR-0002 — SAF 文档访问

[English](ADR-0002-saf-document-access.md) | 中文

## Context

Android 上访问用户目录的标准机制是 Storage Access Framework。Leaflet 以 `ACTION_OPEN_DOCUMENT_TREE` 添加资料库，并用持久化 URI permission 在进程重建后继续访问（`docs/archive/init.md` §11）。约束：不使用绝对文件路径替代 content URI，不依赖 `MANAGE_EXTERNAL_STORAGE`（实施计划 T04 边界）。

`docs/research/eink-readers.md` §2（mdreader-android）实测了树授权 → 递归元数据枚举 → 封顶 → 排序 → 死条自清的完整路径；§1（Pilcrow）证实了 `FileRepository` 边界（Reader 不碰 SAF/Room）与 `takePersistableUriPermission` 重开上次文件的模式。

## Decision

1. SAF 只读：Leaflet 只申请读授权；不写回原文，不创建/删除/重命名用户文件。对用户目录的唯一写操作是 SA​​F 授权本身的获取与释放（`takePersistableUriPermission` / `releasePersistableUriPermission`），释放时机为移除 Library 引用且确认该授权无其他 Library 共享使用时（共享授权仍被使用时不提前释放，T04 验收）。
2. 持久授权：Library 保存 `rootTreeUri` 与持久化授权；授权经过进程重建仍可用（T04 验收）。取消选择、拒绝授权、授权丢失、文件消失均有可恢复状态，不得 crash 或静默丢数据。
3. `DocumentSource` 是 Reader 侧唯一的文档访问边界：
   - 按文档 ID 打开只读流；
   - 解析库内相对资源（相对 Markdown 链接、相对图片），基于已授权树和父目录解析，允许树内 `..`，拒绝越出授权树；
   - 不能把 content URI 当普通文件路径拼接；URI 编码/规范化策略一致；未知 scheme 不执行（实施计划 T10 安全边界）。
4. Reader 不碰 SAF：`:core:reader` 不直接访问 SAF、Room 或厂商 SDK（实施计划 §3、AGENTS.md）。App 通过 Repository 获取文档流与元数据，再把可取消的加载任务交给 Reader。
5. 流与协程：打开的流必须关闭；扫描/加载任务可取消且取消后释放资源（T04、T06 验收）。

## Alternatives Considered

- `MANAGE_EXTERNAL_STORAGE` / 绝对路径直访：实现简单，但权限过宽、Play 政策与隐私成本高，且与分区存储方向相悖。否决。
- MediaStore：面向媒体文件，不适合任意目录的 Markdown / TXT 库。否决。
- 把 SAF 细节暴露给 Reader（Reader 直接持 treeUri 查询）：减少一层转发，但破坏模块边界、Reader 无法做纯 JVM 测试、授权语义泄漏到 UI 层。否决，采用 `DocumentSource` 隔离。

## Consequences

- `:core:storage` 承担所有 SAF、授权生命周期、content URI 解析与越界拒绝逻辑；`:core:reader` 只依赖 `DocumentSource` 接口（见 `docs/architecture/storage.md`）。
- 需要 SAF instrumented tests 覆盖慢查询、缺失元数据、深目录、中途失败（T05 验证），并至少实测一种真实 SAF provider。
- 相对资源解析规则成为安全边界，T10 用例必须覆盖空格/中文路径、嵌套目录、`../` 越界、重复标题片段、缺失目标。

## Follow-up

- 后台实时监视（FileObserver）明确不在 M0/M1 范围（T05 停止条件）；如需引入，另立 ADR 评估 E-Ink 功耗与稳定性影响。
- 授权丢失后的引导式恢复 UX 由 REQ-0001 细化，本 ADR 只约束状态可恢复。
