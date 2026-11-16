// Paint the portal and half-witch atlases with the local PixelFX checkout (MIT, see META-INF).
// Usage: node tools/render-witch-portals.mjs <pixelfx checkout> [contact sheet PNG]
import { mkdirSync, writeFileSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";

if (!process.argv[2]) throw new Error("Provide the path to a PixelFX checkout.");
const upstream = resolve(process.argv[2]);
const importFx = (path) => import(pathToFileURL(join(upstream, path)).href);
const { createFrame, composite, painterFor, hex, TAU } = await importFx("src/core/index.js");
const { canvas, upscale } = await importFx("tools/lib/fxlib.mjs");
const { encodePNG } = await importFx("tools/lib/png.mjs");
const textures = resolve(dirname(fileURLToPath(import.meta.url)), "../src/main/resources/assets/madoka_magic/textures/particle");

const violet = ["#100a17", "#21132f", "#43245f", "#71439c", "#b87ce9", "#edccff"].map(hex);
const teal = ["#09151d", "#102b35", "#21515f", "#388d97", "#82d8c9", "#d4fff0"].map(hex);
const points = (n, draw) => Array.from({ length: n }, (_, i) => draw(i / n, i));

function star(p, x, y, rx, ry, color, alpha = 1) {
    p.poly([[x, y - ry], [x + rx * .17, y - ry * .31], [x + rx * .38, y - ry * .13], [x + rx, y],
        [x + rx * .38, y + ry * .13], [x + rx * .17, y + ry * .31], [x, y + ry],
        [x - rx * .17, y + ry * .31], [x - rx * .38, y + ry * .13], [x - rx, y],
        [x - rx * .38, y - ry * .13], [x - rx * .17, y - ry * .31]], color, alpha);
}

function portal(p, phase, exit) {
    const [ink, shade, inset, mid, rim, shine] = exit ? teal : violet;
    const twist = phase * TAU;
    const direction = exit ? -1 : 1;
    const at = (angle, r = 1) => {
        const s = Math.sin(angle), c = Math.cos(angle);
        const edge = exit ? 1 + .065 * Math.cos(angle * 4) : .83 + .17 * Math.abs(c) + .045 * Math.cos(angle * 5);
        const drift = .7 * Math.sin(3 * angle - twist);
        const x = exit ? Math.sign(c) * Math.pow(Math.abs(c), .68) : c;
        const y = exit ? Math.min(.91, s) : s;
        return [r * x * edge * (34 + drift), r * y * (53 + drift)];
    };
    const shell = (r) => points(32, (u) => at(TAU * u, r));

    p.poly(shell(1.12), ink, .42);
    p.poly(shell(1.05), ink);
    p.poly(shell(1), inset);
    p.poly(shell(.91), mid);
    p.poly(shell(.83), ink);
    p.poly(shell(.76), shade);
    p.poly(shell(.64), ink);

    // Small asymmetric edge facets and two staggered bands inside the opening.
    for (let i = 0; i < 8; i++) {
        const a = TAU * i / 8 + .19;
        const pulse = .67 + .22 * Math.cos(twist + i * 1.53);
        p.poly([at(a - .15, .89), at(a - .075, 1.015), at(a + .17, .965), at(a + .30, .87)], rim, pulse);
        p.polyline([at(a - .085, .93), at(a - .025, .983), at(a + .15, .945)], shine, pulse * .92);
    }
    for (let i = 0; i < 4; i++) {
        const a = i * TAU / 4 + twist * direction;
        const strip = (offset) => points(12, (u) => at(a + direction * u * 1.57, .28 + u * .44 + offset * Math.sin(Math.PI * u)));
        p.poly([...strip(.07), ...strip(-.035).reverse()], i % 2 ? inset : shade, .72);
        p.polyline(strip(.045).slice(5, 11), mid, .35);
    }

    for (let i = 0; i < 14; i++) {
        const f = (phase * direction + i * .61803398875 + 1) % 1;
        const [x, y] = at(i * 2.4 + direction * f * 1.7, .22 + .47 * f);
        const a = .90 * Math.sin(f * Math.PI);
        if (i % 5 === 0) star(p, x, y, 2.8, 4.6, rim, a);
        else p.dot(x, y, i % 4 ? mid : shine, a);
    }

    const outerStars = [[-37, -29], [36, 25], [29, -41], [-28, 43]];
    outerStars.forEach(([x, y], i) => {
        const pulse = .60 + .28 * Math.sin(twist + i * 1.3);
        star(p, x, y + 2 * Math.sin(twist + i), 3.4 + pulse, 6 + pulse * 2, rim, pulse);
        p.dot(x, y + 2 * Math.sin(twist + i), shine, pulse);
    });
    star(p, 0, -51, 6, 8, ink);
    star(p, 0, -51, 4.1, 6.2, rim);
    star(p, 0, -51, 2, 3.2, shine);
    const base = exit ? 48 : 53;
    p.poly([[0, base - 6], [6, base], [0, base + 5], [-6, base]], ink);
    p.polyline([[-4, base], [0, base - 4], [4, base], [0, base + 3], [-4, base]], rim, .9);
}

function inkMote(p, phase) {
    const [ink, shade, inset, mid, rim] = violet;
    const wave = Math.sin(phase * TAU) * 2;
    p.poly([[-8, 10], [-6, 3], [-3, -2], [1, -9], [5, -12], [4 + wave, -6],
        [9, -2], [5, 5], [0, 8], [-2, 13]], ink);
    p.poly([[-6, 8], [-4, 1], [1, -7], [4, -8], [3 + wave, -3], [6, 0], [1, 5], [-2, 9]], shade);
    p.polyline([[-6, 8], [-3, 3], [0, 1], [3 + wave, -4], [4, -7]], inset);
    p.polyline([[-4, 7], [-1, 2], [2 + wave, -2]], mid);
    p.dot(1 + wave, -2, rim, .7);
}

function butterfly(p, phase) {
    const [ink, , inset, mid, rim, shine] = violet;
    const spread = .65 + .35 * (.5 + .5 * Math.cos(phase * TAU));
    for (const side of [-1, 1]) {
        const mirror = (path) => path.map(([x, y]) => [x * side * spread, y]);
        p.poly(mirror([[0, -1], [5, -7], [13, -12], [12, -3], [8, 3], [10, 8], [5, 11], [2, 6]]), ink);
        p.poly(mirror([[1, 0], [6, -6], [11, -9], [10, -2], [6, 3], [7, 7], [5, 8], [2, 4]]), mid);
        p.poly(mirror([[2, 2], [6, 2], [7, 7], [5, 8], [3, 5]]), inset);
        p.polyline(mirror([[2, -1], [7, -6], [10, -8]]), rim);
        p.dot(8 * side * spread, -5, shine, .8);
    }
    p.line(0, -4, 0, 7, inset);
    p.dot(0, -4, shine);
}

function hexMote(p, phase) {
    const [ink, shade, inset, mid, rim, shine] = violet;
    const tilt = .26 * Math.sin(phase * TAU);
    const at = (r, i) => [Math.cos(i * TAU / 6 + tilt) * r, Math.sin(i * TAU / 6 + tilt) * r];
    const shell = (r) => points(6, (_, i) => at(r, i));
    p.poly(shell(12), ink);
    p.poly(shell(10), mid);
    p.poly(shell(7.4), shade);
    p.poly(shell(4.5), inset);
    p.polyline([at(9.5, 3), at(9.5, 4), at(9.5, 5)], rim);
    p.polyline([at(7, 0), at(7, 1), at(7, 2)], inset);
    star(p, 0, 0, 3.5, 4.2, rim, .8);
    p.dot(0, 0, shine);
}

function thorn(p, phase) {
    const [ink, , inset, mid, rim, shine] = violet;
    const sway = 2 * Math.sin(phase * TAU);
    p.poly([[-11, 10], [-7, 2], [-10, -4], [-4, -2], [-2, -7], [-1, -11], [3, -8],
        [10 + sway, -12], [7 + sway, -5], [12, -4], [7, 0], [4, 7], [1, 5], [-3, 11]], ink);
    p.poly([[-8, 8], [-4, 1], [-7, -2], [-3, -1], [-1, -7], [2, -6], [8 + sway, -9],
        [5, -4], [9, -3], [4, 0], [3, 5], [1, 3], [-4, 8]], inset);
    p.polyline([[-7, 8], [-3, 3], [1, 0], [3, -4], [8 + sway, -9]], mid);
    p.polyline([[-2, 2], [1, 0], [5 + sway, -6]], rim);
    p.dot(5 + sway, -6, shine, .9);
}

function crown(p, phase) {
    const [ink, , inset, mid, rim, shine] = violet;
    const pulse = .5 + .5 * Math.cos(phase * TAU);
    star(p, 0, 0, 11, 15, ink);
    star(p, 0, 0, 8.9, 13.5, mid);
    star(p, 0, -.3, 6.6, 10.8, rim);
    p.poly([[0, -8], [2, -1], [0, 5], [-2, 0]], shine, .72 + .28 * pulse);
    p.line(1, 3, 0, 8, inset, .8 - .36 * pulse);
}

function cocoon(p) {
    const [ink, amber, gold, light] = ["#211721", "#824a2d", "#d99b46", "#ffe9a2"].map(hex);
    p.poly([[0, -15], [8, -12], [12, -6], [11, 6], [6, 12], [0, 15], [-7, 12], [-11, 6], [-12, -6], [-7, -12]], ink);
    p.poly([[0, -13], [7, -10], [10, -5], [9, 6], [4, 11], [0, 13], [-5, 10], [-9, 5], [-10, -5], [-6, -10]], amber);
    for (let i = -1; i <= 1; i++) {
        const y = i * 6;
        p.poly([[-10, y + 1], [-4, y - 1], [3, y - 4], [9, y - 5], [10, y - 2], [3, y + 1], [-6, y + 4]], gold);
        p.polyline([[-7, y + 1], [0, y - 1], [6, y - 4]], light, .86);
    }
    star(p, 0, 0, 4.3, 7.1, ink);
    star(p, 0, 0, 3.0, 5.5, light);
}

function rainNode(p) {
    const [ink, mid, blue, light] = ["#16152a", "#425284", "#80b3ed", "#dbf3ff"].map(hex);
    p.poly([[-13, 12], [-8, 7], [-8, 1], [-12, -5], [-5, -2], [-2, -11], [1, -15], [5, -8],
        [7, -3], [13, -6], [10, 3], [8, 9], [13, 13], [4, 12], [0, 14], [-5, 12]], ink);
    p.polyline([[-11, 11], [-6, 7], [-6, 0], [-10, -4]], mid);
    p.polyline([[11, 11], [7, 6], [8, -1], [11, -4]], mid);
    p.poly([[1, -13], [4, -7], [6, -2], [3, 5], [0, 8], [-4, 3], [-4, -2]], mid);
    p.poly([[1, -11], [4, -3], [2, 3], [0, 5], [-2, 2], [-2, -2]], blue);
    star(p, 0, -1, 3.1, 5.4, light);
    p.polyline([[-11, 12], [-6, 9], [-3, 10], [0, 11], [4, 9], [10, 12]], blue, .85);
}

function tear(p) {
    p.poly([[1, -14], [5, -5], [8, 3], [6, 9], [1, 13], [-4, 12], [-8, 7], [-7, 1], [-3, -6]], hex("#314b78"));
    p.poly([[1, -11], [4, -4], [6, 3], [4, 8], [0, 10], [-4, 8], [-5, 4], [-2, -3]], hex("#77b5ec"));
    p.polyline([[-1, -4], [-3, 3], [-2, 7]], hex("#d9efff"));
}

function link(p) {
    p.rect(-16, -6, 32, 12, hex("#ffffff"), .26);
    p.rect(-16, -3, 32, 6, hex("#ffffff"), .73);
    p.rect(-16, -1, 32, 2, hex("#ffffff"));
}

function atlas(file, width, height, columns, tracks) {
    const frames = tracks.flatMap(({ count, draw }) => points(count, (phase) => {
        const frame = createFrame(width, height);
        draw(painterFor(frame).place(width / 2, height / 2), phase);
        return composite(frame, new Uint8ClampedArray(width * height * 4), null);
    }));
    const rows = frames.length / columns;
    if (!Number.isInteger(rows)) throw new Error(`Incomplete atlas ${file}`);
    const sheet = new Uint8ClampedArray(width * columns * height * rows * 4);
    for (const [i, pixels] of frames.entries()) {
        const left = i % columns * width;
        const top = Math.floor(i / columns) * height;
        for (let y = 0; y < height; y++) {
            const line = pixels.subarray(y * width * 4, (y + 1) * width * 4);
            sheet.set(line, ((top + y) * width * columns + left) * 4);
        }
    }
    writeFileSync(join(textures, file), encodePNG(width * columns, height * rows, sheet));
    return frames;
}

const portals = atlas("witch_portals.png", 96, 128, 4, [
    { count: 16, draw: (p, phase) => portal(p, phase, false) },
    { count: 16, draw: (p, phase) => portal(p, phase, true) },
]);
const motes = atlas("half_witch.png", 32, 32, 8,
    [inkMote, butterfly, hexMote, thorn, crown].map((draw) => ({ count: 8, draw })));
const skills = atlas("witch_skills.png", 32, 32, 4,
    [cocoon, rainNode, tear, link].map((draw) => ({ count: 1, draw })));

if (process.argv[3]) {
    const output = resolve(process.argv[3]);
    mkdirSync(dirname(output), { recursive: true });
    const preview = canvas(624, 552, [41, 37, 49, 255]);
    preview.text("WITCH BARRIER / ENTRANCE", 16, 12, 1, [222, 187, 254]);
    preview.text("WITCH BARRIER / EXIT", 16, 158, 1, [165, 238, 224]);
    preview.text("HALF-WITCH / INK, BUTTERFLY, HONEYCOMB, THORN, CROWN", 16, 310, 1, [222, 187, 254]);
    for (let i = 0; i < 6; i++) {
        const frame = Math.floor(i * 16 / 6);
        preview.paste(portals[frame], 96, 128, 14 + i * 100, 24);
        preview.paste(portals[16 + frame], 96, 128, 14 + i * 100, 170);
    }
    for (let i = 0; i < 5; i++) {
        preview.paste(upscale(motes[i * 8 + 1], 32, 32, 3), 96, 96, 20 + i * 121, 324);
    }
    preview.text("WITCH SKILLS / COCOON, THORN NODE, TEAR, LINK", 16, 434, 1, [222, 187, 254]);
    for (let i = 0; i < skills.length; i++) {
        preview.paste(upscale(skills[i], 32, 32, 3), 96, 96, 20 + i * 148, 447);
    }
    writeFileSync(output, preview.png());
}
console.log(`PixelFX exported ${portals.length} portal frames, ${motes.length} half-witch frames and ${skills.length} skill sprites.`);
