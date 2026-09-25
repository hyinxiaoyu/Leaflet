# ADR-0007 — Onyx SDK 集成

[English](ADR-0007-onyx-sdk-integration.md) | 中文

## Context

BOOX / Onyx 设备的官方参考是 `onyx-intl/OnyxAndroidDemo`（`docs/archive/init.md` §19、§46）。T00 已形成研究报告 `docs/research/onyx-epd.md`：EpdController 底层接口、`EpdDeviceManager` 应用层封装（含 GC 间隔计数器、`enter/exitAnimationUpdate` 配对语义）、UpdateMode 全表（DU / GU / GC / REGAL 等）、UpdateOption 应用档、ABI 与 native 传递物、私有仓（明文 http）与许可证不一致（仓库 Apache-2.0 vs mvnrepository 标注 BSD 3-clause，随包文本未验证）等事实。

MVP（M0–M1，含 T12）不集成任何 Onyx SDK（onyx-epd §5 决定）：私有仓 + 传递依赖污染依赖图、ABI/体积/Play/许可证四项未验证、无真机时波形结论无法验收，而 MVP 的 E-Ink 体验完全可用框架手段实现。本 ADR 记录未来集成的隔离前提，在前提满足前保持 Proposed，M5 前不复议。

## Decision（暂定，待前提满足后复议）

1. 集成目标版本为 M5（`docs/product/roadmap.md`：Onyx EPD integration / advanced refresh control / device-specific optimizations，`docs/archive/init.md` §39）。M5 前不复议、不预建 `OnyxEInkController` 空壳。
2. 集成必须逐项满足 `docs/research/onyx-epd.md` §6 的七项前提：
   - (1) 源码隔离：实现只进 `eink` flavor 独立 sourceSet（及 `:core:eink` 下的 onyx 子包/子模块）；`full` flavor 零引用，Reader 核心仍只依赖 `EInkController` 接口。
   - (2) 依赖隔离：Onyx 坐标只声明在 `eink` flavor 依赖块；`fullRelease` 产物不得含有 Onyx 字节码/native（T13 以 APK 分析自证）。
   - (3) 版本 pin 与供应链：固定经核验的 `onyxsdk-device` 版本（以随包 API 为准重核 onyx-epd §2.1 中标未验证项）；私有仓 https/代理、CI 可复现、传递依赖逐项记录。
   - (4) 许可证闭环：随包 LICENSE/NOTICE 核对完成，`THIRD_PARTY_LICENSES` 更新；与仓库 Apache-2.0 不一致处写明。
   - (5) ABI/体积/权限基线：双 ABI 实机安装验证、双包体积差记录、merged manifest 权限评审。
   - (6) 行为契约：把 primitive 包成与 Generic 同语义的意图方法；Fast Mode 进出配对、GC 间隔可配并默认保守；非 BOOX 设备自动降级为 Generic 行为（能力检测，不做机型白名单）。
   - (7) 验收：E-Ink 实机矩阵（6"/7"/10.3" 类别）给出残影/闪烁/翻页结论；失败回退即关闭该实现、回 Generic，不阻塞 full 包。
3. 在集成发生前，ADR-0005 的全部禁令继续有效：Reader 永不直引 SDK；不声称通用实现可控制硬件局刷；不展示无法兑现的硬件档位。

## Alternatives Considered

- MVP 即集成：见 §Context 否决理由（onyx-epd §5）。否决。
- 永久不集成、纯框架手段走到底：若实机矩阵证明通用手段残影可接受，此选项成立；但 BOOX 局刷/全刷波形是厂商私有能力，提前宣称"永远不需要"证据不足。保持开放，由 M5 实机证据决定。
- 全包集成（full 也含 SDK）：污染普通包体积与权限，与 ADR-0003 依赖隔离方向冲突。否决。

## Consequences

- 本 ADR 为 Proposed 期间，任何 Onyx SDK 坐标、仓库声明、相关实现代码进入工程都视为违反架构决策；T12 验收"不接 Onyx SDK"保持有效。
- 长期跟踪 `onyx-intl/OnyxAndroidDemo` 的 SDK 版本与 doc 变更（锚定 commits 见 onyx-epd §0）。

## Follow-up（Open Questions）

- `onyxsdk-device` 是否真实托管于 Maven Central（search.maven.org 直查 403，未验证）。
- SDK 构件真实授权文本（随包 LICENSE/NOTICE 待下载核对）。
- APK 体积增量、完整 native `.so` 清单、Play 兼容（均未验证，M5 集成时补）。
- 以上全部关闭后，本 ADR 方可由 Proposed 转 Accepted；若方案变化，新增 ADR supersede 本文件，不改写历史。
