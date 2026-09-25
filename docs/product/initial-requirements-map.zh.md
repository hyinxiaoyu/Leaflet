# 初始化要求迁移索引

[English](initial-requirements-map.md) | 中文

核对日期：2026-09-25。

结论：M0/M1 的产品能力、工程约束、研究与交付要求已纳入现有 REQ、ADR、架构文档及任务计划。根目录初始化提示词已移至 [历史归档](../archive/init.md)，不再作为工作入口。**要求已拆分不代表实现或验收已完成。**

M2～M5 仅按初始要求保留后续方向，尚未细拆成 READY 开发任务；不得宣称整个远期路线已完成任务分解。

## 当前入口

- WHAT / WHY：[需求索引](../requirements/README.md)、[产品愿景](vision.md)、[设计原则](principles.md)。
- HOW：[架构总览](../architecture/overview.md)、[ADR 索引](../adr/README.md)。
- 初始任务拆分：[T00～T13](../plans/M0-M1-implementation-plan.md)。
- MVP 剩余工作：[R3-01～10](../plans/remaining-mvp-tasks.md)、[下一批交接](../plans/r3-next-deliverables.md)。
- 完成证据：[R3 独立验收](../plans/r3-01-04-independent-review.md)、[缺陷清单](../plans/round-2-open-defects.md)。不能只根据原始计划判断已完成。

## 原章节去向

路径均相对仓库根目录；编号指归档原文，保留历史可追溯性。

| 原章节 | 要求 | 当前承接文档 / 任务 |
| --- | --- | --- |
| 开头、1–2 | 定位、临时包名、M0 优先级 | product/vision、principles；REQ-0004/0010；T01/02 |
| 3–6 | runtime mode 与 flavor、E-Ink 首发 | REQ-0004/0010；ADR-0003；T02/12；R3-04/10 |
| 7 | APK 体积正式需求 | REQ-0011；performance/apk-size；T13；R3-08 |
| 8–9 | 技术方向、原生解析与分块 | architecture/overview、reader；ADR-0004/0006；T00/06 |
| 10 | 文件格式、Markdown 语法与降级 | REQ-0002/0007/0009；T06/09/10；R3-03 |
| 11–13 | 多资料库、SAF、原文只读、元数据扫描 | REQ-0001；ADR-0001/0002；architecture/storage；T04/05；R3-02 |
| 14–15 | 标题、Reader 能力及位置 | REQ-0002/0003/0006/0007/0008/0009；T06～11；R3-03～07 |
| 16–17 | viewport 翻页、两种阅读方式、点击区 | REQ-0005；T08；R3-05 |
| 18–19 | 控制器隔离、Onyx 研究与后置集成 | REQ-0004；ADR-0005/0007；architecture/eink；research/onyx-epd；T00/12；M5 |
| 20 | 可用宽度自适应，小屏优先，双栏可后置 | R3-05/09 的旋转/视口及设备矩阵；双栏列为后置方向，见下方保留约束 |
| 21–22 | E-Ink/Standard 设计系统 | REQ-0004/0007/0010；architecture/eink；T02/09/12；R3-04/05/09 |
| 23–24 | 表格横滑，代码字体/标签/复制/换行 | REQ-0007；T09；R3-03/05；WRAP/CARD 与高亮优化后置 |
| 25 | 文内搜索必做、全库 FTS 后置 | REQ-0008；T11；R3-07；M4 |
| 26–27 | 25 项 MVP 能力与明确非目标 | product/vision；REQ-0001～0011；T00～13；R3-01～10 |
| 28–29 | 少量模块、Repository、StateFlow、manual DI | architecture/overview；T01/02；AGENTS |
| 30–33 | 文档目录、根 AGENTS、局部规则后置 | T01/02；AGENTS；README；第一阶段不新建局部 AGENTS |
| 34–37 | Requirement as Files、模板、状态、WHAT/HOW 分离 | requirements/README；AGENTS；T01 |
| 38 | ADR 模板及不可改写 Accepted 历史 | adr/README；AGENTS；T01 |
| 39 | M0～M5 路线 | product/roadmap；remaining-mvp-tasks 的范围说明 |
| 40–43 | README 内容与版本区别 | README；T02；R3-08 文档当前状态核对 |
| 44 | CHANGELOG | CHANGELOG；AGENTS 完成定义；T02/13；R3-08 |
| 45–46 | 六个参考项目研究与方案比较 | research/README、markdown-renderers、eink-readers、onyx-epd；T00 |
| 47 | 项目/依赖许可、第三方声明 | THIRD_PARTY_LICENSES；AGENTS；T00/02/13；R3-10（许可证选择未完成不影响拆分） |
| 48 | 单测/设备测试及样本 | REQ-0011；testdata/README；development/testing；T03；R3-03/07 |
| 49 | E-Ink 设备字段与真实体验矩阵 | REQ-0011；development/eink-test-matrix；T13；R3-09 |
| 50–51 | 性能基线、100KB～10MB、无 OOM/ANR/crash | REQ-0011；ADR-0006；performance；R3-07/08 |
| 52 | 依赖收益、兼容性、许可与体积 | AGENTS 依赖策略；T00/13；R3-08 |
| 53–54 | coding agent 流程与 DoD | AGENTS；各任务交接与 REQ/AC；R3-08 |
| 55 | 逻辑任务分离与 Conventional Commits 建议 | T计划 §8；下方保留约定（不授权自动提交） |
| 56–57 | 双 flavor CI 与真实构建命令 | .github/workflows/ci.yml；development/build、testing；T02；R3-08 |
| 58 | 默认离线、无账号追踪、网络需明确需求 | product/principles；AGENTS；REQ-0009/0011；R3-06/08 |
| 59–60 | AppCapabilities 与共享实现 | REQ-0004/0010；ADR-0003/0005；architecture/overview；T02/12 |
| 61–62 | 实施顺序与首次初始化停止条件 | T00～13 依赖表；T02 首次交付边界；历史步骤不重新执行 |
| 63 | 交付报告要求 | T13；R3-08；AGENTS 完成定义 |
| 64–65 | 第一批 REQ/ADR 主题 | requirements/README 与 adr/README；按下方语义映射，不硬套示例编号 |
| 66–67 | 十条产品原则与核心阅读路径 | product/principles、vision；AGENTS 项目定位 |

## 保留约束与编号说明

- 自适应以 Window/实际可用宽度判断，不按设备品牌或屏幕英寸硬编码布局；MVP 小屏单栏优先，大屏双栏延后，架构不假定永久单栏。R3-05/09 覆盖旋转和宽度变化；正式双栏进入后续里程碑时另立 REQ。
- 提交建议使用 `feat/fix/perf/refactor/docs/test/build/chore`；不同逻辑任务不混成超大提交，也不制造无意义小提交。提交仍需用户授权，归档操作不自动提交。
- 原文 §64 的 Reader Preferences 由当前 REQ-0007/0010 承接，TOC 是当前 REQ-0006；Distribution Flavors 由 REQ-0004 承接，本地资源独立为 REQ-0009；性能与交付由 REQ-0011 承接。主题完整承接，原示例编号不是现行 API。
- 原文 §65 的 DisplayProfile 主题由当前 ADR-0003/0005 及 REQ-0004 承接；渲染引擎为 ADR-0004，Reader 架构为 ADR-0006，Onyx 为 ADR-0007。不要因编号与原示例不同重建重复文件。
- 发行策略歧义、M1 与 M2 的表格/图片支持分界继续按 T计划第 2 节及现有 Accepted ADR 执行；归档不更改任何产品决策。

## 归档边界

原文保留于 `docs/archive/init.md`，章节及提示词主体不删减；新加归档说明阻止误把末尾“现在开始执行”当作当前任务授权。其引用仅作历史来源，日常开发从现行 REQ、ADR、计划和 AGENTS 开始。

剩余缺陷、实机验收、正式许可证、签名和后续里程碑均继续保留，不因移除根目录文件而关闭。历史 ADR/报告中的来源引用只迁移路径，不重写其决策内容。
