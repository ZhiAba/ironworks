# Mindustry 160.x 单位武器视觉 / 拼接 / 旋转 / 开火底层参数报告

> 目标：给一个 **188 × 220 像素** 的巨型单位主炮提供可直接查询的底层参数解释。
> 依据：`/root/api/Mindustry-src-160.1/Mindustry-160.1/core/src/mindustry` 源码，
> 主要文件为 `type/UnitType.java`、`type/Weapon.java`、`entities/units/WeaponMount.java`、
> `entities/part/DrawPart.java`、`entities/part/RegionPart.java`、`entities/pattern/ShootPattern.java`。
> 行号会随版本轻微变化；本文以 160.1 源码的调用关系为准。

---

## 0. 先给结论

1. **炮塔的视觉中心，不是贴图左上角。**
   `Weapon.region` 使用 `Draw.rect(region, wx, wy, rotation)` 绘制，锚点是 **纹理中心**。
   你的 188 × 220 主炮，默认旋转轴就是纹理中心 `(94, 110)`；如果底座转轴不在中心，
   主贴图本身没有 `originX/originY` 可用，必须重排透明留白，或把炮塔拆成 `RegionPart`。

2. **`Weapon.x / y` 是武器中心相对单位中心的偏移。**
   `x` 是横向，`y` 是纵向/炮管轴方向；不是像素，是世界单位。
   Mindustry 标准素材是 4 像素 = 1 世界单位：
   - 188 px 宽 ≈ 47 世界单位
   - 220 px 高 ≈ 55 世界单位
   - 40 px 偏移 = 10 世界单位

3. **`shootX / shootY` 是炮口相对武器中心的偏移。**
   `shootY` 主要就是炮管长度/炮口前伸距离；`shootX` 是横向炮口偏移。
   它们只影响弹丸和炮口特效的生成点，**不参与炮塔贴图的绘制位置**。

4. **`recoil` 是视觉位移，不是 `shootY`。**
   开火时 `mount.recoil = 1`，绘制时沿炮管轴向反方向移动
   `pow(mount.recoil, recoilPow) * recoil` 世界单位。
   它不会改变 `shootX/shootY` 的弹道生成点，所以大后坐力时贴图会缩回，但弹道仍从原炮口出。

5. **一个主炮不要误开 `mirror`。**
   `mirror` 默认 `true`，`UnitType.init()` 会自动复制一把镜像武器，并把
   **两把**武器的 `reload` 和 `recoilTime` 都翻倍。巨型主炮通常应设 `mirror=false`。

6. **`layerOffset` 是相对的 z 偏移，不是绝对图层号。**
   `top` 控制的是轮廓/炮塔覆盖的绘制时机，不是“是否画武器”的开关。

7. **`rotate` 控制独立瞄准旋转；`baseRotation` 控制固定基准角。**
   `rotate=true` 时 `mount.rotation` 相对单位朝向转；`rotate=false` 时固定为
   `baseRotation`，但弹丸仍可按瞄准点修正方向。

---

## 1. 坐标系与单位换算

### 1.1 世界坐标

Mindustry 世界坐标以屏幕像素/世界单位混合使用：

- 1 个 tile = 8 个世界单位（`Vars.tilesize = 8`）。
- 标准游戏素材通常为 4 像素 / 世界单位。
- `Draw.rect(region, x, y, rotation)` 的 `x/y` 是世界坐标，不是贴图像素坐标。
- 纹理会按 `TextureRegion.scl()` 处理素材缩放；在标准 Mindustry atlas 下，
  一个 188 × 220 的 unit 武器贴图可视尺寸约为 **47 × 55 世界单位**。

> 如果你的 PNG 是按 1 像素 = 1 世界单位绘制的，它不会被自动缩成 1/4；
> 素材必须遵守项目的 atlas 分辨率约定。Mindustry 的原始素材通常是 4x。

### 1.2 Unit 局部坐标

Weapon 的位置不是从贴图左上角算，而是从 **单位中心** 算：

```java
float rotation = unit.rotation - 90f;
float mountX = unit.x + Angles.trnsx(rotation, x, y);
float mountY = unit.y + Angles.trnsy(rotation, x, y);
```

`Weapon.x/y` 在源码中的语义：

- `x`：武器中心相对单位中心的 **横向** 偏移；
- `y`：武器中心相对单位中心的 **纵向/炮管轴方向** 偏移；
- 两者都是世界单位；
- `WeaponMount` 创建后其 `rotation` 初始值为 `weapon.baseRotation`，见
  `WeaponMount(Weapon weapon)`。

注意：Weapon 的 `x/y` 定位的是 **武器贴图中心**，不是炮管根部，也不是贴图左上角。
因此大贴图不要用“贴图左上角对齐船头”的思路，而要用“贴图中心对齐转轴”的思路。

### 1.3 `Angles.trnsx/trnsy` 的真实数学

源码调用等价于把局部向量 `(x, y)` 旋转角度 `a`：

```text
trnsx(a, x, y) = x * cos(a) - y * sin(a)
trnsy(a, x, y) = x * sin(a) + y * cos(a)
```

`Weapon.draw()` 先令 `rotation = unit.rotation - 90f`，所以：

```text
mountX = unit.x + x*cos(r) - y*sin(r)
mountY = unit.y + x*sin(r) + y*cos(r)
```

在 Mindustry 的 Weapon 语义里，`y` 是炮管轴，`shootY` 是沿炮管轴向前量；
`x` 是横向，`shootX` 是横向炮口偏移。`mirror` 的 `flip()` 只取反
`x`、`shootX` 和 `baseRotation`，不会取反 `y`、`shootY`，这也说明
`y/shootY` 是“纵向炮管轴”。

---

## 2. 一个 188 × 220 主炮的锚点与尺寸

### 2.1 主贴图旋转轴 = 纹理中心

`Weapon.draw()` 的核心绘制调用是：

```java
if(region.found()) Draw.rect(region, wx, wy, weaponRotation);
```

`Draw.rect` 默认以传入的 `(wx, wy)` 为 **纹理中心**，而不是左上角。
所以你的 188 × 220 图像：

```text
纹理中心 = (188/2, 220/2) = (94, 110) 像素
世界中心坐标 = (47, 55) 世界单位
```

`Weapon.x/y` 对齐的，是这个 **中心点**。

### 2.2 如果底座转轴不在贴图中心

悲剧点：`Weapon.region` **没有** `originX/originY` 参数。
`Weapon.draw()` 直接 `Draw.rect(region, wx, wy, rotation)`，旋转轴固定为纹理中心。

因此：

- 如果炮塔的底座转轴在图像中心：直接可用。
- 如果转轴在底部中心（例如 `(94, 220)`）：**不能**只靠 `Weapon.x/y` 修正。
  因为 `x/y` 只移动中心点，旋转时整张图仍绕中心转，炮管会绕错点。
- 正确做法任选：
  1. 在 PNG 中加透明留白，把底座转轴放到图像中心；
  2. 把主贴图拆成 `RegionPart`，使用 `originX/originY`；
  3. 底座作为单独 `RegionPart`，炮管也作为 `RegionPart`/独立武器。

### 2.3 `RegionPart` 的 origin

`RegionPart` 支持：

```java
public float originX, originY;
```

`RegionPart.rect(...)` 会用 `originX/originY` 修正绘制原点，并在 `moves`、`children`
中保留局部坐标。对于巨型炮塔，推荐：

- 一个不旋转或低转角的底座 `RegionPart`；
- 一个可旋转的炮管 `RegionPart`；
- 用 `PartProgress.recoil` 的 `PartMove` 或 `moveX/moveY` 做炮管后坐；
- 用 `under=true` 把某些部分画在主武器区域下方；
- 用 `weaponIndex` 选择跟随哪个 weapon 的进度。

`RegionPart` 的 `x/y` 是相对于 weapon mount 的局部坐标；`moves` 的
`x/y/gx/gy/rot` 会按各自的 `progress` 叠加；`mirror=true` 时同 parts 可镜像绘制。
`RegionPart.load()` 在 weapon 加载时会自动查找 `name + suffix`、`-heat`、`-light`、
`-outline` 等区域。

### 2.4 尺寸建议

如果 188 × 220 是 4x 素材：

```text
47 × 55 世界单位
≈ 5.9 × 6.9 tile
```

这个尺寸作为“单位主炮”很大，`hitSize` 不是自动由贴图决定的；
`hitSize` 只影响单位碰撞、z、阴影、瞄准距离等。视觉大小由 `region` 原始尺寸和
atlas scale 决定，二者不要混为一谈。

---

## 3. `x / y / shootX / shootY` 的精确计算

### 3.1 mount 中心

```java
float r = unit.rotation - 90f;
float mountX = unit.x + Angles.trnsx(r, x, y);
float mountY = unit.y + Angles.trnsy(r, x, y);
```

例子（只用于说明方向，最终以单位贴图正面为准确认）：

- 主炮在船头，需要相对单位中心向炮管轴前方移动 `d` 世界单位：
  `y = d`，`x = 0`。
- 两门对称副炮，左右各 `s` 世界单位：
  一门 `x = +s`，另一门 `x = -s`；`y` 相同。
- 主炮在船头偏右 `s`，同时向前 `d`：`x = s`，`y = d`。

你的素材：如果单位中心到船头安装点是 `30 px`，则：

```text
30 px / 4 = 7.5 世界单位
weapon.x = 0
weapon.y = 7.5
```

### 3.2 炮口位置

`Weapon.update()` 和 `Weapon.bullet()` 都使用：

```java
float weaponRotation = unit.rotation - 90f
    + (rotate ? mount.rotation : baseRotation);

float mountX = unit.x + Angles.trnsx(unit.rotation - 90f, x, y);
float mountY = unit.y + Angles.trnsy(unit.rotation - 90f, x, y);

float bulletX = mountX + Angles.trnsx(
    weaponRotation,
    this.shootX + xOffset + xSpread,
    this.shootY + yOffset + ySpread
);
float bulletY = mountY + Angles.trnsy(
    weaponRotation,
    shootX + xOffset + xSpread,
    shootY + yOffset + ySpread
);
```

含义：

- `shootY` 是沿炮管轴的前伸距离；炮口在主炮中心前方 `d` 世界单位时设为 `d`。
- `shootX` 是炮口横向偏移，常用于双管/偏置炮口。
- `xOffset/yOffset` 来自 `shoot.shoot(...)` 的 shot pattern；
  它们会经过同一个 weapon rotation 变换。
- `xSpread/ySpread` 来自 `xRand/yRand`，是随机散布，不是固定装配偏移。
- `shootY` 也会被 `findTarget()` 加到索敌距离里：
  `range + Math.abs(shootY)`。

### 3.3 188 × 220 下的炮口计算

假设：

- 主炮贴图中心就是底座转轴；
- 炮管朝图像上方；
- 炮口在贴图上缘中心，即相对中心 `(0, -110px)`；
- 4 px = 1 世界单位。

那么炮口前伸：

```text
110 / 4 = 27.5 世界单位
shootX = 0
shootY = 27.5
```

如果炮口在图像上方再超出 20 px，则：

```text
shootY = (110 + 20) / 4 = 32.5
```

如果炮口不是正中，而是横向偏 `12px`：

```text
shootX = 12 / 4 = 3.0
shootY = 27.5
```

`shootY` **不会**让贴图前移；只让弹丸/炮口特效在视觉炮口处生成。

### 3.4 炮口与 recoil 的错位

`Weapon.bullet()` 不读取 `mount.recoil` 来修正炮口，所以：

- 后坐力让贴图缩回；
- 弹丸仍从 `shootX/shootY` 的固定位置生成。

这在大后坐力 + 长炮管时会明显“弹道从缩回前的炮口出来”。
解决办法：

- 把 `recoil` 设小；
- 或用 `parts` 的 `recoilIndex` 只让炮管段动，而 `shootY` 用基准位置；
- 或在自定义 `Weapon` 子类中重写 `bullet()`/`shoot()` 计算动态炮口（高级）。

---

## 4. 视觉绘制顺序与层级

### 4.1 `UnitType.draw()` 的顺序

`UnitType.draw(Unit unit)` 的关键顺序：

```text
drawShadow / soft shadow
drawMech / tank / legs / payload / crawl
drawWeaponOutlines(unit)        // 只处理 top=false 的武器轮廓
drawBody(unit)                  // 本体 region
drawCell(unit)                  // cellRegion
drawWeapons(unit)               // 所有 Weapon.draw()
drawItems(unit)
drawLight / shield
```

因此：

- **所有武器的 `region` 都在本体之后绘制**，`top` 不改变这一点；
- `top` 主要改变 `drawOutline()` 的时机：
  - `top=false`：轮廓在 `drawWeaponOutlines()` 中、本体之前绘制；
  - `top=true`：轮廓在 `Weapon.draw()` 里、当前武器层绘制。
- `drawWeaponOutlines()` 只遍历 `!top` 的武器；
  `Weapon.draw()` 自己只对 `top=true` 调用 `drawOutline()`。

### 4.2 z 的计算

在 `UnitType.draw()` 中：

```java
float z = isPayload ? Draw.z() :
    (unit.elevation > 0.5f || (flying && unit.dead)) ? flyingLayer :
    seg != null ? groundLayer + seg.segmentIndex()/4000f ... :
    groundLayer + Mathf.clamp(hitSize / 4000f, 0f, 0.01f);
```

武器绘制时：

```java
float z = Draw.z();
Draw.z(z + layerOffset); // Weapon.draw 内部
...
Draw.z(z); // restore
```

- `layerOffset` 是 **相对当前 z 的浮点偏移**，不是 `Layer.turret` 之类的绝对层号。
- 因为武器区域本来就在 body 之后绘制，`layerOffset` 主要用于同深度批次排序/覆盖；
  对大炮，`0.01f` 量级通常足以压过本体，不要随手写 `10f`。
- 如果武器有 `parts`，`parts.under` 的部件在武器 region 前绘制；
  非 `under` 的部件在 region/cell/heat 之后绘制。
- `Weapon.shadow` 使用 `Drawf.shadow(wx, wy, shadow)`；`shadow < 0` 禁用，
  `shadow` 是世界半径（不是像素）。
- `cellRegion` 与 `region` 尺寸应一致；它用于队伍/生命色覆盖。
- `heatRegion` 也应与 `region` 同尺寸；开火时按 `mount.heat` 做 additive 绘制。

### 4.3 `Weapon.draw()` 的完整顺序

```text
Draw.z(z + layerOffset)
if shadow > 0: Drawf.shadow(wx, wy, shadow)
if top: drawOutline(unit, mount)
for parts where under: part.draw(...)
Draw.xscl *= -sign(flipSprite)
applyColor(unit)
Draw.rect(region, wx, wy, weaponRotation)
if cellRegion: Draw.rect(cellRegion, ...)
if heatRegion && heat > 0: additive heat
Draw.xscl restore
for parts where !under: part.draw(...)
Draw.z(z)
```

这意味着：

- `Weapon.region` 一定以 **中心** 为锚点；
- `flipSprite` 是 `mirror` 复制时自动设置的内部字段，**不要手动设置**；
- `mirror` 的复制体会把 `flipSprite=true`，绘制时水平翻转；
- `parts` 也可以 `under=true` 或 `mirror=true` 做复杂武器。

### 4.4 `layerOffset` 和 `top` 的常见误区

- “让炮塔永远盖在本体上”：武器本身已在 body 之后绘制；需要更强覆盖时加
  `layerOffset` 少量正值即可。
- “`top=true` 就会在 body 上”：不是。`top` 控制轮廓；regions 始终在 body 后。
- “`layerOffset` 越大越安全”：会压到子弹/特效/其它单位，反而破坏层次；
  先用 `0.01f` 级别，按实机调。
- `top=false` 不是“不画顶层”，只是轮廓提前画，适合底座被本体遮住的武器。

---

## 5. 旋转与运动学

### 5.1 相关字段

| 字段 | 默认值 | 作用 |
|---|---:|---|
| `rotate` | `false` | 是否独立朝目标旋转 |
| `rotateSpeed` | `20f` | 旋转角速度，单位：度/tick（60 tick/s） |
| `baseRotation` | `0f` | 固定基准角；`rotate=false` 时直接作为炮塔角 |
| `rotationLimit` | `361f` | 相对 baseRotation 的限位；`<360` 才生效 |
| `shootCone` | `5f` | 发射前允许的角度误差 |
| `controllable` | `true` | 玩家/逻辑是否可控制 |
| `aiControllable` | `true` | AI 是否可控制 |
| `autoTarget` | `false` | `controllable=false` 时自动找目标 |
| `predictTarget` | `true` | 是否预测目标位置 |
| `targetInterval` | `40f` | 无目标时的重索敌间隔 |
| `targetSwitchInterval` | `70f` | 有目标时的切换间隔 |
| `ignoreRotation` | `false` | AI 是否忽略单位朝向（只影响 AI 逻辑） |

### 5.2 `rotate` 的精确更新

`Weapon.update()` 中：

```java
if(rotate && (mount.rotate || mount.shoot) && can){
    float axisX = unit.x + Angles.trnsx(unit.rotation - 90f, x, y);
    float axisY = unit.y + Angles.trnsy(unit.rotation - 90f, x, y);

    mount.targetRotation = Angles.angle(axisX, axisY, mount.aimX, mount.aimY)
        - unit.rotation;

    mount.rotation = Angles.moveToward(
        mount.rotation, mount.targetRotation, rotateSpeed * Time.delta
    );

    if(rotationLimit < 360f){
        float dst = Angles.angleDist(mount.rotation, baseRotation);
        if(dst > rotationLimit / 2f){
            mount.rotation = Angles.moveToward(
                mount.rotation, baseRotation, dst - rotationLimit / 2f
            );
        }
    }
}else if(!rotate){
    mount.rotation = baseRotation;
    mount.targetRotation = unit.angleTo(mount.aimX, mount.aimY);
}
```

解释：

- `mount.rotation` 是 **相对单位朝向** 的角度，不是世界绝对角度。
- `targetRotation = 世界瞄准角 - unit.rotation`；所以 0 表示“和单位朝向一致”。
- `rotateSpeed` 每 tick 改变多少度；默认 20°/tick 非常快，大炮建议 `1~4`。
- `baseRotation` 是固定炮塔的偏转角；例如侧舷炮可设 `90f` 或 `-90f`。
- `rotationLimit` 只在 `rotate=true` 且 `<360` 时生效；默认 `361` 等价于不限位。
- `rotate=false` 时，`mount.rotation` 固定为 `baseRotation`，但 `targetRotation`
  仍记录目标方向，发射逻辑会用它检查 `shootCone`。

### 5.3 绘制旋转

```java
float weaponRotation = unit.rotation - 90f
    + (rotate ? mount.rotation : baseRotation);
```

所以：

- `baseRotation=0` 是“与单位朝向一致”的固定炮；
- 侧向固定炮：`baseRotation=±90`；
- 后向固定炮：`baseRotation=180`；
- 独立旋转炮：`rotate=true`，`mount.rotation` 在 `baseRotation` 附近被限位。

### 5.4 子弹方向

`Weapon.bulletRotation()`：

```java
return rotate
    ? unit.rotation + mount.rotation
    : Angles.angle(bulletX, bulletY, mount.aimX, mount.aimY)
        + (unit.rotation - unit.angleTo(mount.aimX, mount.aimY))
        + baseRotation;
```

- `rotate=true`：弹丸方向 = 单位朝向 + mount 相对角；
- `rotate=false`：弹丸方向会按瞄准点修正，再叠加 `baseRotation`；
- 随后 `Weapon.bullet()` 再加 `angleOffset` 和 `inaccuracy`、
  `bullet.inaccuracy`。
- `shootCone` 检查使用：
  ```java
  Angles.within(
      rotate ? mount.rotation : unit.rotation + baseRotation,
      mount.targetRotation,
      shootCone
  )
  ```
  对于固定炮，注意这里是 `unit.rotation + baseRotation`，不是 `weaponRotation`。

---

## 6. 后坐力

### 6.1 视觉后坐力

字段：

| 字段 | 默认值 | 说明 |
|---|---:|---|
| `recoil` | `1.5f` | 满后坐时的视觉位移，世界单位 |
| `recoilTime` | `-1f` | 回复时间；`<0` 在 `UnitType.init()` 设为 `reload` |
| `recoilPow` | `1.8f` | 后坐力曲线幂 |
| `recoils` | `-1` | 独立后坐计数器数量（多炮管） |

开火时：

```java
mount.recoil = 1f;
```

更新时：

```java
mount.recoil = Mathf.approachDelta(
    mount.recoil, 0f, unit.reloadMultiplier / recoilTime
);
```

绘制时：

```java
float realRecoil = Mathf.pow(mount.recoil, recoilPow) * recoil;
float wx = mountX + Angles.trnsx(weaponRotation, 0f, -realRecoil);
float wy = mountY + Angles.trnsy(weaponRotation, 0f, -realRecoil);
```

- `recoil` 是满后坐时的位移；不是“0~1 的比例”。
- 位移沿炮管轴的 **反方向**（`-recoil` 的局部 y）。
- `recoilPow` 越大，后坐前半段变化越快/曲线越陡（按幂函数）。
- `recoilTime` 越小，回位越快；设得比 `reload` 大，会看到长时间缩回。
- `recoils > 0` 时 `mount.recoils[]` 独立衰减；配合 `parts.recoilIndex`
  可以让多根炮管错开。
- `recoil` **不会**改变 `shootY` 的炮口位置（见 §3.4）。

### 6.2 不要把 `Weapon.recoil` 和 `BulletType.recoil` 混了

- `Weapon.recoil`：视觉后坐，`mount.recoil`，只影响贴图。
- `BulletType.recoil`：物理后坐，开火时给单位一个反向速度：
  ```java
  unit.vel.add(Tmp.v1.trns(shootAngle + 180f, bullet.recoil));
  ```
  它会让船体后退，和炮塔贴图缩回不是同一个东西。

### 6.3 大炮建议

- 想限制“缩进船体”：把 `recoil` 降到 `1~3` 世界单位；
- 想保留机械感但不要离谱：`recoilTime` 取 `reload` 的 0.5~1 倍，
  `recoilPow` 取 `1.2~1.8`；
- 不要为了视觉把 `recoil` 设成几十；它会把炮塔整个拉出底座。
- 需要“炮管分段缩回”而不是整张贴图缩回，用 `parts` +
  `PartProgress.recoil` / `recoilIndex`。

---

## 7. `mirror`、`alternate` 与多武器

### 7.1 `UnitType.init()` 会自动复制镜武器

这是最容易踩坑的地方。源码逻辑：

```java
Seq<Weapon> mapped = new Seq<>();
for(Weapon w : weapons){
    if(w.recoilTime < 0) w.recoilTime = w.reload;
    mapped.add(w);

    if(w.mirror){
        Weapon copy = w.copy();
        copy.flip();
        mapped.add(copy);

        w.recoilTime *= 2f;
        copy.recoilTime *= 2f;
        w.reload *= 2f;
        copy.reload *= 2f;

        w.otherSide = mapped.size - 1;
        copy.otherSide = mapped.size - 2;
    }
}
this.weapons = mapped;
```

结论：

- `mirror=true`（默认）会把一个 Weapon 变成 **两个 mount**；
- 两个 mount 的 `reload` 和 `recoilTime` 都被 **翻倍**；
- 两个 mount 的 `x`、`shootX`、`baseRotation` 被取反；
- `y`、`shootY` 不取反（纵向轴不变）；
- 如果只想一门主炮：显式 `mirror = false`，否则会左右各一门且装填变慢一倍。

### 7.2 `Weapon.flip()` 到底改了什么

```java
public void flip(){
    x *= -1;
    shootX *= -1;
    baseRotation *= -1f;
    flipSprite = !flipSprite;
    shoot = shoot.copy();
    shoot.flip();
}
```

不改：

- `y`；
- `shootY`；
- `reload`（翻倍发生在 `UnitType.init` 外层）；
- `rotate`；
- `recoil`（各自独立但同值）；
- `layerOffset`；
- `top`。

`flipSprite` 是内部字段；源码明确标注 `internal use only - do not set!`。
不要手动写 `flipSprite = true`，让 `mirror` 机制处理。

### 7.3 `alternate` 的真实行为

`alternate` 默认 `true`，且只有镜像对（`otherSide >= 0`）才有意义：

```java
boolean wasFlipped = mount.side;

if(otherSide >= 0 && alternate
    && mount.side == flipSprite
    && otherSide < unit.mounts.length
    && mount.reload <= reload / 2f
    && lastReload > reload / 2f){
    unit.mounts[otherSide].side = !unit.mounts[otherSide].side;
    mount.side = !mount.side;
}

// 发射条件中包含：
(!alternate || wasFlipped == flipSprite)
```

- `alternate=true`：左右炮交替开火；
- `alternate=false`：左右炮同时开火；
- 对 `mirror=false` 的单独武器，没有 `otherSide`，`alternate` 实际上不会产生交替效果。

### 7.4 推荐配置

**单门巨型主炮：**

```java
new Weapon("huge-main-cannon"){{
    mirror = false;       // 关键：不要复制，也不要 reload 翻倍
    rotate = true;
    rotateSpeed = 2f;     // 度/tick，巨大炮塔别用 20
    baseRotation = 0f;
    recoil = 2.5f;        // 视觉后坐，世界单位
    recoilTime = 30f;
    shootY = 27.5f;       // 由 188x220 素材计算
    x = 0f; y = 7.5f;     // 单位中心 -> 船头安装点
    top = true;
    layerOffset = 0.01f;
    reload = 90f;
}};
```

**左右对称副炮（真正镜像、同步）：**

```java
new Weapon("secondary"){{
    mirror = true;        // 自动生成左右两门
    alternate = false;    // 同时开火；设 true 则交替
    x = 12f;              // 横向偏移，另一侧自动 -12
    y = 4f;
    shootY = 10f;
    rotate = true;
    rotateSpeed = 5f;
    reload = 40f;         // 注意：两门各自会变成 80f
}};
```

**手动对称但不想被 `mirror` 翻倍 reload：**

```java
weapons.add(new Weapon("secondary"){{ mirror = false; x = 12f; y = 4f; ... }});
weapons.add(new Weapon("secondary"){{ mirror = false; x = -12f; y = 4f; ... }});
```

两门武器是独立 mount，不再有自动 `otherSide`/`alternate` 关系。

### 7.5 多武器阵列

- `UnitType.weapons` 是 `Seq<Weapon>`；`UnitType.init()` 会按顺序展开镜像；
- `WeaponsComp.setupWeapons` 为每个展开后的 weapon 创建一个 `WeaponMount`；
- `unit.mounts` 的顺序就是最终绘制/更新顺序；
- `WeaponMount.barrelCounter`、`totalShots`、`side` 是运行时状态；
- 不同 weapon 的 `rotate` 独立，瞄准点可能相同但旋转速度不同；
- 多武器时 `shootCone`、`rotationLimit` 各自判断。

---

## 8. 开火时序、弹丸与特效

### 8.1 reload / warmup / heat

| 字段 | 作用 |
|---|---|
| `reload` | 装填帧数；0 表示可立即再开火 |
| `cooldownTime` | 热量冷却到 0 的时间 |
| `shootWarmupSpeed` | warmup 插值速度；只影响 parts 动画 |
| `smoothReloadSpeed` | smoothReload 插值速度 |
| `minWarmup` | 允许开火的最小 warmup |
| `linearWarmup` | warmup 线性/曲线 |
| `alwaysContinuous` | 无 reload 持续开火（隐含 continuous） |
| `continuous` | 保持弹丸并持续更新 |
| `alwaysShooting` | 强制 shoot 状态 |
| `shootCone` | 允许开火的角度差 |
| `inaccuracy` | 每发随机角度 |
| `shoot.shots` | 每次触发的弹丸数量 |
| `shoot.firstShotDelay` | 第一发延迟 |
| `shoot.shotDelay` | 多发间隔 |
| `minShootVelocity` / `maxShootVelocity` | 单位速度限制 |
| `bullet.killShooter` | 只能打一发 |

`Weapon.update()` 每帧大致做：

1. 减少 `mount.reload`；
2. 衰减 `mount.recoil`；
3. 更新 `smoothReload`；
4. 更新 `warmup`；
5. 计算 mount 位置；
6. 旋转/瞄准；
7. 计算 bulletX/Y；
8. 更新 continuous bullet；
9. 检查 `shootCone`、reload、warmup、速度；
10. 调 `shoot()`。

### 8.2 `shoot()` 与 `bullet()`

`Weapon.shoot()`：

- 应用 `shootStatus`；
- 如果有 `firstShotDelay`，进入 charging，播放 `chargeSound`/`chargeEffect`；
- 调用 `shoot.shoot(mount.barrelCounter, handler)`；
- `handler` 为每个 shot 创建 bullet；
- `mount.totalShots++`，更新 `barrelCounter`。

`Weapon.bullet()`：

```java
mountX = unit.x + Angles.trnsx(unit.rotation - 90f, x, y);
mountY = unit.y + Angles.trnsy(unit.rotation - 90f, x, y);

bulletX = mountX + Angles.trnsx(
    weaponRotation,
    shootX + xOffset + xSpread,
    shootY + yOffset + ySpread
);
bulletY = ...;

shootAngle = bulletRotation(unit, mount, bulletX, bulletY)
    + angleOffset
    + Mathf.range(inaccuracy + bullet.inaccuracy);
```

- `ejectEffect` 在 **mount 中心**，角度使用 `angle * Mathf.sign(this.x)`；
- `bullet.shootEffect`/`bullet.smokeEffect` 在 **炮口**；
- `shootSound` 在炮口位置；
- `mount.recoil = 1`；
- `mount.heat = 1`；
- `unit.vel.add(... bullet.recoil)`（物理后坐）。

### 8.3 点激光/持续武器

- `aimChangeSpeed`：点激光瞄准长度变化速度；
- `mount.lastLength`：连续武器当前长度；
- `continuous`：`mount.bullet` 持续存在，跟随 weapon mount；
- `handleBullet()` 里用 `shootY + mount.lastLength` 计算终点；
- 大炮若是持续激光，`shootY` 是起点，终点由 aim 距离决定。

### 8.4 其余会影响射击/表现的字段

| 字段 | 默认值 | 作用 |
|---|---:|---|
| `xRand / yRand` | `0 / 0` | 炮口随机散布；不是安装偏移 |
| `velocityRnd` | `0` | 子弹速度随机比例 |
| `extraVelocity` | `0` | 额外速度比例 |
| `lifeRnd` | `0` | 子弹寿命随机比例 |
| `extraLife` | `0` | 额外寿命比例 |
| `shake` | `0` | 开火屏幕震动 |
| `soundPitchMin/Max` | `0.8/1.0` | 开火音高随机范围 |
| `shootSound` | `Sounds.shoot` | 开火音效 |
| `activeSound` | `Sounds.none` | 持续开火循环音 |
| `initialShootSound` | `Sounds.none` | 持续武器第一次开火音 |
| `chargeSound` | `Sounds.none` | 蓄力音 |
| `parentizeEffects` | `false` | 特效是否跟随单位 |
| `shootOnDeath` | `false` | 死亡时是否开火 |
| `shootOnDeathEffect` | `null` | 死亡开火特效覆盖 |
| `noAttack` | `false` | 是否禁止攻击 |
| `display` | `true` | 是否出现在单位武器统计里 |
| `showStatSprite` | `true` | 数据库是否显示武器贴图 |
| `useAttackRange` | `true` | 是否参与攻击范围计算 |
| `mountType` | `WeaponMount::new` | 自定义 WeaponMount 工厂 |
| `shootStatus` | `none` | 开火时给自身/目标的状态（取决于实现） |
| `shootStatusDuration` | `300` | 状态持续时间（tick） |

### 8.5 武器贴图的自动加载规则

`Weapon.load()`：

```java
region = Core.atlas.find(name);
heatRegion = Core.atlas.find(name + "-heat");
cellRegion = Core.atlas.find(name + "-cell");
outlineRegion = Core.atlas.find(name + "-outline");
```

所以：

- `name` 必须是完整 atlas key，例如 `ship-main-cannon`；
- 热区/队伍色/轮廓分别查找 `ship-main-cannon-heat`、`-cell`、`-outline`；
- 文件名不要写成 `ship-main-cannon-weapon`，除非你的 atlas key 本来就是这个；
- `Weapon()` 无参构造的 `name` 为空字符串，必须显式赋值；
- `parts` 的 `RegionPart.load()` 会以同一个 `name` 为前缀查找部件图。

### 8.6 瞄准/自动索敌

- `controllable=true`：由玩家/逻辑控制；
- `aiControllable=false`：AI 不控制；
- `autoTarget=true` 且 `controllable=false`：自动索敌；
- `predictTarget=true`：使用 `Predict.intercept`；
- `targetInterval`/`targetSwitchInterval`：无目标/切目标间隔；
- `aimDst`（UnitType 根据是否有非旋转武器设为 `hitSize*2` 或 `hitSize/2`）：
  `WeaponsComp.aim()` 会把瞄准点从单位中心推到至少 `aimDst` 距离；
  这会改变近距离目标的瞄准角。

---

## 9. `UnitType` 中影响武器视觉的字段

| 字段 | 影响 |
|---|---|
| `region` | 单位本体贴图，居中绘制 |
| `outlineRegion` | 本体轮廓，`drawOutline` |
| `cellRegion` | 队伍/生命色覆盖层 |
| `softShadowRegion` / `drawSoftShadow` | 软阴影 |
| `shadowRegion` / `drawShadow` | 单位阴影 |
| `shadowElevation` | 阴影抬升；飞行/悬浮 |
| `shadowElevationScl` | 阴影缩放 |
| `drawBody` | 是否绘制本体 |
| `drawCell` | 是否绘制 cell 层 |
| `drawItems` | 是否绘制物品层 |
| `drawShields` | 是否绘制护盾 |
| `hitSize` | z 值、阴影、碰撞、aimDst；**不缩放贴图** |
| `groundLayer` | 地面单位 z 基线 |
| `flyingLayer` | 飞行单位 z |
| `engineLayer` | 引擎层；`>0` 时切换 z |
| `flying` / `lowAltitude` | 决定 flyingLayer |
| `outlineColor` / `applyColor` | 轮廓和染色 |
| `weapons` | 武器列表；init 时展开 mirror |
| `parts` | 单位自身 DrawPart，和 weapon parts 不同 |
| `drawWeaponOutlines` | 非 top 武器轮廓提前绘制 |
| `drawWeapons` | 所有武器绘制入口 |

`UnitType.drawBody`：

```java
Draw.rect(region, unit.x, unit.y, unit.rotation - 90f);
```

所以本体中心 = `unit.x/unit.y`，与 weapon 的 mount 中心是两套坐标。
`hitSize` 大不代表贴图一定大；贴图大小由 `region` 决定。

---

## 10. 188 × 220 主炮的推荐装配流程

### 10.1 先决定转轴

1. 主炮转轴在贴图正中心 `(94,110)`：可以只用 `Weapon.x/y`。
2. 转轴在底部：给 PNG 加透明留白，将转轴移到中心。
3. 必须保留底部转轴且不能改图：改用 `RegionPart` / 多武器拆分。

### 10.2 计算安装点

```text
像素偏移 / 4 = 世界单位
```

例如：

| 需求 | 像素 | 世界单位 | 字段 |
|---|---:|---:|---|
| 单位中心到船头 | 30 | 7.5 | `Weapon.y = 7.5` |
| 船头左右偏 | 24 | 6 | `Weapon.x = ±6` |
| 炮口到中心 | 110 | 27.5 | `shootY = 27.5` |
| 炮口横向偏 | 12 | 3 | `shootX = 3` |
| 后坐视觉 | 10 | 2.5 | `recoil = 2.5` |
| 阴影半径 | 100 | 25 | `shadow = 25` |

### 10.3 建议参数模板

```java
new Weapon("ship-main-cannon"){{
    name = "ship-main-cannon";   // 对应 sprites/units/weapons/ship-main-cannon.png

    mirror = false;              // 单门主炮
    rotate = true;               // 独立旋转
    rotateSpeed = 2f;            // 大炮慢转
    baseRotation = 0f;
    rotationLimit = 45f;         // 只在 rotate=true 时生效；不需要就删

    x = 0f;                      // 横向安装偏移
    y = 7.5f;                    // 纵向安装偏移：单位中心 -> 船头
    shootX = 0f;
    shootY = 27.5f;              // 炮口前伸

    recoil = 2.5f;
    recoilTime = 30f;
    recoilPow = 1.5f;

    top = true;
    layerOffset = 0.01f;
    shadow = 25f;

    reload = 90f;
    cooldownTime = 60f;
    shootCone = 10f;
    inaccuracy = 0f;

    bullet = new BasicBulletType(8f, 200){{
        // ...
    }};
}};
```

如果炮管很长、后坐时不想整张贴图缩进：

- 把炮管拆成 `RegionPart`；
- 用 `PartProgress.recoil` + `moves.add(new PartMove(...))` 只移动炮管；
- 主 region 只保留底座/固定结构；
- `recoil` 调小，`parts` 负责机械行程。

### 10.4 验证清单

1. 旋转 0/90/180/270：炮塔是否绕底座中心而不是贴图角落转。
2. 开火：炮口火焰是否在 `shootY` 对应的视觉炮口。
3. 后坐：炮塔是否只沿炮管轴后退，没有横向漂移。
4. 左右镜像：`mirror=true` 时两边是否对称、reload 是否被翻倍。
5. `alternate=true/false`：左右炮是否按预期交替/同步。
6. `layerOffset/top`：炮塔底座是否被船体正确遮挡/覆盖。
7. 多门副炮：`unit.mounts` 顺序与 `weapons` 顺序一致，瞄准方向是否互相独立。

---

## 11. 源码索引

| 机制 | 源码位置 |
|---|---|
| Weapon 字段 / 默认值 | `type/Weapon.java` 开头字段区 |
| Weapon.draw | `type/Weapon.java` `draw()` |
| Weapon.drawOutline | `type/Weapon.java` `drawOutline()` |
| Weapon.update | `type/Weapon.java` `update()` |
| Weapon 发射/炮口 | `type/Weapon.java` `shoot()` / `bullet()` |
| Weapon.flip | `type/Weapon.java` `flip()` |
| Weapon.init/load | `type/Weapon.java` 末段 |
| UnitType 武器镜像展开 | `type/UnitType.java` `init()` 的 `Seq<Weapon> mapped` |
| UnitType.draw | `type/UnitType.java` `draw()` |
| UnitType.drawWeapons | `type/UnitType.java` `drawWeapons()` |
| UnitType.drawWeaponOutlines | `type/UnitType.java` `drawWeaponOutlines()` |
| UnitType.drawBody | `type/UnitType.java` `drawBody()` |
| WeaponMount 状态 | `entities/units/WeaponMount.java` |
| ShootPattern | `entities/pattern/ShootPattern.java` |
| DrawPart | `entities/part/DrawPart.java` |
| RegionPart | `entities/part/RegionPart.java` |
| Omura 主炮样例 | `content/UnitTypes.java` 的 `omura-cannon` |
| Corvus 主炮样例 | `content/UnitTypes.java` 的 `corvus-weapon` |
| Aegires 多目标能力 | `content/UnitTypes.java` 的 `EnergyFieldAbility` |

---

## 12. 最后提醒

- `Weapon.x/y`、`shootX/Y`、`recoil`、`shadow` **都是世界单位**，不是像素。
- 188 × 220 像素的素材在标准 4x atlas 下约为 47 × 55 世界单位。
- 主 region 的旋转轴永远是纹理中心；转轴不在中心时，靠 `Weapon.x/y` 修不回来。
- 单门主炮务必 `mirror=false`；否则会多出一门镜像炮且 reload 翻倍。
- 不要手动设置 `flipSprite`；它是 `mirror` 的内部状态。
- 大后坐力时，贴图缩回而 `shootX/Y` 炮口不动；这是源码行为，需要 `parts` 或自定义 `Weapon` 解决。
