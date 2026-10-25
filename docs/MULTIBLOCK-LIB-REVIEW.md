# MultiBlockLib 对比评估（2026-10-25）

> 评估对象：[LaoHuaJiOfficial/MultiBlockLib](https://github.com/LaoHuaJiOfficial/MultiBlockLib)
> 本地对照：`IronworksCompute`（4×6 主集群 / 2×3 高级模块）
> 这不是法律意见；许可证结论按仓库公开文件判断。

## 1. 结论

**不能内嵌。** 该仓库没有 `LICENSE`，GitHub API 返回 `license: null`，README 也没有 MIT/其他许可声明。默认版权是“保留所有权利”，不是 MIT。

**对我们的固定 4×6 / 2×3 算力集群，我们的实现更适合当前需求；MultiBlockLib 更适合“给 JSON 模组提供任意矩形拼合”的通用场景。**

本轮只借鉴了它一个我们确实没有的点：**施工期间占位链接区**。占位块是独立实现，没有复制它的代码。

## 2. 它是什么

- 目标游戏版本：`minGameVersion: 146`，构建 `mindustryVersion = v146`。
- 版本：1.3。
- 核心：`linkValues` 把任意形状拆成若干矩形；一个中心块 + 若干隐藏链接块。
- 提供 JSON 类型：`MultiBlock-GenericCrafter`、`MultiBlock-AttributeCrafter`、`MultiBlock-LiquidRouter`。
- 提供旋转/镜像、`DrawRegionRotated`、`DrawFlameRotated` 等绘制适配。
- 用 `PlaceholderBlock` 在施工时占位；用 `LinkBlock` 代理物品/液体/伤害。

## 3. 对比

| 维度 | MultiBlockLib | 我们的 `IronworksCompute` |
| --- | --- | --- |
| 形状 | 任意矩形拼合，最多 4 个链接矩形 | 固定 4×6 / 2×3 |
| 旋转/镜像 | 支持 `rotations / canMirror` | 不旋转、不镜像（算力集群本来固定朝向） |
| 内容类型 | JSON `GenericCrafter` / `AttributeCrafter` / `LiquidRouter` | 自定义 Java `ComputeCluster` / `ComputeModule`，带超速、功率、冷却、贴合方向 |
| 施工占位 | `PlaceholderBlock` | 2.5.1.1 新增独立 `LinkPlaceholder` |
| 存档 | 链接块关系主要靠 `updateTile` 重建，没有显式链接坐标存档 | `relX/relY` 显式写入存档，版本 1，支持蓝图平移重绑 |
| 链接转发 | `linkProximityMap` 代理物品/液体 dump | 链接块液体转发、伤害转主块；算力集群不需要物品输出 |
| 放置校验 | `Build.validPlace` + 链接块 | `fitsExtra` 检查整个链接矩形，含施工脚手架冲突 |
| 测试 | 仓库内没有回归测试 | T95–T100/T108/T112/T114/T115/T186，共 18 条相关断言 |
| 维护状态 | 最后 push 2025-03，target v146 | 当前工程 target v160.1/160.3，2.5.2.0 |
| 许可证 | **无 LICENSE**；代码内还有 `new-horizon-inner-entity` 痕迹 | 本仓库也还没有 LICENSE，但不能因此把无许可代码并进来 |

## 4. 它比我们强的地方

1. **任意矩形拼合**：如果以后要做 L 形、T 形、6×8 等非固定形状，它的 `linkValues` 模型更通用。
2. **旋转/镜像**：对需要四向旋转的 JSON 方块更完整。
3. **通用 JSON 注册**：通过 `ClassMap` 把新类型暴露给 JSON 模组，适合做公共库。
4. **施工占位**：这个我们原来确实缺，已在本轮补上。
5. **物品/液体链接代理**：`linkProximityMap` 对“从链接块那一侧输出”更通用；我们的算力集群不需要物品输出，所以没有照搬。

## 5. 我们比它强的地方

1. **存档可靠性**：链接块相对坐标显式保存，读档/蓝图平移后能重绑；它的链接关系主要靠运行时重建。
2. **测试覆盖**：我们有 T95–T100/T108/T112/T114/T115/T186；它没有测试目录。
3. **与自身内容耦合**：算力模块的贴合方向、加成上限、功率统计、冷却回路、热液排放都在一个体系里；套通用库反而要再写一层适配。
4. **放置冲突处理**：我们检查整个 2×2 链接块占地，并拒绝顶掉别人的施工脚手架；它更像“通用库”，不负责这些内容级规则。
5. **版本匹配**：我们直接对 v160.1/v160.3 编译和回归；它 target v146，内部还用了 `ClassMap` 等引擎内部入口，升到 v160 有兼容风险。

## 6. 本轮实际做的事

- 新增 `LinkPlaceholder`（2×2 / 1×1 隐藏占位块）。
- `ComputeCluster.placeBegan` / `ComputeModule.placeBegan` 在施工开始时占住链接区。
- `ensureLinks` 只替换“同一台主块的占位块”，不再顶掉其他建筑的施工脚手架。
- `fitsExtra` 遇到别人的 `ConstructBuild` 直接判定不可放。
- 新增 T186：先放占位块，再换正式链接块，`placeholder=true links=true`。

## 7. 为什么没有直接内嵌

- 不是 MIT；没有 LICENSE；默认保留所有权利。
- 代码里出现 `new-horizon-inner-entity`，说明它自己也可能来自 NewHorizon 的链接块实现；来源链不干净。
- 目标版本 v146，和我们的 v160 不是同一代 API。
- 直接内嵌会把“来源不明 + 版本不匹配 + 通用库适配层”一起带进来，收益不值得。

## 8. 建议

- 当前 4×6 / 2×3 继续用我们的实现。
- 如果以后要做“任意形状公共库”，可以按它的**思路**独立设计（矩形拆解、占位、旋转/镜像），不要复制源码。
- 若确实想用它的代码，先让作者给出明确 LICENSE 或书面授权，再谈内嵌。
