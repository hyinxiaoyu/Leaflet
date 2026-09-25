# testdata — 阅读器样本与大文件生成器

[English](README.md) | 中文

### 范围与布局

本目录仅服务于 T03，为 T05/T06/T10/T11 阅读器工作提供确定性、自编样本。没有复制第三方文档：文件均为本仓库手写，或由 `generate_large_samples.py` 生成。`long.md` 中的三条短引文（《静夜思》开头、《平家物语》开头、一句归于 McLuhan 的话）属于公版内容或短篇合理引用，不是复制的文档。

上方目录树列出小型 Markdown/TXT 样本、相对资源目标以及 `generated/` 中的 13 个大文件。生成目录由 Git 忽略。

重新生成：

```bash
python3 testdata/generate_large_samples.py generate   # 生成并校验
python3 testdata/generate_large_samples.py verify     # 仅校验
python3 testdata/generate_large_samples.py --out /tmp/gen
```

`verify` 会检查：每个档位的字节数准确（100k=102400、1m=1048576、5m=5242880、10m=10485760）、UTF-8 有效、末尾换行、样本形状规则（many：块数 ≥ 文件大小/400 且最大块 ≤ 4096 B；single-para：恰有一行大于 4096 B 且至少占文件一半；single-code：恰有一个 fence，正文至少占文件一半且含 ≥500 B 的行）、内存中两次生成的哈希一致，以及磁盘文件与新生成字节一致。全部通过才返回 0。2026-09-18 验证结果：13 个文件，共 51,687,424 字节；重新生成后字节完全一致（sha256 diff 无差异）。

### 归档初始化要求 §48 覆盖表

| §48 项目 | 样本 | 验证目标 |
| --- | --- | --- |
| 简单语法 | `markdown/simple.md` | 粗体、斜体、删除线、行内代码、链接、引用、分隔线、列表和围栏代码均可渲染 |
| 标题 | `markdown/headings.md` | 提取 H1–H6；`#NoSpace` 保持为段落；三个重复 `## Duplicate` 产生唯一锚点 |
| 列表 | `markdown/lists.md` | 无序、有序、任务列表和混合嵌套正确渲染 |
| 表格 | `markdown/tables.md` | 简单、对齐、宽表及行内标记；宽表支持横向滚动且不裁切 |
| 代码 | `markdown/code.md` | 围栏（含/不含语言）、缩进、行内及长行换行；显示语言标签，未知标签有降级行为 |
| 长文 | `markdown/long.md` | 20 节滚动、TOC、搜索和位置恢复；`long-doc-needle-*` 可搜索 |
| Unicode | `markdown/unicode.md` | 多文字系统、emoji、重音符号、组合标记、ZWJ 家庭 emoji、RTL 片段和全角标点 |
| 中文 | `markdown/chinese.md` | CJK 标点/引号/列表/表格正确；可找到搜索词“量子计算机” |
| 日文 | `markdown/japanese.md` | 假名与汉字混排、“古池や”可检索，任务列表可用 |
| 相对链接 | `markdown/relative-links.md` 和目标文件 | 兄弟/嵌套/片段/重复片段/编码空格/CJK 路径可在树内解析；缺失目标/片段、`..` 越界、绝对路径、未知 scheme、空目标均显式显示缺失状态且不崩溃；只有点击外链时才交给外部应用 |
| 相对图片 | `markdown/relative-images.md`、`assets/`、含空格/CJK/父目录的目标 | 本地图片采样解码；缺失/不支持/空源显示占位符；远程 http(s) 为静态占位符，不发起网络请求 |
| 畸形 Markdown | `markdown/malformed.md` | 未闭合强调/围栏/链接/图片、参差表格及孤立标记回退为字面文本；不崩溃、不挂起 |
| 大文件 | `markdown/huge.md`（说明入口）与 `generated/large-*` | 打开 100 KB–10 MB 文件时无 OOM/ANR/crash；巨型块显示加载/回退提示；加载可取消且释放资源；内容可读、无静默截断 |
| 超长段落 | `long-paragraph.md`、`generated/large-single-para-*` | 分割、窗口化或回退并显示提示；单块布局有界 |
| 超长代码行 | `code.md` 长行、`long-codeblock.md`、`generated/large-single-code-*` | E-Ink 默认换行；关闭换行时可横向滚动 |
| 大型 Markdown | `generated/large-many-*`、`generated/large-single-*` | 四个档位均满足 AC-2：无 OOM、ANR、crash |
| 异常 Markdown / 编码 | `malformed.md`、`broken-encoding.md` | 明确提示或回退；不得崩溃，也不得声称自动识别编码 |
| 空文件 | `empty.md`（0 B）、`empty.txt`（0 B） | 显示空状态而不是错误；打开/关闭正常 |
| 大 TXT | `sample.txt`、`generated/large-1m.txt` | TXT 按纯文本处理，`#` / `-` 不解析；可搜索 `txt-needle-plain-sample` |
| 额外边界样本 | 重复标题、BOM、损坏编码、嵌套列表、图片/链接目录 | 锚点唯一且链接跳转正确；移除 BOM；有效行仍可读；深层缩进正确；空格/CJK/嵌套/`../` 可解析，`../../outside-root` 必须拒绝，缺失目标需明确提示 |

### 测试作者须知

- 所有 `.md` / `.txt` 样本使用 LF 换行且恰以一个换行符结尾；`empty.*` 为空文件。
- `vector.svg` 有意设为不支持格式：图片管线必须显示占位符，不得尝试 SVG 渲染或联网。
- `leaf-256.png`（256×256）用于采样/超大图测试，`leaf-16.png` 为基线。二者均由仓库使用标准库 `zlib` 生成纯色 PNG，无需图像工具。
- `generated/` 被 Git 忽略（`generated/.gitignore`：`*` 和 `!.gitignore`）；CI/测试必须先运行生成器，不要提交生成产物。
- 测试资源不得进入 Release APK：样本仅位于仓库根目录的 `testdata/`，`app/build.gradle.kts` 的 `sourceSets`、`assets.srcDirs` 和 `res.srcDirs` 均不引用它（2026-09-18 已验证；仅打包 `app/src/`，见下方“APK 不混入测试资源”）。

### APK 不混入测试资源（证据日期：2026-09-18）

- `app/build.gradle.kts` 完全没有 `sourceSets` 块：按 AGP 默认规则打包 `app/src/main/{java,res,assets,AndroidManifest.xml}`。
- `grep -rn "testdata" app/ core/ --include=*.kts --include=*.kt`：除本 README 所在目录外无命中（测试通过相对文件系统路径引用样本，不会把它们打包为资源）。
- `generated/` 产物被 Git 忽略，因此不会出现在干净检出或构建产物中。
