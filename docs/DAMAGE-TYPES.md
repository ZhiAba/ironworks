# 属性伤害（v2.0.0.0）

## 目标

以前模组里所有伤害都是同一种「无种类伤害」：炮塔直接 `h.damage(x)`，
弹体交给原版 `BulletType.hitEntity`。v2.0.0.0 把伤害拆成 **物理 + 元素** 两大类，
并给每个伤害一个唯一属性，后续写逻辑时只需要引用属性，不再重复写状态/护甲/克制。

核心原则：

- **一个入口**：`Dmg.hit / Dmg.area / Dmg.pierce`，任何新炮塔只关心「打多少 + 什么属性」。
- **一个注册表**：`Dmg.register(内容, 属性)`，运行时 `Dmg.typeOf(...)` 就能回答「这是什么伤害」。
- **O(1)**：命中只做一次字段/注册表查询，不 new 对象、不遍历；范围伤害复用原版 `Damage.damage`。
- **原版也能吃到**：海啸/波浪的水冰火弹、蓝瑟的雷霆激光、魔灵的能量弹在加载时各做一次反射拷贝，
  之后仍是原版同类弹体，但结算走属性入口。

## 属性表

| id | 中文 | 颜色 | 元素 | 护甲倍率 | 命中状态 | 克制 |
|---|---|---|---|---|---|---|
| `physical` | 物理 | `c8c8c8` | 否 | 1.00 | — | — |
| `thunder` | 雷霆 | `b6a8ff` | 是 | 0.50 | 电击 1.5s | 潮湿 ×1.5 |
| `water` | 水 | `5aa9ff` | 是 | 0.90 | 潮湿 4s | — |
| `ice` | 冰 | `9fe8ff` | 是 | 0.70 | 冻结 2s | 潮湿 ×1.25 |
| `energy` | 能量 | `d370d3` | 是 | 0.35 | 电击 1s | 电击 ×1.25 |
| `fire` | 火 | `ff8a3c` | 是 | 0.80 | 燃烧 4s | — |
| `corrosive` | 腐蚀 | `8fbf5a` | 是 | 0.60 | 腐蚀 5s | — |

护甲倍率直接乘在目标护甲上：`0 = 完全无视护甲`，`0.5 = 只吃一半护甲`。
建筑在原版 `Building.damage()` 里不结算 `block.armor`，`Dmg.hit` 会显式补算一次。

## 内容归属

| 内容 | 属性 |
|---|---|
| 速射炮 / 穿甲炮 / 迫击炮（高爆/穿甲/破片弹） | 物理 |
| 迫击炮（燃烧弹）、凝固汽油弹 | 火 |
| 腐蚀弹 | 腐蚀 |
| 电弧塔 / 天罚 / 磁轨穿刺炮 | 雷霆 |
| 引力奇点（持续场 + 坍缩） | 能量 |
| 原版 海啸 / 波浪（水、冷冻液、熔渣） | 水 / 冰 / 火 |
| 原版 蓝瑟 | 雷霆 |
| 原版 魔灵 | 能量 |
| 原版 电弧塔 | 雷霆 |
| 原版 熔毁 | 能量 |
| 其它未登记内容 | 物理（兜底） |

## 写代码怎么用

```java
// 1) 直接结算
Dmg.hit(target, 370f, DamageType.thunder);        // 单位/建筑通用
Dmg.area(team, x, y, 120f, 200f, DamageType.fire, false);
Dmg.pierce(unit, 9f, DamageType.thunder);          // 真伤，不附加状态

// 2) 弹体：用 TypedXxxBulletType
ammo(
    ammoItem, new TypedBasicBulletType(DamageType.fire, 4f, 38f){{
        splashDamage = 34f;
        splashDamageRadius = 32f;
    }}
);

// 3) 自定义炮塔：声明一次，命中处引用字段
public DamageType damageType = DamageType.thunder;
Dmg.hit(target, beamDamage, damageType);

// 4) 把原版内容挂到属性上（详情页/查询用）
Dmg.register(Blocks.tsunami, DamageType.water);
```

新增属性：在 `DamageType` 枚举加一行，在 `Dmg.load()` 注册归属；
新增弹体基类：在 `TypedBullets.java` 加一个 `TypedXxx extends Xxx`，
`Dmg.copy` 会自动识别；详情页不需要改动。

## 详情页

`IronworksDamageUI` 替换 `Vars.ui.content`，在 `ContentInfoDialog.show()` 之后
遍历弹药面板的 `Label`，把：

```
[stat]12[lightgray] 伤害
```

改成：

```
[stat]12[lightgray] [#5aa9ff]水[]元素伤害
```

范围伤害行同样处理。物理行显示「物理伤害」，元素行显示「X元素伤害」。
颜色来自 `DamageType.hex`，所有炮塔（原版 + 模组）统一生效，不需要为每门炮重写 `setStats`。

## 已知边界

- 原版 **电弧塔的连锁闪电**、**熔毁的持续激光** 仍由原版逻辑结算；
  它们已经被登记为雷霆/能量，详情页和 `Dmg.typeOf` 能正确回答，
  但逐跳伤害还没有走 `Dmg.hit`（需要替换 `LightningBulletType` 的连锁路径，留到后续）。
- 原版激光打建筑走 `Building.collision(Bullet)`，仍用原版建筑护甲公式；
  单位命中已经走属性。
- 溅射接管只对 `TypedXxxBulletType` 生效；原版未替换的弹体仍按原版物理溅射。
- 属性状态由 `Dmg.hit` 必中附加（`statusChance = 1`），后续要做抗性/概率时改枚举即可。

## 回归

- `T154`：查询归属（海啸水、蓝瑟雷霆、魔灵能量、汽油弹火、腐蚀弹腐蚀、天罚雷霆）+
  实时结算（雷霆对 9 甲目标伤害 > 物理、潮湿目标雷霆伤害 > 干燥、水属性命中挂潮湿）。

## 伤害飘字（2.1.0.0）

`Dmg.hit` 结算前记录 `health + shield`，结算后取差值作为实际伤害；
护甲抵消量用 `Damage.applyArmor(amount, armor * type.armorMult)` 单独算出，所以显示的是
「实际掉血（-护甲抵消）」，例如 `17（-3）`，没有抵消时显示 `-0`。

- 颜色：`DamageType.hex`。
- 生命周期：180 tick；最后 60 tick 渐隐，并上浮约 18 世界单位。
- 对象池 + 最多 64 条，超出丢最旧。
- 模组直接结算与 typed bullet 全部覆盖；未走 `Dmg` 的原版普通子弹由 `UnitDamageEvent` 兜底估算。
- typed 弹体的溅射会由 `Dmg.area(..., showNumbers=true)` 逐个单位显示；原版未替换弹体的溅射、持续激光、连锁闪电不显示，避免把直伤数值错误地显示到溅射目标上。
- 设置 → 铁流工业 → 「显示伤害数字」可整体关闭。
