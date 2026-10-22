#!/bin/bash
# 产线行为回归测试（无头端实跑，不是看 UI）
# 用法：bash tools/chain-test.sh
# 依赖：/root/api/server-release-160.3.jar、JDK、out/ironworks.jar
set -u
JDK=$(cat /root/jdk.path)
TESTDIR=/root/test160
JAR=/root/api/server-release-160.3.jar
mkdir -p "$TESTDIR/config/mods"
cp /sdcard/dsh/mindustry-mod/out/ironworks.jar "$TESTDIR/config/mods/"

cd "$TESTDIR" || exit 1
(
  echo 'host Fortress sandbox'
  sleep 6

  # ---- 1. 金属轧制机：铜 → 铜板 ----
  echo 'js Vars.world.tile(39,40).setBlock(Vars.content.block("power-source"), Vars.state.rules.defaultTeam)'
  echo 'js Vars.world.tile(40,40).setBlock(Vars.content.block("ironworks-rolling-mill"), Vars.state.rules.defaultTeam)'
  sleep 1
  echo 'js Vars.world.tile(40,40).build.items.add(Vars.content.item("copper"), 20)'
  sleep 5
  echo 'js "T1 copper=" + Vars.world.tile(40,40).build.items.get(Vars.content.item("copper")) + " copperPlate=" + Vars.world.tile(40,40).build.items.get(Vars.content.item("ironworks-copper-plate"))'

  # ---- 2. 切配方：铅 → 铅板 ----
  echo 'js Vars.world.tile(40,40).build.items.clear()'
  echo 'js Vars.world.tile(40,40).build.recipeIndex = 1'
  echo 'js Vars.world.tile(40,40).build.items.add(Vars.content.item("lead"), 20)'
  sleep 5
  echo 'js "T2 lead=" + Vars.world.tile(40,40).build.items.get(Vars.content.item("lead")) + " leadPlate=" + Vars.world.tile(40,40).build.items.get(Vars.content.item("ironworks-lead-plate")) + " copperPlate=" + Vars.world.tile(40,40).build.items.get(Vars.content.item("ironworks-copper-plate"))'

  # ---- 3. 精炼槽：铜板 + 水 → 高纯铜 ----
  echo 'js Vars.world.tile(49,50).setBlock(Vars.content.block("power-source"), Vars.state.rules.defaultTeam)'
  echo 'js Vars.world.tile(50,50).setBlock(Vars.content.block("ironworks-electrolysis-plant"), Vars.state.rules.defaultTeam)'
  sleep 1
  echo 'js Vars.world.tile(50,50).build.recipeIndex = 1'
  echo 'js Vars.world.tile(50,50).build.items.add(Vars.content.item("ironworks-copper-plate"), 5)'
  echo 'js Vars.world.tile(50,50).build.liquids.add(Vars.content.liquid("water"), 40)'
  sleep 4
  echo 'js "T3 water=" + Math.round(Vars.world.tile(50,50).build.liquids.get(Vars.content.liquid("water"))) + " plate=" + Vars.world.tile(50,50).build.items.get(Vars.content.item("ironworks-copper-plate")) + " pure=" + Vars.world.tile(50,50).build.items.get(Vars.content.item("ironworks-pure-copper"))'

  # ---- 3b. 电解车间：电解水制氢氧 ----
  echo 'js Vars.world.tile(49,60).setBlock(Vars.content.block("power-source"), Vars.state.rules.defaultTeam)'
  echo 'js Vars.world.tile(50,60).setBlock(Vars.content.block("ironworks-electrolysis-plant"), Vars.state.rules.defaultTeam)'
  sleep 2
  echo 'js Vars.world.tile(50,60).build.recipeIndex = 0'
  echo 'js Vars.world.tile(50,60).build.liquids.add(Vars.content.liquid("water"), 40)'
  sleep 5
  echo 'js "T3b h2=" + Math.round(Vars.world.tile(50,60).build.liquids.get(Vars.content.liquid("hydrogen"))) + " o2=" + Math.round(Vars.world.tile(50,60).build.liquids.get(Vars.content.liquid("ironworks-oxygen"))) + " water=" + Math.round(Vars.world.tile(50,60).build.liquids.get(Vars.content.liquid("water")))'

  # ---- 4. 装桶：水 30 + 铜板 1 → 1 水桶（液体折算回归）----
  echo 'js Vars.world.tile(59,60).setBlock(Vars.content.block("power-source"), Vars.state.rules.defaultTeam)'
  echo 'js Vars.world.tile(60,60).setBlock(Vars.content.block("ironworks-barrel-filler"), Vars.state.rules.defaultTeam)'
  sleep 1
  echo 'js Vars.world.tile(60,60).build.items.add(Vars.content.item("ironworks-copper-plate"), 5)'
  echo 'js Vars.world.tile(60,60).build.liquids.add(Vars.content.liquid("water"), 60)'
  sleep 3
  echo 'js "T4 water=" + Math.round(Vars.world.tile(60,60).build.liquids.get(Vars.content.liquid("water"))) + " plate=" + Vars.world.tile(60,60).build.items.get(Vars.content.item("ironworks-copper-plate")) + " barrels=" + Vars.world.tile(60,60).build.items.get(Vars.content.item("ironworks-water-barrel"))'

  # ---- 5. 炮塔：喂弹 + 刷怪，验证真的开火 ----
  echo 'js Vars.world.tile(70,70).setBlock(Vars.content.block("ironworks-rapid-cannon"), Vars.state.rules.defaultTeam)'
  sleep 1
  echo 'js var tb = Vars.world.tile(70,70).build'
  echo 'js for(var i=0;i<40;i++) tb.handleItem(tb, Vars.content.item("ironworks-copper-ammo"))'
  echo 'js var en = Vars.content.unit("dagger").spawn(Vars.state.rules.waveTeam, 70*8+80, 70*8)'
  sleep 4
  echo 'js "T5 ammoLeft=" + tb.totalAmmo + " enemyHp=" + en.health + " alive=" + en.isValid()'

  # ---- 6. 腐蚀弹：弹药组装机第 6 配方 ----
  echo 'js Vars.world.tile(32,30).setBlock(Vars.content.block("power-source"), Vars.state.rules.defaultTeam)'
  echo 'js Vars.world.tile(30,30).setBlock(Vars.content.block("ironworks-ammo-assembler"), Vars.state.rules.defaultTeam)'
  sleep 1
  echo 'js Vars.world.tile(30,30).build.recipeIndex = 5'
  echo 'js Vars.world.tile(30,30).build.items.add(Vars.content.item("ironworks-casing"), 3)'
  echo 'js Vars.world.tile(30,30).build.items.add(Vars.content.item("ironworks-titanium-plate"), 3)'
  echo 'js Vars.world.tile(30,30).build.items.add(Vars.content.item("ironworks-sulfur"), 6)'
  sleep 6
  echo 'js "T6 sulfur=" + Vars.world.tile(30,30).build.items.get(Vars.content.item("ironworks-sulfur")) + " corrosive=" + Vars.world.tile(30,30).build.items.get(Vars.content.item("ironworks-corrosive-ammo"))'

  # ---- 6b. 同一台弹药组装机：凝固汽油弹灌装 ----
  echo 'js Vars.world.tile(30,30).build.recipeIndex = 6'
  echo 'js Vars.world.tile(30,30).build.items.add(Vars.content.item("ironworks-oil-barrel"), 2)'
  echo 'js Vars.world.tile(30,30).build.items.add(Vars.content.item("ironworks-titanium-plate"), 2)'
  echo 'js Vars.world.tile(30,30).build.items.add(Vars.content.item("silicon"), 2)'
  sleep 5
  echo 'js "T6b napalm=" + Vars.world.tile(30,30).build.items.get(Vars.content.item("ironworks-napalm-ammo")) + " barrel=" + Vars.world.tile(30,30).build.items.get(Vars.content.item("ironworks-oil-barrel"))'

  # ---- 7. 纳米修复场：范围内友军获得纳米装甲 ----
  echo 'js Vars.world.tile(62,40).setBlock(Vars.content.block("power-source"), Vars.state.rules.defaultTeam)'
  echo 'js Vars.world.tile(60,40).setBlock(Vars.content.block("ironworks-nano-field"), Vars.state.rules.defaultTeam)'
  sleep 1
  echo 'js var nu = Vars.content.unit("dagger").spawn(Vars.state.rules.defaultTeam, 66*8, 40*8)'
  sleep 4
  echo 'js "T7 eff=" + Vars.world.tile(60,40).build.efficiency + " applied=" + Vars.world.tile(60,40).build.appliedCount + " nanoEver=" + (Vars.world.tile(60,40).build.appliedCount > 0)'

  # ---- 8. 玉碎：挂状态后击杀，附近敌方墙受伤 ----
  echo 'js Vars.world.tile(75,60).setBlock(Vars.content.block("thorium-wall"), Vars.state.rules.waveTeam)'
  echo 'js var jw = Vars.world.tile(75,60).build'
  echo 'js var ju = Vars.content.unit("dagger").spawn(Vars.state.rules.defaultTeam, 72*8, 60*8)'
  echo 'js ju.apply(Vars.content.statusEffect("ironworks-jade-shatter"), 300)'
  echo 'js var jb = jw.health'
  echo 'js ju.kill()'
  sleep 2
  echo 'js "T8 wallBefore=" + Math.round(jb) + " wallAfter=" + Math.round(jw.health) + " dmg=" + Math.round(jb - jw.health)'

  # ---- 9.5 腐蚀：持续破甲伤害 ----
  echo 'js var cu = Vars.content.unit("dagger").spawn(Vars.state.rules.defaultTeam, 66*8, 44*8)'
  sleep 1
  echo 'js cu.apply(Vars.content.statusEffect("ironworks-corrosion"), 600)'
  echo 'js var chp = cu.health'
  echo 'js "T10 spawnHp=" + Math.round(cu.health) + " corr=" + cu.hasEffect(Vars.content.statusEffect("ironworks-corrosion"))'
  sleep 2
  echo 'js "T10 hpBefore=" + Math.round(chp) + " hpAfter=" + Math.round(cu.health) + " drop=" + Math.round(chp - cu.health)'

  # ---- 9. 腐蚀弹绑定与状态字段 ----
  echo 'js var pc = Vars.content.block("ironworks-piercer").ammoTypes'
  echo 'js "T9 types=" + pc.size + " status=" + pc.get(Vars.content.item("ironworks-corrosive-ammo")).status.name'

  # ---- 11. 装甲压制机：反应装甲块 ----
  echo 'js Vars.world.tile(39,34).setBlock(Vars.content.block("power-source"), Vars.state.rules.defaultTeam)'
  echo 'js Vars.world.tile(40,34).setBlock(Vars.content.block("ironworks-armor-press"), Vars.state.rules.defaultTeam)'
  sleep 1
  echo 'js Vars.world.tile(40,34).build.items.add(Vars.content.item("ironworks-steel-plate"), 4)'
  echo 'js Vars.world.tile(40,34).build.items.add(Vars.content.item("ironworks-lead-plate"), 2)'
  sleep 5
  echo 'js "T11 plate=" + Vars.world.tile(40,34).build.items.get(Vars.content.item("ironworks-reactive-plate"))'

  # ---- 12. 装甲坞：单位获得独立护甲层 ----
  echo 'js Vars.world.tile(62,34).setBlock(Vars.content.block("power-source"), Vars.state.rules.defaultTeam)'
  echo 'js Vars.world.tile(60,34).setBlock(Vars.content.block("ironworks-armor-dock"), Vars.state.rules.defaultTeam)'
  sleep 2
  echo 'js var ad = Vars.world.tile(60,34).build'
  echo 'js var au = Vars.content.unit("dagger").spawn(Vars.state.rules.defaultTeam, 62*8, 36*8)'
  sleep 1
  echo 'js var a0 = au.armorOverride; var h0 = au.health; au.damage(20)'
  echo 'js "T12a controlOverride=" + a0 + " drop=" + Math.round(h0 - au.health)'
  echo 'js au.health = 150'
  echo 'js ad.items.add(Vars.content.item("ironworks-reactive-plate"), 5)'
  echo 'js var ub = Vars.content.unit("dagger").spawn(Vars.state.rules.defaultTeam, 62*8, 37*8)'
  echo 'js var uc = Vars.content.unit("dagger").spawn(Vars.state.rules.defaultTeam, 63*8, 36*8); uc.apply(Vars.content.statusEffect("ironworks-composite-armor"), 600)'
  sleep 2
  echo 'js var h1 = ub.health; ub.damage(20)'
  echo 'js "T12 armor=" + ub.armorOverride + " has=" + ub.hasEffect(Vars.content.statusEffect("ironworks-reactive-armor")) + " drop=" + Math.round(h1 - ub.health) + " equipped=" + ad.equipped'

  # ---- 13. 互斥：已穿复合装甲的单位不会被再装反应装甲 ----
  echo 'js "T13 preArmored=" + uc.hasEffect(Vars.content.statusEffect("ironworks-composite-armor")) + " gotReactive=" + uc.hasEffect(Vars.content.statusEffect("ironworks-reactive-armor")) + " override=" + uc.armorOverride + " equipped=" + ad.equipped'


  # ---- 14. 高炉：铁精矿 + 煤 → 铁水 ----
  echo 'js Vars.world.tile(29,55).setBlock(Vars.content.block("power-source"), Vars.state.rules.defaultTeam)'
  echo 'js Vars.world.tile(31,55).setBlock(Vars.content.block("ironworks-blast-furnace"), Vars.state.rules.defaultTeam)'
  sleep 2
  echo 'js var bf = Vars.world.tile(31,55).build'
  echo 'js bf.items.add(Vars.content.item("ironworks-iron-concentrate"), 8)'
  echo 'js bf.items.add(Vars.content.item("coal"), 8)'
  sleep 6
  echo 'js "T15 moltenIron=" + Math.round(bf.liquids.get(Vars.content.liquid("ironworks-molten-iron"))) + " coal=" + bf.items.get(Vars.content.item("coal"))'

  # ---- 15. 转炉：铁水 + 氧 → 钢水（不需要外部热源）----
  echo 'js Vars.world.tile(37,55).setBlock(Vars.content.block("ironworks-converter"), Vars.state.rules.defaultTeam)'
  sleep 1
  echo 'js var cv = Vars.world.tile(37,55).build'
  echo 'js cv.liquids.add(Vars.content.liquid("ironworks-molten-iron"), 40)'
  echo 'js cv.liquids.add(Vars.content.liquid("ironworks-oxygen"), 30)'
  sleep 6
  echo 'js "T14 moltenSteel=" + Math.round(cv.liquids.get(Vars.content.liquid("ironworks-molten-steel"))) + " iron=" + Math.round(cv.liquids.get(Vars.content.liquid("ironworks-molten-iron"))) + " o2=" + Math.round(cv.liquids.get(Vars.content.liquid("ironworks-oxygen")))'

  # ---- 16. 连铸机：钢水 → 钢锭 ----
  echo 'js Vars.world.tile(49,70).setBlock(Vars.content.block("power-source"), Vars.state.rules.defaultTeam)'
  echo 'js Vars.world.tile(50,70).setBlock(Vars.content.block("ironworks-caster"), Vars.state.rules.defaultTeam)'
  sleep 2
  echo 'js var ca = Vars.world.tile(50,70).build'
  echo 'js ca.liquids.add(Vars.content.liquid("ironworks-molten-steel"), 40)'
  sleep 5
  echo 'js "T16 steel=" + ca.items.get(Vars.content.item("ironworks-steel-ingot")) + " steelLiquid=" + Math.round(ca.liquids.get(Vars.content.liquid("ironworks-molten-steel")))'

  # ---- 16b. 连铸机：铁水 → 生铁锭（铁水的第二去向）----
  echo 'js ca.recipeIndex = 1'
  echo 'js ca.liquids.add(Vars.content.liquid("ironworks-molten-iron"), 40)'
  sleep 5
  echo 'js "T19 ironIngot=" + ca.items.get(Vars.content.item("ironworks-iron-ingot")) + " moltenIron=" + Math.round(ca.liquids.get(Vars.content.liquid("ironworks-molten-iron")))'

  # ---- 16c. 连铸机：铁水 + 沙 → 铸铁件 ----
  echo 'js ca.recipeIndex = 2'
  echo 'js ca.items.add(Vars.content.item("sand"), 6)'
  echo 'js ca.liquids.add(Vars.content.liquid("ironworks-molten-iron"), 40)'
  sleep 5
  echo 'js "T20 castIron=" + ca.items.get(Vars.content.item("ironworks-cast-iron")) + " sand=" + ca.items.get(Vars.content.item("sand"))'

  # ---- 17. 电弧炉：海绵铁 + 电 + 氧 → 钢水 ----
  echo 'js Vars.world.tile(58,70).setBlock(Vars.content.block("power-source"), Vars.state.rules.defaultTeam)'
  echo 'js Vars.world.tile(60,70).setBlock(Vars.content.block("ironworks-arc-furnace"), Vars.state.rules.defaultTeam)'
  sleep 2
  echo 'js var af = Vars.world.tile(60,70).build'
  echo 'js af.items.add(Vars.content.item("ironworks-iron-ingot"), 4)'
  echo 'js af.liquids.add(Vars.content.liquid("ironworks-oxygen"), 20)'
  sleep 6
  echo 'js "T17 moltenSteel=" + Math.round(af.liquids.get(Vars.content.liquid("ironworks-molten-steel"))) + " iron=" + af.items.get(Vars.content.item("ironworks-iron-ingot"))'

  # ---- 18. 空气分离机：氮气 + 氧气（塞普罗的氮气来源）----
  echo 'js Vars.world.tile(39,64).setBlock(Vars.content.block("power-source"), Vars.state.rules.defaultTeam)'
  echo 'js Vars.world.tile(40,64).setBlock(Vars.content.block("ironworks-air-separator"), Vars.state.rules.defaultTeam)'
  sleep 8
  echo 'js "T18 n2=" + Math.round(Vars.world.tile(40,64).build.liquids.get(Vars.content.liquid("nitrogen"))) + " o2=" + Math.round(Vars.world.tile(40,64).build.liquids.get(Vars.content.liquid("ironworks-oxygen")))'

  # ---- 21. 转炉实战：高炉喂铁水 + 空分喂氧气（相邻输送，两面进料）----
  echo 'js Vars.world.tile(32,60).setBlock(Vars.content.block("power-source"), Vars.state.rules.defaultTeam)'
  echo 'js Vars.world.tile(34,60).setBlock(Vars.content.block("ironworks-blast-furnace"), Vars.state.rules.defaultTeam)'
  echo 'js Vars.world.tile(37,60).setBlock(Vars.content.block("ironworks-converter"), Vars.state.rules.defaultTeam)'
  echo 'js Vars.world.tile(37,62).setBlock(Vars.content.block("ironworks-air-separator"), Vars.state.rules.defaultTeam)'
  echo 'js Vars.world.tile(39,62).setBlock(Vars.content.block("power-source"), Vars.state.rules.defaultTeam)'
  sleep 2
  echo 'js var bf2=Vars.world.tile(34,60).build, cv2=Vars.world.tile(37,60).build'
  echo 'js bf2.items.add(Vars.content.item("ironworks-iron-concentrate"), 10); bf2.items.add(Vars.content.item("coal"), 10)'
  sleep 10
  echo 'js "T21 steel=" + Math.round(cv2.liquids.get(Vars.content.liquid("ironworks-molten-steel"))) + " iron=" + Math.round(cv2.liquids.get(Vars.content.liquid("ironworks-molten-iron"))) + " o2=" + Math.round(cv2.liquids.get(Vars.content.liquid("ironworks-oxygen")))'

  # ---- 22. 液体白名单：还原炉只收氢，不收氧 ----
  echo 'js Vars.world.tile(50,80).setBlock(Vars.content.block("ironworks-reducer"), Vars.state.rules.defaultTeam)'
  sleep 1
  echo 'js var rd=Vars.world.tile(50,80).build'
  echo 'js "T22 h2=" + rd.acceptLiquid(null, Vars.content.liquid("hydrogen")) + " o2=" + rd.acceptLiquid(null, Vars.content.liquid("ironworks-oxygen")) + " iron=" + rd.acceptLiquid(rd, Vars.content.liquid("ironworks-molten-iron"))'

  # ---- 23. 空气分离：氮右、氧左，不能混料 ----
  echo 'js Vars.world.tile(30,75).setBlock(Vars.content.block("ironworks-air-separator"), Vars.state.rules.defaultTeam)'
  echo 'js Vars.world.tile(32,75).setBlock(Vars.content.block("liquid-container"), Vars.state.rules.defaultTeam)'
  echo 'js Vars.world.tile(28,75).setBlock(Vars.content.block("liquid-container"), Vars.state.rules.defaultTeam)'
  echo 'js Vars.world.tile(30,77).setBlock(Vars.content.block("power-source"), Vars.state.rules.defaultTeam)'
  sleep 9
  echo 'js var rt=Vars.world.tile(32,75).build, lt=Vars.world.tile(28,75).build'
  echo 'js "T23 rightN2=" + Math.round(rt.liquids.get(Vars.content.liquid("nitrogen"))) + " rightO2=" + Math.round(rt.liquids.get(Vars.content.liquid("ironworks-oxygen"))) + " leftN2=" + Math.round(lt.liquids.get(Vars.content.liquid("nitrogen"))) + " leftO2=" + Math.round(lt.liquids.get(Vars.content.liquid("ironworks-oxygen")))'

  # ---- 24. 电解水：氢右、氧左 ----
  echo 'js Vars.world.tile(40,75).setBlock(Vars.content.block("ironworks-electrolysis-plant"), Vars.state.rules.defaultTeam)'
  echo 'js Vars.world.tile(42,75).setBlock(Vars.content.block("liquid-container"), Vars.state.rules.defaultTeam)'
  echo 'js Vars.world.tile(38,75).setBlock(Vars.content.block("liquid-container"), Vars.state.rules.defaultTeam)'
  echo 'js Vars.world.tile(40,77).setBlock(Vars.content.block("power-source"), Vars.state.rules.defaultTeam)'
  sleep 2
  echo 'js Vars.world.tile(40,75).build.liquids.add(Vars.content.liquid("water"), 40)'
  sleep 9
  echo 'js var rt2=Vars.world.tile(42,75).build, lt2=Vars.world.tile(38,75).build'
  echo 'js "T24 rightH2=" + Math.round(rt2.liquids.get(Vars.content.liquid("hydrogen"))) + " rightO2=" + Math.round(rt2.liquids.get(Vars.content.liquid("ironworks-oxygen"))) + " leftH2=" + Math.round(lt2.liquids.get(Vars.content.liquid("hydrogen"))) + " leftO2=" + Math.round(lt2.liquids.get(Vars.content.liquid("ironworks-oxygen")))'

  # ---- 25. 单液体输出：高炉铁水全向流出，左右罐子都能收到 ----
  echo 'js Vars.world.tile(30,65).setBlock(Vars.content.block("ironworks-blast-furnace"), Vars.state.rules.defaultTeam)'
  echo 'js Vars.world.tile(32,65).setBlock(Vars.content.block("liquid-container"), Vars.state.rules.defaultTeam)'
  echo 'js Vars.world.tile(27,65).setBlock(Vars.content.block("liquid-container"), Vars.state.rules.defaultTeam)'
  echo 'js Vars.world.tile(30,67).setBlock(Vars.content.block("power-source"), Vars.state.rules.defaultTeam)'
  sleep 2
  echo 'js var bf3=Vars.world.tile(30,65).build'
  echo 'js bf3.items.add(Vars.content.item("ironworks-iron-concentrate"), 8); bf3.items.add(Vars.content.item("coal"), 8)'
  sleep 9
  echo 'js var r3=Vars.world.tile(32,65).build, l3=Vars.world.tile(27,65).build'
  echo 'js "T25 rightIron=" + Math.round(r3.liquids.get(Vars.content.liquid("ironworks-molten-iron"))) + " leftIron=" + Math.round(l3.liquids.get(Vars.content.liquid("ironworks-molten-iron")))'

  # ---- 26. 循环冷却：拒收水，只收冷液 ----
  echo 'js Vars.world.tile(30,85).setBlock(Vars.content.block("ironworks-rapid-cannon"), Vars.state.rules.defaultTeam)'
  sleep 1
  echo 'js var t26=Vars.world.tile(30,85).build'
  echo 'js for(var i=0;i<20;i++) t26.handleItem(t26, Vars.content.item("ironworks-copper-ammo"))'
  echo 'js "T26 water=" + t26.acceptLiquid(t26, Vars.content.liquid("water")) + " cold=" + t26.acceptLiquid(t26, Vars.content.liquid("ironworks-graphene-coolant"))'

  # ---- 27. 超速器：炮塔能否被加速 ----
  echo 'js Vars.world.tile(40,85).setBlock(Vars.content.block("overdrive-projector"), Vars.state.rules.defaultTeam)'
  echo 'js Vars.world.tile(39,85).setBlock(Vars.content.block("power-source"), Vars.state.rules.defaultTeam)'
  echo 'js Vars.world.tile(42,85).setBlock(Vars.content.block("ironworks-rapid-cannon"), Vars.state.rules.defaultTeam)'
  sleep 4
  echo 'js "T27 timeScale=" + Vars.world.tile(42,85).build.timeScale() + " odEff=" + Vars.world.tile(40,85).build.efficiency'

  # ---- 28. 铁板：轧机第 5 配方 ----
  echo 'js Vars.world.tile(40,40).build.recipeIndex = 4'
  echo 'js Vars.world.tile(40,40).build.items.add(Vars.content.item("ironworks-iron-ingot"), 6)'
  sleep 4
  echo 'js "T28 ironPlate=" + Vars.world.tile(40,40).build.items.get(Vars.content.item("ironworks-iron-plate"))'

  # ---- 29. 炮弹组装室：高爆炮弹 ----
  echo 'js Vars.world.tile(72,60).setBlock(Vars.content.block("power-source"), Vars.state.rules.defaultTeam)'
  echo 'js Vars.world.tile(70,60).setBlock(Vars.content.block("ironworks-shell-assembly"), Vars.state.rules.defaultTeam)'
  sleep 2
  echo 'js var sa=Vars.world.tile(70,60).build'
  echo 'js sa.items.add(Vars.content.item("ironworks-casing"), 3)'
  echo 'js sa.items.add(Vars.content.item("ironworks-black-powder"), 6)'
  echo 'js sa.items.add(Vars.content.item("ironworks-iron-plate"), 6)'
  sleep 6
  echo 'js "T29 heShell=" + sa.items.get(Vars.content.item("ironworks-he-shell"))'

  # ---- 30. 迫击炮最小射程盲区（用静止墙体测试，避免单位被地图防御打死）----
  echo 'js Vars.world.tile(66,46).setBlock(Vars.content.block("ironworks-mortar"), Vars.state.rules.defaultTeam)'
  echo 'js Vars.world.tile(68,46).setBlock(Vars.content.block("thorium-wall"), Vars.state.rules.waveTeam)'
  sleep 1
  echo 'js var mo=Vars.world.tile(66,46).build'
  echo 'js for(var i=0;i<12;i++) mo.handleItem(mo, Vars.content.item("ironworks-he-shell"))'
  sleep 3
  echo 'js "T30a closeTarget=" + (mo.target != null) + " ammo=" + mo.totalAmmo'
  echo 'js Vars.world.tile(68,46).setBlock(Vars.content.block("air"))'
  echo 'js Vars.world.tile(90,46).setBlock(Vars.content.block("thorium-wall"), Vars.state.rules.waveTeam)'
  echo 'js var w2=Vars.world.tile(90,46).build'
  sleep 9
  echo 'js "T30b farTarget=" + (mo.target != null) + " wallAlive=" + (w2.isValid() && w2.health > 0) + " wallHp=" + Math.round(w2.health) + " ammo=" + mo.totalAmmo'

  # ---- 31. 迫击炮索敌：优先最密集的敌群 ----
  echo 'js Vars.world.tile(90,46).setBlock(Vars.content.block("thorium-wall"), Vars.state.rules.waveTeam)'
  echo 'js Vars.world.tile(91,46).setBlock(Vars.content.block("thorium-wall"), Vars.state.rules.waveTeam)'
  echo 'js Vars.world.tile(90,47).setBlock(Vars.content.block("thorium-wall"), Vars.state.rules.waveTeam)'
  echo 'js Vars.world.tile(44,46).setBlock(Vars.content.block("thorium-wall"), Vars.state.rules.waveTeam)'
  sleep 2
  echo 'js "T31 targetX=" + (mo.target == null ? -1 : Math.round(mo.target.getX())) + " targetY=" + (mo.target == null ? -1 : Math.round(mo.target.getY()))'

  # ---- 32. 石墨烯炉：石墨 + 氢 + 电 -> 石墨烯 ----
  echo 'js Vars.world.tile(20,30).setBlock(Vars.content.block("power-source"), Vars.state.rules.defaultTeam)'
  echo 'js Vars.world.tile(18,30).setBlock(Vars.content.block("ironworks-graphene-forge"), Vars.state.rules.defaultTeam)'
  sleep 1
  echo 'js var gf=Vars.world.tile(18,30).build'
  echo 'js gf.items.add(Vars.content.item("graphite"), 6)'
  echo 'js gf.liquids.add(Vars.content.liquid("hydrogen"), 20)'
  sleep 5
  echo 'js "T32 graphene=" + gf.items.get(Vars.content.item("ironworks-graphene"))'

  # ---- 33. 流化机：石墨烯 + 水 -> 冷液 ----
  echo 'js Vars.world.tile(24,30).setBlock(Vars.content.block("power-source"), Vars.state.rules.defaultTeam)'
  echo 'js Vars.world.tile(22,30).setBlock(Vars.content.block("ironworks-fluidizer"), Vars.state.rules.defaultTeam)'
  sleep 1
  echo 'js var fl=Vars.world.tile(22,30).build'
  echo 'js fl.items.add(Vars.content.item("ironworks-graphene"), 4)'
  echo 'js fl.liquids.add(Vars.content.liquid("water"), 30)'
  sleep 5
  echo 'js "T33 cold=" + Math.round(fl.liquids.get(Vars.content.liquid("ironworks-graphene-coolant")))'

  # ---- 34. 冷却工厂：热液 + 水 -> 冷液 ----
  echo 'js Vars.world.tile(28,30).setBlock(Vars.content.block("power-source"), Vars.state.rules.defaultTeam)'
  echo 'js Vars.world.tile(26,30).setBlock(Vars.content.block("ironworks-coolant-plant"), Vars.state.rules.defaultTeam)'
  sleep 1
  echo 'js var cp=Vars.world.tile(26,30).build'
  echo 'js cp.liquids.add(Vars.content.liquid("ironworks-hot-coolant"), 20)'
  echo 'js cp.liquids.add(Vars.content.liquid("water"), 20)'
  sleep 5
  echo 'js "T34 cold=" + Math.round(cp.liquids.get(Vars.content.liquid("ironworks-graphene-coolant"))) + " hot=" + Math.round(cp.liquids.get(Vars.content.liquid("ironworks-hot-coolant")))'

  # ---- 35. 炮塔循环：冷液进、热液出 ----
  echo 'js t26.liquids.add(Vars.content.liquid("ironworks-graphene-coolant"), 20)'
  sleep 5
  echo 'js "T35 cold=" + Math.round(t26.liquids.get(Vars.content.liquid("ironworks-graphene-coolant"))) + " hot=" + Math.round(t26.liquids.get(Vars.content.liquid("ironworks-hot-coolant")))'

  # ---- 36. 制酸车间：硫 + 水 -> 酸 ----
  echo 'js Vars.world.tile(36,30).setBlock(Vars.content.block("power-source"), Vars.state.rules.defaultTeam)'
  echo 'js Vars.world.tile(34,30).setBlock(Vars.content.block("ironworks-acid-plant"), Vars.state.rules.defaultTeam)'
  sleep 1
  echo 'js var ap=Vars.world.tile(34,30).build'
  echo 'js ap.items.add(Vars.content.item("ironworks-sulfur"), 6)'
  echo 'js ap.liquids.add(Vars.content.liquid("water"), 20)'
  sleep 5
  echo 'js "T36 acid=" + Math.round(ap.liquids.get(Vars.content.liquid("ironworks-acid")))'

  # ---- 37. 普通管道拒收熔融金属，耐高温管道放行 ----
  echo 'js Vars.world.tile(40,30).setBlock(Vars.content.block("conduit"), Vars.state.rules.defaultTeam)'
  echo 'js Vars.world.tile(42,30).setBlock(Vars.content.block("ironworks-hot-conduit"), Vars.state.rules.defaultTeam)'
  sleep 1
  echo 'js "T37 normalMolten=" + Vars.world.tile(40,30).build.acceptLiquid(ap, Vars.content.liquid("ironworks-molten-iron")) + " hotMolten=" + Vars.world.tile(42,30).build.acceptLiquid(ap, Vars.content.liquid("ironworks-molten-iron")) + " normalWater=" + Vars.world.tile(40,30).build.acceptLiquid(ap, Vars.content.liquid("water"))'

  # ---- 38. 熔融铁水桶：装桶 ----
  echo 'js Vars.world.tile(46,30).setBlock(Vars.content.block("power-source"), Vars.state.rules.defaultTeam)'
  echo 'js Vars.world.tile(44,30).setBlock(Vars.content.block("ironworks-barrel-filler"), Vars.state.rules.defaultTeam)'
  sleep 1
  echo 'js var bf4=Vars.world.tile(44,30).build'
  echo 'js bf4.recipeIndex = 2'
  echo 'js bf4.items.add(Vars.content.item("ironworks-iron-plate"), 3)'
  echo 'js bf4.liquids.add(Vars.content.liquid("ironworks-molten-iron"), 40)'
  sleep 5
  echo 'js "T38 barrel=" + bf4.items.get(Vars.content.item("ironworks-molten-iron-barrel"))'

  # ---- 39. 碳化硅：高炉第 2 配方 ----
  echo 'js Vars.world.tile(52,30).setBlock(Vars.content.block("power-source"), Vars.state.rules.defaultTeam)'
  echo 'js Vars.world.tile(50,30).setBlock(Vars.content.block("ironworks-blast-furnace"), Vars.state.rules.defaultTeam)'
  sleep 1
  echo 'js var bf5=Vars.world.tile(50,30).build'
  echo 'js bf5.recipeIndex = 1'
  echo 'js bf5.items.add(Vars.content.item("sand"), 6)'
  echo 'js bf5.items.add(Vars.content.item("graphite"), 4)'
  sleep 5
  echo 'js "T39 sic=" + bf5.items.get(Vars.content.item("ironworks-silicon-carbide"))'

  # ---- 40. 普通管道进熔融金属 → 掉血 ----
  echo 'js Vars.world.tile(54,30).setBlock(Vars.content.block("conduit"), Vars.state.rules.defaultTeam)'
  echo 'js var nc=Vars.world.tile(54,30).build; var hp0=nc.health'
  echo 'js nc.handleLiquid(nc, Vars.content.liquid("ironworks-molten-iron"), 5)'
  sleep 3
  echo 'js "T40 hp0=" + Math.round(hp0) + " hp1=" + Math.round(nc.health) + " burned=" + (nc.health < hp0)'

  # ---- 41. 耐高温路由器/交叉器/管道桥 ----
  echo 'js Vars.world.tile(56,30).setBlock(Vars.content.block("ironworks-hot-router"), Vars.state.rules.defaultTeam)'
  echo 'js Vars.world.tile(58,30).setBlock(Vars.content.block("ironworks-hot-junction"), Vars.state.rules.defaultTeam)'
  echo 'js Vars.world.tile(60,30).setBlock(Vars.content.block("ironworks-hot-bridge"), Vars.state.rules.defaultTeam)'
  sleep 1
  echo 'js "T41 router=" + Vars.world.tile(56,30).build.acceptLiquid(Vars.world.tile(56,30).build, Vars.content.liquid("ironworks-molten-iron")) + " junctionLiq=" + Vars.content.block("ironworks-hot-junction").hasLiquids + " bridgeLiq=" + Vars.content.block("ironworks-hot-bridge").hasLiquids'

  # ---- 42. 其余原版液体方块：脉冲/电镀导管、容器也要烧 ----
  echo 'js Vars.world.tile(58,34).setBlock(Vars.content.block("pulse-conduit"), Vars.state.rules.defaultTeam)'
  echo 'js Vars.world.tile(59,34).setBlock(Vars.content.block("liquid-container"), Vars.state.rules.defaultTeam)'
  echo 'js Vars.world.tile(62,34).setBlock(Vars.content.block("liquid-container"), Vars.state.rules.defaultTeam)'
  sleep 1
  echo 'js var pc=Vars.world.tile(58,34).build, tk=Vars.world.tile(62,34).build'
  echo 'js var hp1=pc.health, hp2=tk.health'
  echo 'js for(var i=0;i<20;i++) pc.handleLiquid(pc, Vars.content.liquid("ironworks-molten-iron"), 5)'
  echo 'js tk.handleLiquid(tk, Vars.content.liquid("ironworks-molten-iron"), 100)'
  sleep 4
  echo 'js "T42 pulse=" + Math.round(hp1) + "->" + Math.round(pc.health) + " tank=" + Math.round(hp2) + "->" + Math.round(tk.health)'

  # ---- 43. 炮塔冷却：无目标不消耗；热液满则失去加速 ----
  echo 'js Vars.world.tile(30,85).setBlock(Vars.content.block("ironworks-rapid-cannon"), Vars.state.rules.defaultTeam)'
  sleep 1
  echo 'js var ct=Vars.world.tile(30,85).build'
  echo 'js for(var i=0;i<20;i++) ct.handleItem(ct, Vars.content.item("ironworks-copper-ammo"))'
  echo 'js ct.liquids.add(Vars.content.liquid("ironworks-graphene-coolant"), 20)'
  sleep 3
  echo 'js "T43a cold=" + Math.round(ct.liquids.get(Vars.content.liquid("ironworks-graphene-coolant"))) + " cooling=" + ct.cooling'
  echo 'js Vars.world.tile(46,85).setBlock(Vars.content.block("thorium-wall"), Vars.state.rules.waveTeam)'
  sleep 3
  echo 'js "T43b cold=" + Math.round(ct.liquids.get(Vars.content.liquid("ironworks-graphene-coolant"))) + " hot=" + Math.round(ct.liquids.get(Vars.content.liquid("ironworks-hot-coolant"))) + " cooling=" + ct.cooling'
  echo 'js ct.liquids.add(Vars.content.liquid("ironworks-hot-coolant"), 40)'
  sleep 1
  echo 'js "T43c cold=" + Math.round(ct.liquids.get(Vars.content.liquid("ironworks-graphene-coolant"))) + " cooling=" + ct.cooling'

  # ---- 44. 闭环守恒：冷+热总量保持 40 ----
  echo 'js Vars.world.tile(50,85).setBlock(Vars.content.block("ironworks-rapid-cannon"), Vars.state.rules.defaultTeam)'
  echo 'js Vars.world.tile(52,85).setBlock(Vars.content.block("ironworks-coolant-plant"), Vars.state.rules.defaultTeam)'
  echo 'js Vars.world.tile(54,85).setBlock(Vars.content.block("power-source"), Vars.state.rules.defaultTeam)'
  sleep 1
  echo 'js var lt=Vars.world.tile(50,85).build, lp2=Vars.world.tile(52,85).build'
  echo 'js lt.block.coolantOnFireOnly = false'
  echo 'js lp2.liquids.add(Vars.content.liquid("water"), 60)'
  echo 'js lt.liquids.add(Vars.content.liquid("ironworks-graphene-coolant"), 40)'
  sleep 12
  echo 'js "T44 tot=" + Math.round(lt.liquids.get(Vars.content.liquid("ironworks-graphene-coolant")) + lp2.liquids.get(Vars.content.liquid("ironworks-graphene-coolant")) + lt.liquids.get(Vars.content.liquid("ironworks-hot-coolant")) + lp2.liquids.get(Vars.content.liquid("ironworks-hot-coolant")))'

  # ---- 45. 冷却工厂 1:1 ----
  echo 'js Vars.world.tile(58,85).setBlock(Vars.content.block("ironworks-coolant-plant"), Vars.state.rules.defaultTeam)'
  echo 'js Vars.world.tile(60,85).setBlock(Vars.content.block("power-source"), Vars.state.rules.defaultTeam)'
  sleep 1
  echo 'js var cp2=Vars.world.tile(58,85).build'
  echo 'js cp2.liquids.add(Vars.content.liquid("water"), 40)'
  echo 'js cp2.liquids.add(Vars.content.liquid("ironworks-hot-coolant"), 30)'
  sleep 8
  echo 'js "T45 hot=" + Math.round(cp2.liquids.get(Vars.content.liquid("ironworks-hot-coolant"))) + " cold=" + Math.round(cp2.liquids.get(Vars.content.liquid("ironworks-graphene-coolant")))'

  # ---- 46. 传送带通液：液体随带面流到下游，物品照常走 ----
  echo 'js Vars.world.tile(64,70).setBlock(Vars.content.block("power-source"), Vars.state.rules.defaultTeam)'
  echo 'js Vars.world.tile(60,70).setBlock(Vars.content.block("ironworks-liquid-conveyor"), Vars.state.rules.defaultTeam)'
  echo 'js Vars.world.tile(61,70).setBlock(Vars.content.block("ironworks-liquid-conveyor"), Vars.state.rules.defaultTeam)'
  echo 'js Vars.world.tile(62,70).setBlock(Vars.content.block("ironworks-coolant-plant"), Vars.state.rules.defaultTeam)'
  sleep 1
  echo 'js var cv=Vars.world.tile(60,70).build, cv2=Vars.world.tile(61,70).build, cpl=Vars.world.tile(62,70).build'
  echo 'js cv.handleLiquid(cv, Vars.content.liquid("water"), 6); cv.handleItem(cv, Vars.content.item("coal"))'
  sleep 3
  echo 'js "T46 plantWater=" + Math.round(cpl.liquids.get(Vars.content.liquid("water"))) + " cvItems=" + cv.items.total() + " cv2Items=" + cv2.items.total()'

  # ---- 47. 断头泄漏：带上有液体但前方没东西，液体会漏掉 ----
  echo 'js Vars.world.tile(56,74).setBlock(Vars.content.block("ironworks-liquid-conveyor"), Vars.state.rules.defaultTeam)'
  sleep 1
  echo 'js var cw=Vars.world.tile(56,74).build; cw.handleLiquid(cw, Vars.content.liquid("ironworks-graphene-coolant"), 9)'
  sleep 2
  echo 'js "T47 remain=" + Math.round(cw.liquids.currentAmount()*10)/10'

  # ---- 48. 用户布局复现：速射炮 + 冷/热两根导管 + 冷却工厂，总量必须守恒 ----
  echo 'js Vars.world.tile(10,10).setBlock(Vars.content.block("ironworks-rapid-cannon"), Vars.state.rules.defaultTeam)'
  echo 'js Vars.world.tile(10,12).setBlock(Vars.content.block("conduit"), Vars.state.rules.defaultTeam)'
  echo 'js Vars.world.tile(11,12).setBlock(Vars.content.block("conduit"), Vars.state.rules.defaultTeam)'
  echo 'js Vars.world.tile(10,13).setBlock(Vars.content.block("ironworks-coolant-plant"), Vars.state.rules.defaultTeam)'
  echo 'js Vars.world.tile(12,13).setBlock(Vars.content.block("power-source"), Vars.state.rules.defaultTeam)'
  sleep 1
  echo 'js Vars.world.tile(10,12).build.rotation = 1; Vars.world.tile(11,12).build.rotation = 3'
  echo 'js var lt2=Vars.world.tile(10,10).build, lp3=Vars.world.tile(10,13).build, lc0=Vars.world.tile(10,12).build, lc1=Vars.world.tile(11,12).build'
  echo 'js lt2.block.coolantOnFireOnly = false; lp3.liquids.add(Vars.content.liquid("water"), 60); lt2.liquids.add(Vars.content.liquid("ironworks-graphene-coolant"), 40)'
  sleep 12
  echo 'js "T48 tot=" + Math.round(lt2.liquids.get(Vars.content.liquid("ironworks-graphene-coolant")) + lt2.liquids.get(Vars.content.liquid("ironworks-hot-coolant")) + lp3.liquids.get(Vars.content.liquid("ironworks-graphene-coolant")) + lp3.liquids.get(Vars.content.liquid("ironworks-hot-coolant")) + lc0.liquids.currentAmount() + lc1.liquids.currentAmount())'

  # ---- 49. 通液路由器：物品 + 液体都能转发 ----
  echo 'js Vars.world.tile(70,80).setBlock(Vars.content.block("ironworks-liquid-belt-router"), Vars.state.rules.defaultTeam)'
  echo 'js Vars.world.tile(71,80).setBlock(Vars.content.block("ironworks-liquid-conveyor"), Vars.state.rules.defaultTeam)'
  echo 'js Vars.world.tile(69,80).setBlock(Vars.content.block("ironworks-liquid-conveyor"), Vars.state.rules.defaultTeam)'
  sleep 1
  echo 'js var rt2=Vars.world.tile(70,80).build, rb1=Vars.world.tile(71,80).build, rb0=Vars.world.tile(69,80).build'
  echo 'js rt2.handleItem(rt2, Vars.content.item("coal")); rt2.handleLiquid(rt2, Vars.content.liquid("water"), 6)'
  sleep 2
  echo 'js "T49 items=" + (rb0.items.total() + rb1.items.total()) + " liquid=" + Math.round(rb0.liquids.currentAmount() + rb1.liquids.currentAmount())'

  # ---- 50. 侧面物品输入：任意方向都能塞进带子 ----
  echo 'js "T50 sideInput=" + rb1.acceptItem(rb1, Vars.content.item("coal"))'

  # ---- 51. 带桥存在且有液体模块 ----
  echo 'js Vars.world.tile(74,80).setBlock(Vars.content.block("ironworks-liquid-belt-bridge"), Vars.state.rules.defaultTeam)'
  sleep 1
  echo 'js "T51 bridgeLiq=" + (Vars.world.tile(74,80).build.liquids != null)'

  # ---- 52. 带内液体缓慢消耗：每格固定周期扣 1 单位 ----
  echo 'js Vars.world.tile(80,80).setBlock(Vars.content.block("ironworks-liquid-conveyor-adv"), Vars.state.rules.defaultTeam)'
  sleep 1
  echo 'js var bw=Vars.world.tile(80,80).build; bw.block.liquidConsumeTicks = 30'
  echo 'js bw.handleLiquid(bw, Vars.content.liquid("water"), 5)'
  sleep 2
  echo 'js "T52 remain=" + Math.round(bw.liquids.get(Vars.content.liquid("water"))*100)/100'

  # ---- 53. 通液带桥：链接后物品与液体都要能跨格（回归死循环卡死）----
  echo 'js Vars.world.tile(128,34).setBlock(Vars.content.block("ironworks-liquid-belt-bridge"), Vars.state.rules.defaultTeam)'
  echo 'js Vars.world.tile(134,34).setBlock(Vars.content.block("ironworks-liquid-belt-bridge"), Vars.state.rules.defaultTeam)'
  sleep 1
  echo 'js var ba=Vars.world.tile(128,34).build, bb=Vars.world.tile(134,34).build; ba.configure(new Packages.arc.math.geom.Point2(6,0))'
  echo 'js ba.handleItem(ba, Vars.content.item("coal")); ba.handleLiquid(ba, Vars.content.liquid("water"), 5)'
  sleep 4
  echo 'js "T53 items=" + bb.items.total() + " liquid=" + Math.round(bb.liquids.get(Vars.content.liquid("water"))*100)/100'

  # ---- 54. 带桥补液必须停在容量上限（不能灌到 20+）----
  echo 'js Vars.world.tile(150,60).setBlock(Vars.content.block("ironworks-liquid-belt-bridge-adv"), Vars.state.rules.defaultTeam)'
  echo 'js Vars.world.tile(158,60).setBlock(Vars.content.block("ironworks-liquid-belt-bridge-adv"), Vars.state.rules.defaultTeam)'
  sleep 1
  echo 'js var pa=Vars.world.tile(150,60).build, pb=Vars.world.tile(158,60).build; pa.configure(new Packages.arc.math.geom.Point2(8,0))'
  echo 'js pa.liquids.add(Vars.content.liquid("water"), 14); pb.liquids.add(Vars.content.liquid("water"), 14)'
  sleep 8
  echo 'js "T54 src=" + Math.round(pa.liquids.get(Vars.content.liquid("water"))*100)/100 + " dst=" + Math.round(pb.liquids.get(Vars.content.liquid("water"))*100)/100'

  # ---- 62. 贵金属浸出：铁精矿 + 酸 -> 银 / 金 ----
  echo 'js Vars.world.tile(22,50).setBlock(Vars.content.block("power-source"), Vars.state.rules.defaultTeam)'
  echo 'js Vars.world.tile(20,50).setBlock(Vars.content.block("ironworks-precious-leach"), Vars.state.rules.defaultTeam)'
  sleep 1
  echo 'js var pl=Vars.world.tile(20,50).build; pl.items.add(Vars.content.item("scrap"), 8); pl.liquids.add(Vars.content.liquid("ironworks-acid"), 30)'
  sleep 5
  echo 'js "T62 silver=" + pl.items.get(Vars.content.item("ironworks-silver"))'

  # ---- 63. 芯片车间：基础集成电路 ----
  echo 'js Vars.world.tile(28,50).setBlock(Vars.content.block("power-source"), Vars.state.rules.defaultTeam)'
  echo 'js Vars.world.tile(26,50).setBlock(Vars.content.block("ironworks-chip-fab"), Vars.state.rules.defaultTeam)'
  sleep 1
  echo 'js var cf=Vars.world.tile(26,50).build; cf.items.add(Vars.content.item("silicon"), 8); cf.items.add(Vars.content.item("ironworks-copper-plate"), 4); cf.liquids.add(Vars.content.liquid("ironworks-acid"), 30)'
  sleep 5
  echo 'js "T63 basicChip=" + cf.items.get(Vars.content.item("ironworks-basic-chip"))'

  # ---- 64. 电弧塔：耗电炮塔存在 ----
  echo 'js Vars.world.tile(34,50).setBlock(Vars.content.block("power-source"), Vars.state.rules.defaultTeam)'
  echo 'js Vars.world.tile(32,50).setBlock(Vars.content.block("ironworks-arc-tower"), Vars.state.rules.defaultTeam)'
  sleep 1
  echo 'js "T64 arc=" + (Vars.content.block("ironworks-arc-tower") != null) + " shoot=" + (Vars.content.block("ironworks-arc-tower").shootType != null)'

  # ---- 65. 电弧塔过热机制字段 ----
  echo 'js var at=Vars.content.block("ironworks-arc-tower"); Log.info("ARC overheat=" + at.overheatTime + " speedup=" + at.maxSpeedupScl)'

  # ---- 66. 铝线②溶出：电解车间拜耳法（铝土矿 + 酸 -> 铝酸钠 + 赤泥） ----
  echo 'js Vars.world.tile(26,80).setBlock(Vars.content.block("power-source"), Vars.state.rules.defaultTeam)'
  echo 'js Vars.world.tile(24,80).setBlock(Vars.content.block("ironworks-electrolysis-plant"), Vars.state.rules.defaultTeam)'
  sleep 1
  echo 'js var dg=Vars.world.tile(24,80).build; dg.recipeIndex=3; dg.items.add(Vars.content.item("ironworks-bauxite"), 8); dg.liquids.add(Vars.content.liquid("ironworks-acid"), 40)'
  sleep 7
  echo 'js "T66 liquor=" + Math.round(dg.liquids.get(Vars.content.liquid("ironworks-sodium-aluminate"))) + " redMud=" + dg.items.get(Vars.content.item("ironworks-red-mud"))'

  # ---- 67. 铝线③焙烧：电解车间分解（铝酸钠 -> 氧化铝 + 回收酸） ----
  echo 'js Vars.world.tile(34,80).setBlock(Vars.content.block("power-source"), Vars.state.rules.defaultTeam)'
  echo 'js Vars.world.tile(32,80).setBlock(Vars.content.block("ironworks-electrolysis-plant"), Vars.state.rules.defaultTeam)'
  sleep 1
  echo 'js var cl=Vars.world.tile(32,80).build; cl.recipeIndex=4; cl.liquids.add(Vars.content.liquid("ironworks-sodium-aluminate"), 40)'
  sleep 7
  echo 'js "T67 alumina=" + cl.items.get(Vars.content.item("ironworks-alumina")) + " acidBack=" + Math.round(cl.liquids.get(Vars.content.liquid("ironworks-acid")))'

  # ---- 68. 铝线④铝电解：电解车间（氧化铝 -> 熔融铝 + 氧气） ----
  echo 'js Vars.world.tile(42,80).setBlock(Vars.content.block("power-source"), Vars.state.rules.defaultTeam)'
  echo 'js Vars.world.tile(40,80).setBlock(Vars.content.block("ironworks-electrolysis-plant"), Vars.state.rules.defaultTeam)'
  sleep 1
  echo 'js var hc=Vars.world.tile(40,80).build; hc.recipeIndex=6; hc.items.add(Vars.content.item("ironworks-alumina"), 8); hc.items.add(Vars.content.item("ironworks-cryolite"), 4); hc.items.add(Vars.content.item("ironworks-graphite-electrode"), 4)'
  sleep 9
  echo 'js "T68 moltenAl=" + Math.round(hc.liquids.get(Vars.content.liquid("ironworks-molten-aluminum"))) + " oxygen=" + Math.round(hc.liquids.get(Vars.content.liquid("ironworks-oxygen")))'

  # ---- 69. 铝线⑤铸造：连铸机（熔融铝 -> 铝锭）；轧机（铝锭 -> 铝板） ----
  echo 'js Vars.world.tile(50,80).setBlock(Vars.content.block("power-source"), Vars.state.rules.defaultTeam)'
  echo 'js Vars.world.tile(48,80).setBlock(Vars.content.block("ironworks-caster"), Vars.state.rules.defaultTeam)'
  sleep 1
  echo 'js var ap=Vars.world.tile(48,80).build; ap.recipeIndex=3; ap.liquids.add(Vars.content.liquid("ironworks-molten-aluminum"), 40)'
  sleep 6
  echo 'js "T69 aluminum=" + ap.items.get(Vars.content.item("ironworks-aluminum"))'
  echo 'js Vars.world.tile(56,80).setBlock(Vars.content.block("power-source"), Vars.state.rules.defaultTeam)'
  echo 'js Vars.world.tile(54,80).setBlock(Vars.content.block("ironworks-rolling-mill"), Vars.state.rules.defaultTeam)'
  sleep 1
  echo 'js var rm=Vars.world.tile(54,80).build; rm.recipeIndex=5; rm.items.add(Vars.content.item("ironworks-aluminum"), 6)'
  sleep 5
  echo 'js "T69b sheet=" + rm.items.get(Vars.content.item("ironworks-aluminum-sheet"))'

  # ---- 70. 铝热炼铁：电弧炉（铝 + 铁精矿 -> 铁水 + 氧化铝） ----
  echo 'js Vars.world.tile(22,88).setBlock(Vars.content.block("power-source"), Vars.state.rules.defaultTeam)'
  echo 'js Vars.world.tile(24,88).setBlock(Vars.content.block("ironworks-arc-furnace"), Vars.state.rules.defaultTeam)'
  sleep 1
  echo 'js var tf=Vars.world.tile(24,88).build; tf.recipeIndex=6; tf.items.add(Vars.content.item("ironworks-aluminum"), 8); tf.items.add(Vars.content.item("ironworks-iron-concentrate"), 12)'
  sleep 8
  echo 'js "T70 moltenIron=" + Math.round(tf.liquids.get(Vars.content.liquid("ironworks-molten-iron"))) + " aluminaBack=" + tf.items.get(Vars.content.item("ironworks-alumina"))'

  # ---- 71. 原版闭环·硅晶炉：沙 + 煤 -> 硅 ----
  echo 'js Vars.world.tile(32,88).setBlock(Vars.content.block("ironworks-silicon-furnace"), Vars.state.rules.defaultTeam)'
  echo 'js Vars.world.tile(34,88).setBlock(Vars.content.block("power-source"), Vars.state.rules.defaultTeam)'
  sleep 1
  echo 'js var sf=Vars.world.tile(32,88).build; sf.items.add(Vars.content.item("sand"), 12); sf.items.add(Vars.content.item("coal"), 6)'
  sleep 7
  echo 'js "T71 silicon=" + sf.items.get(Vars.content.item("silicon")) + " sand=" + sf.items.get(Vars.content.item("sand"))'

  # ---- 72. 原版闭环·玻璃窑 -> 玻璃 ----
  echo 'js Vars.world.tile(40,88).setBlock(Vars.content.block("ironworks-glass-kiln"), Vars.state.rules.defaultTeam)'
  echo 'js Vars.world.tile(42,88).setBlock(Vars.content.block("power-source"), Vars.state.rules.defaultTeam)'
  sleep 1
  echo 'js var gk=Vars.world.tile(40,88).build; gk.items.add(Vars.content.item("sand"), 9); gk.items.add(Vars.content.item("lead"), 6)'
  sleep 7
  echo 'js "T72 metaglass=" + gk.items.get(Vars.content.item("metaglass"))'

  # ---- 73. 原版闭环·石墨化炉：煤 -> 石墨 ----
  echo 'js Vars.world.tile(48,88).setBlock(Vars.content.block("ironworks-graphitizer"), Vars.state.rules.defaultTeam)'
  echo 'js Vars.world.tile(46,88).setBlock(Vars.content.block("power-source"), Vars.state.rules.defaultTeam)'
  sleep 1
  echo 'js var gz=Vars.world.tile(48,88).build; gz.items.add(Vars.content.item("coal"), 12)'
  sleep 9
  echo 'js "T73 graphite=" + gz.items.get(Vars.content.item("graphite"))'

  # ---- 74. 原版闭环·废料分选厂：废料 -> 铜 ----
  echo 'js Vars.world.tile(56,88).setBlock(Vars.content.block("ironworks-scrap-works"), Vars.state.rules.defaultTeam)'
  echo 'js Vars.world.tile(54,88).setBlock(Vars.content.block("power-source"), Vars.state.rules.defaultTeam)'
  sleep 1
  echo 'js var sw=Vars.world.tile(56,88).build; sw.items.add(Vars.content.item("scrap"), 10)'
  sleep 7
  echo 'js "T74 scrap=" + sw.items.get(Vars.content.item("scrap")) + " copperBack=" + sw.items.get(Vars.content.item("copper"))'


  # ---- 75. 应急烧结制铝：连铸机（沙 + 煤 -> 铝锭，老存档起步） ----
  echo 'js Vars.world.tile(66,88).setBlock(Vars.content.block("power-source"), Vars.state.rules.defaultTeam)'
  echo 'js Vars.world.tile(64,88).setBlock(Vars.content.block("ironworks-caster"), Vars.state.rules.defaultTeam)'
  sleep 1
  echo 'js var sial=Vars.world.tile(64,88).build; sial.recipeIndex=8; sial.items.add(Vars.content.item("sand"), 20); sial.items.add(Vars.content.item("coal"), 8)'
  sleep 12
  echo 'js "T75 sinterAl=" + sial.items.get(Vars.content.item("ironworks-aluminum")) + " recipes=" + Vars.content.block("ironworks-caster").recipes.length'

  # ---- 78. 转炉巨浪合金熔炼：熔融铜铅钛 + 硅 + 氧 -> 合金熔体 + 炉渣 ----
  echo 'js Vars.world.tile(70,92).setBlock(Vars.content.block("power-source"), Vars.state.rules.defaultTeam)'
  echo 'js Vars.world.tile(72,92).setBlock(Vars.content.block("ironworks-converter"), Vars.state.rules.defaultTeam)'
  sleep 1
  echo 'js var cv=Vars.world.tile(72,92).build; cv.recipeIndex=1; cv.items.add(Vars.content.item("silicon"), 6); cv.liquids.add(Vars.content.liquid("ironworks-molten-copper"), 20); cv.liquids.add(Vars.content.liquid("ironworks-molten-lead"), 30); cv.liquids.add(Vars.content.liquid("ironworks-molten-titanium"), 20); cv.liquids.add(Vars.content.liquid("ironworks-oxygen"), 20)'
  sleep 10
  echo 'js "T78 surgeMelt=" + Math.round(cv.liquids.get(Vars.content.liquid("ironworks-molten-surge"))) + " slag=" + cv.items.get(Vars.content.item("ironworks-slag"))'

  # ---- 79. 连铸巨浪合金：熔融合金 -> 巨浪合金 ----
  echo 'js Vars.world.tile(80,92).setBlock(Vars.content.block("power-source"), Vars.state.rules.defaultTeam)'
  echo 'js Vars.world.tile(78,92).setBlock(Vars.content.block("ironworks-caster"), Vars.state.rules.defaultTeam)'
  sleep 1
  echo 'js var sc=Vars.world.tile(78,92).build; sc.recipeIndex=7; sc.liquids.add(Vars.content.liquid("ironworks-molten-surge"), 30)'
  sleep 7
  echo 'js "T79 surgeAlloy=" + sc.items.get(Vars.content.item("surge-alloy"))'

  # ---- 80. 炼油厂常压分馏：原油 -> 汽油 + 重油 ----
  echo 'js Vars.world.tile(18,100).setBlock(Vars.content.block("power-source"), Vars.state.rules.defaultTeam)'
  echo 'js Vars.world.tile(20,100).setBlock(Vars.content.block("ironworks-refinery"), Vars.state.rules.defaultTeam)'
  sleep 1
  echo 'js var rf=Vars.world.tile(20,100).build; rf.liquids.add(Vars.content.liquid("oil"), 60)'
  sleep 9
  echo 'js "T80 gasoline=" + Math.round(rf.liquids.get(Vars.content.liquid("ironworks-gasoline"))) + " heavy=" + Math.round(rf.liquids.get(Vars.content.liquid("ironworks-heavy-oil")))'

  # ---- 81. 延迟焦化：重油 -> 石油焦 + 汽油 ----
  echo 'js Vars.world.tile(26,100).setBlock(Vars.content.block("power-source"), Vars.state.rules.defaultTeam)'
  echo 'js Vars.world.tile(28,100).setBlock(Vars.content.block("ironworks-refinery"), Vars.state.rules.defaultTeam)'
  sleep 1
  echo 'js var rk=Vars.world.tile(28,100).build; rk.recipeIndex=1; rk.liquids.add(Vars.content.liquid("ironworks-heavy-oil"), 50)'
  sleep 10
  echo 'js "T81 petcoke=" + rk.items.get(Vars.content.item("ironworks-petcoke")) + " gasBack=" + Math.round(rk.liquids.get(Vars.content.liquid("ironworks-gasoline")))'

  # ---- 82. 聚合车间：汽油 + 硫 -> 橡胶 ----
  echo 'js Vars.world.tile(34,100).setBlock(Vars.content.block("power-source"), Vars.state.rules.defaultTeam)'
  echo 'js Vars.world.tile(36,100).setBlock(Vars.content.block("ironworks-polymer-works"), Vars.state.rules.defaultTeam)'
  sleep 1
  echo 'js var pw=Vars.world.tile(36,100).build; pw.items.add(Vars.content.item("ironworks-sulfur"), 4); pw.liquids.add(Vars.content.liquid("ironworks-gasoline"), 30); pw.liquids.add(Vars.content.liquid("water"), 20)'
  sleep 8
  echo 'js "T82 rubber=" + pw.items.get(Vars.content.item("ironworks-rubber"))'

  # ---- 83. 聚合车间：汽油 + 氧化铝催化 -> 塑料 ----
  echo 'js Vars.world.tile(42,100).setBlock(Vars.content.block("power-source"), Vars.state.rules.defaultTeam)'
  echo 'js Vars.world.tile(44,100).setBlock(Vars.content.block("ironworks-polymer-works"), Vars.state.rules.defaultTeam)'
  sleep 1
  echo 'js var pl=Vars.world.tile(44,100).build; pl.recipeIndex=1; pl.items.add(Vars.content.item("ironworks-alumina"), 3); pl.liquids.add(Vars.content.liquid("ironworks-gasoline"), 30)'
  sleep 8
  echo 'js "T83 plastic=" + pl.items.get(Vars.content.item("ironworks-plastic"))'

  # ---- 84. 冷却厂：水 + 塑料 -> 冷冻液 ----
  echo 'js Vars.world.tile(52,100).setBlock(Vars.content.block("power-source"), Vars.state.rules.defaultTeam)'
  echo 'js Vars.world.tile(50,100).setBlock(Vars.content.block("ironworks-coolant-plant"), Vars.state.rules.defaultTeam)'
  sleep 1
  echo 'js var cp=Vars.world.tile(50,100).build; cp.items.add(Vars.content.item("ironworks-plastic"), 4); cp.liquids.add(Vars.content.liquid("water"), 40)'
  sleep 8
  echo 'js "T84 chiller=" + Math.round(cp.liquids.get(Vars.content.liquid("ironworks-chiller")))'

  # ---- 85. 天罚：参数与四炮口配置 ----
  echo 'js var tf=Vars.content.block("ironworks-tianfa"); Log.info("T85 size=" + tf.size + " range=" + tf.range + " power=" + tf.consumePower + " muzzles=" + tf.muzzleX.length + " coolant=" + tf.coolantLiquids.length + " overload=" + (Vars.content.statusEffects().find(function(s){return s.name.equals("ironworks-overload")}) != null))'

  # ---- 86. 所有炮塔必须有 shootType 或 ammoTypes（否则详情页 StatValues.ammo 会 NPE 崩游戏） ----
  echo 'js function isTurret(b){var c=b.getClass(); while(c!=null){if(c.getName().indexOf("Turret")>=0) return true; c=c.getSuperclass();} return false;} var bad=[]; var bs=Vars.content.blocks(); for(var i=0;i<bs.size;i++){var b=bs.get(i); if(b.minfo==null||b.minfo.mod==null||!isTurret(b)) continue; var st=b.shootType; var am=b.ammoTypes; if(st==null && am==null) bad.push(b.name);} Log.info("T86 noAmmoOrShoot=" + bad.length + " :: " + bad.join(","))'

  echo 'exit'
) | timeout 520 "$JDK/bin/java" -jar "$JAR" 2>&1 | sed 's/\x1b\[[0-9;]*m//g' | grep -E "^\[.*\] \[I\] T[0-9]+[a-z]? |Error|Exception"
