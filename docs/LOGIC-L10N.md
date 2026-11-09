# 逻辑块编辑界面汉化说明（3.6.0.0）

> 本文对应用户要求中的“逻辑块编辑器汉化（显示层）”与“未覆盖清单”。
> 结论：**只翻译界面显示，不修改任何逻辑文本、变量名、关键字、选项值或存档内容。**

## 一、开关

- 设置项名称：`开启逻辑块汉化功能`
- 存储键：`ironworks.logic-localization`
- 默认值：**只有游戏语言为中文时默认开启**；其它语言默认关闭。
- 玩家手动关闭后写入设置并持久化，之后启动不会再次被默认值覆盖。
- 实现方式：同步原版 `logiclocalization` 显示开关；逻辑编辑器自身读取该开关，只替换标题/分类/枚举标签。

设置入口沿用模组设置页（`IronworksSettings`），与游戏语言无关，随时可以改。

## 二、已覆盖

Mindustry 160.1 的 `LStatements` 中共有 31 个 `bundle("...")` 指令标题键；`bundle_zh_CN.properties` 中对应的 `name.token.*` **31/31 全部存在**，由原版逻辑编辑器在显示层替换：

- 所有 `LStatements` 指令标题（如 `set`、`op`、`jump`、`sensor`、`control`、`print`、`draw`、`wait` 等）；
- 指令分类 `lcategory.*`；
- 枚举/运算符的 `label` 显示文本（由原版 `LStatement`/`LExecutor` 查 bundle）；
- 编辑器通用按钮（`@add`、`@back`、`@edit`、`@variables`、`@clear`、`@copy.clipboard`、`@load.clipboard` 等）使用原版已有中文本地化。

玩家变量名、内容引用（方块/单位/物品名）、逻辑里保存的字符串、数字、比较符和关键字**不参与替换**，仍按原样显示/存储。

## 三、未覆盖清单（硬编码，本次未改逻辑执行）

以下字符串在 160.1 原版源码里是硬编码或由 `PrintI.toString()` 生成，没有对应的 `name.token.*` 键。按“显示层汉化、不得改变解析语义”的边界，本次**保留原文**，不往逻辑存储里写中文：

| 位置 | 原文 | 说明 |
|---|---|---|
| `LStatements` 世界消息参数 | `send` | 选项关键字，参与解析，不能直接替换 |
| `LStatements` 世界消息参数 | `on` | 同上 |
| `LStatements` 世界消息参数 | `, reliable` | 同上，逗号/空格是格式的一部分 |
| `LStatements` 文本拼接示例 | ` = `、` # ` | 编辑器提示文本，非必填字段标签 |
| `LogicDialog.typeName()` | `number`、`null`、`string`、`content`、`building`、`team`、`unit`、`enum`、`unknown` | 变量类型提示，硬编码；只影响显示，不影响类型解析 |
| 逻辑导出/变量名输入 | 玩家输入内容 | **明确禁止**翻译或改写 |

如果后续要覆盖 `send`/`on`/`, reliable` 这类解析参与项，只能采用“保留原值 + 在控件旁追加中文注释”的方式，不能把它们替换成中文；本次未做，因为原版控件没有稳定的旁注挂点，强行改会违反“不得改变逻辑执行/存储”的验收条件。

## 四、验收方法

1. 中文环境下首次进游戏：设置页“开启逻辑块汉化功能”应为开；逻辑编辑器中的指令标题/分类为中文。
2. 关闭开关：指令标题恢复英文；重进游戏后仍保持关闭（不会被默认值重置）。
3. 打开旧逻辑：不出现解析错误；变量名、内容引用和保存的字符串保持原样。
4. 复制 → 清空 → 导入：逻辑文本逐字节一致，执行结果一致。
5. 保存/读取存档：不因汉化开关产生额外字段或报错。
6. 多人/跨客户端：开关只是本地显示设置；服务端逻辑文本不变，客户端不会因为中文显示而无法解析。
7. 中文变长文本：原版编辑器允许换行/滚动；本次没有改布局。若某个硬编码控件出现截断，按上表补“未覆盖”，不通过改逻辑文本规避。

## 五、代码位置

- `src/ironworks/IronworksLogicLocalization.java`：默认值、持久化、同步原版开关。
- `src/ironworks/IronworksSettings.java`：设置页入口。
- `src/ironworks/IronworksMod.java`：启动时初始化。
- 原版开关：`LStatement.logicLocalization()` 读取 `Core.settings.getBool("logiclocalization", true)`。

> 本功能不修改 `LStatement`/`LExecutor`，不写 `name.token.*` 以外的 bundle，不改逻辑 JSON。
