#!/usr/bin/env node
/*
 * 生成 RBMK 警告音（WAV）。输出默认 /tmp/ironworks-reactor-alert.wav。
 * 转 OGG：
 *   node tools/gen-reactor-alert.js
 *   oggenc -q 4 -o assets/sounds/ironworks-reactor-alert.ogg /tmp/ironworks-reactor-alert.wav
 *
 * 声音设计：两声高/低交替的短促工业警报，再重复一次；带二次谐波和
 * 轻微低频尾音，避免纯正弦太单薄。全部由本脚本合成，无外部采样。
 */
'use strict';

const fs = require('fs');
const out = process.argv[2] || '/tmp/ironworks-reactor-alert.wav';
const rate = 44100;
const duration = 1.28;
const count = Math.ceil(rate * duration);
const data = new Float32Array(count);

const pulses = [
  { start: 0.00, len: 0.19, freq: 760, gain: 1.00 },
  { start: 0.25, len: 0.19, freq: 540, gain: 0.95 },
  { start: 0.50, len: 0.19, freq: 760, gain: 1.00 },
  { start: 0.75, len: 0.19, freq: 540, gain: 0.95 },
  { start: 0.97, len: 0.24, freq: 380, gain: 0.55 }
];

for(const p of pulses){
  const start = Math.floor(p.start * rate);
  const n = Math.floor(p.len * rate);
  for(let i = 0; i < n; i++){
    const t = i / rate;
    const env = Math.min(1, t / 0.008) * Math.min(1, (p.len - t) / 0.05);
    const w = 2 * Math.PI * p.freq * t;
    // 基频 + 二次/三次谐波，做成有厚度的工业蜂鸣
    const v = Math.sin(w) + 0.32 * Math.sin(2 * w) + 0.13 * Math.sin(3 * w);
    data[start + i] += v * env * p.gain;
  }
}

// 轻微的低频底噪尾音，让最后一声不突兀
for(let i = 0; i < count; i++){
  const t = i / rate;
  const tail = Math.max(0, Math.min(1, (t - 1.00) / 0.28));
  data[i] += Math.sin(2 * Math.PI * 95 * t) * 0.05 * tail;
}

// 去直流 + 归一化 + 全曲淡出
let mean = 0;
for(const v of data) mean += v;
mean /= count;
let peak = 0;
for(let i = 0; i < count; i++){
  data[i] -= mean;
  const fade = Math.min(1, (count - i) / (rate * 0.04));
  data[i] *= fade;
  peak = Math.max(peak, Math.abs(data[i]));
}
const scale = peak > 0 ? 0.82 / peak : 1;

const buf = Buffer.alloc(44 + count * 2);
buf.write('RIFF', 0);
buf.writeUInt32LE(36 + count * 2, 4);
buf.write('WAVE', 8);
buf.write('fmt ', 12);
buf.writeUInt32LE(16, 16);
buf.writeUInt16LE(1, 20);          // PCM
buf.writeUInt16LE(1, 22);          // mono
buf.writeUInt32LE(rate, 24);
buf.writeUInt32LE(rate * 2, 28);
buf.writeUInt16LE(2, 32);
buf.writeUInt16LE(16, 34);
buf.write('data', 36);
buf.writeUInt32LE(count * 2, 40);

for(let i = 0; i < count; i++){
  const s = Math.max(-1, Math.min(1, data[i] * scale));
  buf.writeInt16LE(Math.round(s * 32767), 44 + i * 2);
}

fs.writeFileSync(out, buf);
console.log(`${out}  ${buf.length} bytes  ${duration.toFixed(2)}s  peak=${peak.toFixed(3)}`);
