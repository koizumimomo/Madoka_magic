// Usage: node tools/render-mourning-fx.mjs <pixelfx checkout> [preview PNG]
import { mkdirSync, writeFileSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";

if (!process.argv[2]) throw new Error("Provide the path to a PixelFX checkout.");
const upstream = resolve(process.argv[2]);
const fromPixelFx = path => import(pathToFileURL(join(upstream, path)).href);
const { createFrame, painterFor, composite, hex, clamp, ease, TAU } = await fromPixelFx("src/core/index.js");
const { canvas, upscale } = await fromPixelFx("tools/lib/fxlib.mjs");
const { encodePNG } = await fromPixelFx("tools/lib/png.mjs");
const assets = resolve(dirname(fileURLToPath(import.meta.url)), "../src/main/resources/assets/madoka_magic");
const [ink, shadow, purple, blue, aqua, pale] = ["#120e1d", "#2f2138", "#624869", "#407979", "#72cdbe", "#d0fff0"].map(hex);
const side = 48;

function footprint(p, t) {
    const alpha = clamp(t * 6) * clamp((1 - t) * 7);
    const spread = ease.outCubic(clamp(t * 2.2));
    const radius = 10 + spread * 12;
    const shine = Math.pow(t, 2);
    for (let i = 0; i < 4; i++) {
        const angle = i * TAU / 4 + 0.2;
        p.arc(0, 0, radius, 3, angle, angle + 1.13, ink, alpha * 0.85);
        p.arc(0, 0, radius, 1, angle, angle + 1.13, i % 2 ? aqua : blue, alpha * (0.64 + shine * 0.36));
        const x = Math.cos(angle + 1.22) * radius;
        const y = Math.sin(angle + 1.22) * radius;
        p.dot(x, y, pale, alpha * (0.45 + shine * 0.55));
    }
    for (const [x, y] of [[-4, 5], [4, -4]]) {
        p.poly([[x - 2, y - 6], [x + 1, y - 7], [x + 3, y - 5], [x + 2, y - 2], [x - 1, y - 1],
            [x - 3, y - 3]], aqua, alpha * 0.85);
        p.poly([[x - 1, y + 1], [x + 1, y], [x + 2, y + 3], [x - 1, y + 4]], blue, alpha);
        p.dot(x, y - 5, pale, alpha * (0.6 + 0.4 * shine));
    }
    for (let i = 0; i < 6; i++) {
        const angle = 1.1 + TAU * i / 6;
        const r = 8 + t * (5 + i % 3);
        p.dot(Math.cos(angle) * r, Math.sin(angle) * r, aqua, alpha * (0.5 + 0.5 * Math.sin(t * Math.PI)));
    }
}

function bloom(p, t) {
    const growth = ease.outCubic(clamp(t * 4.4 + 0.16));
    const opening = ease.outExpo(clamp((t - 0.04) * 4.0));
    const alpha = clamp((1 - t) * 2.7);
    const flowerY = 13 - 24 * growth;
    p.beam(-2, 17, 0, flowerY + 3, 3, ink, alpha);
    p.line(-2, 16, 0, flowerY + 3, blue, alpha * 0.9);
    p.poly([[-1, 12], [-9 * growth, 7], [-10 * growth, 1], [-5 * growth, 4], [-1, 8]], shadow, alpha);
    p.poly([[-1, 9], [9 * growth, 4], [10 * growth, -1], [4 * growth, 2], [-1, 6]], shadow, alpha);
    p.line(-1, 11, -8 * growth, 4, blue, alpha * 0.75);
    p.line(0, 8, 8 * growth, 1, blue, alpha * 0.75);

    const width = 3 + 11 * opening;
    const height = 4 + 5 * opening;
    const at = (x, y) => [x * width, flowerY + y * height];
    const petal = (points, color) => p.poly(points.map(([x, y]) => at(x, y)), color, alpha);
    petal([[-1.02, -0.33], [-0.77, -0.91], [-0.42, -0.79], [-0.19, -1.06], [0.23, -0.96],
        [0.53, -0.9], [0.86, -0.74], [1.02, -0.34], [0.74, 0.38], [0.32, 0.73],
        [-0.31, 0.81], [-0.75, 0.40]], ink);
    petal([[-0.92, -0.38], [-0.71, -0.76], [-0.54, -0.53], [-0.51, 0.2], [-0.20, 0.55],
        [-0.60, 0.35]], purple);
    petal([[0.87, -0.39], [0.65, -0.66], [0.47, -0.46], [0.44, 0.11], [0.20, 0.58],
        [0.55, 0.37]], shadow);
    petal([[-0.45, -0.28], [-0.35, -0.72], [-0.11, -0.9], [0.29, -0.73], [0.41, -0.35],
        [0.19, -0.08], [-0.14, -0.04]], purple);
    petal([[-0.25, -0.41], [-0.15, -0.68], [0.18, -0.58], [0.19, -0.39], [-0.05, -0.18],
        [-0.22, -0.23]], ink);
    p.line(...at(-0.82, -0.47), ...at(-0.64, -0.66), aqua, alpha * 0.8);
    p.line(...at(0.49, -0.28), ...at(0.67, -0.55), blue, alpha * 0.85);
    p.line(...at(-0.27, -0.64), ...at(0.08, -0.71), aqua, alpha);
    p.dot(...at(-0.02, -0.46), pale, alpha * (1 - t));

    for (let i = 0; i < 7; i++) {
        const angle = 0.53 + i * TAU / 7;
        const x = Math.cos(angle) * (4 + t * (11 + i % 3));
        const y = flowerY - 1 + Math.sin(angle) * 4 - t * (7 + i % 4);
        const fade = alpha * clamp((1 - t) * (1.1 + i % 3));
        p.dot(x, y + 1, shadow, fade);
        p.dot(x, y, i % 3 ? aqua : pale, fade);
    }
}

const effects = [["mourning_footprint", 16, footprint], ["mourning_rose", 16, bloom]];
const preview = process.argv[3] && canvas(576, 192, [42, 38, 48, 255]);
for (const [row, [id, count, draw]] of effects.entries()) {
    const textureDir = join(assets, "textures/particle/mourning", id);
    mkdirSync(textureDir, { recursive: true });
    const textures = Array.from({ length: count }, (_, index) => {
        const frame = createFrame(side, side);
        draw(painterFor(frame).place(side / 2, side / 2), index / (count - 1));
        const rgba = composite(frame, new Uint8ClampedArray(side * side * 4), null);
        const name = String(index).padStart(2, "0");
        writeFileSync(join(textureDir, `${name}.png`), encodePNG(side, side, rgba));
        if (preview && index % 3 === 0) {
            preview.paste(upscale(rgba, side, side, 2), 96, 96, index / 3 * 96, row * 96);
        }
        return `madoka_magic:mourning/${id}/${name}`;
    });
    writeFileSync(join(assets, "particles", `${id}.json`), JSON.stringify({ textures }, null, 2) + "\n");
}
if (preview) writeFileSync(resolve(process.argv[3]), preview.png());
