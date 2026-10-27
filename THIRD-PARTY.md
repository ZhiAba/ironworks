# 第三方素材与来源说明 / Third-party assets

本文件记录仓库中**不是完全原创**的素材。项目整体按 `GPL-3.0-only`
分发；下列素材同样按 GPL-3.0 分发，并保留其原始版权。

## Mindustry 原版素材

以下文件与 Mindustry 160.1 的原始素材逐字节一致，或直接以其为基础修改。
Mindustry 由 Anuken 及贡献者开发，按 GNU GPL v3.0 授权：

- 上游仓库：<https://github.com/Anuken/Mindustry>
- 上游许可证：GNU GPL v3.0

| 本仓库文件 | Mindustry 原版路径 | 说明 |
|---|---|---|
| `assets/sprites/hot-conduit-bottom-0.png` | `blocks/liquid/conduits/conduit-bottom-0.png` | 耐高温导管底图 |
| `assets/sprites/hot-conduit-bottom-1.png` | `blocks/liquid/conduits/conduit-bottom-1.png` | 耐高温导管底图 |
| `assets/sprites/hot-conduit-bottom-2.png` | `blocks/liquid/fluid.png` | 液体纹理切片 |
| `assets/sprites/hot-conduit-bottom-3.png` | `blocks/liquid/fluid.png` | 液体纹理切片 |
| `assets/sprites/hot-conduit-bottom-4.png` | `blocks/liquid/fluid.png` | 液体纹理切片 |
| `assets/sprites/hot-conduit-top-0.png` | `blocks/liquid/conduits/conduit-top-0.png` | 耐高温导管顶图 |
| `assets/sprites/hot-conduit-top-1.png` | `blocks/liquid/conduits/conduit-top-1.png` | 耐高温导管顶图 |
| `assets/sprites/hot-conduit-top-2.png` | `blocks/liquid/fluid.png` | 液体纹理切片 |
| `assets/sprites/hot-conduit-top-3.png` | `blocks/liquid/fluid.png` | 液体纹理切片 |
| `assets/sprites/hot-conduit-top-4.png` | `blocks/liquid/fluid.png` | 液体纹理切片 |
| `assets/sprites/liquid-sorter.png` | `blocks/distribution/sorter.png` | 液体分类器底图 |
| `assets/sprites/liquid-inverted-sorter.png` | `blocks/distribution/inverted-sorter.png` | 反向液体分类器底图 |
| `assets/sprites/tianfa-base.png` | `blocks/turrets/bases/block-4.png` | 天罚炮塔底座，炮塔主体为原创 |

上述原版素材的版权归 Anuken 与 Mindustry 贡献者所有；本项目仅在
GPL-3.0 下使用和再分发。

## 本项目生成的素材

以下素材由本仓库的脚本或程序生成，随项目按 GPL-3.0 分发：

- `tools/art/Gen.java` — 算力集群、模块和 UI 图标
- `tools/make-cooling-sprites.js` — 冷却塔占位贴图
- `tools/make-rbmk9-sprite.js` — RBMK-9 占位贴图
- `tools/make-turbine4-sprite.js` — 4×4 汽轮机占位贴图
- `tools/gen-reactor-alert.js` — 反应堆警告音

其余方块、物品、液体图标和界面图形由项目维护者绘制或程序生成；
如有遗漏，请提交 issue 更正。

## 代码

除 Mindustry 的公开 API 外，本项目未声明内嵌其他项目的源代码。
设计上参考过的项目与其许可证记录在 `docs/RESEARCH-3D.md` 和
`docs/MULTIBLOCK-LIB-REVIEW.md`；参考思路本身不构成代码复制。
