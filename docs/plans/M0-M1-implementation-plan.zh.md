# Leaflet M0 / M1 实施任务计划

[English](M0-M1-implementation-plan.md) | 中文

状态：PLANNED（仅规划，未开始实现）
日期：2026-09-17
需求来源：`docs/archive/init.md` §1–63
当前仓库：仅有 `docs/archive/init.md`，无 Android 工程、构建配置或现有实现。

## 1. 目标与范围

M0：建立双 flavor 可构建的工程，以及“添加目录 → 浏览 → 阅读 → 恢复位置”的基础闭环。

M1：完成 `docs/archive/init.md` 定义的 E-Ink MVP，形成可验收的 Alpha 发布候选。实际签名与对外发布独立于开发验收。

本计划只定义任务、边界、依赖、交付物及验收；不表示任何实现或验证已完成。后续 agent 每次只领取一个满足依赖的任务。

明确不做：编辑器、账号、云同步、AI/RAG、PDF/EPUB、完整标注、插件、全库 FTS、厂商 SDK 集成、大屏双栏、复杂动画。不得以架构预留为由创建这些功能的空壳实现。

## 2. 需求解释与待决策事项

| 事项 | 本计划采用的解释 | 来源与处理 |
| --- | --- | --- |
| E-Ink flavor 的 Standard 模式 | M0/M1 两个 flavor 均有基础模式切换；eink 默认 EINK | §6、§26 的明确切换要求优先于 §42 的 README 示例；记录 ADR，README 必须如实描述。以后锁定 E-Ink-only 需单独变更需求 |
| 表格、代码块、相对资源 | M1 提供基础可用实现；M2 改进性能与体验 | §10、§15、§23、§24 已明确要求，不能按 §39 的 roadmap 将全部支持延期 |
| DisplayProfile 引入时间 | 抽象与策略在工程基础阶段建立，完整阅读交互后续接入 | §62 的首次交付要求优先；§61 Phase 4 解释为完整接入与验证 |
| 大文档 | 10 MB 不承诺完整富文本体验，但必须可取消、无 OOM/ANR/crash；允许有说明的纯文本降级 | 不允许静默截断、假装读取完成，或仅以拒绝所有大文件代替阅读路径 |
| 图片与外链 | 本地图片和相对文档链接必做；远程图片显示静态占位；外链仅在用户点击时交给外部应用 | MVP 不新增网络 SDK，不以渲染 Markdown 为由默认访问网络 |
| 字体 | 使用系统字体提供基础选择；不默认打包字体文件 | 满足基础能力并控制 APK 大小 |
| 收藏 | 暂不纳入 MVP 功能 | §开头允许数据库存储收藏，不等于 §26 要求实现收藏 |
| 许可证、正式 applicationId、签名 | 许可证待项目所有者选择；使用临时 ID；正式发布前确认 | 不擅自授予开源许可，不生成或提交生产签名材料 |

以下技术问题由前置任务收集证据后定案，当前不宣称兼容性已验证：JDK/Gradle/AGP/Kotlin/Compose 组合、minSdk/targetSdk、Markwon 维护与扩展兼容性、目标 BOOX Android 版本、性能阈值、最终许可证。

许可证与正式 ID 不阻塞本地开发。目标设备暂不可得时可以继续实现，但不得把 E-Ink 实机验收标记为通过。

## 3. 架构建议与所有权

建议从 6 个 Gradle 模块起步，具体在 T01 冻结：

| 模块 | 职责 | 允许依赖 |
| --- | --- | --- |
| `:app` | Compose Shell、导航、ViewModel、手动 DI、flavor 配置；按 library/reader/settings 分包 | 其余核心模块 |
| `:core:model` | Library、Document、ReadingPosition、轻量 ID/值类型 | Kotlin 基础库；不依赖 Android UI |
| `:core:storage` | SAF 访问、扫描、Room、DataStore、Repository | model |
| `:core:markdown` | 原生解析适配、标题、TOC、渲染内容及位置映射 | model、经审计的 Markdown 依赖 |
| `:core:reader` | View/RecyclerView 阅读组件、布局、滚动、搜索定位、交互 | model、markdown、eink |
| `:core:eink` | DisplayProfile、MotionPolicy、EInkController、通用策略 | Android 基础 API；不依赖 app/storage |

设计系统先放 `app` 中，通过参数向 Reader 提供样式。数据库和 storage 先同模块、分包管理。暂不创建 `feature:*` 空模块或大型 DI 框架；有独立构建/复用收益后再拆分。

Reader 不直接访问 Room、SAF 或厂商 SDK。App 通过 Repository 获取文档流与元数据，将可取消的加载任务交给 Reader。Markdown 适配可以使用解析器 AST，不先发明覆盖全部 Markdown 的自研 AST/解析器。

基础契约在 T01 定义、T02 建立最小可编译形态，至少包括：

- 文档 ID 为应用内部稳定标识；URI/relativePath 是访问与显示信息，不假设重命名后 URI 一定稳定。
- LibraryRepository：添加、列出、移除引用、刷新及扫描状态；DocumentSource：按 ID 打开只读流、解析库内相对资源。
- Reader 输入、加载/错误/降级状态，以及版本化 ReadingPosition；位置包含 blockIndex/blockOffset/progress/anchor 等可选字段，不只依赖像素值。
- ParsedDocument 的标题、TOC、可渲染内容与定位映射；Reader 导航不能假设一整个文件对应一个 TextView。
- DisplayProfile/MotionPolicy 控制动效、滚动、对比度与刷新策略；AppCapabilities 控制功能可用性，二者不混用。
- EInkController 的进入、退出、翻页、滚动、刷新请求；MVP 仅实现确有用途的 NoOp/通用适配，不创建假的 Onyx 实现。

契约发生破坏性变化时暂停依赖任务，由架构负责人更新 ADR 与任务影响范围；不得自行重构其他 agent 的实现。

## 4. 里程碑与依赖顺序

| 批次 | 任务 | 进入条件 | 退出条件 |
| --- | --- | --- | --- |
| A：方案准备 | T00 | 无 | 技术证据与未决项明确 |
| B：需求/契约 | T01 | T00 | REQ、ADR、接口边界可供实施 |
| C：首次初始化 | T02 | T01 | 双 flavor 编译，最小 Library/Settings，基础策略与 CI |
| D：基础设施 | T03、T04 | T02 | 测试样本、只读存储基础 |
| E：阅读闭环 | T05、T06 | T03、T04 | 目录发现、基础阅读；两任务可并行 |
| F：闭环持久化 | T07 | T05、T06 | M0 基础闭环验收 |
| G：MVP 阅读能力 | T08 → T09 → T10 → T11 | T07 | 导航、排版、资源、搜索均可用 |
| H：质量与交付 | T12 → T13 | T08–T11 | 模式集成、设备/性能/发布候选验收 |

首次构建 agent 的任务范围只到 T02；不要自动执行完整 MVP。T00/T01 是前置规划与证据工作，不要求一轮包办所有任务。

仅允许在文件所有权清晰时并行：T03/T04；T05/T06。阅读模块内部默认串行，避免多个 agent 同时修改 Reader 会话、Adapter、定位与手势。全局 Gradle/版本目录由当前集成任务负责，不允许并发修改。

## 5. 任务卡

### T00 — 技术证据与可行性审计

- 依赖：无。性质：只读调研，无生产实现。
- 范围：检查本地 Android/JDK 工具链；查官方版本兼容表；研究 init §46 的 PilcrowMD、Markdown Reader、Markor、KOReader、OnyxAndroidDemo、Markwon。
- 交付：`docs/research/README.md`、`markdown-renderers.md`、`eink-readers.md`、`onyx-epd.md`；记录来源 URL、日期、tag/commit、许可证、可借鉴设计与不采用原因。
- 必须比较：Markwon、WebView + marked、Compose Markdown、自研 parser；重点确认 RecyclerView、表格、任务列表、相对资源、超长单块的限制。
- 验收：给出一套可实施的固定版本组合及其依据；无法确认的项明确标注。禁止只列库名、依赖热度或未验证的“支持”。
- 停止条件：形成首选方案与风险清单；不集成 Onyx SDK，不复制参考项目代码。

### T01 — 需求、架构与交接契约

- 依赖：T00。
- 交付：根 `AGENTS.md` 草案；product vision/principles/roadmap；REQ/ADR 模板与索引；architecture overview/reader/storage/eink。
- 需求编号固定：REQ-0001 资料库与扫描、0002 MD/TXT 渲染、0003 位置与最近阅读、0004 显示策略与 flavor、0005 翻页与手势、0006 TOC、0007 排版/表格/代码、0008 文内搜索、0009 本地资源与链接、0010 设置/About、0011 性能与发布验证。
- 架构决策：ADR-0001 folder-first、0002 SAF、0003 flavors/capabilities、0004 native renderer、0005 EInkController；补充模块边界、位置恢复和大文件降级决策。
- 验收：每条 MVP 要求能映射到 REQ 和本计划任务；每份 REQ 包含 init §35 的字段；状态使用 §36 枚举。证据不足的 ADR 保持 Proposed，不能伪装 Accepted。
- 停止条件：实现任务的关键契约明确，待开发 REQ 可设 READY；构建命令暂标“待 T02 验证”。

### T02 — 最小工程与双 flavor（首次实现交付）

- 依赖：T01。对应：REQ-0004、0010、0011 的基础部分。
- 范围：Gradle Wrapper、Kotlin DSL、统一版本目录、模块、manual DI、共享 Compose Shell、Library 空态、Settings/About、最小 DisplayProfile/MotionPolicy。
- flavor：`full`/`eink`，同一 dimension；临时 applicationId 集中配置；Reader 不复制到 flavor 目录。两个 flavor 默认模式分别 STANDARD/EINK。
- 交付：最小工程；README、CHANGELOG、开发/测试/发布文档、基础 GitHub Actions、THIRD_PARTY_LICENSES。许可证未定时显式记录待定，不擅自填入 MIT 等许可。
- 验收：两个 debug APK 可构建、安装启动、区分应用身份；最小页面可导航；双 flavor 编译/单测/lint 纳入 CI；格式检查工具和实际命令明确。
- 验证：从实际 Gradle tasks 确认命令后执行。未提供设备时，构建与启动验证分别报告。
- 停止条件：满足 init §62 的基础交付；不顺带实现 SAF、完整 Reader 或示例假业务数据。

### T03 — 阅读样本与测试基线

- 依赖：T02。对应：REQ-0011。
- 范围：`testdata/markdown/` 覆盖 init §48 的全部样本；补充空文件、大 TXT、重复标题、BOM、损坏编码、超长单段/单代码块、嵌套列表、图片和相对链接目录。
- 交付：确定性样本生成脚本、fixture 说明、性能记录模板、`eink-test-matrix.md`。
- 验收：可生成 100 KB/1 MB/5 MB/10 MB 样本并校验大小；覆盖“多小块”和“单个巨大块”；测试资源不进入正式 APK；不复制来源不明文档。
- 停止条件：提供可复用输入与断言目标，不编造性能测量数据。

### T04 — SAF 与持久化基础

- 依赖：T02。对应：REQ-0001、0003、0010。
- 范围：Library/Document Room 表与外键、版本化位置/最近阅读；DataStore 设置；SAF 只读访问与持久授权；明确流关闭与协程取消。
- 边界：不使用绝对文件路径替代 content URI；不依赖 MANAGE_EXTERNAL_STORAGE；原文不得写入数据库或默认复制为内部文档。
- 验收：授权经过进程重建仍可用；取消选择、拒绝授权、授权丢失、文件消失均有可恢复状态；移除 Library 只清本地引用；共享授权仍被使用时不提前释放。
- 验证：Repository/DAO 测试及 SAF instrumented tests；记录原文件操作前后校验结果。
- 停止条件：提供可测试的数据契约，不实现递归浏览 UI。

### T05 — 资料库扫描与目录浏览

- 依赖：T03、T04。对应：REQ-0001。
- 范围：多 Library、系统目录选择、递归元数据扫描、`.md/.markdown/.txt` 发现、保留目录层级、手动刷新、静态/节流进度状态。
- 增量规则：mtime/size 有效且相同可跳过；缺失或不可靠时不宣称文件未变；按需重新读取。扫描可取消、并发刷新合并或串行。
- 删除规则：完整扫描成功后才提交消失项清理；部分失败、权限错误、取消不得把未扫描条目当删除。
- 验收：重复添加行为确定；新增/修改/删除可刷新反映；空目录与不支持文件可处理；首次扫描不解析全文；导航保持原始目录层级。
- 验证：测试 provider 覆盖慢查询、缺失元数据、深目录、中途失败；实测至少一种真实 SAF provider。
- 停止条件：文件选择可交给 Reader，不做后台实时监视或全库搜索。

### T06 — 原生 Markdown/TXT 基础 Reader

- 依赖：T03、T04。对应：REQ-0002。
- 范围：文件打开、可取消后台解析、TXT、Markdown 基础语法、加载/失败状态、标题提取；实现与后续定位可兼容的分块渲染。
- 标题：frontmatter title → 首个 H1 → 文件名；限定 frontmatter 支持子集，异常元数据不得使正文失败。
- 技术门槛：在真实样本上验证 Markwon 适配；不能无条件把整篇文档装入单 TextView。单个巨大块需有分段纯文本降级或等效有界策略。
- 验收：H1–H6、段落、粗斜体、删除线、列表、任务列表、引用、代码、分隔线可读；未知语法降级；UTF-8/BOM 策略明确；未知编码有明确提示或降级，不虚报自动识别。
- 验证：中日英/emoji、空文件、malformed、10 MB 样本；后台任务快速切换时旧结果不能覆盖新文档；取消后释放资源。
- 停止条件：基础 Reader 可独立验证；表格交互、图片、搜索等按后续任务接入。

### T07 — 打开闭环、阅读位置与最近阅读

- 依赖：T05、T06。对应：REQ-0003。
- 范围：目录到 Reader 导航、阅读位置节流保存与退出补存、最近阅读、进程重建恢复。
- 恢复策略：同版本文档优先块/偏移；内容变化、字体/宽度变化后校验位置并回退至 anchor/progress；不假设块序号跨编辑稳定。缓存必须按文档变化失效。
- 验收：退出/重启恢复到合理阅读位置；删除、重命名或失去权限的最近项有明确提示；Reader 页面重建不重复全文解析无变化内容。
- M0 出口：两个 flavor 均能“添加资料库 → 浏览 → 阅读 MD/TXT → 退出 → 恢复”。不能把 M0 出口等同 M1 功能完成。

### T08 — TOC、翻页与交互仲裁

- 依赖：T07。对应：REQ-0005、0006。
- 范围：TOC 层级、重复标题 anchor、点击跳转；连续滚动/PAGE_TURN 切换；左/中/右区域；菜单显示隐藏。
- 翻页：按 Reader 实际可见高度计算，默认 overlap 5%（需求允许 3%–8%）；处理 Insets、菜单遮挡、旋转、首尾边界；E-Ink 禁止平滑滚动。
- 交互优先级：链接、按钮、代码复制、横向表格/代码手势优先于翻页区域；长按不得误翻页。
- 验收：TOC 跳到对应内容；连续快速翻页不越界；横向滚动不触发翻页；模式切换保持当前位置。
- 停止条件：不实现真正分页排版引擎、可配置 tap-zone 编辑器或完整手势系统。

### T09 — 排版、代码块与表格

- 依赖：T08。对应：REQ-0007、0010。
- 范围：字号、系统字体、行距、页边距；代码等宽字体、语言标签、复制、soft-wrap；宽表格水平滚动。
- 规则：E-Ink 默认代码换行；关闭换行后可横向滚动；仅实现 NORMAL 表格模式，WRAP/CARD 记录为后续扩展，不展示不可用开关。
- 验收：设置持久化；排版变化保持阅读上下文；窄屏能访问全部表格列和长代码行；深色/灰度下文字可辨；不要求强制语法高亮。
- 停止条件：不引入字体包、表格卡片渲染或复杂语法着色引擎。

### T10 — 相对链接与本地图片

- 依赖：T09。对应：REQ-0009。
- 范围：库内相对 MD/TXT、目录上跳与 fragment、同文档锚点、本地相对图片、用户点击外链。
- 安全边界：基于已授权树和父目录解析，不能把 content URI 当普通路径拼接；允许树内 `..`，拒绝越出授权树；URI 编码/规范化策略一致。未知 scheme 不执行。
- 图片：按视口采样、限制解码内存、取消加载与释放资源；不存在/损坏/不支持的图片提供占位；远程图片不自动联网。
- 验收：空格/中文路径、嵌套目录、`../`、重复标题片段、缺失目标均有测试；无可用外部应用时外链操作不崩溃；超大尺寸图片不引发 OOM。
- 停止条件：不扩展网络图片、任意跨库访问或内置浏览器。

### T11 — 当前文档搜索

- 依赖：T10。对应：REQ-0008。
- 范围：当前已加载文档内字面量搜索、匹配计数、前后跳转、当前匹配高亮；Markdown 按可见文本搜索，不把语法标记当正文。
- 实现约束：使用解析/渲染位置映射，禁止直接把源码 offset 当屏幕坐标；搜索可取消，过期结果不覆盖；大文件/降级模式有明确搜索行为。
- 验收：中文、日文、emoji、重复匹配、无匹配、跨块查询规则、代码/表格文本均覆盖；切文档清除旧状态；不在每次输入时重新解析全文。
- 停止条件：不做正则、高级查询、后台全库索引或 FTS。

### T12 — 显示策略与设置集成验收

- 依赖：T08–T11。对应：REQ-0004、0010。
- 范围：两套 DisplayProfile、MotionPolicy 全面接线、AppCapabilities、EInkController 生命周期事件、设置与 About 完整化。
- 验收：两 flavor 可热切模式、恢复后保持选择；E-Ink 下动画、smooth scroll、overscroll、ripple、crossfade 均符合关闭策略；无无限 loading 动画；Standard 基础阅读正常。
- 刷新边界：Generic/NoOp 不得声称能触发实际硬件局刷/全刷；设置中不展示无法兑现的硬件能力。系统刷新表现留给实机矩阵验证。
- 停止条件：不接 Onyx SDK、不制作普通 Android 专属动画；控制策略集中，Reader 不散落设备品牌判断。

### T13 — 质量门禁与 E-Ink Alpha 交付

- 依赖：T12。对应：REQ-0011，复核全部 MVP REQ。
- 范围：双 flavor 编译/单测/lint/格式检查、instrumented tests、release 构建检查、APK 分析、真实性能与 E-Ink 实机矩阵、文档交付。
- 交付：`docs/performance/apk-size.md`、性能基线、`docs/development/eink-test-matrix.md`、构建/测试/发布说明、完整第三方许可清单、CHANGELOG。
- APK：分别记录 fullRelease/einkRelease 字节数、构建参数、R8 配置、签名状态、依赖/native/font/image 组成；初版大小接近是允许结果，不得为制造差异加入无用依赖。
- 性能：记录设备/系统/固件/版本与样本；测 cold start、Library open、Document open、翻页/滚动、内存峰值、扫描；建立可复测阈值后才设自动回归门禁。
- 设备：普通手机加 6/7/10.3 寸目标类别；尺寸自适应可用模拟器辅助，残影/刷新结论只能来自 E-Ink 实机。缺设备的行标 BLOCKED/NOT_RUN，并保留发布风险。
- 数据：卸载/清数据库/移除 Library 后原文件不变；release merged manifest 不包含未经需求批准的权限或网络能力。
- 完成：提交 init §63 要求的交付报告；仅证据齐全的 REQ 标 DONE。签名、许可证、正式 ID 或实机验证欠缺时，明确交付为候选包，不能宣称已正式发布。

## 6. 验证门禁

以下命令是候选，须由 T02 对实际任务名验证后写入 README/AGENTS；当前仓库尚不能执行：

```bash
./gradlew :app:assembleEinkDebug :app:assembleFullDebug
./gradlew test
./gradlew lint
./gradlew :app:connectedEinkDebugAndroidTest
./gradlew :app:connectedFullDebugAndroidTest
./gradlew :app:assembleEinkRelease :app:assembleFullRelease
```

格式检查命令在工具选定后补充，不编造不存在的 Gradle task。JVM 测试、Android 设备测试、E-Ink 实机测试分别报告；一个不能替代另一个。

通用 Definition of Done：

1. 只完成对应 READY REQ 和任务范围；代码、相关测试、双 flavor 编译通过。
2. 验收条件附证据，标出未执行项及原因；不以截图代替行为测试。
3. 无原文件修改、默认导入复制、flavor 重复 Reader 或未审计依赖。
4. 更新必要的 REQ 状态、架构说明、用户可见 CHANGELOG；新增依赖记录许可证和体积影响。
5. 留下清晰交接：变更文件、已验证命令与结果、已知限制、下一任务依赖是否就绪。

## 7. 快速 agent 派发模板

```text
执行 docs/plans/M0-M1-implementation-plan.md 中的 TXX，仅限该任务。

先读取：根 AGENTS.md、对应 REQ、相关 architecture/ADR、任务前置交付。
前置状态：列出已完成任务与证据；依赖未就绪不得伪造实现。
允许修改：任务所属模块与必要测试/文档。
禁止扩展：不引入需求外功能，不并发改全局版本配置，不擅自改变已冻结契约。

实施前确认验收条件；发现需求冲突或方案失效时提供证据和最小替代方案。
完成后报告：文件、行为变化、验证命令/结果、未验证项、剩余风险。
达到任务停止条件即结束，不自动领取后续任务。
```

## 8. 发布前决策与回退

- T00/T01 阻塞技术定案的问题：实际设备 Android 基线、原生 renderer 对所需语法与大文件的可行性。证据不足时保留提案状态。
- M1 对外发布前由项目所有者决定：许可证、正式包名、分发渠道、签名管理。开发 agent 不代为选择法律授权或提交生产密钥。
- 每个逻辑任务独立提交；失败优先回退该任务的提交或关闭其能力，不重置用户工作区。数据库变更需提供迁移测试，不以清库掩盖位置/设置丢失。
- Markdown 路径不稳定时使用已验证的只读纯文本降级；厂商刷新不可用时使用通用系统刷新。两者必须在 UI/交付报告中说明实际能力。
- 未设工期承诺：先完成 T00 与 T02，再依据工具链、渲染适配和设备条件评估剩余工作；任务数量不等于确定的人日估算。
