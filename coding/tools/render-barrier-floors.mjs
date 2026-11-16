// Draw the original barrier floor tiles with the local PixelFX checkout.
// Usage: node tools/render-barrier-floors.mjs <pixelfx checkout> [contact sheet PNG]
import { mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";

if (!process.argv[2]) throw new Error("Provide the path to a PixelFX checkout.");
const checkout = resolve(process.argv[2]);
const load = (path) => import(pathToFileURL(join(checkout, path)).href);
const { createFrame, composite, painterFor, hex, Rng, TAU } = await load("src/core/index.js");
const { encodePNG } = await load("tools/lib/png.mjs");
const { canvas } = await load("tools/lib/fxlib.mjs");
const resources = resolve(dirname(fileURLToPath(import.meta.url)), "../src/main/resources");
const assetRoot = join(resources, "assets", "madoka_magic");
const size = 64;
const variants = 4;

const themes = {
    sacrifice: { label: "BUTTERFLY / TORN DIARY", motif: "wing", palette: ["#2c231d", "#544634", "#725d3f", "#a38651", "#c0a572", "#dbca9b"] },
    take: { label: "BEE QUEEN / WAX PRINT", motif: "hex", palette: ["#34251f", "#5e4233", "#79543a", "#9d6d42", "#c09565", "#dbbb87"] },
    atonement: { label: "SORROW / INK AND TEARS", motif: "tear", palette: ["#242b40", "#41465e", "#555a75", "#707b96", "#93a4b7", "#b7c9cd"] },
    wither: { label: "WITHER / RUBBINGS", motif: "ribs", palette: ["#282431", "#48434d", "#625861", "#82717b", "#a08a91", "#c2acac"] },
    gluttony: { label: "GLUTTONY / GEL PRINT", motif: "jelly", palette: ["#26352d", "#405846", "#547157", "#73916a", "#97aa7e", "#bbc39a"] },
    kriemhild_gretchen: { label: "SALVATION / PRESSED PETALS", motif: "flower", palette: ["#3c3039", "#64515f", "#856473", "#a27d89", "#c8a1a7", "#dec0b6"] },
    homulilly: { label: "NUTCRACKER / CLOCK RUBBINGS", motif: "gear", palette: ["#292536", "#474052", "#605367", "#7a6b86", "#9e8da7", "#bcaac0"] },
    candeloro: { label: "HOSPITALITY / PAPER LACE", motif: "lace", palette: ["#3d2f28", "#68513e", "#856c4f", "#a68c62", "#c6ac84", "#e4cea1"] },
    oktavia: { label: "MERMAID / BROKEN SCORE", motif: "score", palette: ["#24313c", "#3d5266", "#4f6c83", "#668b9b", "#94aeb5", "#b7c9cb"] },
    ophelia: { label: "OPHELIA / STAGE SCRAPS", motif: "flame", palette: ["#39272c", "#654049", "#82505a", "#a16564", "#bd8982", "#d9aca0"] },
};

function inside(points, x, y) {
    let hit = false;
    for (let i = 0, previous = points.length - 1; i < points.length; previous = i++) {
        const [ax, ay] = points[i];
        const [bx, by] = points[previous];
        if ((ay > y) !== (by > y) && x < (bx - ax) * (y - ay) / (by - ay) + ax) hit = !hit;
    }
    return hit;
}

function panel(p, rng, points, fill, palette, weave) {
    const [ink, , shadow, , paper, edge] = palette;
    p.poly(points.map(([x, y]) => [x + 1.5, y + 2]), ink, .78);
    p.poly(points, fill);
    for (let y = 0; y < size; y++) {
        for (let x = 0; x < size; x++) {
            if (!inside(points, x + .5, y + .5)) continue;
            if (weave ? (x + 2 * y) % 6 === 0 : (y + Math.floor(x * .27)) % 5 === 0) {
                p.dot(x, y, shadow, .26);
            }
            if (rng.next() < .08) p.dot(x, y, rng.next() < .35 ? edge : paper, .38);
        }
    }
    p.polyline(points.slice(0, 6), edge, .70);
    p.polyline(points.slice(6), shadow, .72);
}

function stamp(p, motif, palette, x, y, angle, scale) {
    const [ink, , shadow, print, paper, edge] = palette;
    const c = Math.cos(angle), s = Math.sin(angle);
    const point = ([u, v]) => [x + (u * c - v * s) * scale, y + (u * s + v * c) * scale];
    const poly = (points, color = ink, alpha = 1) => p.poly(points.map(point), color, alpha);
    const line = (points, color = edge, alpha = 1) => p.polyline(points.map(point), color, alpha);
    const ring = (count, radius, offset = 0, cy = 0) => Array.from({ length: count + 1 }, (_, i) => {
        const a = i * TAU / count + offset;
        return [Math.cos(a) * radius, cy + Math.sin(a) * radius];
    });
    switch (motif) {
        case "wing":
            for (const sign of [-1, 1]) {
                const mirror = (points) => points.map(([a, b]) => [a * sign, b]);
                poly(mirror([[0, 1], [5, -8], [14, -11], [12, -2], [7, 3], [10, 8], [5, 11], [0, 5]]));
                line(mirror([[1, 2], [7, -4], [11, -8]]), print);
                line(mirror([[2, 5], [6, 7], [8, 6]]), paper);
            }
            line([[0, -6], [0, 7]], ink);
            break;
        case "hex":
            for (const [dx, dy] of [[-6, -4], [6, -4], [0, 7]]) {
                const cell = ring(6, 7, Math.PI / 6).map(([a, b]) => [a + dx, b + dy]);
                poly(cell, shadow);
                line(cell, edge);
            }
            break;
        case "tear":
            poly([[0, -13], [6, -2], [8, 4], [4, 10], [-3, 11], [-8, 6], [-7, -1]], ink);
            poly([[0, -8], [4, -1], [5, 4], [2, 7], [-3, 7], [-4, 3]], paper);
            line([[-10, 11], [-6, 7], [-9, 1], [-5, 3]], print);
            line([[10, 11], [6, 7], [10, 2], [6, 3]], edge);
            break;
        case "ribs":
            line([[0, -13], [0, 14]], edge);
            for (let j = 0; j < 4; j++) {
                const row = j * 5 - 8;
                line([[-10 + j, row - 2], [-6, row + 2], [0, row + 3], [6, row + 2], [10 - j, row - 2]], paper);
                line([[-10 + j, row - 1], [-5, row + 3], [0, row + 4], [5, row + 3], [10 - j, row - 1]], ink);
            }
            break;
        case "jelly":
            poly([[-11, -7], [-6, -11], [7, -10], [12, -4], [11, 7], [5, 11], [-9, 10], [-13, 5]], ink);
            poly([[-9, -6], [-5, -8], [6, -8], [9, -3], [8, 6], [4, 9], [-8, 8], [-10, 4]], print);
            line([[-7, -1], [-7, -5], [-4, -6], [3, -6]], edge);
            line([[-4, 4], [-3, 4]], ink);
            line([[3, 4], [4, 4]], ink);
            break;
        case "flower":
            for (let i = 0; i < 5; i++) {
                const t = i * TAU / 5;
                poly([[1, 0], [7, -5], [13, -3], [14, 1], [9, 4], [4, 3]].map(([a, b]) =>
                    [a * Math.cos(t) - b * Math.sin(t), a * Math.sin(t) + b * Math.cos(t)]), i % 2 ? paper : print);
            }
            poly(ring(7, 3), ink);
            break;
        case "gear":
            poly(Array.from({ length: 32 }, (_, i) => {
                const r = i % 4 < 2 ? 14 : 10;
                return [Math.cos(i * TAU / 32) * r, Math.sin(i * TAU / 32) * r];
            }), ink);
            line(ring(14, 8), edge);
            line([[0, -6], [0, 0], [5, 3]], paper);
            break;
        case "lace":
            line(ring(16, 12), edge);
            line(ring(12, 7), edge);
            for (let i = 0; i < 8; i++) {
                const t = i * TAU / 8;
                poly([[7, 0], [10, -3], [15, 0], [10, 3]].map(([a, b]) =>
                    [a * Math.cos(t) - b * Math.sin(t), a * Math.sin(t) + b * Math.cos(t)]), paper);
            }
            break;
        case "score":
            for (let j = 0; j < 5; j++) line([[-14, j * 3 - 6], [14, j * 3 - 6]], ink, .8);
            poly([[-7, 4], [-3, 3], [-1, 6], [-4, 9], [-7, 8]], ink);
            poly([[5, 1], [9, 0], [11, 3], [8, 6], [5, 5]], ink);
            line([[-2, 7], [-2, -8], [10, -11], [10, 3]], edge);
            break;
        case "flame":
            poly([[-8, 11], [-12, 5], [-9, -3], [-5, -1], [-3, -9], [1, -14], [4, -4], [8, -8], [12, 1], [11, 8], [6, 11]], ink);
            poly([[-6, 8], [-5, 2], [-2, 4], [2, -5], [5, 1], [8, -2], [8, 6], [4, 9]], paper);
            line([[-3, 8], [0, 4], [2, 1], [4, 7]], edge);
            break;
    }
}

function makeTile(theme, seed, variant) {
    const frame = createFrame(size, size);
    const p = painterFor(frame);
    const rng = new Rng(seed * 193 + variant * 127);
    const palette = theme.palette.map(hex);
    const [ink, base, shadow, print, paper, edge] = palette;
    p.rect(0, 0, size, size, base);
    if (variant === 2) {
        for (let y = 0; y < 4; y++) {
            for (let x = 0; x < 4; x++) {
                if ((x + y) % 2 === 0) p.rect(x * 17 - 3, y * 17 - 7, 17, 17, ink, .45);
            }
        }
    }
    for (let i = 0; i < 350; i++) {
        const x = rng.next() * size, y = rng.next() * size;
        p.dot(x, y, rng.next() < .60 ? ink : edge, .34);
    }
    const offset = variant * 4;
    panel(p, rng, [[-6, 3 + offset], [9, 0], [15, 3], [23, -1], [44, 1], [54, 9],
        [52, 14], [57, 17], [52, 24], [56, 26], [47, 34], [29, 37], [16, 35], [6, 41], [-5, 38]], shadow, palette, true);
    panel(p, rng, [[14 - offset, 28], [23, 25], [25, 27], [37, 23], [47, 25], [56, 22], [68, 25],
        [68, 62], [54, 66], [41, 62], [36, 64], [22, 59], [18, 54], [11 - offset, 54], [14 - offset, 46], [9 - offset, 40]],
        variant % 2 === 0 ? print : shadow, palette, false);
    panel(p, rng, [[-4, 46], [6, 44], [9, 47], [18, 43], [25, 47], [23, 51], [26, 54], [23, 60],
        [25, 66], [0, 66]], variant % 2 === 0 ? shadow : print, palette, true);
    if (variant !== 2) {
        const left = 30 + (variant % 3) * 3;
        const top = 31 - variant * 3;
        const tilt = [-.22, .33, 0, -.45][variant];
        const c = Math.cos(tilt), s = Math.sin(tilt);
        const sheet = [[-16, -7], [-8, -12], [-2, -11], [6, -14], [18, -11], [17, -4], [20, -2], [16, 5],
            [18, 8], [12, 13], [-4, 14], [-9, 12], [-15, 14], [-13, 7], [-17, 1]];
        panel(p, rng, sheet.map(([x, y]) => [left + x * c - y * s, top + 9 + x * s + y * c]),
            paper, palette, variant === 1);
        stamp(p, theme.motif, palette, left + 1, top + 9, tilt, .70 + variant * .08);
    } else {
        for (let line = 0; line < 4; line++) p.line(36 - line, 35 + line * 4, 61, 32 + line * 4, paper, .64);
    }
    for (let i = 0; i < 3; i++) {
        const x = 4 + i * 6, y = 8 + i * 2 + variant;
        p.line(x - 1, y, x + 2, y, edge, .74);
        p.dot(x, y + 1, ink, .78);
    }
    return composite(frame, new Uint8ClampedArray(size * size * 4), null);
}

const textureDir = join(assetRoot, "textures/block/barrier_floor");
const modelDir = join(assetRoot, "models/block");
const statesDir = join(assetRoot, "blockstates");
for (const directory of [textureDir, modelDir, statesDir]) mkdirSync(directory, { recursive: true });
const writeJson = (path, value) => writeFileSync(path, JSON.stringify(value, null, 2) + "\n");
const tiles = [];
for (const [index, [themeId, theme]] of Object.entries(themes).entries()) {
    const floorId = `barrier_floor_${themeId}`;
    const path = join(resources, "data/madoka_magic/dimension", `witch_${themeId}.json`);
    const layers = JSON.parse(readFileSync(path, "utf8")).generator.settings.layers;
    const baseBlock = layers[1].block.replace("minecraft:", "minecraft:block/");
    const models = [];
    const frames = [];
    for (let variant = 0; variant < variants; variant++) {
        const pixels = makeTile(theme, index + 1, variant);
        frames.push(pixels);
        writeFileSync(join(textureDir, `${themeId}_${variant}.png`), encodePNG(size, size, pixels));
        const model = `${floorId}_${variant}`;
        writeJson(join(modelDir, `${model}.json`), {
            parent: "minecraft:block/cube_bottom_top",
            textures: { top: `madoka_magic:block/barrier_floor/${themeId}_${variant}`, side: baseBlock, bottom: baseBlock },
        });
        const weight = variant === 2 ? 5 : 1;
        for (const y of [0, 90, 180, 270]) models.push({ model: `madoka_magic:block/${model}`, y, weight });
    }
    writeJson(join(statesDir, `${floorId}.json`), { variants: { "": models } });
    tiles.push({ label: theme.label, frames });
}

if (process.argv[3]) {
    const path = resolve(process.argv[3]);
    mkdirSync(dirname(path), { recursive: true });
    const preview = canvas(720, 500, [35, 31, 38, 255]);
    tiles.forEach(({ label, frames }, i) => {
        const x = i % 2 * 360 + 20, y = Math.floor(i / 2) * 100 + 10;
        preview.text(label, x, y, 1, [235, 219, 210]);
        frames.forEach((pixels, variant) => preview.paste(pixels, size, size, x + variant * size, y + 15));
    });
    writeFileSync(path, preview.png());
}
console.log(`PixelFX exported ${tiles.length * variants} collage tiles for ${tiles.length} barriers.`);
