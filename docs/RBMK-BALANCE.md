# RBMK-9 v3.1 平衡数据、公式与实现边界

> 适用版本：**3.1.0.0**。旧 5×5 的参数保留在本文末尾，RBMK-9 的分区物理以本文为准。
> 代码：`src/ironworks/IronworksRBMK.java`、`IronworksRbmkWindow.java`、`IronworksRbmkAlerts.java`。
> 时间单位：`dt = delta()/60` 秒；正常 60 tps。测试里 `b.updateTile()` 一次约等于 1 tick。

## 0. 这版新增/修了什么

- **三节点热工**：燃料芯块 `zoneHeat` → 包壳 `zoneClad` → 冷却剂 `zoneCoolant`，不再把燃料和冷却剂当成一个混合温度。
- **燃料蓄热滞后**：停堆后燃料仍比包壳/冷却剂热，包壳再慢慢把热交给水；SCRAM 不会让温度瞬间归零。
- **蒸汽干度与湿蒸汽**：低堆芯水位时干度下降，干度 <90% 的部分进入 `ironworks-wet-steam`。湿蒸汽可以进汽轮机，但不发电，并累积叶片水锤损伤。
- **堆芯水 / 水罐水拆分**：公共水罐不再自动倒灌堆芯。首次灌水后必须用堆芯注水泵（开关 + 注水率）补水；注水也受泵惰转影响。
- **主泵惰转**：失去水源后泵速按 `exp(-t/45s)` 衰减，不会瞬停；重新有水源才重新升速。
- **一回路压力与水锤**：低堆芯水位/高热/高汽量会升压；冷水注入高温低水位堆芯会产生水锤和压力损伤，压力损伤到 1 会炸堆。
- **控制棒高温卡死**：燃料温度超过卡死阈值并持续 2 秒后 `rodJammed=true`，SCRAM 也无法再插棒；只能先注水降温。
- **执行器行程**：正常棒 20 秒、SCRAM 3 秒、自动保护 1.5 秒；节流阀 8 秒；水位设定值 10 秒。不再瞬动。
- **自动功率不再有 100%/125% 硬上限**：目标功率可设到 999%，实际功率由传热、堆芯水、汽包和空泡自己限制。
- **UI**：总览新增包壳温度、堆芯水量、干度、湿蒸汽、泵速、回路压力、水锤、卡棒；控制页新增堆芯注水开关和注水率滑条；自动目标滑条可超过 100%。

## 1. 容量与额定值

| 项目 | 数值 |
|---|---:|
| 分区 | 3×3 个 4×4 |
| 单区水容量 | 3600 L |
| 单区汽容量 | 720 L |
| 总堆芯水容量 | 32400 L |
| 公共水罐 | 32400 L |
| 公共汽包 | 6480 L |
| 公共湿蒸汽包 | 3240 L（汽包一半） |
| 给水泵 | 4700 L/s |
| 堆芯注水泵 | 额定给水的 1.5×，即 4860 L/s（满注水率） |
| 额定总产汽 | 3240 L/s（9×360） |
| 燃料 | 燃料棒 900 秒/根，钍 20 秒/个 |

`zoneWater` 是堆芯水；`liquids.get(water)` 是公共水罐。两者独立记账。首次 `syncZoneWater()` 会把公共水按容量搬进堆芯并从罐里扣除，避免复制水；之后 `corePrimed=true`，罐里的水不会自己再流进堆芯。

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
target_i = max(0, target_i)
zoneFlux_i = lerpDelta(zoneFlux_i, target_i, 0.008)
```

- 0～20% 插入段有石墨棒尖，`rodEffect` 先升后降；全插仍然归零。
- 通量没有模式上限；自动目标只改变控制器的目标功率，不直接截断 `zoneFlux`。
- 毒物、多普勒、空泡和棒位共同决定目标，最终由热工和水/汽转化限制稳态。

## 3. 三节点热工

```text
waterFrac_i = zoneWater_i / zoneWaterCap_i
heatGen_i = zoneFlux_i*0.14 + zonePrompt_i + decayHeat/9

fuelToClad_i    = 0.9 * max(0, zoneHeat_i - zoneClad_i)
cladToCoolant_i = 3.0 * waterFrac_i * max(0, zoneClad_i - zoneCoolant_i)
coolingLoss_i   = zoneFlow_i * waterFrac_i
                  * (0.06 + 0.09*zoneCoolant_i) + 0.01

zoneHeat_i    += (heatGen_i - fuelToClad_i) / 6.0 * dt
zoneClad_i    += (fuelToClad_i - cladToCoolant_i) / 1.5 * dt
zoneCoolant_i += (cladToCoolant_i - coolingLoss_i) * dt

zoneHeat_i    = clamp(0, 3.0)
zoneClad_i    = clamp(0, 2.5)
zoneCoolant_i = clamp(0, 1.5)
zonePrompt_i -= 0.22*dt
```

- 燃料热容 6.0、包壳 1.5，所以停堆后燃料温度会拖尾；包壳温度再慢半拍。
- 水少 → `waterFrac` 小 → 包壳→水传热差 → 包壳和燃料一起升温；这是失水事故曲线。
- UI：`tempK = 280 + fuel*800`，包壳/冷却剂只显示归一化百分比；冷却剂温度还控制蒸发加速。
- 旧 5×5 仍用原来的单节点 `heat` 公式，不套三节点，避免破坏旧存档和既有平衡。

## 4. 蒸汽干度与湿蒸汽

```text
zoneDryness_i = clamp(0.45 + 0.55*waterFrac_i, 0.35, 1)
dry_i = converted_i * zoneDryness_i
wet_i = converted_i - dry_i

公共汽包加 dry_i；公共湿蒸汽包加 wet_i
若湿蒸汽总量超过湿蒸汽包剩余空间，按比例缩放整批转化量
```

- 干度 <0.90 时开始产生湿蒸汽；干度低到 0.45（水位很低）时一半以上是湿汽。
- 湿蒸汽和干蒸汽都从非进水侧排出，可以进现有汽轮机。
- 汽轮机吃湿蒸汽时：
  - 不产生电（`used` 只统计干蒸汽）；
  - `bladeDamage += wetUsed/(warmRoom+1)*dt*0.8`，最高 1，功率最多损失 90%；
  - `waterHammer += wetUsed/need*dt*0.25`。
- 湿蒸汽冷凝成温水，仍遵守汽轮机温水排出口限制。

## 5. 堆芯水、注水泵与主泵惰转

```text
corePrimed = false 初始
syncZoneWater():
  if corePrimed: return
  corePrimed = true
  把公共水罐现有水按 zoneWaterCap 分进 zoneWater，并从罐里扣掉

injectCoreWater(dt):
  if !coreInjectEnabled: return
  budget = min(水罐水, 4860 * coreInjectRate * pumpSpeed * dt)
  按各分区剩余容量分配；lastCoreInjection 记录本 tick 实际注入量
```

- 堆芯水只通过注水泵补充；水罐水不会自动倒灌。
- 注水开关默认开，注水率 0～100%（配置 `4000+v`）。
- 给水泵从相邻水源抽水，泵速：
  - 有水源且低于低线：`α = 1-exp(-dt/2)` 升速；
  - 失去水源/高于高线：`α = 1-exp(-dt/45)` 惰转下降。
- 没有相邻水源时不会假装升速；这是 v3.1 修掉的“泵永远满速”问题。

## 6. 水→汽与真实冷却流量

```text
nominal_i = max(zoneFlux_i,0) * 360 * evapFactor_i * dt
roomSteam_i = zoneSteamCap_i - zoneSteam_i
converted_i = min(nominal_i * zoneThrottleActual_i,
                  zoneWater_i,
                  roomSteam_i / max(zoneDryness_i, 0.01))
converted_i = max(0, converted_i)

zoneWater_i -= converted_i
zoneSteam_i += converted_i * zoneDryness_i
公共湿蒸汽 += converted_i * (1-zoneDryness_i)
```

- 转化只受**堆芯水**、单区汽空间、湿蒸汽包和阀位限制；公共水罐满不会再把堆芯转化卡死（v3.1 修）。
- `zoneFlow` 是实际转化/理论需求，不再用“阀位”冒充流量：

```text
flowTarget_i =
  0                                   if 堆芯无水
  converted_i / nominal_i             if nominal_i > 0
  throttleActual_i                    if 无热需求且汽未满
zoneFlow_i = lerpDelta(zoneFlow_i, flowTarget_i, 0.15)
```

- 汽包满/湿蒸汽包满时流量下降；冷却水少时流量也下降。
- `evapFactor = clamp(1 + 0.6*max(0, coolant-0.5), 1, 1.6)`。

## 7. 执行器行程

| 执行器 | 全行程 |
|---|---:|
| 正常插/拔棒 | 20 s |
| SCRAM | 3 s |
| 自动保护压棒 | 1.5 s |
| 手动/自动节流阀 | 8 s |
| 手动水位设定值 | 10 s |

```text
rodStep(dt, travel) = clamp(dt / travel, 0, 1)
rods = approach(rods, target, rodStep(...))
```

- 强制手动模式不再“滑条一动棒就到”；T189 验证 2 秒只走约 9%，20 秒才到目标。
- 自动/半自动的保护压棒仍比正常插棒快，但不再是瞬动。

## 8. 一回路压力、水锤与卡棒

```text
avgCore = 平均堆芯水位
steamFrac = 公共汽包 / 汽包容量
pressureTarget = 0.45 + (1-avgCore)*0.80 + coolantHeat*0.25 + steamFrac*0.15
loopPressure = lerpDelta(loopPressure, pressureTarget, 0.03)

if lastCoreInjection > 1 && heat > 1.0 && avgCore < 0.45:
    shock = clamp((heat-0.8)*(0.45-avgCore)
                  *(lastCoreInjection/ratedFlow)*0.35, 0, 0.5)
    waterHammer += shock
    pressureDamage += shock*0.015*dt*60
    loopPressure += shock*0.35

waterHammer -= 0.02*dt
if loopPressure > 1.4:
    pressureDamage += (loopPressure-1.4)*0.20*dt
if pressureDamage >= 1: detonate()
```

- 冷水注入热而低水位的堆芯 → 水锤；小流量、慢注水更安全。
- 压力损伤是累积的，不会瞬间消失；UI 显示水锤和回路压力。
- `updateRodJam(dt, guard)`：`heat > guard` 持续 2 秒 → `rodJammed=true`。
  - RBMK-9 的保护阈值是 2.20；旧 5×5 是 `meltTemp-0.05`。
  - 卡死后所有模式都不能移动棒，包括 SCRAM；只能等温度降下来（当前实现不自动复位，需维护/读档）。
- 卡棒提示走 `IronworksRbmkAlerts`。

## 9. 自动/半自动/手动

自动（`MODE_AUTO`）：

```text
steamFactor = clamp(1 - (steamFrac-0.5)/0.4, 0, 1)
waterFactor = clamp((avgCore-0.1)/0.2, 0, 1)   // 看堆芯水位，不看公共水罐
heatFactor  = clamp((2.05 - heat)/0.15, 0, 1)   // 接近 200% 才收功率
effective   = autoPower * min(steamFactor, waterFactor, heatFactor)
feedRods    = autoRodsFor(effective, poison, doppler, voidFactor)
error       = effective - flux
autoIntegral = clamp(autoIntegral + error*dt*0.05, -0.60, 0.60)
common      = clamp(feedRods - error*0.25 - autoIntegral, 0, 1)
common      = max(common, protectRods)
if scramLatch: common = 1
```

- `autoPowerMax = 9.99`，UI 目标滑条 0～999%；`autoRodsFor` 不再把目标比压到 1。
- 自动水位高线仍限制在 90%，给水不超过额定；堆芯注水独立控制。
- 半自动：`common = scramLatch ? 1 : max(targetRods, protectRods)`，保护解除后回到操作员目标。
- 强制手动：棒/阀按实际行程追随目标，无自动保护；可单区调棒和节流。
- 保护量现在用**堆芯水位**、燃料/包壳热趋势、冷却流量和蒸汽压力；不会因为公共水罐空就永久满棒。

## 10. 配置码（`applyConfig`）

| 值 | 含义 |
|---|---|
| `-1` | SCRAM（锁存，插棒） |
| `-2` | 注硼武装 |
| `-3` | 手动模式静音通知 |
| `-4` | 复位 SCRAM 锁存 |
| `-5` | 切换堆芯注水开关 |
| `-10/-11/-12` | 半自动/全自动/强制手动 |
| `-20..-28` | 选择 0..8 号分区 |
| `-30` | 总控 |
| `0..999` | 自动目标功率 0..999%，或非自动的棒位 0..100% |
| `1000+v` | 手动最高水位线 v% |
| `2000+v` | 手动最低水位线 v% |
| `3000+v` | 手动节流阀 v% |
| `4000+v` | 堆芯注水率 v% |

## 11. 存档

分区堆存档 revision **15**：

- revision 9：分区基础量；10：节流目标；11：冷却剂/熔毁；12：分区水/汽；13：实际阀位；14：包壳温度、干度、泵速、压力、水锤、注水开关/速率、卡棒；15：`corePrimed`。
- 旧 revision 读入时给包壳/干度/泵速等合理默认值；没有 `corePrimed` 的旧档会在首次 `syncZoneWater()` 时补一次。
- 旧 5×5 revision 8 不变。

## 12. 测试与已知边界

测试：`tools/chain-test.sh` 的 T139–T197，覆盖分区、自动高功率、手动、棒尖、SCRAM、闪蒸、节流、衰变热、熔毁、泵惰转、湿蒸汽、水锤、卡棒、堆芯注水、汽轮机湿蒸汽损伤。期望见 `tools/test-expect.txt`。

仍未实现/保留：

- 5×5 旧堆仍是单节点热工，不套三节点。
- 没有独立安全阀/泄压阀；压力只能靠降功率、注水、排汽间接控制。
- 没有逐区氙毒和硼混合延迟；氙/硼仍是整堆标量。
- 卡棒不会自动复位；当前需要降温后读档/维护（后续可加维护按钮）。
