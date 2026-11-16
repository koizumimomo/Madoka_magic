// Usage: node tools/render-contract-weapons.mjs <pixelfx checkout> [preview PNG]
import { mkdirSync, writeFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const upstream = resolve(process.argv[2]);
const load = path => import(pathToFileURL(join(upstream, path)).href);
const { createFrame, painterFor, composite, hex } = await load('src/core/index.js');
const { encodePNG } = await load('tools/lib/png.mjs');
const { canvas, upscale } = await load('tools/lib/fxlib.mjs');
const asset = fileURLToPath(new URL('../src/main/resources/assets/madoka_magic/', import.meta.url));
const size = 32;

function paint(draw) {
    const frame = createFrame(size, size);
    draw(painterFor(frame));
    return composite(frame, new Uint8ClampedArray(size * size * 4), null);
}

function butterfly(p) {
    const [outline, steel, edge, bronze, gold, cream, ribbon] =
        ['#1b1721', '#38343e', '#756f7a', '#a86927', '#e9a936', '#ffe38a', '#746032'].map(hex);
    p.poly([[13, 17], [15, 11], [21, 6], [27, 4], [30, 1], [30, 5], [27, 10], [23, 14], [16, 20]], outline);
    p.poly([[15, 16], [17, 12], [22, 8], [28, 5], [26, 9], [22, 13], [16, 18]], steel);
    p.polyline([[17, 13], [22, 8], [28, 5], [30, 2]], edge);
    p.line(16, 16, 24, 10, gold);
    p.poly([[4, 25], [10, 19], [13, 21], [7, 28], [4, 28], [2, 26]], outline);
    p.line(5, 25, 11, 20, bronze);
    p.px(7, 24, gold);
    p.px(9, 22, gold);
    p.poly([[7, 18], [8, 13], [10, 13], [15, 17], [18, 17], [21, 18], [20, 21], [16, 23], [13, 22], [10, 20]], outline);
    p.poly([[8, 17], [9, 14], [13, 17], [14, 19], [19, 18], [20, 19], [17, 22], [14, 21], [11, 18]], gold);
    p.polyline([[9, 15], [11, 17], [13, 19], [16, 20], [19, 19]], cream);
    p.poly([[4, 27], [6, 28], [10, 27], [11, 29], [7, 31], [4, 30], [1, 31], [2, 28]], outline);
    p.polyline([[3, 29], [5, 29], [7, 30], [10, 28]], ribbon);
    p.px(14, 20, cream);
}

function bee(p) {
    const [outline, bronze, amber, gold, cream, throat] =
        ['#2a1922', '#824527', '#c46a2b', '#eea43b', '#fff0a0', '#5a2730'].map(hex);
    p.poly([[1, 27], [6, 22], [8, 23], [18, 13], [19, 10], [24, 6], [30, 1], [30, 6], [27, 12], [22, 16],
        [19, 16], [9, 26], [8, 28], [4, 31], [1, 30]], outline);
    p.polyline([[4, 27], [9, 24], [18, 15], [22, 13], [28, 6]], bronze);
    p.polyline([[4, 28], [9, 25], [18, 16], [21, 14]], amber);
    p.poly([[18, 12], [21, 9], [24, 8], [28, 4], [29, 3], [28, 8], [26, 12], [22, 15], [19, 15]], gold);
    p.poly([[21, 11], [25, 9], [26, 8], [25, 11], [22, 13], [20, 13]], throat);
    p.line(25, 8, 29, 3, cream);
    p.poly([[12, 16], [16, 15], [19, 17], [19, 20], [15, 23], [11, 21], [10, 18]], outline);
    p.poly([[12, 17], [16, 16], [18, 18], [17, 20], [14, 22], [12, 20], [11, 18]], amber);
    p.poly([[13, 17], [16, 17], [17, 19], [15, 21], [13, 20], [12, 18]], gold);
    p.polyline([[13, 18], [15, 17], [16, 18]], cream);
    p.polyline([[3, 29], [5, 29], [8, 26]], gold);
    p.px(9, 24, cream);
}

function thorn(p) {
    const [outline, ink, steel, silver, white, blue, light] =
        ['#141d35', '#273652', '#7f97b7', '#c0d1e4', '#eaf4ff', '#436a9d', '#75add4'].map(hex);
    p.poly([[11, 19], [16, 12], [17, 9], [23, 4], [29, 1], [30, 7], [28, 12], [24, 17], [20, 18], [16, 22]], outline);
    p.poly([[13, 18], [18, 12], [19, 9], [23, 6], [28, 3], [28, 8], [26, 12], [23, 16], [19, 17], [16, 20]], steel);
    p.poly([[17, 16], [21, 10], [28, 3], [27, 8], [24, 12], [20, 16], [16, 20], [15, 19]], silver);
    p.polyline([[19, 12], [23, 7], [28, 3]], white);
    p.polyline([[19, 15], [22, 12], [24, 9]], ink);
    p.poly([[3, 28], [9, 22], [12, 22], [13, 25], [8, 29], [5, 31], [2, 30]], outline);
    p.polyline([[4, 29], [7, 28], [11, 24]], steel);
    p.poly([[6, 18], [8, 15], [12, 18], [14, 20], [18, 20], [23, 18], [22, 22], [19, 24], [15, 25], [11, 23], [8, 23]], outline);
    p.polyline([[7, 18], [10, 18], [13, 22], [16, 23], [20, 21], [22, 19]], blue);
    p.polyline([[8, 17], [10, 20], [14, 21], [17, 23], [19, 22]], light);
    p.polyline([[10, 20], [8, 21], [7, 24], [5, 24]], blue);
    p.polyline([[13, 20], [14, 17], [16, 16]], blue);
    p.px(14, 21, white);
}

function cocoon(p, isBee) {
    const [dark, shell, shade, line, shine] = (isBee
        ? ['#3b2037', '#6f324b', '#a54b59', '#eb836d', '#ffd38b']
        : ['#211722', '#493126', '#825533', '#d6a34e', '#ffe6a0']).map(hex);
    p.poly([[15, 1], [18, 2], [20, 5], [24, 8], [26, 14], [26, 21], [23, 27], [18, 30],
        [12, 29], [8, 26], [6, 21], [6, 13], [9, 8], [13, 5]], dark);
    p.poly([[16, 3], [18, 5], [22, 8], [24, 14], [24, 21], [21, 26], [17, 28], [12, 27],
        [9, 24], [8, 20], [8, 14], [10, 10], [14, 6]], shell);
    p.poly([[15, 6], [19, 7], [22, 11], [22, 20], [20, 24], [17, 26], [13, 25], [10, 21],
        [10, 15], [12, 11]], shade);
    if (isBee) {
        for (const [x, y] of [[15, 10], [20, 14], [11, 15], [16, 19], [19, 23]]) {
            p.poly([[x, y - 2], [x + 2, y - 1], [x + 2, y + 1], [x, y + 2], [x - 2, y + 1], [x - 2, y - 1]], line);
            p.px(x, y, dark);
        }
    } else {
        p.polyline([[9, 14], [13, 12], [18, 10], [21, 8]], line);
        p.polyline([[8, 19], [12, 19], [18, 16], [23, 13]], line);
        p.polyline([[10, 24], [14, 23], [19, 21], [24, 18]], line);
        p.polyline([[13, 27], [17, 25], [21, 24]], line);
    }
    p.polyline([[14, 7], [15, 9], [14, 12]], shine);
    p.px(17, 16, shine);
    p.px(20, 19, line);
}

const weapons = [
    ['butterfly_blade', 'FOLDED PAGE', butterfly],
    ['queen_stinger', 'ROYAL STINGER', bee],
    ['thorn_greatsword', 'BEARER OF GUILT', thorn],
];
const preview = canvas(620, 132, [37, 32, 43, 255]);
function previewSprite(pixels, index, label) {
    const left = 12 + index * 122;
    preview.rect(left, 9, 108, 108, [47, 40, 55, 255]);
    preview.paste(upscale(pixels, size, size, 3), size * 3, size * 3, left + 6, 15);
    preview.text(label, left + 3, 122, 1);
}

for (const [index, [name, label, draw]] of weapons.entries()) {
    const pixels = paint(draw);
    const texture = join(asset, 'textures/item', name + '.png');
    const model = join(asset, 'models/item', name + '.json');
    writeFileSync(texture, encodePNG(size, size, pixels));
    try {
        writeFileSync(model, JSON.stringify({
            parent: 'minecraft:item/handheld',
            textures: { layer0: 'madoka_magic:item/' + name },
        }, null, 2) + '\n', { flag: 'wx' });
    } catch (error) {
        if (error.code !== 'EEXIST') throw error;
    }
    previewSprite(pixels, index, label);
}

const atlas = canvas(64, 32, [0, 0, 0, 0]);
for (const index of [0, 1]) {
    const pixels = paint(p => cocoon(p, index === 1));
    atlas.paste(pixels, size, size, index * size, 0);
    previewSprite(pixels, index + 3, index === 0 ? 'BUTTERFLY COCOON' : 'ROYAL COMB');
}
writeFileSync(join(asset, 'textures/particle/arena_cocoons.png'), atlas.png());
writeFileSync(join(asset, 'textures/particle/arena_mark.png'), encodePNG(1, 1, new Uint8Array([255, 255, 255, 255])));
if (process.argv[3]) {
    const file = resolve(process.argv[3]);
    mkdirSync(dirname(file), { recursive: true });
    writeFileSync(file, preview.png());
}
