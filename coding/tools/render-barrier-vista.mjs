// Draw a barrier far-scenery billboard with the local PixelFX checkout.
// Transparency only: the in-game sky and fog are already there.
// Usage: node tools/render-barrier-vista.mjs <pixelfx checkout> [theme] [preview PNG]
import { mkdirSync, writeFileSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";

if (!process.argv[2]) throw new Error("Provide the path to a PixelFX checkout.");
const upstream = resolve(process.argv[2]);
const load = (path) => import(pathToFileURL(join(upstream, path)).href);
const { createFrame, painterFor, composite, hex } = await load("src/core/index.js");
const { encodePNG } = await load("tools/lib/png.mjs");

const WIDTH = 320;
const HEIGHT = 180;
const textures = resolve(dirname(fileURLToPath(import.meta.url)),
    "../src/main/resources/assets/madoka_magic/textures/environment/vista");

const palette = {
    ink: hex("#140d19"),
    body: hex("#1d1424"),
    edge: hex("#3a2a40"),
    faded: hex("#2a1b2e"),
    gold: hex("#f5c452"),
    glow: hex("#ffe28a")
};

const frame = createFrame(WIDTH, HEIGHT);
const p = painterFor(frame);

/** 粗枝干：一条从 base 到 tip 的锥形多边形。 */
function limb(x0, y0, x1, y1, w0, w1, color) {
    const dx = x1 - x0, dy = y1 - y0, len = Math.hypot(dx, dy) || 1;
    const nx = -dy / len, ny = dx / len;
    p.poly([
        [x0 + nx * w0, y0 + ny * w0], [x1 + nx * w1, y1 + ny * w1],
        [x1 - nx * w1, y1 - ny * w1], [x0 - nx * w0, y0 - ny * w0]
    ], color);
}

// 主巨树：粗干、三根主枝，再分细枝。整棵是近黑剪影。
p.poly([[108, HEIGHT], [110, 118], [112, 96], [118, 78], [126, 68], [134, 78], [140, 96],
    [142, 118], [144, HEIGHT]], palette.ink);
limb(126, 68, 100, 54, 7, 3, palette.ink);
limb(100, 54, 78, 40, 3, 1.4, palette.ink);
limb(100, 54, 86, 30, 2.2, 1, palette.ink);
limb(126, 68, 150, 52, 6, 2.6, palette.ink);
limb(150, 52, 170, 38, 2.6, 1.2, palette.ink);
limb(150, 52, 156, 26, 2, 0.9, palette.ink);
limb(126, 68, 122, 40, 3.4, 1.4, palette.ink);
limb(122, 40, 112, 20, 1.4, 0.8, palette.ink);
limb(122, 40, 132, 16, 1.4, 0.8, palette.ink);

// 细枝网：只给轮廓线，末端一点金。
const twigs = [
    [[78, 40], [64, 30], [52, 24]], [[86, 30], [76, 18], [70, 10]],
    [[170, 38], [184, 30], [196, 24]], [[156, 26], [160, 12], [158, 4]],
    [[112, 20], [102, 8]], [[132, 16], [138, 6]],
    [[118, 78], [96, 70], [84, 60]], [[134, 78], [154, 70], [168, 62]]
];
for (const path of twigs) {
    p.polyline(path, palette.edge, 0.9);
    const tip = path[path.length - 1];
    p.dot(tip[0], tip[1], palette.gold, 0.55);
}

// 右侧断裂拱门：两柱一梁，拱顶塌了一半。
p.poly([[238, HEIGHT], [238, 62], [248, 52], [258, 62], [258, HEIGHT]], palette.body);
p.poly([[288, HEIGHT], [288, 96], [298, 88], [298, HEIGHT]], palette.body);
// 坍塌的拱梁：左边还连着，右边断在外面。
p.poly([[232, 62], [238, 50], [252, 42], [266, 46], [274, 58], [264, 54], [250, 50], [240, 56], [234, 66]],
    palette.body);
p.poly([[274, 58], [282, 70], [286, 84], [282, 94], [278, 82], [272, 68]], palette.ink);
p.polyline([[238, 62], [248, 52], [258, 62]], palette.edge, 0.7);
p.polyline([[240, 70], [240, 110], [244, 150]], palette.gold, 0.18);

// 左侧残墙：比主树远一层，压暗。
p.poly([[0, HEIGHT], [4, 150], [16, 146], [22, 126], [34, 130], [40, 152], [56, 156], [62, HEIGHT]],
    palette.faded, 0.72);
p.polyline([[4, 150], [16, 146], [22, 126], [34, 130]], palette.edge, 0.5);

// 底边的枯花茎，把巨构和地面连起来。
for (let i = 0; i < 18; i++) {
    const x = 6 + i * 18 + (i * 5) % 9;
    const h = 8 + (i * 11) % 22;
    p.line(x, HEIGHT, x + (i % 2 ? 3 : -3), HEIGHT - h, palette.ink, 0.95);
    if (i % 2 === 0) {
        p.dot(x + (i % 2 ? 3 : -3), HEIGHT - h, palette.gold, 0.32);
    }
}

const pixels = composite(frame, new Uint8ClampedArray(WIDTH * HEIGHT * 4), null);
mkdirSync(textures, { recursive: true });
const theme = process.argv[3] ?? "sacrifice";
writeFileSync(join(textures, `${theme}_vista.png`), encodePNG(WIDTH, HEIGHT, pixels));

if (process.argv[4]) {
    const output = resolve(process.argv[4]);
    mkdirSync(dirname(output), { recursive: true });
    writeFileSync(output, encodePNG(WIDTH, HEIGHT, pixels));
}
console.log(`PixelFX exported ${theme}_vista.png (${WIDTH}x${HEIGHT}).`);
