# Java 模组教程：炮塔自发光与子弹光照强度

> 适用：Mindustry 160.x Java 模组。  
> 目标：让炮塔“看起来在发光”，并让子弹/弹道照亮周围地面。  
> 先记住三个不同的东西：**炮塔贴图发光**、**炮塔环境光照**、**子弹动态光照**。它们不是同一个字段。

---

## 1. 先分清三种“亮”

### 1.1 炮塔开火时的自发光（heat）

炮塔开火后，原版会把 `blockname-heat.png` 用 **additive（加色）** 方式盖在炮塔上。

相关字段：

```java
public Color heatColor = Pal.turretHeat; // Turret
public float cooldownTime = 20f;         // Turret，热量衰减时间
```

运行时：

```java
build.heat = 1f;                         // 每次开火
heat -= 1 / cooldownTime * Time.delta;  // 逐帧衰减
```

绘制：

```java
Drawf.additive(
    heat,
    block.heatColor.write(Tmp.c1).a(build.heat),
    build.x + recoilOffset.x,
    build.y + recoilOffset.y,
    build.drawrot(),
    Layer.turretHeat
);
```

要点：

- **必须有 `blockname-heat.png`**，否则 `heat.found() == false`，什么也不会发生。
- `heatColor` 的 RGB 决定颜色；绘制时 alpha 会被 `build.heat` 覆盖，改 `heatColor.a` 不会改变亮度。
- 想亮得更久：增大 `cooldownTime`。
- 想开火瞬间更亮：使用更亮的 `heatColor`，或在 `-heat` 贴图里用更接近白色的核心区域。
- `-heat` 的尺寸应与炮塔旋转区域一致，通常和 `blockname.png` 同尺寸，透明背景。

最小例子：

```java
public class GlowTurret extends ItemTurret{
    public GlowTurret(String name){
        super(name);

        // 开火后保持黄色余热
        heatColor = Color.valueOf("ffd27a");
        cooldownTime = 45f;

        // 其它炮塔配置……
    }
}
```

资源：

```text
sprites/blocks/turrets/glow-turret.png
sprites/blocks/turrets/glow-turret-heat.png
```

`DrawTurret` 会自动查找 `glow-turret-heat`；不需要手动 `@Load`。

---

### 1.2 炮塔常亮的环境光（emitLight）

如果想让炮塔像灯一样**照亮周围地面**，需要：

```java
emitLight = true;
lightColor = Color.valueOf("ffd27a");
lightRadius = 80f; // 世界单位，不是像素
lightColor.a = 0.8f; // 这个 alpha 在环境光路径里表示强度
```

但注意：**只设置 `emitLight` 对“有实体建筑”的炮塔不一定够。**

原版 `BlockRenderer` 对建筑会调用：

```java
entity.drawLight();
```

而 `Building.drawLight()` 默认只处理液体发光，不会自动调用
`block.drawEnvironmentLight()`。所以炮塔需要在自己的 `Build` 里补一次 `Drawf.light`。

示例：

```java
public class GlowTurret extends ItemTurret{
    public GlowTurret(String name){
        super(name);

        emitLight = true;
        lightColor = Color.valueOf("ffb84d");
        lightColor.a = 0.8f;
        lightRadius = 90f;

        // emitLight=true 时 Block.init() 会自动把 lightClipSize 扩展到 lightRadius*2，
        // 但动态半径更大时建议手动扩大：
        lightClipSize = Math.max(lightClipSize, lightRadius * 2.5f);

        buildType = GlowTurretBuild::new;
    }

    public class GlowTurretBuild extends ItemTurretBuild{
        @Override
        public void drawLight(){
            super.drawLight(); // 保留液体光等父类行为

            float pulse = 0.85f + Mathf.absin(Time.time, 12f, 0.15f);
            Drawf.light(
                x, y,
                lightRadius * pulse,
                lightColor,
                lightColor.a * pulse
            );
        }
    }
}
```

要点：

- `lightRadius` 是世界单位。60 约等于 7.5 tile；不要拿像素直接写。
- `lightColor.a` 是光照强度；`Drawf.light` 的最后一个参数是透明度/强度。
- `lightClipSize` 影响光照裁剪；半径动态变大时要给它留足余量。
- 如果炮塔只是“开火时亮一下”，优先用 `heat`；常亮环境光才用 `emitLight` + `drawLight`。
- `drawLight()` 是客户端绘制逻辑；不要在里面改战斗数值。

---

### 1.3 子弹的光

`BulletType` 有三个直接字段：

```java
public float lightRadius = -1f;  // 世界单位；-1 = 自动
public float lightOpacity = 0.3f; // 0~1
public Color lightColor = Pal.powerLight;
```

绘制逻辑：

```java
public void drawLight(Bullet b){
    if(lightOpacity <= 0f || lightRadius <= 0f) return;
    Drawf.light(b, lightRadius, lightColor, lightOpacity);
}
```

如果 `lightRadius` 保持 `-1`，`BulletType.init()` 会自动设为：

```java
lightRadius = Math.max(18, hitSize * 5f);
```

例子：炮弹弹体更大时会自动更亮；但通常应该显式设置。

```java
bullet = new BasicBulletType(8f, 40){{
    width = 12f;
    height = 18f;

    // 光色：暖黄
    lightColor = Color.valueOf("ffd166");
    // 照亮半径（世界单位）
    lightRadius = 70f;
    // 强度 0~1
    lightOpacity = 0.75f;
}};
```

调参建议：

| 字段 | 建议 | 说明 |
|---|---:|---|
| `lightColor` | 暖黄/橙色 | 与弹体/炮口颜色一致最自然 |
| `lightRadius` | 40~120 | 世界单位；小子弹 30~50，大炮弹 70~120 |
| `lightOpacity` | 0.3~0.9 | 越大越亮；太高会盖住地形细节 |
| `lightRadius = 0` | 关闭 | `drawLight` 直接 return |
| `lightOpacity = 0` | 关闭 | 完全无光 |

> `lightOpacity` 是主要亮度旋钮。`lightColor.a` 不是子弹光照强度的主字段；
> 子弹路径用的是 `lightOpacity`。

### 1.4 自定义子弹光（脉冲/拖尾）

如果想让子弹光呼吸、闪烁，重写 `drawLight`：

```java
bullet = new BasicBulletType(8f, 40){{
    lightColor = Color.valueOf("ffd166");
    lightRadius = 80f;
    lightOpacity = 0.7f;

    @Override
    public void drawLight(Bullet b){
        if(lightOpacity <= 0f || lightRadius <= 0f) return;

        float pulse = 0.8f + Mathf.absin(Time.time, 8f, 0.2f);
        Drawf.light(b, lightRadius * pulse, lightColor, lightOpacity * pulse);
    }
}};
```

注意：重写后不再调用父类，但效果等价；不要在里面再创建实体或写战斗逻辑。

---

## 2. 炮口闪光与炮塔自发光怎么配合

### 2.1 炮口闪光

炮塔和子弹都可以有 `shootEffect`：

```java
bullet = new BasicBulletType(8f, 40){{
    shootEffect = new Effect(18f, e -> {
        Drawf.light(e.x, e.y, 55f * e.fout(), Color.valueOf("ffd166"), 0.8f * e.fout());
    });
}};
```

`e.fout()` 从 1 衰减到 0，光也随之衰减。

想给炮塔单独指定炮口效果：

```java
public class GlowTurret extends ItemTurret{
    public GlowTurret(String name){
        super(name);

        shootEffect = new Effect(20f, e -> {
            Drawf.light(e.x, e.y, 60f * e.fout(), Color.valueOf("ffd166"), 0.7f * e.fout());
        });
    }
}
```

`Turret.shootEffect` 会覆盖子弹自带的 `shootEffect`；如果不同弹药需要不同颜色，优先放在各自的 `BulletType` 上。

### 2.2 两者叠加不要过量

推荐分工：

- 炮塔 `-heat`：开火后的机械余热，范围小、颜色偏橙白；
- 炮口 `shootEffect`：开火瞬间的短促闪光；
- 子弹 `lightColor`：飞行中的持续照明；
- 炮塔 `emitLight`：常亮/待机氛围光（可选）。

四项全开时，把每一项的亮度控制在 `0.5~0.8`，否则容易整片地面发白。

---

## 3. 最小可运行示例

### 3.1 自定义炮塔

```java
public class GlowTurret extends ItemTurret{
    public GlowTurret(String name){
        super(name);

        // 开火余热
        heatColor = Color.valueOf("ffd27a");
        cooldownTime = 50f;

        // 常亮氛围光（可选）
        emitLight = true;
        lightColor = Color.valueOf("ffb84d");
        lightColor.a = 0.65f;
        lightRadius = 90f;
        lightClipSize = Math.max(lightClipSize, lightRadius * 2.5f);

        buildType = GlowTurretBuild::new;
    }

    public class GlowTurretBuild extends ItemTurretBuild{
        @Override
        public void drawLight(){
            super.drawLight();

            float pulse = 0.9f + Mathf.absin(Time.time, 10f, 0.1f);
            Drawf.light(x, y, lightRadius * pulse, lightColor, lightColor.a * pulse);
        }
    }
}
```

### 3.2 子弹

```java
bullet = new BasicBulletType(8f, 40){{
    width = 12f;
    height = 18f;

    lightColor = Color.valueOf("ffd166");
    lightRadius = 80f;
    lightOpacity = 0.75f;

    shootEffect = new Effect(16f, e -> {
        Drawf.light(e.x, e.y, 50f * e.fout(), lightColor, 0.7f * e.fout());
    });
}};
```

### 3.3 资源文件

```text
sprites/blocks/turrets/glow-turret.png       // 炮塔旋转部分
sprites/blocks/turrets/glow-turret-heat.png  // 开火余热（同尺寸、透明底）
sprites/blocks/turrets/glow-turret-base.png  // 可选底座
sprites/blocks/turrets/glow-turret-top.png   // 可选顶层
```

`DrawTurret` 自动查找规则：

```java
preview = name + "-preview";
outline = name + "-outline";
liquid  = name + "-liquid";
top     = name + "-top";
heat    = name + "-heat";
base    = name + "-base";
```

---

## 4. 常见问题

### 4.1 设置了 `heatColor` 但炮塔不亮

检查：

1. 是否真的有 `blockname-heat.png`？
2. `-heat` 是否是透明背景、和炮塔主体同尺寸？
3. `cooldownTime` 是否太小，开火后马上归零？
4. 是否被自定义 `drawer` 覆盖，没有调用 `DrawTurret.drawHeat()`？

### 4.2 设置了 `emitLight` 但没有地面光

- 只写 `emitLight=true` 对已有 `Build` 的炮塔通常不够；
- 在 `Build.drawLight()` 里调用 `Drawf.light(...)`；
- 确认 `lightClipSize` 足够大，别被光照裁剪掉；
- 确认游戏画质/光照设置没有关闭灯光。

### 4.3 子弹不亮

- `lightRadius` 是否 `<= 0`？
- `lightOpacity` 是否 `<= 0`？
- 是否重写了 `drawLight` 但没有实际调用 `Drawf.light`？
- 是否被 `BulletType` 的自定义 `draw` 覆盖，没有调用 `drawLight`？

### 4.4 太亮/发白

降低顺序：

1. `lightOpacity`（子弹）；
2. `emitLight` 的 `lightColor.a`；
3. `lightRadius`；
4. `-heat` 贴图的白色区域面积；
5. `shootEffect` 的闪光强度。

### 4.5 性能

- 每颗子弹都开一盏大范围动态光，数量多时开销明显；
- 小弹丸不必给 `lightRadius` 太大，`30~50` 足够；
- 大量同屏子弹时优先用较窄的 `lightRadius` + 较高 `lightOpacity`，而不是反过来；
- `lightClipSize` 只影响裁剪范围，不要无脑设几百。

---

## 5. 推荐起步参数

### 炮塔

```java
heatColor = Color.valueOf("ffd27a");
cooldownTime = 45f;

emitLight = true;
lightColor = Color.valueOf("ffb84d");
lightColor.a = 0.6f;
lightRadius = 80f;
lightClipSize = 200f;
```

### 子弹

```java
lightColor = Color.valueOf("ffd166");
lightRadius = 70f;
lightOpacity = 0.7f;
```

先按这组参数看效果，再按“炮塔余热 / 炮口闪光 / 子弹光”分别微调，避免一次改四个变量导致无法判断是哪一项造成的。

---

## 6. 附：`lightColor.a` 到底是什么

`Color` 是 RGBA 四个分量，`.a` 就是 **alpha（透明度）分量**，范围 0~1。  
`Color.valueOf("ffd27a")` 得到的是 `a = 1` 的颜色；`lightColor.a = 0.8f` 只是把这个 Color 对象的 alpha 改成 0.8。

但它**是否影响灯光，取决于调用者有没有把它当作 opacity 传进去**：

- **Block 环境光路径**：`Block.drawEnvironmentLight()` 是这么写的：
  ```java
  Drawf.light(tile.worldx(), tile.worldy(), lightRadius, lightColor, lightColor.a);
  ```
  这里 alpha 被显式当成强度参数传入，所以 `lightColor.a` 确实影响亮度。

- **Bullet 路径**：`BulletType.drawLight()` 传的是 `lightOpacity`：
  ```java
  Drawf.light(b, lightRadius, lightColor, lightOpacity);
  ```
  而 `LightRenderer.add` 只取颜色的 `r/g/b` 和 opacity 参数，
  **颜色自身的 `.a` 被忽略**。所以给 `bullet.lightColor.a` 设值，不会改变子弹亮度；
  子弹亮度看 `lightOpacity`。

- **Turret heat 路径**：`DrawTurret.drawHeat()` 会写：
  ```java
  block.heatColor.write(Tmp.c1).a(build.heat)
  ```
  alpha 被 `build.heat` 覆盖，`heatColor.a` 在这里无效。

- **你自己写 `Drawf.light(...)` 时**：如果你把 `lightColor.a` 作为最后一个参数传进去，
  它当然会生效，因为那是你自己的代码在用它。

结论：

> `lightColor.a` 不是全局的“灯光强度开关”，它只是 Color 的 alpha 分量。  
> 不同灯光路径是否使用它，由路径本身决定。

调亮度时按路径看：

| 路径 | 真正的亮度参数 |
|---|---|
| 炮塔开火余热 | `build.heat` / `cooldownTime` / `heatColor` 的 RGB |
| 炮塔环境光（原版路径） | `lightColor.a`（被当作 opacity 传入） |
| 炮塔环境光（自定义 `drawLight`） | 你传入 `Drawf.light` 的最后一个参数 |
| 子弹光 | `lightOpacity` |
| 炮口闪光 | `Effect` 里你传给 `Drawf.light` 的 opacity |
