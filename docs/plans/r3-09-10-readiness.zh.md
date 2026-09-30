# R3-09 / R3-10 就绪状态 — 设备验收与发布决策

[English](r3-09-10-readiness.md) | 中文

日期：2026-09-28
包：R3-09（设备矩阵）与 R3-10（发布决策），见[初始 MVP 的剩余任务](remaining-mvp-tasks.md) (Remaining Tasks for the Initial MVP)
状态：**BLOCKED** — R3-09 需要由所有者提供物理硬件；R3-10 需要所有者决策。二者都无法由本环境中的 agent 完成，且任何模拟器结果都不得记录为硬件证据（REQ-0004/0011、AGENTS.md）。

## R3-09 — 设备矩阵（阻塞）

需要所有者提供：至少一部普通 Android 手机，以及目标 E-Ink 类别
（约 6"、约 7"、约 10.3"）。模拟器运行（`dev_arm64_api35`）仅覆盖软件行为；残影、
对比度与硬件局部刷新无法在那里产生或测量。

当有设备可用时，为每台设备/尺寸记录（追加到
[`eink-test-matrix.md`](../development/eink-test-matrix.md)）：

| 项目 | 如何记录 |
| --- | --- |
| 设备身份 | 型号、尺寸类别、Android 版本、面板类型 |
| 安装/升级 | 在上一构建之上执行 `adb install -r`；说明状态是否保留 |
| 冷启动 | 每种显示模式各启动一次，使用秒表或 `am start -W` |
| 翻页后的残影 | 在密集文本上翻十页；拍摄前后照片，记录残留影像 |
| 刷新行为 | 观察区域/全屏刷新节奏；记录通用控制器是否不动作（MVP 的预期行为） |
| 对比度/排版 | 默认与最大字号档位下的正文、标题、代码、表格 |
| 菜单/恢复/旋转 | 菜单打开/关闭、后台→恢复、旋转；位置不丢失 |
| 快速翻页 | 在尾部和头部快速点击十次；无过冲，无 ANR |

未提供的行保持 `NOT_RUN`；不得根据模拟器推断它们。

## R3-10 — 发布决策（阻塞于所有者）

所有者必须决定，且只有所有者可以授权：

1. **项目许可证。** 不得代表所有者选择或添加许可证文件（AGENTS.md）。
2. **正式 application ID。** `app/build.gradle.kts` 目前使用的是临时 ID；更改
   它们是一项发布决策，且会使现有安装失效。
3. **签名保管。** 不得在此创建任何 keystore、密钥或生产签名配置。
4. **分发渠道。** APK/AAB 发布在哪里，以及由谁批准发布。

已准备就绪且可安全复用：

- **第三方清单**：[`THIRD_PARTY_LICENSES.md`](../../THIRD_PARTY_LICENSES.md) 列出了随包发布
  以及仅用于测试的产物及其许可证；每当依赖变更时都会更新。
- **APK 组成/大小**：[`docs/performance/apk-size.md`](../performance/apk-size.md)；由
  R3-08 候选报告刷新，其中包含仅 debug 的测试资源（它们在 release 中不存在）。
- **构建/验证命令**：[`docs/development/build.md`](../development/build.md) 和
  [`docs/development/release.md`](../development/release.md)。
- **版本/变更说明**：[`CHANGELOG.md`](../../CHANGELOG.md)（英文 + 中文配对）。
- **安装/升级检查**：R3-08 候选报告记录了 debug/release 安装与
  数据库升级证据（schema 1 → 2）。

在所有者决定之前被阻塞的操作：添加 `LICENSE` 文件、更改 application ID、
生成或使用签名材料、为发布打标签，以及上传产物。

## 交接

两个包在[初始 MVP 的剩余任务](remaining-mvp-tasks.md) (Remaining Tasks for the Initial MVP)中保持开放。R3-08
交付一个不依赖上述任一决策的有限软件候选版本；本文中的任何内容都不授权发布。
