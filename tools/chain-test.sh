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

  # ---- 87. 天罚炮口坐标：必须朝上且在方块范围内（世界单位，半宽 = size*4） ----
  echo 'js var tb=Vars.content.block("ironworks-tianfa"); var half=tb.size*4; var ok=true; for(var i=0;i<tb.muzzleY.length;i++){ if(tb.muzzleY[i] <= 0 || tb.muzzleY[i] > half || Math.abs(tb.muzzleX[i]) > half) ok=false; } Log.info("T87 muzzleInBounds=" + ok + " half=" + half + " y0=" + tb.muzzleY[0] + " x0=" + tb.muzzleX[0])'

  # ---- 88. 天罚远程连锁：射程 45 格、最多 12 连、每连 120 伤害 ----
  echo 'js var tb=Vars.content.block("ironworks-tianfa"); Log.info("T88 range=" + tb.range + " chain=" + tb.chainTargets + " chainDmg=" + tb.chainDamage + " chainR=" + tb.chainRange)'
  # ---- 90. 天罚真实供给：液体源 + 导管（不是直接投液），闲置时应当存住 ----
  echo 'js Vars.world.tile(100,200).setBlock(Vars.content.block("liquid-source"), Vars.state.rules.defaultTeam)'
  echo 'js Vars.world.tile(101,200).setBlock(Vars.content.block("conduit"), Vars.state.rules.defaultTeam)'
  echo 'js Vars.world.tile(102,200).setBlock(Vars.content.block("conduit"), Vars.state.rules.defaultTeam)'
  echo 'js Vars.world.tile(103,200).setBlock(Vars.content.block("conduit"), Vars.state.rules.defaultTeam)'
  echo 'js Vars.world.tile(105,200).setBlock(Vars.content.block("ironworks-tianfa"), Vars.state.rules.defaultTeam)'
  sleep 2
  echo 'js var src=Vars.world.tile(100,200).build; src.source=Vars.content.liquid("ironworks-chiller"); Log.info("T90 srcSet=" + (src.source != null))'
  sleep 8
  echo 'js Log.info("T90b turret=" + Math.round(Vars.world.tile(105,200).build.liquids.get(Vars.content.liquid("ironworks-chiller"))))'
  # ---- 91. 天罚冷却配置：1440 容量 / 120 每秒 / 虚拟热槽 ----
  echo 'js var tb=Vars.content.block("ironworks-tianfa"); Log.info("T91 cap=" + tb.liquidCapacity + " use=" + tb.coolantUse + " hotCap=" + tb.hotCapacity + " virtualHot=" + tb.virtualHot + " consumeOnlyFiring=" + tb.consumeOnlyWhenFiring)'

  # ---- 92. 多配方机器只收当前配方的液体（转炉：1 号配方收熔融铜铅钛，不收水） ----
  echo 'js Vars.world.tile(52,130).setBlock(Vars.content.block("power-source"), Vars.state.rules.defaultTeam)'
  echo 'js Vars.world.tile(55,130).setBlock(Vars.content.block("ironworks-converter"), Vars.state.rules.defaultTeam)'
  sleep 2
  echo 'js var cv=Vars.world.tile(55,130).build; var w=Vars.content.liquid("water"); var fe=Vars.content.liquid("ironworks-molten-iron"); Log.info("T92 waterAcc=" + cv.acceptLiquid(cv, w) + " ironAcc=" + cv.acceptLiquid(cv, fe))'

  # ---- 93. 热冷却液堆满 → 强制过热、停止开火 ----
  echo 'js Vars.world.tile(130,200).setBlock(Vars.content.block("ironworks-tianfa"), Vars.state.rules.defaultTeam)'
  echo 'js Vars.world.tile(133,200).setBlock(Vars.content.block("power-source"), Vars.state.rules.defaultTeam)'
  sleep 2
  echo 'js var hb=Vars.world.tile(130,200).build; hb.hotStored=1440; Log.info("T93 hot=" + Math.round(hb.hotStored))'
  echo 'js var dt=null; var us=Vars.content.units(); for(var i=0;i<us.size;i++){if(us.get(i).name=="dagger") dt=us.get(i);} var tgt=dt.spawn(Vars.state.rules.waveTeam, 140*8, 200*8); Log.info("T93b spawnHp=" + Math.round(tgt.health))'
  sleep 4
  echo 'js Log.info("T93c heat=" + Math.round(Vars.world.tile(130,200).build.overheat) + " hot=" + Math.round(Vars.world.tile(130,200).build.hotStored))'
  # ---- 94. 内容静态检查：颜色/容量/配方输出与液体方向 ----
  echo 'js function hasCls(b,n){var c=b.getClass(); while(c!=null){if(c.getName().indexOf(n)>=0) return true; c=c.getSuperclass();} return false;} var iss=[]; var bs=Vars.content.blocks(); for(var i=0;i<bs.size;i++){var b=bs.get(i); if(b.minfo==null||b.minfo.mod==null) continue; if(b.hasLiquids && b.liquidCapacity<=0) iss.push(b.name+":cap"); if(hasCls(b,"MultiCrafter")){for(var k=0;k<b.recipes.length;k++){var r=b.recipes[k]; if(r.outputs.length==0 && r.liquidOutputs.length==0) iss.push(b.name+"/"+r.name+":noOut"); if(r.liquidOutputs.length>1 && (b.liquidOutputDirections==null || b.liquidOutputDirections.length<r.liquidOutputs.length)) iss.push(b.name+"/"+r.name+":dirs");}}} var ls=Vars.content.liquids(); for(var j=0;j<ls.size;j++){var l=ls.get(j); if(l.minfo!=null && l.minfo.mod!=null && l.color==null) iss.push("liquid:"+l.name);} Log.info("T94 lintIssues=" + iss.length + " :: " + iss.join(","))'

  # ---- 95. 全域算力集群（4×4 核心 + 链接块拼 4×6）：挂载量子模块 + 供电 + 冷却液 → 超速生效 ----
  echo 'js Vars.world.tile(220,240).setBlock(Vars.content.block("ironworks-compute-cluster"), Vars.state.rules.defaultTeam)'
  echo 'js Vars.world.tile(223,240).setBlock(Vars.content.block("power-source"), Vars.state.rules.defaultTeam)'
  echo 'js Vars.world.tile(223,241).setBlock(Vars.content.block("liquid-source"), Vars.state.rules.defaultTeam)'
  echo 'js Vars.world.tile(217,240).setBlock(Vars.content.block("ironworks-compute-quantum"), Vars.state.rules.defaultTeam)'
  sleep 2
  echo 'js Vars.world.tile(223,241).build.source=Vars.content.liquid("ironworks-graphene-coolant")'
  sleep 5
  echo 'js var cb=Vars.world.tile(220,240).build; Log.info("T95 mods=" + cb.moduleCount + " bonus=" + Math.round(cb.moduleBonus*100) + " boost=" + Math.round(cb.appliedBoost*100) + " eff=" + cb.efficiency)'

  # ---- 96. 挂载上限：4×6 四周铺满 10 个量子模块（正好 200%）时加成封顶 ----
  echo 'js Vars.world.tile(150,240).setBlock(Vars.content.block("ironworks-compute-cluster"), Vars.state.rules.defaultTeam)'
  echo 'js for(var k=0;k<3;k++){Vars.world.tile(147,237+k*2).setBlock(Vars.content.block("ironworks-compute-quantum"),Vars.state.rules.defaultTeam); Vars.world.tile(153,237+k*2).setBlock(Vars.content.block("ironworks-compute-quantum"),Vars.state.rules.defaultTeam);} for(var k=0;k<2;k++){Vars.world.tile(149+k*2,235).setBlock(Vars.content.block("ironworks-compute-quantum"),Vars.state.rules.defaultTeam); Vars.world.tile(149+k*2,243).setBlock(Vars.content.block("ironworks-compute-quantum"),Vars.state.rules.defaultTeam);}'
  sleep 3
  echo 'js var cb2=Vars.world.tile(150,240).build; Log.info("T96 mods=" + cb2.moduleCount + " bonus=" + Math.round(cb2.moduleBonus*100) + " capped=" + (cb2.moduleBonus <= 2.001))'

  # ---- 97. 整边贴合 + 四方向识别：只贴 1 格不算；上下左右 dir = 0/1/2/3 ----
  echo 'js Vars.world.tile(180,240).setBlock(Vars.content.block("ironworks-compute-cluster"), Vars.state.rules.defaultTeam)'
  echo 'js Vars.world.tile(184,237).setBlock(Vars.content.block("ironworks-compute-basic"), Vars.state.rules.defaultTeam)'
  sleep 2
  echo 'js Log.info("T97a partial mods=" + Vars.world.tile(180,240).build.moduleCount + " attached=" + Vars.world.tile(184,237).build.attached)'
  echo 'js Vars.world.tile(184,237).setBlock(Vars.content.block("air"), Vars.state.rules.defaultTeam)'
  echo 'js Vars.world.tile(184,240).setBlock(Vars.content.block("ironworks-compute-basic"), Vars.state.rules.defaultTeam)'
  echo 'js Vars.world.tile(177,240).setBlock(Vars.content.block("ironworks-compute-basic"), Vars.state.rules.defaultTeam)'
  echo 'js Vars.world.tile(180,235).setBlock(Vars.content.block("ironworks-compute-basic"), Vars.state.rules.defaultTeam)'
  echo 'js Vars.world.tile(180,244).setBlock(Vars.content.block("ironworks-compute-basic"), Vars.state.rules.defaultTeam)'
  sleep 3
  echo 'js var rb=Vars.world.tile(184,240).build; var lb=Vars.world.tile(177,240).build; var bb=Vars.world.tile(180,235).build; var ub=Vars.world.tile(180,244).build; Log.info("T97b full mods=" + Vars.world.tile(180,240).build.moduleCount + " R=" + rb.dir + " L=" + lb.dir + " D=" + bb.dir + " U=" + ub.dir)'

  # ---- 98. 矩形占地：主集群 4×6 / 高级模块 2×3 的链接块确实生成并绑定核心 ----
  echo 'js Vars.world.tile(160,240).setBlock(Vars.content.block("ironworks-compute-cluster"), Vars.state.rules.defaultTeam)'
  echo 'js Vars.world.tile(163,240).setBlock(Vars.content.block("ironworks-compute-advanced"), Vars.state.rules.defaultTeam)'
  sleep 3
  echo 'js var c=Vars.world.tile(160,240).build; var cl=Vars.world.tile(159,237).build; var cr=Vars.world.tile(162,238).build; var adv=Vars.world.tile(163,240).build; var al=Vars.world.tile(163,239).build; Log.info("T98 clusterLinks=" + (cl!=null && cl.host==c) + "," + (cr!=null && cr.host==c) + " advLink=" + (al!=null && al.host==adv) + " advAttached=" + adv.attached + " advDir=" + adv.dir)'

  # ---- 99. 拆除主块时链接块一起消失（含模块的通风块） ----
  echo 'js Vars.world.tile(160,240).remove()'
  sleep 2
  echo 'js var t=Vars.content.block("air"); var gone=(Vars.world.tile(159,237).block()==t) && (Vars.world.tile(162,238).block()==t); Log.info("T99a clusterLinksGone=" + gone)'
  echo 'js Vars.world.tile(163,240).remove()'
  sleep 2
  echo 'js var agone=(Vars.world.tile(163,239).block()==Vars.content.block("air")); Log.info("T99b advLinkGone=" + agone)'

  # ---- 100. 读档重绑：链接块按相对坐标重新找到主块（host 置空后 10 tick 内恢复） ----
  echo 'js Vars.world.tile(200,220).setBlock(Vars.content.block("ironworks-compute-cluster"), Vars.state.rules.defaultTeam)'
  sleep 3
  echo 'js var lk=Vars.world.tile(199,217).build; Log.info("T100a link=" + (lk!=null) + " rel=" + (lk!=null ? (lk.relX + "," + lk.relY) : "-") + " bound=" + (lk!=null && lk.host!=null))'
  echo 'js Vars.world.tile(199,217).build.host=null'
  sleep 1
  echo 'js var lk2=Vars.world.tile(199,217).build; Log.info("T100b rebound=" + (lk2!=null && lk2.host==Vars.world.tile(200,220).build))'

  # ---- 101. 冷却液转化：240/秒石墨烯冷却液 → 热冷却液，容量 2000，基础超速 50% ----
  echo 'js Vars.world.tile(140,220).setBlock(Vars.content.block("ironworks-compute-cluster"), Vars.state.rules.defaultTeam)'
  echo 'js Vars.world.tile(143,220).setBlock(Vars.content.block("power-source"), Vars.state.rules.defaultTeam)'
  echo 'js Vars.world.tile(143,221).setBlock(Vars.content.block("liquid-source"), Vars.state.rules.defaultTeam)'
  sleep 3
  echo 'js Vars.world.tile(143,221).build.source=Vars.content.liquid("ironworks-graphene-coolant")'
  sleep 2
  echo 'js var h=Vars.content.liquid("ironworks-hot-coolant"); var c=Vars.world.tile(140,220).build; Log.info("T101a cap=" + c.block.liquidCapacity + " boost=" + Math.round(c.appliedBoost*100) + " hot=" + Math.round(c.liquids.get(h)))'
  sleep 3
  echo 'js var h=Vars.content.liquid("ironworks-hot-coolant"); var c=Vars.world.tile(140,220).build; Log.info("T101b hot2=" + Math.round(c.liquids.get(h)) + " cold=" + Math.round(c.liquids.get(Vars.content.liquid("ironworks-graphene-coolant"))))'

  # ---- 102. 冷却液不超上限；热冷却液能从链接块一侧排进管道/储罐 ----
  echo 'js for(var y=232;y<=244;y++){for(var x=196;x<=207;x++){Vars.world.tile(x,y).setBlock(Vars.content.block("air"), Vars.state.rules.defaultTeam);}}'
  echo 'js Vars.world.tile(200,240).setBlock(Vars.content.block("ironworks-compute-cluster"), Vars.state.rules.defaultTeam)'
  echo 'js Vars.world.tile(203,240).setBlock(Vars.content.block("power-source"), Vars.state.rules.defaultTeam)'
  echo 'js Vars.world.tile(198,237).setBlock(Vars.content.block("liquid-source"), Vars.state.rules.defaultTeam)'
  echo 'js Vars.world.tile(201,235).setBlock(Vars.content.block("liquid-tank"), Vars.state.rules.defaultTeam)'
  sleep 3
  echo 'js Vars.world.tile(198,237).build.source=Vars.content.liquid("ironworks-graphene-coolant")'
  sleep 6
  echo 'js var c=Vars.world.tile(200,240).build; var t=Vars.world.tile(201,235).build; var cold=Vars.content.liquid("ironworks-graphene-coolant"); var hot=Vars.content.liquid("ironworks-hot-coolant"); Log.info("T102 links=" + ((Vars.world.tile(199,237).build!=null?1:0)+(Vars.world.tile(201,237).build!=null?1:0)) + " clamped=" + (c.liquids.get(cold) <= c.block.liquidCapacity + 1) + " cold=" + Math.round(c.liquids.get(cold)) + " hot=" + Math.round(c.liquids.get(hot)) + " tankHot=" + Math.round(t.liquids.get(hot)))'

  # ---- 103. 物品跃迁节点·传送：不储存，直接送到连接处 ----
  echo 'js Vars.state.rules.waves = false; var wt=Vars.state.rules.waveTeam; var us=wt.data().units; for(var ui=us.size-1;ui>=0;ui--){us.get(ui).kill();} for(var y=194;y<=212;y++){for(var x=140;x<=200;x++){Vars.world.tile(x,y).setBlock(Vars.content.block("air"), Vars.state.rules.defaultTeam);}}'
  echo 'js Vars.world.tile(150,200).setBlock(Vars.content.block("ironworks-mct-item-node"), Vars.state.rules.defaultTeam)'
  echo 'js Vars.world.tile(149,200).setBlock(Vars.content.block("power-source"), Vars.state.rules.defaultTeam)'
  echo 'js Vars.world.tile(152,200).setBlock(Vars.content.block("container"), Vars.state.rules.defaultTeam)'
  sleep 3
  echo 'js var n=Vars.world.tile(150,200).build; var c=Vars.world.tile(152,200).build; n.linkTo(c); var cu=Vars.content.item("copper"); var ok=n.acceptItem(c, cu); n.handleItem(c, cu); Log.info("T103 accept=" + ok + " node=" + n.items.total() + " target=" + c.items.get(cu) + " mode=" + n.mode)'

  # ---- 104. 物品跃迁节点·吸取：只抽产出，向四邻分发 ----
  echo 'js Vars.world.tile(160,200).setBlock(Vars.content.block("graphite-press"), Vars.state.rules.defaultTeam)'
  echo 'js Vars.world.tile(159,200).setBlock(Vars.content.block("power-source"), Vars.state.rules.defaultTeam)'
  echo 'js Vars.world.tile(162,200).setBlock(Vars.content.block("ironworks-mct-item-node"), Vars.state.rules.defaultTeam)'
  echo 'js Vars.world.tile(163,200).setBlock(Vars.content.block("power-source"), Vars.state.rules.defaultTeam)'
  echo 'js Vars.world.tile(162,201).setBlock(Vars.content.block("container"), Vars.state.rules.defaultTeam)'
  sleep 3
  echo 'js Vars.world.tile(160,200).build.items.add(Vars.content.item("coal"), 20)'
  echo 'js var n=Vars.world.tile(162,200).build; n.linkTo(Vars.world.tile(160,200).build); n.setMode(1)'
  sleep 6
  echo 'js var n=Vars.world.tile(162,200).build; var c=Vars.world.tile(162,201).build; Log.info("T104 graphite=" + c.items.get(Vars.content.item("graphite")) + " node=" + n.items.total() + " coalLeft=" + Vars.world.tile(160,200).build.items.get(Vars.content.item("coal")))'

  # ---- 105. 液体跃迁节点·传送：管道液体直接送到连接处 ----
  echo 'js Vars.world.tile(171,200).setBlock(Vars.content.block("ironworks-mct-liquid-node"), Vars.state.rules.defaultTeam)'
  echo 'js Vars.world.tile(170,200).setBlock(Vars.content.block("liquid-source"), Vars.state.rules.defaultTeam)'
  echo 'js Vars.world.tile(171,201).setBlock(Vars.content.block("power-source"), Vars.state.rules.defaultTeam)'
  echo 'js Vars.world.tile(173,200).setBlock(Vars.content.block("liquid-tank"), Vars.state.rules.defaultTeam)'
  sleep 3
  echo 'js Vars.world.tile(170,200).build.source=Vars.content.liquid("water")'
  echo 'js var n=Vars.world.tile(171,200).build; n.linkTo(Vars.world.tile(173,200).build)'
  sleep 5
  echo 'js var w=Vars.content.liquid("water"); var n=Vars.world.tile(171,200).build; var t=Vars.world.tile(173,200).build; Log.info("T105 tank=" + Math.round(t.liquids.get(w)) + " node=" + Math.round(n.liquids.get(w)))'

  # ---- 106. 液体跃迁节点·吸取：抽取产出液体并向四邻分发 ----
  echo 'js Vars.world.tile(180,200).setBlock(Vars.content.block("liquid-source"), Vars.state.rules.defaultTeam)'
  echo 'js Vars.world.tile(181,200).setBlock(Vars.content.block("ironworks-mct-liquid-node"), Vars.state.rules.defaultTeam)'
  echo 'js Vars.world.tile(181,201).setBlock(Vars.content.block("power-source"), Vars.state.rules.defaultTeam)'
  echo 'js Vars.world.tile(183,200).setBlock(Vars.content.block("liquid-tank"), Vars.state.rules.defaultTeam)'
  sleep 3
  echo 'js Vars.world.tile(180,200).build.source=Vars.content.liquid("water")'
  echo 'js var n=Vars.world.tile(181,200).build; n.linkTo(Vars.world.tile(180,200).build); n.setMode(1)'
  sleep 5
  echo 'js var w=Vars.content.liquid("water"); var t=Vars.world.tile(183,200).build; Log.info("T106 pulled=" + Math.round(t.liquids.get(w)))'

  # ---- 107. 跃迁节点参数：耗电 1080/秒、描述、连接距离 ----
  echo 'js var b=Vars.content.block("ironworks-mct-item-node"); var l=Vars.content.block("ironworks-mct-liquid-node"); Log.info("T107 item=" + (b.consPower.usage*60) + " liquid=" + (l.consPower.usage*60) + " desc=" + (b.description.indexOf("MCT")>=0) + " size=" + b.size)'

  # ---- 108. 链接块位置被占：不能放置；强制放置不吃掉已有建筑 ----
  echo 'js for(var y=234;y<=246;y++){for(var x=154;x<=168;x++){Vars.world.tile(x,y).setBlock(Vars.content.block("air"), Vars.state.rules.defaultTeam);}}'
  echo 'js Vars.world.tile(161,239).setBlock(Vars.content.block("conveyor"), Vars.state.rules.defaultTeam)'
  echo 'js var adv=Vars.content.block("ironworks-compute-advanced"); Log.info("T108a canPlace=" + adv.canPlaceOn(Vars.world.tile(160,240), Vars.state.rules.defaultTeam, 0))'
  echo 'js Vars.world.tile(160,240).setBlock(Vars.content.block("ironworks-compute-advanced"), Vars.state.rules.defaultTeam)'
  sleep 2
  echo 'js var link=Vars.content.block("ironworks-compute-advanced-link"); var conv=Vars.content.block("conveyor"); Log.info("T108b conv=" + (Vars.world.tile(161,239).block()==conv) + " link0=" + (Vars.world.tile(160,239).block()==link) + " link1=" + (Vars.world.tile(161,239).block()==link))'

  # ---- 109. 液体节点·吸取：从储液罐抽水并向四邻分发 ----
  echo 'js for(var y=214;y<=226;y++){for(var x=172;x<=192;x++){Vars.world.tile(x,y).setBlock(Vars.content.block("air"), Vars.state.rules.defaultTeam);}}'
  echo 'js Vars.world.tile(180,220).setBlock(Vars.content.block("liquid-tank"), Vars.state.rules.defaultTeam)'
  echo 'js Vars.world.tile(182,220).setBlock(Vars.content.block("ironworks-mct-liquid-node"), Vars.state.rules.defaultTeam)'
  echo 'js Vars.world.tile(182,221).setBlock(Vars.content.block("power-source"), Vars.state.rules.defaultTeam)'
  echo 'js Vars.world.tile(184,220).setBlock(Vars.content.block("liquid-tank"), Vars.state.rules.defaultTeam)'
  sleep 2
  echo 'js Vars.world.tile(180,220).build.liquids.add(Vars.content.liquid("water"), 1200)'
  echo 'js var n=Vars.world.tile(182,220).build; n.linkTo(Vars.world.tile(180,220).build); n.setMode(1)'
  sleep 4
  echo 'js var w=Vars.content.liquid("water"); var src=Vars.world.tile(180,220).build; var dst=Vars.world.tile(184,220).build; Log.info("T109 src=" + Math.round(src.liquids.get(w)) + " dst=" + Math.round(dst.liquids.get(w)))'

  # ---- 110. 液体节点·吸取：从全域算力集群抽热冷却液 ----
  echo 'js for(var y=210;y<=224;y++){for(var x=196;x<=210;x++){Vars.world.tile(x,y).setBlock(Vars.content.block("air"), Vars.state.rules.defaultTeam);}}'
  echo 'js Vars.world.tile(200,218).setBlock(Vars.content.block("ironworks-compute-cluster"), Vars.state.rules.defaultTeam)'
  echo 'js Vars.world.tile(203,218).setBlock(Vars.content.block("ironworks-mct-liquid-node"), Vars.state.rules.defaultTeam)'
  echo 'js Vars.world.tile(203,219).setBlock(Vars.content.block("power-source"), Vars.state.rules.defaultTeam)'
  echo 'js Vars.world.tile(205,218).setBlock(Vars.content.block("liquid-tank"), Vars.state.rules.defaultTeam)'
  sleep 3
  echo 'js Vars.world.tile(200,218).build.liquids.add(Vars.content.liquid("ironworks-hot-coolant"), 900)'
  echo 'js var n=Vars.world.tile(203,218).build; n.linkTo(Vars.world.tile(200,218).build); n.setMode(1)'
  sleep 4
  echo 'js var hot=Vars.content.liquid("ironworks-hot-coolant"); var t=Vars.world.tile(205,218).build; Log.info("T110 tankHot=" + Math.round(t.liquids.get(hot)))'

  # ---- 111. 液体节点·吸取：从炮塔的虚拟热槽抽热冷却液 ----
  echo 'js for(var y=192;y<=210;y++){for(var x=190;x<=216;x++){Vars.world.tile(x,y).setBlock(Vars.content.block("air"), Vars.state.rules.defaultTeam);}}'
  echo 'js Vars.world.tile(200,200).setBlock(Vars.content.block("ironworks-tianfa"), Vars.state.rules.defaultTeam)'
  echo 'js Vars.world.tile(203,200).setBlock(Vars.content.block("ironworks-mct-liquid-node"), Vars.state.rules.defaultTeam)'
  echo 'js Vars.world.tile(203,201).setBlock(Vars.content.block("power-source"), Vars.state.rules.defaultTeam)'
  echo 'js Vars.world.tile(205,200).setBlock(Vars.content.block("liquid-tank"), Vars.state.rules.defaultTeam)'
  sleep 3
  echo 'js Vars.world.tile(200,200).build.hotStored = 600'
  echo 'js var n=Vars.world.tile(203,200).build; n.linkTo(Vars.world.tile(200,200).build); n.setMode(1)'
  sleep 3
  echo 'js var hot=Vars.content.liquid("ironworks-hot-coolant"); var t=Vars.world.tile(205,200).build; var tf=Vars.world.tile(200,200).build; Log.info("T111 tankHot=" + Math.round(t.liquids.get(hot)) + " turretLeft=" + Math.round(tf.hotStored))'

  # ---- 112. 2×2 链接块：只查锚点会吞掉另一格建筑（整块占地都要检查） ----
  echo 'js for(var y=232;y<=246;y++){for(var x=154;x<=170;x++){Vars.world.tile(x,y).setBlock(Vars.content.block("air"), Vars.state.rules.defaultTeam);}}'
  echo 'js Vars.world.tile(160,237).setBlock(Vars.content.block("thorium-wall"), Vars.state.rules.defaultTeam)'
  echo 'js Vars.world.tile(160,240).setBlock(Vars.content.block("ironworks-compute-cluster"), Vars.state.rules.defaultTeam)'
  sleep 2
  echo 'js var link=Vars.content.block("ironworks-compute-cluster-link"); var wall=Vars.content.block("thorium-wall"); Log.info("T112 wall=" + (Vars.world.tile(160,237).block()==wall) + " leftLink=" + (Vars.world.tile(159,237).block()==link) + " rightLink=" + (Vars.world.tile(161,237).block()==link))'

  # ---- 113. 高级模块链接行有钍墙：不生成链接、墙保留、拆除后墙还在 ----
  echo 'js for(var y=0;y<=8;y++){for(var x=0;x<=8;x++){Vars.world.tile(x,y).setBlock(Vars.content.block("air"), Vars.state.rules.defaultTeam);}}'
  echo 'js Vars.world.tile(5,1).setBlock(Vars.content.block("thorium-wall"), Vars.state.rules.defaultTeam); Vars.world.tile(6,1).setBlock(Vars.content.block("thorium-wall"), Vars.state.rules.defaultTeam)'
  echo 'js Vars.world.tile(5,2).setBlock(Vars.content.block("ironworks-compute-advanced"), Vars.state.rules.defaultTeam)'
  sleep 2
  echo 'js var wall=Vars.content.block("thorium-wall"); var link=Vars.content.block("ironworks-compute-advanced-link"); Log.info("T113a walls=" + (Vars.world.tile(5,1).block()==wall) + "," + (Vars.world.tile(6,1).block()==wall) + " links=" + (Vars.world.tile(5,1).block()==link) + "," + (Vars.world.tile(6,1).block()==link))'
  echo 'js Vars.world.tile(5,2).remove()'
  sleep 1
  echo 'js var wall=Vars.content.block("thorium-wall"); Log.info("T113b afterRemove=" + (Vars.world.tile(5,1).block()==wall) + "," + (Vars.world.tile(6,1).block()==wall))'

  # ---- 114. 高级模块 vs 集群：逐个比对 Block 属性（父类不同带来的实现差距） ----
  echo 'js var A=Vars.content.block("ironworks-compute-advanced"), C=Vars.content.block("ironworks-compute-cluster"); Log.info("T114a cached=" + A.drawCached + "/" + C.drawCached + " dyn=" + A.drawDynamic + "/" + C.drawDynamic + " over=" + A.canOverdrive + "/" + C.canOverdrive + " same=" + (A.drawCached==C.drawCached && A.drawDynamic==C.drawDynamic && A.canOverdrive==C.canOverdrive && A.solid==C.solid))'

  # 114b. 模块实测占地（应为 6 格 / 2×3），贴图中心相对引擎中心的下移量
  echo 'js var T=Vars.state.rules.defaultTeam; for(var y=0;y<=11;y++){for(var x=0;x<=11;x++){Vars.world.tile(x,y).setBlock(Vars.content.block("air"), T);}}'
  echo 'js Vars.world.tile(5,5).setBlock(Vars.content.block("ironworks-compute-advanced"), T)'
  sleep 2
  echo 'js var A=Vars.content.block("ironworks-compute-advanced"), L=Vars.content.block("ironworks-compute-advanced-link"); var n=0,x1=99,x2=-1,y1=99,y2=-1; for(var y=0;y<=11;y++){for(var x=0;x<=11;x++){var b=Vars.world.tile(x,y).block(); if(b==A||b==L){n++; if(x<x1)x1=x; if(x>x2)x2=x; if(y<y1)y1=y; if(y>y2)y2=y;}}} var bb=Vars.world.tile(5,5).build; Log.info("T114b advTiles=" + n + " box=" + (x2-x1+1) + "x" + (y2-y1+1) + " dx=" + Math.round((x1+x2)/2*8 - bb.x) + " dy=" + Math.round((y1+y2)/2*8 - bb.y))'

  # 114c. 集群实测占地（应为 24 格 / 4×6），验证同一套下移规则
  echo 'js var T=Vars.state.rules.defaultTeam; for(var y=0;y<=12;y++){for(var x=0;x<=12;x++){Vars.world.tile(x,y).setBlock(Vars.content.block("air"), T);}}'
  echo 'js Vars.world.tile(6,6).setBlock(Vars.content.block("ironworks-compute-cluster"), T)'
  sleep 2
  echo 'js var C=Vars.content.block("ironworks-compute-cluster"), L=Vars.content.block("ironworks-compute-cluster-link"); var n=0,x1=99,x2=-1,y1=99,y2=-1; for(var y=0;y<=12;y++){for(var x=0;x<=12;x++){var b=Vars.world.tile(x,y).block(); if(b==C||b==L){n++; if(x<x1)x1=x; if(x>x2)x2=x; if(y<y1)y1=y; if(y>y2)y2=y;}}} var bb=Vars.world.tile(6,6).build; Log.info("T114c clusterTiles=" + n + " box=" + (x2-x1+1) + "x" + (y2-y1+1) + " dx=" + Math.round((x1+x2)/2*8 - bb.x) + " dy=" + Math.round((y1+y2)/2*8 - bb.y))'

  # ---- 115. 链接块不可被「合法替换」：墙/模块不能压在 2×3 占地上 ----
  echo 'js var A=Vars.content.block("ironworks-compute-advanced"), C=Vars.content.block("ironworks-compute-cluster"), AL=Vars.content.block("ironworks-compute-advanced-link"), CL=Vars.content.block("ironworks-compute-cluster-link"), W=Vars.content.block("thorium-wall"); Log.info("T115a wallOnLink=" + W.canReplace(AL) + "," + W.canReplace(CL) + " advOnWall=" + A.canReplace(W) + " clusterOnLink=" + C.canReplace(CL) + " repl=" + AL.replaceable + " groups=" + A.group + "/" + C.group + "/" + AL.group)'

  # 115b. 链接块被强行清掉（模拟沙盒调色板/蓝图）必须自动补回，模块不许停在 2×2
  echo 'js var T=Vars.state.rules.defaultTeam; for(var y=0;y<=11;y++){for(var x=0;x<=11;x++){Vars.world.tile(x,y).setBlock(Vars.content.block("air"), T);}}'
  echo 'js Vars.world.tile(5,5).setBlock(Vars.content.block("ironworks-compute-advanced"), T)'
  sleep 2
  echo 'js Vars.world.tile(5,4).remove(); Vars.world.tile(6,4).remove()'
  sleep 2
  echo 'js var A=Vars.content.block("ironworks-compute-advanced"), L=Vars.content.block("ironworks-compute-advanced-link"); var n=0; for(var y=0;y<=11;y++){for(var x=0;x<=11;x++){var b=Vars.world.tile(x,y).block(); if(b==A||b==L)n++;}} var t1=Vars.world.tile(5,4).block(), t2=Vars.world.tile(6,4).block(); Log.info("T115b tiles=" + n + " links=" + (t1==L) + "," + (t2==L) + " solid=" + (t1.solid && t2.solid))'

  # ---- 116. RBMK 核电站：发电 / 提插棒 / 失冷升温 / 汽轮机 / 熔毁+辐射 ----
  # 说明：用 b.updateTile() 手动步进，等价于跑 N tick，不阻塞游戏线程（Thread.sleep 会卡住整个模拟）
  echo 'js var T=Vars.state.rules.defaultTeam; for(var y=52;y<=74;y++){for(var x=52;x<=104;x++){var t=Vars.world.tile(x,y); if(t!=null)t.setBlock(Vars.content.block("air"),T);}}'
  # 116a：水 500 + 钍 30，插棒 20%，步进 360 tick（≈6 秒）
  echo 'js var T=Vars.state.rules.defaultTeam; Vars.world.tile(58,56).setBlock(Vars.content.block("ironworks-rbmk-reactor"),T); var b=Vars.world.tile(58,56).build; b.items.add(Vars.content.item("thorium"),30); b.liquids.add(Vars.content.liquid("water"),500); b.targetRods=0.2; b.rods=0.2; for(var i=0;i<360;i++) b.updateTile(); var hot=Vars.content.liquid("ironworks-steam"); Log.info("T116 flux=" + Math.round(b.flux*100) + " heat=" + Math.round(b.heat*100) + " power=" + Math.round(b.getPowerProduction()*60) + " steam=" + Math.round(b.liquids.get(hot)) + " water=" + Math.round(b.liquids.get(Vars.content.liquid("water"))))'
  # 116b：插棒到底，通量必须掉一半以上
  echo 'js var b=Vars.world.tile(58,56).build; var f1=b.flux; b.targetRods=1; b.rods=1; for(var i=0;i<360;i++) b.updateTile(); Log.info("T117 dropped=" + (b.flux < f1*0.5) + " f1=" + Math.round(f1*100) + " f2=" + Math.round(b.flux*100))'
  # 116c：抽干冷却水 → flow=0、堆温上涨
  echo 'js var b=Vars.world.tile(58,56).build; b.targetRods=0; b.rods=0; b.heat=0.4; b.liquids.clear(); var h1=b.heat; for(var i=0;i<360;i++) b.updateTile(); Log.info("T118 rise=" + (b.heat > h1 + 0.05) + " flow=" + Math.round(b.flow*100) + " h1=" + Math.round(h1*100) + " h2=" + Math.round(b.heat*100))'
  # 116d：汽轮机吃热冷却液 → 电 + 回收水
  echo 'js var T=Vars.state.rules.defaultTeam; Vars.world.tile(80,56).setBlock(Vars.content.block("ironworks-steam-turbine"),T); var b=Vars.world.tile(80,56).build; b.liquids.add(Vars.content.liquid("ironworks-steam"),2000); for(var i=0;i<60;i++) b.updateTile(); Log.info("T119 power=" + Math.round(b.getPowerProduction()*60) + " warm=" + Math.round(b.liquids.get(Vars.content.liquid("ironworks-warm-water"))) + " flow=" + Math.round(b.flow*100))'
  # 116e：超温 → 熔毁（反应堆消失；旁边钍墙在下个 tick 被爆炸打伤；爆心外的单位吃到辐射）
  echo 'js Vars.state.rules.reactorExplosions=true; var T=Vars.state.rules.defaultTeam; Vars.world.tile(70,60).setBlock(Vars.content.block("ironworks-rbmk-reactor"),T); Vars.world.tile(64,60).setBlock(Vars.content.block("thorium-wall"),T); var u=Packages.mindustry.content.UnitTypes.dagger.spawn(T,784,480); var b=Vars.world.tile(70,60).build; b.items.add(Vars.content.item("thorium"),5); b.liquids.add(Vars.content.liquid("water"),500); b.heat=1.6; for(var i=0;i<180 && !b.melted;i++) b.updateTile(); var r=Vars.content.statusEffect("ironworks-radiation"); var cor=Vars.content.block("ironworks-corium"); var cn=0; for(var yy=57;yy<=63;yy++){for(var xx=67;xx<=73;xx++){var tt=Vars.world.tile(xx,yy); if(tt!=null && tt.block()==cor) cn++;}} Log.info("T120a gone=" + (Vars.world.tile(70,60).block()==Vars.content.block("air")) + " melted=" + b.melted + " rad=" + (u!=null && r!=null && u.hasEffect(r)) + " corium=" + cn)'
  sleep 1
  echo 'js var wall=Vars.world.tile(64,60).build; Log.info("T120b wallHurt=" + (wall==null || wall.health<wall.maxHealth) + " wallHp=" + (wall==null?-1:Math.round(wall.health)))'
  # 116f：反应堆必须打开 configurable（否则点击方块根本不弹面板）
  echo 'js var r=Vars.content.block("ironworks-rbmk-reactor"); Log.info("T121 configurable=" + r.configurable + " outputsPower=" + r.outputsPower)'

  # ---- 123. 天罚冷却液档位：原版冷冻液必须被认（0.56.0 漏的） ----
  echo 'js var T=Vars.state.rules.defaultTeam; for(var y=148;y<=152;y++){for(var x=148;x<=152;x++){var t=Vars.world.tile(x,y); if(t!=null)t.setBlock(Vars.content.block("air"),T);}}'
  echo 'js var T=Vars.state.rules.defaultTeam; Vars.world.tile(150,150).setBlock(Vars.content.block("ironworks-tianfa"),T); var b=Vars.world.tile(150,150).build; var k=b.block; var cryo=Vars.content.liquid("cryofluid"); var has=false; for(var i=0;i<k.coolantLiquids.length;i++){if(k.coolantLiquids[i]==cryo) has=true;} Log.info("T123 tiers=" + k.coolantLiquids.length + " hasCryo=" + has + " accept=" + b.acceptLiquid(b, cryo))'

  # ---- 124. 核工业全套：浸出 → 浓缩 → 制棒 → 反应堆烧棒 → 乏燃料池 → 后处理 → 固化 → 干储 ----
  echo 'js var T=Vars.state.rules.defaultTeam; for(var y=108;y<=124;y++){for(var x=108;x<=146;x++){var t=Vars.world.tile(x,y); if(t!=null)t.setBlock(Vars.content.block("air"),T);}}'
  # 124a 浸出：铀矿石 + 酸 + 电 → 黄饼
  echo 'js var T=Vars.state.rules.defaultTeam; Vars.world.tile(110,110).setBlock(Vars.content.block("power-source"),T); Vars.world.tile(112,110).setBlock(Vars.content.block("ironworks-leach-plant"),T); var b=Vars.world.tile(112,110).build; b.items.add(Vars.content.item("ironworks-uranium-ore"),30); b.liquids.add(Vars.content.liquid("ironworks-acid"),60); Log.info("T124a placed=" + (b!=null))'
  sleep 5
  echo 'js var b=Vars.world.tile(112,110).build; Log.info("T124 yellow=" + b.items.get(Vars.content.item("ironworks-yellowcake")) + " ore=" + b.items.get(Vars.content.item("ironworks-uranium-ore")))'
  # 124b 浓缩：黄饼 + 酸 + 电 → 浓缩铀
  echo 'js var T=Vars.state.rules.defaultTeam; Vars.world.tile(122,110).setBlock(Vars.content.block("power-source"),T); Vars.world.tile(124,110).setBlock(Vars.content.block("ironworks-enrichment-plant"),T); var b=Vars.world.tile(124,110).build; b.items.add(Vars.content.item("ironworks-yellowcake"),10); b.liquids.add(Vars.content.liquid("ironworks-acid"),40); Log.info("T125a placed=" + (b!=null))'
  sleep 7
  echo 'js var b=Vars.world.tile(124,110).build; Log.info("T125 enriched=" + b.items.get(Vars.content.item("ironworks-enriched-uranium")))'
  # 124c 制棒：浓缩铀 + 钢板 + 碳化硅 → 燃料棒
  echo 'js var T=Vars.state.rules.defaultTeam; Vars.world.tile(134,110).setBlock(Vars.content.block("power-source"),T); Vars.world.tile(136,110).setBlock(Vars.content.block("ironworks-fuel-factory"),T); var b=Vars.world.tile(136,110).build; b.items.add(Vars.content.item("ironworks-enriched-uranium"),3); b.items.add(Vars.content.item("ironworks-steel-plate"),12); b.items.add(Vars.content.item("ironworks-silicon-carbide"),6); Log.info("T126a placed=" + (b!=null))'
  sleep 5
  echo 'js var b=Vars.world.tile(136,110).build; Log.info("T126 rods=" + b.items.get(Vars.content.item("ironworks-fuel-rod")))'
  # 124d 反应堆烧燃料棒 → 乏燃料棒
  echo 'js var T=Vars.state.rules.defaultTeam; Vars.world.tile(108,118).setBlock(Vars.content.block("ironworks-rbmk-reactor"),T); var b=Vars.world.tile(108,118).build; b.items.add(Vars.content.item("ironworks-fuel-rod"),5); b.liquids.add(Vars.content.liquid("water"),500); b.targetRods=0; b.targetRods=0; b.rods=0; b.flux=1.0; b.fuelUse=1.0; for(var i=0;i<10;i++) b.updateTile(); Log.info("T127 depleted=" + b.items.get(Vars.content.item("ironworks-depleted-rod")) + " rods=" + b.items.get(Vars.content.item("ironworks-fuel-rod")) + " burn=" + Math.round(b.rodBurn*100))'
  # 124e 注硼：先武装（不花东西），接硼酸后才会真正注入；注满即永久中毒
  echo 'js var T=Vars.state.rules.defaultTeam; Vars.world.tile(105,118).setBlock(Vars.content.block("liquid-source"),T); var s=Vars.world.tile(105,118).build; s.source=Vars.content.liquid("water"); s.liquids.set(s.source,100); var b=Vars.world.tile(108,118).build; b.applyConfig(-2); var armed=b.boronArmed; b.liquids.set(Vars.content.liquid("water"),10000); b.liquids.add(Vars.content.liquid("ironworks-boric-acid"), 400); for(var i=0;i<900;i++){ s.updateTile(); b.updateTile(); } Log.info("T128 armed=" + armed + " boron=" + Math.round(b.boron*100) + " poisoned=" + b.poisoned + " power=" + Math.round(b.getPowerProduction()*60))'
  # 124f 乏燃料池：乏燃料棒 + 水 → 冷却乏燃料棒
  echo 'js var T=Vars.state.rules.defaultTeam; Vars.world.tile(114,118).setBlock(Vars.content.block("power-source"),T); Vars.world.tile(116,118).setBlock(Vars.content.block("ironworks-spent-fuel-pool"),T); var b=Vars.world.tile(116,118).build; b.items.add(Vars.content.item("ironworks-depleted-rod"),3); b.liquids.add(Vars.content.liquid("water"),30); Log.info("T129a placed=" + (b!=null))'
  sleep 8
  echo 'js var b=Vars.world.tile(116,118).build; Log.info("T129 cooled=" + b.items.get(Vars.content.item("ironworks-cooled-rod")))'
  # 124g 后处理：冷却乏燃料棒 + 酸 → 钚 + 高放废料
  echo 'js var T=Vars.state.rules.defaultTeam; Vars.world.tile(126,118).setBlock(Vars.content.block("power-source"),T); Vars.world.tile(128,118).setBlock(Vars.content.block("ironworks-reprocessor"),T); var b=Vars.world.tile(128,118).build; b.items.add(Vars.content.item("ironworks-cooled-rod"),2); b.liquids.add(Vars.content.liquid("ironworks-acid"),40); Log.info("T130a placed=" + (b!=null))'
  sleep 6
  echo 'js var b=Vars.world.tile(128,118).build; Log.info("T130 pu=" + b.items.get(Vars.content.item("ironworks-plutonium")) + " waste=" + b.items.get(Vars.content.item("ironworks-nuclear-waste")))'
  # 124h 固化：废料 + 沙 + 石墨 → 固化废物体
  echo 'js var T=Vars.state.rules.defaultTeam; Vars.world.tile(138,118).setBlock(Vars.content.block("power-source"),T); Vars.world.tile(140,118).setBlock(Vars.content.block("ironworks-vitrifier"),T); var b=Vars.world.tile(140,118).build; b.items.add(Vars.content.item("ironworks-nuclear-waste"),4); b.items.add(Vars.content.item("sand"),8); b.items.add(Vars.content.item("graphite"),8); Log.info("T131a placed=" + (b!=null))'
  sleep 5
  echo 'js var b=Vars.world.tile(140,118).build; Log.info("T131 vitrified=" + b.items.get(Vars.content.item("ironworks-vitrified-waste")))'
  # 124i 干式储存：只收固化废物体
  echo 'js var T=Vars.state.rules.defaultTeam; Vars.world.tile(140,124).setBlock(Vars.content.block("ironworks-dry-storage"),T); var b=Vars.world.tile(140,124).build; Log.info("T132 onlyVitrified=" + (b.acceptItem(b, Vars.content.item("ironworks-vitrified-waste")) && !b.acceptItem(b, Vars.content.item("copper"))))'

  # ---- 133. 核岛联动：导管进水/汽轮机进液/爆炸规则/棒位上限/冷却塔 ----
  echo 'js var T=Vars.state.rules.defaultTeam; for(var y=128;y<=150;y++){for(var x=106;x<=150;x++){var t=Vars.world.tile(x,y); if(t!=null)t.setBlock(Vars.content.block("air"),T);}}'
  # 133a 导管把水送进反应堆（修复「送不进去」）
  echo 'js var T=Vars.state.rules.defaultTeam; Vars.world.tile(110,132).setBlock(Vars.content.block("liquid-source"),T); Vars.world.tile(111,132).setBlock(Vars.content.block("conduit"),T); Vars.world.tile(114,132).setBlock(Vars.content.block("ironworks-rbmk-reactor"),T); Vars.world.tile(110,132).build.source=Vars.content.liquid("water"); Log.info("T133a setup=" + (Vars.world.tile(114,132).build!=null))'
  sleep 6
  echo 'js var b=Vars.world.tile(114,132).build; Log.info("T133a water=" + Math.round(b.liquids.get(Vars.content.liquid("water"))))'
  # 133b 导管把热冷却液送进汽轮机
  echo 'js var T=Vars.state.rules.defaultTeam; Vars.world.tile(111,138).setBlock(Vars.content.block("liquid-source"),T); Vars.world.tile(112,138).setBlock(Vars.content.block("conduit"),T); Vars.world.tile(114,138).setBlock(Vars.content.block("ironworks-steam-turbine"),T); Vars.world.tile(111,138).build.source=Vars.content.liquid("ironworks-steam"); Log.info("T133b setup=" + (Vars.world.tile(114,138).build!=null))'
  sleep 6
  echo 'js var b=Vars.world.tile(114,138).build; Log.info("T133b steam=" + Math.round(b.liquids.get(Vars.content.liquid("ironworks-steam"))) + " flow=" + Math.round(b.flow*100) + " power=" + Math.round(b.getPowerProduction()*60))'
  # 133c 运行时被炸毁 → 爆炸（邻墙受伤）
  echo 'js var T=Vars.state.rules.defaultTeam; Vars.world.tile(120,132).setBlock(Vars.content.block("ironworks-rbmk-reactor"),T); Vars.world.tile(117,132).setBlock(Vars.content.block("thorium-wall"),T); var b=Vars.world.tile(120,132).build; b.items.add(Vars.content.item("ironworks-fuel-rod"),5); b.liquids.add(Vars.content.liquid("water"),900); b.rods=0; b.targetRods=0; b.rods=0; b.flux=0.8; for(var i=0;i<20;i++) b.updateTile(); Vars.state.rules.reactorExplosions=true; b.killed(); Log.info("T133c killed=" + b.melted)'
  sleep 1
  echo 'js var w=Vars.world.tile(117,132).build; Log.info("T133c boom=" + (w==null || w.health<w.maxHealth))'
  # 133d 玩家拆除运行时 → 爆炸
  echo 'js var T=Vars.state.rules.defaultTeam; Vars.world.tile(128,132).setBlock(Vars.content.block("ironworks-rbmk-reactor"),T); Vars.world.tile(125,132).setBlock(Vars.content.block("thorium-wall"),T); var b=Vars.world.tile(128,132).build; b.items.add(Vars.content.item("ironworks-fuel-rod"),5); b.liquids.add(Vars.content.liquid("water"),900); b.rods=0; b.targetRods=0; b.rods=0; b.flux=0.8; for(var i=0;i<20;i++) b.updateTile(); Vars.state.rules.reactorExplosions=true; var ok=false; try{ b.onDeconstructed(null); ok=true; }catch(e){ ok=false; } Log.info("T133d called=" + ok + " melted=" + b.melted)'
  sleep 1
  echo 'js var w=Vars.world.tile(125,132).build; Log.info("T133d boom=" + (w==null || w.health<w.maxHealth))'
  # 133e 高功率 SCRAM（AZ-5）→ 闪蒸/升温后熔毁（不是立即引爆）
  echo 'js var T=Vars.state.rules.defaultTeam; Vars.world.tile(136,132).setBlock(Vars.content.block("ironworks-rbmk-reactor"),T); var b=Vars.world.tile(136,132).build; b.items.add(Vars.content.item("ironworks-fuel-rod"),5); b.liquids.add(Vars.content.liquid("water"),900); b.heat=0.9; b.flux=1.1; b.applyConfig(-1); for(var i=0;i<360 && !b.melted;i++) b.updateTile(); Log.info("T133e boom=" + b.melted + " gone=" + (Vars.world.tile(136,132).block()==Vars.content.block("air")))'
  # 133f 冷却塔：温水 → 水（放远一点，免得被上面的爆炸波及）
  echo 'js var T=Vars.state.rules.defaultTeam; for(var y=156;y<=172;y++){for(var x=106;x<=150;x++){var t=Vars.world.tile(x,y); if(t!=null)t.setBlock(Vars.content.block("air"),T);}}'
  echo 'js var T=Vars.state.rules.defaultTeam; Vars.world.tile(113,160).setBlock(Vars.content.block("power-source"),T); Vars.world.tile(114,160).setBlock(Vars.content.block("ironworks-cooling-tower"),T); var b=Vars.world.tile(114,160).build; b.liquids.add(Vars.content.liquid("ironworks-warm-water"),300); Log.info("T133f setup=" + (b!=null))'
  sleep 5
  echo 'js var b=Vars.world.tile(114,160).build; Log.info("T133f water=" + Math.round(b.liquids.get(Vars.content.liquid("water"))))'
  # 133g 棒位 = 功率上限：50% 插棒只能到 ~50%，不会拉满
  echo 'js var T=Vars.state.rules.defaultTeam; Vars.world.tile(130,164).setBlock(Vars.content.block("ironworks-rbmk-reactor"),T); Vars.world.tile(133,164).setBlock(Vars.content.block("liquid-void"),T); var b=Vars.world.tile(130,164).build; b.items.add(Vars.content.item("ironworks-fuel-rod"),5); b.liquids.set(Vars.content.liquid("water"),10000); b.targetRods=0.5; b.rods=0.5; for(var i=0;i<400;i++) b.updateTile(); Log.info("T133g ceiling=" + Math.round(b.rodCeilingPct) + " flux=" + Math.round(b.flux*100) + " power=" + Math.round(b.getPowerProduction()*60))'
  # ---- 134. 物理真实性：控制棒行程 / 冷却流量惯性 / 氙坑 ----
  echo 'js var T=Vars.state.rules.defaultTeam; for(var y=176;y<=190;y++){for(var x=106;x<=150;x++){var t=Vars.world.tile(x,y); if(t!=null)t.setBlock(Vars.content.block("air"),T);}}'
  # 134a 棒位不会瞬移：目标 0%，1 秒后实际仍 >90%，10 秒后才到 ~70%
  echo 'js var T=Vars.state.rules.defaultTeam; Vars.world.tile(112,180).setBlock(Vars.content.block("ironworks-rbmk-reactor"),T); var b=Vars.world.tile(112,180).build; b.items.add(Vars.content.item("ironworks-fuel-rod"),5); b.liquids.set(Vars.content.liquid("water"),10000); b.targetRods=0; b.rods=1; for(var i=0;i<60;i++) b.updateTile(); var r1=Math.round(b.rods*100); for(var i=0;i<540;i++) b.updateTile(); Log.info("T134a after1s=" + r1 + " after10s=" + Math.round(b.rods*100) + " travel=" + (r1 > 90 && b.rods < 0.8))'
  # 134b 冷却流量有惯性：抽干水后不是立刻 0
  echo 'js var b=Vars.world.tile(112,180).build; b.targetRods=0; b.rods=0; b.liquids.add(Vars.content.liquid("water"),300); for(var i=0;i<10;i++) b.updateTile(); b.liquids.clear(); for(var i=0;i<3;i++) b.updateTile(); var f1=b.flow; for(var i=0;i<200;i++) b.updateTile(); Log.info("T134b early=" + Math.round(f1*100) + " late=" + Math.round(b.flow*100) + " inertia=" + (f1 > 0.4 && b.flow < 0.1))'
  # 134c 氙坑：满功率运行后停堆，氙毒反而升高
  echo 'js var T=Vars.state.rules.defaultTeam; Vars.world.tile(109,180).setBlock(Vars.content.block("liquid-source"),T); var s=Vars.world.tile(109,180).build; s.source=Vars.content.liquid("water"); var b=Vars.world.tile(112,180).build; b.liquids.add(Vars.content.liquid("water"),600); b.targetRods=0; b.rods=0; for(var i=0;i<1200;i++){ s.updateTile(); b.updateTile(); } var before=b.xenon; b.targetRods=1; b.rods=1; for(var i=0;i<1800;i++){ s.updateTile(); b.updateTile(); } Log.info("T134c before=" + Math.round(before*100) + " after=" + Math.round(b.xenon*100) + " pit=" + (b.xenon > before))'
  # ---- 135. RBMK 三种控制模式：半自动 / 全自动 / 强制手动 ----
  echo 'js var T=Vars.state.rules.defaultTeam; for(var y=192;y<=206;y++){for(var x=106;x<=150;x++){var t=Vars.world.tile(x,y); if(t!=null)t.setBlock(Vars.content.block("air"),T);}}'
  # 135a 强制手动：棒立刻就位（没有行程缓冲）
  echo 'js var T=Vars.state.rules.defaultTeam; Vars.world.tile(112,196).setBlock(Vars.content.block("ironworks-rbmk-reactor"),T); var b=Vars.world.tile(112,196).build; b.items.add(Vars.content.item("ironworks-fuel-rod"),5); b.liquids.add(Vars.content.liquid("water"),500); b.applyConfig(-12); b.applyConfig(30); for(var i=0;i<2;i++) b.updateTile(); Log.info("T135a mode=" + b.controlMode + " target=" + Math.round(b.targetRods*100) + " actual=" + Math.round(b.rods*100) + " instant=" + (Math.abs(b.rods-b.targetRods) < 0.01))'
  # 135b 半自动：同样的目标，1 秒内棒还在慢慢走
  echo 'js var T=Vars.state.rules.defaultTeam; Vars.world.tile(122,196).setBlock(Vars.content.block("ironworks-rbmk-reactor"),T); var b=Vars.world.tile(122,196).build; b.items.add(Vars.content.item("ironworks-fuel-rod"),5); b.liquids.set(Vars.content.liquid("water"),10000); b.applyConfig(-10); b.applyConfig(30); for(var i=0;i<60;i++) b.updateTile(); Log.info("T135b mode=" + b.controlMode + " target=" + Math.round(b.targetRods*100) + " actual=" + Math.round(b.rods*100) + " buffered=" + (b.rods > 0.9))'
  # 135c 全自动：设目标功率 40%，自动调棒后通量收到 ~40%
  echo 'js var T=Vars.state.rules.defaultTeam; Vars.world.tile(132,196).setBlock(Vars.content.block("ironworks-rbmk-reactor"),T); Vars.world.tile(129,196).setBlock(Vars.content.block("liquid-source"),T); Vars.world.tile(135,196).setBlock(Vars.content.block("liquid-void"),T); var s=Vars.world.tile(129,196).build; s.source=Vars.content.liquid("water"); var b=Vars.world.tile(132,196).build; b.items.add(Vars.content.item("ironworks-fuel-rod"),5); b.liquids.set(Vars.content.liquid("water"),10000); b.applyConfig(-11); b.applyConfig(40); for(var i=0;i<1800;i++){ s.updateTile(); b.updateTile(); } Log.info("T135c mode=" + b.controlMode + " power=" + Math.round(b.autoPower*100) + " flux=" + Math.round(b.flux/1.25*100) + " rods=" + Math.round(b.rods*100) + " water=" + Math.round(b.waterLevel*100))'
  # 135d 强制手动：没有自动 SCRAM（过热也不插棒）
  echo 'js var b=Vars.world.tile(112,196).build; b.applyConfig(-12); b.targetRods=0.4; b.rods=0.4; b.heat=1.3; b.targetRods=0.4; for(var i=0;i<30;i++) b.updateTile(); Log.info("T135d heat=" + Math.round(b.heat*100) + " target=" + Math.round(b.targetRods*100) + " noAutoScram=" + (b.targetRods < 0.5))'
  # ---- 136. 彩蛋：15 秒内按顺序说出「核电轻而易举啊 / 坏了 / 坏了坏了」→ 全图工作中的反应堆熔毁 ----
  echo 'js var T=Vars.state.rules.defaultTeam; for(var y=200;y<=220;y++){for(var x=106;x<=200;x++){var t=Vars.world.tile(x,y); if(t!=null)t.setBlock(Vars.content.block("air"),T);}}'
  # 136a 顺序正确 → 引爆
  echo 'js var T=Vars.state.rules.defaultTeam; Vars.state.rules.reactorExplosions=true; Vars.world.tile(112,208).setBlock(Vars.content.block("ironworks-rbmk-reactor"),T); var b=Vars.world.tile(112,208).build; b.items.add(Vars.content.item("ironworks-fuel-rod"),5); b.liquids.add(Vars.content.liquid("water"),500); b.targetRods=0; b.rods=0; b.flux=1.0; var E=Packages.mindustry.game.EventType.PlayerChatEvent; Packages.arc.Events.fire(new E(null,"核电，轻而易举啊")); Packages.arc.Events.fire(new E(null,"坏了")); Packages.arc.Events.fire(new E(null,"坏了坏了")); Log.info("T136a melted=" + b.melted)'
  # 136b 顺序错误（第一条不对）→ 不引爆
  echo 'js var T=Vars.state.rules.defaultTeam; Vars.world.tile(150,208).setBlock(Vars.content.block("ironworks-rbmk-reactor"),T); var b=Vars.world.tile(150,208).build; b.items.add(Vars.content.item("ironworks-fuel-rod"),5); b.liquids.add(Vars.content.liquid("water"),500); b.targetRods=0; b.rods=0; b.flux=1.0; var E=Packages.mindustry.game.EventType.PlayerChatEvent; Packages.arc.Events.fire(new E(null,"核电，轻而易举啊")); Packages.arc.Events.fire(new E(null,"坏了坏了")); Log.info("T136b wrongOrderAlive=" + (!b.melted))'
  sleep 1
  # 136c 超过 15 秒 → 序列作废
  echo 'js var b=Vars.world.tile(150,208).build; b.targetRods=1; b.rods=1; b.flux=0; b.heat=0; b.liquids.clear(); var E=Packages.mindustry.game.EventType.PlayerChatEvent; Packages.arc.Events.fire(new E(null,"核电，轻而易举啊")); Log.info("T136c started=true")'
  sleep 16
  echo 'js var b=Vars.world.tile(150,208).build; var E=Packages.mindustry.game.EventType.PlayerChatEvent; Packages.arc.Events.fire(new E(null,"坏了")); Packages.arc.Events.fire(new E(null,"坏了坏了")); Log.info("T136c timedOutAlive=" + (b!=null && !b.melted))'
  # ---- 137. 拆除运行中的反应堆：爆炸但**不能**自己清掉 tile（否则 Build.beginBreak 里 tile.build.dead 会 NPE 崩溃） ----
  echo 'js var T=Vars.state.rules.defaultTeam; for(var y=224;y<=236;y++){for(var x=150;x<=200;x++){var t=Vars.world.tile(x,y); if(t!=null)t.setBlock(Vars.content.block("air"),T);}}'
  echo 'js var T=Vars.state.rules.defaultTeam; Vars.state.rules.reactorExplosions=true; Vars.world.tile(160,230).setBlock(Vars.content.block("ironworks-rbmk-reactor"),T); var b=Vars.world.tile(160,230).build; b.items.add(Vars.content.item("ironworks-fuel-rod"),5); b.liquids.add(Vars.content.liquid("water"),500); b.targetRods=0; b.rods=0; b.flux=0.9; var ok=false; try{ b.onDeconstructed(null); ok=true; }catch(e){ Log.info("T137 err=" + e); } var tb=Vars.world.tile(160,230).build; Log.info("T137 aliveTile=" + (tb!=null) + " melted=" + b.melted + " noCrash=" + ok)'
  # ---- 138. 无冷却液 + 控制棒 0%：升温速度（应当在 ~30 秒内熔毁，不能慢吞吞） ----
  echo 'js var T=Vars.state.rules.defaultTeam; for(var y=238;y<=252;y++){for(var x=150;x<=200;x++){var t=Vars.world.tile(x,y); if(t!=null)t.setBlock(Vars.content.block("air"),T);}}'
  echo 'js var T=Vars.state.rules.defaultTeam; Vars.state.rules.reactorExplosions=true; Vars.world.tile(160,244).setBlock(Vars.content.block("ironworks-rbmk-reactor"),T); var b=Vars.world.tile(160,244).build; b.items.add(Vars.content.item("ironworks-fuel-rod"),5); b.applyConfig(-12); b.targetRods=0; b.rods=0; var h0=b.heat; var t10=-1; for(var i=0;i<1800 && !b.melted;i++){ b.updateTile(); if(t10<0 && b.heat>=1.0) t10=i; } Log.info("T138 meltedIn=" + (b.melted ? Math.round((t10<0?1800:t10)/60) + "s" : "never") + " melted=" + b.melted + " water=" + Math.round(b.liquids.get(Vars.content.liquid("water"))))'
  # ---- 139. 水→汽 1:1：100% 通量下 60 tick 消耗 980 水、产出 980 蒸汽 ----
  echo 'js var T=Vars.state.rules.defaultTeam; for(var y=244;y<=255;y++){for(var x=100;x<=230;x++){var t=Vars.world.tile(x,y); if(t!=null)t.setBlock(Vars.content.block("air"),T);}}'
  echo 'js var T=Vars.state.rules.defaultTeam; Vars.world.tile(108,250).setBlock(Vars.content.block("ironworks-rbmk-reactor"),T); var b=Vars.world.tile(108,250).build; b.items.add(Vars.content.item("ironworks-fuel-rod"),5); b.liquids.set(Vars.content.liquid("water"),10000); b.applyConfig(-12); b.targetRods=0.2; b.rods=0.2; b.flux=1.0; for(var i=0;i<60;i++) b.updateTile(); var w=b.liquids.get(Vars.content.liquid("water")); var st=b.liquids.get(Vars.content.liquid("ironworks-steam")); Log.info("T139 water=" + Math.round(w) + " steam=" + Math.round(st) + " sum=" + Math.round(w+st) + " ratio=" + Math.round(st/Math.max(1,10000-w)*100))'
  # ---- 140. 强制手动水位上限：50% 时不收超过 5000 L 的水 ----
  echo 'js var T=Vars.state.rules.defaultTeam; Vars.world.tile(124,250).setBlock(Vars.content.block("ironworks-rbmk-reactor"),T); var b=Vars.world.tile(124,250).build; b.applyConfig(-12); b.applyConfig(1050); b.liquids.set(Vars.content.liquid("water"),6000); b.updateTile(); var water=Vars.content.liquid("water"); var high=b.acceptLiquid(b,water); b.liquids.set(water,4000); var low=b.acceptLiquid(b,water); Log.info("T140 target=" + Math.round(b.waterTarget*100) + " acceptHigh=" + high + " acceptLow=" + low)'
  # ---- 141. 全自动：目标功率 40% → 后台自动算棒位 + 水位 ----
  echo 'js var T=Vars.state.rules.defaultTeam; Vars.world.tile(140,250).setBlock(Vars.content.block("ironworks-rbmk-reactor"),T); Vars.world.tile(137,250).setBlock(Vars.content.block("liquid-source"),T); Vars.world.tile(143,250).setBlock(Vars.content.block("liquid-void"),T); var s=Vars.world.tile(137,250).build; s.source=Vars.content.liquid("water"); var b=Vars.world.tile(140,250).build; b.items.add(Vars.content.item("ironworks-fuel-rod"),5); b.liquids.set(Vars.content.liquid("water"),10000); b.applyConfig(-11); b.applyConfig(40); for(var i=0;i<900;i++){ s.updateTile(); b.updateTile(); } Log.info("T141 mode=" + b.controlMode + " auto=" + Math.round(b.waterLevel*100) + " rods=" + Math.round(b.rods*100) + " flux=" + Math.round(b.flux*100))'
  # ---- 142. 汽路憋压 + 水罐满 + 插棒到底 → AZ-5 引爆 ----
  echo 'js var T=Vars.state.rules.defaultTeam; Vars.state.rules.reactorExplosions=true; Vars.world.tile(160,250).setBlock(Vars.content.block("ironworks-rbmk-reactor"),T); var b=Vars.world.tile(160,250).build; b.items.add(Vars.content.item("ironworks-fuel-rod"),5); b.liquids.set(Vars.content.liquid("water"),10000); b.liquids.set(Vars.content.liquid("ironworks-steam"),2000); b.applyConfig(-12); b.targetRods=1; b.rods=1; b.flux=0.5; b.heat=0.5; var ticks=-1; for(var i=0;i<300 && !b.melted;i++){ b.updateTile(); if(b.melted) ticks=i; } Log.info("T142 melted=" + b.melted + " ticks=" + ticks)'
  # ---- 143. 汽轮机：980 L/s 蒸汽 → 45000 电 + 980 L/s 温水 ----
  echo 'js var T=Vars.state.rules.defaultTeam; Vars.world.tile(196,253).setBlock(Vars.content.block("ironworks-steam-turbine"),T); var b=Vars.world.tile(196,253).build; b.liquids.add(Vars.content.liquid("ironworks-steam"),2000); for(var i=0;i<60;i++) b.updateTile(); Log.info("T143 power=" + Math.round(b.getPowerProduction()*60) + " warm=" + Math.round(b.liquids.get(Vars.content.liquid("ironworks-warm-water"))) + " flow=" + Math.round(b.flow*100))'
  # ---- 144. 小型冷却塔：2×2，120 L/s 温水 → 水 ----
  echo 'js var T=Vars.state.rules.defaultTeam; Vars.world.tile(207,253).setBlock(Vars.content.block("power-source"),T); Vars.world.tile(208,253).setBlock(Vars.content.block("ironworks-cooling-tower"),T); var b=Vars.world.tile(208,253).build; b.liquids.add(Vars.content.liquid("ironworks-warm-water"),96); Log.info("T144 setup=" + (b!=null))'
  sleep 2
  echo 'js var b=Vars.world.tile(208,253).build; Log.info("T144 water=" + Math.round(b.liquids.get(Vars.content.liquid("water"))) + " warm=" + Math.round(b.liquids.get(Vars.content.liquid("ironworks-warm-water"))))'
  # ---- 145. 同样的憋压工况，但棒没插到底 → 不引爆 ----
  echo 'js var T=Vars.state.rules.defaultTeam; Vars.state.rules.reactorExplosions=true; Vars.world.tile(220,250).setBlock(Vars.content.block("ironworks-rbmk-reactor"),T); var b=Vars.world.tile(220,250).build; b.items.add(Vars.content.item("ironworks-fuel-rod"),5); b.liquids.set(Vars.content.liquid("water"),10000); b.liquids.set(Vars.content.liquid("ironworks-steam"),2000); b.applyConfig(-12); b.targetRods=0.5; b.rods=0.5; b.flux=0.5; b.heat=0.5; for(var i=0;i<300;i++) b.updateTile(); Log.info("T145 melted=" + b.melted + " boom=" + Math.round(b.coreBoom*10))'
  # ---- 146. 给水侧不被蒸汽污染：水从左进、汽从右出，左管只允许是水 ----
  echo 'js var T=Vars.state.rules.defaultTeam; for(var y=246;y<=254;y++){for(var x=230;x<=248;x++){var t=Vars.world.tile(x,y); if(t!=null)t.setBlock(Vars.content.block("air"),T);}}'
  echo 'js var T=Vars.state.rules.defaultTeam; Vars.world.tile(234,250).setBlock(Vars.content.block("liquid-source"),T); Vars.world.tile(235,250).setBlock(Vars.content.block("conduit"),T); Vars.world.tile(238,250).setBlock(Vars.content.block("ironworks-rbmk-reactor"),T); Vars.world.tile(241,250).setBlock(Vars.content.block("conduit"),T); var s=Vars.world.tile(234,250).build; s.source=Vars.content.liquid("water"); s.liquids.set(s.source,10000); var c1=Vars.world.tile(235,250).build, c2=Vars.world.tile(241,250).build, b=Vars.world.tile(238,250).build; b.items.add(Vars.content.item("ironworks-fuel-rod"),5); b.applyConfig(-12); b.targetRods=0.5; b.rods=0.5; for(var i=0;i<400;i++){ s.updateTile(); c1.updateTile(); b.updateTile(); c2.updateTile(); } var st=Vars.content.liquid("ironworks-steam"), wt=Vars.content.liquid("water"); Log.info("T146 side=" + b.waterSide + " leftSteam=" + Math.round(c1.liquids.get(st)) + " rightSteam=" + Math.round(c2.liquids.get(st)) + " reactorWater=" + Math.round(b.liquids.get(wt)) + " melted=" + b.melted)'
  # ---- 147. 温水满 → 汽轮机停机；排掉温水 → 恢复 ----
  echo 'js var T=Vars.state.rules.defaultTeam; for(var y=228;y<=236;y++){for(var x=230;x<=248;x++){var t=Vars.world.tile(x,y); if(t!=null)t.setBlock(Vars.content.block("air"),T);}}'
  echo 'js var T=Vars.state.rules.defaultTeam; Vars.world.tile(238,232).setBlock(Vars.content.block("ironworks-steam-turbine"),T); var b=Vars.world.tile(238,232).build; var st=Vars.content.liquid("ironworks-steam"), ww=Vars.content.liquid("ironworks-warm-water"); b.liquids.set(st,1000); b.liquids.set(ww,1200); for(var i=0;i<60;i++) b.updateTile(); var stopped=(b.flow<0.01 && b.getPowerProduction()==0); var kept=(Math.round(b.liquids.get(st))==1000); b.liquids.remove(ww,600); for(var i=0;i<60;i++) b.updateTile(); var resumed=(b.flow>0.9 && Math.round(b.getPowerProduction()*60)==24000); Log.info("T147 stopped=" + stopped + " steamKept=" + kept + " resumed=" + resumed + " warm=" + Math.round(b.liquids.get(ww)))'
  # ---- 148. 堆芯熔融物：生存模式不可拆/不可摧毁/不自消失；沙盒模式正常 ----
  echo 'js var c=Vars.content.block("ironworks-corium"); var T=Vars.state.rules.defaultTeam; var old=Vars.state.rules.infiniteResources; Vars.world.tile(100,228).setBlock(c,T); Vars.world.tile(102,228).setBlock(c,T); var a=Vars.world.tile(100,228).build, b=Vars.world.tile(102,228).build; Vars.state.rules.infiniteResources=false; var cbS=c.canBreak(Vars.world.tile(100,228)); var hp0=a.health; a.damage(10000); var dmgIgnored=(a.health==hp0); a.life=-1; a.updateTile(); var stayed=(Vars.world.tile(100,228).block()==c); Vars.state.rules.infiniteResources=true; var cbB=c.canBreak(Vars.world.tile(102,228)); var hp1=b.health; b.damage(10000); var sandboxDmg=(b.health==hp1); b.life=-1; b.updateTile(); var gone=(Vars.world.tile(102,228).block()!=c); Vars.state.rules.infiniteResources=old; Log.info("T148 survivalBreak=" + cbS + " sandboxBreak=" + cbB + " damageIgnored=" + dmgIgnored + " sandboxDmg=" + sandboxDmg + " stayed=" + stayed + " gone=" + gone)'
  # ---- 149. 全自动：蒸汽排不出去 → 迅速插棒降功率，绝不触发 AZ-5 ----
  echo 'js var T=Vars.state.rules.defaultTeam; for(var y=236;y<=246;y++){for(var x=100;x<=120;x++){var t=Vars.world.tile(x,y); if(t!=null)t.setBlock(Vars.content.block("air"),T);}}'
  echo 'js var T=Vars.state.rules.defaultTeam; Vars.state.rules.reactorExplosions=true; Vars.world.tile(110,242).setBlock(Vars.content.block("ironworks-rbmk-reactor"),T); var b=Vars.world.tile(110,242).build; b.items.add(Vars.content.item("ironworks-fuel-rod"),5); b.liquids.set(Vars.content.liquid("water"),9000); b.liquids.set(Vars.content.liquid("ironworks-steam"),2000); b.applyConfig(-11); b.applyConfig(100); for(var i=0;i<600;i++) b.updateTile(); Log.info("T149 autoRods=" + Math.round(b.rods*100) + " water=" + Math.round(b.liquids.get(Vars.content.liquid("water"))) + " steam=" + Math.round(b.liquids.get(Vars.content.liquid("ironworks-steam"))) + " melted=" + b.melted + " boom=" + Math.round(b.coreBoom*10))'
  # ---- 150. 全自动：给水见底 → 同样迅速插棒停机 ----
  echo 'js var T=Vars.state.rules.defaultTeam; Vars.state.rules.reactorExplosions=true; Vars.world.tile(124,242).setBlock(Vars.content.block("ironworks-rbmk-reactor"),T); var b=Vars.world.tile(124,242).build; b.items.add(Vars.content.item("ironworks-fuel-rod"),5); b.liquids.set(Vars.content.liquid("water"),1000); b.applyConfig(-11); b.applyConfig(100); for(var i=0;i<600;i++) b.updateTile(); Log.info("T150 autoRods=" + Math.round(b.rods*100) + " water=" + Math.round(b.liquids.get(Vars.content.liquid("water"))) + " melted=" + b.melted)'
  # ---- 151. 反应堆爆炸不能把已有的堆芯熔融物炸没（沙盒也一样） ----
  echo 'js var T=Vars.state.rules.defaultTeam; for(var y=238;y<=245;y++){for(var x=230;x<=248;x++){var t=Vars.world.tile(x,y); if(t!=null)t.setBlock(Vars.content.block("air"),T);}}'
  echo 'js var c=Vars.content.block("ironworks-corium"); var T=Vars.state.rules.defaultTeam; Vars.state.rules.reactorExplosions=true; Vars.world.tile(232,242).setBlock(c,T); Vars.world.tile(238,242).setBlock(Vars.content.block("ironworks-rbmk-reactor"),T); var b=Vars.world.tile(238,242).build; b.items.add(Vars.content.item("ironworks-fuel-rod"),5); b.heat=1.6; b.flux=1.0; for(var i=0;i<240 && !b.melted;i++) b.updateTile(); var survivor=(Vars.world.tile(232,242).block()==c); var n=0; for(var y=238;y<=246;y++){for(var x=230;x<=246;x++){var t=Vars.world.tile(x,y); if(t!=null && t.block()==c) n++;}} Log.info("T151 survivor=" + survivor + " coriumCount=" + n + " melted=" + b.melted)'
  # ---- 152. 自动雷达：四种索敌 + 接管炮塔 + 10 链接上限 ----
  echo 'js var T=Vars.state.rules.defaultTeam; for(var y=196;y<=216;y++){for(var x=56;x<=96;x++){var t=Vars.world.tile(x,y); if(t!=null)t.setBlock(Vars.content.block("air"),T);}}'
  echo 'js var T=Vars.state.rules.defaultTeam; Vars.world.tile(70,205).setBlock(Vars.content.block("ironworks-auto-radar"),T); Vars.world.tile(74,205).setBlock(Vars.content.block("duo"),T); var b=Vars.world.tile(70,205).build; var t=Vars.world.tile(74,205).build; b.toggleLink(t); var T2=Vars.state.rules.waveTeam; var mace=Vars.content.unit("mace").spawn(T2, 90*8, 210*8); var d1=Vars.content.unit("dagger").spawn(T2, 75*8, 205*8); b.applyConfig(-1); b.retargetTimer=0; b.updateTile(); var maxHp=b.target.type.name; b.applyConfig(-2); b.retargetTimer=0; b.updateTile(); var near=b.target.type.name; var m2=Vars.content.unit("mace").spawn(T2, 64*8, 205*8); m2.health=50; b.applyConfig(-4); b.retargetTimer=0; b.updateTile(); var low=b.target.type.name; Vars.content.unit("dagger").spawn(T2, 77*8, 210*8); Vars.content.unit("dagger").spawn(T2, 78*8, 210*8); Vars.content.unit("dagger").spawn(T2, 79*8, 210*8); Vars.content.unit("mace").spawn(T2, 64*8, 205*8); b.applyConfig(-3); b.retargetTimer=0; b.updateTile(); var dense=(b.target!=null && b.target.x>=616); var idx=t.block.timerTarget; b.retargetTimer=0; b.updateTile(); var linked=(t.target==b.target); var timer=(t.timer.getTime(idx) < 5); t.team=T2; b.retargetTimer=0; b.updateTile(); var teamKeep=(b.links.contains(t.pos()) && t.target==b.target && t.timer.getTime(idx) < 5); for(var i=0;i<11;i++){Vars.world.tile(60+i,198).setBlock(Vars.content.block("duo"),T); b.toggleLink(Vars.world.tile(60+i,198).build);} Log.info("T152 maxHp=" + maxHp + " nearest=" + near + " lowHp=" + low + " dense=" + dense + " linked=" + linked + " timer=" + timer + " teamKeep=" + teamKeep + " cap=" + b.links.size)'
  # ---- 153. 实时接管：炮塔自己的“最近”索敌不能抢雷达的 max-HP 目标（3 秒真实运行） ----
  echo 'js var T=Vars.state.rules.defaultTeam; for(var y=196;y<=216;y++){for(var x=56;x<=96;x++){var t=Vars.world.tile(x,y); if(t!=null)t.setBlock(Vars.content.block("air"),T);}}'
  echo 'js var T=Vars.state.rules.defaultTeam; Vars.world.tile(70,205).setBlock(Vars.content.block("ironworks-auto-radar"),T); Vars.world.tile(74,205).setBlock(Vars.content.block("duo"),T); var b=Vars.world.tile(70,205).build; var t=Vars.world.tile(74,205).build; for(var i=0;i<20;i++) t.handleItem(t, Vars.content.item("copper")); b.toggleLink(t); var T2=Vars.state.rules.waveTeam; Vars.content.unit("mace").spawn(T2, 90*8, 210*8); Vars.content.unit("dagger").spawn(T2, 76*8, 205*8); b.applyConfig(-1); b.retargetTimer=0; b.updateTile(); Log.info("T153a before=" + (t.target==null?"null":t.target.type.name))'
  sleep 3
  echo 'js var b=Vars.world.tile(70,205).build; var t=Vars.world.tile(74,205).build; Log.info("T153 after=" + (t.target==null?"null":t.target.type.name) + " same=" + (t.target==b.target))'
  # ---- 154. 属性伤害：归属查询 + 护甲/克制/状态实时结算 ----
  echo 'js var T=Vars.state.rules.defaultTeam; for(var y=205;y<=225;y++){for(var x=140;x<=200;x++){var t=Vars.world.tile(x,y); if(t!=null)t.setBlock(Vars.content.block("air"),T);}}'
  echo 'js var T=Vars.state.rules.defaultTeam; Vars.world.tile(150,215).setBlock(Vars.content.block("ironworks-verbose-logger"),T); Log.info("T154 " + Vars.world.tile(150,215).build.damageTest())'
  # ---- 155. 彩蛋开关：关闭后不引爆，重新开启后恢复 ----
  echo 'js var T=Vars.state.rules.defaultTeam; Vars.state.rules.reactorExplosions=true; Vars.world.tile(180,208).setBlock(Vars.content.block("ironworks-rbmk-reactor"),T); var b=Vars.world.tile(180,208).build; b.items.add(Vars.content.item("ironworks-fuel-rod"),5); b.liquids.add(Vars.content.liquid("water"),500); b.targetRods=0; b.rods=0; b.flux=1.0; Packages.arc.Core.settings.put("ironworks.rbmk.meme", false); var E=Packages.mindustry.game.EventType.PlayerChatEvent; Packages.arc.Events.fire(new E(null,"核电，轻而易举啊")); Packages.arc.Events.fire(new E(null,"坏了")); Packages.arc.Events.fire(new E(null,"坏了坏了")); Log.info("T155a disabledAlive=" + (!b.melted))'
  echo 'js var b=Vars.world.tile(180,208).build; Packages.arc.Core.settings.put("ironworks.rbmk.meme", true); var E=Packages.mindustry.game.EventType.PlayerChatEvent; Packages.arc.Events.fire(new E(null,"核电，轻而易举啊")); Packages.arc.Events.fire(new E(null,"坏了")); Packages.arc.Events.fire(new E(null,"坏了坏了")); Log.info("T155b enabledMelted=" + b.melted)'
  # ---- 156. 双水位线：最高线是硬上限，最低线独立存在 ----
  echo 'js var T=Vars.state.rules.defaultTeam; for(var y=156;y<=172;y++){for(var x=240;x<=260;x++){var t=Vars.world.tile(x,y); if(t!=null)t.setBlock(Vars.content.block("air"),T);}}'
  echo 'js var T=Vars.state.rules.defaultTeam; Vars.world.tile(250,164).setBlock(Vars.content.block("ironworks-rbmk-reactor"),T); var b=Vars.world.tile(250,164).build; var w=Vars.content.liquid("water"); b.applyConfig(-12); b.applyConfig(1050); b.applyConfig(2020); b.waterLevel=b.waterTarget; b.waterLowLevel=b.waterLowTarget; b.liquids.set(w,6000); var high=b.acceptLiquid(b,w); b.liquids.set(w,4000); var low=b.acceptLiquid(b,w); b.liquids.set(w,1500); var atLow=b.acceptLiquid(b,w); Log.info("T156 high=" + Math.round(b.waterTarget*100) + " low=" + Math.round(b.waterLowTarget*100) + " acceptHigh=" + high + " acceptLow=" + low + " acceptAtLow=" + atLow)'
  # ---- 157. 自动/半自动水汽通量 ≤980 L/s；手动可超通量 ----
  echo 'js var T=Vars.state.rules.defaultTeam; for(var y=172;y<=188;y++){for(var x=220;x<=260;x++){var t=Vars.world.tile(x,y); if(t!=null)t.setBlock(Vars.content.block("air"),T);}}'
  echo 'js var T=Vars.state.rules.defaultTeam; var w=Vars.content.liquid("water"), fr=Vars.content.item("ironworks-fuel-rod"); function mk(x,mode){Vars.world.tile(x,180).setBlock(Vars.content.block("ironworks-rbmk-reactor"),T); var b=Vars.world.tile(x,180).build; b.items.add(fr,5); b.liquids.set(w,10000); b.applyConfig(mode); if(mode==-11) b.applyConfig(100); b.targetRods=0; b.rods=0; b.flux=1.25; b.heat=0.5; return b;} var semi=mk(250,-10), auto=mk(240,-11), man=mk(230,-12); var s0=semi.liquids.get(w), a0=auto.liquids.get(w), m0=man.liquids.get(w); semi.updateTile(); auto.updateTile(); man.updateTile(); var sUsed=s0-semi.liquids.get(w), aUsed=a0-auto.liquids.get(w), mUsed=m0-man.liquids.get(w); Log.info("T157 semi=" + Math.round(sUsed*60) + " auto=" + Math.round(aUsed*60) + " manual=" + Math.round(mUsed*60) + " cap=" + (sUsed*1.1 < mUsed && aUsed*1.1 < mUsed))'
  # ---- 158. 手动模式：高线 50%、低线 20%，低于低线才补水到高线 ----
  echo 'js var T=Vars.state.rules.defaultTeam; for(var y=204;y<=220;y++){for(var x=240;x<=260;x++){var t=Vars.world.tile(x,y); if(t!=null)t.setBlock(Vars.content.block("air"),T);}}'
  echo 'js var T=Vars.state.rules.defaultTeam; Vars.world.tile(247,212).setBlock(Vars.content.block("liquid-source"),T); var s=Vars.world.tile(247,212).build; s.source=Vars.content.liquid("water"); s.liquids.set(s.source,10000); Vars.world.tile(250,212).setBlock(Vars.content.block("ironworks-rbmk-reactor"),T); var b=Vars.world.tile(250,212).build; var w=Vars.content.liquid("water"); b.applyConfig(-12); b.applyConfig(1050); b.applyConfig(2020); b.liquids.set(w,1500); for(var i=0;i<300;i++) b.updateTile(); var filled=Math.round(b.liquids.get(w)); b.liquids.set(w,3000); for(var i=0;i<60;i++) b.updateTile(); var hold=Math.round(b.liquids.get(w)); Log.info("T158 filled=" + filled + " hold=" + hold + " low=" + Math.round(b.waterLowLevel*100) + " high=" + Math.round(b.waterLevel*100))'
  # ---- 159. 全自动：高低线自动调节，高线 ≤90% ----
  echo 'js var T=Vars.state.rules.defaultTeam; for(var y=220;y<=236;y++){for(var x=240;x<=260;x++){var t=Vars.world.tile(x,y); if(t!=null)t.setBlock(Vars.content.block("air"),T);}}'
  echo 'js var T=Vars.state.rules.defaultTeam; Vars.world.tile(250,228).setBlock(Vars.content.block("ironworks-rbmk-reactor"),T); var b=Vars.world.tile(250,228).build; b.items.add(Vars.content.item("ironworks-fuel-rod"),5); b.liquids.set(Vars.content.liquid("water"),10000); b.applyConfig(-11); b.applyConfig(100); b.flux=1.0; for(var i=0;i<600;i++) b.updateTile(); Log.info("T159 high=" + Math.round(b.waterLevel*100) + " low=" + Math.round(b.waterLowLevel*100) + " cap=" + (b.waterLevel<=0.9001) + " band=" + (b.waterLowLevel<b.waterLevel))'
  # ---- 160. 自动模式：即使水满+汽满+插棒到底也不触发 AZ-5（压力爆炸只属手动） ----
  echo 'js var T=Vars.state.rules.defaultTeam; for(var y=236;y<=252;y++){for(var x=240;x<=260;x++){var t=Vars.world.tile(x,y); if(t!=null)t.setBlock(Vars.content.block("air"),T);}}'
  echo 'js var T=Vars.state.rules.defaultTeam; Vars.state.rules.reactorExplosions=true; Vars.world.tile(250,244).setBlock(Vars.content.block("ironworks-rbmk-reactor"),T); var b=Vars.world.tile(250,244).build; b.items.add(Vars.content.item("ironworks-fuel-rod"),5); b.liquids.set(Vars.content.liquid("water"),10000); b.liquids.set(Vars.content.liquid("ironworks-steam"),2000); b.applyConfig(-11); b.targetRods=1; b.rods=1; b.flux=0.5; b.heat=0.5; for(var i=0;i<300;i++) b.updateTile(); Log.info("T160 melted=" + b.melted + " boom=" + Math.round(b.coreBoom*10))'
  # ---- 161. 高功率 SCRAM：先闪蒸、再靠堆温熔毁；不再直接引爆 ----
  echo 'js var T=Vars.state.rules.defaultTeam; for(var y=188;y<=204;y++){for(var x=240;x<=260;x++){var t=Vars.world.tile(x,y); if(t!=null)t.setBlock(Vars.content.block("air"),T);}}'
  echo 'js var T=Vars.state.rules.defaultTeam; var w=Vars.content.liquid("water"), st=Vars.content.liquid("ironworks-steam"); Vars.state.rules.reactorExplosions=true; Vars.world.tile(250,196).setBlock(Vars.content.block("ironworks-rbmk-reactor"),T); var b=Vars.world.tile(250,196).build; b.items.add(Vars.content.item("ironworks-fuel-rod"),5); b.applyConfig(-12); b.targetRods=0; b.rods=0; b.flux=1.25; b.heat=0.85; b.liquids.set(w,10000); b.applyConfig(-1); var water=b.liquids.get(w), steam=b.liquids.get(st), flash=b.lastFlash, immediate=b.melted, spike=b.promptHeat; for(var i=0;i<600 && !b.melted;i++) b.updateTile(); Log.info("T161 water=" + Math.round(water) + " steam=" + Math.round(steam) + " flash=" + Math.round(flash) + " immediate=" + immediate + " spike=" + (spike>0.1) + " melted=" + b.melted + " ticks=" + i)'
  # ---- 162. 手动最高水位线是硬上限：调低后罐内超出的水必须排掉/拒收 ----
  echo 'js var T=Vars.state.rules.defaultTeam; for(var y=188;y<=204;y++){for(var x=240;x<=260;x++){var t=Vars.world.tile(x,y); if(t!=null)t.setBlock(Vars.content.block("air"),T);}}'
  echo 'js var T=Vars.state.rules.defaultTeam; Vars.world.tile(250,196).setBlock(Vars.content.block("ironworks-rbmk-reactor"),T); var b=Vars.world.tile(250,196).build; var w=Vars.content.liquid("water"); b.applyConfig(-12); b.applyConfig(1010); b.liquids.set(w,5000); b.updateTile(); var clamped=Math.round(b.liquids.get(w)); b.liquids.set(w,0); b.handleLiquid(b,w,5000); b.updateTile(); var bulk=Math.round(b.liquids.get(w)); Log.info("T162 line=" + Math.round(b.waterTarget*100) + " clamped=" + clamped + " bulk=" + bulk + " accept=" + b.acceptLiquid(b,w))'
  # ---- 163. 手动模式异常通知开关：只切本堆、只允许手动 ----
  echo 'js var T=Vars.state.rules.defaultTeam; for(var y=188;y<=204;y++){for(var x=240;x<=260;x++){var t=Vars.world.tile(x,y); if(t!=null)t.setBlock(Vars.content.block("air"),T);}}'
  echo 'js var T=Vars.state.rules.defaultTeam; Vars.world.tile(250,196).setBlock(Vars.content.block("ironworks-rbmk-reactor"),T); var b=Vars.world.tile(250,196).build; b.applyConfig(-12); b.applyConfig(-3); var on=b.alertsMuted; b.applyConfig(-3); var off=b.alertsMuted; b.applyConfig(-11); b.applyConfig(-3); var locked=b.alertsMuted; Log.info("T163 on=" + on + " off=" + off + " locked=" + locked)'
  # ---- 164. 5% 硬容积上限：批量进水不能冲过高线，100% 时真上限仍是 10000 L ----
  echo 'js var T=Vars.state.rules.defaultTeam; for(var y=188;y<=204;y++){for(var x=240;x<=260;x++){var t=Vars.world.tile(x,y); if(t!=null)t.setBlock(Vars.content.block("air"),T);}}'
  echo 'js var T=Vars.state.rules.defaultTeam; Vars.world.tile(250,196).setBlock(Vars.content.block("ironworks-rbmk-reactor"),T); var b=Vars.world.tile(250,196).build; var w=Vars.content.liquid("water"); b.applyConfig(-12); b.applyConfig(1005); b.liquids.set(w,500); for(var i=0;i<8;i++){ b.handleLiquid(b,w,5000); b.updateTile(); } var bulk=Math.round(b.liquids.get(w)); b.liquids.set(w,5000); b.updateTile(); var clamped=Math.round(b.liquids.get(w)); b.applyConfig(1100); b.liquids.set(w,10000); b.handleLiquid(b,w,5000); var trueCap=Math.round(b.liquids.get(w)); Log.info("T164 line=" + Math.round(b.waterTarget*100) + " bulk=" + bulk + " clamped=" + clamped + " trueCap=" + trueCap)'
  # ---- 165. 真实液源直连 5% 高线：批量供水也不能把水罐冲过 500 L ----
  echo 'js var T=Vars.state.rules.defaultTeam; for(var y=188;y<=204;y++){for(var x=236;x<=260;x++){var t=Vars.world.tile(x,y); if(t!=null)t.setBlock(Vars.content.block("air"),T);}}'
  echo 'js var T=Vars.state.rules.defaultTeam; Vars.world.tile(247,196).setBlock(Vars.content.block("liquid-source"),T); var s=Vars.world.tile(247,196).build; s.source=Vars.content.liquid("water"); s.liquids.set(s.source,10000); Vars.world.tile(250,196).setBlock(Vars.content.block("ironworks-rbmk-reactor"),T); var b=Vars.world.tile(250,196).build; b.applyConfig(-12); b.applyConfig(1005); for(var i=0;i<180;i++){ s.updateTile(); b.updateTile(); } Log.info("T165 line=" + Math.round(b.waterTarget*100) + " water=" + Math.round(b.liquids.get(Vars.content.liquid("water"))) + " source=" + Math.round(s.liquids.get(s.source)))'
  # ---- 166. 半自动断水：实时保护必须在水烧干前降功率，不能等 100%% 才插棒 ----
  echo 'js var T=Vars.state.rules.defaultTeam; for(var y=188;y<=204;y++){for(var x=240;x<=260;x++){var t=Vars.world.tile(x,y); if(t!=null)t.setBlock(Vars.content.block("air"),T);}}'
  echo 'js var T=Vars.state.rules.defaultTeam; Vars.state.rules.reactorExplosions=true; Vars.world.tile(250,196).setBlock(Vars.content.block("ironworks-rbmk-reactor"),T); var b=Vars.world.tile(250,196).build; b.items.add(Vars.content.item("ironworks-fuel-rod"),5); b.liquids.set(Vars.content.liquid("water"),10000); b.applyConfig(-10); b.targetRods=0; b.rods=0; var maxHeat=0, ph=-1, pw=0; for(var i=0;i<2400 && !b.melted;i++){ b.updateTile(); if(b.heat>maxHeat) maxHeat=b.heat; if(ph<0 && b.protectRods>0.1){ ph=b.heat; pw=b.liquids.get(Vars.content.liquid("water")); } } Log.info("T166 startHeat=" + Math.round(ph*100) + " startWater=" + Math.round(pw) + " melted=" + b.melted + " maxHeat=" + Math.round(maxHeat*100) + " protect=" + Math.round(b.protectRods*100) + " rods=" + Math.round(b.rods*100) + " water=" + Math.round(b.liquids.get(Vars.content.liquid("water"))))'
  # ---- 167. 手动静音后切回自动/半自动，必须恢复通知 ----
  echo 'js var T=Vars.state.rules.defaultTeam; for(var y=188;y<=204;y++){for(var x=240;x<=260;x++){var t=Vars.world.tile(x,y); if(t!=null)t.setBlock(Vars.content.block("air"),T);}}'
  echo 'js var T=Vars.state.rules.defaultTeam; Vars.world.tile(250,196).setBlock(Vars.content.block("ironworks-rbmk-reactor"),T); var b=Vars.world.tile(250,196).build; b.applyConfig(-12); b.applyConfig(-3); var manual=b.alertsMuted; b.applyConfig(-11); var auto=b.alertsMuted; b.applyConfig(-12); b.applyConfig(-3); b.applyConfig(-10); var semi=b.alertsMuted; Log.info("T167 manual=" + manual + " auto=" + auto + " semi=" + semi)'
  # ---- 168. 9×9 分区堆：方块尺寸 9、9 个分区、默认总控 ----
  echo 'js var T=Vars.state.rules.defaultTeam; for(var y=184;y<=212;y++){for(var x=234;x<=270;x++){var t=Vars.world.tile(x,y); if(t!=null)t.setBlock(Vars.content.block("air"),T);}} Vars.world.tile(250,196).setBlock(Vars.content.block("ironworks-rbmk-reactor-9"),T); var b=Vars.world.tile(250,196).build; Log.info("T168 size=" + b.block.size + " zones=" + b.zoneHeat.length + " sel=" + b.selectedZone)'
  # ---- 169. 手动分区棒位：0/1/2 号堆独立目标，通量必须分开 ----
  echo 'js var T=Vars.state.rules.defaultTeam; for(var y=184;y<=212;y++){for(var x=234;x<=270;x++){var t=Vars.world.tile(x,y); if(t!=null)t.setBlock(Vars.content.block("air"),T);}} Vars.world.tile(250,196).setBlock(Vars.content.block("ironworks-rbmk-reactor-9"),T); var b=Vars.world.tile(250,196).build; var fr=Vars.content.item("ironworks-fuel-rod"), w=Vars.content.liquid("water"); b.items.add(fr,5); b.liquids.set(w,10000); b.applyConfig(-12); b.applyConfig(-30); b.applyConfig(0); b.applyConfig(-21); b.applyConfig(80); b.applyConfig(-22); b.applyConfig(20); for(var i=0;i<300;i++) b.updateTile(); var indep=(b.zoneFlux[0] > b.zoneFlux[1] * 1.5 && b.zoneRods[1] > 0.7 && b.zoneRods[2] < 0.3); Log.info("T169 r0=" + b.zoneRods[0].toFixed(2) + " r1=" + b.zoneRods[1].toFixed(2) + " r2=" + b.zoneRods[2].toFixed(2) + " f0=" + Math.round(b.zoneFlux[0]*100) + " f1=" + Math.round(b.zoneFlux[1]*100) + " indep=" + indep + " melted=" + b.melted)'
  # ---- 170. 总控：一次写入 9 个分区的目标棒位 ----
  echo 'js var T=Vars.state.rules.defaultTeam; for(var y=184;y<=212;y++){for(var x=234;x<=270;x++){var t=Vars.world.tile(x,y); if(t!=null)t.setBlock(Vars.content.block("air"),T);}} Vars.world.tile(250,196).setBlock(Vars.content.block("ironworks-rbmk-reactor-9"),T); var b=Vars.world.tile(250,196).build; b.applyConfig(-12); b.applyConfig(-30); b.applyConfig(35); var all=true; for(var i=0;i<9;i++){ if(Math.abs(b.zoneTarget[i]-0.35)>0.001) all=false; } Log.info("T170 all35=" + all + " sel=" + b.selectedZone)'
  # ---- 171. 冷却节流：总控关到 50%，分区流量必须下降 ----
  echo 'js var T=Vars.state.rules.defaultTeam; for(var y=184;y<=212;y++){for(var x=234;x<=270;x++){var t=Vars.world.tile(x,y); if(t!=null)t.setBlock(Vars.content.block("air"),T);}} Vars.world.tile(250,196).setBlock(Vars.content.block("ironworks-rbmk-reactor-9"),T); var b=Vars.world.tile(250,196).build; b.items.add(Vars.content.item("ironworks-fuel-rod"),5); b.liquids.set(Vars.content.liquid("water"),10000); b.applyConfig(-12); b.applyConfig(-30); b.applyConfig(40); for(var i=0;i<40;i++) b.updateTile(); var f0=0; for(var i=0;i<9;i++) f0+=b.zoneFlow[i]; f0/=9; b.applyConfig(3050); for(var i=0;i<40;i++) b.updateTile(); var f1=0; for(var i=0;i<9;i++) f1+=b.zoneFlow[i]; f1/=9; Log.info("T171 th=" + b.zoneThrottle[0].toFixed(2) + " flow0=" + f0.toFixed(2) + " flow1=" + f1.toFixed(2) + " less=" + (f1 < f0 * 0.75))'
  # ---- 172. 单区节流：只改选中区，其它区保持 100% ----
  echo 'js var T=Vars.state.rules.defaultTeam; for(var y=184;y<=212;y++){for(var x=234;x<=270;x++){var t=Vars.world.tile(x,y); if(t!=null)t.setBlock(Vars.content.block("air"),T);}} Vars.world.tile(250,196).setBlock(Vars.content.block("ironworks-rbmk-reactor-9"),T); var b=Vars.world.tile(250,196).build; b.applyConfig(-12); b.applyConfig(-24); b.applyConfig(3050); Log.info("T172 th4=" + b.zoneThrottle[4].toFixed(2) + " th0=" + b.zoneThrottle[0].toFixed(2) + " sel=" + b.selectedZone)'
  # ---- 173. 半自动下切换分区不得偷改公共目标棒位 ----
  echo 'js var T=Vars.state.rules.defaultTeam; for(var y=184;y<=212;y++){for(var x=234;x<=270;x++){var t=Vars.world.tile(x,y); if(t!=null)t.setBlock(Vars.content.block("air"),T);}} Vars.world.tile(250,196).setBlock(Vars.content.block("ironworks-rbmk-reactor-9"),T); var b=Vars.world.tile(250,196).build; b.applyConfig(-12); b.applyConfig(-25); b.applyConfig(90); b.applyConfig(-10); var before=b.targetRods; b.applyConfig(-26); var after=b.targetRods; Log.info("T173 same=" + (Math.abs(before-after)<0.001) + " sel=" + b.selectedZone)'
  # ---- 174. 自动模式忽略单区节流，冷却仍按公共集管满流 ----
  echo 'js var T=Vars.state.rules.defaultTeam; for(var y=184;y<=212;y++){for(var x=234;x<=270;x++){var t=Vars.world.tile(x,y); if(t!=null)t.setBlock(Vars.content.block("air"),T);}} Vars.world.tile(250,196).setBlock(Vars.content.block("ironworks-rbmk-reactor-9"),T); var b=Vars.world.tile(250,196).build; b.items.add(Vars.content.item("ironworks-fuel-rod"),5); b.liquids.set(Vars.content.liquid("water"),10000); b.applyConfig(-12); b.applyConfig(-30); b.applyConfig(40); b.applyConfig(3010); b.applyConfig(-11); b.applyConfig(100); for(var i=0;i<40;i++) b.updateTile(); var avg=0; for(var i=0;i<9;i++) avg+=b.zoneFlow[i]; avg/=9; Log.info("T174 flow=" + avg.toFixed(2) + " high=" + (avg > 0.8))'
  # ---- 175. SCRAM 闪蒸必须把热冲击分给全部 9 区，而不是只写全局标量 ----
  echo 'js var T=Vars.state.rules.defaultTeam; for(var y=184;y<=212;y++){for(var x=234;x<=270;x++){var t=Vars.world.tile(x,y); if(t!=null)t.setBlock(Vars.content.block("air"),T);}} Vars.world.tile(250,196).setBlock(Vars.content.block("ironworks-rbmk-reactor-9"),T); var b=Vars.world.tile(250,196).build; b.items.add(Vars.content.item("ironworks-fuel-rod"),5); b.liquids.set(Vars.content.liquid("water"),10000); b.applyConfig(-12); b.applyConfig(-30); b.applyConfig(0); for(var i=0;i<9;i++){ b.zoneHeat[i]=0.8; b.zoneFlux[i]=1.0; } b.heat=0.8; b.flux=1.0; b.applyConfig(-1); var prompts=0, targets=0; for(var i=0;i<9;i++){ if(b.zonePrompt[i]>0.1) prompts++; if(b.zoneTarget[i]>0.99) targets++; } Log.info("T175 flash=" + (b.promptHeat>0.1) + " prompts=" + prompts + " targets=" + targets)'
  # ---- 176. 4×4 大型汽轮机：360 蒸汽/秒 → 72000 电/秒，冷凝 360 温水/秒 ----
  echo 'js var T=Vars.state.rules.defaultTeam; for(var y=184;y<=212;y++){for(var x=234;x<=270;x++){var t=Vars.world.tile(x,y); if(t!=null)t.setBlock(Vars.content.block("air"),T);}} Vars.world.tile(250,196).setBlock(Vars.content.block("ironworks-steam-turbine-4"),T); var b=Vars.world.tile(250,196).build; b.liquids.set(Vars.content.liquid("ironworks-steam"),2000); for(var i=0;i<60;i++) b.updateTile(); var power=Math.round(b.getPowerProduction()*60), warm=Math.round(b.liquids.get(Vars.content.liquid("ironworks-warm-water"))), used=Math.round(2000-b.liquids.get(Vars.content.liquid("ironworks-steam"))), flow=Math.round(b.flow*100), ratio=Math.round(warm/Math.max(1,used)*100)/100; Log.info("T176 power=" + power + " warm=" + warm + " used=" + used + " ratio=" + ratio + " flow=" + flow)'
  # ---- 177. 12×12 分区堆常态通量：9×360=3240 L/s，单区约 360 ----
  echo 'js var T=Vars.state.rules.defaultTeam; for(var y=184;y<=212;y++){for(var x=234;x<=270;x++){var t=Vars.world.tile(x,y); if(t!=null)t.setBlock(Vars.content.block("air"),T);}} Vars.world.tile(250,196).setBlock(Vars.content.block("ironworks-rbmk-reactor-9"),T); var b=Vars.world.tile(250,196).build; var w=Vars.content.liquid("water"), st=Vars.content.liquid("ironworks-steam"); b.items.add(Vars.content.item("ironworks-fuel-rod"),5); b.applyConfig(-12); b.applyConfig(1100); b.liquids.set(w,32400); b.applyConfig(-30); b.applyConfig(0); for(var i=0;i<300;i++){ for(var z=0;z<9;z++) b.zoneFlux[z]=1.0; b.liquids.set(st,0); b.updateTile(); } var rate=Math.round(b.steamRate), per=Math.round(rate/9); Log.info("T177 rate=" + rate + " perZone=" + per + " near=" + (rate>3100 && rate<3350))'
  sleep 2
  # ---- 178. 大型冷却塔：4×4，600 L/s 温水 → 水 ----
  echo 'js var T=Vars.state.rules.defaultTeam; for(var y=184;y<=212;y++){for(var x=234;x<=270;x++){var t=Vars.world.tile(x,y); if(t!=null)t.setBlock(Vars.content.block("air"),T);}} Vars.world.tile(248,196).setBlock(Vars.content.block("power-source"),T); Vars.world.tile(250,196).setBlock(Vars.content.block("ironworks-cooling-tower-large"),T); var b=Vars.world.tile(250,196).build; b.liquids.add(Vars.content.liquid("ironworks-warm-water"),960); Log.info("T178 setup=" + (b!=null))'
  sleep 3
  echo 'js var T=Vars.state.rules.defaultTeam; var b=Vars.world.tile(250,196).build; if(b==null){Vars.world.tile(250,196).setBlock(Vars.content.block("ironworks-cooling-tower-large"),T); b=Vars.world.tile(250,196).build; b.liquids.add(Vars.content.liquid("ironworks-warm-water"),960); Log.info("T178 retry=true");}'
  sleep 3
  echo 'js var b=Vars.world.tile(250,196).build; if(b==null){Log.info("T178 missing=true");}else{Log.info("T178 water=" + Math.round(b.liquids.get(Vars.content.liquid("water"))) + " warm=" + Math.round(b.liquids.get(Vars.content.liquid("ironworks-warm-water"))))}'
  # ---- 179. 侧面贴图：导管贴在汽轮机侧面（不朝前）也应张开 blendbits ----
  echo 'js var T=Vars.state.rules.defaultTeam; for(var y=184;y<=212;y++){for(var x=234;x<=270;x++){var t=Vars.world.tile(x,y); if(t!=null)t.setBlock(Vars.content.block("air"),T);}} Vars.world.tile(250,196).setBlock(Vars.content.block("ironworks-steam-turbine-4"),T); var b=Vars.world.tile(250,196).build; var tx=b.tileX(), ty=b.tileY(); Vars.world.tile(tx-2,ty+1).setBlock(Vars.content.block("conduit"),T); var c=Vars.world.tile(tx-2,ty+1).build; c.rotation=1; c.updateProximity(); var side=Vars.world.build(tx-1,ty+1); Log.info("T179 outs=" + b.block.outputsLiquid + " side=" + (side==b) + " bits=" + c.blendbits)'

  # ---- 180. v2.5 分区水：公共水按容量×偏差分到9区，单区上限3600 ----
  echo 'js var T=Vars.state.rules.defaultTeam; for(var y=184;y<=212;y++){for(var x=234;x<=270;x++){var t=Vars.world.tile(x,y); if(t!=null)t.setBlock(Vars.content.block("air"),T);}} Vars.world.tile(250,196).setBlock(Vars.content.block("ironworks-rbmk-reactor-9"),T); var b=Vars.world.tile(250,196).build; b.liquids.set(Vars.content.liquid("water"),32400); b.items.add(Vars.content.item("ironworks-fuel-rod"),1); b.updateTile(); var sw=0,cap=0,mn=99999,mx=0; for(var i=0;i<9;i++){sw+=b.zoneWater[i]; cap+=b.zoneWaterCap[i]; if(b.zoneWater[i]<mn)mn=b.zoneWater[i]; if(b.zoneWater[i]>mx)mx=b.zoneWater[i];} Log.info("T180 water=" + Math.round(sw) + " cap=" + Math.round(cap) + " min=" + Math.round(mn) + " max=" + Math.round(mx))'
  # ---- 181. v2.5 温度加速蒸发：冷却剂1.0时因子1.3，满通量应超过3240 L/s ----
  echo 'js var T=Vars.state.rules.defaultTeam; for(var y=184;y<=212;y++){for(var x=234;x<=270;x++){var t=Vars.world.tile(x,y); if(t!=null)t.setBlock(Vars.content.block("air"),T);}} Vars.world.tile(250,196).setBlock(Vars.content.block("ironworks-rbmk-reactor-9"),T); var b=Vars.world.tile(250,196).build; var w=Vars.content.liquid("water"), st=Vars.content.liquid("ironworks-steam"); b.items.add(Vars.content.item("ironworks-fuel-rod"),5); b.applyConfig(-12); b.applyConfig(1100); b.liquids.set(w,32400); b.applyConfig(-30); b.applyConfig(0); for(var k=0;k<200;k++){ b.liquids.set(st,0); for(var z=0;z<9;z++){ b.zoneFlux[z]=1.0; b.zoneCoolant[z]=1.0; } b.updateTile(); } Log.info("T181 rate=" + Math.round(b.steamRate) + " evap=" + (Math.round(b.steamRate/3240*100)/100))'
  # ---- 182. v2.5 棒尖曲线：0.1插入应比0.2产生更高通量 ----
  echo 'js var T=Vars.state.rules.defaultTeam; for(var y=184;y<=212;y++){for(var x=234;x<=270;x++){var t=Vars.world.tile(x,y); if(t!=null)t.setBlock(Vars.content.block("air"),T);}} Vars.world.tile(250,196).setBlock(Vars.content.block("ironworks-rbmk-reactor-9"),T); var b=Vars.world.tile(250,196).build; b.items.add(Vars.content.item("ironworks-fuel-rod"),5); b.applyConfig(-12); b.applyConfig(1100); b.liquids.set(Vars.content.liquid("water"),32400); b.applyConfig(-30); b.updateTile(); for(var z=0;z<9;z++){ b.zoneRods[z]=0.1; b.zoneTarget[z]=0.1; } var st=Vars.content.liquid("ironworks-steam"); for(var k=0;k<240;k++){ var w=Vars.content.liquid("water"); b.liquids.set(w,32400); b.liquids.set(st,0); for(var z=0;z<9;z++){ b.zoneHeat[z]=0.5; b.zoneCoolant[z]=0.5; } b.updateTile(); } var f1=b.flux; for(var z=0;z<9;z++){ b.zoneRods[z]=0.2; b.zoneTarget[z]=0.2; } for(var k=0;k<240;k++){ var w2=Vars.content.liquid("water"); b.liquids.set(w2,32400); b.liquids.set(st,0); for(var z=0;z<9;z++){ b.zoneHeat[z]=0.5; b.zoneCoolant[z]=0.5; } b.updateTile(); } var f2=b.flux; Log.info("T182 f1=" + f1.toFixed(2) + " f2=" + f2.toFixed(2) + " tip=" + (f1>f2))'
  # ---- 183. v2.5 衰变热：停堆通量历史仍产生7%量级热 ----
  echo 'js var T=Vars.state.rules.defaultTeam; for(var y=184;y<=212;y++){for(var x=234;x<=270;x++){var t=Vars.world.tile(x,y); if(t!=null)t.setBlock(Vars.content.block("air"),T);}} Vars.world.tile(250,196).setBlock(Vars.content.block("ironworks-rbmk-reactor-9"),T); var b=Vars.world.tile(250,196).build; b.items.add(Vars.content.item("ironworks-fuel-rod"),5); b.updateTile(); b.fluxHistory=1.0; b.decayTimer=0.0; b.flux=0.0; b.updateTile(); Log.info("T183 decay=" + Math.round(b.decayHeat*1000)/1000 + " positive=" + (b.decayHeat>0.01))'
  # ---- 184. v2.5 双节点熔毁损伤：燃料2.5、无水时逐区损伤累积 ----
  echo 'js var T=Vars.state.rules.defaultTeam; for(var y=184;y<=212;y++){for(var x=234;x<=270;x++){var t=Vars.world.tile(x,y); if(t!=null)t.setBlock(Vars.content.block("air"),T);}} Vars.world.tile(250,196).setBlock(Vars.content.block("ironworks-rbmk-reactor-9"),T); var b=Vars.world.tile(250,196).build; b.items.add(Vars.content.item("ironworks-fuel-rod"),5); b.applyConfig(-12); b.updateTile(); b.liquids.set(Vars.content.liquid("water"),0); b.liquids.set(Vars.content.liquid("ironworks-steam"),0); for(var z=0;z<9;z++){ b.zoneHeat[z]=2.5; b.zoneCoolant[z]=1.0; b.zoneMeltDamage[z]=0.0; } for(var k=0;k<30;k++) b.updateTile(); var md=0; for(var z=0;z<9;z++){ if(b.zoneMeltDamage[z]>md) md=b.zoneMeltDamage[z]; } Log.info("T184 damage=" + Math.round(md*1000)/1000 + " accumulated=" + (md>0.001))'
  # ---- 185. v2.5 SCRAM锁存：自动模式按SCRAM后不会下一tick被目标功率拔棒，-4复位 ----
  echo 'js var T=Vars.state.rules.defaultTeam; for(var y=184;y<=212;y++){for(var x=234;x<=270;x++){var t=Vars.world.tile(x,y); if(t!=null)t.setBlock(Vars.content.block("air"),T);}} Vars.world.tile(250,196).setBlock(Vars.content.block("ironworks-rbmk-reactor-9"),T); var b=Vars.world.tile(250,196).build; b.items.add(Vars.content.item("ironworks-fuel-rod"),5); b.liquids.set(Vars.content.liquid("water"),20000); b.applyConfig(-11); b.applyConfig(100); b.updateTile(); b.applyConfig(-1); for(var k=0;k<10;k++) b.updateTile(); var held=b.targetRods; b.applyConfig(-4); b.applyConfig(100); b.updateTile(); var released=b.targetRods; Log.info("T185 held=" + held.toFixed(2) + " released=" + released.toFixed(2) + " latch=" + (held>0.99 && released<0.9))'
  echo 'exit'
) | timeout 2400 "$JDK/bin/java" -jar "$JAR" 2>&1 | sed 's/\x1b\[[0-9;]*m//g' | grep -E "^\[.*\] \[I\] T[0-9]+[a-z]? |Error|Exception"
