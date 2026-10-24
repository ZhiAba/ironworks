# RBMK-9 v2.5 平衡数据、公式与实现边界

> 适用版本：**2.5.0.0**。旧 2.4 版数据保留在 `docs/RBMK-BALANCE-2.4.md`。
> 代码：`src/ironworks/IronworksRBMK.java`、`IronworksRbmkWindow.java`、`IronworksRbmkAlerts.java`。
> 时间单位：`dt = delta()/60` 秒；`meltTimer` 这类用 `delta()` 的地方是 **tick**。正常 60 tps。

## 0. 这版到底改了什么

已实现：

- **双节点热工**：燃料温度 `zoneHeat` + 冷却剂温度 `zoneCoolant`，不再只有一个混合温度。
- **多普勒负反馈**：燃料温度越高，目标通量越低。
- **控制棒棒尖**：0～20% 插入段先引入正反应性，之后再转负；SCRAM 仍然能把棒价值压到 0。
- **温度加速蒸发**：冷却剂超过 50% 后，同通量下产汽更快，最高 1.6×。
- **逐区熔毁损伤**：燃料 ≥200% 后累积损伤，任意一区到 1 就整堆爆炸；不再是全局 125%/1秒。
- **衰变热**：停堆后仍有约 7% 的功率历史热，不能立刻拆泵。
- **分区水汽账面**：9 区各有水/汽账面，独立转化，不再按总转化量权重分配。
- **弹出式控制台**：黑色半透明浮层 + 总览/控制/分区/维护四页签，替换原来方块 config 里的长条。
- **自动/半自动更新**：按燃料温度、多普勒、棒尖曲线反算棒位；SCRAM 增加锁存与复位。

未实现（保留为后续，不是隐藏开关）：

- 分区水容量/汽容量百分比滑条（当前固定单区 3600 L / 720 L）。
- 汽轮机直接调用 `extractSteam()`；目前仍读公共汽包，以保持原版导管/汽轮机兼容。
- 燃耗对反应性的长期影响、压力安全阀、主泵惰转、蒸汽干度、逐区氙、卡棒、硼混合延迟。

## 1. 数据与容量

| 项目 | 数值 |
|---|---:|
| 分区 | 3×3 个 4×4 |
| 单区水账面 | 3600 L |
| 单区汽账面 | 720 L |
| 总水罐 | 32400 L |
| 总汽包 | 6480 L |
| 给水泵 | 4700 L/s |
| 额定总产汽 | 3240 L/s（9×360） |
| 手动峰值 | 4050 L/s（9×450） |
| 燃料 | 燃料棒 900 秒/根，钍 20 秒/个 |

公共水罐/汽包仍是物理容器；`zoneWater/zoneSteam` 是分区账面和独立转化边界。公共总量变化时（补水、汽轮机抽汽、测试直接 set），下一次 reactor tick 会按容量×`zoneBias` 重新对齐，保证账面总和跟公共罐一致。

## 2. 通量

```text
rodEffect(r):
  worth = r < 0.2 ? r - 0.35*sin(pi*r/0.2) : r
  return clamp(1 - worth, 0, 1.25)

doppler(fuel) = clamp(1 - 0.30*max(0, fuel-0.8), 0.05, 1)
poison = clamp(1 - 0.65*xenon - 1.2*boron, 0, 1)

target_i = 1.25 * rodEffect(zoneRods_i) * poison * doppler(zoneHeat_i)
if(poisoned) target_i = 0
target_i *= 1 + (1 - zoneFlow_i)*1.2
target_i = clamp(target_i, 0, manual ? 1.25 : 1)
zoneFlux_i = lerpDelta(zoneFlux_i, target_i, 0.008)
```

- `rodEffect` 在 r=0 为 1；r=0.1 约 1.25；r=0.2 为 0.8；r=1 为 0。因此 0～20% 段会先升功率，但全插仍然归零。
- 这个 `rodTipStrength=0.35` 是为了让 `(1-rod)` 上限下仍然能看见正反应性；建议文档里的 +0.15 在旧式乘法里不会产生净上升。
- 毒物是加法括号，再乘到通量；控制棒/多普勒/空泡是乘法。
- 自动/半自动上限 100%；强制手动 125%。

## 3. 双节点热工

```text
waterFrac_i = zoneWater_i / zoneWaterCap_i
heatGen_i = zoneFlux_i*0.14 + zonePrompt_i + decayHeat/9

fuelToCoolant_i = 2.0 * waterFrac_i * max(0, zoneHeat_i - zoneCoolant_i)
coolingLoss_i = zoneFlow_i * waterFrac_i
                 * (0.06 + 0.09*zoneCoolant_i) + 0.01

zoneHeat_i    += (heatGen_i - fuelToCoolant_i)*dt
zoneCoolant_i += (fuelToCoolant_i - coolingLoss_i)*dt

zoneHeat_i    = clamp(0, 3.0)
zoneCoolant_i = clamp(0, 1.5)
zonePrompt_i -= 0.22*dt
```

- 燃料温度决定：多普勒、自动 `heatFactor`、熔毁损伤、保护热风险。
- 冷却剂温度决定：蒸发加速、冷却损失、水位提前量；保护时乘 0.85 作为辅助。
- 水少 → `waterFrac` 小 → 燃料→冷却剂传热差 → 燃料温度飙升；这是失水事故曲线。
- 显示：`tempK = 280 + fuel*800`，`coolantK = 280 + coolant*80`，UI 标 °C。

## 4. 温度加速蒸发

```text
evapFactor_i = clamp(1 + 0.6*max(0, coolant_i - 0.5), 1, 1.6)
nominal_i    = max(zoneFlux_i,0) * 360 * evapFactor_i * dt
```

- 冷却剂 0.5 以下不加速；0.5→1.0 线性到 1.3；1.5 封顶 1.6。
- 手动节流仍只影响手动模式：`throttle=1`（自动/半自动）或 `zoneThrottle[i]`。
- 之前“没有温度→蒸发加速”的结论只适用于 2.4；v2.5 已显式加入。

## 5. 分区水→汽

```text
roomSteam_i = max(zoneSteamCap_i - zoneSteam_i, 0)
converted_i = clamp(min(nominal_i*throttle_i,
                        zoneWater_i,
                        roomSteam_i), 0, inf)

zoneWater_i -= converted_i
zoneSteam_i += converted_i
```

- 每区独立，不再用 `convertedTotal * 权重` 跨区分配。
- 公共水罐/汽包和总液容仍做最后一道全局比例缩放，保证不会凭空多水/多汽。
- `zoneFlow` 不再用“转化量/需求”，改成：

```text
steamFull_i = zoneSteamCap_i<=0 || zoneSteam_i>=cap-0.001 || 公共汽包满
flowTarget_i = (steamFull_i || throttle_i<0.01) ? 0 : throttle_i
zoneFlow_i = lerpDelta(zoneFlow_i, flowTarget_i, 0.08)
```

- 所以停堆、水满、汽不满时 `zoneFlow` 仍可保持 1，正常冷却；汽满/节流关死才归零。
- 没有跨区蒸汽转移；公共汽包只作为管路出口，汽轮机仍吃公共汽包。

## 6. 衰变热

```text
fluxHistory = lerpDelta(fluxHistory, flux, 0.01)
if(flux < 0.01) decayTimer += dt
else if(flux > 0.05) decayTimer = 0
decayHeat = 0.07 * fluxHistory * exp(-decayTimer/300)
```

- `decayHeat` 是总额定通量比例，平均分到 9 区热源（每区 `+decayHeat/9`）。
- 停堆后不能立刻拆泵；UI 维护页显示衰变热，停堆时给出提示。

## 7. 熔毁损伤

```text
if(fuel_i >= 2.0):
    zoneMeltDamage_i += (fuel_i - 2.0 + 0.10) * 0.04 * dt
else:
    zoneMeltDamage_i -= 0.02 * dt

zoneMeltDamage_i = clamp(0, 1)
if(any zoneMeltDamage_i >= 1) detonate()
```

- 阈值是 **燃料温度 200%**，不是旧全局 125%。
- 2.1 约 125 秒、2.5 约 42 秒、3.0 约 23 秒（连续保持时）。
- 低于 2.0 后损伤按 0.02/s 恢复；不是瞬时清零。
- 任意一区到 1 → 整堆爆炸，爆炸/辐射参数沿用旧版（26 格、12000、900 tick、落尘半径 26×8×1.6）。

## 8. SCRAM 与锁存

- `applyConfig(-1)` 设 `scramLatch=true`，自动/半自动的公共目标强制 1；不会下一 tick 被目标功率拔棒。
- `applyConfig(-4)` 复位锁存。UI 应急页有“复位 SCRAM 锁存”。
- 手动 SCRAM 仍把 9 区目标设 1，实际棒位瞬动（手动模式）。
- 高功率 SCRAM 仍会先闪蒸、加 prompt 热冲击，再由燃料温度积分决定是否熔毁。
- 压力 AZ-5：仍只手动判定；水≥98% + 汽包满 + 9 区棒全部到底，持续 2 秒爆炸。

## 9. 自动模式

每 tick 先算保护量，再算全自动目标：

```text
steamFactor = clamp(1 - (steamFrac-0.5)/0.4, 0, 1)
waterFactor = clamp((waterFrac-0.1)/0.2, 0, 1)
heatFactor  = clamp((2.0 - fuelHeat)/0.5, 0, 1)   // 1.5以下=1，2.0=0
effective   = autoPower * min(steamFactor, waterFactor, heatFactor)

poison      = clamp(1 - 0.65*xenon - 1.2*boron, 0.05, 1)
voidFactor  = 1 + (1-flow)*1.2
doppler     = clamp(1 - 0.30*max(0,fuelHeat-0.8), 0.05, 1)

common = autoRodsFor(effective, poison, doppler, voidFactor)
common = max(common, protectRods)
if(scramLatch) common = 1
```

`autoRodsFor` 对 `rodEffect(r)` 做数值反解：

- 目标落在正常段（r≥0.2）用二分；
- 目标高到需要进入 0～0.2 棒尖段时，从 0.2 向 0 扫描，取较大的解，避免自动钻到正反应性峰上。

水位：

```text
autoHigh = clamp(0.55 + 0.35*min(flux,1), 0.55, 0.90)
if(coolantHeat>0.9 && fuelHeat<1.2) autoHigh = min(autoHigh+0.05, 0.95)
autoLow = clamp(autoHigh-0.20, 0.25, autoHigh-0.05)
```

- 冷却剂热、燃料不热 = 传热差/水位低，优先加泵，不先插棒。
- 棒执行器仍为 `1/90` 插入（约1.5秒）、`1/2000` 拔出（约33.3秒）。

## 10. 半自动

```text
common = scramLatch ? 1 : max(targetRods, protectRods)
zoneTarget[i] = common
zoneRods[i] = approachDelta(zoneRods[i], common,
    common > zoneRods[i] ? 1/90 : 1/2000)
```

- `targetRods` 是操作员插入比例；0=全提，1=全插。
- 保护量只是下限；风险解除后回到操作员目标，不会被棘轮抬高。
- 9 区共用一套目标；没有单区自动配平。

## 11. 保护量

```text
low = max(waterLowLevel, 0.10)
trigger = max(0.12, low*0.75)
below = clamp((trigger-waterFrac)/max(trigger*0.75,0.05), 0, 1)

demand = flux*3240
runway = demand>1 ? water/demand : 99
runwayRisk = clamp((9-runway)/6, 0, 1)

waterRisk = below
if(!feedwaterAvailable || below>0)
    waterRisk = max(waterRisk, runwayRisk)

futureFuel = fuelHeat + max(heatTrend,0)*2.5
thermal = max(futureFuel, coolantHeat*0.85)
heatRisk = clamp((thermal-1.15)/0.55, 0, 1)

flowRisk = (waterFrac<trigger || fuelHeat>1.4)
    ? clamp((0.9-flow)/0.4,0,1) : 0
steamRisk = clamp((steamFrac-0.8)/0.15,0,1)

risk = max(waterRisk, heatRisk, flowRisk*0.9, steamRisk*0.85)
if(waterFrac<=0.02) risk=max(risk,0.9)
if(fuelHeat>=1.8) risk=1

protectRods = lerpDelta(protectRods, risk, risk>protectRods?0.10:0.02)
if(fuelHeat>=1.8) protectRods=max(protectRods,0.98)
```

## 12. 弹出式 UI

- 点击方块直接弹出控制台，方块 config 只留标题、模式/通量/燃料温度摘要和入口按钮。
- 控制台挂在 `ui.hudGroup`，`Styles.black6` 全屏遮罩 + 同色半透明面板，和日志弹窗同一套视觉。
- 四页签：
  - **总览**：通量、燃料温度、冷却剂温度、流量、水/汽、衰变热、最大损伤、棒位、蒸发率。
  - **控制**：模式、目标滑条、分区选择、水位、节流、SCRAM/注硼/静音/复位锁存。
  - **分区**：3×3 卡片，每区通量/燃料/冷却剂/流量/水/汽/棒位/损伤。
  - **维护**：衰变热、多普勒、损伤、氙、燃耗、燃料、憋压、棒价值与物理提示。
- 所有数值 `Label.update()` 实时刷新；面板关闭/方块被拆/读档时自动隐藏。

## 13. 配置码与存档

| 值 | 作用 |
|---|---|
| `-10/-11/-12` | 半自动/全自动/强制手动 |
| `-20..-28` | 选择1..9号堆 |
| `-30` | 总控 |
| `-1` | SCRAM（并锁存） |
| `-2` | 武装注硼 |
| `-3` | 静音（仅手动） |
| `-4` | 复位 SCRAM 锁存 |
| `1000+v` / `2000+v` | 手动高/低水位线 |
| `3000+v` | 手动冷却节流 |
| `0..100` | 棒位/目标功率 |

- 分区堆存档版本 **12**：旧 v11 读档后按公共水/汽自动分摊到 9 区；温度/v11 字段兼容。
- 旧 5×5 版本仍为 **8**，物理逻辑保持 2.4 的全局单节点，不受 v2.5 双节点改动影响。

## 14. 平衡提示

1. 改 360 仍然主要改水汽/涡轮机数量；热平衡由 `0.14 / 2.0 / 0.06 / 0.09` 决定。
2. 手动 125% + 低流量区仍可能把燃料推过 2.0；但现在熔毁需要损伤积分，不是一到线就爆。
3. 多普勒会限制高温功率；自动的反算已包含它，半自动需要操作员自己补偿。
4. 0～20% 棒尖段是危险区；UI 滑条旁已给出提示。
5. 停堆后衰变热仍在；冷却泵和给水不能立刻全拆。
6. 汽包满 → 该区蒸汽空间为 0 → `zoneFlow` 归零 → 冷却只剩 0.01，但给水泵不会因汽包满而停。

## 15. 测试

新增：

- T180 分区水账面总和 32400、单区 3600。
- T181 冷却剂 1.0 时蒸发 1.3×（约 4212 L/s）。
- T182 棒尖：0.1 插入通量高于 0.2。
- T183 停堆衰变热 >0（约 7%）。
- T184 燃料 2.5/无水时逐区损伤累积。
- T185 自动 SCRAM 锁存，`-4` 复位。

全量回归结果见 `docs/STATE.md` / `docs/HANDOFF.md`。
