#!/usr/bin/env node
// 本地小模型（qwen3-4b）的轻量 agent 外壳。
// 目的：绕开 DSH 子代理那套 ~17K 的大提示词，用几百 token 的系统提示 + 4 个工具跑机械任务。
// 用法：node tools/local-agent.mjs "任务描述" [--cwd /path] [--rounds 6] [--max-tokens 1200]
import {spawnSync} from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';

const ENDPOINT = process.env.LOCAL_CODE_URL || 'http://127.0.0.1:18080/v1/chat/completions';
const MODEL = process.env.LOCAL_CODE_MODEL || 'qwen3-4b';

const argv = process.argv.slice(2);
const argRaw = (name, def) => { const i = argv.indexOf('--' + name); return i >= 0 && argv[i + 1] ? argv[i + 1] : def; };
const taskFile = argRaw('task-file', null);
const task = taskFile ? fs.readFileSync(taskFile, 'utf8') : (argv.find(a => !a.startsWith('--')) || '');
const arg = (name, def) => { const i = argv.indexOf('--' + name); return i >= 0 && argv[i + 1] ? argv[i + 1] : def; };
const CWD = path.resolve(arg('cwd', process.cwd()));
const ROUNDS = parseInt(arg('rounds', '6'), 10);
const MAXTOK = parseInt(arg('max-tokens', '1200'), 10);
const DEADLINE = Date.now() + parseInt(arg('minutes', '8'), 10) * 60000;

if(!task){ console.error('用法: node tools/local-agent.mjs "任务描述" [--task-file f.md] [--cwd .] [--rounds 6] [--minutes 8]'); process.exit(2); }

const clip = (s, n) => (s || '').length > n ? s.slice(0, n) + `\n…[截断 ${s.length - n} 字符]` : (s || '');
const inside = p => { const r = path.resolve(CWD, p); return r === CWD || r.startsWith(CWD + path.sep) || r.startsWith('/tmp/'); };

const TOOLS = [
 {type:'function', function:{name:'bash', description:'在仓库目录执行 bash 命令并返回输出',
   parameters:{type:'object', properties:{cmd:{type:'string'}}, required:['cmd']}}},
 {type:'function', function:{name:'read_file', description:'读取文本文件',
   parameters:{type:'object', properties:{path:{type:'string'}, offset:{type:'integer'}, limit:{type:'integer'}}, required:['path']}}},
 {type:'function', function:{name:'write_file', description:'写入/覆盖文本文件（只能写仓库内或 /tmp）',
   parameters:{type:'object', properties:{path:{type:'string'}, content:{type:'string'}}, required:['path','content']}}},
 {type:'function', function:{name:'finish', description:'任务完成时调用',
   parameters:{type:'object', properties:{summary:{type:'string'}}, required:['summary']}}}
];

const SYS = `你是在 Android 手机上工作的代码助手，工作目录 ${CWD}。
规则：
1. 只做任务要求的事，不顺手重构无关文件。
2. 改代码前先 read_file 看清原文；写完可以 bash 跑 "bash -n" 之类做语法检查。
3. 不构建、不部署、不改 mod.hjson。
4. 不要输出解释性长文，直接调用工具干活。
5. 全部完成后调用 finish，summary 用 3 行以内说明改了什么。`;

function runTool(call){
  const name = call.function.name;
  let a = {};
  try { a = JSON.parse(call.function.arguments || '{}'); } catch(e) { return `参数 JSON 解析失败: ${e.message}`; }
  if(name === 'bash'){
    const r = spawnSync('bash', ['-c', a.cmd || ''], {cwd: CWD, encoding:'utf8', timeout: 180000, maxBuffer: 8*1024*1024});
    return clip(`exit=${r.status}\nSTDOUT:\n${r.stdout||''}\nSTDERR:\n${r.stderr||''}`, 4000);
  }
  if(name === 'read_file'){
    const p = path.resolve(CWD, a.path);
    if(!inside(a.path)) return '拒绝：路径不在仓库或 /tmp 内';
    try {
      const lines = fs.readFileSync(p, 'utf8').split('\n');
      const off = Math.max(0, (a.offset|0) - 1 || 0), lim = a.limit || 200;
      return clip(lines.slice(off, off + lim).map((l,i)=>`${off+i+1}\t${l}`).join('\n'), 8000);
    } catch(e){ return '读取失败: ' + e.message; }
  }
  if(name === 'write_file'){
    if(!inside(a.path)) return '拒绝：路径不在仓库或 /tmp 内';
    const p = path.resolve(CWD, a.path);
    try { fs.mkdirSync(path.dirname(p), {recursive:true}); fs.writeFileSync(p, a.content ?? ''); return `已写入 ${p} (${(a.content||'').length} 字符)`; }
    catch(e){ return '写入失败: ' + e.message; }
  }
  return '未知工具 ' + name;
}

function chat(messages){
  // 用 curl 而不是 fetch：undici 默认 headersTimeout=300s，本地模型慢时会被误杀
  const body = {model: MODEL, messages, tools: TOOLS, tool_choice: 'auto', temperature: 0.2, max_tokens: MAXTOK, stream: false};
  const reqFile = '/tmp/local-agent-req.json', respFile = '/tmp/local-agent-resp.json';
  fs.writeFileSync(reqFile, JSON.stringify(body));
  const r = spawnSync('curl', ['-s','-m','900','-X','POST', ENDPOINT, '-H','Content-Type: application/json','-d','@'+reqFile,'-o',respFile,'-w','%{http_code}'],
    {encoding:'utf8', timeout: 920000});
  if(r.status !== 0) throw new Error('curl 失败: ' + (r.stderr||'').slice(0,300));
  const code = (r.stdout||'').trim();
  if(code !== '200') throw new Error('HTTP ' + code + ': ' + fs.readFileSync(respFile,'utf8').slice(0,300));
  const j = JSON.parse(fs.readFileSync(respFile,'utf8'));
  return j.choices?.[0]?.message || {};
}

const messages = [{role:'system', content: SYS}, {role:'user', content: task}];
let final = '';

for(let round = 0; round < ROUNDS; round++){
  if(Date.now() > DEADLINE){ console.error('[超时] 到达时间上限'); break; }
  const msg = await chat(messages);
  const calls = msg.tool_calls || [];
  if(!calls.length){
    final = msg.content || '';
    console.error(`[第 ${round+1} 轮] 模型无工具调用，结束`);
    break;
  }
  messages.push({role:'assistant', content: msg.content || '', tool_calls: calls});
  for(const c of calls){
    if(c.function.name === 'finish'){
      try { final = JSON.parse(c.function.arguments || '{}').summary || '完成'; } catch(e){ final = '完成'; }
      console.log(final);
      process.exit(0);
    }
    const out = runTool(c);
    console.error(`[工具] ${c.function.name} → ${clip(out, 120).replace(/\n/g,' ')}`);
    messages.push({role:'tool', tool_call_id: c.id, content: out});
  }
}
if(final) console.log(final);
else { console.error('[未完成] 达到轮数上限'); process.exit(1); }
