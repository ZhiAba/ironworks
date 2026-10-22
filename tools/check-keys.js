#!/usr/bin/env node
// 本地化键静态检查：源码里声明的内容，中英文 bundle 必须有对应键。
// 用法：node tools/check-keys.js
const fs = require('fs');
const path = require('path');

const root = path.resolve(__dirname, '..');
const srcDir = path.join(root, 'src/ironworks');
const zh = fs.readFileSync(path.join(root, 'assets/bundles/bundle_zh_CN.properties'), 'utf8');
const en = fs.readFileSync(path.join(root, 'assets/bundles/bundle.properties'), 'utf8');

const files = [];
(function walk(d) {
  for (const f of fs.readdirSync(d)) {
    const p = path.join(d, f);
    if (fs.statSync(p).isDirectory()) walk(p);
    else if (f.endsWith('.java')) files.push(p);
  }
})(srcDir);

const kinds = {
  Item: 'item', Liquid: 'liquid', StatusEffect: 'status',
  MultiCrafter: 'block', GenericCrafter: 'block', PowerTurret: 'block',
  ItemTurret: 'block', Conveyor: 'block', Wall: 'block', Turret: 'block',
  OverheatTurret: 'block', RailTurret: 'block', SingularityTurret: 'block', MortarTurret: 'block'
};

const found = new Set();
const recipes = new Set();
for (const f of files) {
  const s = fs.readFileSync(f, 'utf8');
  for (const m of s.matchAll(/new\s+([A-Za-z]+)\("([a-z0-9-]+)"\)/g)) {
    const kind = kinds[m[1]];
    if (kind) found.add(`${kind}.ironworks-${m[2]}.name`);
  }
  for (const m of s.matchAll(/new\s+Recipe\("([a-z0-9-]+)"/g)) recipes.add(m[1]);
}
for (const r of recipes) found.add(`recipe.ironworks.${r}`);

const missing = [];
for (const key of found) {
  const line = new RegExp('^' + key.replace(/\./g, '\\.') + '\\s*=', 'm');
  if (!line.test(zh)) missing.push(key + '  (缺中文)');
  if (!line.test(en)) missing.push(key + '  (缺英文)');
}
if (missing.length) {
  console.log('缺失本地化键 ' + missing.length + ' 个:');
  for (const m of missing) console.log('  ' + m);
  process.exit(1);
}
console.log(`本地化键检查通过：${found.size} 个键，中英文齐全`);
