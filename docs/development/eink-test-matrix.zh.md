# E-Ink 实机测试矩阵

[English](eink-test-matrix.md) | 中文

负责人：T03 建立矩阵框架；T13 在真实硬件上填写结果。
每行状态可用：`PASS` / `FAIL` / `BLOCKED` / `NOT_RUN`。
规则（REQ-0011 FR-6、`docs/archive/init.md` §49）：模拟器可辅助检查尺寸适配，但残影/刷新结论只能来自真实 E-Ink 硬件。没有设备的行保持 `NOT_RUN`，不得标通过。

## 设备清单

| ID | 设备 | Android | 屏幕 | 分辨率 | 固件 | Leaflet 版本 | 测试日期 | 状态 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| PHONE-REF | 普通手机（通用，待定） | TBD | TBD | TBD | N/A | TBD | TBD | NOT_RUN |
| EINK-6 | 6 英寸级 E-Ink 设备（待定） | TBD | 6" | TBD | TBD | TBD | TBD | NOT_RUN |
| EINK-7 | 7 英寸级 E-Ink 设备（待定） | TBD | 7" | TBD | TBD | TBD | TBD | NOT_RUN |
| EINK-103 | 10.3 英寸级 E-Ink 设备（待定） | TBD | 10.3" | TBD | TBD | TBD | TBD | NOT_RUN |

记录对应测试前，每台实体设备先填写一行清单。

## 测试用例（`docs/archive/init.md` §49）

| # | 用例 | 检查内容 | PHONE-REF | EINK-6 | EINK-7 | EINK-103 |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | 翻页残影 | 用 `testdata/markdown/long.md` 翻 20 页，记录残影程度 | NOT_RUN | NOT_RUN | NOT_RUN | NOT_RUN |
| 2 | 菜单显隐 | 切换阅读菜单，内容重排后无残留痕迹 | NOT_RUN | NOT_RUN | NOT_RUN | NOT_RUN |
| 3 | 快速连续翻页 | 连续快速翻 10 页，不跳页/重叠/卡住 | NOT_RUN | NOT_RUN | NOT_RUN | NOT_RUN |
| 4 | 长按 | 长按正文不得触发翻页；选择/上下文行为正常（若已实现） | NOT_RUN | NOT_RUN | NOT_RUN | NOT_RUN |
| 5 | 返回导航 | 从 Reader 返回资料库并保留位置；无空白页 | NOT_RUN | NOT_RUN | NOT_RUN | NOT_RUN |
| 6 | 字号调整 | 调整字号后保留阅读上下文，不裁切行 | NOT_RUN | NOT_RUN | NOT_RUN | NOT_RUN |
| 7 | TOC | 打开 `long.md` 目录，跳到所点章节 | NOT_RUN | NOT_RUN | NOT_RUN | NOT_RUN |
| 8 | 长文档 | 打开 `testdata/generated/large-many-1m.md`，端到端滚动无 ANR | NOT_RUN | NOT_RUN | NOT_RUN | NOT_RUN |
| 9 | 代码块 | `testdata/markdown/code.md`：E-Ink 默认换行；关闭后可横向滚动 | NOT_RUN | NOT_RUN | NOT_RUN | NOT_RUN |
| 10 | 表格 | `testdata/markdown/tables.md` 宽表全部列可横向到达 | NOT_RUN | NOT_RUN | NOT_RUN | NOT_RUN |
| 11 | 图片 | `testdata/markdown/relative-images.md`：本地显示、远程静态占位、缺失占位 | NOT_RUN | NOT_RUN | NOT_RUN | NOT_RUN |
| 12 | 黑白对比度 | 默认与高对比设置下正文/代码/表格文字可读 | NOT_RUN | NOT_RUN | NOT_RUN | NOT_RUN |
| 13 | App 恢复 | 阅读中切后台/回前台，位置和模式（STANDARD/EINK）保留 | NOT_RUN | NOT_RUN | NOT_RUN | NOT_RUN |
| 14 | 旋转 | 阅读中旋转，位置合理、无崩溃、内容不丢 | NOT_RUN | NOT_RUN | NOT_RUN | NOT_RUN |

## 备注

- 应用版本记录为 `versionName` + `versionCode` + flavor（`full` / `eink`）。
- 用例 1、3、8 附上相应 `docs/performance/baseline-*.md` 测量记录，说明时间/内存环境。
- 每个失败单独记录：设备 ID、用例编号、观察到的行为、样本文件、日志位置。没有实际执行的用例继续标 `NOT_RUN`。

## 模拟器辅助证据（2026-09-18；不可替代实机）

设备：emulator-5554（sdk_gphone64_arm64，Android 15，1080×2400）。构建：Full 0.1.0 / E-Ink 0.1.0-eink，versionCode 1（debug，开发密钥签名）。上方真实硬件行保持 `NOT_RUN`；以下只覆盖布局/逻辑，不证明残影/刷新（REQ-0011 FR-6）。

| # | 检查 | 证据 |
| --- | --- | --- |
| 5 | 返回导航 | PASS（模拟器）：Reader → Back → 资料库/浏览，无空白页（R2-07 Full + E-Ink 走查） |
| 6 | 字号调整 | PASS（模拟器）：Step 3→4 强制停止后仍保留；上下文保持（T09 走查） |
| 7 | TOC | PASS（模拟器）：目录抽屉 25 个标题，Section-10 跳至 20/56·34%（R2-07 走查与页测） |
| 8 | 长文档（逻辑） | PASS（模拟器）：生产页打开 1 MB/12,149 块约 8 秒，稳定 PSS 122 MB，无 OOM/ANR；10 MB 在 6 秒显示 Loading，可 Back 取消，解析中 PSS 260 MB/RSS 371 MB，无 ANR；`:core:markdown` 设备套件 5/5 |
| 9 | 代码块 | PASS（模拟器）：设置中可切换换行，长行可横向滚动（T09） |
| 10 | 表格 | PASS（模拟器）：双 flavor 原生单元格网格，宽表支持横向滚动（R2-07 E-Ink 截图）；表头加粗、单元格行内标记可见 |
| 11 | 图片 | PASS（模拟器）：本地 16/256 PNG 绘制为真实位图；空格/CJK 路径可解析；越界/缺失显示静态 MISSING；远程 http(s) 静态提示“远程图片不自动加载”，加载器调用为零 |
| 12 | 对比度 | NOT_RUN（需人工判断可读性并留截图，T09 AC-7） |
| 13 | App 恢复 | PASS（模拟器）：强制停止后模式保留（DataStore EINK 已验证），从最近阅读恢复到 26/56·45%（R2-07 走查） |
| 14 | 旋转 | PASS（模拟器）：横屏仍位于 Section 10 顶部 26/56·45%，回竖屏位置一致，无崩溃/丢内容（走查 + 页测） |
| 1–4 | 残影/菜单痕迹/快速翻页/长按 | NOT_RUN（仅真实 E-Ink 硬件可验证） |
| 15 | 搜索计数（R2-07） | PASS（模拟器）：修复后显示“1 / 1”；生产页可跳转并高亮；页测断言“1 / 3”→下一项→“2 / 3” |
| 16 | 文件消失恢复（R2-07） | PASS（模拟器）：重扫移除已删行，冷启动提示“无法打开文档/已移动、改名或删除”，提供移除/重试；资料库死条目仍提示消失状态 |
| 17 | TXT 字面性（R2-07） | PASS（模拟器）：E-Ink flavor 将伪标题/列表符号按原文显示；CJK/emoji 正常；页测 `txtOpensAsLiteralParagraphs` |
| 18 | 模式切换（R2-07） | PASS（模拟器）：Full 从 STANDARD 切到 EINK 后全局高对比生效，DataStore 持久化 `display_mode=EINK`；E-Ink flavor 零设置默认 EINK |
