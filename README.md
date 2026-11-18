# CNPC-Gun for Minecraft 1.21.1 (NeoForge)

**非官方移植版 / Unofficial port** — 把 [CNPC-Gun for 1.20.1 V1.1.0](https://www.mcmod.cn/)（作者 **XiaoBiniaoX**）
移植到 Minecraft **1.21.1 + NeoForge 21.1**，让 CustomNPCs 的 NPC 能够使用枪械与拔刀剑。

> 本项目是社区移植，**不是**原作者发布，也不代表作者观点。已在作者同意下制作，原始作品以 **GPLv3** 授权，
> 本移植继续以 **GPLv3** 发布（见 [LICENSE](LICENSE)）。

---

## 这是什么

原模组给 CustomNPCs 的 NPC 增加了一套「武器战斗」能力：

- **枪械**：NPC 自动/半自动射击、换弹、切枪、弹匣与备弹管理、后坐力与散布、开火动作；
- **拔刀剑**：连招图（combo graph）、条件判定、招式动作、居合/格挡；
- **投掷物**：雪球/鸡蛋/药水/火焰弹的弹道规划与命中判定；
- **友伤拦截**：同阵营 NPC 之间不互相误伤；
- **GUI 配置界面**：在 NPC 编辑界面里多一个标签页，编辑武器槽、动作、连招、参数；
- **脚本 API**：给 CNPC 脚本暴露 `cnpcgun` 系列对象（枪械、弹药、刀、战斗状态）。

## 运行要求

| 模组 | 版本 | 说明 |
|---|---|---|
| Minecraft | 1.21.1 | |
| NeoForge | 21.1.x | 开发验证于 21.1.209 |
| [CustomNPCs-Unofficial](https://modrinth.com/mod/customnpcs-unofficial) | 1.21.1 NeoForge 分支 | **必需** |
| [playerAnimator](https://modrinth.com/mod/playeranimator) | 2.0.x +1.21.1 | **必需** |
| [Timeless and Classics Zero (TaCZ)](https://github.com/MUKSC/TACZ-1.21.1) | 1.21.1 NeoForge 移植版 | 可选，装了 NPC 才会用枪 |

> 原 1.20.1 版还依赖 CNPC+、以及 CGM / Superb Warfare / Iron's Artifice / LRTactical / SlashBlade 等枪械或刀剑模组，
> 这些依赖在 1.21.1 尚无对应版本（或 API 变化过大），因此这一版**只保留 TaCZ 适配**，详见下文。

## 构建

```bash
# JDK 21 + Gradle 8.12（工程自带 gradle/wrapper 或用系统 gradle）
# 先把上表中三个前置模组的 jar 放进 libs/（仅本地编译用，不会打包进产物）
./gradlew build
# 产物：build/libs/cnpcgun-1.21.1-neoforge-<version>.jar
```

`libs/` 里的 jar 不会随仓库发布（第三方模组，许可各自独立）。

## 与 1.20.1 原版的差异

| 项目 | 说明 |
|---|---|
| CNPC+ 外部存储 | 原版把 NPC 枪械配置存在 CNPC+ 的 `NpcDataExternalStore` 里用于克隆共享；本移植直接写进 NPC 自身 NBT，克隆时仍从源 NPC 复制一份。 |
| SlashBlade 原生动画 | 1.21.1 的 SlashBlade 分支 API 变化太大，VMD 原生动画、刀光/幻影预览、刀耐久 mixin 未移植；拔刀剑的连招/条件/策略逻辑保留。 |
| CGM / Superb Warfare / Iron's Artifice / LRTactical | 无 1.21.1 版本，对应适配器与 mixin 已移除。 |
| NPC Pathfinding 插件 | 桥接为空实现，回落到 CustomNPCs 自带寻路。 |
| 枪械适配 | TaCZ（1.21.1 移植版）。 |

技术细节见 [PORTING-NOTES.md](PORTING-NOTES.md)。

## 已测试 / 未测试

- ✅ 1.21.1 专用服务器可启动（`Done`），全部 mixin 正常应用，`adapters: [tacz]`；
- ✅ NPC 数据（`CNPCGunData`）存盘/读档往返正常；
- ⚠️ 客户端画面（持枪动作、GUI、命中判定）需要实机验证；
- ⚠️ 与 **TaCZ 1.2.0+**、**CNPC 2025 之后的分支**可能存在 API 漂移：相关 mixin 已设为 `require = 0`，
  对不上时静默跳过而不是崩溃。

## 已知问题

- 若 CustomNPCs 内部字段再次改动，`NpcDataMixin` 的部分注入可能静默失效（日志里会有 mixin 警告），
  表现为枪械配置不再存档。
- 与原版一致的 UI 细节（滚动速度、预览角度）可能与 1.20.1 有细微差别。

## 许可与致谢

- 原始模组 `CNPC-Gun for 1.20.1 V1.1.0` © **XiaoBiniaoX**，以 **GPLv3** 授权（原 jar 内含 GPLv3 全文）。
- 本移植同样以 **GPLv3** 发布：你可以自由使用、修改、再分发，但**必须保留原作者署名与同样的 GPLv3 授权**，
  并且分发二进制时提供对应源码。
- 移植过程中使用 Vineflower 反编译、MCPConfig + Mojang 官方映射还原方法名。
- 感谢 CustomNPCs-Unofficial、playerAnimator、TaCZ 1.21.1 移植版的作者。

本项目与上述模组的作者无隶属关系，使用「CustomNPCs」「Timeless and Classics Zero」等名称仅作说明用途。

## English (short)

Unofficial community port of **CNPC-Gun** (by XiaoBiniaoX) to Minecraft 1.21.1 / NeoForge 21.1.
Gives CustomNPCs NPCs the ability to use guns (TaCZ) and bladed weapons, with a per-NPC weapon editor,
combo graphs, throwing ballistics and friendly-fire protection.

Requires: Minecraft 1.21.1, NeoForge 21.1.x, CustomNPCs-Unofficial (1.21.1 NeoForge), playerAnimator 2.0.4+1.21.1,
optionally TaCZ 1.21.1 NeoForge port for actual firearms.

Licensed under **GPLv3**, same as the original mod. This is a derivative work; original copyright belongs to
XiaoBiniaoX. Not affiliated with the original author or the dependency mods' authors.
