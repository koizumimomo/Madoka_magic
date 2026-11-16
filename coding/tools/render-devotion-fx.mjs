// Render the Forge particle frames with a local checkout of patch-gg/pixelfx.
// Usage: node tools/render-devotion-fx.mjs <pixelfx checkout> [preview PNG]
import { mkdirSync, writeFileSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";

if (!process.argv[2]) throw new Error("Provide the path to a PixelFX checkout.");
const upstream = resolve(process.argv[2]);
const fromUpstream = (path) => import(pathToFileURL(join(upstream, path)).href);
const { createFrame, composite, painterFor, hex, ease, clamp, Rng, TAU } = await fromUpstream("src/core/index.js");
const { loadEffect, renderFrames, canvas, upscale } = await fromUpstream("tools/lib/fxlib.mjs");
const { encodePNG } = await fromUpstream("tools/lib/png.mjs");
const root = resolve(dirname(fileURLToPath(import.meta.url)), "..", "src", "main", "resources", "assets", "madoka_magic");
const textureRoot = join(root, "textures", "particle", "devotion");
const descriptions = join(root, "particles");
const effects = new Map();

const palette = ["#111014", "#2c1e20", "#62401f", "#b87521", "#f2c14e", "#ffe28b", "#fff3c0"];
const [ink, shadow, ochre, amber, gold, pale, cream] = palette.map(hex);
const red = hex("#cf735d");
const violet = hex("#b58cf1");

function paint(id, side, count, draw) {
    const frames = Array.from({ length: count }, (_, i) => {
        const frame = createFrame(side, side);
        const painter = painterFor(frame).place(side / 2, side / 2);
        draw(painter, i / (count - 1), i);
        return composite(frame, new Uint8ClampedArray(side * side * 4), null);
    });
    effects.set(id, { side, frames });
}

async function recolor(id, upstreamId, side, count, center, options, first = 0) {
    const effect = await loadEffect(upstreamId);
    const rendered = renderFrames(effect, { fps: 20, bg: "none", actor: false, options });
    const frames = rendered.frames.slice(first, first + count).map(({ clear }) => {
        const frame = new Uint8ClampedArray(side * side * 4);
        for (let y = 0; y < side; y++) {
            const sy = y + center[1] - side / 2;
            if (sy < 0 || sy >= rendered.h) continue;
            for (let x = 0; x < side; x++) {
                const sx = x + center[0] - side / 2;
                if (sx < 0 || sx >= rendered.w) continue;
                const offset = (sy * rendered.w + sx) * 4;
                frame.set(clear.subarray(offset, offset + 4), (y * side + x) * 4);
            }
        }
        return frame;
    });
    if (frames.length !== count) throw new Error(`Not enough PixelFX frames for ${id}`);
    effects.set(id, { side, frames });
}

// One joined crescent, painted from the leading edge to the fading tail.
paint("butterfly_slash", 128, 13, (p, t) => {
    if (t >= 1) return;
    const sweep = clamp(t * 3.15 + 0.20, 0, 1);
    const fade = clamp((1 - t) * 2.45, 0, 1);
    const tail = -1.15 + Math.max(0, t - 0.56) * 2.65;
    const lead = -1.15 + 2.30 * sweep;
    const point = (i, inset, size) => {
        const u = i / 26;
        const angle = tail + (lead - tail) * u;
        const depth = inset * Math.pow(Math.sin(Math.PI * u), 0.75) * size;
        const x = Math.sin(angle) * (63 - depth);
        return [x, 36 - Math.cos(angle) * (57 - depth) - x * 0.17];
    };
    const strip = (outer, inner) => {
        const a = Array.from({ length: 27 }, (_, i) => point(i, outer, fade));
        const b = Array.from({ length: 27 }, (_, i) => point(26 - i, inner, fade));
        return [...a, ...b];
    };
    p.poly(strip(-2.3, 16), ink, fade);
    p.poly(strip(0, 12), ochre, fade);
    p.poly(strip(0, 8.2), gold, fade);
    p.poly(strip(0, 3.2), pale, fade);
    p.polyline(Array.from({ length: 14 }, (_, i) => point(i + 9, 0.7, fade)), cream, fade);
    if (t < 0.65) {
        const [x, y] = point(25, 0, fade);
        p.beam(x - 4, y + 1, x + 5, y - 3, 1, cream, fade);
    }
});
await recolor("butterfly_mote", "critters/butterfly", 128, 16, [64, 44], {
    palette, spots: ["#2c1e20", "#b87521", "#f2c14e", "#fff3c0"],
});

function transform(points, side, width, height, yOffset) {
    return points.map(([x, y]) => [x * side * width, y * height + yOffset]);
}

function wings(p, width, height, alpha, fall = 0) {
    const upper = [[1, 2], [5, -7], [16, -17], [26, -18], [24, -7], [19, 1], [11, 5]];
    const lower = [[1, 3], [10, 4], [17, 4], [21, 9], [17, 15], [11, 18], [5, 12]];
    for (const side of [-1, 1]) {
        const shape = (points) => transform(points, side, width, height, fall);
        p.poly(shape(upper), ink, alpha);
        p.poly(shape(lower), ink, alpha);
        p.poly(shape([[2, 1], [8, -8], [17, -15], [24, -16], [19, -6], [13, -1]]), fall ? ochre : gold, alpha);
        p.poly(shape([[3, 5], [12, 5], [18, 8], [15, 14], [9, 15]]), fall ? shadow : amber, alpha);
        p.polyline(shape([[2, 1], [9, -3], [18, -12], [24, -16]]), fall ? ochre : cream, alpha);
        p.polyline(shape([[3, 3], [12, 7], [16, 13]]), gold, alpha * 0.75);
        for (const [x, y] of shape([[21, -12], [19, -9], [14, 11]])) p.dot(x, y, pale, alpha);
    }
    p.line(0, -6 * height + fall, 0, 12 * height + fall, ink, alpha);
    p.line(0, -5 * height + fall, 0, 8 * height + fall, pale, alpha * 0.8);
}

function cross(p, x, y, radius, color, alpha) {
    p.poly([[x - radius, y], [x - 1, y - 1], [x, y - radius], [x + 1, y - 1],
        [x + radius, y], [x + 1, y + 1], [x, y + radius], [x - 1, y + 1]], color, alpha);
}

paint("butterfly_mark", 64, 12, (p, t) => {
    const alpha = Math.pow(1 - t, 0.7);
    const size = 0.52 + 0.45 * ease.outExpo(t);
    p.arc(0, 0, 13 + t * 11, 1, -0.2 + t, 2.05 + t, gold, alpha * 0.65);
    p.arc(0, 0, 13 + t * 11, 1, 2.95 + t, 5.1 + t, gold, alpha * 0.65);
    wings(p, size * 0.72, size * 0.7, alpha);
    cross(p, 0, -16 - t * 6, 4 * alpha, cream, alpha);
});

paint("tulip_burst", 64, 16, (p, t) => {
    const alpha = clamp((1 - t) * 2.8);
    const grow = ease.outExpo(clamp(t * 2.9 + 0.14));
    for (const [x, height, lean] of [[-12, 24, -4], [10, 30, 5], [0, 42, 1]]) {
        const y = 25 - height * grow;
        p.beam(x, 26, x + lean, y + 5, 3, ink, alpha);
        p.line(x + 1, 24, x + lean + 1, y + 6, ochre, alpha);
        p.poly([[x, y + 13], [x - 8, y + 9], [x - 4, y + 16], [x, y + 19]], shadow, alpha);
        const cx = x + lean, open = 3 + grow * 5;
        p.poly([[cx - open - 1, y - 9], [cx - 3, y - 6], [cx, y - 11], [cx + 4, y - 6],
            [cx + open, y - 9], [cx + open - 1, y + 1], [cx + 3, y + 6], [cx - 3, y + 6],
            [cx - open, y + 1]], ink, alpha);
        p.poly([[cx - open + 1, y - 6], [cx - 2, y - 2], [cx, y - 8], [cx + 3, y - 2],
            [cx + open - 2, y - 5], [cx + 3, y + 3], [cx - 2, y + 4]], gold, alpha);
        p.line(cx, y - 7, cx + 1, y + 2, pale, alpha);
        p.dot(cx + open + t * 5, y - t * 13, pale, alpha);
    }
});

paint("pollen_pulse", 32, 9, (p, t) => {
    const fade = (1 - t) ** 1.5;
    for (let i = 0; i < 7; i++) {
        const angle = TAU * i / 7 + 0.36;
        const r = 3 + (8 + i % 3) * ease.outCubic(t);
        const x = Math.cos(angle) * r, y = Math.sin(angle) * r - 3 * t;
        p.dot(x + 1, y + 1, ink, fade);
        p.dot(x, y, i % 2 ? gold : cream, fade);
    }
    cross(p, 0, 0, 3.5 * fade, pale, fade);
});

paint("blossom_open", 64, 18, (p, t) => {
    const size = ease.outExpo(clamp(0.1 + t * 2.6));
    const alpha = clamp((1 - t) * 3);
    wings(p, size, 0.85 + 0.15 * size, alpha);
    p.arc(0, 0, 11 + 16 * size, 1.2, -2.8, 1.1, gold, alpha * 0.7);
    p.arc(0, 0, 13 + 14 * size, 1.2, 1.6, 3.9, amber, alpha * 0.55);
    cross(p, 0, -10 - 14 * t, 5 * alpha, cream, alpha);
});

paint("blossom_close", 64, 13, (p, t) => {
    const alpha = clamp((1 - t) * 2.3);
    wings(p, Math.max(0.08, 1 - ease.inOutCubic(t)), 1 - 0.45 * t, alpha);
    cross(p, 0, -8 * t, (1 - t) * 7, pale, alpha);
    p.ring(0, 0, 19 * (1 - t) + 3, 1, gold, alpha * 0.6);
});

const shards = Array.from({ length: 26 }, (_, i) => {
    const random = new Rng(40 + i);
    return { x: random.range(-23, 23), y: random.range(-16, 14), dx: random.range(-13, 13),
        fall: random.range(12, 28), size: random.range(1.2, 3.6), angle: random.range(0, TAU) };
});

paint("blossom_fall", 64, 19, (p, t) => {
    if (t < 0.48) wings(p, 1 - t * 0.5, 1 - t * 0.3, 1 - t / 0.48, t * 9);
    for (const [i, shard] of shards.entries()) {
        const a = shard.angle + t * 7, size = shard.size * (1 - 0.65 * t);
        const x = shard.x + shard.dx * t, y = shard.y + shard.fall * t * t;
        const dx = Math.cos(a) * size, dy = Math.sin(a) * size;
        const alpha = clamp((1 - t) * 2);
        p.poly([[x - dx, y - dy], [x - dy * 0.4, y + dx * 0.4], [x + dx, y + dy],
            [x + dy * 0.35, y - dx * 0.35]], i % 3 ? ink : ochre, alpha);
        if (i % 3 === 0) p.line(x - dx, y - dy, x, y, gold, alpha * 0.9);
    }
});

paint("tulip_petal", 16, 8, (p, t) => {
    const width = 2 + 2 * Math.abs(Math.cos(t * TAU));
    p.poly([[-width, -2], [-width * 0.45, -5], [width, -2], [width * 0.55, 2], [0, 5]], ink, 1);
    p.poly([[-width + 1, -2], [-width * 0.35, -4], [width - 1, -2], [0, 3]], gold, 1);
    p.line(-width * 0.2, -3, 0, 1, pale, 0.8);
});

function fracture(p, t, accent) {
    const sweep = 8 + 48 * ease.outExpo(t * 0.85 + 0.12);
    const alpha = Math.pow(1 - t, 0.72);
    for (let i = 0; i < 10; i++) {
        const random = new Rng(90 + i);
        const angle = TAU * i / 10 + random.range(-0.15, 0.15);
        const tip = (r, skew = 0) => [Math.cos(angle + skew) * r, Math.sin(angle + skew) * r];
        const crack = [tip(sweep * 0.20, -0.09), tip(sweep * 0.45, 0.08), tip(sweep * 0.58, -0.025),
            tip(sweep * 0.74, 0.055), tip(sweep, -0.08)];
        p.poly([tip(sweep * 0.21, -0.07), tip(sweep * 0.57, -0.22), tip(sweep, -0.08),
            tip(sweep * 0.79, 0.065), tip(sweep * 0.62, 0.20)], ink, alpha * 0.91);
        p.polyline(crack, i % 3 ? gold : accent, alpha);
        p.line(...crack[2], ...tip(sweep * 0.75, 0.17), accent, alpha * 0.75);
        const [x, y] = tip(sweep + t * 5, 0.12);
        p.dot(x, y, pale, alpha);
    }
    p.arc(0, 0, sweep * 0.33, 2, t, t + Math.PI * 1.55, accent, alpha * 0.8);
    cross(p, 0, 0, (1 - t) * 12, cream, alpha);
}

paint("wither_life", 128, 16, (p, t) => fracture(p, t, red));
paint("wither_taint", 128, 16, (p, t) => fracture(p, t, violet));

mkdirSync(descriptions, { recursive: true });
for (const [id, { side, frames }] of effects) {
    const dir = join(textureRoot, id);
    mkdirSync(dir, { recursive: true });
    const textures = frames.map((rgba, i) => {
        const name = String(i).padStart(2, "0");
        writeFileSync(join(dir, `${name}.png`), encodePNG(side, side, rgba));
        return `madoka_magic:devotion/${id}/${name}`;
    });
    writeFileSync(join(descriptions, `${id}.json`), JSON.stringify({ textures }, null, 2) + "\n");
}

if (process.argv[3]) {
    const preview = resolve(process.argv[3]);
    mkdirSync(dirname(preview), { recursive: true });
    const tile = 128, columns = 5, rowHeight = tile + 16;
    const img = canvas(columns * tile, effects.size * rowHeight, [44, 41, 48, 255]);
    let row = 0;
    for (const [id, { side, frames }] of effects) {
        img.rect(0, row * rowHeight, img.w, 16, [23, 21, 28, 255]);
        img.text(id.replaceAll("_", " ").toUpperCase(), 5, row * rowHeight + 4, 1, [245, 219, 157]);
        for (let i = 0; i < columns; i++) {
            const frame = frames[Math.floor(i * (frames.length - 1) / (columns - 0.75))];
            const scale = tile / side;
            img.paste(upscale(frame, side, side, scale), tile, tile, i * tile, row * rowHeight + 16);
        }
        row++;
    }
    writeFileSync(preview, img.png());
}
console.log(`Wrote ${effects.size} effects and ${[...effects.values()].reduce((n, fx) => n + fx.frames.length, 0)} frames.`);
