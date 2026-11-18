// Usage: node tools/render-annegret.mjs <PixelFX checkout>
import { writeFileSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { pathToFileURL } from 'node:url';

if (!process.argv[2]) throw new Error('Provide the PixelFX checkout path.');
const load = path => import(pathToFileURL(join(resolve(process.argv[2]), path)).href);
const { createFrame, painterFor, composite, hex } = await load('src/core/index.js');
const { encodePNG } = await load('tools/lib/png.mjs');
const textures = new URL('../src/main/resources/assets/madoka_magic/textures/', import.meta.url);

function save(path, draw) {
    const frame = createFrame(32, 32);
    draw(painterFor(frame));
    writeFileSync(new URL(path, textures), encodePNG(32, 32, composite(frame, new Uint8ClampedArray(4096), null)));
}

const [ink, berry, sugar, cream, vanilla, caramel] = ['#251526', '#8d375a', '#ee85a7', '#fff2dd', '#e4bfa2', '#b6835d'].map(hex);
save('entity/annegret_mask.png', p => {
    p.poly([[1,4],[8,2],[13,3],[16,0],[19,3],[23,2],[30,4],[31,17],[27,24],[22,28],[16,31],[10,28],[4,24],[0,17]], ink);
    p.poly([[2,5],[8,3],[13,5],[16,2],[19,5],[23,3],[29,5],[30,17],[26,23],[21,27],[16,30],[11,27],[5,23],[1,17]], berry);
    p.poly([[3,6],[8,4],[13,6],[16,3],[19,6],[23,4],[28,6],[29,16],[25,22],[21,26],[16,29],[11,26],[6,22],[2,16]], sugar);
    p.poly([[5,7],[8,6],[13,8],[16,5],[19,8],[23,6],[26,7],[27,16],[23,22],[19,26],[16,27],[13,26],[9,22],[4,16]], vanilla);
    p.poly([[5,7],[8,7],[13,9],[16,6],[19,9],[23,7],[26,7],[26,15],[22,21],[18,24],[14,24],[10,21],[5,15]], cream);
    p.poly([[14,10],[16,7],[18,10],[16,13]], caramel);
    p.dot(16,9,sugar);
    p.polyline([[12,21],[14,22],[18,22],[20,21]], berry);
    for (const side of [-1, 1]) {
        const x = distance => 16 + distance * side;
        p.poly([[x(3),13],[x(8),10],[x(10),11],[x(9),15],[x(6),17],[x(4),16]], ink);
        p.line(x(6),13,x(8),12,vanilla);
        p.dot(x(8),12,cream);
        p.line(x(5),19,x(8),18,sugar);
        p.dot(x(3),25,caramel);
    }
});

save('particle/annegret_frosting_tile.png', p => {
    p.poly([[2,0],[29,0],[31,2],[31,29],[29,31],[2,31],[0,29],[0,2]], berry, 0.45);
    p.polyline([[2,0],[29,0],[31,2],[31,29],[29,31],[2,31],[0,29],[0,2],[2,0]], sugar);
    p.polyline([[3,3],[9,3],[14,8],[12,12],[17,16],[14,22],[18,27],[18,30]], cream, 0.92);
    p.polyline([[14,8],[18,9],[21,6],[23,1]], cream, 0.68);
    p.polyline([[17,16],[23,15],[26,19],[30,19]], sugar, 0.96);
    p.polyline([[14,22],[8,23],[6,28],[2,28]], sugar, 0.96);
    for (const [x,y] of [[4,9],[25,6],[8,16],[25,24],[14,27]]) {
        p.line(x-1,y,x+1,y,vanilla,0.75);
        p.line(x,y-1,x,y+1,cream,0.75);
    }
});
