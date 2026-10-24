# 铁流工业 交接文档（2026-10-24，v2.1.5.0）

## 仓库与产物
- 源码：`/sdcard/dsh/mindustry-mod`
- 构建：`cd /sdcard/dsh/mindustry-mod && bash build.sh` → `out/ironworks.jar`
- 版本号：`a.b.c.d`，无进制、按语义递增（修 Bug→d，平衡/小调整→c，新机制/内容→b，架构/大时代→a，高位变化时低位归零）。`bash tools/bump-version.sh hotfix|patch|minor|major`，`--next` 只试算
- API：`/root/api/Mindustry-160.1.jar`（编译）、`/root/api/server-release-160.3.jar`（无头测试）
- 部署（Shizuku 可用时）：
  ```
  phone_sh: cp out/ironworks.jar /sdcard/Android/data/io.anuke.mindustry/files/mods/ironworks.jar
            cp out/ironworks.jar /sdcard/Android/data/com.github.tinylake.mindustryX/files/mods/ironworks.jar
  ```
- 当前 jar md5：`2c807262327eb16e8b469e810fd663af`（2.1.5.0，已部署双客户端）
- 测试（分级）：`bash tools/run-tests.sh 95 96` 冒烟 / `group liquid` 集成 / 无参全量；单实例锁 + 脚本快照

## 本次（2.1.5.0）：RBMK 异常广播
- 新增 `IronworksRbmkAlerts`：在 `RBMKReactorBuild.updateTile` 末尾调用，客户端按条件边沿写 `IronworksNotifications.warn`；异常消失并稳定 3 秒后重新武装，避免抖动刷屏
- 温度安全阈值 **100%**（约 780°C；熔毁 125%）；温度/流量/水罐/汽包/汽路/压力/SCRAM/中毒分别有独立提示；消息带 tile 坐标、当前百分比、控制模式和动作
- 动作文案：全自动「已自动降功率/已紧急停堆」；半自动「已紧急停堆/未自动介入，请手动插棒」；强制手动「未自动介入，请立即手动插棒」
- 熔毁日志改为 `⚠️警告：位于 x,y 的 RBMK 反应堆堆芯已熔毁`；去重状态 `alertMask/alertQuiet` 是运行时字段，不写存档
- 边界：客户端本地通知、headless 跳过、不改反应堆逻辑或数值
- 测试：RBMK 116/135/136/142/149 + T30/T154/T155 合并复跑 **18/18**，38 秒；文案/触发时机需实机确认
- 当前 jar md5：`2c807262327eb16e8b469e810fd663af`（2.1.5.0，已部署双客户端）

## 本次（2.1.4.0）：独立动画日志面板
- `IronworksLogDialog` 已删除，换成 `IronworksLogPanel`：不再用 `BaseDialog`，直接挂 `hudGroup` 的 `windowBack + panel`
- 打开：背景 `fadeIn`，面板从左侧 `moveToAligned` 居中，`0.42s`、`Interp.pow3Out`；关闭：反向 `0.30s`、`pow3In`，点面板外/右上 × 均可关
- 逐条消除：每条右侧 `<` 先 `fadeOut(0.25s)` + 向左 `moveBy`，动画结束后才 `IronworksNotifications.remove(entry)` 并重建列表；避免行先消失导致动画看不到
- 左侧长条点击改为 toggle；`ironworks.sidebar.enabled` 与「辅助界面…」入口保持不变
- 测试：T30/T154/T155 子集 **3/3**，19 秒；动画/尺寸/位置需实机确认
- 当前 jar md5：`14876dcf8a4859efd56768b7cd35b8ff`（2.1.4.0，已部署双客户端）

## 本次（2.1.3.0）：左侧长条 + 日志
- 研究 DeepSpace 的 `singularity.ui.fragments.NotificationFrag`：左侧长边是一个 26×520 的侧边按钮（`SglStyles.sideButtonRight`），点击展开 notificationHistory；不是一排功能图标
- 独立实现 `IronworksSidebar`：没有改 DeepSpace 代码；没有塞 `blockCatTable`（我们的 `IronworksBlockSearch` 已经在重排那张表），改为挂在 `ui.hudGroup` 左缘中央，26×520、黑色半透明、中间齿轮
- 点击打开 `IronworksLogDialog`：`IronworksNotifications` 保留最近 100 条（加载/地图/电网缺电恢复/RBMK 熔毁/彩蛋/辅助开关），可清空、可跳辅助界面
- 设置：`ironworks.sidebar.enabled` 默认开；「设置 → 铁流工业 → 辅助界面…」可关
- 边界：客户端 only、无头跳过、不拦截地图点击、不改任何游戏数值
- 测试：T30 + T154 + T155 子集 **3/3**，20 秒；长条实际位置需实机确认
- 当前 jar md5：`8d66775dbab305441a87d42c58b8a46c`（2.1.3.0，已部署双客户端）

## 本次（2.1.1.0）：内嵌辅助 HUD（电网 / 物流）
- 新增 `IronworksHud`：客户端右侧 HUD；`IronworksSettings` 加「辅助 HUD…」按钮，`IronworksAuxSettings` 是独立二级设置界面
- 电网总览：扫描 `Groups.build` 的 `PowerGraph`，汇总 `getLastPowerProduced/getLastPowerNeeded/getPowerBalance`（×60 换每秒）与电池储量；缺电电网标红；0.5 秒刷新
- 物流监视：鼠标/手指指向建筑，1 秒窗口统计物品/液体库存净变化，最多 3 项；只读，不碰传输；自研 MultiCrafter 也能按库存变化统计
- 两个开关默认关闭；二级界面可一键全部关闭；无头跳过、`hudGroup` 不可用时跳过、不拦截点击
- 测试：全量 **93/93**（700 秒）；最终 jar 上 T30 + T154 + T155 子集 **3/3**，20 秒。HUD 是客户端 UI，无头回归不覆盖；实机需看右侧位置/宽度是否挡界面
- 当前 jar md5：`dc2fd12da3742e1922f644bfbb96ce7a`（2.1.1.0，已部署双客户端）

## 本次（1.6.0.0）：RBMK 水/汽闭环
- 反应堆：水→汽 **1:1**、100% 通量 **980/980 L/s**；水罐 10000 L + 汽包 2000 L；反应堆自带 1400 L/s 给水泵（从相邻管道/水罐主动抽）
- 强制手动模式新增「水位上限」滑条（config 编码 `1000+百分比`）；半自动/全自动后台按通量自动算 55%~90%
- 全自动修正：目标功率反算棒位时补上 `/fluxMax`（旧公式会把 40% 目标算成 125%）
- 温度：`dT/dt = 0.14·flux − flow·(0.06 + 0.09·T) − 0.01`；满功率满给水 ~79%，断水 ~9 秒到 125%、再 1 秒熔毁
- AZ-5：汽包满 + 水罐到当前上限 + 控制棒插到底 → `coreBoom` 2 秒引爆（T142）；棒没到底不炸（T145）
- 汽轮机 980 L/s 蒸汽 → 45000 电/s + 980 L/s 温水；`inputSide` 禁止温水倒灌进蒸汽管（1.6.1.0 已调为 122.5 L/s → 24000 电/s）
- 冷却塔 1200 L/s（120 L / 6 tick），闭环留 20% 余量
- 彩蛋计时改 `Vars.state.tick`（900 tick = 15 秒），并记录 `[MEME] feed step=.. gap=..`
- 测试：RBMK 段 33/33；新增 T139–T146；全量回归 **83/83**（700 秒，2026-10-23）

## 本次（1.6.1.0）：汽轮机调参 + 熔融物生存规则
- 汽轮机：122.5 L/s 蒸汽 → 24000 电/s + 122.5 L/s 温水；温水箱满自动停机（不吃汽、不发电、不收汽），排掉后自动恢复
- 一台满功率反应堆（980 L/s）现在配 8 台汽轮机；冷却塔 1200 L/s 仍够 8 台的冷凝水
- 堆芯熔融物：生存模式不可拆、免疫伤害、永久残留；沙盒模式可拆可打、180 秒凝固
- 测试：新增 T147（温水满停机/恢复）、T148（corium 生存/沙盒）。改动相关子集全绿：MCT/集群 10/10、RBMK/汽轮机/熔融物 35/35；全量 1.6.0.0 为 83/83，1.6.1.0 全量一轮被设备降频干扰（CPU 降到 4%，sleep 型用例错位），受影响段落复跑通过。

## 本次（1.6.2.0）：全自动保护
- 全自动：有效功率 = 目标 × min(蒸汽因子, 给水因子, 温度因子)；汽包 50~90%、给水 30~10%、堆温 0.95~1.15 时快速降到 0
- 反算棒位计入空泡正反应性（`voidFactor`）；保护性插棒 1.5 秒走完全程
- AZ-5 保护：水罐 >95% 且汽包满时自动棒位封顶 98%；自动水位最高 90%，正常自动模式不会到 9800 L
- AZ-5 判定改用实际水罐 98%（9800 L），不再按滑条上限
- 测试：新增 T149（蒸汽堵死自动降功率不炸）、T150（给水见底自动停机）；RBMK 段 **37/37**

## 本次（1.6.3.0）：堆芯熔融物免疫爆炸
- 问题：反应堆爆炸的 `Damage.damage` 会把已有的 corium 一起打掉（沙盒 900 血直接清空）
- 修复：`CoriumBuild.damage()` 直接返回，corium 免疫一切伤害（爆炸/炮火/核弹/单位），不再掉血
- 沙盒仍可拆除（`canBreak` 仅沙盒 true）、180 秒自凝固；生存不可拆、永久残留
- 测试：T148 增加沙盒伤害免疫；新增 T151（爆炸范围内已有 corium 存活 + 新 corium 生成）；RBMK 段 **37/37**
- 调试方块（效果分类、免费）：
  - 液体计量器 → `/sdcard/ironworks-liquid.log`
  - 全量日志器 → `/sdcard/ironworks-tick.log`（每 tick：dt/ms、玩家、单位/建筑数、模组炮塔弹药/液体/目标、总量 [DELTA]、事件）

## 本次（1.7.0.0）：自动雷达
- 新方块 `auto-radar` 3×3，103×103 格正方形索敌；四种模式：血量上限最高 / 距离最近 / 最密集 / 当前血量最低
- 面板点炮塔链接/取消，最多 10 个、30 格内；雷达每 12 tick 重选目标，直接写炮塔 `target`，并把炮塔自身索敌计时器推到未来（接管期间不自己换目标）
- 炮塔开火仍走原版 `within(target, range())`，不会超射程；空/地过滤 + 弹药 `collidesAir/collidesGround` 也检查
- 标记：四枚指向中心的实心亮红等腰三角，300 tick 一圈 + 0.7 秒心跳缩放，画在所有单位之上
- 性能：12 tick 扫描 + pos 错帧；最密集用 8×8 计数网格（复用 int[1024]）；射程框/连线只在面板打开时画
- 科技挂在弹药组装机节点下；测试 T152（四模式 + 接管 + 计时器 + 10 上限）通过

## 本次（2.1.0.0）：伤害飘字 + 彩蛋开关
- `DamageNumbers`：`Dmg.hit` 结算前后取生命+护盾差值显示实际伤害，护甲抵消按属性护甲倍率单独计算；格式 `17（-3）`，无抵消 `-0`，颜色=属性色，180 tick 渐隐上浮
- 覆盖：所有 `Dmg.hit`（天罚/磁轨/电弧/奇点/腐蚀/玉碎等）+ typed bullet；typed 弹体溅射逐个单位显示；模组物理弹（速射/穿甲/迫击炮）和原版双管炮改为 typed physical；未走 Dmg 的普通子弹用 `UnitDamageEvent` 兜底（其溅射/持续激光/连锁闪电跳过）
- 设置：铁流工业分类新增「显示伤害数字」和「启用彩蛋熔毁」两个开关
- 彩蛋开关：`IronworksRbmkChat.enabled()` 读 `ironworks.rbmk.meme`；关闭时 feed 直接 reset，不引爆
- 测试：T155（关闭不引爆 / 开启恢复），T136 保持通过
- 当前 jar md5：`e105bb7320512e7516bab8df1c4310aa`（2.1.0.0，已部署双客户端）

## 本次（2.0.0.0）：属性伤害
- 三件套：`DamageType`（七属性 + 色号/护甲/状态/克制）、`Dmg`（`hit/area/pierce` + 归属注册表 + 原版弹体反射拷贝）、`TypedBullets`（6 个带属性弹体基类）
- 属性：物理 / 雷霆 / 水 / 冰 / 能量 / 火 / 腐蚀；雷霆 0.5 甲 + 潮湿 ×1.5，冰 0.7 甲 + 潮湿 ×1.25，能量 0.35 甲 + 电击 ×1.25
- 模组直接结算全部改走 `Dmg.hit`：天罚/磁轨/电弧塔=雷霆，奇点=能量，腐蚀弹=腐蚀，汽油弹/燃烧炮弹=火，其余物理
- 原版接入：海啸/波浪的水/冷冻液/熔渣弹、蓝瑟激光、魔灵弹体加载时一次性反射拷贝为同类带属性弹体；电弧塔/熔毁登记归属（连锁/持续激光逐跳仍走原版，见 docs/DAMAGE-TYPES.md 边界）
- 详情页：`IronworksDamageUI` 替换 `Vars.ui.content`，弹药伤害行改为 `[stat]12[lightgray] [#5aa9ff]水[]元素伤害`，范围伤害行同样着色
- 测试：T154（归属 + 雷霆半甲 > 物理 + 潮湿目标雷霆加成 + 水属性挂潮湿）；新增 `docs/DAMAGE-TYPES.md`
- 当前 jar md5：`754916c78e3a90df568473f4cd4b08d4`（2.0.0.0，已部署双客户端）

## 本次（1.7.0.1）：雷达链接跨换队 + 索敌计时器
- 主因：炮塔索敌计时器被设成**未来**，Arc `Interval.check` 把未来判成时钟回拨并立即返回 true → 炮塔自己的 `findTarget` 每 tick 都跑，抢走雷达的 max-HP 目标（换成它的“最近敌人”）
- 修复：每 tick 把计时器按到 `Time.time`（elapsed=0）；解除链接设 `Time.time - 1000` 恢复
- 已链接炮塔换队不再自动断开；新链接只收友军
- 目标变成炮塔友军时炮塔自身不开火（除非友伤），不会转打玩家；连锁/溅射另算
- 全量日志新增 `[RADAR]` 与炮塔 `ttgt=type/team/hp`；T152 teamKeep + T153 实时 3 秒均通过

## 内容（v0.62.0）
- 39+物品：钢铁线、弹药、大口径炮弹、芯片（基础/高级/量子核心）、银/金、钛合金、铝/铝合金/航空铝
- 液体：氧气、铁水、钢水、酸、石墨烯冷却液、热冷却液
- 铝全量：铝土矿矿脉→溶出罐→焙烧炉→电解槽→铝厂(铝锭/铝板/铝合金/航空铝)→铝热炉(铁水+氧化铝回炉)
- 原版材料闭环：硅晶炉/玻璃窑/石墨化炉/聚合釜/相织机/合金炉/火工车间/废料分选厂
- 方块：冶炼（高炉/转炉/连铸/电弧炉/还原炉）、化工（电解/空分/脱硫/火药/制酸/贵金属浸出/芯片车间/铝厂）、弹药线、炮弹组装、炮塔（速射/穿甲/迫击炮/电弧/磁轨/奇点）、装甲/支援、储运（桶/高温管道/坩埚/通液传送带 2 档 + 路由/交叉/带桥）、调试方块
- 科技：材料线与建筑线挂基础核心；物品产出解锁免费，建筑显式研究成本

## 参考：新视界源码 vs 发布版
- 用户实机版本：`1yuria-shikibenewhorizonmod.zip` → `2.0_v154_1`（minGameVersion 154）
- 本地源码：`源码NewHorizonMod-main` → `2.0-TEST-VERSION`（minGameVersion 154）
- 对比：bundle 仅差 16 行（源码略新，多了 silicar-crusher / stamping-presser / ammonia / hydrazine 等），贴图目录完全一致 → 直接用源码版参考，无需逆向

## 矩形占地（0.62.0 / 0.63.0）
- 主集群 = 4×4 核心 + 两个 2×2 隐藏链接块（`compute-cluster-link`）→ 4×6；高级模块 = 2×2 + 两个 1×1 通风块（`compute-advanced-link`）→ 2×3
- 链接块：`BuildVisibility.hidden`、不可建造、不可拆除、液体转发给核心、`relX/relY` 相对坐标读档重绑
- 放置校验 `canPlaceOn` 检查链接块占地；拆主块 `onRemoved` 连带清链接块；遗留链接块 10 tick 自毁
- 贴合判定：包围盒 + 整边覆盖；方向 dir 指「主集群相对模块的方向」（0 上 / 1 右 / 2 下 / 3 左）
- 0.63.2：链接块展示委托（名称/图标/血条/消耗 → 主块）、共享 items/liquids 池、isHidden 恒真；放置幽灵只由 drawPlan 画（半透明+闪白）
- 0.63.0：主块画整张一体贴图（`region` 即 4×6 / 2×3 大图），链接块 `draw()` 留空；`*-ui` 为建造菜单方形图标；`drawPlace`/`drawPlanRegion` 按包围盒居中画幽灵

## MCT 跃迁节点（0.64.0）
- 两个 1×1 方块：`mct-item-node` / `mct-liquid-node`，耗电 18/tick
- 连接：`onConfigureBuildTapped` + config(Integer pos)，12 格内、同队伍
- 配置用负值区分模式：-1 传送 / -2 吸取 / -3 断开，>=0 为连接目标 pos
- 传送模式：acceptItem/acceptLiquid 只对连接处判断，handle 直接转发，节点不存
- 吸取模式：`isOutputItem/isOutputLiquid` 只认产出（MultiCrafter 配方 / GenericCrafter / 分离机 / 钻头 / 泵 / 液体源），从目标移除后交给四邻
- 绘制：连接时 `Drawf.laser` 脉冲激光

## 让新方块支持跃迁吸取（一劳永逸）
- 节点通过 `IronworksTeleport.JumpSource` 识别来源：`jumpLiquid(Liquid)` 返回可抽量，`jumpExtract(Liquid, float)` 实际扣除；物品对应 `jumpItem/jumpExtractItem`
- 不实现接口也可以：只要方块有液体且**不消耗**该液体（储罐/液体源/热液缓冲），通用兜底会自动允许抽取
- 物品仍按 MultiCrafter 配方产出 / GenericCrafter / 分离机 / 钻头识别
- 炮塔：`OverheatTurretBuild` 已实现接口（virtualHot 走 hotStored，真实热液走 liquids）

## 已修复的典型坑
- 配方索引只追加，不插队
- 多液体产出必须按产出下标分方向；单液体全向
- 冷液/热液 `blockReactive=false`（否则原版冷热反应销毁液体）
- MultiCrafter 液体按 craft 整量扣（`ConsumeLiquidsCraft`）+ craft 前原料校验
- 通液带桥 `transportTime=4`（默认 0 会死循环卡死）
- 带桥末端：link 无效时**不能提前 return**，否则不给邻居排液
- 直接结算炮塔（电弧/磁轨/奇点）不发射子弹；音效需手动 `shootSound.at()`
- 玩家操控点击超出射程必须判距离，否则打射程外
- 电弧雷电：自绘 4 根折线（宽 9/5/2，透明度 45%/28%），不用原版 Lightning 细线

## 未完成（下一会话优先级）
0. **实机视觉验收 0.62.0**：4×6 主集群 / 2×3 高级模块的贴图拼接缝、液体从链接块接入、拆除动画是否自然
1. 从 `/sdcard/文件中转/工作站` 移植「律令」炮塔（含贴图）做对比，定位弧电差异
2. 全局特效：磁轨聚能光圈、屏幕震动、命中粒子；奇点碎片贴图化
3. 芯片作为精密建筑建材分档（T1/T2/T3 建筑）
4. 铝/合金进一步进入更多建筑成本与弹药配方
5. 新内容实机检查：数据库图标、配方表、铝土矿矿脉生成

## 文档约定
- 任何文档/内容/工具改动都要同步 `docs/CHANGELOG.md`（见 `docs/WORKFLOW.md`）
- 发版前过 `docs/RELEASE-CHECKLIST.md`

## 环境注意
- Android + proot 容器；无 systemd/GUI；Shizuku 提供特权 shell（`phone_sh`）
- 容器不能直接写 `/sdcard/Android/data`，部署只能用 `phone_sh`
- 游戏内改 mod 必须**完全重启游戏进程**才加载新 jar
- 用户当前使用游戏刻加速模组（日志 `dt` 可看倍率）

## 重启后从这里继续（2026-10-24 08:25）
状态：v2.1.5.0 已构建并部署双客户端，md5 `2c807262327eb16e8b469e810fd663af`。
最近子集：RBMK 116/135/136/142/149 + T30/T154/T155 合并复跑 **18/18**（38 秒）。RBMK 广播属客户端通知，文案/触发时机需实机确认；反应堆数值逻辑未改。

1. 若主人在会话里说“继续”：先看这份文档，然后确认是否需要把 T76/T77 纳入整轮回归
   （`bash tools/chain-test.sh`，约 9 分钟，一次只能开一个实例）。
2. 本地模型：快速通道 `local-code "..."` 可用；轻量外壳 `node tools/local-agent.mjs "..."`
   可用（读/写/bash/finish 四个工具，curl 传输）；DSH 的 `local_coder` 仍未接通
   （模型返回纯文本不调工具，疑似 tool_choice 被传成对象 + 提示词过重）。
   要修 `local_coder` 需要在 DSH 与 18080 之间插代理抓请求，并重启 App。
3. 待办优先级：① 整轮回归 ② 律令炮塔移植 ③ 特效升级（磁轨充能环/震动/命中粒子）
   ④ 芯片分档建材 ⑤ 铝/合金进入更多配方。
4. 仓库有 git（元数据在 `/root/git/ironworks`），重启前工作区是干净的。
5. 实机验证辅助界面：设置 → 铁流工业 →「辅助界面…」；左侧工具栏看位置/宽度，右侧 HUD 看是否挡原版 UI，必要时调 `IronworksSidebar` 的 `padLeft` 或 `IronworksHud` 的 `padRight`。
