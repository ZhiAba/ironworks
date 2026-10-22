# 开发工作流约定

> 本文件是「每次改动都要做的事」的清单，和 `RELEASE-CHECKLIST.md`（发版前）互补。

## 一、改完必做

1. **同步更新日志**：改 `docs/`、模组内容、工具脚本 → 必须在 `docs/CHANGELOG.md`
   对应版本下追加条目（新增/修复/根因/对应测试编号）。
2. **版本号三处同步**：`mod.hjson`、`docs/STATE.md`、`docs/HANDOFF.md`。
3. **补测试并登记**：新增/修复功能补 `T` 编号，更新 `docs/TEST-INDEX.md`
   （重新跑索引生成或手动补行）。
4. **构建 + 回归**：`bash build.sh` → `bash tools/run-tests.sh`。
5. **部署**：Shizuku 复制到两个客户端，核对 md5 一致，写进 `HANDOFF.md`。
6. **提交**：提交信息带版本号与 `T` 编号。

## 二、命令速查

```bash
cd /sdcard/dsh/mindustry-mod
bash build.sh                    # 构建 out/ironworks.jar
node tools/check-keys.js         # 本地化键检查
bash tools/run-tests.sh          # 回归（单实例锁 + 脚本快照）
git add -A && git commit -m "feat(x.y.z): ..."
```

部署（需要 Shizuku）：

```
phone_sh: cp out/ironworks.jar /sdcard/Android/data/io.anuke.mindustry/files/mods/ironworks.jar
          cp out/ironworks.jar /sdcard/Android/data/com.github.tinylake.mindustryX/files/mods/ironworks.jar
```

## 三、踩过的坑（不要重复）

- `consumePower` 参数是**每 tick**，每秒值要 ÷60。
- 贴图 128px = 32 世界单位，像素偏移要 ÷4。
- 炮塔贴图“上方”对应 `rotation=90°`，局部坐标按 `rotation-90` 旋转，Y 向上为正。
- 炮塔必须给 `shootType` 或 `ammoTypes`，否则详情页 `StatValues.ammo` 崩溃。
- `Bar` 颜色回调不能返回 null（液体用 `barColor != null ? barColor : color`）。
- 多液体产出必须配 `liquidOutputDirections`；单液体可四面输出。
- 配方索引只追加，不插队（旧存档兼容）。
- 无头端一次只能跑一个实例；**不要边跑边改测试脚本**（用 `run-tests.sh` 的快照）。
