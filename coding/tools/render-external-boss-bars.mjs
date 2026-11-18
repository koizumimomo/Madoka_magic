// Usage: node tools/render-external-boss-bars.mjs <PixelFX checkout> [preview PNG]
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const upstream = resolve(process.argv[2]);
const load = path => import(pathToFileURL(join(upstream, path)).href);
const { createFrame, composite, painterFor, hex } = await load('src/core/index.js');
const { encodePNG } = await load('tools/lib/png.mjs');
const { canvas, upscale } = await load('tools/lib/fxlib.mjs');
const assets = resolve(dirname(fileURLToPath(import.meta.url)), '../src/main/resources/assets/madoka_magic');
const styles = JSON.parse(readFileSync(join(assets, 'visuals/witch_styles.json'), 'utf8'));
const themes = ['netherite_monstrosity', 'ignis', 'ender_guardian', 'the_harbinger', 'the_leviathan', 'amethyst_crab',
    'ancient_remnant', 'maledictus', 'scylla', 'brunhilde', 'sieglinde', 'annegret'];
const output = join(assets, 'textures/gui/bossbars');
mkdirSync(output, { recursive: true });
const preview = canvas(600, 12 * 82, [30, 25, 36, 255]);

for (const [index, theme] of themes.entries()) {
    const frame = createFrame(182, 36);
    const p = painterFor(frame);
    const { crown, primary, secondary } = styles[theme];
    const pale = hex(crown), mid = hex(primary), dark = hex(secondary), ink = hex('#211927');
    // Frame and its hooks leave the actual health fill transparent.
    for (const side of [-1, 1]) {
        const mirror = points => points.map(([x, y]) => [side < 0 ? x : 181 - x, y]);
        p.poly(mirror([[0, 11], [7, 4], [14, 9], [14, 16], [7, 23], [0, 16]]), ink);
        p.polyline(mirror([[1, 11], [7, 6], [12, 10], [12, 16], [7, 21], [2, 16], [1, 11]]), mid);
        p.polyline(mirror([[3, 11], [7, 8], [10, 11]]), pale);
        p.polyline(mirror([[13, 9], [37, 9], [43, 6], [62, 8], [82, 8]]), dark);
        p.polyline(mirror([[14, 8], [35, 8], [42, 5], [61, 7], [81, 7]]), pale);
        p.polyline(mirror([[13, 16], [41, 16], [47, 19], [66, 17], [83, 17]]), mid);
        p.polyline(mirror([[18, 18], [39, 18], [45, 21], [56, 21]]), dark);
        p.poly(mirror([[29, 6], [31, 3], [33, 6], [31, 9]]), mid);
        p.poly(mirror([[49, 17], [51, 20], [50, 24], [48, 21]]), pale);
        p.poly(mirror([[7, 11], [9, 13], [7, 16], [5, 13]]), pale);
    }
    p.poly([[81, 7], [86, 2], [95, 2], [101, 8], [99, 19], [92, 26], [88, 26], [81, 18]], ink);
    p.polyline([[82, 7], [87, 3], [94, 3], [100, 8], [98, 18], [91, 24], [84, 18], [82, 7]], mid);
    p.place(91, 12);

    switch (theme) {
        case 'netherite_monstrosity':
            p.poly([[-7,-5],[7,-5],[5,-1],[2,1],[2,6],[-2,6],[-2,1],[-6,0]], mid);
            p.line(-7,-5,7,-5,pale); p.line(-4,6,4,6,pale); p.poly([[-1,-4],[1,-2],[0,1],[-2,-1]],pale);
            break;
        case 'ignis':
            p.poly([[-6,3],[-6,-1],[-2,-4],[-2,-8],[2,-4],[4,-5],[6,0],[5,4],[0,8]],mid);
            p.poly([[-3,3],[-2,-1],[0,-4],[1,1],[3,2],[1,6],[-1,7]],pale);
            break;
        case 'ender_guardian':
            p.poly([[-7,-2],[-3,-6],[3,-6],[7,-2],[5,5],[0,8],[-5,5]],dark);
            p.polyline([[-6,-2],[0,-5],[6,-2],[0,3],[-6,-2]],pale); p.rect(-1,-3,3,5,mid); p.dot(0,-2,pale);
            break;
        case 'the_harbinger':
            for (let i=0;i<8;i++) { const a=i*Math.PI/4; p.rect(Math.round(Math.cos(a)*6)-1, Math.round(Math.sin(a)*6)-1,3,3,mid); }
            p.poly([[-5,-3],[-2,-6],[3,-5],[6,-1],[4,4],[0,6],[-5,3]],mid);
            p.rect(-3,-3,7,7,ink); p.rect(-1,-2,3,5,pale); p.line(-3,6,3,6,pale);
            break;
        case 'the_leviathan':
            p.polyline([[-7,-4],[-4,-5],[-4,2],[0,6],[4,2],[4,-5],[7,-4]],pale);
            p.poly([[-2,-6],[2,-6],[3,-1],[0,4],[-3,-1]],mid); p.line(0,-5,0,0,pale);
            p.polyline([[-6,5],[-3,7],[0,6],[3,8],[6,6]],mid);
            break;
        case 'amethyst_crab':
            p.poly([[-5,1],[-3,-6],[0,-8],[3,-6],[5,1],[0,8]],mid);
            p.polyline([[-3,-5],[-1,0],[0,6],[2,-3],[0,-6]],pale);
            p.polyline([[-5,0],[-7,3],[-7,6]],pale); p.polyline([[5,0],[7,3],[7,6]],pale);
            break;
        case 'ancient_remnant':
            p.poly([[-7,6],[0,-7],[7,6]],mid); p.polyline([[-6,5],[0,-5],[0,6]],pale);
            p.polyline([[-6,3],[6,3]],dark); p.line(-3,0,3,0,dark); p.rect(-2,4,4,3,ink);
            break;
        case 'maledictus':
            p.polyline([[-5,7],[3,-6],[7,-3],[7,0]],pale);
            p.poly([[2,-6],[7,-5],[8,-2],[5,-3],[1,-3],[-4,1],[-2,-3]],mid);
            p.polyline([[-6,-4],[-4,0],[-6,2],[-2,5]],mid);
            break;
        case 'scylla':
            p.polyline([[0,-7],[0,6],[-5,2],[-6,-2]],pale); p.polyline([[0,6],[5,2],[6,-2]],pale);
            p.polyline([[-7,-3],[-5,0],[-3,-2]],mid); p.polyline([[3,-2],[5,0],[7,-3]],mid);
            p.poly([[-3,-7],[3,-7],[2,-3],[-2,-3]],mid); p.line(-3,7,3,7,pale);
            break;
        case 'brunhilde':
            p.polyline([[-6,-6],[-7,-2],[-3,3],[0,7],[3,3],[7,-2],[6,-6]],pale);
            p.poly([[-5,-2],[-1,0],[0,5],[1,0],[5,-2],[3,4],[0,8],[-3,4]],mid);
            p.dot(-3,-3,pale); p.dot(3,-3,pale); p.line(-6,-6,-3,-4,mid); p.line(6,-6,3,-4,mid);
            break;
        case 'sieglinde':
            p.poly([[-5,-6],[1,-7],[5,-3],[4,4],[0,8],[-4,3]],mid);
            p.polyline([[-4,-5],[0,-3],[-2,0],[2,3],[0,6]],ink);
            p.polyline([[2,-5],[3,-2],[2,0]],pale); p.poly([[-6,2],[-4,3],[-6,6],[-7,4]],pale);
            break;
        case 'annegret':
            p.poly([[-7,-3],[-3,-5],[0,-1],[3,-5],[7,-3],[6,3],[2,2],[0,0],[-2,2],[-6,3]],mid);
            p.polyline([[-6,-3],[-3,-3],[-1,-1]],pale); p.polyline([[6,-3],[3,-3],[1,-1]],pale);
            p.poly([[-2,1],[2,1],[3,5],[0,8],[-3,5]],pale); p.line(-1,3,1,5,mid);
            break;
    }
    p.place(0, 0);
    const pixels = composite(frame, new Uint8ClampedArray(182 * 36 * 4), null);
    writeFileSync(join(output, theme + '.png'), encodePNG(182, 36, pixels));
    preview.text(theme.toUpperCase().replaceAll('_', ' '), 9, index * 82 + 4, 1);
    preview.rect(38, index * 82 + 30, 500, 12, [72, 46, 76, 255]);
    preview.rect(38, index * 82 + 30, 343, 12, [...[primary.slice(1,3),primary.slice(3,5),primary.slice(5,7)].map(value=>parseInt(value,16)),255]);
    preview.paste(upscale(pixels, 182, 36, 3), 546, 108, 9, index * 82 + 7);
}

if (process.argv[3]) {
    const target = resolve(process.argv[3]);
    mkdirSync(dirname(target), { recursive: true });
    writeFileSync(target, preview.png());
}
