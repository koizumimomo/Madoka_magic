// Usage: node tools/render-ritual-review.mjs <pixelfx checkout>
import { mkdirSync, writeFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const upstream = resolve(process.argv[2]);
const load = path => import(pathToFileURL(join(upstream, path)).href);
const { createFrame, painterFor, composite, hex } = await load('src/core/index.js');
const { encodePNG } = await load('tools/lib/png.mjs');
const textures = fileURLToPath(new URL('../src/main/resources/assets/madoka_magic/textures/', import.meta.url));

function draw(path, width, height, paint) {
    const frame = createFrame(width, height);
    paint(painterFor(frame));
    const rgba = composite(frame, new Uint8Array(width * height * 4), null);
    const output = join(textures, path + '.png');
    mkdirSync(dirname(output), { recursive: true });
    writeFileSync(output, encodePNG(width, height, rgba));
}

const parchment = ['#382525', '#84503b', '#be865b', '#dfb681', '#f5dda8', '#ffe9b4'].map(hex);
const [ink, edge, fold, paperShadow, paper, highlight] = parchment;

draw('gui/altar_parchment', 166, 104, p => {
    p.poly([[5, 5], [18, 3], [147, 3], [159, 5], [164, 12], [162, 96], [156, 101],
        [12, 101], [3, 96], [2, 13]], ink);
    p.poly([[7, 7], [18, 5], [146, 5], [157, 7], [161, 13], [159, 94], [155, 98],
        [12, 98], [6, 94], [5, 13]], fold);
    p.poly([[12, 11], [154, 11], [153, 93], [147, 96], [17, 96], [12, 93]], paper);
    p.rect(15, 15, 136, 76, highlight, 0.42);
    p.rect(15, 88, 136, 5, paperShadow, 0.55);
    for (const x of [10, 153]) {
        p.rect(x, 12, 3, 80, paperShadow);
        p.line(x, 12, x, 92, fold);
    }
    for (const y of [8, 95]) {
        p.line(14, y, 151, y, edge);
        p.line(18, y + 1, 148, y + 1, highlight);
        p.poly([[6, y], [9, y - 2], [14, y], [14, y + 4], [9, y + 6], [6, y + 3]], fold);
        p.poly([[152, y], [157, y - 2], [160, y], [160, y + 3], [156, y + 5], [152, y + 3]], fold);
    }
    for (let i = 0; i < 85; i++) {
        p.px(15 + (i * 71) % 134, 13 + (i * 47) % 80, i % 3 === 0 ? paperShadow : highlight);
    }
    const cx = 54;
    const cy = 49;
    const ring = hex('#c49366');
    const sigil = hex('#d5ae7e');
    for (const radius of [25, 29, 34]) {
        const vertices = Array.from({length: 65}, (_, i) => {
            const theta = i * Math.PI / 32;
            return [cx + Math.cos(theta) * radius, cy + Math.sin(theta) * radius];
        });
        p.polyline(vertices, radius === 29 ? ring : sigil);
    }
    const star = Array.from({length: 6}, (_, i) => {
        const a = -Math.PI / 2 + i * Math.PI * 4 / 5;
        return [cx + Math.cos(a) * 24, cy + Math.sin(a) * 24];
    });
    p.polyline(star, sigil);
    for (let i = 0; i < 8; i++) {
        const a = i * Math.PI / 4;
        const x = cx + Math.cos(a) * 37;
        const y = cy + Math.sin(a) * 37;
        p.poly([[x, y - 2], [x + 2, y], [x, y + 2], [x - 2, y]], ring);
    }
    p.poly([[91, 47], [109, 47], [109, 43], [117, 49], [109, 55], [109, 51], [91, 51]], edge);
    p.line(93, 47, 110, 47, highlight);
    p.line(15, 87, 150, 87, ring);
});

draw('gui/altar_parchment_slot', 20, 20, p => {
    p.poly([[3, 0], [16, 0], [19, 3], [19, 16], [16, 19], [3, 19], [0, 16], [0, 3]], edge);
    p.poly([[3, 1], [16, 1], [18, 3], [18, 16], [16, 18], [3, 18], [1, 16], [1, 3]], fold);
    p.rect(2, 2, 16, 16, paperShadow);
    p.line(3, 17, 17, 17, highlight);
    p.line(17, 3, 17, 16, paper);
});

const dark = hex('#23151c');
const gold = hex('#e7a741');
const light = hex('#ffe69b');
const white = hex('#fff9cf');

draw('particle/butterfly_orbit', 64, 32, p => {
    for (const frame of [0, 1]) {
        const center = frame * 32 + 16;
        const scale = frame === 0 ? 1 : 0.66;
        for (const side of [-1, 1]) {
            const wing = points => points.map(([x, y]) => [center + x * side * scale, y]);
            p.poly(wing([[0, 13], [4, 6], [12, 3], [15, 3], [14, 11], [10, 16], [5, 18],
                [11, 23], [10, 27], [5, 25], [1, 21]]), dark);
            p.poly(wing([[2, 14], [6, 7], [13, 4], [12, 10], [9, 14], [4, 17]]), gold);
            p.poly(wing([[3, 18], [8, 22], [9, 25], [5, 24], [2, 21]]), gold);
            p.polyline(wing([[3, 14], [7, 10], [12, 5]]), white);
            p.polyline(wing([[3, 19], [6, 21], [8, 24]]), light);
            p.line(center + 6 * side * scale, 11, center + 10 * side * scale, 12, light);
            p.dot(center + 9 * side * scale, 29, gold, 0.86);
        }
        p.line(center, 11, center, 23, dark);
        p.line(center, 14, center, 21, white);
        p.px(center, 10, light);
    }
});

draw('particle/take/honey_mote', 32, 32, p => {
    p.poly([[16, 3], [27, 9], [28, 20], [18, 28], [7, 24], [3, 14], [6, 8]], dark);
    p.poly([[16, 4], [26, 10], [26, 20], [18, 26], [8, 23], [5, 14], [7, 9]], gold);
    p.poly([[16, 7], [22, 12], [21, 18], [16, 22], [10, 19], [9, 13]], hex('#a95620'));
    p.polyline([[7, 14], [9, 10], [16, 6], [21, 8]], white);
    p.line(10, 21, 17, 24, light);
    p.px(24, 16, white);
});

draw('particle/take/honey_illusion', 32, 32, p => {
    p.poly([[2, 15], [8, 8], [16, 6], [24, 9], [30, 16], [23, 23], [16, 25], [8, 22]], dark);
    p.poly([[4, 15], [9, 9], [16, 8], [23, 10], [28, 16], [22, 22], [16, 23], [10, 21]], hex('#de855d'));
    p.poly([[6, 15], [12, 11], [20, 11], [26, 16], [20, 20], [12, 20]], hex('#692e45'));
    p.poly([[16, 8], [19, 15], [16, 23], [13, 15]], light);
    p.px(17, 13, white);
    p.px(25, 7, light);
});

draw('particle/take/dandelion_seed', 32, 32, p => {
    p.beam(11, 26, 16, 12, 3, hex('#705267'));
    p.line(11, 25, 16, 12, gold);
    for (const [x, y] of [[7, 10], [11, 5], [17, 3], [23, 6], [27, 11]]) {
        p.beam(16, 15, x, y, 2, hex('#a990ad'));
        p.line(16, 14, x, y, white);
        p.px(x, y - 1, light);
    }
    p.poly([[16, 10], [19, 14], [16, 17], [13, 14]], white);
});
