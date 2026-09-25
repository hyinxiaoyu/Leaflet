# ADR-0003 — 产品 Flavors（full / eink）

[English](ADR-0003-product-flavors.md) | 中文

## Context

Leaflet 必须从 Day 1 区分两个正交概念（`docs/archive/init.md` §3–§6、§60；AGENTS.md）：运行模式 Runtime Mode（STANDARD / EINK，可在设置中热切换，原则上不重启）与发行版本 Distribution Flavor（`full` / `eink`，编译期维度，产出两个独立 APK/AAB）。

独立 E-Ink 版本不是换皮：E-Ink 设备存储宝贵、不需要普通 Android 专属动画与重型功能、需要更小依赖面、未来可能集成 BOOX 厂商 SDK 而不能污染普通包（`docs/archive/init.md` §5、§7）。M0/M1 只要求真正完成 `Leaflet E-Ink`，但设置中保留显示模式概念以验证 `DisplayProfile` 架构可切换（`docs/archive/init.md` §6；实施计划 §2 采用"§6、§26 的明确切换要求优先于 §42 README 示例"的解释）。

## Decision

1. 同一 dimension 下的两个 flavor：`full` 与 `eink` 属于同一个 flavor dimension；`src/main/` 承载全部共享功能（目标 90%+ 核心代码共享，`docs/archive/init.md` §4），`src/full/` 与 `src/eink/` 只放各自特有内容。
2. 临时 applicationId 集中配置：在正式发布确认前使用临时 namespace（如 `io.leaflet.reader` / `io.leaflet.reader.eink`，`docs/archive/init.md` §1、§4），集中配置于一处、易于替换，不在代码中大量硬编码。不同 applicationId、应用名称、图标是允许的 flavor 差异。
3. flavor 只承载真正的发行差异：依赖（dependency）、资源（resource）、manifest（含权限与 applicationId）、平台适配（platform adapter）、功能可用性（feature availability）。禁止把 Reader 等业务实现复制成 `src/full/ReaderScreen.kt` / `src/eink/ReaderScreen.kt` 两套（`docs/archive/init.md` §60；AGENTS.md）。
4. 模式与 flavor 解耦：STANDARD / EINK 运行模式由 `DisplayProfile` / `MotionPolicy` / `AppCapabilities` / `EInkController` 实现差异（见 ADR-0005 与 `docs/architecture/eink.md`），M0/M1 两个 flavor 均保留基础模式切换，`eink` flavor 默认 EINK、`full` flavor 默认 STANDARD。以后锁定 E-Ink-only 需单独变更需求（实施计划 §2）。
5. 依赖隔离方向：`eink` 做减法（剔除动画资源、Web runtime、重型编辑器/AI/网络/analytics/ad SDK，`docs/archive/init.md` §7），Onyx 等厂商 SDK 将来只允许进 `eink` flavor 依赖块（见 ADR-0007）；`full` 产物不得被 E-Ink 专用依赖污染。

## Alternatives Considered

- 单包 + 运行时分支（无 flavor）：发布简单，但 APK 体积、权限、厂商 SDK 污染无法隔离，违背"两个完全独立的 APK/AAB"目标。否决。
- flavor 间复制业务代码、各写一套 Reader：短期最快，但分叉后双倍维护成本，E-Ink 修复难以同步到 full。明确禁止（`docs/archive/init.md` §60）。
- 多 dimension（device × paid 等）：MVP 无付费/渠道维度需求，过度设计。否决，保持单 dimension 双 flavor。

## Consequences

- T02 必须交付双 flavor 可编译工程与候选构建命令验证（`assembleFullDebug` / `assembleEinkDebug` 系列，命令以实际 Gradle tasks 为准）；CI 保证双 flavor 编译、单测、lint（`docs/archive/init.md` §56）。
- T13 记录 `fullRelease` / `einkRelease` 双包字节数、构建参数、R8 配置与依赖/字体/图片组成；初版大小接近是允许结果，不得为制造差异加入无用依赖。
- 任何新增依赖必须评估"eink flavor 是否需要"（`docs/archive/init.md` §52），并更新 `THIRD_PARTY_LICENSES` 与 `docs/performance/apk-size.md`。

## Follow-up

- 正式 applicationId、签名、许可证、分发渠道由项目所有者在 M1 对外发布前决定（实施计划 §8）；开发 agent 不代选、不提交生产密钥。
- M3（Full Android Edition）完善 Standard 侧体验时如需 flavor 差异扩大，以新 ADR 说明，不默默复制代码。
