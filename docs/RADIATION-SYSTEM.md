# 辐射系统（3.9.0.0）

> 适用范围：Mindustry 160.x Java 模组，实现位于 `src/ironworks/IronworksRadiation.java`。
> 这是运行时机制，不是单纯给子弹加一个状态。

---

## 1. 总流程

每 tick：

1. 扫描建筑源（每 15 tick 重建一次缓存）；
2. 对每个存活单位计算周围源 + 自身携带放射性物品；
3. 增加/衰减污染值；
4. 由污染值判定四级状态；
5. 对生物/装甲施加百分比生命伤害；
6. 对机械/装甲写入动态移速/装填减益，并在重度/致死时随机干扰；
7. 重度/致死时触发一次永久最大生命削减；
8. 更新状态图标；
9. 单位死亡时按污染值和携带物生成临时 corium。

---

## 2. 污染值

| 参数 | 值 | 说明 |
|---|---:|---|
| 轻度阈值 | 15 | 机械开始轻微干扰 |
| 中度阈值 | 40 | 生物开始明显掉血 |
| 重度阈值 | 80 | 永久损伤 15%，开始停火干扰 |
| 致死阈值 | 140 | 永久损伤 35% |
| 上限 | 240 | 防止数值无限增长 |
| 基础增速 | 6 / 秒 / 源强度 | 贴脸单源强度 1 时的增速 |
| 半衰期 | 10 秒 | 脱离所有源后的指数衰减 |

### 2.1 多源叠加

每个源先按距离衰减：

```text
falloff = (1 - distance / radius)^2
contribution = strength × falloff
```

多个源不是线性相加，而是：

```text
combined = sqrt(Σ contribution²)
```

所以两个等强度源贴脸叠加约为单源的 1.414 倍，而不是 2 倍；弱源不会把强源线性推爆。

### 2.2 物品携带

单位只携带一个 `ItemStack`。物品源强度：

```text
strength = item.radioactivity × amount × 0.35
```

上限 25。即使离开建筑范围，只要背包里还有放射性物品，污染仍会上升。

建筑库存也会产生源：

```text
strength = Σ(item.radioactivity × amount × 0.02)
```

单建筑上限 8；用于燃料棒仓库、乏燃料池、干式储存库等。

---

## 3. 单位分类

原版没有“生物”标签，因此使用显式登记 + 结构判断：

- **装甲**：带 Ironworks 外挂装甲状态、`armor >= 8`、或 `TankUnit`；
- **生物**：登记在 `IronworksRadiation.biologicalTypes` 的单位（默认 crawler / atrax / spiroct / arkyid / toxopid），或 `LegsUnit` / `CrawlUnit`；
- **机械**：其余单位。

模组单位可以：

```java
IronworksRadiation.biological(MyUnits.myBug);
IronworksRadiation.resistance(MyUnits.myBug, 0.25f);
```

注意：原版 crawler 实际生成类是 `MechUnit`，不能只靠 `instanceof` 判断，所以默认用类型名登记。

---

## 4. 生命伤害

伤害按**当前最大生命**的百分比计算，每 tick 缩放：

```text
amount = maxHealth × rate[level] × tierFactor × dt
```

| 等级 | 每秒比例（生物） |
|---|---:|
| 轻度 | 0.2% |
| 中度 | 0.8% |
| 重度 | 2.5% |
| 致死 | 7.0% |

- **生物**：全额；
- **装甲**：×0.5；
- **机械**：0（完全免疫生命扣除）；
- **高阶单位**：`tierFactor = 1 - clamp((type.health - 500) / 10000, 0, 0.3)`，最高只减免 30%。

伤害走 `unit.damagePierce()`，绕过护甲/护盾/最终减伤；这是“辐射不是普通子弹”的边界。

---

## 5. 电子干扰

机械/装甲受到动态状态减益：

| 等级 | 机械移速 | 机械装填 | 装甲移速 | 装甲装填 |
|---|---:|---:|---:|---:|
| 轻度 | 0.97 | 0.97 | 0.979 | 0.979 |
| 中度 | 0.92 | 0.90 | 0.944 | 0.930 |
| 重度 | 0.84 | 0.80 | 0.888 | 0.860 |
| 致死 | 0.70 | 0.60 | 0.790 | 0.720 |

装甲的移动/装填减益只打七折，但**电子干扰本身仍是全额**：重度/致死都会触发停火。

- 重度：每约 18 秒尝试一次，停火 18 tick；
- 致死：每约 12 秒尝试一次，停火 30 tick。

实现上使用 `applyDynamicStatus()` 写 `speedMultiplier` / `reloadMultiplier`，不覆盖外挂装甲的 `armorOverride`。

**已知边界**：原版没有可安全按单位修改的“索敌半径/转向速度/自动修复”接口，因此这三项没有硬改；污染等级、伤害、停火、移速/装填已经落地。

---

## 6. 永久结构损伤

机械/装甲在**无专用抗性**（`resistance < 0.15`）时：

- 首次到重度：`scar = 0.15`；
- 首次到致死：`scar = 0.35`；
- 只增不减；
- 当前生命高于新上限时同步压低；
- 通过 `Unit.maxHealth` 生效，而不是普通状态时间。

存读档标记：

- `radiation-scar-15`
- `radiation-scar-35`

两个状态都是永久状态，只做重建标记。连续比例 `Track.scar` 存在运行时表中；读档时按标记恢复为 15% 或 35%（净化到中间值时，读档可能回退到较接近的标记值）。

外挂装甲会提供抗性：

- 反应装甲 +0.15
- 复合装甲 +0.25
- 纳米装甲块 +0.35

抗性降低污染累积，并缩短脱离后的半衰期；抗性 ≥0.15 时不再触发新的永久损伤。

---

## 7. 死亡污染

监听 `UnitDestroyEvent`：

- 死亡时污染 ≥ 重度，或携带放射性物品，或已有结构损伤 → 在死亡点生成 1 格临时 corium；
- 强度：`1 + itemStrength × 0.5 + pollution / 70`，夹在 1~8；
- 半径：`48 + pollution × 0.22 + itemStrength × 2`，夹在 48~120；
- 临时 corium 寿命 240 秒，强度半衰期 180 秒；
- 主熔毁的 corium 仍按原逻辑：生存永久、沙盒 180 秒凝固。

临时 corium 会阻碍通行，但到期会 `tile.remove()`，不会永久堵路。

---

## 8. 建筑源与净化

### 8.1 自定义建筑源

实现接口：

```java
public class MyReactorBuild extends Building implements IronworksRadiation.Emitter{
    @Override
    public float radiationStrength(){
        return 1f + heat;
    }

    @Override
    public float radiationRadius(){
        return 96f;
    }
}
```

或注册固定源：

```java
IronworksRadiation.source(MyBlocks.wasteTank, 1.2f, 80f);
```

RBMK 使用动态强度：

```text
0.2 + heat × 1.1 + flux × 0.45
```

冷停堆仍有一点点，熔毁后归零（由 corium 接替）。

### 8.2 辐射净化舱

`IronworksDecontaminator.decontaminator`：

- 3×3，耗电 2.5；
- 半径 104 世界单位；
- 每 10 tick 对范围内友军执行一次净化；
- 每秒恢复 0.008 的永久损伤比例（35% 约需 44 秒）；
- 同时按 `amount × 800` 快速冲掉污染；
- 只恢复上限，不复活死亡单位。

---

## 9. 物品放射性

`IronworksNuclear` 的物品已经写入 `Item.radioactivity`：

| 物品 | 放射性 |
|---|---:|
| 铀矿石 | 0.05 |
| 黄饼 | 0.15 |
| 浓缩铀 | 0.50 |
| 燃料棒 | 0.80 |
| 乏燃料棒 | 1.00 |
| 冷却乏燃料棒 | 0.35 |
| 钚 | 0.90 |
| 高放废料 | 1.50 |
| 固化废物体 | 0.10 |

原版钍 1.0、相织布 0.6、裂变物质 1.5 继续沿用。

---

## 10. 属性伤害兼容

新增 `DamageType.radiation`（颜色 `9be86a`，形态 ENERGY）。当前辐射生命伤害直接走 `damagePierce`，属性枚举主要用于：

- 详情页/飘字/后续贫铀弹、核弹的伤害归属；
- `Resistance` 表自动扩展；
- 后续 `Dmg.hit(..., DamageType.radiation)` 可直接接入现有属性管线。

`Dmg` 的 `FinalDamage`、跳弹/过穿、Combo 等逻辑没有被辐射系统改写；辐射伤害是独立的真实伤害路径。

---

## 11. 调参入口

都在 `IronworksRadiation`：

- `THRESHOLDS`
- `GAIN_PER_SECOND`
- `DECAY_HALF_LIFE`
- `DAMAGE_PER_SECOND`
- `SCAR_SEVERE` / `SCAR_LETHAL`
- `JAM_SEVERE` / `JAM_LETHAL`
- `SOURCE_REFRESH_TICKS`
- `UNIT_ITEM_SCALE` / `BUILDING_ITEM_SCALE`

调整后至少跑 T235，并实机确认状态图标、停火和净化舱表现。

---

## 12. RBMK 屏幕抖动

`IronworksFissionFx`：

- 主爆炸：`Effect.shake(32, 240, x, y)`；
- 每次二次爆燃：`Effect.shake(4, 26, sx, sy)`，不再只每 6 次抖一次；
- 抖动强度仍由游戏设置 `screenshake` 缩放，最高 100 的引擎上限没有触碰。

主爆幅度大但时间短，二次爆燃幅度小；二者都按距离衰减，远处不会把镜头抖飞。
