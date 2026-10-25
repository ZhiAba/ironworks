# 属性伤害（v2.6：元素 × 形态）

> 旧版 v2.0 的「护甲倍率 + 固定克制表」已被 v2.6 的穿甲曲线 + 独立元素附着取代。
> 完整公式、Combo 表、过穿规则和已知边界见 [`COMBAT-SYSTEM.md`](COMBAT-SYSTEM.md)。
> 本文只保留当前代码入口和内容归属，避免旧文档继续误导。

## 目标

所有伤害统一走 `Dmg.hit / Dmg.area / Dmg.pierce`，由两个正交维度描述：

- **元素**：物理 / 雷霆 / 水 / 冰 / 火 / 腐蚀；
- **形态**：动能 / 能量 / 爆炸 / 切割。

元素决定 Combo、附着与图鉴弱点；形态决定过穿、护盾交互和材质表现。
`能量` 是形态，不是元素——`雷霆-动能`（磁轨）和 `雷霆-能量`（电弧）可以共用
“雷霆”元素，却有过穿/护盾上的不同行为。

## 代码入口

| 类 | 作用 |
|---|---|
| `DamageType` | 6 个元素、默认形态、默认穿甲曲线、附着状态 |
| `DamageForm` | 4 个形态；`kinetic` / `bypassShields` 两个行为位 |
| `ArmorPen` | 额定穿甲、阈值、线性衰减、10% 保底 |
| `ElementCombo` | 独立 Aura 表、7 条 Combo、图鉴弱点 |
| `TargetMaterial` | 建筑=结构；高甲/高血单位=装甲；其余=软体 |
| `Dmg` | 注册表、结算、范围伤害、弹体溅射接管、过穿标记 |
| `TypedBullets` | 带 `dtype` 的弹体，`hitEntity` 统一交给 `Dmg.bulletHit` |
| `DamageNumbers` | 护甲/Combo/过穿飘字 |
| `IronworksDamageUI` | 炮塔详情页追加形态、穿甲、Combo |
| `IronworksCombatDialog` | 设置里的独立说明页 |

## 当前元素默认值

| 元素 | 默认形态 | 额定穿甲 | 阈值 | 衰减 | 附着 |
|---|---|---:|---:|---:|---|
| 物理 | 动能 | 65% | 18 | 2.0%/点 | — |
| 雷霆 | 动能 | 50% | 15 | 2.5%/点 | 电击 1.5s |
| 水 | 能量 | 30% | 12 | 2.5%/点 | 潮湿 4s |
| 冰 | 能量 | 40% | 15 | 2.5%/点 | 冻结 2s |
| 火 | 爆炸 | 25% | 10 | 2.5%/点 | 燃烧 4s |
| 腐蚀 | 动能 | 80% | 25 | 1.2%/点 | 腐蚀 5s |

具体弹种的覆盖表见 `COMBAT-SYSTEM.md` 第 3 节。

## 内容归属

| 内容 | 元素 | 形态 |
|---|---|---|
| 速射炮 / 穿甲炮 / 迫击炮基础弹 | 物理 | 动能 |
| 迫击炮 HE / 燃烧 / 破片 | 物理 / 火 / 物理 | 爆炸 |
| 迫击炮 AP | 物理 | 动能 |
| 凝固汽油弹 | 火 | 爆炸 |
| 腐蚀弹 | 腐蚀 | 动能 |
| 电弧塔 / 天罚 | 雷霆 | 能量 |
| 磁轨穿刺炮 | 雷霆 | 动能 |
| 引力奇点 | 物理 | 能量 |
| 原版 海啸 / 波浪（水 / 冷冻液 / 熔渣） | 水 / 冰 / 火 | 能量 / 能量 / 爆炸 |
| 原版 蓝瑟 | 雷霆 | 能量 |
| 原版 魔灵 | 物理 | 能量 |
| 原版 熔毁 | 物理 | 能量 |
| 原版 电弧塔 | 雷霆 | 能量（连锁仍走原版结算） |
| 未登记内容 | 物理 | 动能（兜底） |

## 写代码怎么用

```java
// 直接结算
Dmg.hit(target, 370f, DamageType.thunder);                 // 使用元素默认形态
Dmg.hit(target, 240f, DamageType.thunder, true, true,
    DamageType.thunder.pen, true, DamageForm.KINETIC);      // 磁轨：雷霆-动能
Dmg.area(team, x, y, 120f, 200f, DamageType.fire, false);  // 范围不触发过穿
Dmg.pierce(unit, 9f, DamageType.thunder);                  // 真伤，不附加状态

// 弹体：用 TypedXxxBulletType
ammo(ammoItem, new TypedBasicBulletType(DamageType.fire, 4f, 38f){{
    splashDamage = 34f;
    splashDamageRadius = 32f;
}});

// 原版内容挂到元素/形态（详情页与查询兜底）
Dmg.register(Blocks.lancer, DamageType.thunder, DamageForm.ENERGY);
```

新增元素：在 `DamageType` 枚举加一行，在 `Dmg.load()` 注册归属；
新增弹体基类：在 `TypedBullets.java` 加 `TypedXxx extends Xxx`，`Dmg.copy` 会自动识别；
新增形态：在 `DamageForm` 加枚举值，并在 `Dmg.hit()` / `Dmg.bulletHit()` 里接行为。

## 详情页

`IronworksDamageUI` 替换 `Vars.ui.content`，在 `ContentInfoDialog.show()` 之后
遍历弹药面板，把原版伤害行改成元素色，并追加：

- `形态：动能/能量/爆炸/切割`
- `穿甲 90%（阈值 25，衰减 1.5%/点）`
- `Combo：水→感电 ×1.5`
- 动能：过穿提示
- 能量：无视物理护盾提示

弹体类型/形态的解析顺序是“弹体自身覆盖 → 炮塔覆盖 → 元素默认”，
所以原版电弧塔这种“弹体没类型、方块有类型”的情况也能正确显示。

## 已知边界

- 原版 **电弧塔的连锁闪电** 仍由 `LightningBulletType` 逐跳结算，没有逐跳走穿甲；
  方块归属/详情页正确，实际连锁伤害仍是原版公式。
- 原版未拷贝的弹体仍按原版护甲/溅射结算；当前只拷贝海啸、波浪、蓝瑟、魔灵、熔毁、双管炮。
- `DamageForm.SLASHING` 已定义但还没有内容绑定。
- 范围伤害的 `complete` 地形破坏尚未完全保留。
- 没有每个单位独立的元素抗性表；高甲 + 低穿甲元素形成自然克制。

## 回归

- T154：归属、形态、穿甲曲线、10% 保底、雷霆+潮湿 Combo、建筑伤害、过穿封顶、
  过穿不触发 Combo、能量护盾不消耗、低伤/能量/重甲不过穿、AP 过穿跳过溅射。
- 伤害数字相关边界见 `DamageNumbers` 与 `docs/STATE.md`。
