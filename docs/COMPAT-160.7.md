# Mindustry 160.7 兼容性审计

- 审计基准源码：`/storage/emulated/0/文件中转/工作站/Mindustry-160.7/Mindustry-160.7`
- 对比基准：`/root/api/Mindustry-src-160.1/Mindustry-160.1`
- 结论：**没有发现会阻止本 mod 在 160.7 运行的 API 破坏性变更**；可直接沿用 160.1 编译基准、160.3 无头运行基准。
- 版本号：160.7 的 `build.gradle` 中 `versionNumber = 8`（160.1 为 1），但内容版本/类签名仍兼容。

## 有差异但不影响本 mod 的核心点

| 160.7 变化 | 影响评估 |
| --- | --- |
| `BulletType` 的追踪目标优先取 `aimTile.build` | 本 mod 的自定义弹体没有依赖旧的目标回退顺序；蜂群/导弹仍由原版追踪逻辑控制。 |
| 新增 `Mods.loadModPatches()` | 本 mod 的 `IronworksMod.loadContent/init` 不依赖该钩子；不需要额外注册。 |
| `Block.addLiquidBar` 条件改为 `liquidCapacity > 0f` | 本 mod 的液体方块都有正容量；没有容量为 0 仍加液体条的方块。 |
| 其余核心差异集中在渲染/编辑器/地图生成等非炮塔路径 | 本 mod 主要扩展 Block/Turret/Building/Content，未触碰这些内部实现。 |

## 炮塔相关

`diff` 结果中 **`mindustry/world/blocks/defense/turrets/` 下的 Turret 及所有子类没有改动**。本 mod 的炮塔数值重写只依赖：

- `BaseTurret.range`
- `ReloadTurret.reload`
- `Turret.targetAir / targetGround`
- `ItemTurret.ammoTypes` / `LiquidTurret.ammoTypes`
- `PowerTurret.shootType`
- `BulletType` 的 damage/splash/status/pierce/reloadMultiplier/rangeChange 等公开字段

这些在 160.7 中签名未变。

## 编译/运行建议

1. 继续使用 `/root/api/Mindustry-160.1.jar` 编译（本版未使用 160.7 新 API）。
2. 运行基准继续用 `/root/api/server-release-160.3.jar`。
3. 若之后切到 160.7 的 release jar，优先复跑 T219、T222、T224、T226、T227、T228、T232；这些覆盖炮塔字段、激光、护盾/能量、建造时间和读档血量。
4. 160.7 源码目录没有构建好的 release jar，本版没有把编译基准切换到它。

## 本版结论

- 无需为 160.7 修改源码。
- 无需迁移内容 ID、存档版本或科技树。
- 若用户实际客户端升级到 160.7 后出现差异，优先检查 `BulletType` 追踪目标与液体条显示，而不是炮塔数值。
