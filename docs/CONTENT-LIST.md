# 铁流工业 v1.7.0.0 注册实体全量清单

运行时统计（`Vars.content` 中 `minfo.mod != null` 的内容，无头端 160.3 实测）：

| 类型 | 数量 |
|---|---|
| 物品 | 73 |
| 液体 | 18 |
| 方块 | 85 |
| 状态效果 | 9 |
| 单位 | 0 |

**合计 160 个注册实体**。命名规则：游戏内 ID = `ironworks-<名字>`；贴图文件名不加前缀。

## 方块

| 分组 | 数量 | 内容 |
|---|---|---|
| 钢铁/冶炼 | 7 | `separator` 磁选机<br>`reducer` 氢还原炉<br>`blast-furnace` 高炉<br>`converter` 转炉<br>`caster` 连铸机<br>`arc-furnace` 电弧炉<br>`steel-wall` 钢墙 |
| 化工 | 7 | `electrolysis-plant` 电解车间<br>`air-separator` 空气分离机<br>`desulfurizer` 脱硫塔<br>`acid-plant` 制酸车间<br>`powder-mixer` 火药混合机<br>`precious-leach` 贵金属浸出车间<br>`chip-fab` 芯片车间 |
| 石油化工 | 2 | `refinery` 炼油厂<br>`polymer-works` 聚合车间 |
| 原版替代厂 | 8 | `silicon-furnace` 硅晶炉<br>`glass-kiln` 玻璃窑<br>`graphitizer` 石墨化炉<br>`polymerizer` 聚合釜<br>`phase-weaver` 相织机<br>`alloy-furnace` 合金炉<br>`pyro-works` 火工车间<br>`scrap-works` 废料分选厂 |
| 加工 | 2 | `rolling-mill` 轧机<br>`press` 冲压机 |
| 弹药/桶装 | 4 | `ammo-assembler` 弹药组装机<br>`shell-assembly` 炮弹组装室<br>`barrel-filler` 封装机<br>`barrel-emptier` 拆封机 |
| 高温/冷却 | 9 | `graphene-forge` 石墨烯炉<br>`fluidizer` 石墨烯流化机<br>`coolant-plant` 冷却工厂<br>`hot-conduit` 耐高温管道<br>`coolant-conduit` 冷却循环管道<br>`hot-router` 耐高温路由器<br>`hot-junction` 耐高温交叉器<br>`hot-bridge` 耐高温管道桥<br>`graphite-crucible` 石墨坩埚 |
| 炮塔 | 7 | `rapid-cannon` 速射炮<br>`piercer` 穿甲炮<br>`mortar` 迫击炮<br>`arc-tower` 电弧塔<br>`rail-tower` 磁轨穿刺炮<br>`singularity-tower` 引力奇点发射器<br>`tianfa` 天罚（4×4 四炮口电弧塔） |
| 通液物流 | 8 | `liquid-conveyor` 通液传送带<br>`liquid-belt-router` 通液路由器<br>`liquid-belt-junction` 通液交叉器<br>`liquid-belt-bridge` 通液带桥<br>`liquid-conveyor-adv` 强化通液传送带<br>`liquid-belt-router-adv` 强化通液路由器<br>`liquid-belt-junction-adv` 强化通液交叉器<br>`liquid-belt-bridge-adv` 强化通液带桥 |
| 支援 | 3 | `nano-field` 纳米修复场<br>`jade-beacon` 玉碎信标<br>`critical-device` 临界装置 |
| 雷达（1.7.0） | 1 | `auto-radar` 自动雷达 3×3（103×103 索敌，链接 10 炮塔） |
| 装甲 | 2 | `armor-press` 装甲压制机<br>`armor-dock` 外挂装甲坞 |
| 算力（0.62.0） | 7 | `compute-cluster` 全域算力集群（4×4 核心，占地 4×6）<br>`compute-micro` 微型算力集群 1×1<br>`compute-basic` 基础算力集群 3×3<br>`compute-advanced` 高级算力集群（2×2 核心，占地 2×3）<br>`compute-quantum` 量子算力集群 2×2<br>`compute-cluster-link` 主集群链接块 2×2（隐藏）<br>`compute-advanced-link` 高级模块通风块 1×1（隐藏） |
| 跃迁（0.64.0） | 2 | `mct-item-node` 物品跃迁节点 1×1<br>`mct-liquid-node` 液体跃迁节点 1×1 |
| 调试 | 2 | `liquid-monitor` 液体计量器<br>`verbose-logger` 全量日志器 |
| 矿脉/环境 | 2 | `iron-ore` 铁矿脉<br>`bauxite-ore` 铝土矿脉 |

隐藏方块（链接块）不出现在建造菜单，只由主块自动生成：拆除/被毁时连带清除，读档按相对坐标重新绑定。

## 液体（15）

`oxygen` 氧气 · `acid` 酸 · `molten-iron` 铁水 · `molten-steel` 钢水 · `molten-copper` 熔融铜 · `molten-lead` 熔融铅 · `molten-titanium` 熔融钛 · `molten-surge` 熔融巨浪合金 · `molten-aluminum` 熔融铝 · `sodium-aluminate` 铝酸钠溶液 · `graphene-coolant` 石墨烯冷却液 · `hot-coolant` 热冷却液 · `chiller` 冷冻液 · `gasoline` 汽油 · `heavy-oil` 重油

## 状态效果（8）

`corrosion` 腐蚀 · `overload` 过载（天罚专属，减速 + 穿甲持续伤害，潮湿翻倍） · `nano-armor` 纳米装甲 · `nanite-armor` 纳米装甲块 · `jade-shatter` 玉碎 · `critical` 临界 · `reactive-armor` 反应装甲 · `composite-armor` 复合装甲

## 统计方式

本清单由无头端（`server-release-160.3.jar` + 本模组）运行时查询 `Vars.content.*` 生成，
物品/液体/方块/状态的数量与名称均以实际注册结果为准；版本升级后可用同样方式复核。
