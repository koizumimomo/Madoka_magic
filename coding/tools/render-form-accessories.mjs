// Usage: node tools/render-form-accessories.mjs <pixelfx checkout> [preview PNG]
import { mkdirSync, writeFileSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";

if (!process.argv[2]) throw new Error("Provide the path to a PixelFX checkout.");
const pixelFx = resolve(process.argv[2]);
const load = path => import(pathToFileURL(join(pixelFx, path)).href);
const { createFrame, composite, painterFor, hex, rgb, Rng } = await load("src/core/index.js");
const { canvas, upscale } = await load("tools/lib/fxlib.mjs");
const { encodePNG } = await load("tools/lib/png.mjs");
const output = resolve(dirname(fileURLToPath(import.meta.url)), "../src/main/resources/assets/madoka_magic/textures/entity/form");

function paint(width, height, draw) {
    const frame = createFrame(width, height);
    draw(painterFor(frame).place(0, 0));
    return composite(frame, new Uint8ClampedArray(width * height * 4), null);
}

// Forewing bands inspired by the emerald swallowtail. The multicolored hindwing and tails
// borrow the proportions and copper-to-rose color story of the Madagascar sunset moth.
const wing = paint(64, 64, p => {
    const [ink, purple, lilac, orchid, copper, amber, cream, deepTeal, teal, mint, ice] = [
        "#211726", "#483451", "#735278", "#ce8d9e", "#a65759", "#ebad64", "#ffe1a2",
        "#315159", "#508f85", "#85d5bc", "#d5f5cf",
    ].map(hex);

    // Scalloped hindwing, with two short tails and warm eyespots.
    p.poly([[1, 37], [23, 32], [40, 33], [53, 41], [55, 46], [52, 51], [46, 53], [44, 57],
        [40, 56], [38, 62], [34, 63], [32, 61], [33, 56], [28, 58], [24, 56], [22, 53],
        [17, 54], [13, 52], [8, 48], [1, 43]], ink);
    p.poly([[3, 38], [25, 34], [39, 35], [49, 41], [53, 45], [50, 49], [44, 51], [42, 54],
        [38, 53], [36, 60], [34, 61], [35, 53], [29, 55], [25, 53], [24, 50], [17, 51], [12, 48]], purple);
    p.poly([[8, 38], [28, 35], [42, 37], [50, 43], [47, 46], [41, 45], [36, 49], [30, 49],
        [25, 48], [23, 45], [17, 46]], lilac);
    p.poly([[11, 37], [21, 37], [33, 40], [43, 43], [49, 44], [47, 47], [41, 47], [36, 52],
        [29, 53], [25, 49], [20, 50], [17, 46]], copper);
    p.poly([[18, 40], [27, 42], [35, 43], [45, 43], [47, 45], [41, 45], [36, 49], [30, 48],
        [27, 46], [24, 47]], amber);
    p.poly([[20, 46], [25, 46], [28, 49], [32, 49], [35, 51], [30, 53], [25, 49], [22, 50]], orchid);
    p.polyline([[6, 40], [17, 45], [25, 46], [33, 54], [36, 58]], ink);
    p.polyline([[9, 40], [22, 41], [35, 46], [44, 50]], ink);
    for (const [x, y] of [[33, 45], [45, 43]]) {
        p.poly([[x - 3, y], [x - 1, y - 2], [x + 2, y - 2], [x + 3, y], [x + 1, y + 2], [x - 2, y + 2]], ink);
        p.dot(x, y - 1, cream);
        p.dot(x + 1, y, orchid);
    }
    p.polyline([[37, 54], [36, 58], [34, 61]], orchid);
    p.line(28, 55, 30, 55, cream);

    // Longer forewing with an iridescent diagonal band and a dark, spotted apex.
    p.poly([[1, 40], [3, 34], [8, 24], [15, 15], [24, 9], [36, 4], [47, 2], [60, 2], [63, 4],
        [62, 10], [58, 18], [53, 26], [46, 33], [38, 37], [26, 40], [11, 43], [1, 43]], ink);
    p.poly([[4, 39], [7, 31], [16, 17], [26, 11], [37, 6], [49, 5], [59, 4], [57, 12], [52, 21],
        [44, 30], [37, 34], [25, 37], [12, 41]], deepTeal);
    p.poly([[8, 36], [15, 25], [24, 19], [33, 15], [44, 10], [56, 5], [57, 7], [52, 14], [46, 20],
        [36, 25], [25, 29], [17, 33]], teal);
    p.poly([[12, 32], [19, 24], [29, 20], [41, 14], [52, 9], [50, 13], [44, 17], [35, 22],
        [26, 25], [18, 29]], mint);
    p.polyline([[23, 24], [30, 21], [40, 17], [47, 13], [51, 11]], ice);
    p.poly([[6, 39], [14, 35], [26, 32], [36, 29], [43, 25], [47, 25], [41, 31], [29, 35], [17, 38]], lilac);
    p.polyline([[8, 38], [21, 35], [29, 33], [38, 30]], amber);
    for (const line of [[[5, 38], [22, 29], [32, 19], [37, 8]], [[8, 38], [25, 33], [40, 26], [49, 15], [56, 9]],
        [[5, 38], [16, 28], [23, 15], [31, 9]]]) {
        p.polyline(line, ink);
    }
    for (const [x, y] of [[57, 9], [54, 15], [50, 22], [45, 29], [36, 34]]) {
        p.dot(x, y, cream);
        p.dot(x + 1, y - 1, x > 48 ? ice : orchid);
    }
});

const specimenFaceHeight = 152;
const specimen = paint(128, 256, p => {
    const [ink, shadow, rosewood, redWood, brass, gold, pale, mat, paper, fiber, pinShadow] = [
        "#241822", "#382433", "#58313e", "#784b4d", "#a77455", "#dcb080", "#ffe2a1",
        "#b69873", "#e1c8a4", "#f0d7af", "#a28779",
    ].map(hex);
    const bottom = specimenFaceHeight - 1;
    const frame = (inset, bevel, color) => {
        const left = inset, right = 95 - inset, top = inset, lowerEdge = bottom - inset;
        p.poly([[left + bevel, top], [right - bevel, top], [right, top + bevel], [right, lowerEdge - bevel],
            [right - bevel, lowerEdge], [left + bevel, lowerEdge], [left, lowerEdge - bevel], [left, top + bevel]], color);
    };
    frame(0, 5, ink);
    frame(1, 5, rosewood);
    p.polyline([[3, bottom - 4], [3, 7], [7, 3], [89, 3], [92, 6]], redWood);
    p.polyline([[4, bottom - 5], [7, bottom - 3], [88, bottom - 3], [92, bottom - 7], [92, 7]], shadow);
    frame(5, 4, brass);
    frame(6, 3, gold);
    frame(7, 3, shadow);
    frame(9, 2, mat);
    frame(11, 1, paper);
    p.polyline([[12, bottom - 13], [12, 13], [83, 13]], fiber);
    p.polyline([[13, bottom - 13], [83, bottom - 13], [83, 14]], mat);
    for (const [x, y] of [[7, 7], [88, 7], [7, bottom - 7], [88, bottom - 7]]) {
        p.poly([[x, y - 4], [x + 3, y], [x, y + 3], [x - 3, y]], shadow);
        p.poly([[x, y - 3], [x + 2, y], [x, y + 2], [x - 2, y]], gold);
        p.dot(x, y - 1, pale);
    }
    const rng = new Rng(9868);
    for (let i = 0; i < 208; i++) {
        p.dot(rng.range(14, 82), rng.range(15, bottom - 14), i % 3 === 0 ? mat : fiber, 0.30);
    }

    // Mount a single butterfly on the paper by mirroring its original painted wing.
    p.place(0, (specimenFaceHeight - 80) / 2);
    function wings(offsetY, shading) {
        for (let y = 0; y < 38; y++) {
            for (let x = 0; x < 34; x++) {
                const offset = (Math.floor(y * 64 / 38) * 64 + Math.floor(x * 64 / 34)) * 4;
                if (wing[offset + 3] === 0) continue;
                const color = shading ? pinShadow : rgb(wing[offset], wing[offset + 1], wing[offset + 2]);
                p.dot(49 + x, 16 + y + offsetY, color, shading ? 0.40 : 1);
                p.dot(46 - x, 16 + y + offsetY, color, shading ? 0.40 : 1);
            }
        }
    }
    wings(2, true);
    wings(0, false);
    p.poly([[47, 35], [49, 35], [50, 39], [49, 48], [48, 51], [46, 48], [46, 39]], ink);
    p.polyline([[47, 37], [46, 33], [42, 30]], shadow);
    p.polyline([[48, 37], [49, 33], [53, 30]], shadow);
    p.dot(47, 37, brass);
    p.dot(48, 40, redWood);
    p.dot(48, 41, pale);
    p.rect(34, 58, 28, 6, mat);
    p.rect(34, 58, 27, 5, fiber);
    p.line(39, 60, 48, 60, brass);
    p.line(39, 61, 54, 61, mat);
    p.dot(57, 60, brass);

    p.place(0, 0);
    p.rect(96, 0, 32, 48, rosewood);
    p.rect(96, 4, 32, 3, brass);
    p.rect(96, 7, 32, 2, gold);
    p.rect(96, 41, 32, 7, shadow);
    p.place(0, specimenFaceHeight);
    p.rect(0, 0, 96, 96, shadow);
    p.rect(2, 2, 92, 92, rosewood);
    p.polyline([[3, 93], [3, 3], [92, 3]], redWood);
    for (let i = 0; i < 22; i++) {
        const y = 6 + i * 4;
        p.polyline([[4, y], [27, y + 1], [52, y - 1], [92, y]], i % 2 === 0 ? shadow : redWood, 0.72);
    }
    p.rect(31, 2, 5, 92, shadow);
    p.rect(60, 2, 5, 92, shadow);
    p.rect(32, 45, 3, 5, brass);
    p.rect(61, 45, 3, 5, brass);
});

const honeycomb = paint(64, 64, p => {
    const [brown, deep, bronze, ochre, amber, wax, shine] = [
        "#43261e", "#633723", "#a05629", "#c78339", "#efa946", "#ffd988", "#fff0bf",
    ].map(hex);
    for (let cell = 0; cell < 2; cell++) {
        const face = (points, color) => p.poly(points.map(([x, y]) => [x + cell * 32, y]), color);
        face([[16, 1], [29, 8], [29, 24], [16, 31], [3, 24], [3, 8]], brown);
        face([[16, 2], [28, 9], [28, 23], [16, 30], [4, 23], [4, 9]], ochre);
        face([[16, 2], [28, 9], [26, 12], [16, 7], [8, 12], [4, 9]], wax);
        face([[16, 7], [25, 12], [25, 21], [16, 26], [7, 21], [7, 12]], deep);
        face([[16, 10], [23, 14], [23, 20], [16, 24], [9, 20], [9, 14]], cell === 0 ? brown : amber);
        face([[8, 20], [16, 25], [24, 20], [25, 21], [16, 26], [7, 21]], amber);
        p.line(5 + cell * 32, 9, 15 + cell * 32, 3, shine);
        p.line(5 + cell * 32, 10, 5 + cell * 32, 19, wax);
        if (cell === 1) {
            p.line(12 + cell * 32, 15, 17 + cell * 32, 12, wax);
            p.dot(19 + cell * 32, 14, shine);
            p.line(17 + cell * 32, 23, 22 + cell * 32, 20, bronze);
        }
    }
    const rng = new Rng(215);
    p.rect(0, 32, 16, 16, ochre);
    for (let i = 0; i < 25; i++) {
        p.dot(rng.range(0, 15), rng.range(33, 47), i % 3 === 0 ? wax : bronze);
    }
    p.rect(0, 32, 16, 2, amber);
    p.rect(0, 46, 16, 2, bronze);
});

const thorns = paint(32, 32, p => {
    const [ink, bark, indigo, blue, needle, glint] = [
        "#1d2030", "#323951", "#495b7c", "#758cb1", "#a6bed5", "#d0dff1",
    ].map(hex);
    p.rect(0, 0, 16, 32, bark);
    p.poly([[1, 0], [6, 0], [5, 11], [8, 19], [7, 31], [3, 31], [5, 20], [2, 12]], indigo);
    p.poly([[12, 0], [15, 0], [15, 31], [8, 31], [11, 24], [12, 12]], ink);
    p.polyline([[3, 1], [2, 6], [3, 13], [6, 19], [5, 28], [5, 31]], blue);
    p.line(9, 2, 8, 8, indigo);
    p.line(9, 15, 10, 21, indigo);
    p.line(8, 26, 7, 31, blue);

    p.rect(16, 0, 16, 32, indigo);
    p.poly([[16, 0], [22, 0], [25, 9], [24, 18], [28, 31], [18, 31], [22, 21], [20, 11]], blue);
    p.poly([[17, 1], [21, 0], [22, 10], [23, 17], [20, 26], [20, 30], [19, 25], [21, 16]], needle);
    p.line(18, 1, 18, 7, glint);
    p.line(18, 12, 20, 16, needle);
    p.line(26, 10, 29, 22, bark);
});

mkdirSync(output, { recursive: true });
writeFileSync(join(output, "butterfly_specimen.png"), encodePNG(128, 256, specimen));
writeFileSync(join(output, "honeycomb.png"), encodePNG(64, 64, honeycomb));
writeFileSync(join(output, "thorn_vine.png"), encodePNG(32, 32, thorns));

if (process.argv[3]) {
    const preview = resolve(process.argv[3]);
    const image = canvas(782, 372, [37, 36, 46, 255]);
    const front = new Uint8ClampedArray(96 * specimenFaceHeight * 4);
    for (let y = 0; y < specimenFaceHeight; y++) {
        front.set(specimen.subarray(y * 128 * 4, (y * 128 + 96) * 4), y * 96 * 4);
    }
    image.paste(upscale(front, 96, specimenFaceHeight, 2), 192, specimenFaceHeight * 2, 120, 24);
    image.paste(upscale(honeycomb, 64, 64, 3), 192, 192, 432, 24);
    image.paste(upscale(thorns, 32, 32, 4), 128, 128, 635, 24);
    mkdirSync(dirname(preview), { recursive: true });
    writeFileSync(preview, image.png());
}
