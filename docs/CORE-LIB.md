# 铁流核心组件库（`ironworks.core`）

> 目标：**同一套机制只实现一次**。以后凡是「液体容量判断 / 熔融侵蚀 / 冷却循环 / 热液排放 / 状态条」，
> 一律调用这里的组件，不再在方块里手抄一遍。

## 铁律

1. **纯底层重构**：只搬实现，不改任何数值（速率、容量、伤害、上限一个都不许动）。
2. **不新增游戏内容**：这个包里不允许出现新的方块/物品/液体/状态。
3. **行为等价**：搬完之后原有回归测试必须全绿（T1–T120）。
4. 新方块需要这些机制时，**先找组件，找不到才写**；写完如果是通用机制，顺手补进核心库。

## 包结构

| 组件 | 职责 | 以前散落在 |
|---|---|---|
| `LiquidKit` | 液体原子能力：单/总容量、按容量截断、白名单接收、安全交接 | 10 个文件的 `liquids.get(x) < liquidCapacity - 0.001f` |
| `Molten` | 熔融金属判定 + 侵蚀（掉血/起火）+ 给原版管道打补丁 | `IronworksHighTemp`（同一段匿名 build 抄了 5 遍） |
| `HotDump` | 热液往四邻倒：逐邻居问目标容量，单 tick 上限截断 | 过热炮塔里真实槽/虚拟槽各一遍 |
| `CoolantLoop` | 冷液 → 热液 的循环转换（真实槽 / 虚拟槽两种） | 冷却炮塔、算力集群、反应堆各写一遍 |
| `CoreBars` | 删掉引擎自动的通用液体条 + 每种液体一条 | 4 个方块里重复的 `removeBar("liquid")` |

## 用法速查

### LiquidKit

```java
// acceptLiquid：只收白名单 + 有空间（null 安全）
return LiquidKit.acceptOnly(this, source, liquid, IronworksLiquids.grapheneCoolant);

// 往自己罐里加产物：按容量截断，绝不溢出
LiquidKit.addCapped(this, IronworksLiquids.hotCoolant, flow);

// 多液体共用同一池子时（水 + 热液）：按总容量截断
LiquidKit.addCappedShared(this, Liquids.water, waterBack);

// 安全交接给邻居：能收且有空间才给
LiquidKit.give(this, dest, hot, amount);
```

### Molten

```java
Molten.isMolten(liquid);      // 熔融金属判定
Molten.burn(build);           // 每 tick 侵蚀（掉血 + 起火）
Molten.patchVanillaPipes();   // 给原版导管/储罐/交叉器/桥统一套侵蚀
```

### CoolantLoop（循环冷却）

```java
// 方块里的 build 持有一个实例
public final CoolantLoop loop = new CoolantLoop(coldLiquid, hotLiquid);

// 每 tick：热侧装不下就少消耗，冷→热按 ratio 转换
if(active) loop.convert(this, true);

// 只收冷液
public boolean acceptLiquid(Building source, Liquid liquid){
    return loop.acceptCold(this, source, liquid);
}
```

虚拟热槽（不占液体池、热满停机）只要 `loop.virtualHot = true; loop.hotCapacity = ...`。

### HotDump

```java
HotDump.drain(this, hotLiquid);                  // 从液体池往外倒
hotStored = HotDump.spill(this, hotLiquid, hotStored);  // 虚拟槽往外倒
```

### CoreBars

```java
CoreBars.perLiquid(this, coolantLiquid, hotLiquid);  // 删通用条 + 每种液体一条
```

## 故意**不**抽的部分（以及原因）

| 位置 | 为什么保留自己的实现 |
|---|---|
| 算力集群的「热液只从底边排 + 已装热液的邻格继续排」 | 是它独有的防堵管规则，和通用四邻排放不是一回事 |
| 过热炮塔的冷却液分级（tier）与过热量 | 是它的核心玩法，`CoolantLoop` 只接管「一次转换」 |
| 反应堆的背压（热液排不出去 → 升温） | 反应堆专属物理 |
| 跃迁节点的单目标跳转 | 已有 `JumpSource` 抽象，属于另一层 |

## 迁移记录（v1.0.0.0）

- `IronworksHighTemp`：`molten/burnMolten/patchVanillaPipes` 改为薄委托；石墨坩埚状态条走 `CoreBars`。
- `LiquidConveyor`、`LiquidBeltRouter/Junction/Bridge`：`acceptLiquid` 的空间判断走 `LiquidKit`。
- `IronworksTeleport`：单目标液体交接的空间判断走 `LiquidKit.hasSpace`。
- `IronworksCompute`：链接块与核心的接受判定、`hotFull()`、热液入池走 `LiquidKit`。
- `IronworksRBMK`：进水判定走 `LiquidKit.acceptOnly`；热液/回水入罐走 `LiquidKit.addCappedShared`。
- `CoolantTurret`：整段「消耗冷液 → 产热液 → 热满停机 → 倒热液」换成 `CoolantLoop` + `CoreBars`。
- `OverheatTurret`：`dumpHot()` 两种槽位合并成 `HotDump`；接受判定与热槽空间走 `LiquidKit`；状态条走 `CoreBars`。
