#!/usr/bin/env node
/** 平衡推演：时期 × 造价 × 弹药消耗（速率×单位成本） */
const PRICE = { copper:1, lead:1, titanium:2, sand:0.5, coal:1, oil:1.5, water:0.2, nitrogen:2, silicon:2, surgeAlloy:8 };

// 物资产量（每 craft 产出 / 消耗）
const RECIPES = {
  copperPlate:{out:1, in:{copper:2}},
  leadPlate:{out:1, in:{lead:2}},
  titaniumPlate:{out:1, in:{titanium:2}},
  pureCopper:{out:1, in:{copperPlate:1, water:15}},
  pureLead:{out:1, in:{leadPlate:1, water:15}},
  hydrogen:{out:1, in:{water:1}},
  oxygen:{out:1, in:{water:1}},
  // 调整：硫 4 油 + 2 氢 -> 2 个
  sulfur:{out:2, in:{oil:4, hydrogen:2}},
  // 调整：火药 1 煤 + 1 硫 + 3 氮 -> 3 个
  blackPowder:{out:3, in:{coal:1, sulfur:1, nitrogen:3}},
  ironConcentrate:{out:1, in:{sand:3}},
  ironIngot:{out:1, in:{ironConcentrate:1, hydrogen:2}},
  // 焦炭路线：高炉(2矿+2煤→6铁水) → 转炉(6铁水+3氧→6钢水) → 连铸(6钢水→2锭)
  steelIngot:{out:2, in:{ironConcentrate:2, coal:2, oxygen:3}},
  steelPlate:{out:2, in:{steelIngot:1}},
  casing:{out:4, in:{copperPlate:1}},
  copperHead:{out:2, in:{copper:1}},
  leadHead:{out:2, in:{lead:1}},
  titaniumHead:{out:2, in:{titanium:1}},
  alloyHead:{out:2, in:{surgeAlloy:1}},
  // 调整：批量提高
  copperAmmo:{out:8, in:{casing:1, copper:2, blackPowder:1}},
  heavyAmmo:{out:2, in:{casing:1, leadHead:2, blackPowder:2}},
  leadCoreAmmo:{out:3, in:{casing:1, leadHead:1, pureLead:1, blackPowder:1}},
  tiPierceAmmo:{out:1, in:{casing:1, titaniumHead:1, blackPowder:1}},
  tiOverpressureAmmo:{out:1, in:{casing:1, alloyHead:1, blackPowder:2}},
  napalmAmmo:{out:2, in:{oilBarrel:1, titaniumPlate:1, silicon:1}},
  corrosiveAmmo:{out:3, in:{casing:1, titaniumPlate:1, sulfur:2}},
  oilBarrel:{out:1, in:{oil:30, copperPlate:1}},
  ironPlate:{out:1, in:{ironIngot:2}},
  heShell:{out:2, in:{casing:1, blackPowder:2, ironPlate:2}},
  apShell:{out:2, in:{casing:1, blackPowder:1, titaniumPlate:2, ironPlate:1}},
  incendiaryShell:{out:2, in:{casing:1, blackPowder:1, ironPlate:2, oil:6}},
  fragShell:{out:1, in:{casing:1, blackPowder:2, ironPlate:1, leadPlate:1}},
  reactivePlate:{out:1, in:{steelPlate:2, leadPlate:1}},
  compositePlate:{out:1, in:{titaniumPlate:1, steelPlate:1, leadPlate:1}},
  nanitePlate:{out:1, in:{steelPlate:1, silicon:1, copper:1}},
};

const cost = Object.assign({}, PRICE);
for (let i=0;i<80;i++) for (const [k,r] of Object.entries(RECIPES)) {
  let c=0; for (const [m,v] of Object.entries(r.in)) c += (cost[m]??0)*v;
  const u=c/r.out; if (cost[k]==null||u<cost[k]) cost[k]=u;
}

// 炮塔：reload 单位 tick；子弹 damage / reloadMultiplier / ammoMultiplier
const TURRETS = [
  { name:"duo(原版)", tier:"T1", size:1, range:160, reload:20, build:{copper:35},
    ammo:[{n:"copper",dmg:9,rm:1,am:2}] },
  { name:"速射炮", tier:"T2", size:2, range:160, reload:8,
    build:{copperPlate:40, copper:120, lead:80},
    ammo:[
      {n:"copperAmmo",dmg:16,rm:1,am:1},
      {n:"heavyAmmo",dmg:50,rm:3,am:2},
    ]},
  { name:"salvo(原版)", tier:"T3", size:2, range:190, reload:29, shots:4, build:{copper:100,graphite:80,silicon:60,titanium:50},
    ammo:[{n:"silicon",dmg:31,rm:1,am:5}] },
  { name:"穿甲炮", tier:"T3+", size:3, range:300, reload:25,
    build:{steelPlate:60, titanium:120, silicon:80},
    ammo:[
      {n:"leadCoreAmmo",dmg:30,rm:1,am:3},
      {n:"tiPierceAmmo",dmg:42,rm:1,am:5},
      {n:"tiOverpressureAmmo",dmg:90,rm:1,am:8},
      {n:"napalmAmmo",dmg:38,rm:1,am:4},
      {n:"corrosiveAmmo",dmg:30,rm:1,am:3,dot:24},
    ]},
  { name:"迫击炮", tier:"T3+", size:3, range:560, reload:60, build:{steelPlate:80, ironPlate:60, titanium:100, silicon:60},
    ammo:[
      {n:"heShell",dmg:345,rm:1,am:1},
      {n:"apShell",dmg:320,rm:1,am:1},
      {n:"incendiaryShell",dmg:215,rm:1,am:2,dot:24},
      {n:"fragShell",dmg:345,rm:1,am:2},
    ]},
];

const f=(n)=>n<10?n.toFixed(1):Math.round(n);
const buildCost=(b)=>Object.entries(b).reduce((s,[k,v])=>s+(cost[k]??PRICE[k]??1)*v,0);

console.log("=== 弹药单位成本（铜当量）===");
for (const k of ["casing","blackPowder","copperAmmo","heavyAmmo","leadCoreAmmo","tiPierceAmmo","tiOverpressureAmmo","napalmAmmo","corrosiveAmmo"])
  console.log("  "+k.padEnd(20)+f(cost[k]));

console.log("\n=== 外挂装甲（每件成本 / 护甲加成）===");
const ARMORS=[["reactivePlate",8],["compositePlate",6],["nanitePlate",3]];
for(const [k,bonus] of ARMORS){
  console.log("  "+k.padEnd(16)+f(cost[k]).toString().padEnd(8)+"armor +"+bonus);
}

console.log("\n=== 综合平衡表 ===");
console.log("炮塔".padEnd(12)+"T".padEnd(5)+"造价".padEnd(7)+"弹药".padEnd(20)+"弹耗/秒".padEnd(9)+"成本/秒".padEnd(9)+"DPS".padEnd(6)+"效率");
for (const t of TURRETS){
  for (const a of t.ammo){
    const sps = (60/t.reload)/a.rm*(t.shots??1);
    const dps = a.dmg*sps + (a.dot??0);
    const ammoPerSec = sps/a.am;
    const cps = ammoPerSec*(cost[a.n]??1);
    console.log(t.name.padEnd(12)+t.tier.padEnd(5)+f(buildCost(t.build)).toString().padEnd(7)+
      a.n.padEnd(20)+f(ammoPerSec).toString().padEnd(9)+f(cps).toString().padEnd(9)+
      f(dps).toString().padEnd(6)+f(dps/cps));
  }
}
