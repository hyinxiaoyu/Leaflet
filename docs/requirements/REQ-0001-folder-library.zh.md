# REQ-0001 — 资料库与目录扫描

[English](REQ-0001-folder-library.md) | 中文


Status: IN_PROGRESS
Priority: P0
Milestone: M0（T04 存储基础 → T05 扫描浏览；M0 出口闭环入口）

来源：`docs/archive/init.md` §11（Library 模型）、§12（不复制原文件）、§13（目录扫描）、§26（MVP：添加/删除 Library 引用、目录浏览、文件发现、手动刷新）；`docs/plans/M0-M1-implementation-plan.md` T04/T05；`docs/research/eink-readers.md` §5 folder-first 落点。

## 问题

用户的大量 Markdown/TXT 资料已经按目录组织在设备目录中（AI 生成文档、技术资料、同步目录），且会被 Syncthing、Git、Termux、PC、Agent 脚本等外部工具持续更新。要求“导入成书籍/复制进应用”的阅读器与这一工作流冲突：复制即产生过期副本，用户无法信任阅读器内容。需要 folder-first 模型：Library 只是对用户目录的引用 + 元数据索引，原始文件始终是唯一事实来源。

## 用户故事

- 作为资料整理用户，我希望选择一个 Android 目录并将其添加为 Library，同时保留原始目录层级，以便无需导入或复制就能直接阅读 Markdown/TXT。
- 作为拥有多个资料来源的用户，我希望添加多个 Library（如 AI Documents / Work Notes / Research），以便隔离不同来源，并分别刷新或移除。
- 作为使用外部同步的用户，我希望外部工具修改文件后通过手动刷新看到新增、修改和删除，以便阅读器与原文件保持一致。

## 范围

包含：

- 多 Library 管理：经 `ACTION_OPEN_DOCUMENT_TREE` 选择系统目录、持久化 URI 授权（`takePersistableUriPermission`）、列表展示、移除引用。
- 递归元数据扫描：枚举 `.md` / `.markdown` / `.txt`（大小写不敏感），记录 `relativePath`、`uri`、`displayName`、`extension`、`size`、`lastModified`，保留原始目录层级用于浏览导航。
- 增量规则：`mtime` + `size` 均有效且与索引一致时跳过内容相关更新；任一缺失或不可靠时不宣称文件未变，按需重新读取。
- 手动“刷新资料库”：用户显式触发的整库/单库重扫；首次扫描只做元数据索引，不解析全文。
- 扫描状态机与静态/节流进度展示（已扫描文件数、当前目录），可取消；并发刷新合并或串行执行，不允许两个扫描任务对同一 Library 交错写索引。
- 消失项清理规则：仅当一次完整扫描成功完成后，才提交该 Library 本次未见条目的删除；部分失败、权限错误、取消不得把未扫描条目当删除。
- 授权丢失/文件消失的可恢复状态（错误态 + 重试/重新授权入口），提示文案见 UX。

## 非目标

明确不包含（`docs/plans/M0-M1-implementation-plan.md` §1 计划级不做项在本 REQ 全部适用）：编辑器、账号系统、云同步/自有云存储、AI Chat、RAG/向量数据库、在线 AI、PDF/EPUB/CBZ、手写笔记、OCR、复杂 annotation、双链知识库、插件系统、WebDAV/Git/Syncthing 客户端、完整标签系统、在线商店；以及：后台实时文件监视（FileObserver）、全库 FTS（仅 T11 当前文档搜索，FTS 留 M4）、把原文复制进应用内部存储作为默认模型、大屏双栏布局（架构不假设单栏，但 M0/M1 只交付单栏导航）、复杂动画。

## 用户体验

1. 空态：Library 列表页显示“还没有资料库”静态说明 + “添加资料库”按钮；E-Ink 下无旋转 loading 动画，用静态文字 + 计件进度。
2. 添加流程：点击添加 → 系统目录选择器 → 用户确认 → Library 以用户可编辑的 `displayName`（默认取所选目录名）出现在列表，状态为“扫描中（n/m）”→ 完成后显示文件数与 `lastScannedAt`。
3. 重复添加同一目录（同一 tree URI 规范化后相同）：不创建第二个 Library，而是定位到已有 Library 并提示“已存在”，其 `displayName` 可由用户改名而不影响去重。
4. 浏览：Library → 目录树（与原目录同层级）→ 文档条目显示标题（标题规则见 REQ-0002）+ 相对路径；空目录显示“空目录”占位；无支持文件的目录显示“无 Markdown/TXT 文件”占位。
5. 刷新：Library 行与 Library 详情页均有“刷新”操作；刷新期间显示节流更新的静态进度（更新间隔 ≥ 500ms），提供“取消”；取消后保留上次完整索引并提示“已取消，显示上次完整结果”。
6. 失败态：权限丢失显示“无法访问该目录（授权已失效），原文不受影响。[重新授权]”；部分失败显示“本次扫描未完成，已保留上次完整结果，未删除任何条目。[重试]”。
7. 移除 Library：二次确认“仅移除引用与本地索引，不删除原始文件”，确认后删除本地行并在无其他 Library 引用同一授权时释放持久授权。

## 功能需求

- FR-1（添加）：App 经 `ACTION_OPEN_DOCUMENT_TREE` 获取 tree URI 后调用 `takePersistableUriPermission` 持久化读授权，创建 `Library{id, displayName, rootTreeUri, createdAt, lastScannedAt}`。同一规范化 tree URI 重复添加必须去重定位，不得建第二行。（映射 T05 验收“重复添加行为确定”）
- FR-2（递归发现）：对 Library 树做递归（BFS/DFS 实现不限）元数据枚举，仅收录扩展名 `.md/.markdown/.txt`（大小写不敏感）；记录 `Document{libraryId, relativePath, uri, displayName, extension, size, lastModified}`；`relativePath` 以所选树根为基准、分隔符统一为 `/`。（映射“文件发现、保留目录层级”）
- FR-3（首扫不解析全文）：首次扫描与常规刷新默认只读取 `path/filename/size/mtime/uri`，不得打开文件流解析 Markdown 全文、提取标题/heading 或建 FTS。（映射 T05 验收“首次扫描不解析全文”）
- FR-4（增量）：重扫时 `mtime` 与 `size` 均可得且与已存记录完全一致 → 跳过该文件；任一不可得、不可靠（如 provider 返回 0/缺失）→ 视为可能变化，按需重新读取元数据/内容，不得标记“未变”。（映射 T05 增量规则）
- FR-5（消失项清理）：仅当单次扫描遍历完整结束且无权限错误、无取消时，才删除本库中本次未见的 Document 行；任何部分失败/权限错误/取消 → 跳过清理并保留旧索引。（映射 T05 删除规则）
- FR-6（手动刷新）：提供 Library 级手动刷新；刷新任务可取消（取消 ≤ 1s 内停止枚举、不再写索引）；同一 Library 的并发刷新请求合并为一次或串行排队，不得交错写。（映射 T05“扫描可取消、并发刷新合并或串行”）
- FR-7（进度状态）：扫描对外暴露 `Idle/Scanning(fileCount, currentDir)/Cancelling/Succeeded/Failed(reason)/Cancelled`；进度回调节流（≥ 500ms 一次），供 UI 做静态展示。（映射“静态/节流进度状态”）
- FR-8（移除语义）：移除 Library 只删除本地 Library/Document/位置等引用行，不删除、不修改原始文件；释放持久授权前检查是否仍被其他 Library 引用，仍被引用则不释放。（映射 T04 验收）
- FR-9（元数据缺失容错）：单个条目查询失败（`DocumentFile` 不存在、列缺失）不得中断整库扫描，记为该条目级错误并继续；条目错误数与原因在完成态中可查。
- FR-10（只读边界）：扫描全程只读；不写回原文、不复制原文到应用私有目录、不请求 `MANAGE_EXTERNAL_STORAGE`。（映射 T04 边界）

## 验收标准

- AC-1：给定已授权目录树（含 3 级子目录、20 个 `.md` + 5 个 `.txt` + 3 个 `.png` + 1 个空目录），执行添加 → Library 出现，Document 行数 = 25，目录浏览层级与原树一致，空目录显示占位，无全文解析调用（以 Repository 调用日志/测试替身断言）。
- AC-2：重复添加同一目录（再次选择同一 tree URI）→ Library 总数不变，定位到已有 Library 并提示已存在。
- AC-3：外部新增 2 个 `.md`、修改 1 个文件内容（`size` 或 `mtime` 变化）、删除 1 个文件后手动刷新 → 新增/修改/删除均在浏览列表中反映；删除项不再出现。
- AC-4：修改 1 个文件但保持 `mtime+size` 不变（测试替身构造）→ 允许跳过；`mtime` 返回缺失的 provider 下 → 该文件被重新读取而非标记未变。
- AC-5：在扫描中途取消 → 取消后 1s 内无新索引写入，旧索引完整可用，状态为 Cancelled 且提示显示上次完整结果时间。
- AC-6：构造“枚举到一半抛权限错误”的 provider → 扫描状态为 Failed，旧索引零删除（Document 行数与扫描前一致），UI 显示重试入口。
- AC-7：移除 Library → 其 Document/位置行被清除，原始目录文件逐字节不变（测试前后 checksum 对比），无其他引用时持久授权被释放、有共享引用时保留。
- AC-8：所有 AC 断言均基于可观测行为（行数、列表内容、状态、checksum），不出现“支持XXX”无判据表述。

## 边界情况

- 空目录 / 全是不支持扩展名：建库成功，文件数为 0 并显示占位，不报错。
- 深目录（≥ 15 层）、超多条目（测试 provider 构造 5000+ 条目）：扫描可完成或可取消，不 ANR；枚举上限策略（如有封顶数）写入 Open Questions 而非静默截断——默认不限定硬封顶，性能基线见 REQ-0011 占位。
- 文件名含空格/中文/emoji/大小写混合扩展名（`.MD`）：正常发现，`relativePath` 往返一致。
- 同一目录被授予两次不同 `displayName`：仍去重为一行，改名不影响去重键。
- 授权在进程重建后仍有效（T04 持久化）；用户在系统设置中撤销授权 → 下次访问进入失败态而非 crash。
- provider 返回 `size=0` 且 `mtime` 缺失的条目：按 FR-4 视为不可靠，不跳过。

## E-Ink 考量

- 扫描进度用静态文字 + 计件（“已扫描 120 项”）+ 细进度条，更新节流 ≥ 500ms；禁止旋转/spinner 无限动画、shimmer、crossfade。
- 高对比纯色文字；刷新/取消按钮为静态 hit 区，无 ripple 或极弱 ripple（遵循 DisplayProfile，见 REQ-0004）。

## Standard Android 考量

- full flavor 的 Standard 模式允许轻微 ripple 与正常滚动，但同样不得为扫描进度引入复杂过渡动画；进度展示与 E-Ink 共用同一状态机，仅视觉强度可不同（由 DisplayProfile 控制）。

## 性能考量

- 首次扫描时间复杂度与条目数线性相关；不得在主线程执行枚举；单次 `ContentResolver` 查询游标必须关闭；取消通过协程取消传播。
- 性能阈值（多少条目/多少秒）未验证，留 `docs/performance` 基线（T03/T13）实测后回填，见 Open Questions；本 REQ 只冻结“可取消、无 ANR、无交错写”的行为契约。

## 存储与数据影响

- Room：`Library` 表、`Document` 表（外键 `libraryId`，级联删除本地行）；仅存元数据与 `uri` 字符串，不存正文、不存绝对文件路径（用 tree URI + `relativePath`）。
- DataStore：不存扫描结果；可存上次扫描时间展示字段（与 Room `lastScannedAt` 二选一，T04 定案）。
- 数据库删除/卸载/移除 Library 后原始文件必须逐字节不受影响（AC-7 覆盖）。

## 测试计划

- JVM 单测（Repository + 假 provider）：AC-1–AC-4、AC-6 的去重/增量/清理规则；慢查询、缺失元数据、深目录、中途失败的 provider 变体。
- Instrumented 测试：至少一种真实 SAF provider（系统文件应用）实测添加→浏览→刷新→移除；记录原文件操作前后 checksum。
- 进程重建测试：授权持久化后杀进程重进，Library 可列出、可刷新。
- 不做全库 FTS、不测实时监视（Non-goals）。

## 依赖

- T02 最小工程（模块、Room/DataStore 基座）；T04 SAF 只读访问 + 持久授权 + DAO 契约；T03 测试样本与性能记录模板。
- 被依赖：T07 打开闭环（目录 → Reader 导航）、REQ-0002（标题提取按需解析）、REQ-0003（位置/最近阅读外键）。

## 增补（2026-09-28）

A1 与 A2 已实现，包括 [资料库浏览与移除任务](../plans/library-browse-and-remove-tasks.zh.md) 里的 LT-3 目录元数据。

### A1 — 浏览保持目录结构

资料库 (Library) 浏览一次只显示一个目录层，与所选文件夹的层级一致，而不是把全部已索引文档平铺在同一张列表里。

当前层先列出子目录，再列出文件。两组都按各自名称做大小写不敏感排序，名称完全相同时再用原始字符串打破平局。进入子目录后只显示该目录的直接子项。返回则回到上一层。资料库根目录显示根的直接子项。

本条不改变哪些文件会被索引。除每个受支持文件外，扫描还会为每个访问到的子目录保存一行纯路径记录，因此没有任何已索引 Markdown 或 TXT 后代的文件夹（空的，或只含不受支持文件的）会出现在其父层。目录行不携带文件正文、不复制原文，只在一次完整成功的扫描中被替换，打开时显示浏览页的空占位文案。

### A2 — 长按移除选中的资料库

在资料库列表上长按一条资料库，确认后移除该资料库。这里的资料库就是用户添加时选中的那个目录。

移除会删掉本地资料库行、该库的已索引文档 (Document) 行、其已索引目录行、这些文档的阅读位置，以及该库的最近阅读记录。不删除、不修改原始文件。仅当没有其他资料库仍使用同一授权时，才释放持久授权。确认文案说明只移除本地引用和索引。

在浏览树内部长按不会删除子目录。该手势不在本条范围内。

## 待确认问题

- 大库枚举是否需要封顶数：500 篇文档上限已去掉，扫描会索引本次遍历里发现的全部受支持文档。仍保留访问条目 5000 的遍历上界（目录、文件、残缺行都计数），超限显式 FAILED + 保留旧索引（`LibraryScanner` 单测覆盖）。该上界数值仍 UNVERIFIED，待真机性能基线后重定。
- `contentHash` 字段（init §11 标 optional）：M0/M1 不引入，仅 `mtime+size`（mtime=0/缺失视为未知，`size=-1/mtime=0` 哨兵永不命中跳过）。算法与成本未验证，另立任务。
- 重复添加判定键用规范化 tree URI 是否足以覆盖所有 provider 的 URI 别名情况：部分验证 —— 同一规范化 URI 去重已测（单测 + instrumented）；文本不同但同授权的别名仍视为不同 Library（`TreeUris` KNOWN LIMIT 保留），移除时按共享授权检查不提前释放。真机多 provider 别名实测仍 UNVERIFIED（T13）。
- 性能阈值（冷启动/扫描时长/内存峰值）待 T03 样本 + T13 实机基线回填。
