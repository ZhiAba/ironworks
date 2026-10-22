#!/usr/bin/env node
// 读取测试日志与期望值，输出 PASS/FAIL 摘要。
// 用法：node tools/check-results.js <logfile>
const fs = require('fs');
const path = require('path');
const root = path.resolve(__dirname, '..');
const logFile = process.argv[2];
if (!logFile || !fs.existsSync(logFile)) { console.error('缺少日志文件'); process.exit(2); }
const log = fs.readFileSync(logFile, 'utf8').split('\n');
const expect = fs.readFileSync(path.join(root, 'tools/test-expect.txt'), 'utf8')
  .split('\n').map(l => l.trim()).filter(l => l && !l.startsWith('#'));

let pass = 0, fail = 0, skip = 0;
const failures = [];
for (const line of expect) {
  const [id, pattern] = line.split('\t');
  if (!id || !pattern) continue;
  const rows = log.filter(l => new RegExp('T' + id + '[^0-9]').test(l));
  if (!rows.length) { skip++; continue; }
  const re = new RegExp(pattern);
  if (rows.some(r => re.test(r))) { pass++; }
  else { fail++; failures.push('T' + id + ' 期望 /' + pattern + '/，实际: ' + rows[0].slice(0, 120)); }
}
for (const f of failures) console.log('FAIL ' + f);
console.log(`ASSERT pass=${pass} fail=${fail} skipped_not_run=${skip}`);
process.exit(fail ? 1 : 0);
