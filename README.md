# 铁流工业 / Ironworks

Mindustry 160.x 的 Java 模组：从铁砂、钢铁和铝开始，一路搭到核电站、元素弹药和自动化物流。

作者 / 维护者：**只会阿巴阿巴の屑**。AI 助手「肥鱼」参与实现、测试与文档整理。

源码仓库：<https://github.com/ZhiAba/ironworks>

> 当前版本：**3.3.3.0** · 游戏版本：**Mindustry 160.x** · 许可证：**GPL-3.0-only**
>
> 最近全量回归：**140/142**（T1–T202，无头服务端，754 秒）；T142/T144 为已知顺序干扰，单独复跑 2/2 通过。

## 主要内容

- **冶金与材料链**：磁选、高炉、转炉、连铸、轧制、电解精炼、铝全量、原版材料闭环。
- **弹药与炮塔**：7 种弹药、速射炮/穿甲炮/迫击炮、天罚电弧塔、磁轨与奇点武器。
- **元素 × 形态战斗**：6 种元素 × 4 种形态；平方衰减穿甲、跳弹、过穿、永久腐蚀、力场盾、元素/能量抗性与 Combo。
- **RBMK-9 核电站**：12×12 九分区堆芯、三节点热工、湿蒸汽、独立堆芯注水泵、自动跳闸锁存、卡棒维护、4×4 汽轮机；熔毁时伤害与视觉统一为旧伤害半径的 3 倍、总时长约 2 倍；主爆固定 999999、二次爆燃每次 15000，火元素+爆炸形态，友军 100%、敌军 2%；主爆后每 0.2 秒一次、贯穿全程的二次爆燃，每颗都有范围伤害与高亮光球，扩散速度 2 倍并配原版重击反应堆爆炸声；液体分类器使用动态液体帧。
- **物流**：通液传送带、液体路由/交叉/带桥、**液体分类器 / 反向分类器**、MCT 物品/液体跃迁节点。
- **自动化与辅助**：自动雷达、全域算力集群、多配方工厂、方块搜索、通知日志。
- **战斗反馈**：分类合并伤害飘字；同一目标多来源分行，反应倍率与伤害数值分色。

详细机制与版本记录：

- [`docs/CHANGELOG.md`](docs/CHANGELOG.md) — 全量变更记录
- [`docs/COMBAT-SYSTEM.md`](docs/COMBAT-SYSTEM.md) — 伤害公式与战斗管线
- [`docs/RBMK-BALANCE.md`](docs/RBMK-BALANCE.md) — 反应堆热工与平衡
- [`docs/TEST-INDEX.md`](docs/TEST-INDEX.md) — 无头回归索引

## 兼容性

| 客户端 | 版本 | 状态 |
|---|---|---|
| 原版 `io.anuke.mindustry` | 160.3 | 实测加载 |
| 改端 `com.github.tinylake.mindustryX` | 160.1 | 实测加载 |
| 低于 v160 的客户端 | — | 被 `minGameVersion` 拒绝 |

编译基准是 Mindustry **160.1**；产物包含 `.class` 与 `classes.dex`，桌面与安卓通用。

## 构建

需要 JDK 17+、Mindustry 160.1 的 jar，以及 [R8](https://r8.googlesource.com/r8) 的 `r8.jar`：

```bash
# 按你的环境修改 build.sh 顶部的 API / R8_JAR / JDK 路径
bash build.sh
# -> out/ironworks.jar
```

构建脚本会依次执行 `javac`、资源平铺、`jar`、`d8`，最后把 `classes.dex`
合并进同一个 jar。

安装：把 jar 放进游戏的 `mods/` 目录，然后**完全重启游戏进程**。

## 测试

```bash
bash tools/run-tests.sh 154 200 201 202   # 指定用例
bash tools/run-tests.sh group liquid       # 按分组
bash tools/run-tests.sh                    # 全量（约 12–15 分钟）
```

全量测试依赖无头 Mindustry 服务端和 `tools/` 中的脚本；路径按当前开发环境
写在 `tools/run-tests.sh` / `tools/chain-test.sh` 顶部，移植时按需修改。

## 仓库结构

```
src/ironworks/        Java 源码
assets/sprites/       方块、物品、液体贴图
assets/bundles/       中英文文本
tools/                构建辅助、测试、美术生成
docs/                 设计、平衡、测试与版本文档
build.sh              构建入口
```

## 许可证

本项目采用 **GNU General Public License v3.0 only**（SPDX：
`GPL-3.0-only`）。完整条款见 [`LICENSE`](LICENSE)。

这意味着你可以自由使用、修改和再分发本模组，但分发修改版或 jar 时，必须：

1. 保留版权与许可证声明；
2. 以 GPL-3.0 提供对应的完整源代码；
3. 不附加 GPL 之外的额外限制。

Mindustry 本体及部分原版素材同样按 GPL-3.0 授权；第三方素材清单见
[`THIRD-PARTY.md`](THIRD-PARTY.md)，版权与分发说明见 [`NOTICE.md`](NOTICE.md)。

本项目是非官方模组，与 Anuken / Mindustry 官方无隶属关系。

## 参与贡献

欢迎提交 issue、测试报告和 pull request。贡献规则见 [`CONTRIBUTING.md`](CONTRIBUTING.md)：
本项目**不要求 CLA、不要求转让版权**，采用 GPL-3.0-only 的入向=出向授权，
并用 `Signed-off-by` 做原创声明；请在 PR 中说明测试方式。

如果发现贴图、音效或代码来源标注遗漏，请直接开 issue，我们会更正。
