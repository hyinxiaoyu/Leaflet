# E-Ink 阅读器与文件夹阅读方案调研（T00）

[English](eink-readers.md) | 中文

- 日期：2026-09-17（查阅日期；以下版本日期为各仓库公开信息，无法确认处标“未验证”）
- 范围：只读调研，不复制参考项目代码；只记录 URL / 日期 / tag / commit / 许可证 / 可借鉴设计 / 不采用原因。
- 配套计划：`docs/plans/M0-M1-implementation-plan.md` T05（扫描浏览）→ T06（基础 Reader）→ T07（位置/最近）→ T08（TOC/翻页）→ T09（排版/代码/表格）。
- 约束对照：`AGENTS.md`（folder-first、SAF 只读、viewport 翻页 overlap 5%、静态加载状态、无厂商 SDK、通用/NoOp EInkController）。

---

## 1. pilcrowmd/pilcrow — 原生 Markwon 阅读器（最接近 Leaflet 的参照）

- URL：https://github.com/pilcrowmd/pilcrow
- 查阅日期：2026-09-17
- 版本：`v1.0.7`（CHANGELOG 标注 2026-09-08；releases feed 顶部同为 v1.0.7；`main` HEAD commit SHA 未验证，不编造）
- 许可证：**GPL-3.0-or-later**（根 `LICENSE` 为 GPLv3 全文；`app/src/main/assets/licenses/` 另带 Apache-2.0/MIT/LGPL/OFL 第三方文本；`LICENSES.md` 跟踪依赖兼容性）。Copyleft 强传染，**不得复制代码**，只做设计参照。
- 技术栈（来自 README + `docs/ARCHITECTURE.md` + 源码阅读）：Kotlin 2.3、Jetpack Compose（Material 3）Shell、Clean Architecture + MVVM + UDF、手动 `AppContainer` DI、Coroutines/Flow、Markwon 原生渲染（无 WebView）、Prism4j 代码高亮、JLatexMath 数学、Sora Editor 编辑器、Jetpack DataStore、SAF。minSdk 26（Android 8.0+），JDK 21 + API 36 构建（README 自述）。

### 解决的问题 / 架构

- 单 Activity Compose 宿主 + `MarkdownViewModel`（StateFlow）编排；Domain 纯 Kotlin（`ParseMarkdownHeadingsUseCase`、`SearchMarkdownUseCase`、Frontmatter 不改写源码保偏移）；Data 层 `FileRepository`（SAF）+ `StorageManager`（DataStore）+ `MarkwonRenderer` + `PdfExporter`。UI 被动、无业务逻辑；颜色只走 design-token。
- 文件访问走 SAF + `takePersistableUriPermission` 重开上次文件；保存用 WAL（`noBackupFilesDir` + fsync，`recoverPendingSaves` 防启动竞态）——编辑器保障，与 Leaflet 只读无关。
- 位置记忆：`LocalStorageManager` 存 `last_file_uri` + `recent_files`（上限 8，新首去重）+ `scroll_positions`（`ScrollAnchor(index, offset)` 块级锚点，非纯像素；另有 preview/editor font scale 分离）。`distinctUntilChanged` 防无关写触发重载。
- 搜索：`SearchMarkdownUseCase` 按块偏移寻址 + `SearchHighlighter` 绑定时着色；计数按渲染文本（含数学修正）。
- 目录/TOC：`ParseMarkdownHeadingsUseCase` 与渲染块模型共享，抽屉点击跳转；脚注 `FootnotePlugin` + `ReaderScroll.kt` 底部 spacer（一 viewport headroom 的 `ItemDecoration`，非 padding）+ 到达高亮 1200ms。
- 大文件：`MarkwonRendererLargeFileTest` 约 5000 行混合块不崩；v1.0.4 修“行尾检测主线程二次正则导致 1.3MB 卡数十秒”→单次线性 pass 移出主线程；`JLatexMathPlugin` 配 `ForkJoinPool.commonPool()` 异步 + 失败回退原文；块绑定 try/catch 降级占位。无全库 FTS、无后台监视。

### Markwon 用法（可实施证据）

- 单例 `MarkwonRenderer.markwon`（lazy）+ 测试用 `buildPilcrowMarkwon(context)` 同链无预热实例（InlineProcessor 有状态不可并发）。
- 插件链顺序敏感：`CorePlugin` → `FrontmatterPlugin`（自定义 BlockParser，不改源码）→ `FootnotePlugin` → `TablePlugin.create(context)` / `StrikethroughPlugin` / `TaskListPlugin` → `LinkifyPlugin` → `MarkwonInlineParserPlugin`（先于 JLatexMath 注册单 `$` 处理器）→ `CeMacroShimPlugin` + `JLatexMathPlugin`（inlines/blocks 开、`blocksLegacy(false)`、errorHandler 返回 null 降级）→ `SyntaxHighlightPlugin(Prism4j + kapt GrammarLocator)` → `HtmlPlugin`（仅 br/sub/sup/details）。
- 管线：`content string → Markwon.parse → MarkwonAdapter(Prose/FencedCode/Table/Latex/Frontmatter/PlainText BlockEntry) → RecyclerView` 回收块视图；宽代码/表格横向滚动；拷贝按钮是 `FencedCodeBlockEntry` 悬浮层而非插件。
- 滚动几何坑（`ReaderScroll.kt` 注释实测）：`scrollToPositionWithOffset(target,0)` 尾部被 clamp 顶不到顶（脚注恒为末块必现）→ 用 decoration spacer 扩展 scroll range + `scrollToDocumentEnd` 两步法；`documentOverflowsViewport` 必须用 `last.bottom-first.top` 内容高而非 range/坐标。

### 可借鉴（对应 T06–T08/T11 预研）

- 原生块级 RecyclerView 而非单 TextView；frontmatter 不改源码保偏移；数学/未知语法失败回退原文；搜索按渲染文本块寻址；TOC 与渲染块模型同源；块级 `ScrollAnchor(index,offset)` + font-scale 稳定恢复；DataStore 单例 + 腐蚀回退空默认。
- 大块策略：单巨大块需分段纯文本降级/有界策略（T06 门槛），Pilcrow 的“后台解析 + 可取消 + 降级占位”方向可借鉴，阈值留 T03/T13 实测。

### 不抄什么（不采用原因）

- **不复制任何代码**：GPL-3.0-or-later 与 Leaflet 许可证待定冲突（AGENTS：不代选许可证、不复制未核实许可代码）。
- 不抄编辑器栈（Sora + TextMate + 原子保存/WAL/PDF 导出/云 Mermaid 开关）：Leaflet 是只读阅读器，M0/M1 明确不做编辑器/PDF/云同步。
- 不抄数学全套（JLatexMath + 单 `$` 处理器 + CeMacroShim + 字体预热）：MVP 不承诺 LaTeX/Mermaid，按 T06“未知语法降级”处理；引入即增体积与维护成本。
- 不抄 8 条 recents 上限与拼串编码：Leaflet 有 Room + DataStore 分工（T04/T07），自定契约。
- 不抄 Compose 整页宿主：Leaflet Reader 是 View/RecyclerView 组件（`:core:reader`），只借块模型思想。

---

## 2. zero-made/mdreader-android — SAF 树 + WebView 对照组（渲染路线反例）

- URL：https://github.com/zero-made/mdreader-android
- 查阅日期：2026-09-17
- 版本：**未验证 tag**（查阅时无 Release tag 公示；以 `main` 分支为准，commit SHA 未验证，不编造）
- 许可证：**MIT**（`LICENSE`：Copyright (c) 2026 Philip Bool；`THIRD_PARTY_LICENSES.md` 声明 bundled marked/highlight.js/KaTeX/Mermaid/github-markdown-css 为 MIT/BSD-3-Clause）。宽松许可，但架构路线不适合 E-Ink MVP，不复制。
- 技术栈（源码实测）：单 `MainActivity.java`（Activity + WebView shell）+ `assets/viewer.html/viewer.css/viewer.js` + `assets/lib/` 全离线 bundled（marked.min.js、highlight、KaTeX+fonts、mermaid）；`minSdk 26 / targetSdk 34`；零框架外依赖；无网络权限；R8 release + gitignored keystore。

### 解决的问题 / SAF 与浏览设计（T05/T07 对照价值最高）

- `ACTION_OPEN_DOCUMENT`（`text/markdown,text/x-markdown,text/plain,application/octet-stream`，读授权）开单文件；`ACTION_OPEN_DOCUMENT_TREE`（读 + `PERSISTABLE`）选目录一次；`takePersistableUriPermission` 持久化；`releasePersistableUriPermission` 在移除 recent/clearFolder 时释放。
- 递归浏览：`DocumentsContract.getTreeDocumentId → buildChildDocumentsUriUsingTree` BFS（`ArrayDeque` + guard 5000 + `MAX_FOLDER_FILES=500` 封顶），`MIME_TYPE_DIR` 入栈，`.md/.markdown/.txt`（大小写不敏感）收集后按名忽略大小写排序；失败返回空（无崩溃但静默，Leaflet 需显式错误态）。列表走后台线程经 `App.setFolderList` 推回页面，首页过滤框纯前端。
- 最近文档：SharedPreferences JSON（`recents` 上限 30 `{uri,name,ts}` MRU 去重前插；`scroll {uri→y}` 像素；`settings {}`；`folder {uri,name}`；`last_uri` + `reopenLast` 开关）。打开 recent 失败（文件消失/失权）自动删死条 + toast + `refreshHome`。`ACTION_VIEW` 注册 Open-with。
- TOC/搜索/进度：JS 端 `marked.parse` 后 `assignHeadingIds + buildToc` 抽屉跳转；find-in-page 计数 + prev/next；`saveScroll` 节流 + 图/公式落定后重贴 scroll；阅读进度条 + 代码块复制按钮 + front-matter 元数据卡 + 外链（http/https/mailto/tel）交外部应用、其余拦截防劫持。
- 数学扩展教训：KaTeX `$` 必须在 marked tokenizer 层做不透明 token（block `$$…$$` + inline 单行 `$…$` + `\\$` 转义），否则 marked 先把 LaTeX 内 `_/*` 吃成 em/strong 再跑 KaTeX 全坏——原生路线同样要“解析期保护数学”，不能后处理正则。

### WebView 渲染对照（为什么 Leaflet 不选它）

- 优点：GFM/表/任务列表/高亮/KaTeX/Mermaid 一次全得，CSS 五主题（Auto/Dark/AMOLED Black/Light/Sepia）+ 字号/衬线/宽度/wrap-code 开关便宜；`file:///android_asset/` 离线无权限即可跑。
- 代价：整页 `innerHTML` 一次渲染（大文档无分块/回收/取消），位置只是整页 `scrollY` 像素（旋转/字号一变即漂移，不满足 AGENTS“不只把像素 scrollY 当长期位置契约”）；E-Ink 下 WebView 平滑滚动/动效/重排闪烁不可控，与 DisplayProfile/MotionPolicy 集中策略冲突；JS bridge 跑 binder 线程需处处 marshal 回 UI，错误多为 toast 吞掉，难做版本化 ReadingPosition/anchor/progress 回退。

### 可借鉴

- SAF 树授权→递归元数据枚举→封顶 500→按名排序→死条自清→失权可恢复提示，映射到 T05 扫描/浏览验收（Leaflet 改为 Room 存辅助元数据 + 首扫只索引元数据 + 完整成功才提交消失清理）。
- “外链点击才交外部、远程图片默认不联网占位”与计划 §2 一致；`reopenLast` 可选、recent 可删可清是 T07 可用交互。

### 不抄什么（不采用原因）

- **不抄 WebView 整页渲染 + JS bridge 整套**：与原生 renderer 方向及 E-Ink 无动画/无 smooth scroll/静态加载要求冲突；大文件无取消/无分块，10MB 无法满足“可取消、无 OOM/ANR、可说明降级”。
- 不抄像素 `scrollY` 位置契约、SharedPreferences 拼 JSON 存全部状态、异常吞掉只 toast：Leaflet 用版本化 `ReadingPosition(blockIndex/blockOffset/progress/anchor)` + Room/DataStore + 显式加载/错误/降级态（T04/T07）。
- 不抄 KaTeX/Mermaid 全量 bundled：体积与 E-Ink 刷新成本超 MVP；远程图默认占位、Mermaid 降级为代码块（T06/T10）。
- MIT 许可虽宽松，但“许可允许”不等于“架构适用”，故只借 SAF/浏览交互，不引渲染实现。

---

## 3. gsantner/markor — 文件夹工作流与离线（只借文件管理，不抄定位/渲染）

- URL：https://github.com/gsantner/markor
- 查阅日期：2026-09-17
- 版本：feed 顶部 `v2.16.1`（发布时间未验证；CHANGELOG/NEWS 另有 v2.11–v2.10 功能史，不逐条考证）
- 许可证：**Apache-2.0**（`LICENSE.txt`，代码部分）+ 翻译/样例 **CC0-1.0**；F-Droid/Play 双发。Apache-2.0 允许借鉴思想，本报告仍不复制代码。
- 技术栈（README 自述）：Java + Android SDK + AndroidX、无 NDK、Gradle/Makefile；编辑器基于 EditText 定制 + 各格式自定义高亮；预览 Android WebView；Markdown 解析 flexmark-java；Zim/todo.txt 自研转译；离线优先。

### 解决的问题 / 文件夹工作流

- Notebook 即根目录：全部文档存通用文件系统目录（默认 Documents，可指向任意内/SD 位置），子目录即分类；文件是 plaintext，可被 vim/grep/同步工具直接操作；QuickNote/ToDo 只是文本文件；与 Syncthing 等协作但自身不做云同步。
- 完全离线：无连接可建/读/改/预览；隐私声明“除用户内容引用的外部资源外不联网”；权限仍含 `WRITE/READ_EXTERNAL_STORAGE + INTERNET + INSTALL_SHORTCUT`（历史包袱，Leaflet 不学）。
- 格式广度：Markdown/todo.txt/Zim/CSV/AsciiDoc/Org 等 + 模板/导出 HTML/PDF/AES256 文本加密——编辑器/笔记平台能力，与 Leaflet 只读阅读器定位相反。

### 可借鉴（仅文件管理）

- “原始文件是唯一事实来源、库只是引用、删库不删原文、换设备可用外部工具验证内容”即 Leaflet folder-first + SAF 只读的原型；空目录/不支持文件可处理、保留目录层级、手动刷新（T05）与 Markor 行为一致。
- 离线默认 + 外部资源需用户动作才加载，与 Leaflet“远程图片静态占位、外链点击交外部”（T10）一致。

### 不抄什么（不采用原因）

- **不抄定位/搜索实现**：Markor 是编辑器 caret/行号体系 + WebView 预览 + 编辑器内搜索，无版本化阅读锚点、无渲染位置映射，满足不了 T07（块/偏移→anchor/progress 回退）与 T11（按可见文本搜、不把源码 offset 当屏幕坐标）。
- 不抄渲染：WebView 预览 + flexmark 全家桶超出只读 MVP，且与 E-Ink 静态策略不匹配；Leaflet 走 Markwon 原生分块（T06，另见 `markdown-renderers.md`）。
- 不抄权限/存储：`WRITE_EXTERNAL_STORAGE` 传统路径与 Leaflet SAF 只读 + 不复制成内部库 + 不写回原文冲突；不抄收藏/待办/加密/多格式/小部件等笔记平台功能（计划 §1 明确不做）。
- 不抄文件浏览器全套（含收藏/多选/模板）：T05 只做递归元数据扫描 + 层级浏览 + 节流静态进度，不做后台实时监视/全库搜索。

---

## 4. koreader/koreader — E-Ink 交互百科（非 Android 架构参考）

- URL：https://github.com/koreader/koreader
- 查阅日期：2026-09-17
- 版本：feed 顶部 `v2026.07.2`（发布时间未验证；另有 v2026.07.1/v2026.07/v2026.03 等月度列车，不定点考证）
- 许可证：**AGPL-3.0**（根 `COPYING`；README badge 同指 AGPL）。网络条款传染 + 嵌入式 Lua 栈，**只做交互参照，不复用代码/架构**。
- 技术栈声明（**非 Android 架构参考**）：Lua + 自研跨设备框架（Kindle/Kobo/PocketBook/reMarkable/Android/Linux），`frontend/apps/reader/modules/reader*.lua`（paging/rolling/footer/toc/search/font/menu 等）+ `filemanager` + 插件；与 Leaflet `:app/:core:*` + Room/DataStore + 原生渲染无对应关系，不做模块映射。
- 文档格式：PDF/DjVu/EPUB/FB2/Mobi/DOC/TXT/HTML 等重排与固定版式双栈，K2pdfopt 重排扫描版——Leaflet 只做 MD/TXT，不做 PDF/EPUB。

### E-Ink 交互（可迁移的经验，非实现）

- 翻页：Paged（按屏翻，带可配 overlap）vs Continuous（垂直滚）双模式；Leaflet MVP 只做 viewport 减 overlap 即时滚动、不做真分页排版引擎（T08），KOReader 证明“overlap + 首尾 clamp + 旋转/Inset 重算”是必备项。
- Tap zones：`Taps and gestures > Page Turns` 网格预设（左/右翻、单手 top/bottom、防误触禁区）+ `tap_zone_guides.koplugin` 可视化；Leaflet 取最小子集：左/中/右三区 + 链接/按钮/横向表格·代码手势优先 + 长按不翻页（T08 交互仲裁），不做可配置编辑器/全手势系统。
- 菜单/低干扰：无动画分页菜单、点按呼出/自动隐、阅读区最大化；Leaflet 对应“菜单显示隐藏 + 遮挡计入翻页计算 + E-Ink 禁 smooth/overscroll/ripple/crossfade”（T08/T12）。
- 字体边距排版：任意页边距、行距覆盖、外置字体、多语言断字；Leaflet 缩为系统字体 + 字号/行距/边距 + 代码等宽 + 仅 NORMAL 表格（T09），不打包字体、不做 WRAP/CARD。
- 进度/状态条：footer 百分比/`Page X of Y`/细进度条三选一、忌粗条 + 常变时钟（每分钟 region 刷新致闪/残影）；全刷间隔按页数/章节设。Leaflet 对应：静态细进度（无动画、节流更新）+ 数字百分比为主，E-Ink 下不高频更新（T08/T12）。
- 大文档导航：TOC + 书签 + 历史 + goto + 文件内搜索 + 缩略图/页映射；Leaflet 取 TOC 跳转 + 当前文档字面搜索 + anchor/progress 回退（T08/T11），不做书签/标注/全库 FTS。

### 可借鉴

- “默认 paged、continuous 可选；翻页 overlap；手势让位于内容交互；进度极简防闪；排版变化保上下文”五条可直接写成 T08/T09/T12 验收细则。
- 实机矩阵方法（6/7/10.3 寸 + 残影/刷新只能实机）与 T13 设备策略一致。

### 不抄什么（不采用原因）

- **不抄架构/代码**：AGPL-3.0 + Lua/自绘 UI 与 Android View/RecyclerView 不兼容，且 KOReader 是全功能电子书套件（字典/OPDS/RSS/FTP/SSH/calibre/Wallabag/翻译插件），与 local-first 单格式只读器目标相悖。
- 不抄完整分页引擎、K2pdfopt、重排算法、多字体打包、可配置 tap-zone 矩阵、插件系统：T08 明确不做真分页引擎与手势编辑器，T09 不引字体包/语法着色引擎。
- 不抄刷新硬件调用：Leaflet 用通用/NoOp `EInkController`，不集成 Onyx/Kindle 私有 SDK、不声称局刷/全刷（T12/onyx-epd 另研）。

---

## 5. Leaflet 对照结论（指向 T05–T09）

- **folder-first（T05）**：采 Markor“目录即库、原文唯一事实” + mdreader“选树一次、递归枚举、500 封顶、死条自清”；落到 Leaflet 为多 Library + `ACTION_OPEN_DOCUMENT_TREE` + 递归元数据扫描（`.md/.markdown/.txt`、保层级、mtime/size 增量、完整成功才清消失项、可取消/合并刷新、静态节流进度）。首扫不解析全文；不做后台监视/全库 FTS。
- **SAF 只读（T04→T05/T10）**：采 Pilcrow `FileRepository` 边界（Reader 不碰 SAF/Room）+ mdreader 持久授权/释放时机；Leaflet 加严为只读 `DocumentSource`（按 ID 开流、树内 `..` 允许、越树拒绝、content URI 不拼路径、流必关、失权/消失可恢复态；删库/清库/卸载不删原文）。
- **viewport 翻页 overlap 5%（T08）**：采 KOReader paged-overlap + Pilcrow `ReaderScroll` clamp/旋转教训；Leaflet 为按可见高即时滚动、默认 5%（REQ 允许 3–8%）、计入 Insets/菜单遮挡/首尾边界、E-Ink 禁 smooth；交互仲裁（链接/按钮/复制/横向滚优先，长按不翻）+ TOC（层级+重复标题 anchor）点击跳转；模式切换保位置。不做分页引擎/zone 编辑器。
- **静态加载状态（T06/T12）**：采 Pilcrow 降级占位 + KOReader 低干扰（细进度/百分比、节流、无动画）；Leaflet 为加载/失败/降级三态 + 无限 loading 禁止 + 禁动画/overscroll/ripple/crossfade，大文件（100KB/1MB/5MB/10MB）可取消、有界、允许说明降级（单巨大块纯文本分段），禁 OOM/ANR/crash/静默截断。
- **位置与排版（T07/T09）**：采 Pilcrow 块级 `ScrollAnchor(index,offset)` + 节流存/退出补存/版本化回退（块→anchor→progress）；排版取 KOReader 最小子集（系统字体、字号/行距/边距持久化、代码等宽+换行默认开+横滚、NORMAL 表横滚），mdreader 五主题/捏放只取“设置持久化”思想，不引 WebView 主题实现。
- **搜索与资源（T10/T11 预告）**：Pilcrow 块寻址搜索 + mdreader tokenizer 期护数学的教训 → T11 按可见文本、映射寻址、可取消；相对链接/本地图按授权树解析、采样解码、缺图占位、远图不联网（T10）。

| Leaflet 任务 | 主参照 | 对照组 | 本报告落点 |
|---|---|---|---|
| T05 扫描浏览 | Markor 文件夹语义 | mdreader 树枚举/封顶/死条清理；KOReader filemanager 不抄 | 递归元数据+保层级+静态进度 |
| T06 基础 Reader | Pilcrow 块 RecyclerView + 降级 | mdreader WebView 反例；Markor flexmark 不选 | 分块+可取消+三态 |
| T07 位置/最近 | Pilcrow ScrollAnchor + DataStore | mdreader 像素 scrollY 不用；Markor caret 不用 | 版本化回退 |
| T08 TOC/翻页 | KOReader overlap/zones | Pilcrow spacer/clamp 坑 | 5% viewport 翻页 |
| T09 排版表格代码 | KOReader 最小排版 | Pilcrow Prism 全套不引 | 系统字体+NORMAL 表 |

---

## 6. 未验证与风险（T01 前必须关）

- 三仓库 commit SHA 均未取（本次只读 feed + 原始文件，未跑 git）；Pilcrow v1.0.7 / Markor v2.16.1 / KOReader v2026.07.2 的发布时刻未验证——T01 引 ADR 时需补 tag 日期与 SHA。
- Markwon/AGP/Kotlin/BOOX 基线兼容性未在本文件定案，见 `markdown-renderers.md` + `onyx-epd.md`；本文件只证明“原生分块 + SAF 树 + 块锚点 + viewport 翻页”路线可行。
- mdreader `MAX_FOLDER_FILES=500` 与 Pilcrow recents=8 只是参照常量，Leaflet 上限留 T05/T07 按性能基线（T03/T13）重定。
- 无复制代码声明：本报告所有实现细节均为转述与文件指向（已给精确路径如 `rendering/ReaderScroll.kt`、`repository/LocalFileRepository.kt`、`MainActivity.java:REQ_TREE/listFolderFiles`、`frontend/apps/reader/modules/reader*.lua`），未粘贴受保护源码。

---

## 来源

- https://github.com/pilcrowmd/pilcrow （README / docs/ARCHITECTURE.md / CHANGELOG v1.0.7 2026-09-08 / app/src/main/java/com/pilcrowmd/rendering/MarkwonRenderer.kt / rendering/ReaderScroll.kt / storage/LocalStorageManager.kt）
- https://github.com/zero-made/mdreader-android （README / LICENSE MIT / app/src/main/java/com/phil/mdreader/MainActivity.java / app/src/main/assets/viewer.js / viewer.html / THIRD_PARTY_LICENSES.md）
- https://github.com/gsantner/markor （README / LICENSE.txt Apache-2.0 / releases v2.16.1 feed / NEWS.md）
- https://github.com/koreader/koreader （README / COPYING AGPL-3.0 / releases v2026.07.2 feed / frontend/apps/reader/modules/ / frontend/apps/filemanager/）
- 计划与约束：docs/plans/M0-M1-implementation-plan.md（T05–T09）、AGENTS.md（存储/Flavor/E-Ink 硬约束）
