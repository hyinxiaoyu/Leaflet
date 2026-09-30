# 存储架构

[English](storage.md) | 中文

## 1. 表结构（Room）

```text
Library
--------
libraryId   : String（PK，应用内部稳定标识，UUID）
displayName : String（用户命名，如 "AI Documents"）
rootTreeUri : String（SAF tree URI 字符串；显示与授权恢复用，不当文件路径拼接）
createdAt   : Long
lastScannedAt : Long?

Document
--------
documentId   : String（PK，应用内部稳定标识；见 overview 契约 1）
libraryId    : String（FK → Library.libraryId，CASCADE 删除本地行；删库不删原文）
relativePath : String（库内相对路径，显示与层级浏览用）
uri          : String（content URI 字符串；访问用，不假设重命名后稳定）
displayName  : String（文件名）
title        : String?（按标题规则提取，可为空回退显示）
extension    : String（md / markdown / txt，小写归一）
size         : Long
lastModified : Long
contentHash  : String?（可选；M0/M1 可为空，未来增量依据）

Directory（每个已索引文件夹一行；数据库版本 2，LT-3）
--------
libraryId    : String（复合 PK / FK → Library.libraryId，CASCADE 删本地行；删库不删原文）
relativePath : String（复合 PK；`/` 分隔的库内文件夹路径；不含文件正文）

ReadingPosition（版本化位置，按 documentId 一行）
--------
documentId  : String（PK/FK → Document.documentId）
version     : Int（契约版本，默认 1）
blockIndex  : Int
blockOffset : Int
progress    : Float（0..1）
anchor      : String?（标题 anchor 或引用文本片段）
updatedAt   : Long

RecentRead（最近阅读，MRU；上限 30，T04 落地；T03/T13 基线可重定）
--------
documentId  : String（PK，**无 FK** —— 文件删除/失权时保留快照行供可恢复提示，删库时由 repository 显式清理）
libraryId   : String（删库清理用，无 FK）
titleSnapshot : String（删除后仍可显示的标题快照）
lastOpenedAt: Long

- 外键约束：Document → Library（CASCADE），Directory → Library（CASCADE），ReadingPosition → Document（CASCADE）；RecentRead 无 FK。删 Library 级联删本地行 + repository 显式清 recents，**绝不触碰 SAF 原文**（ADR-0001）。
- 原文（文件正文）不得写入任何表；数据库仅存索引、进度、设置、UI 状态类辅助数据。
- Room schema 预留 FTS 扩展位（M4），但 M0/M1 不建 FTS 表、不做全库索引。
- Schema 导出入库在 `core/storage/schemas/<db-fqn>/`：`1.json`（已发布的 v1）与 `2.json`（当前 v2，新增 `directories`）。版本变更必须带已测 Migration 以及打开上一版 schema 的迁移测试（`DirectoryMigrationTest` 覆盖 1 → 2，见 `LeafletDatabase` KDoc）。重新生成 schema 使用 `core/storage/build.gradle.kts` 里记录的按需导出（`./gradlew :core:storage:kspDebugKotlin -Proom.schemaExport=true`），因为默认导出路径会触发上文提到的 Room 2.8.5 + KSP 错误。KSP 构建怪象见 `core/storage/build.gradle.kts` 注释：从零验证前先 `./gradlew --stop` 并删模块 `build/` 目录。

## 2. SAF 只读访问

- 添加 Library：`ACTION_OPEN_DOCUMENT_TREE` + `takePersistableUriPermission(READ)`；保存 `rootTreeUri` 字符串。
- 移除 Library：只清本地引用（级联删行）；仅当该 tree 授权无其他 Library 共享使用时才 `releasePersistableUriPermission`。
- `DocumentSource`（overview 契约 3）是唯一文档访问出口：按 ID 开只读流（调用方必关）、库内相对资源解析（允许树内 `..`、拒绝越授权树、content URI 不拼路径、URI 编码/规范化一致、未知 scheme 不执行）。
- 失权/文件消失/取消选择/拒绝授权一律映射为可恢复的 `ReaderState.Failed` 或扫描 `FAILED/CANCELLED`，不 crash。
- 默认离线：远程图片不自动联网（静态占位）；外链仅用户点击时交外部应用；无可用外部应用时不崩溃。

## 3. 扫描规则（T05 落地）

### 3.1 增量规则

- 首次扫描只做元数据扫描（path / filename / size / mtime / uri），建立 Document 索引；**不解析全文**，不抽标题/TOC（按需在打开时解析）。显示用 `title` 列存文件名去扩展名回退（T06/REQ-0002 接管真实标题提取）。
- 后续扫描以 `(mtime, size)` 为跳过依据：两者有效（mtime 非 0/非缺失）且与库中可靠记录相同 → 跳过（`LibraryScanner.unchanged`）；缺失或不可靠 → **不宣称文件未变**，重新索引元数据。未知元数据以 `size=-1 / mtime=0` 哨兵入库，永不命中跳过。
- 未来可加 `contentHash`，另立任务，不在本架构预设算法。

### 3.2 删除提交规则

- 只有**完整扫描成功后**才提交消失项清理（库中存在但树中已无对应项 → 删本地行；位置行随文档行级联删除；原文不受影响）。畸形目录行（缺 id/name）使子树证据不可证 → 该轮跳过清理但仍报告成功 + 提示。
- 部分失败、权限错误、取消、枚举上溢：**不得把未扫描条目当删除**。失败/取消/上溢提交零索引写（旧索引逐行保留）。

### 3.3 取消与并发语义

- 扫描可取消（协程取消，每目录/每条目检查）；取消后释放资源（游标在 lister `use {}` 内关闭），状态 `CANCELLED`，清理不提交。
- 同一 Library 的并发刷新串行排队（per-library mutex，不交错写；FR-6 允许合并或串行，本实现选串行）。
- 进度为静态/节流状态（`ScanProgress`，≥ 500ms，文字 + 计件），无动画。

### 3.4 发现与浏览

- 递归元数据枚举（BFS 经 `DocumentsContract` 子文档查询）`.md / .markdown / .txt`（大小写不敏感），保留原始目录层级；空目录与不支持文件可处理（成功、0 文档）；重复添加按规范化 tree URI 去重 + UI “已存在”提示。
- LT-3：每个访问到的子目录都以纯路径 Directory 行写入，与文档在同一个提交事务里；因此没有任何已索引文档的文件夹仍会出现在其父浏览层。浏览层把这些行与文档路径推导出的父路径片段合并。目录行遵守上面的删除提交规则（子树证据缺失时不做清理），且从不读取正文、不复制原文。
- 排序按名忽略大小写（每目录枚举后排序，提交前按 `relativePath` 忽略大小写全排序）；深目录（18 层已测）、中途失败、慢查询、缺失元数据由 T05 provider 测试覆盖；stub tree provider 经真实 `ContentResolver` 路径实测。
- 遍历上界（UNVERIFIED，待真机基线重定）：单次扫描最多访问 5000 个条目（目录、文件、残缺行都计数）。超限即 FAILED + 显式原因 + 保留旧索引 + 重试入口，绝不静默截断。上界之内发现的受支持文档全部索引。原先的 500 篇文档上限已去掉；那是从其他阅读器抄来、未经 Leaflet 实测的文件夹限制，不是测得的性能上限。

## 4. DataStore 分工

- 存：显示模式选择、字号/行距/页边距/字体选择、翻页 overlap（3%–8%内可调，默认 5%）、菜单状态等设置与 UI 状态。
- 不存：文档正文、SAF 授权替代物、大列表（recents 全量在 Room，DataStore 只存轻量开关）。
- 腐蚀数据回退空默认；进程重建后授权与设置恢复可用。
