# 别人的 3D 管线调研（2026-10-20）

用户给的四个仓库，逐个看了代码/说明。

| 仓库 | 内容 | 许可 | 对模组的意义 |
|---|---|---|---|
| `UniverseEmbedded/ArcY` | Arc 框架的 fork，分支 `shared-campaign/v160.4/a1` | Apache-2.0 | 共享战役的**引擎侧**改动；仓库里 859 个文件全是 Arc 框架本体，没有战役代码 |
| `UniverseEmbedded/Mindustry` | 改过的客户端 | — | 通过公开 API/代理访问返回 403/404（私有或已改名），看不到源码 |
| `sayed1145/Singularity` | 大型整合模组（v160.5，GPL-3.0） | GPL-3.0 | `blackhole/g3d`、`voxel/gfx`：方块/单位/炮塔的实时 3D |
| `sayed1145/RBMK-White-Reactor` | 小型核电模组，5 个全 3D 方块 | GPL-3.0 | `rbmk/gfx`：**离线烘焙 + 运行时运动部件**的完整管线 |

## RBMK 的 3D 管线（最值得借鉴）

核心思路：**静态几何离线烘焙成贴图，运动部件运行时逐面投影**，不用自定义着色器。

| 文件 | 作用 |
|---|---|
| `gfx/Mesh.java` | 四边形网格 + 建模工具（box/bevel/lathe/pipe/ring），带材质标记 |
| `gfx/Cam.java` | 固定透视相机（烘焙与运行时共用） |
| `gfx/Light.java` | 主光 + 环境光 + 高光 |
| `gfx/Live.java` | 运行时逐面投影：`Fill.quad` + 背面剔除 + 光照 |
| `gfx/BlockModel.java` | 一个建筑的模型基类：`buildStatic` / `buildEnvelope` / `buildRest` / `drawLive` / `drawOver` |
| `world/DrawModel.java` | 接入点：`extends DrawBlock`，方块 `drawer = new DrawModel(model)` |
| `tools/bake/Baker.java` | 离线 z-buffer 光栅化，SSAA、按高度的 AO、程序化材质，输出 `<name>-hd.png` 与 `<name>-front-hd.png` |

遮挡处理：把「比任何运动部件都更靠近相机」的静态像素单独烘焙成前景层，
运行顺序 = 烘焙底图 → 运动部件 → 前景层 → 永远置顶部件，等价于深度测试。

一个建筑固定 2 张贴图；动态部件只提交少量四边形。作者实测 8x8 电站 0.27ms/次、
约 3555 个四边形。

## 可行性验证（已在容器里跑过）

把 `Mesh / Cam / Light / Pal / Live / BlockModel / DrawModel` 直接拷到临时工程，
用我们的编译基准 `Mindustry-160.1.jar` 编译：

```
javac --release 16 -cp /root/api/Mindustry-160.1.jar ... → exit 0，零报错
```

说明这套 API 在 160.1/160.3 上是兼容的，不存在版本迁移成本。
Baker 只依赖 `javax.imageio` / `java.awt`，容器里的 JDK 可以直接跑。

## 移植到本模组的工作量

| 步骤 | 估算 |
|---|---|
| 1. vendoring `gfx` 包（改名到 `ironworks.gfx`，保留 GPL 版权头） | 小，纯拷贝 |
| 2. 把 Baker 接进 `build.sh`（编译工具 → 烘焙到 `assets/sprites`） | 小 |
| 3. 给一个方块写模型（Mesh 代码，按格数手动摆几何） | **每个方块 80~200 行**，主要成本 |
| 4. 实机验证：图集重建后贴图是否正常（`BlockModel.load()` 已处理） | 中 |

结论：管线本身可以直接搬；真正的工作量在**建模**。建议先拿一个方块做试点。

## Singularity 的 g3d（更通用，也更大）

`blackhole/g3d` 除了方块，还有单位/炮塔的 3D：
`UnitRenderer`（17KB）、`Walker3D`、`Tank3D`、`TurretModel`、`DrawTurret3D`、`LegGuard`……
它是运行时渲染为主，规模比 RBMK 大得多。

对我们的意义：等做单位线时再研究；当前先不碰。

## 许可证

两个模组仓库都是 **GPL-3.0**。Mindustry 本体也是 GPL-3.0，所以把它们的代码并进我们的模组
在法律上没问题，但需要：保留版权声明、我们的模组也按 GPL-3.0 分发、在 README 注明来源。
