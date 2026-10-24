#!/usr/bin/env node
// 9×9 RBMK 占位贴图生成器：把 5×5 底图放大到 288×288，并画出 3×3 分区网格。
const fs = require('fs');
const path = require('path');
const zlib = require('zlib');
const root = path.resolve(__dirname, '..');
const srcPath = path.join(root, 'assets/sprites/rbmk-reactor-9.png');
const outPath = srcPath;
const uiPath = path.join(root, 'assets/sprites/rbmk-reactor-9-ui.png');

function readPng(file){
    const b = fs.readFileSync(file);
    let off = 8, w = 0, h = 0;
    const idat = [];
    while(off < b.length){
        const len = b.readUInt32BE(off);
        const type = b.toString('ascii', off + 4, off + 8);
        const data = b.subarray(off + 8, off + 8 + len);
        if(type === 'IHDR'){ w = data.readUInt32BE(0); h = data.readUInt32BE(4); }
        else if(type === 'IDAT') idat.push(data);
        else if(type === 'IEND') break;
        off += 12 + len;
    }
    const raw = zlib.inflateSync(Buffer.concat(idat));
    const bpp = 4, stride = w * bpp;
    const out = Buffer.alloc(w * h * bpp);
    let prev = Buffer.alloc(stride);
    for(let y = 0; y < h; y++){
        const filter = raw[y * (stride + 1)];
        let line = Buffer.from(raw.subarray(y * (stride + 1) + 1, (y + 1) * (stride + 1)));
        for(let x = 0; x < stride; x++){
            const a = x >= bpp ? line[x - bpp] : 0;
            const b0 = prev[x];
            const c = x >= bpp ? prev[x - bpp] : 0;
            let v = line[x];
            if(filter === 1) v = (v + a) & 255;
            else if(filter === 2) v = (v + b0) & 255;
            else if(filter === 3) v = (v + ((a + b0) >> 1)) & 255;
            else if(filter === 4){
                const p = a + b0 - c;
                const pa = Math.abs(p - a), pb = Math.abs(p - b0), pc = Math.abs(p - c);
                v = (v + (pa <= pb && pa <= pc ? a : pb <= pc ? b0 : c)) & 255;
            }
            line[x] = v;
        }
        line.copy(out, y * stride);
        prev = line;
    }
    return {w, h, data: out};
}

function resize(src, dw, dh){
    const out = Buffer.alloc(dw * dh * 4);
    for(let y = 0; y < dh; y++){
        const sy = Math.min(src.h - 1, Math.floor(y * src.h / dh));
        for(let x = 0; x < dw; x++){
            const sx = Math.min(src.w - 1, Math.floor(x * src.w / dw));
            const si = (sy * src.w + sx) * 4, di = (y * dw + x) * 4;
            out[di] = src.data[si]; out[di+1] = src.data[si+1];
            out[di+2] = src.data[si+2]; out[di+3] = src.data[si+3];
        }
    }
    return {w: dw, h: dh, data: out};
}

function blend(img, x, y, r, g, b, a){
    if(x < 0 || y < 0 || x >= img.w || y >= img.h || a <= 0) return;
    const i = (y * img.w + x) * 4;
    const sa = img.data[i+3] / 255;
    const na = a + sa * (1 - a);
    if(na <= 0) return;
    img.data[i]   = Math.round((r * a + img.data[i]   * sa * (1 - a)) / na);
    img.data[i+1] = Math.round((g * a + img.data[i+1] * sa * (1 - a)) / na);
    img.data[i+2] = Math.round((b * a + img.data[i+2] * sa * (1 - a)) / na);
    img.data[i+3] = Math.round(na * 255);
}

function fill(img, x, y, w, h, r, g, b, a){
    for(let yy = y; yy < y + h; yy++){
        for(let xx = x; xx < x + w; xx++) blend(img, xx, yy, r, g, b, a);
    }
}

function png(img){
    const stride = img.w * 4;
    const raw = Buffer.alloc((stride + 1) * img.h);
    for(let y = 0; y < img.h; y++){
        raw[y * (stride + 1)] = 0;
        img.data.copy(raw, y * (stride + 1) + 1, y * stride, (y + 1) * stride);
    }
    const table = new Uint32Array(256);
    for(let n = 0; n < 256; n++){
        let c = n;
        for(let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
        table[n] = c >>> 0;
    }
    const crc = buf => {
        let c = 0xffffffff;
        for(const v of buf) c = table[(c ^ v) & 255] ^ (c >>> 8);
        return (c ^ 0xffffffff) >>> 0;
    };
    const chunk = (type, data) => {
        const len = Buffer.alloc(4); len.writeUInt32BE(data.length);
        const td = Buffer.concat([Buffer.from(type, 'ascii'), data]);
        const ck = Buffer.alloc(4); ck.writeUInt32BE(crc(td));
        return Buffer.concat([len, td, ck]);
    };
    const ihdr = Buffer.alloc(13);
    ihdr.writeUInt32BE(img.w, 0); ihdr.writeUInt32BE(img.h, 4);
    ihdr[8] = 8; ihdr[9] = 6;
    return Buffer.concat([
        Buffer.from([137,80,78,71,13,10,26,10]),
        chunk('IHDR', ihdr),
        chunk('IDAT', zlib.deflateSync(raw, {level: 9})),
        chunk('IEND', Buffer.alloc(0))
    ]);
}

const src = readPng(srcPath);
const img = resize(src, 288, 288);
const cell = 96;
for(let z = 0; z < 9; z++){
    const cx = z % 3, cy = Math.floor(z / 3);
    fill(img, cx * cell + 3, cy * cell + 3, cell - 6, cell - 6, 255, 120, 60, 0.055);
}
for(let k = 1; k < 3; k++){
    fill(img, k * cell - 3, 0, 6, 288, 8, 10, 14, 0.72);
    fill(img, 0, k * cell - 3, 288, 6, 8, 10, 14, 0.72);
    fill(img, k * cell - 2, 0, 2, 288, 120, 140, 160, 0.18);
    fill(img, 0, k * cell - 2, 288, 2, 120, 140, 160, 0.18);
}
for(let z = 0; z < 9; z++){
    const cx = z % 3, cy = Math.floor(z / 3);
    const x = cx * cell + 44, y = cy * cell + 44;
    fill(img, x, y, 9, 9, 10, 12, 16, 0.85);
    fill(img, x + 2, y + 2, 5, 5, 255, 196, 84, 0.95);
}
fs.writeFileSync(outPath, png(img));
fs.writeFileSync(uiPath, png(resize(img, 256, 256)));
console.log('wrote', outPath, '288x288;', uiPath, '256x256');
