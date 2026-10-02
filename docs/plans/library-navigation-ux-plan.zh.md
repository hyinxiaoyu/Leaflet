# 资料库导航体验计划

[English](library-navigation-ux-plan.md) | 中文

日期：2026-10-01
状态：DELIVERED — 软件已验证，设备待补；NB-00 的需求门禁与 NB-01–NB-05 均已完成——见[交付报告](library-navigation-ux-delivery.zh.md)。
上级：[UI/UX 优化任务](ui-ux-optimization-tasks.md)，UX-05（ISS-014/015/016/017）。
任务：[资料库导航体验任务](library-navigation-ux-tasks.md)。
基线：[UX-07 交付](ux-07-delivery.md)；[已实现的资料库浏览与移除任务](library-browse-and-remove-tasks.md)。

## 目标与范围

让文件夹浏览更容易导航和操作：用户可以跳到祖先目录，扫描状态变化不会挤动文件列表，文件和文件夹行具有清晰的类型提示及至少 48 dp 的触控目标 (touch target)。

这是下一轮浏览改进，覆盖 `LibraryBrowseScreen` 及其所需的应用层导航状态。UX-04 的首页与最近阅读重构、UX-01 的外部打开 intent、全局 UX-02 调色板工作、全局 UX-10 字符串提取、Reader 行为、存储迁移和新依赖仍归属既有工作包。

## 已确认基线

| 证据 | 当前行为 | 实施含义 |
| --- | --- | --- |
| `LibraryScreens.kt`、`LibraryBrowseScreen` | `state.prefix` 是纯文字；只有进入目录和返回操作 | 根据已索引相对前缀增加祖先目录操作 |
| `LibraryViewModel.kt` | `openLibrary` 重置前缀；`enterDirectory`/`leaveDirectory` 负责前缀更新 | 文件夹导航留在 ViewModel；切换资料库时从对应根目录开始 |
| `BrowseLevel.kt` | 已索引目录与从文档推导的父目录生成带稳定键的直接子项 | 复用排序和空目录支持；不新增扫描或数据库表 |
| `LibraryScreens.kt` 的进度与提示分支 | 状态文字、按钮和间隔在列表上方出现或消失 | 预留稳定状态区域，避免按扫描状态插入间隔 |
| `LibraryScreens.kt` 的行 modifier | 文件行纵向内边距为 4 dp，文件夹行为 8 dp | 测量实际点击边界，设置最小高度而非固定行高 |
| `MainActivity.kt`、`LeafletNav.kt`、`Theme.kt` | 已启用边到边布局 (edge-to-edge)；这些路由没有明确的安全区域 scaffold；E-Ink 调色板只设置部分 token | 仅凭源码检查无法确认 UX-01/02 已就绪 |

源码审查确认了这些实现事实。本次规划未提供新的模拟器或实机证据。UX-07 测试数量是该轮交付的历史证据，不代表本轮验收。

## 验收决策

- **AC-N1 — 祖先目录：** 在 `Docs/Notes/2026` 显示资料库根节点和每一段路径。每个祖先节点打开对应精确前缀；当前位置有明确标识且不提供重复导航操作。根目录返回离开资料库；嵌套目录返回恰好向上一层。
- **AC-N2 — 路径身份：** 根目录使用空前缀。按完整路径段匹配；`a` 不是 `ab` 的祖先。保留大小写、空格、中文名称和字面百分号。只接受针对当前资料库生成的根目录、当前目录或祖先目标；过期或无关目标被拒绝，状态不变。不得把 content URI 当作路径字符串。
- **AC-N3 — 长路径：** 在窄屏和 200% 系统字号下，当前位置可辨且每个祖先目录都可达。优先使用横向滚动的面包屑 (breadcrumbs)，限制分段标签宽度并提供可访问的完整标签；换行不得因扫描状态更新而改变高度。新增面包屑控件也满足 48 dp 触控目标。
- **AC-N4 — 扫描稳定性：** 在宽度、字号缩放、前缀和已索引条目不变时，`IDLE`、`SCANNING`、`CANCELLING`、`SUCCEEDED`、`CANCELLED`、`FAILED` 之间切换，列表视口位置、尺寸及首个可见条目的键和偏移保持不变，允许一个物理像素的测量取整误差。刷新、取消、重试可操作；失败或取消原因可获取。文档索引变化或设备配置变化单独测试，不属于仅扫描状态变化时的不变量。
- **AC-N5 — 行操作提示：** 文件和文件夹可点击行高度至少 48 dp，名称换行或字号增大时能够增长，整行可点击。文件夹与文档提示及可读的 MD/TXT 标签不只依赖颜色。打开文件仍向 Reader 传递同一个文档 ID。
- **AC-N6 — 既有契约：** 保留空目录、目录优先于文件的排序、资料库相互独立、只读 SAF、扫描取消、失败后保留上次索引等行为。两个发行版共用同一实现及既有显示、运动策略。

<a id="nb-00-readiness-record-2026-10-01"></a>
## NB-00 就绪记录（2026-10-01）

**需求门禁：已关闭。** [REQ-0012 — 资料库导航（面包屑、稳定扫描状态、行触控目标）](../requirements/REQ-0012-library-navigation.md) 为 `READY`，并把本计划的 AC-N1–AC-N6 承接为 AC-1–AC-6。REQ-0001 保持 `IN_PROGRESS`；本切片不宣称其完成。

**基线。** `HEAD = 5a754292c1ea02f33af0cb917f5cf24c29dbb763`，工作区有 57 项未提交变更。未提交的 UX-06/UX-07 交付与 [2026-10-01 审查跟进](code-review-2026-10-01.zh.md) 修复保持原样；NB-01–NB-05 只改动了 `LibraryScreens.kt`、`LibraryViewModel.kt`、浏览层级辅助代码、其测试、新增字符串资源，以及配对文档。

**前置条件矩阵（2026-10-01 源码检查）。**

| 前置条件 | 结论 | 证据 |
| --- | --- | --- |
| UX-01 窗口安全区与 `ACTION_VIEW` 脚手架 | 未交付 | `app/src/main/kotlin` 中不存在 `windowInsetsPadding`/`safeDrawing` 调用（仅 `MainActivity.kt` 的 `enableEdgeToEdge()`）；`AndroidManifest.xml` 没有 Markdown/TXT 的 `ACTION_VIEW` 过滤器 |
| UX-02 E-Ink 颜色 token 与搜索高亮 | 部分完成 | `Theme.kt` 已有 EINK 分支与运动门禁，但没有 UX-02 的 token 赋值；搜索高亮改动缺失 |
| UX-04 统一资料库屏与最近阅读进度 | 未交付 | `LibraryListScreen` 仍是在 `Column` 中嵌套无权重 `LazyColumn`；行仍无图标或进度徽标 |

**依赖收窄与理由。** NB-01 无 UI 前置，直接开始。NB-02/NB-03/NB-04 收窄到 `LibraryBrowseScreen`、其行组件与浏览 ViewModel/状态；它们不需要 UX-04 的 `LibraryListScreen`/Recent 重构、UX-01 的安全区工作或 UX-02 调色板，自身也不引入新的调色板 token 或安全区处理。用户在 2026-10-01 明确授权在前置未完成的情况下继续。残余风险是 UX-01/UX-04 落地时共享文件 `LibraryScreens.kt` 需要变基；UX-01/UX-02/UX-04 仍归各自工作包所有，此处不作任何完成声明。UI 任务按记录的 NB-02 → NB-03 → NB-04 顺序串行推进。

**长路径与字号策略。** 面包屑是高度受限、可横向滚动的一行；视觉标签可以缩短，但完整名称始终暴露给无障碍服务。所有测量结论都在固定宽度与字号下给出，并包含 200% 系统字号用例，绝不作为通用高度。

## 前置条件与需求门禁

上级 UX-05 任务卡依赖 UX-01 和 UX-04；UX-04 依赖 UX-02。本拆分保留该依赖链。NB-00 记录它们的实际就绪状态和必要交接证据。缺少的前置交付在原工作包中处理，不偷偷并入面包屑任务。

[REQ-0001](../requirements/REQ-0001-folder-library.md) 当前为 `IN_PROGRESS`，覆盖浏览和扫描安全。[REQ-0004](../requirements/REQ-0004-display-profiles.md) 覆盖显示策略。实现前，NB-00 必须依据 [AGENTS.md](../../AGENTS.md)，为新增祖先操作和以上验收记录一份 READY 功能需求或 READY 的局部补充。本计划不修改 REQ 状态、不宣布前置工作已交付。缩减上级依赖必须有明确记录的理由和证据。

## 实现边界

保持既有 UI → ViewModel → storage 所有权。使用小型纯映射生成面包屑分段，通过带校验的 ViewModel 祖先转换复用 `browseLevel`、`parentPrefix` 和既有状态。UI 接收标签和目标，不遍历 SAF、不从 URI 推导路径、不改变索引。

导航到不同前缀时，该目录列表回到顶部；点击当前位置不做任何操作。重组合和仅扫描状态变化保留滚动状态。每目录滚动历史、进程死亡后的浏览路径恢复，以及改变从 Reader 返回时的恢复行为不在本轮范围内。

状态区域在给定宽度和字号缩放下保持外层边界稳定。根据支持的状态与操作布局确定尺寸，保证长路径和错误详情可获取且不挤动列表。不假设适用于所有设备的硬编码槽位高度，不用省略号隐藏失败。旋转或字号缩放变化后允许合理重新测量。

使用既有主题 token、运动策略、资源和测试依赖。新增用户可见标签进入 Android 字符串资源，提供英文和简体中文对应文本；这不授权全量提取历史字符串。复用既有图标资源或小型本地矢量 drawable，不引入图标库依赖。预计无需存储/API 迁移或替代既有 ADR。

## 执行与文件所有权

NB-00 → NB-01 → NB-02；NB-03 和 NB-04 在 NB-00 及外部 UI 前置条件之后执行；NB-05 在 NB-02/03/04 后执行。详细任务卡和停工条件见[任务拆分](library-navigation-ux-tasks.md)。

NB-02/03/04 共用 `LibraryScreens.kt`。虽然行为耦合有限，实现或集成仍按 NB-02 → NB-03 → NB-04 顺序进行。导航辅助函数和 ViewModel 文件先由 NB-01 负责，再交接给 NB-02。共享资源和页面测试改动串行集成。这是所有权规则，不是启动并行代理的指令。

## 验证与完成条件

使用既有 JUnit/Coroutines 和 Compose 插桩测试接缝，只增加验证真实路径转换、实测视口边界、可点击行边界的聚焦测试。替身数据源可以提供确定的资料库元数据和进度；测试应执行生产页面与导航链路，避免用测试专用 UI 镜像实现。

NB-05 使用冻结的 JDK 17 工具链执行门禁和两个 debug 发行版，再对每个发行版执行聚焦浏览页面套件。记录实际测试数、截图、失败命令及修正、未执行检查。E-Ink 实机残影、刷新及 TalkBack 需要独立证据；软件验收不代表发布或完成硬件验收。

```bash
./gradlew test lint ktlintCheck
./gradlew :app:assembleFullDebug :app:assembleEinkDebug
./gradlew :app:connectedFullDebugAndroidTest
./gradlew :app:connectedEinkDebugAndroidTest
```

以上是计划中的实现门禁，本次文档改动未执行这些命令。避免多个构建共用同一构建目录；必要时使用隔离源码副本。临时样本、日志、模拟器副本仅放在仓库 cwd 或 `/tmp`，用完清理，只保留明确记录的交付证据。

本次规划创建英文/中文计划和任务文件，并从 UX-05 添加入口。只验证配对、链接、引用和差异；本次不包含产品代码、构建、模拟器、临时数据或提交。
