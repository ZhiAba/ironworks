# RBMK-9 平衡数据、逻辑与公式（v2.4.0.1）

> 以当前代码为准：`src/ironworks/IronworksRBMK.java`、`IronworksNuclear.java`、`IronworksRbmkAlerts.java`。
> `dt = delta()/60`（秒），`second = delta()`（秒）。所有“/s”都是按 60 tick=1 秒折算。
> **重要更正：熔毁确认是连续 60 秒，不是 1 秒。** 旧版说明和部分注释里的“1 秒”是错的，代码 `meltTimer >= 60f` 才是实际值。

## 0. 先给结论

- 12×12 `RBMK-9`：9 个 4×4 分区，额定 **3240 L/s** 总水汽，单区 **360 L/s**。
- 手动 125% 通量峰值：**4050 L/s**（9×450 L/s），沿用普通 RBMK 的 125% 规则。
- **当前没有“温度越高，水蒸发越快”的耦合。** 水→汽速度只由各区的 `zoneFlux` 决定；`heat` 不进入 `nominal`/蒸发公式。
- 每个分区有独立热、通量、棒位、流量；水罐/汽包/给水泵/燃料/氙/硼是全局共用。
- 单区温度 `>= 1.25` 连续 **60 秒** → 整堆熔毁；低于阈值时熔毁计时按 2×速度回落。
- 分区堆水罐 **32400 L**、汽包 **6480 L**、给水泵 **4700 L/s**。
- 4×4 大型汽轮机：**360 蒸汽/s → 72000 电/s + 360 温水/s**（1:1）。
- 2×2 小型冷却塔 **120 L/s**；4×4 大型冷却塔 **600 L/s**；3240 L/s 回水需要 **6 台大型塔**。

## 1. 方块/容量/额定值

| 项目 | 旧 5×5 RBMK | 12×12 RBMK-9 |
|---|---:|---:|
| 方块尺寸 | 5×5 | 12×12 |
| 分区 | 无 | 3×3 个 4×4 |
| 额定总水汽 | 980 L/s | **3240 L/s**（9×360） |
| 手动 125% 峰值 | 1225 L/s | **4050 L/s**（9×450） |
| 水罐 | 10000 L | **32400 L** |
| 汽包 | 2000 L | **6480 L** |
| 液容合计 | 12000 L | **38880 L** |
| 给水泵 | 1400 L/s | **4700 L/s** |
| 料仓 | 40 | 180 |
| 血量 | 9000 | 32000 |
| 废料/熔毁 | 26 格爆炸 | 同左 |

- 水罐/汽包共享一个 `liquidCapacity`；`LiquidKit.totalSpace()` 是水+汽+硼酸的总空间。
- 分区堆的 `waterCap/steamCap/feedRate/ratedFlow` 是方块字段；所有水位、告警、给水、SCRAM 都读当前方块值，不再读旧静态 10000/2000。

## 2. 每个 tick 的物理顺序（分区堆）

`updateZoned(dt, second, hasFuel)` 的实际顺序：

1. 初始化 9 区（首次）：
   - `zoneHeat=heat`（默认 0）
   - `zoneFlux=0`
   - `zoneFlow = max(flow,0.01) * zoneBias[i]`
   - `zoneRods=rods`（默认 1，即全插）
   - `zoneTarget=targetRods`（默认 1）
   - `zoneThrottle=1`
2. 手动模式同步水位；自动/半自动平滑更新水位线。
3. 汇总 `heat=max(zoneHeat)`、`flux=avg(zoneFlux)`、`flow=avg(zoneFlow)`。
4. `updateProtection()` 算保护杆位 `protectRods`（自动/半自动）。
5. 控制棒：手动每区直接取 `zoneTarget`；自动/半自动下发公共目标。
6. 排汽：从非进水侧尝试 `dumpLiquid(steam, 2, side)`。
7. `pullFeedwater()`：低于低线启泵，锁存到高线。
8. 水→汽：算 9 区需求、总转化量、按权重分配、更新 `zoneFlow`、`steamRate`。
9. 每区热积分、`promptHeat` 衰减、全局 `heat=max`、更新 `tempK`。
10. 燃料/氙/硼/永久中毒。
11. 压力 AZ-5 判定（仅手动）。
12. 熔毁计时、UI、告警。

## 3. 中子通量与目标功率

### 3.1 单区目标通量

```
rodCeiling_i = (1 - zoneRods[i]) * (hasFuel ? 1 : 0)
poison         = clamp(1 - 0.65*xenon - 1.2*boron, 0, 1)
target_i       = 1.25 * rodCeiling_i * poison
if(poisoned) target_i = 0
target_i      *= 1 + (1 - zoneFlow[i]) * 1.2        // 空泡正反应性
target_i       = clamp(target_i, 0, manual ? 1.25 : 1.0)
zoneFlux[i]    = lerpDelta(zoneFlux[i], target_i, 0.008)
```

- `zoneRods` 是插入比例：0=全提，1=全插。
- **毒物是乘法因子中的一个加法括号**：氙和硼先合成一个 `poison`，再乘到通量上；不是分别乘两次。
- `poison` 下限是 0；氙 1.0 时 `1-0.65=0.35`，硼 1.0 时 `1-1.2` 被钳到 0。
- **空泡项**是加在“通量倍数”里的：流量越差，目标通量最高到 `2.2×`；再被 `1.25` 总上限钳住。
- 自动/半自动目标通量上限是 **1.0**；只有强制手动允许到 **1.25**。
- `lerpDelta(..., 0.008)` 是一阶平滑，不是瞬跳；实际响应约数秒量级。
- 全局 `flux = avg(zoneFlux)`；燃料/氙/硼用全局平均通量。
- 反应堆 `productionEfficiency=flux`，但 `getPowerProduction()=0`；它不发电，只产汽。

### 3.2 控制棒速度

| 模式 | 实际动作 |
|---|---|
| 强制手动 | `zoneRods = zoneTarget` 每 tick 瞬动，无行程 |
| 自动 | `approachDelta(zoneRods, common, common>rods ? 1/90 : 1/2000)` |
| 半自动 | 与自动相同：插入 1/90 per tick，拔出 1/2000 per tick |
| 永久中毒 | `approachDelta(zoneRods, 1, 1/180)`，同时通量按 0.02/tick 归零 |

- `1/90` ≈ 1.5 秒走完全程；`1/2000` ≈ 33.3 秒；`1/180` ≈ 3 秒。

## 4. 温度、发热与“蒸发耦合”

### 4.1 单区热积分（原公式没改）

```
heatGen_i  = zoneFlux[i] * 0.14 + zonePrompt[i]
cooling_i  = zoneFlow[i] * (0.06 + 0.09 * zoneHeat[i]) + 0.01
zoneHeat_i = clamp(zoneHeat_i + (heatGen_i - cooling_i) * dt, 0, 2)
zonePrompt_i = max(0, zonePrompt_i - 0.22 * dt)
```

- `cooling` 对 `zoneFlow` 是**线性**，对 `zoneHeat` 也是**线性**；没有 `当前水量/上限` 的非线性项。
- `zoneFlow` 是“本区实际获得的冷却比例”（0..1），不是水罐百分比；水不够时它会下降。
- `zonePrompt` 只由 SCRAM 闪蒸等事件注入，按 0.22/s 衰减。
- 全局 `heat = max(zoneHeat)`；**任意一区到达熔毁阈值都算整堆**。

### 4.2 平衡点例子（无 prompt）

| 通量 | zoneFlow | 平衡 heat | 约显示温度 |
|---:|---:|---:|---:|
| 1.00 | 1.00 | 0.778 | 669 |
| 1.00 | 0.96 | 0.838 | 699 |
| 1.25 | 1.00 | 1.167 | 863 |
| 1.25 | 0.96 | 1.243 | 902 |
| 1.00 | 0 | 到上限 2.0 | 1280 |
| 1.25 | 0 | 到上限 2.0 | 1280 |

- `tempK = 280 + heat*500`，UI 标成 °C；这是显示标度，不是严格热力学温度。
- 1.00 通量、1.00 流量平衡在 77.8%；1.25 通量、0.96 流量已经到 124.3%，离 125% 熔毁线非常近。这是手动 125% + 最低节流区最危险的组合。
- `heat` 到 1.25 后还要连续 60 秒才爆；但 124.3% 的平衡点意味着一个很小的扰动就可能把它推过线。

### 4.3 “温度越高蒸发越快”？

**没有。当前代码没有这个耦合。**

水→汽公式里只有：

```
nominal_i = max(zoneFlux[i], 0) * 360 * dt
```

没有 `heat`、没有 `waterLevel/waterCapacity`、没有蒸发曲线。温度升高不会让同样通量下多产汽；它只通过以下间接路径影响系统：

1. 温度高 → 自动/半自动的 `heatFactor` 降低目标功率；
2. 温度高 → `protectRods` 插棒；
3. 到 125% 连续 60 秒 → 熔毁。

如果你要做“温度加速蒸发”，需要显式改 `nominal_i`（例如乘 `1 + k*max(0,heat-1)`），现在的平衡数据不包含这项。

## 5. 水→蒸汽速度

### 5.1 需求与转化

对每区：

```
throttle_i = manual ? zoneThrottle[i] : 1.0
nominal_i  = max(zoneFlux[i], 0) * 360 * dt          // L this tick
effectiveTotal = sum(nominal_i * throttle_i)
convertedTotal = clamp(min(
    effectiveTotal,
    actual water,
    steamCap - steam,
    total liquid space
), 0, +inf)

liquids.remove(water, convertedTotal)
liquids.add(steam, convertedTotal)                  // 1:1
```

- **额定 100% 通量：9×360=3240 L/s**。
- **手动 125%：9×450=4050 L/s**。
- 节流阀只降低本区实际进水量，也降低本区产汽；总蒸汽按实际转化量走。
- 水量/汽包空间不够时，先到哪个上限就取哪个；不会产负蒸汽。
- `steamRate` 是转化量/delta 的平滑值（平滑系数 0.03/tick），面板显示用。

### 5.2 水罐/汽包/给水泵

- 水罐：32400 L；汽包：6480 L；总液容 38880 L。
- 高水位线（手动 `waterTarget`）默认 **100%**；低水位线（`waterLowTarget`）默认 **55%**。
- `acceptLiquid` 只收到高线；`handleLiquid` 还会按剩余空间截断每一批，外部 5000 L 也不能冲过高线。
- `pullFeedwater`：
  - `high = waterLimit()`（手动=高线%，自动=waterLevel）
  - `low = waterLowLevel * waterCap`
  - 水低于 `low` 才开泵，开泵后锁存到 `high`
  - 单 tick 预算 `feedRate*dt = 4700*dt`
  - 只从相邻、同队、有水的建筑转移
- **蒸汽满了不会直接关闭给水泵**；给水泵只受水位线和相邻水源限制。蒸汽满时真正停的是“转化/冷却”——因为 `steamRoom` 为 0，`convertedTotal=0`，每区 `zoneFlow` 掉到 0，冷却只剩 `0.01`。

## 6. 9 区进水分配（重点）

### 6.1 不是平均分，是“需求 × 固定偏差 × 手动节流”的加权分

```
weight_i     = nominal_i * zoneBias[i] * throttle_i
converted_i  = convertedTotal * weight_i / sum(weight)
zoneFlow_i   = converted_i / nominal_i        (nominal_i>0)
```

- `nominal_i` 是本区按通量算出的需求：`flux_i*360*dt`。
- `zoneBias` 是固定并联通道偏差，不随机：

| 区号 i | 0 | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| `zoneBias` | 0.960 | 0.995 | 0.985 | 0.975 | 0.965 | 1.000 | 0.990 | 0.980 | 0.970 |

- 平均水平约 0.98；所以满水满通量时 `zoneFlow` 平均约 0.98，最低区 0.96、最高区 1.00。
- 手动节流 `zoneThrottle` 0..100%，总控写 9 区；选单区只写该区。
- 自动/半自动强制 `throttle=1`；单区节流只在强制手动生效。
- 如果某区 `throttle=0` 或 `nominal=0`，它拿不到水，`zoneFlow→0`；其它区仍按权重重分。

### 6.2 每区的“实际水量”和“上限”

- 没有每区独立水罐；水是整堆共享的。
- 本区实际转化水量 = `converted_i`（L/tick），折合 L/s 为 `converted_i/dt`。
- 本区冷却用的 `zoneFlow` 是“实际/需求”的比例，再经过 `lerpDelta(..., 0.08)` 平滑；所以冷却响应比水量变化略慢。
- 本区没有独立的“水量上限”；上限来自整堆水罐、整堆汽包和整堆液容。
- 水位百分比是整堆 `liquids.water / waterCap()`，不是单区。

## 7. 冷却与憋压

### 7.1 单区冷却效果

```
cooling_i = zoneFlow_i * (0.06 + 0.09 * zoneHeat_i) + 0.01
```

- 是 `flow × (常数 + 0.09×热)`，没有 `waterLevel/waterCapacity` 的非线性衰减。
- 当水不够时，`zoneFlow` 下降；`zoneFlow=0` 时冷却固定 `0.01/s`。
- 当温度升高时，同样的 `zoneFlow` 反而冷却略强（0.09×heat 项）；这是热方程的自限项，不是蒸发加速。
- 温度到 1.25 连续 60 秒才熔毁；不是一到 125% 就爆。

### 7.2 蒸汽满 / 水满 / 憋压

- 排汽：每 tick 从非进水侧 `dumpLiquid(steam, 2, side)`；汽包满后，转化被 `steamRoom=0` 卡住。
- 转化被卡住 → `zoneFlow` 向 0 收敛 → 冷却只剩 0.01 → 温度上升。
- **给水泵不会因为汽包满而停机**；如果水罐未到高线还会继续抽水。
- 手动 AZ-5 憋压条件：
  - 手动模式
  - 实际水罐 ≥ 98%（32400×0.98=31752 L）
  - 汽包 ≥ 6480-2 L
  - 9 区 `zoneTarget` 与 `zoneRods` 全部 ≥0.995
  - 有燃料且（heat>0.1 或 flux>0.02）
  - 持续 2 秒 → `detonate()`
- 自动/半自动没有压力爆炸判定，最多插棒。

## 8. 熔毁判定

```
if(heat >= 1.25):
    meltTimer += second
    if(meltTimer >= 60): detonate()
else:
    meltTimer = max(0, meltTimer - second*2)
```

- 全局 `heat=max(zoneHeat)`；任意一区热到 1.25 都累计同一个 `meltTimer`。
- **阈值 1.25（125%）**；**连续 60 秒**；低于阈值后计时以 2×速度回落。
- 不是“1 秒”，旧说明是错的；平衡时别按 1 秒算。
- 熔毁后：
  - `meltRadius=26` 格
  - `meltDamage=12000`
  - `scale=0.45 + min(flux,1.4)*0.45 + min(heat,1.6)*0.35`，半径/伤害再乘 `min(scale,1.6)`
  - 能量属性伤害、1~5 格 corium、辐射 900 tick，落尘半径 `26*8*1.6` 世界单位
- 拆除/被摧毁时：如果 `flux>0.15` 或 `heat>0.5` 也会 `detonate()`；冷停堆拆掉不炸。

## 9. 三种控制模式与保护

### 9.1 强制手动

- 9 区棒位瞬动；单区节流可用；无自动保护。
- 高水位线/低水位线都可手动设；高线是水罐硬上限。
- 只有手动允许 125% 通量和压力 AZ-5。

### 9.2 半自动

- 操作员设公共目标棒位 `targetRods`。
- 实际公共目标：`common = max(targetRods, protectRods)`。
- 9 区共用 `common`；`targetRods` 不被保护量写回（不会棘轮抬高）。
- 插棒快（1/90 per tick），抜棒慢（1/2000）。

### 9.3 全自动

```
steamFrac = steam / steamCap
waterFrac = water / waterCap
steamFactor = clamp(1 - (steamFrac-0.5)/0.4, 0, 1)
waterFactor = clamp((waterFrac-0.1)/0.2, 0, 1)
heatFactor  = clamp((1.15-heat)/0.2, 0, 1)
effective   = autoPower * min(steamFactor, waterFactor, heatFactor)
poison      = clamp(1 - 0.65*xenon - 1.2*boron, 0.05, 1)
voidFactor  = 1 + (1-flow)*1.2
needCeil    = effective / (1.25 * poison * voidFactor)
common      = clamp(1 - needCeil, 0, 1)
common      = max(common, protectRods)
```

- 自动水位线：`high=clamp(0.55+0.35*min(flux,1),0.55,0.9)`，`low=high-0.2`，均按 0.02/tick 平滑。
- `heatFactor` 在 95%~115% 之间从 1 降到 0；`steamFactor` 在汽包50%~90%降；`waterFactor` 在水罐10%~30%降。

### 9.4 `protectRods` 保护量

`updateProtection` 看：

- 水位低于 `max(waterLowLevel,0.10)*0.75` → `below`
- 无水源时，用库存/消耗算 `runway`，剩余9→3秒线性升风险
- 堆温趋势外推2.5秒，超过0.92开始升风险
- 流量低于0.9、汽包超过80%也加权
- 取 `max(waterRisk, heatRisk, flowRisk*0.9, steamRisk*0.85)`
- 风险上升用0.10/tick、下降0.02/tick平滑
- 热≥1.0时强制 `protectRods≥0.98`；自动/半自动最后防线也会在heat>1.0时拉满。

## 10. SCRAM / AZ-5 数据

- 手动 `applyConfig(-1)`：
  - 如果 `heat>0.75 && flux>0.9`，先 `startScramFlash`
  - 9 区目标棒位=1；全局热冲击 `jump` 平分到9区
  - `zonePrompt` 取 `promptHeat`
- `startScramFlash`：
  - 需要水≥100 L
  - `severity = clamp(0.30 + flux*0.25 + heat*0.12 + waterFrac*0.18, 0.30, 0.75)`
  - `flash = water * severity`，1:1 水→汽
  - `promptHeat = max(promptHeat, 0.16 + severity*0.18)`
  - `heat += 0.02 + severity*0.06`
  - `scramBoom=4s`，只用于特效/告警计时，不直接引爆
- 压力 AZ-5：见 7.2，2 秒倒计时。

## 11. 燃料、氙、硼

```
fuelUse += flux * (fuelRod?1/900 : 0.05) * dt
iodine += (flux*0.02 - iodine*0.011) * dt
xenon  += (iodine*0.012 - xenon*(0.0012 + flux*0.06)) * dt
```

- 燃料棒：满通量 900 秒/根；钍：满通量 20 秒/个（0.05/s）。
- 燃料棒烧完变成乏燃料棒；乏燃料仓满时停止消耗。
- 氙上限1.5，碘上限1.5。
- 硼：`boron += boricAcid_used*0.25`；每秒最多吃4 L 硼酸，即最多 +1硼/s；硼到1 → `poisoned=true` 永久。
- `boronReactivity=0.05`/`boronDecaySeconds=45` 目前只是常量，实际反应性走 `poison` 的 `1.2*boron` 项；不要在平衡表里把这两个当生效参数。

## 12. 汽轮机与冷却塔（配套数据）

### 12.1 汽轮机

| 方块 | 尺寸 | 蒸汽 | 电 | 温水 | 液容 |
|---|---:|---:|---:|---:|---:|
| 旧汽轮机 | 3×3 | 122.5 L/s | 24000/s | 122.5 L/s | 1200 L |
| 大型汽轮机 | 4×4 | 360 L/s | 72000/s | 360 L/s | 3600 L |

- 温水满 → 停机；排掉后恢复。
- 一台大型机配一个 4×4 分区；9 台吃满 3240 L/s。
- 峰值 4050 L/s 需要约 11.25 台，实际要 12 台才能全吃。

### 12.2 冷却塔

| 方块 | 尺寸 | 温水→水 | 液容 | 配方 |
|---|---:|---:|---:|---|
| 小型 | 2×2 | 120 L/s | 300 L | 12 L / 6 tick |
| 大型 | 4×4 | 600 L/s | 3600 L | 60 L / 6 tick |

- 都是1:1；3240 L/s 回水需要 **6 台大型塔**（6×600=3600）。
- 小塔需要27台，不推荐。

## 13. 配置码/UI

| 配置值 | 作用 |
|---|---|
| `-10/-11/-12` | 半自动/全自动/强制手动 |
| `-20..-28` | 选择1..9号堆 |
| `-30` | 总控（9区一起调） |
| `-1` | SCRAM |
| `-2` | 武装注硼 |
| `-3` | 手动静音通知（仅手动） |
| `1000+v` | 高水位线 v% |
| `2000+v` | 低水位线 v% |
| `3000+v` | 冷却节流 v%（仅手动） |
| `0..100` | 手动目标棒位 / 半自动公共目标 / 全自动目标功率 |

- 顶部横向滚动：`总控` + `1号堆`…`9号堆`。
- 自动/半自动下选择器只读，节流滑条置灰；节流不参与物理。

## 14. 做平衡时最容易踩的坑

1. **改 360 L/s 不会改热平衡。** 只要水够、`zoneFlow≈1`，热平衡由 `0.14`、`0.06`、`0.09` 决定；360 只改变蒸汽/水量和涡轮机数量。
2. **没有温度→蒸发加速。** 想要这个效果，必须显式在 `nominal_i` 里乘温度因子；当前没有。
3. **熔毁是 60 秒，不是 1 秒。** 旧注释/文档会误导。
4. **1.25 通量 + 0.96 最低流量区已经到 1.243**，离熔毁线只有 0.007；手动满通量非常敏感。
5. **分区不是均分水**：按 `nominal × zoneBias × throttle` 加权；`zoneBias` 固定表见 6.1。
6. **自动/半自动忽略单区节流**；单区节流只在强制手动生效。
7. **任何一区到 1.25 都累计整堆熔毁计时**，不是平均温度。
8. **给水泵不会因汽包满停机**；停的是转化/冷却，不是给水泵。
9. **告警阈值用当前方块真实容量**，不是旧 5×5 的 2000/10000。

---

### 快速调参入口（代码）

| 目标 | 变量 |
|---|---|
| 总产汽/水 | `zonedWaterPerZone`（当前360） |
| 单区热平衡 | `heatGen` 的 `0.14`、`cooling` 的 `0.06/0.09/0.01` |
| 分区水量偏差 | `zoneBias` 表（6.1） |
| 手动节流范围 | `zoneThrottle`（0..1） |
| 熔毁时间 | `meltTimer >= 60f` |
| 熔毁阈值 | `meltTemp=1.25` |
| 手动峰值 | `fluxMax=1.25` |
| 压力爆炸 | `pressureFuse=2` |
| 汽包/水罐 | `zonedSteamCapacity/zonedWaterCapacity` |
| 给水泵 | `zonedFeedwaterRate` |
