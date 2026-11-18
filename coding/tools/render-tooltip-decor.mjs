// Usage: node tools/render-tooltip-decor.mjs <PixelFX checkout>
import { writeFileSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { pathToFileURL } from 'node:url';

if (!process.argv[2]) throw new Error('Provide the PixelFX checkout path.');
const load = path => import(pathToFileURL(join(resolve(process.argv[2]), path)).href);
const { createFrame, painterFor, composite, hex } = await load('src/core/index.js');
const { encodePNG } = await load('tools/lib/png.mjs');
const width = 128, height = 64;
const frame = createFrame(width, height);
const p = painterFor(frame);
const [ink, plum, bronze, gold, ivory, white] =
    ['#241525', '#794b70', '#a2745d', '#e9b680', '#ffe3bd', '#ffffff'].map(hex);

// The nib points down-left, toward the tooltip's lower-right corner.
p.poly([[0,14],[2,10],[5,8],[7,4],[11,1],[15,0],[14,5],[12,7],[8,9],[6,11],[4,12],[2,15]],ink);
p.poly([[5,10],[7,5],[11,2],[14,1],[13,5],[10,8],[8,8]],bronze);
p.poly([[6,9],[7,6],[10,3],[14,1],[12,4],[10,5],[10,6]],ivory);
p.poly([[7,8],[10,5],[12,5],[10,7],[9,7]],plum);
p.line(2,13,12,3,gold); p.line(1,14,3,11,ivory); p.dot(2,14,white);

// Left and right forked ends, stored as one continuous eight-pixel-high ribbon.
const ribbonInk = hex('#574665');
const ribbonMid = hex('#bca2c0');
const ribbonRim = hex('#f4d8db');
p.poly([[16,0],[31,0],[29,3],[31,7],[16,7],[18,3]],ribbonInk);
p.poly([[17,1],[30,1],[28,3],[30,6],[17,6],[19,3]],ribbonMid);
p.line(18,1,29,1,ribbonRim); p.line(18,6,29,6,plum);
p.line(20,2,27,2,ivory,.65); p.line(20,5,27,5,ribbonInk,.65);

// A hollow rim with engraved ticks and angular insets. The center remains open.
p.place(64,32);
const turn = Math.PI * 2;
p.arc(0,0,30,1,0,turn,ivory);
p.arc(0,0,28,1,0,turn,gold);
p.arc(0,0,24,1,0,turn,bronze);
for (let i = 0; i < 12; i++) {
    const angle = i * turn / 12;
    const at = r => [Math.cos(angle) * r, Math.sin(angle) * r];
    p.line(...at(25), ...at(27), ivory);
    if (i % 3 === 0) p.poly([at(30), [Math.cos(angle+.1)*25,Math.sin(angle+.1)*25],
        at(22), [Math.cos(angle-.1)*25,Math.sin(angle-.1)*25]], gold);
}

p.place(112,16);
for (const sign of [-1, 1]) {
    const mirror = points => points.map(([x,y]) => [x*sign,y]);
    p.polyline(mirror([[0,-13],[10,-7],[10,0],[7,7],[0,13]]),ivory);
    p.polyline(mirror([[8,-6],[5,-2],[6,3],[0,9]]),gold);
}
p.polyline([[0,-8],[4,0],[0,8],[-4,0],[0,-8]], ivory);
p.place(0,0);
p.dot(0,16,ivory); p.dot(1,16,gold); p.dot(0,17,gold);

const rgba = composite(frame, new Uint8ClampedArray(width*height*4), null);
writeFileSync(new URL('../src/main/resources/assets/madoka_magic/textures/gui/tooltip_decor.png',import.meta.url),
    encodePNG(width,height,rgba));
