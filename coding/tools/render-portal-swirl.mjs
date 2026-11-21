// Generate the rotating portal swirl with the local PixelFX checkout.
// Usage: node tools/render-portal-swirl.mjs <pixelfx checkout> [preview PNG]
import { mkdirSync, writeFileSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";

if (!process.argv[2]) throw new Error("Provide the path to a PixelFX checkout.");
const upstream = resolve(process.argv[2]);
const load = (path) => import(pathToFileURL(join(upstream, path)).href);
const { createFrame, createFx, composite, hex } = await load("src/core/index.js");
const { encodePNG } = await load("tools/lib/png.mjs");
const voidOrb = await load("src/fx/shadow/void-orb.js");

const WIDTH = 96;
const HEIGHT = 128;
const FRAMES = 16;
const COLUMNS = 4;
const textures = resolve(dirname(fileURLToPath(import.meta.url)),
    "../src/main/resources/assets/madoka_magic/textures/particle");

// 灰度调色板：游戏里按主题色 tint，一套帧服务所有魔女。
const grayscale = ["#05050a", "#101018", "#1e1e2a", "#353548", "#55556e",
    "#7d7d9a", "#b6b6cc", "#ffffff"].map(hex);

const fx = createFx(voidOrb, { x: WIDTH / 2, y: HEIGHT - 6, palette: grayscale, seed: 7 });
const dt = (voidOrb.meta.duration ?? 3.2) / FRAMES;
const frames = [];
for (let i = 0; i < FRAMES; i++) {
    const frame = createFrame(WIDTH, HEIGHT);
    fx.draw(frame);
    frames.push(composite(frame, new Uint8ClampedArray(WIDTH * HEIGHT * 4), null));
    fx.update(dt);
}

const rows = Math.ceil(frames.length / COLUMNS);
const sheet = new Uint8ClampedArray(WIDTH * COLUMNS * HEIGHT * rows * 4);
for (const [i, pixels] of frames.entries()) {
    const left = (i % COLUMNS) * WIDTH;
    const top = Math.floor(i / COLUMNS) * HEIGHT;
    for (let y = 0; y < HEIGHT; y++) {
        const line = pixels.subarray(y * WIDTH * 4, (y + 1) * WIDTH * 4);
        sheet.set(line, ((top + y) * WIDTH * COLUMNS + left) * 4);
    }
}
writeFileSync(join(textures, "witch_portal_swirl.png"),
    encodePNG(WIDTH * COLUMNS, HEIGHT * rows, sheet));

if (process.argv[3]) {
    const output = resolve(process.argv[3]);
    mkdirSync(dirname(output), { recursive: true });
    const preview = new Uint8ClampedArray(WIDTH * COLUMNS * HEIGHT * rows * 4);
    // 简单拼贴：保持原始像素，方便看单帧轮廓。
    preview.set(sheet);
    writeFileSync(output, encodePNG(WIDTH * COLUMNS, HEIGHT * rows, preview));
}
console.log(`PixelFX exported ${FRAMES} portal swirl frames (${WIDTH * COLUMNS}x${HEIGHT * rows}).`);
