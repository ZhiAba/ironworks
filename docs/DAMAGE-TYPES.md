# 属性伤害（v3.0.1：元素 × 形态）

> v2.6 的线性衰减、10% 保底、按最大生命过穿已被 v3.0 的平方衰减、
> 跳弹/过穿与四层防御管线取代。完整规则见 [`COMBAT-SYSTEM.md`](COMBAT-SYSTEM.md)。

## 两个正交维度

- **元素**：物理 / 雷霆 / 水 / 冰 / 火 / 腐蚀，负责 Combo、Aura、元素抗性、腐蚀减甲。
- **形态**：动能 / 能量 / 爆炸 / 切割，负责力场盾、跳弹、过穿、能量抗性。

`能量` 是形态，不再是元素；雷霆-动能（磁轨）与雷霆-能量（电弧）可以共存。

## 代码入口

| 类 | 作用 |
|---|---|
| `DamageType` | 6 元素、默认形态、默认穿透曲线、附着状态 |
| `DamageForm` | 4 形态；`kinetic` / `bypassShields` 行为位 |
| `ArmorPen` | 白值、基础穿透率、平方衰减、跳弹系数、K=100 减伤 |
| `Resistance` | 元素抗性 / 能量抗性表，默认 0，上限 90% |
| `Corrosion` | 永久减甲、潮湿酸蚀、15%/8% 上限 |
| `ElementCombo` | 独立 Aura、6 条 Combo、状态消耗、弱点 |
| `Dmg` | 盾→跳弹→过穿→穿透/减伤→抗性→Combo→扣血 |
| `DamageNumbers` | 跳弹/过穿/护盾/真伤/普通分类与合并 |
| `IronworksDamageUI` | 炮塔详情、敌人图鉴 |
| `IronworksCombatDialog` | 设置里的独立说明页 |

## 当前元素默认值

| 元素 | 默认形态 | 基础穿透率 | 白值 | 平方衰减 | 跳弹系数 | 附着 |
|---|---|---:|---:|---:|---:|---|
| 物理 | 动能 | 65% | 18 | 0.30 | 0.02 | — |
| 雷霆 | 动能 | 50% | 15 | 0.30 | 0.02 | 电击 1.5s |
| 水 | 能量 | 30% | 12 | 0.35 | 0.02 | 潮湿 4s |
| 冰 | 能量 | 40% | 15 | 0.35 | 0.02 | 冻结 2s |
| 火 | 爆炸 | 25% | 10 | 0.45 | 0.02 | 燃烧 4s |
| 腐蚀 | 动能 | 80% | 25 | 0.20 | 0.02 | 腐蚀 5s |

具体弹种覆盖见 `COMBAT-SYSTEM.md` 第 3 节。

## Combo

感电（雷霆+水 ×1.5）、融化（火+冰 ×1.5）、蒸发（水+火 ×1.5）、
超导（雷霆+冰 ×1.35）、冻结（冰+水 ×1.25）、碎冰（物理+冰 ×1.25）。
腐蚀不参与 Combo。

## 使用

```java
// 普通直接结算（使用元素默认曲线）
Dmg.hit(target, 370f, DamageType.thunder);

// 雷霆-动能、指定穿透曲线
Dmg.hit(target, 240f, DamageType.thunder, true, true,
    new ArmorPen(0.50f, 15f, 0.30f, 0.03f), true, DamageForm.KINETIC);

// 范围伤害：不触发跳弹/过穿
Dmg.area(team, x, y, 120f, 200f, DamageType.fire, false);

// 真伤：红色“真伤”飘字
Dmg.pierce(unit, 9f, DamageType.thunder);
```

新增元素：在 `DamageType` 枚举加一行，在 `Dmg.load()` 注册归属。
新增弹体基类：在 `TypedBullets.java` 加 `TypedXxx extends Xxx`；`Dmg.copy()` 只复制已知基类，未知弹体原样保留。
原版炮塔由 `Dmg.load()` 中的 `integrateVanillaTurrets()` 自动接入，物品/液体/电力炮塔都无需逐个写死。
新增抗性：`Resistance.set("unit-name", DamageType.fire, 0.5f)`。

## 已知边界

- 抗性表默认空；具体单位抗性尚未配置，需要主人给数值。
- 腐蚀当前只减甲，没有独立每 tick 真伤。
- 原版电弧塔的闪电分叉已换成带属性 `lightningType` 子类；逐跳命中在实机仍需验收。
- 连续激光/轨道炮/霰射弹用带属性子类接管 `hitEntity`，建筑端表现仍需实机验收。
- 切割形态已定义但没有内容。
- 无头回归不渲染 UI，视觉需实机验收。
