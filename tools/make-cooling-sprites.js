#!/usr/bin/env node
// 冷却塔占位贴图：2×2 小型 64×64，4×4 大型 128×128。
const fs = require('fs');
const path = require('path');
const zlib = require('zlib');
const root = path.resolve(__dirname, '..');
const src = fs.readFileSync(path.join(root, 'assets/sprites/cooling-tower.png'));
const outSmall = path.join(root, 'assets/sprites/cooling-tower.png');
const outLarge = path.join(root, 'assets/sprites/cooling-tower-large.png');

function readPng(b){
    let off = 8, w = 0, h = 0;
    const idat = [];
    while(off < b.length){
        const n = b.readUInt32BE(off);
        const t = b.toString('ascii', off + 4, off + 8);
        const d = b.subarray(off + 8, off + 8 + n);
        if(t === 'IHDR'){ w = d.readUInt32BE(0); h = d.readUInt32BE(4); }
        else if(t === 'IDAT') idat.push(d);
        else if(t === 'IEND') break;
        off += 12 + n;
    }
    const raw = zlib.inflateSync(Buffer.concat(idat));
    const stride = w * 4, px = Buffer.alloc(stride * h);
    let prev = Buffer.alloc(stride);
    for(let y = 0; y < h; y++){
        const f = raw[y * (stride + 1)];
        const line = Buffer.from(raw.subarray(y * (stride + 1) + 1, (y + 1) * (stride + 1)));
        for(let x = 0; x < stride; x++){
            const a = x >= 4 ? line[x - 4] : 0, u = prev[x], c = x >= 4 ? prev[x - 4] : 0;
            let v = line[x];
            if(f === 1) v = (v + a) & 255;
            else if(f === 2) v = (v + u) & 255;
            else if(f === 3) v = (v + ((a + u) >> 1)) & 255;
            else if(f === 4){
                const p = a + u - c, pa = Math.abs(p - a), pb = Math.abs(p - u), pc = Math.abs(p - c);
                v = (v + (pa <= pb && pa <= pc ? a : pb <= pc ? u : c)) & 255;
            }
            line[x] = v;
        }
        line.copy(px, y * stride);
        prev = line;
    }
    return {w, h, px};
}

function scale(s, n){
    const d = Buffer.alloc(n * n * 4);
    for(let y = 0; y < n; y++){
        const sy = Math.min(s.h - 1, Math.floor(y * s.h / n));
        for(let x = 0; x < n; x++){
            const sx = Math.min(s.w - 1, Math.floor(x * s.w / n));
            const si = (sy * s.w + sx) * 4, di = (y * n + x) * 4;
            d[di] = s.px[si]; d[di+1] = s.px[si+1]; d[di+2] = s.px[si+2]; d[di+3] = s.px[si+3];
        }
    }
    return {w: n, h: n, px: d};
}

function png(img){
    const stride = img.w * 4;
    const raw = Buffer.alloc((stride + 1) * img.h);
    for(let y = 0; y < img.h; y++){
        raw[y * (stride + 1)] = 0;
        img.px.copy(raw, y * (stride + 1) + 1, y * stride, (y + 1) * stride);
    }
    const table = new Uint32Array(256);
    for(let n = 0; n < 256; n++){
        let c = n;
        for(let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
        table[n] = c >>> 0;
    }
    const crc = b => {
        let c = 0xffffffff;
        for(const v of b) c = table[(c ^ v) & 255] ^ (c >>> 8);
        return (c ^ 0xffffffff) >>> 0;
    };
    const chunk = (t, d) => {
        const l = Buffer.alloc(4); l.writeUInt32BE(d.length);
        const td = Buffer.concat([Buffer.from(t, 'ascii'), d]);
        const c = Buffer.alloc(4); c.writeUInt32BE(crc(td));
        return Buffer.concat([l, td, c]);
    };
    const ihdr = Buffer.alloc(13);
    ihdr.writeUInt32BE(img.w, 0); ihdr.writeUInt32BE(img.h, 4); ihdr[8] = 8; ihdr[9] = 6;
    return Buffer.concat([Buffer.from([137,80,78,71,13,10,26,10]), chunk('IHDR', ihdr),
        chunk('IDAT', zlib.deflateSync(raw, {level: 9})), chunk('IEND', Buffer.alloc(0))]);
}

const base = readPng(src);
fs.writeFileSync(outSmall, png(scale(base, 64)));
fs.writeFileSync(outLarge, png(scale(base, 128)));
console.log('wrote', outSmall, '64x64;', outLarge, '128x128');
