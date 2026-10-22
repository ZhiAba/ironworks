#!/usr/bin/env node
// 测试选择器：从 chain-test.sh 里挑出指定编号（或分组）的测试块，输出可执行脚本。
// 用法：
//   node tools/select-tests.js 95 96
//   node tools/select-tests.js group liquid
//   node tools/select-tests.js list
const fs = require('fs');
const path = require('path');
const root = path.resolve(__dirname, '..');
const src = fs.readFileSync(path.join(root, 'tools/chain-test.sh'), 'utf8').split('\n');

// 分组关键词（匹配测试注释里的中文描述）
const groups = {
  liquid:  ['液','导管','管道','桶','通液','冷','分馏','焦化','橡胶'],
  power:   ['电','发电','供电'],
  turret:  ['炮','塔','弹','射'],
  factory: ['工厂','厂','配方','机','冶炼','铸造','合金','铝','硅','石墨'],
  multicrafter: ['配方','多配方','切配方'],
  content: ['物品','材料','科技','数据库','内容'],
  ui:      ['详情','界面','条','UI']
};

const marker = /^\s*#\s*----\s*([0-9]+[a-z]?)\.\s*(.+?)\s*----\s*$/;
const starts = [];
for (let i = 0; i < src.length; i++) {
  const m = src[i].match(marker);
  if (m) starts.push({ id: m[1], desc: m[2], line: i });
}
if (!starts.length) { console.error('没有找到测试块'); process.exit(1); }

function blockOf(idx) {
  const from = starts[idx].line;
  const to = idx + 1 < starts.length ? starts[idx + 1].line : src.findIndex((l, i) => i > from && l.trim() === "echo 'exit'");
  return src.slice(from, to);
}

const args = process.argv.slice(2);
if (!starts.length || args[0] === 'list') {
  for (const s of starts) console.log(s.id + '\t' + s.desc);
  process.exit(0);
}

let picked = [];
if (args[0] === 'group') {
  const name = args[1];
  const keys = groups[name];
  if (!keys) { console.error('未知分组: ' + name + '，可用: ' + Object.keys(groups).join(', ')); process.exit(1); }
  picked = starts.map((s, i) => ({ s, i })).filter(({ s }) => keys.some(k => s.desc.includes(k))).map(({ i }) => i);
} else {
  const want = new Set(args);
  picked = starts.map((s, i) => ({ s, i })).filter(({ s }) => want.has(s.id)).map(({ i }) => i);
  if (!picked.length) { console.error('没有匹配的测试编号'); process.exit(1); }
}
picked.sort((a, b) => a - b);

const headerEnd = starts[0].line;
const exitLine = src.findIndex((l, i) => i > headerEnd && l.trim() === "echo 'exit'");
const out = [];
out.push(...src.slice(0, headerEnd));
for (const i of picked) out.push(...blockOf(i));
out.push('');
out.push(...src.slice(exitLine));
process.stdout.write(out.join('\n'));
console.error(`[select] 选中 ${picked.length} 个测试: ${picked.map(i => starts[i].id).join(' ')}`);
