# R3-01～04 独立验收

[English](r3-01-04-independent-review.md) | 中文

检查开始：2026-09-19；结论确认：2026-09-20
范围：执行报告、相关源码与测试检查、现有双 flavor 门禁。未修改产品代码或新增测试。

## 结论

**部分通过，可以继续不依赖缺口的任务；R3-01～04 不能整体标记 DONE。**

| 任务 | 判定 | 理由与交接 |
| --- | --- | --- |
| R3-01 | PARTIAL | 已有缺陷回归与生命周期场景有进展；DEF-008 保持待验证。新增销毁补存测试不能区分先前保存和退出补存，见 DEF-010 |
| R3-02 | PARTIAL | 新增扫描规则矩阵与容量边界断言有效；尚无完整多库 UI/实际 provider 规模验证。验证常量的边界行为不等于为容量阈值提供性能测量依据 |
| R3-03 | 本轮已覆盖语法场景可接受 | DEF-009 的生产任务列表切片修复已落地；测试检查实际 TextView 文本与 spans，不再只看 AST。不能扩大为所有语法组合或 E-Ink 观感已验证 |
| R3-04 | PARTIAL | 磁盘 DataStore/Room 测试与编辑回退场景有效；跨进程报告只证明测试数据可读取，尚未证明生产 App 重启后的阅读页/设置恢复，见 DEF-011 |

下一步：R3-06 可按已具备的渲染契约推进；R3-05 可准备独立手势场景，但涉及位置/设置恢复的依赖先补 R3-04。无需重新实现 R3-03；R3-01/04 补证据即可，发现真实功能故障才修产品代码。

## 证据审计

- MarkdownRenderer 与 ReaderLifecycleTest 的 SHA-256 与执行报告一致。
- ReaderPersistenceTest 当前 SHA-256 为 `215dfa8786efa5fba40257b9549c24e4efe6c7819127d5e42ad4da8dbd7a38bc`，不同于执行报告中的 `686b294f...`。历史两阶段运行不能不加说明地绑定到当前源码；本轮未独立重跑 adb seed → force-stop → after-restart 流程。
- 持久化测试写 `r3-persist.db` / `r3-persist.preferences_pb`，生产 App 使用 `leaflet.db` / `settings` DataStore。测试本身有价值，但不能替代生产配置接线及页面恢复验证。
- 生命周期测试先调用 jumpToBlock（自身立即保存），再用 savedPositions.contains(documentId) 判断退出/重建时补存。该条件在退出前就可能成立；activity.recreate 后也没有重新挂载生产阅读页面，只检查测试持有的 ViewModel。
- R3-02 精确矩阵实际断言 21 个文档，不应称为已经完成原 AC 的 25 文档矩阵。上限 500/5000 的规则验证通过，不代表在真实 provider 上的性能预算已经完成。

## 需要补充的最小证据

1. 退出前制造尚未持久化的阅读位置；记录旧保存地址/次数；退出后断言新增写入与最终地址。关闭补存回调时该测试必须失败。
2. 实际 AppContainer/生产设置入口改变值，正常阅读后杀进程；在新进程打开生产页面，断言设置及可见位置恢复。不能仅检查专用测试表中的行。
3. 两阶段持久化验证使用独立播种标识判断前置是否存在，不能用待验证的字号值决定是否跳过，否则字号丢失可能被记为 skip 而非失败。
4. 为真实扫描矩阵/多库 UI 与容量依据保留未验收项；如果容量性能归入 R3-08，明确交接，不提前标 PASS。

本轮未独立验证 release、core 独立设备套件、Linux 干净构建、E-Ink 实机；不扩大已有验证结论。

## 本轮实际验证结果

```bash
./gradlew :app:assembleFullDebug :app:assembleEinkDebug test lint ktlintCheck \
  :app:connectedFullDebugAndroidTest :app:connectedEinkDebugAndroidTest --console=plain -q
```

命令完成，exit 0；包含增量复用。JVM 当前报告为 588 variant / 276 unique，零 failure/error。

- Full：40 tests，0 failures/errors，0 skipped。
- E-Ink：40 tests，0 failures/errors，1 skipped（ReaderPersistenceAfterRestartTest）。即 39 项未跳过通过，不能写成 40/40 全部执行通过。
- 本轮 XML 时间分别为 2026-09-19T15:08:00 / 15:08:54 UTC；实际数量与执行报告的 41 项不同，以本轮报告为准。
- DEF-008 本次未复现，维持待验证，不用一次套件成功证明历史根因。
- 已将覆盖缺口登记为 DEF-010/011；它们不是本轮新发现的产品崩溃或数据丢失。

R3-03 本轮软件语法场景可交接至 R3-06；R3-01/02/04 保留上述缺口，不能整体标记完成。合并交付报告形式可接受，不要求为了文件命名拆成四份报告。
