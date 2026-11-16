// Usage: node tools/render-soul-gems.mjs <pixelfx checkout> [preview PNG]
import { mkdirSync, writeFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const upstream = resolve(process.argv[2]);
const load = path => import(pathToFileURL(join(upstream, path)).href);
const { createFrame, painterFor, composite, hex } = await load('src/core/index.js');
const { encodePNG } = await load('tools/lib/png.mjs');
const { upscale } = await load('tools/lib/fxlib.mjs');
const textures = fileURLToPath(new URL('../src/main/resources/assets/madoka_magic/textures/', import.meta.url));

const palette = {
    neutral: ['#292538', '#5b586c', '#9c99ab', '#dedbea', '#f8f6fc'],
    sacrifice: ['#4d281a', '#935020', '#df9f27', '#ffda60', '#fff5c7'],
    take: ['#4c222b', '#92382a', '#d66823', '#ffa34a', '#ffe9ac'],
    atonement: ['#15203a', '#263f6b', '#466cb0', '#81bbef', '#e1faff'],
};
const core = [[15, 7], [18, 8], [21, 12], [23, 17], [23, 21], [21, 24],
    [17, 26], [13, 26], [9, 23], [8, 20], [9, 15], [11, 10]];
const mount = [[15, 5], [20, 7], [23, 11], [25, 16], [26, 21], [24, 26],
    [20, 28], [11, 28], [7, 25], [5, 21], [6, 16], [8, 11], [11, 7]];
const glassTop = 7;
const glassBottom = 24;

function drawGem(frame, theme) {
    const p = painterFor(frame);
    const [dark, shade, mid, bright, glint] = palette[theme].map(hex);
    const metal = (theme === 'neutral'
        ? ['#262332', '#555167', '#938eaa', '#e3d9f2']
        : ['#342122', '#81482d', '#c98c49', '#ffe1a0']).map(hex);
    const [rim, bronze, gold, shine] = metal;

    p.poly([[14, 6], [13, 3], [15, 1], [17, 1], [19, 4], [17, 6]], rim);
    p.poly([[14, 3], [15, 2], [17, 2], [18, 4], [16, 5]], gold);
    p.px(16, 3, bright);
    p.poly(mount, rim);
    p.poly([[15, 6], [20, 8], [24, 15], [25, 21], [23, 25], [19, 27], [12, 27],
        [8, 24], [6, 21], [7, 16], [9, 11], [12, 8]], bronze);
    p.polyline([[7, 19], [8, 14], [11, 9], [15, 6], [18, 7], [21, 9]], gold);
    p.line(12, 8, 15, 7, shine);
    p.poly(core, dark);
    p.poly([[15, 8], [18, 9], [21, 13], [22, 17], [22, 21], [19, 24], [16, 25],
        [12, 24], [9, 21], [10, 16], [12, 11]], shade);
    p.poly([[14, 9], [17, 9], [20, 13], [21, 17], [20, 21], [17, 23], [13, 23],
        [10, 20], [10, 17], [12, 12]], mid);
    p.poly([[14, 10], [17, 10], [19, 13], [19, 17], [17, 20], [13, 21], [11, 18], [12, 14]], bright);
    p.poly([[14, 10], [17, 10], [16, 12], [14, 13], [13, 16], [12, 16], [12, 14]], glint);
    p.line(13, 12, 12, 15, glint);
    p.px(13, 18, glint);
    p.line(18, 20, 20, 18, mid);

    p.poly([[6, 20], [10, 22], [14, 24], [18, 24], [22, 22], [25, 20],
        [24, 25], [21, 28], [19, 28], [21, 30], [20, 31], [11, 31],
        [10, 30], [12, 28], [9, 27], [7, 25]], rim);
    p.polyline([[7, 21], [10, 23], [14, 25], [18, 25], [22, 23], [24, 21]], gold);
    p.line(9, 23, 12, 24, shine);
    p.line(11, 30, 20, 30, gold);
    p.line(13, 29, 18, 29, bronze);
    p.poly([[15, 24], [18, 24], [19, 26], [17, 28], [15, 28], [13, 26]], bronze);
    p.px(16, 25, glint);
    p.px(16, 26, bright);
    if (theme === 'sacrifice') {
        p.px(14, 25, bright);
        p.px(18, 25, bright);
    } else if (theme === 'take') {
        p.px(15, 26, bright);
        p.px(17, 26, bright);
    }
}

function drawTaint(frame, theme) {
    const p = painterFor(frame);
    p.poly([[15, 40], [18, 42], [19, 46], [17, 48], [16, 54], [11, 54], [10, 51],
        [12, 46], [11, 45], [13, 43]], hex('#1b1721'));
    p.poly([[18, 52], [20, 50], [21, 53], [19, 55], [15, 56]], hex('#211925'));
    p.px(13, 45, hex('#2e2434'));
    p.px(19, 48, hex('#271f2e'));
    p.px(15, 53, hex('#2a212f'));
    const glassColors = new Set(palette[theme].map(color => hex(color) >>> 0));
    const ink = hex('#09070d');
    for (let y = 0; y < 32; y++) {
        for (let x = 0; x < 32; x++) {
            const source = y * 32 + x;
            const overlay = source + 32 * 32;
            const glass = y >= glassTop && y < glassBottom && glassColors.has(frame.color[source]);
            frame.color[overlay] = glass ? frame.color[overlay] || ink : 0;
        }
    }
}

function save(frame, target) {
    const bytes = new Uint8Array(frame.w * frame.h * 4);
    composite(frame, bytes, null);
    mkdirSync(dirname(target), { recursive: true });
    writeFileSync(target, encodePNG(frame.w, frame.h, bytes));
    return bytes;
}

const tiles = [];
for (const theme of Object.keys(palette)) {
    const frame = createFrame(32, 64);
    drawGem(frame, theme);
    drawTaint(frame, theme);
    tiles.push(save(frame, join(textures, 'gui', 'soul_gem', theme + '.png')));
}

function tool(name, draw) {
    const frame = createFrame(16, 16);
    const p = painterFor(frame);
    draw(p);
    save(frame, join(textures, 'item', name + '.png'));
}

tool('test_release_contract', p => {
    p.poly([[3, 2], [13, 2], [13, 10], [11, 14], [3, 14], [1, 12], [2, 9], [2, 4]], hex('#332031'));
    p.poly([[4, 3], [12, 3], [12, 10], [10, 13], [3, 13], [2, 12], [3, 10], [3, 4]], hex('#c78c6d'));
    p.rect(4, 4, 7, 6, hex('#eed3a6'));
    p.line(4, 6, 10, 6, hex('#ae835f'));
    p.line(4, 8, 9, 8, hex('#ae835f'));
    p.beam(8, 7, 14, 13, 3, hex('#4b213a'));
    p.beam(14, 7, 8, 13, 3, hex('#4b213a'));
    p.line(8, 7, 14, 13, hex('#fc7a85'));
    p.line(14, 7, 8, 13, hex('#fc7a85'));
});

tool('test_max_purity', p => {
    p.poly([[8, 1], [12, 4], [14, 8], [12, 12], [8, 15], [4, 12], [2, 8], [4, 4]], hex('#233051'));
    p.poly([[8, 2], [11, 5], [13, 8], [11, 11], [8, 14], [5, 11], [3, 8], [5, 5]], hex('#729ac6'));
    p.poly([[8, 3], [10, 6], [11, 9], [8, 12], [5, 9], [6, 6]], hex('#a6e9ed'));
    p.poly([[8, 4], [9, 7], [12, 8], [9, 9], [8, 12], [7, 9], [4, 8], [7, 7]], hex('#fff3cb'));
    p.px(3, 3, hex('#ddf6fa'));
    p.px(13, 12, hex('#ddf6fa'));
});

if (process.argv[3]) {
    const width = 32 * tiles.length;
    const height = 32;
    const sheet = new Uint8Array(width * height * 4);
    for (let y = 0; y < height; y++) {
        for (let index = 0; index < tiles.length; index++) {
            sheet.set(tiles[index].subarray(y * 128, (y + 1) * 128), (y * width + index * 32) * 4);
        }
    }
    writeFileSync(resolve(process.argv[3]), encodePNG(width * 4, height * 4, upscale(sheet, width, height, 4)));
}
