# 单位动态模块库（Unit Module Library）

> 状态：3.10.0.0 设计稿 + 实现说明
> 目标：把“单位模块化”做成可复用库，而不是某个单位的固定功能。
> 参考：Eve / 超频没有团队《Modular Weapon Unit 2.0》只用于研究思路；不复制其代码、jar 或反编译源码。

## 1. 为什么重写

参考模组把 3 个武器槽写死在 `UnitType` 里，用反射改 `WeaponMount.weapon`，只支持一种自定义实体。它证明了“运行时换武器”可行，但不够：

- 槽位数量固定，不能随时加模块。
- 模块只能是武器，不能是装甲、护盾、推进器、传感器或纯装饰。
- 安装时没有坐标/贴图绑定，不能在外挂装甲上贴图。
- 没有模块图、来源栈、事务回滚和通用联机同步。

本库改为**模块图 + 行为组合 + 坐标/贴图绑定**。

## 2. 核心概念

| 概念 | 说明 |
|---|---|
| `ModuleSpec` | 模块规格：id、名称、行为列表、默认视觉、造价、标签。 |
| `ModuleBehavior` | 开放 SPI；武器、装甲、视觉、挂点、每帧逻辑都是行为。 |
| `ModuleNode` | 已安装实例：instanceId、moduleId、parentId、socketId、坐标、旋转、缩放、z、贴图覆盖、来源。 |
| `UnitModuleState` | 一棵模块图：root + nodes + sources + revision。 |
| `ModuleSocket` | 模块或单位根提供的挂点；可继续装子模块。 |
| `ModulePlacement` | 安装参数：父节点/挂点或显式坐标、贴图覆盖、tint。 |
| `ModuleVisual` | 贴图/绘制描述：region、局部坐标、旋转、缩放、z、tint、自定义绘制。 |
| `ModuleStatSet` | 聚合属性：护甲、生命/速度/伤害/装填倍率、自定义 key。 |
| `ModuleSource` | 来源：base / arsenal / attachment / affix；用于附加/卸载。 |

## 3. 行为 SPI

```java
public interface ModuleBehavior{
    default void onInstall(ModuleContext ctx){}
    default void onUninstall(ModuleContext ctx){}
    default void update(ModuleContext ctx){}
    default void contributeMounts(Seq<MountSpec> out, ModuleContext ctx){}
    default void contributeStats(ModuleStatSet out, ModuleContext ctx){}
    default void contributeVisuals(Seq<ModuleVisual> out, ModuleContext ctx){}
    default void contributeSockets(Seq<ModuleSocket> out, ModuleContext ctx){}
}
```

新增功能 = 新增一个行为实现并注册模块，不需要改 `Unit`、`UnitType` 或核心库。

已提供的行为：

- `WeaponBehavior`：贡献一个或多个 `MountSpec`，由 `MountManager` 映射到 `unit.mounts`。
- `StatBehavior`：贡献护甲/倍率/自定义属性。
- `VisualBehavior`：贡献贴图。
- `SocketBehavior`：贡献子挂点。

## 4. 安装/卸载/替换

```java
ModuleNode node = UnitModuleRuntime.install(unit, "ironworks-armor-plate", 6f, -2f);
ModuleNode gun  = UnitModuleRuntime.install(unit, "ironworks-machine-gun", 8f, 2f, "ironworks-gun");
UnitModuleRuntime.move(unit, node.instanceId, 7f, -3f, 15f);
UnitModuleRuntime.uninstall(unit, node.instanceId);
```

- 坐标为**单位本地坐标**；单位旋转后自动跟随。
- 第 5 个参数可覆盖贴图名；不传则用模块默认视觉。
- 安装后贴图节点立即进入绘制队列；武器挂载在下一 tick 的结构刷新里重建，避免在单位遍历中改数组。
- `uninstall` 删除节点及子树、武器挂载、贴图、来源引用和属性贡献。
- `replace` 保留节点 id/父级，热替换模块内容。

## 5. 动态武器挂载

`MountManager`：

- 保留单位原有 base mounts，模块武器追加在后面。
- 模块武器 key 为 `module:<instanceId>:<mountId>`；重建时按 key 复用旧 `WeaponMount`，拷贝 `reload/rotation/recoil/heat/warmup/aimX/aimY/target` 等状态。
- 新增/删除模块只重建数组，不反射 final 字段。
- 重建在 `Trigger.update` 的结构队列里执行；测试可调 `flush()`。
- 单位升级/重构触发 `UnitChangeEvent` 时重挂模块图。

## 6. 坐标与贴图

- `ModuleVisual` 支持 `regionName`、局部 `x/y/rotation/scale/z/tint`、`ModuleDrawer` 自定义绘制。
- 绘制在 `Trigger.draw` 中按单位层 + z 入队（`Draw.draw` + `Draw.sort`），因此能落在单位本体前/后。
- 缺失贴图只日志一次并跳过，不崩溃。
- 嵌套模块逐层合成父节点变换。
- 视觉层只做无头结构测试；实际观感必须实机确认。

## 7. 存档与联机

- 侧车 `UnitModuleRegistry` 按 `unit.id` 保存模块图，任意原版单位可用。
- 存档块 `ironworks-unit-modules`：写 unitId、revision、节点、父级/挂点、坐标、贴图、来源。
- 联机加入：chunk `writeNet=true` 随世界流发送。
- 实时变更：`UnitModuleSyncPacket`；客户端按 revision 应用，旧包丢弃；单位未到则 pending。
- 未知模块跳过该节点及子树；未知挂点拒绝子节点。

## 8. 后续接入

### 兵工厂重构

用 `ModuleEditSession` 做图级预演、撤销、净消耗、提交；`ArsenalModuleAdapter` 把 loadout 转成一次提交。

### 其他单位附加

`AttachmentModuleAdapter.attach(host, sourceUnit, moduleId, placement)` 记录来源；`detachSource` 删除该来源全部节点/贴图/武器/属性；来源死亡自动清理。

### T7 词条

`DifficultyModuleAdapter` 实现 `ModuleModifierSource`，按 `Difficulty.tier()`/词条 revision 触发 `refreshAll`；具体数值由后续内容决定，不在核心库写死。

## 9. 边界

- 不做玩家可见编辑器/兵工厂/图鉴。
- 不做完整动画编辑器；`ModuleDrawer` 留给内容层。
- 不支持运行中删除单位根；只删除模块节点。
- 属性写入与现有 T7/装甲系统可能冲突，由 adapter 协调；核心库只提供聚合值。
- 第三方参考模组无许可证：不复制、不提交。