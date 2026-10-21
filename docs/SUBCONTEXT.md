# 本地模型上下文包（给 local_coder / local-code 用）

本文件是给**本地小模型**看的精简约定，目标是让它不用猜 API 就能改对代码。
规则：短、准、可执行。主 agent 只在需要委派时把本文件路径告诉子代理。

## 仓库
- 根目录：`/sdcard/dsh/mindustry-mod`
- 语言：Java 16 语法，运行在 Android（Mindustry 160）
- 源码：`src/ironworks/*.java`，子包 `src/ironworks/multicraft/`
- 文案：`assets/bundles/bundle_zh_CN.properties`、`bundle.properties`
- 贴图：`assets/sprites/*.png`（平铺，文件名 = 内容名去掉 `ironworks-` 前缀）
- 构建：`bash build.sh` → `out/ironworks.jar`（约 20 秒）
- 回归：`bash tools/chain-test.sh`（无头服务器，T1–T75，约 9 分钟，一次只能开一个实例）

## 内容命名
- 代码里 `new Item("aluminum")`、`new MultiCrafter("digester")`，游戏内自动变成
  `ironworks-aluminum`、`ironworks-digester`。
- 本地化键必须带前缀：`item.ironworks-xxx.name`、`block.ironworks-xxx.name`、
  `recipe.ironworks.<recipeName>`。
- 贴图文件名**不带**前缀：`assets/sprites/aluminum.png`。

## MultiCrafter 用法（自研库，不是原版）
```java
import ironworks.multicraft.MultiCrafter;
import ironworks.multicraft.Recipe;
import mindustry.type.LiquidStack;
import static mindustry.type.ItemStack.with;

block = new MultiCrafter("my-machine"){{
    requirements(Category.crafting, with(Items.copper, 60, Items.lead, 40));
    size = 3;                 // 3x3 贴图 96x96，2x2 贴图 64x64
    itemCapacity = 30;
    liquidCapacity = 50f;
    recipes = new Recipe[]{
        new Recipe("recipe-key", 60f)      // 60 帧工时
            .items(with(Items.sand, 4, Items.coal, 2))
            .liquids(new LiquidStack(Liquids.water, 6f))
            .power(4f)                      // 耗电
            .output(with(Items.silicon, 2))
            .outputLiquid(new LiquidStack(Liquids.hydrogen, 3f))
    };
}};
```
- 一个配方可以**同时**出物品和液体。
- 多液体产出必须指定方向：`liquidOutputDirections = new int[]{0, 2};`（0=右 2=左）。
- 液体输入白名单自动取当前配方的 `liquidInputs`，不用手写。
- **配方索引只追加**：已有存档靠索引，别插入或删除中间配方。

## 必须遵守的坑（踩过）
1. 有液体的方块一律用 `MultiCrafter`，不要用 `GenericCrafter`。
2. 自定义方块必须是 `public static` 字段，并在 `IronworksMod.loadContent()` 里 `load()`。
3. 需要 `import mindustry.gen.Building;` 时注意别和 `mindustry.world.Building` 混。
4. 冷热循环液必须 `blockReactive = false`，否则原版冷热反应会销毁液体。
5. 矿物贴图要 `名称1.png / 名称2.png / 名称3.png` 三张变体（OreBlock）。
6. 测试里 3x3 方块以点击格为中心覆盖 ±1，`power-source` 放在 `x-2` 处才贴得住。
7. 不要改动 `mod.hjson` 的版本号（主 agent 发布时统一改）。

## 交付格式（重要）
- 只改任务指定的文件；不要顺手重构别的文件。
- 改完**不要**自己构建/部署（主 agent 统一验证）。
- 最后用 5 行以内说明：改了哪些文件、每处改动一句话、有没有风险点。
