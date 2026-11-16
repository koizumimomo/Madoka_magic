// Usage: node tools/render-spell-gui.mjs <pixelfx checkout> [preview PNG]
import { mkdirSync, writeFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

if (!process.argv[2]) throw new Error('Provide a PixelFX checkout.');
const upstream = resolve(process.argv[2]);
const load = path => import(pathToFileURL(join(upstream, path)).href);
const { createFrame, painterFor, composite, hex } = await load('src/core/index.js');
const { encodePNG } = await load('tools/lib/png.mjs');
const { upscale } = await load('tools/lib/fxlib.mjs');
const gui = fileURLToPath(new URL('../src/main/resources/assets/madoka_magic/textures/gui/', import.meta.url));
const side = 32;

const ink = hex('#15131f');
const warm = ['#62402b', '#af642f', '#f2ba4e', '#fff0b3'].map(hex);
const amber = ['#593433', '#ba6338', '#eda24e', '#fff0ba'].map(hex);
const blue = ['#33406c', '#536eb1', '#99c3e8', '#e0f4ff'].map(hex);
const violet = ['#4b315d', '#815483', '#c78cb7', '#ffd8db'].map(hex);

const polygon = (p, points, color) => p.poly(points, color);
function form(p, points, color, rim = ink) {
    polygon(p, points, color);
    p.polyline([...points, points[0]], rim);
}
function stroke(p, points, color, width = 2) {
    for (let i = 1; i < points.length; i++) {
        p.beam(...points[i - 1], ...points[i], width + 2, ink);
    }
    for (let i = 1; i < points.length; i++) {
        p.beam(...points[i - 1], ...points[i], width, color);
    }
}
function gem(p, x, y, r, colors) {
    form(p, [[x, y-r], [x+r, y], [x, y+r], [x-r, y]], colors[2]);
    polygon(p, [[x, y-r+1], [x, y+1], [x-r+1, y]], colors[3]);
}
function glint(p, x, y, size, color) {
    polygon(p, [[x, y-size], [x+1, y-1], [x+size, y], [x+1, y+1], [x, y+size],
        [x-1, y+1], [x-size, y], [x-1, y-1]], color);
}
function tulip(p, x, y, colors) {
    stroke(p, [[x, y+5], [x-1, y+12]], hex('#8b965b'));
    form(p, [[x, y+10], [x-7, y+5], [x-4, y+12], [x, y+13]], hex('#637645'));
    form(p, [[x-8, y-6], [x-3, y-3], [x, y-8], [x+3, y-3], [x+8, y-6], [x+6, y+3],
        [x+2, y+7], [x-3, y+7], [x-7, y+3]], colors[1]);
    polygon(p, [[x, y-6], [x+3, y], [x+6, y-3], [x+4, y+3], [x, y+6], [x-3, y+1]], colors[2]);
    p.line(x, y-4, x+2, y+1, colors[3]);
}
function butterfly(p, x, y, scale, colors) {
    for (const direction of [-1, 1]) {
        const wing = points => points.map(([dx, dy]) => [x+dx*scale*direction, y+dy*scale]);
        form(p, wing([[0,0],[4,-6],[11,-8],[10,-2],[6,2],[9,5],[6,8],[2,6]]), colors[1]);
        polygon(p, wing([[1,0],[5,-5],[10,-7],[8,-1],[4,2],[7,5],[5,6],[2,3]]), colors[2]);
        p.polyline(wing([[2,0],[5,-4],[8,-6]]), colors[3]);
    }
    stroke(p, [[x,y-2*scale],[x,y+5*scale]], colors[3], 1);
}
function thorn(p, points) {
    stroke(p, points, blue[1]);
    points.slice(1,-1).forEach(([x,y], index) => {
        const s = index % 2 === 0 ? 1 : -1;
        form(p, [[x-2,y+1],[x+4*s,y-5],[x+2*s,y+2]], blue[2]);
    });
    p.polyline(points, blue[2]);
}
function bee(p, x, y) {
    form(p, [[x-4,y-2],[x-5,y-6],[x-1,y-5],[x,y-2],[x+3,y-6],[x+6,y-5],[x+4,y-1]], blue[3]);
    form(p, [[x-6,y],[x-2,y-3],[x+3,y-2],[x+5,y+1],[x+3,y+4],[x-2,y+4],[x-5,y+2]], amber[2]);
    p.line(x-1,y-1,x-2,y+3,amber[0]);
    p.line(x+2,y-1,x+1,y+3,amber[0]);
}

const icons = {
    butterfly_slash(p) {
        p.arc(12, 11, 18, u => 6 * Math.sin(u * Math.PI), -0.42, 2.4, warm[0]);
        p.arc(12, 11, 17, u => 4 * Math.sin(u * Math.PI), -0.32, 2.22, warm[2]);
        p.arc(12, 11, 17, 1.2, 0.13, 1.74, warm[3]);
        butterfly(p, 12, 12, .82, warm);
    },
    tulip_burst(p) {
        for (const [x,y] of [[4,10],[25,7],[28,15],[7,22],[23,24]]) glint(p,x,y,2,warm[2]);
        tulip(p,16,14,warm);
        glint(p,10,5,3,warm[3]);
    },
    pollen(p) {
        p.arc(16,16,12,2,0.25,5.9,warm[1]);
        for (const [x,y,r] of [[8,11,2],[24,8,2],[14,5,1],[26,18,3],[7,23,3],[18,26,2],[13,18,2],[18,12,4]]) gem(p,x,y,r,warm);
    },
    blossom(p) {
        for (let i=0;i<6;i++) {
            const a=i*Math.PI/3;
            const petal = points => points.map(([x,y]) => [16+x*Math.cos(a)-y*Math.sin(a),16+x*Math.sin(a)+y*Math.cos(a)]);
            form(p,petal([[0,0],[-4,-6],[-3,-10],[0,-13],[3,-10],[4,-6]]),warm[2]);
            p.polyline(petal([[0,-3],[0,-9],[-1,-10]]),warm[3]);
        }
        gem(p,16,16,5,warm);
    },
    wither(p) {
        p.arc(15,16,13,2.2,0.1,5.5,violet[1]);
        stroke(p,[[19,6],[14,13],[15,19],[11,25]],violet[1]);
        form(p,[[14,14],[8,10],[6,6],[12,6],[17,9],[17,13]],violet[2]);
        form(p,[[16,18],[22,18],[26,14],[25,20],[21,24],[16,23]],violet[1]);
        glint(p,23,6,3,violet[3]);
    },
    devotion_wing(p) { butterfly(p,16,14,1.05,warm); gem(p,16,23,4,violet); glint(p,25,8,2,warm[3]); },
    bee_sting(p) {
        stroke(p,[[6,25],[13,20],[21,11],[26,5]],amber[2],3);
        form(p,[[15,17],[16,10],[22,6],[28,3],[25,11],[21,16]],amber[3]);
        form(p,[[9,22],[9,15],[14,17],[15,21]],amber[1]);
        glint(p,8,9,3,amber[3]);
    },
    swarm_call(p) { bee(p,10,12); bee(p,22,11); bee(p,17,23); glint(p,25,24,2,amber[2]); },
    honey(p) {
        form(p,[[7,12],[16,6],[25,12],[25,23],[16,28],[7,23]],amber[1]);
        form(p,[[10,13],[16,9],[22,13],[22,21],[16,25],[10,21]],amber[2],amber[0]);
        form(p,[[16,11],[20,17],[20,20],[16,22],[12,20],[12,17]],amber[3],amber[1]);
        glint(p,25,7,3,amber[3]);
    },
    injection(p) {
        form(p,[[7,19],[17,9],[23,15],[13,25]],blue[1]);
        form(p,[[9,19],[17,11],[21,15],[13,23]],amber[2],ink);
        stroke(p,[[20,12],[27,5]],blue[3],1);
        stroke(p,[[6,19],[13,26]],blue[2]);
        p.line(14,18,17,15,amber[3]);
    },
    illusion(p) {
        p.ring(16,16,12,5,(v,a)=>v>.65 ? violet[0] : Math.sin(a*3+v*5)>0 ? violet[2] : violet[1]);
        form(p,[[9,16],[14,10],[23,11],[20,20],[13,22]],violet[0]);
        p.arc(16,16,7,2,-2.8,0.8,violet[3]);
        gem(p,19,16,3,amber);
        glint(p,7,7,2,violet[3]);
    },
    dandelion_drift(p) {
        stroke(p,[[10,26],[12,19],[14,14]],hex('#7c905c'));
        p.circle(14,13,7,blue[0]);
        for(const [x,y] of [[9,11],[11,7],[16,7],[19,10],[19,15],[14,18],[10,16],[23,7],[26,13],[23,18],[28,8]]) {
            p.line(x,y,x-2,y+3,blue[2]);
            p.line(x-1,y,x+1,y,blue[3]);
        }
        gem(p,14,13,3,amber);
    },
    selfish_eye(p) {
        form(p,[[3,16],[9,10],[15,8],[22,10],[29,16],[23,22],[16,24],[9,22]],amber[1]);
        p.circle(16,16,6,amber[0]); p.ring(16,16,5,2,amber[2]);
        gem(p,16,16,3,amber); glint(p,22,11,2,amber[3]);
    },
    thorn_embrace(p) {
        thorn(p,[[7,23],[5,15],[9,8],[18,6],[24,11],[25,19],[21,25],[12,25],[10,19],[15,14]]);
        gem(p,16,17,4,blue);
    },
    unpayable_promise(p) {
        stroke(p,[[7,19],[10,12],[14,12],[18,20],[22,20],[25,13]],blue[2]);
        gem(p,7,19,5,blue); gem(p,25,13,5,blue);
        glint(p,16,16,3,blue[3]);
    },
    redemption_blade(p) {
        form(p,[[10,20],[13,14],[27,4],[23,14],[15,23]],blue[2]);
        polygon(p,[[13,20],[18,14],[25,6],[21,14]],blue[3]);
        stroke(p,[[8,18],[15,25]],blue[1]); stroke(p,[[11,22],[6,27]],blue[2]);
        glint(p,21,9,3,blue[3]);
    },
    sin_mark(p) {
        p.ring(16,16,12,2,blue[0]); p.ring(16,16,10,1.6,blue[2]);
        for (const [x,y] of [[16,4],[4,16],[27,16],[16,27]]) glint(p,x,y,2,blue[2]);
        form(p,[[16,6],[23,16],[16,26],[9,16]],blue[0]);
        p.polyline([[16,8],[16,23]],blue[3]); p.line(11,17,21,17,blue[3]);
        gem(p,16,14,3,blue);
    },
    obsession(p) {
        stroke(p,[[21,7],[26,12],[25,19],[20,24],[11,24],[6,20],[5,14],[9,10],[14,10],[18,14],[16,19],[12,18]],violet[2]);
        gem(p,19,8,4,violet); gem(p,12,16,4,violet);
    },
    ember(p) {
        form(p,[[11,26],[6,20],[10,13],[11,19],[16,10],[16,5],[22,11],[23,17],[27,14],[26,22],[21,27]],amber[1]);
        polygon(p,[[13,25],[10,21],[14,18],[16,21],[20,12],[22,20],[23,23],[20,26]],amber[2]);
        glint(p,19,23,3,amber[3]); glint(p,6,10,2,amber[2]);
    },
};

function pixels(width, height, draw, background = null) {
    const frame = createFrame(width,height);
    draw(painterFor(frame));
    return composite(frame,new Uint8ClampedArray(width*height*4),background);
}

const rendered = Object.entries(icons).map(([name, draw]) => [name, pixels(side,side,draw)]);
const wheelSize = 192;
const center = wheelSize/2;
const ringColors = ['#251c35','#342645','#523b60','#796084','#c5a5c7','#efd9e6'].map(hex);
const wheel = pixels(wheelSize,wheelSize,p => {
    p.ring(center,center,94,47,(v,a) => {
        if (v<.045 || v>.971) return ringColors[5];
        if (v<.085 || v>.925) return ringColors[3];
        if (v>.86 || v<.125) return ringColors[2];
        if (v>.79 && Math.cos(a*48)>.88) return ringColors[4];
        return v>.72 ? ringColors[1] : ringColors[0];
    });
    for(let i=0;i<12;i++) {
        const a=i*Math.PI/6;
        glint(p,center+Math.cos(a)*54,center+Math.sin(a)*54,i%3===0 ? 3 : 2,ringColors[4]);
    }
});

mkdirSync(join(gui,'spells'),{recursive:true});
for (const [name, rgba] of rendered) writeFileSync(join(gui,'spells',name+'.png'),encodePNG(side,side,rgba));
writeFileSync(join(gui,'spell_wheel.png'),encodePNG(wheelSize,wheelSize,wheel));

if (process.argv[3]) {
    const w=7*40, h=3*40;
    const frame=createFrame(w,h);
    const painter=painterFor(frame);
    painter.rect(0,0,w,h,hex('#202033'));
    let index=0;
    for (const [,draw] of Object.entries(icons)) {
        const x=4+(index%7)*40, y=4+Math.floor(index/7)*40;
        painter.place(x,y);
        painter.rect(-2,-2,36,36,hex('#302a40'));
        draw(painter);
        index++;
    }
    const rgba = composite(frame,new Uint8ClampedArray(w*h*4),null);
    writeFileSync(resolve(process.argv[3]),encodePNG(w*3,h*3,upscale(rgba,w,h,3)));
}
