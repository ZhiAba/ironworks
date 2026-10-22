# 铁流工业 交接文档（2026-10-21，v0.59.2）

## 仓库与产物
- 源码：`/sdcard/dsh/mindustry-mod`
- 构建：`cd /sdcard/dsh/mindustry-mod && bash build.sh` → `out/ironworks.jar`
- API：`/root/api/Mindustry-160.1.jar`（编译）、`/root/api/server-release-160.3.jar`（无头测试）
- 部署（Shizuku 可用时）：
  ```
  phone_sh: cp out/ironworks.jar /sdcard/Android/data/io.anuke.mindustry/files/mods/ironworks.jar
            cp out/ironworks.jar /sdcard/Android/data/com.github.tinylake.mindustryX/files/mods/ironworks.jar
  ```
- 当前 jar md5：`PENDING`（0.53.0）
- 测试：`bash tools/chain-test.sh`（T1–T75，无头端，单实例运行，内部 timeout 520s）
- 调试方块（效果分类、免费）：
  - 液体计量器 → `/sdcard/ironworks-liquid.log`
  - 全量日志器 → `/sdcard/ironworks-tick.log`（每 tick：dt/ms、玩家、单位/建筑数、模组炮塔弹药/液体/目标、总量 [DELTA]、事件）

## 内容（v0.53.0）
- 39+物品：钢铁线、弹药、大口径炮弹、芯片（基础/高级/量子核心）、银/金、钛合金、铝/铝合金/航空铝
- 液体：氧气、铁水、钢水、酸、石墨烯冷却液、热冷却液
- 铝全量：铝土矿矿脉→溶出罐→焙烧炉→电解槽→铝厂(铝锭/铝板/铝合金/航空铝)→铝热炉(铁水+氧化铝回炉)
- 原版材料闭环：硅晶炉/玻璃窑/石墨化炉/聚合釜/相织机/合金炉/火工车间/废料分选厂
- 方块：冶炼（高炉/转炉/连铸/电弧炉/还原炉）、化工（电解/空分/脱硫/火药/制酸/贵金属浸出/芯片车间/铝厂）、弹药线、炮弹组装、炮塔（速射/穿甲/迫击炮/电弧/磁轨/奇点）、装甲/支援、储运（桶/高温管道/坩埚/通液传送带 2 档 + 路由/交叉/带桥）、调试方块
- 科技：材料线与建筑线挂基础核心；物品产出解锁免费，建筑显式研究成本

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
1. 从 `/sdcard/文件中转/工作站` 移植「律令」炮塔（含贴图）做对比，定位弧电差异
2. 全局特效：磁轨聚能光圈、屏幕震动、命中粒子；奇点碎片贴图化
3. 芯片作为精密建筑建材分档（T1/T2/T3 建筑）
4. 铝/合金进一步进入更多建筑成本与弹药配方
5. 新内容实机检查：数据库图标、配方表、铝土矿矿脉生成

## 环境注意
- Android + proot 容器；无 systemd/GUI；Shizuku 提供特权 shell（`phone_sh`）
- 容器不能直接写 `/sdcard/Android/data`，部署只能用 `phone_sh`
- 游戏内改 mod 必须**完全重启游戏进程**才加载新 jar
- 用户当前使用游戏刻加速模组（日志 `dt` 可看倍率）

## 重启后从这里继续（2026-10-22 00:20）
状态：v0.53.0 已构建并部署双客户端，md5 `PENDING`。
T1–T93 整套回归已跑过全绿；T76/T77 已并入整轮回归并全绿。

1. 若主人在会话里说“继续”：先看这份文档，然后确认是否需要把 T76/T77 纳入整轮回归
   （`bash tools/chain-test.sh`，约 9 分钟，一次只能开一个实例）。
2. 本地模型：快速通道 `local-code "..."` 可用；轻量外壳 `node tools/local-agent.mjs "..."`
   可用（读/写/bash/finish 四个工具，curl 传输）；DSH 的 `local_coder` 仍未接通
   （模型返回纯文本不调工具，疑似 tool_choice 被传成对象 + 提示词过重）。
   要修 `local_coder` 需要在 DSH 与 18080 之间插代理抓请求，并重启 App。
3. 待办优先级：① 整轮回归 ② 律令炮塔移植 ③ 特效升级（磁轨充能环/震动/命中粒子）
   ④ 芯片分档建材 ⑤ 铝/合金进入更多配方。
4. 仓库有 git（元数据在 `/root/git/ironworks`），重启前工作区是干净的。
