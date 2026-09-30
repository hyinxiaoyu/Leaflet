# R3-08 交付 — 软件候选证据

[English](r3-08-delivery.md) | 中文

日期：2026-09-28
包：R3-08，见[初始 MVP 的剩余任务](remaining-mvp-tasks.md) (Remaining Tasks for the Initial MVP)
需求：REQ-0011（性能/发布），AC 映射见下文
状态：已汇总有限的软件候选证据。未发布：许可证、application ID、签名、分发与实机行不在本包范围内（R3-09/R3-10）。
独立评审：未执行（执行者自述）。

## 源码标识

`/Users/clover/source_dev/Leaflet` 在 2026-09-28 的工作树 (working tree)（未创建提交；该
工作区属于用户）。已验证的来源是 R3-05/06/07 交付报告中列出的文件。下方的产物哈希 (artifact
hashes) 把候选二进制绑定到该源码状态。

## 实际运行的命令

```bash
./gradlew test lint ktlintCheck :app:assembleFullDebug :app:assembleEinkDebug \
  :app:assembleFullRelease :app:assembleEinkRelease :core:storage:assembleDebugAndroidTest
./gradlew :app:connectedFullDebugAndroidTest :app:connectedEinkDebugAndroidTest \
  :core:storage:connectedDebugAndroidTest :core:markdown:connectedDebugAndroidTest
./gradlew :app:dependencies --configuration fullReleaseRuntimeClasspath
```

三条命令均以 0 退出。宿主：macOS（没有可用的 Linux 构建 → 干净 Linux 证据 **NOT_RUN**）。
设备：emulator-5554，`dev_arm64_api35`（Android 15）。

## 测试结果

| 套件 | 结果 |
| --- | --- |
| JVM（`test`，所有变体） | 652 条变体记录 / **295 个唯一项**（class+name 去重），0 失败 |
| `:app:connectedFullDebugAndroidTest` | 75 个测试，**0 失败**，3 跳过 |
| `:app:connectedEinkDebugAndroidTest` | 75 个测试，**0 失败**，3 跳过 |
| `:core:storage:connectedDebugAndroidTest` | 17 个测试，0 失败（包含 v1→v2 迁移测试） |
| `:core:markdown:connectedDebugAndroidTest` | 5 个测试，0 失败 |
| `lint` / `ktlintCheck` | 绿色（`:app` 与每个 `:core:*` 模块） |

每个 flavor 的 3 个跳过项是受 `assumeTrue` 保护的两阶段持久化测试
（`ReaderProductionPersistenceTest`、`ReaderPersistenceTest`），它们要求其种子阶段
已经运行；它们是被跳过，而不是通过。

本轮新增：`ReaderInteractionTest` 12 个、`ReaderResourceTest` 9 个、
`ReaderLargeDocumentPageTest` 9 个（R3-05/06/07）。

## 产物

| 产物 | 大小（字节） | SHA-256 |
| --- | --- | --- |
| `app-full-debug.apk` | 28,485,321 | `520dc5c3e4698c76238a71e898f252f73a8b114aea37dcfada2d1f9d4fde4109` |
| `app-eink-debug.apk` | 28,378,941 | `d73829ff29a1171d186254053cac5e5564b3e80241fddeac0e039ca9445ac706` |
| `app-full-release-unsigned.apk` | 22,618,101 | `ab51769a52a1cf473421442514dfe634eea70708463106ed3eeec93b1fb805bd` |
| `app-eink-release-unsigned.apk` | 22,617,845 | `4ce7b1d2fb3e71ca5c50d17dc9500073ef44eec74693f02d0b9862c1c13c4bd5` |

- 这些 release APK 是 **未签名** 的；不存在也没有创建任何签名材料。
- 合并后的 release 清单 (merged release manifest) 除框架生成的
  `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` 外未声明任何运行时权限 (runtime
  permission)——特别是**没有 `INTERNET`**，这正是默认离线 (offline-by-default)
  的保证（REQ-0009/0011）。
- 仅测试依赖隔离 (test-only dependency isolation)：`fullReleaseRuntimeClasspath` 中
  **没有** `androidx.room:room-testing`、`androidx.compose.ui:ui-test-junit4` 或
  `ui-test-manifest` 条目；release 体积没有被测试框架撑大。
- debug APK 比 release 大约 6.2 MB；差异来自 debug 运行时加上仅 debug 的 UI 测试
  清单，与现有 APK 记录一致。

## 性能证据

按形态记录于 [r3-07-delivery.md](r3-07-delivery.md)：100 KB → 10 MB 的文档在
0.2–8.0 s 内打开到首个可读内容，峰值 PSS 为 79–245 MB，且每个文件末尾标记
都能在页面上到达。已测量的最慢形态是 5 MB 多小块 (many-small-block)
文档（82,096 块，7.9 s）。`docs/performance/baseline-emulator-5554-2026-09-18.md`
中较早的人工数据仍属历史记录。

## AC → 证据映射

| 领域 | 证据 | 结论 |
| --- | --- | --- |
| 库扫描/浏览/移除 (Library scan/browse/removal)（REQ-0001） | LT 交付报告；storage 设备套件；JVM 扫描器/仓库测试 | PASS (软件) |
| Markdown/TXT 渲染，含表格/代码（REQ-0002/0007） | `ReaderSyntaxPageTest`、`ReaderEntryTest`、R3-05 交互套件 | PASS (软件) |
| 位置/设置/模式持久化（REQ-0003/0004/0010） | `ReaderPersistenceTest`、`ReaderProductionPersistenceTest`（两阶段 (2-phase)，无种子阶段时跳过 3 项）、`ZzActivityRecreationTest` | PASS (软件，已注明跳过项) |
| 翻页/TOC/手势（REQ-0005/0006） | `ReaderInteractionTest` 12/12 + `ReaderDisplayTest` | PASS (软件)；E-Ink 硬件 NOT_RUN |
| 链接/资源/系统边界（REQ-0009） | `ReaderResourceTest` 9/9；JVM 解析器测试 | PARTIAL：真实 provider 图片解码 + 峰值内存 NOT_RUN |
| 大文档/搜索/TOC（REQ-0008/0011） | `ReaderLargeDocumentPageTest` 9/9，附测量值 | PASS (软件)；provider 读取拆分 NOT_RUN |
| 可复现构建 / 候选包（REQ-0011） | 本报告：门禁命令、哈希、权限、隔离 | PARTIAL：Linux 构建 NOT_RUN，未签名 |
| E-Ink 实机 + 手机矩阵 | 无 | NOT_RUN (R3-09) |
| 许可证/ID/签名/分发 | 无 | BLOCKED (R3-10) |

## 已解决的状态漂移

- `README.md`：「Alpha candidate」段落与验证段落现在携带当前的测试数量，并链接到
  R3 交付报告；过时的 12 页测试 / 240 JVM 数字被标注为 R2-07 快照。
- `AGENTS.md`：当前阶段段落现在说明 R3-05–08 软件状态，以及 R3-09/R3-10 因硬件和
  所有者决策而阻塞。
- `docs/development/testing.md`：套件数量已更新（JVM 652/295；每个 flavor 的
  设备 app 测试 75 个，含 3 个假设跳过；storage 17；markdown 5）。

## 发布前的遗留阻塞项

1. 一台真实 E-Ink 设备和一部普通手机，用于 R3-09 矩阵（残影、对比度、
   刷新、恢复）——没有模拟器可替代。
2. R3-10 的所有者决策（许可证、application ID、签名保管、分发）。
3. 如实记录的未完成软件事项：真实 provider 图片管线（解码/采样/峰值
   内存）、provider 层读取计时、>16 MiB 读取上限路径，以及 100 KB 以上的巨型代码块
   测量。

## 停止条件

已为有限的软件候选提供证据；本文中的任何内容都不授权发布，也没有任何
缺陷在没有证据的情况下被关闭。R3-09/R3-10 保持开放，其最小的所有者
解决方案记录在 [r3-09-10-readiness.md](r3-09-10-readiness.md) 中。
