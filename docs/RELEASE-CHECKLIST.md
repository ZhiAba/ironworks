# 发布前验收清单

> 每次发版前逐项打勾。历史事故几乎都能落在下面某一条里。

## 一、内容与静态检查

- [ ] **贴图齐全**：block / item / liquid / status 均有同名 png；矿脉要 `名称1~3.png`
- [ ] **本地化键**：`node tools/check-keys.js` 通过（中英各 140+ 键）
- [ ] **详情页不崩**：
  - 炮塔必须有 `shootType` 或 `ammoTypes`（否则 `StatValues.ammo` 读 null 崩溃）
  - 所有 `Bar` 颜色回调返回非 null（液体用 `barColor != null ? barColor : color`）
- [ ] **单位正确**：
  - 耗电：`consumePower(每秒值 / 60)`（引擎参数是每 tick）
  - 贴图偏移：128px 贴图 = 32 世界单位（像素 ÷ 4）
  - 炮塔局部坐标：`rotation - 90` 旋转，Y 向上为正
- [ ] **配方合法**：
  - 单液体输出可四面；多液体输出必须给 `liquidOutputDirections`
  - 液体容量 ≥ 同时存在的输入 + 输出峰值
  - 配方索引只追加，不插队（旧存档兼容）
- [ ] **存档**：自定义 Building 重写 `version()/write()/read()`；配置要 `saveConfig`

## 二、构建与测试

- [ ] `bash build.sh` 无报错（Android 需 `.class` + `classes.dex`）
- [ ] `bash tools/run-tests.sh` 全绿（单实例锁 + 脚本快照，避免边跑边改）
- [ ] 新增/修复的功能有对应 `T` 编号，并更新 `docs/TEST-INDEX.md`
- [ ] 大重构附带迁移说明（旧存档/旧蓝图）

## 三、部署

- [ ] jar 复制到两个客户端
- [ ] 两端 `md5sum` 一致
- [ ] **完全重启游戏**后实测：建造、详情页、开火、存档读档
- [ ] 记录 md5 到 `docs/HANDOFF.md`

## 四、平衡（有炮塔/弹药时）

- [ ] `node tools/balance.js` 更新
- [ ] 新炮塔写清：射速、伤害、DPS、弹药/电耗、冷却液分级
