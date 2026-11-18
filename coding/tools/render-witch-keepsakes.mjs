// Usage: node tools/render-witch-keepsakes.mjs <PixelFX checkout> [contact sheet PNG]
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

if (!process.argv[2]) throw new Error('Provide the PixelFX checkout path.');
const load = name => import(pathToFileURL(join(resolve(process.argv[2]), name)).href);
const { createFrame, painterFor, composite, hex } = await load('src/core/index.js');
const { encodePNG } = await load('tools/lib/png.mjs');
const { canvas, upscale } = await load('tools/lib/fxlib.mjs');
const assets = resolve(dirname(fileURLToPath(import.meta.url)), '../src/main/resources/assets/madoka_magic');
const styles = JSON.parse(readFileSync(join(assets, 'visuals/witch_styles.json'), 'utf8'));
const ink = hex('#1d1426');
const gold = hex('#e4b982');
const glint = hex('#fff0cf');
const size = 32;

function star(p, x, y, r, color) {
  p.poly([[x,y-r],[x+1,y-1],[x+r,y],[x+1,y+1],[x,y+r],[x-1,y+1],[x-r,y],[x-1,y-1]],color);
}

function lines(p, paths, color) {
  for (const path of paths) p.polyline(path, color);
}

function crystal(p, x, base, width, height, color, light) {
  const tip = base - height;
  p.poly([[x,tip],[x+width,tip+4],[x+width,base-2],[x,base],[x-width,base-2],[x-width,tip+4]],color);
  p.poly([[x,tip],[x,base-1],[x-width+1,base-3],[x-width+1,tip+4]],light);
}

function arc(p, radius, start, end, color, width = 1.5) {
  p.arc(0,0,radius,width,start,end,color);
}

const motifs = {
  sacrifice(p,c,l) {
    for (const side of [-1,1]) {
      const wing = points => points.map(([x,y])=>[side*x,y]);
      p.poly(wing([[1,0],[3,-5],[9,-7],[8,-2],[6,1],[7,5],[4,8],[2,4]]), c);
      p.poly(wing([[2,-1],[5,-5],[8,-6],[6,-1],[3,2]]), l);
      p.line(3*side,3,5*side,6,gold);
    }
    p.line(0,-4,0,5,l);
  },
  take(p,c,l) {
    const cell = [[0,-9],[8,-4],[8,4],[0,9],[-8,4],[-8,-4]];
    p.poly(cell,c);
    p.poly([[0,-6],[5,-3],[5,3],[0,6],[-5,3],[-5,-3]],ink);
    lines(p,[[[-4,0],[-2,2],[2,2],[4,0]],[[-3,-3],[-2,-4]],[[2,-4],[3,-3]]],l);
    p.line(-5,-5,0,-8,gold);
  },
  atonement(p,c,l) {
    lines(p,[[[-1,-7],[-8,-7],[-8,7],[-2,7]],[[3,-7],[8,-7],[8,0]],[[6,3],[8,3],[8,7],[2,7]]],c);
    lines(p,[[[-7,-5],[-7,6]],[[-1,-9],[1,-5],[-1,-2],[3,0],[0,4],[3,8]]],l);
    p.poly([[-5,3],[-4,0],[-2,0],[-1,5],[-5,5]],c);
    p.poly([[2,5],[3,0],[5,0],[6,5]],c);
  },
  kriemhild_gretchen(p,c,l) {
    for (let i=0;i<6;i++) {
      const a = Math.PI*i/3;
      const x = Math.round(Math.cos(a)*6), y = Math.round(Math.sin(a)*7);
      p.line(x,y,Math.round(x*.48),Math.round(y*.48),c);
    }
    star(p,0,0,5,l);
    p.arc(0,2,7,1.5,.2,Math.PI-.2,c);
    p.dot(-7,-5,gold); p.dot(7,-5,gold);
  },
  homulilly(p,c,l) {
    arc(p,7,-Math.PI*.9,Math.PI*.9,c);
    p.poly([[-7,-9],[-4,-5],[-1,-8],[2,-5],[6,-9],[5,-2],[-6,-2]],c);
    lines(p,[[[-5,1],[-5,5],[5,5],[5,1]],[[-3,2],[3,2]],[[-2,1],[-2,5]],[[2,1],[2,5]]],l);
    p.dot(0,8,gold);
  },
  candeloro(p,c,l) {
    p.poly([[-8,-2],[5,-2],[4,3],[1,6],[-4,6],[-7,3]],c);
    lines(p,[[[5,-1],[9,-1],[9,2],[5,4]],[[-9,7],[7,7]],[[-6,-1],[3,-1]]],l);
    p.poly([[-5,-8],[0,-6],[5,-8],[4,-4],[0,-5],[-4,-4]],l);
  },
  oktavia(p,c,l) {
    arc(p,7,0,Math.PI*2,c,2);
    lines(p,[[[0,-7],[0,6]],[[-6,-3],[6,3]],[[-6,3],[6,-3]]],c);
    p.circle(-2,3,2,l); p.line(0,3,0,-5,l);
    p.poly([[0,-6],[5,-4],[5,-2],[0,-4]],l);
  },
  ophelia(p,c,l) {
    p.poly([[1,-9],[5,-4],[4,-1],[0,0],[-2,-3]],l);
    p.rect(-1,1,4,5,c); p.line(-5,7,6,7,l);
    lines(p,[[[-8,4],[-8,0],[-6,-4],[-3,-1]],[[-8,4],[-4,6]],[[6,-1],[8,2],[7,6]]],c);
  },
  walpurgisnacht(p,c,l) {
    arc(p,7,0,Math.PI*2,c,2);
    for (const [x,y] of [[-8,0],[8,0],[0,-8],[0,8],[-6,-6],[6,6],[-6,6],[6,-6]]) p.rect(x-1,y-1,3,3,c);
    p.poly([[-4,-5],[4,-5],[5,0],[2,5],[-2,5],[-5,0]],l);
    lines(p,[[[-3,-2],[-1,0]],[[3,-2],[1,0]],[[-2,3],[2,3]]],ink);
  },
  wither(p,c,l) {
    p.line(0,1,-1,9,c); p.line(-1,5,-5,3,c); p.line(-1,6,4,4,c);
    p.poly([[0,-8],[5,-5],[6,0],[2,3],[-3,3],[-6,0],[-5,-5]],c);
    p.poly([[-3,-4],[1,-6],[4,-3],[2,0],[-2,1],[-3,-1],[1,-2],[0,-3]],l);
    p.dot(1,-2,ink);
  },
  gluttony(p,c,l) {
    p.poly([[-8,4],[-8,-3],[-4,-7],[4,-8],[8,-4],[9,3],[5,7],[-5,7]],c);
    lines(p,[[[-7,1],[-3,-4],[2,-4],[7,1],[-7,1]],[[-6,4],[0,6],[6,3]]],l);
    p.dot(-4,-2,ink); p.dot(4,-2,ink);
  },
  brunhilde(p,c,l) {
    p.poly([[-9,-3],[-6,-7],[5,-7],[9,-3],[7,0],[-7,0]],c);
    p.poly([[-7,-1],[-2,-1],[-5,7]],l);
    p.poly([[-3,-2],[3,-2],[0,9]],c);
    p.poly([[3,-1],[7,-1],[5,7]],l);
    lines(p,[[[-4,-7],[-1,-4],[3,-6]],[[0,-2],[0,4]]],l);
  },
  sieglinde(p,c,l) {
    p.poly([[-8,-4],[-6,-7],[-3,-7],[0,-4],[3,-7],[6,-7],[8,-4],[7,0],[0,7],[-7,0]],c);
    lines(p,[[[-6,-4],[-4,-6],[-2,-5]],[[3,-5],[5,-6],[7,-4]],[[-1,-6],[2,-2],[-2,1],[1,4],[-1,7]]],l);
    p.dot(-4,4,l); p.dot(4,4,l);
  },
  annegret(p,c,l) {
    p.poly([[-8,-2],[4,-5],[8,-2],[8,5],[-8,7]],c);
    p.poly([[-8,-2],[4,-5],[8,-2],[6,0],[-8,3]],l);
    p.line(-6,4,7,2,gold);
    p.circle(-1,-5,2,c); p.dot(-2,-6,l);
    p.polyline([[-7,8],[7,7],[9,5]],l);
  },
  netherite_monstrosity(p,c,l) {
    p.poly([[-9,-5],[9,-5],[6,-1],[3,0],[2,4],[6,5],[6,7],[-6,7],[-6,5],[-2,4],[-3,0],[-6,-1]],c);
    p.line(-8,-5,8,-5,l);
    star(p,0,-5,3,l); p.line(-3,5,3,5,gold);
  },
  ignis(p,c,l) {
    p.poly([[-7,5],[-8,0],[-5,-4],[-4,0],[0,-4],[1,-9],[5,-5],[5,-1],[8,-4],[8,3],[4,7],[-3,8]],c);
    p.poly([[-4,4],[-2,0],[0,2],[3,-2],[5,3],[3,6],[-1,6]],l);
    p.dot(0,4,gold);
  },
  ender_guardian(p,c,l) {
    lines(p,[[[-8,7],[-8,-3],[-5,-7],[0,-9],[5,-7],[8,-3],[8,7],[-8,7]],[[-5,6],[-5,-2],[0,-6],[5,-2],[5,6]]],c);
    p.line(0,-5,0,6,l);
    lines(p,[[[-8,-1],[7,5]],[[-7,5],[8,-1]]],l);
    p.rect(-1,1,3,3,gold);
  },
  the_harbinger(p,c,l) {
    arc(p,6,-2.5,2.6,c,2);
    p.circle(-2,0,4,ink);
    lines(p,[[[-9,4],[-3,1],[1,0]],[[3,-1],[7,-3],[9,-5]]],l);
    star(p,-5,-5,2,l); p.dot(7,4,gold);
  },
  the_leviathan(p,c,l) {
    arc(p,7,-2.9,1.9,c,2);
    arc(p,4,-.5,4.1,l,2);
    p.polyline([[1,2],[-2,2],[-2,0],[0,-1],[1,0]],c);
    p.dot(-7,4,l); p.dot(5,7,gold);
  },
  amethyst_crab(p,c,l) {
    crystal(p,-6,5,2,9,c,l);
    crystal(p,6,6,2,10,c,l);
    crystal(p,0,7,3,16,c,l);
    p.dot(-5,-5,gold); p.dot(6,-6,l);
  },
  ancient_remnant(p,c,l) {
    p.rect(-8,-6,16,2,l); p.rect(-8,6,16,2,c);
    p.rect(-6,-3,3,8,c); p.rect(3,-3,3,8,c);
    p.poly([[-2,-3],[2,-3],[1,0],[-1,0]],l);
    p.poly([[-2,4],[0,1],[2,4]],l); p.dot(0,1,gold);
  },
  maledictus(p,c,l) {
    p.poly([[-6,5],[-4,-3],[1,-8],[5,-8],[5,-3],[1,2],[-3,5]],c);
    p.line(-7,7,4,-7,l);
    p.line(0,6,8,6,c); p.line(4,0,4,8,l);
    lines(p,[[[1,3],[4,2],[7,3]],[[0,5],[2,5]],[[6,5],[8,5]]],l);
  },
  scylla(p,c,l) {
    p.poly([[-7,4],[-9,0],[-7,-5],[-2,-8],[4,-7],[8,-3],[7,2],[3,5],[-1,6]],c);
    lines(p,[[[-6,1],[-6,-3],[-2,-6],[3,-5],[5,-2],[3,1],[0,1],[-1,-1]],[[-8,6],[-4,8],[0,7],[4,8],[8,6]]],l);
    p.dot(2,-3,gold);
  },
  unknown(p,c,l) {
    star(p,0,0,7,c); star(p,0,0,4,l);
    p.dot(-7,-4,c); p.dot(7,4,c);
  },
};

function pixels(w, h, draw) {
  const frame = createFrame(w,h);
  const painter = painterFor(frame);
  draw(painter);
  return composite(frame,new Uint8ClampedArray(w*h*4),null);
}

function write(path, contents) {
  const target = join(assets,path);
  mkdirSync(dirname(target),{recursive:true});
  writeFileSync(target,contents);
}

function json(path, value) {
  write(path, JSON.stringify(value,null,2)+'\n');
}

function seal(theme) {
  const style = styles[theme] ?? {crown:'#dfb7e3',primary:'#aa75bc',secondary:'#4e365b'};
  const [light,color,shade] = [style.crown,style.primary,style.secondary].map(hex);
  return pixels(size,size,p=>{
    p.place(16,16);
    p.poly([[-14,0],[-11,-7],[-6,-10],[0,-15],[6,-10],[11,-7],[14,0],[11,7],[6,10],[0,15],[-6,10],[-11,7]],ink);
    p.poly([[-12,0],[-10,-5],[-5,-9],[0,-13],[5,-9],[10,-5],[12,0],[10,5],[5,9],[0,13],[-5,9],[-10,5]],shade);
    lines(p,[[[-12,0],[-10,-5],[-5,-9],[0,-13],[5,-9],[10,-5],[12,0]],[[12,0],[10,5],[5,9],[0,13],[-5,9],[-10,5],[-12,0]]],color);
    p.poly([[-10,0],[-8,-6],[-4,-9],[0,-10],[4,-9],[8,-6],[10,0],[8,6],[4,9],[0,10],[-4,9],[-8,6]],ink);
    motifs[theme](p,color,light);
    star(p,0,-12,2,light); star(p,0,12,2,color);
    p.dot(-12,0,gold); p.dot(12,0,gold);
  });
}

const themes=Object.keys(motifs).filter(theme=>theme!=='unknown').sort();
const iconModel = (texture) => ({parent:'minecraft:item/generated',textures:{layer0:'madoka_magic:item/'+texture}});
const overrides = [];
const preview = canvas(768,556,[28,24,37,255]);
for (const [index,theme] of ['unknown',...themes].entries()) {
  const texture = index===0 ? 'witch_kiss' : 'witch_kiss/'+theme;
  const icon = seal(theme);
  write('textures/item/'+texture+'.png',encodePNG(size,size,icon));
  if (index>0) {
    json('models/item/'+texture+'.json',iconModel(texture));
    overrides.push({predicate:{'madoka_magic:kiss_seal':index},model:'madoka_magic:item/'+texture});
  }
  const x = index%6*128, y = Math.floor(index/6)*138;
  preview.rect(x+12,y+8,104,104,[42,35,52,255]);
  preview.paste(upscale(icon,size,size,3),96,96,x+16,y+12);
  const label=theme.toUpperCase();
  for(const [row,part] of label.split('_').reduce((rows,word)=>{
    if(rows.length && (rows.at(-1)+' '+word).length<28) rows[rows.length-1]+=' '+word;
    else rows.push(word);
    return rows;
  },[]).entries()) preview.text(part,x+12,y+117+row*6,1);
}
json('models/item/witch_kiss.json',{...iconModel('witch_kiss'),overrides});

const brass=hex('#aa7453'), bronze=hex('#6d4653'), amethyst=hex('#9362bd');
const baseTexture=pixels(32,32,p=>{
  p.rect(0,0,32,32,ink);
  p.rect(0,0,16,16,bronze);
  p.rect(1,1,14,14,brass);
  p.rect(2,2,12,12,ink);
  p.poly([[8,2],[13,8],[8,13],[3,8]],hex('#3e2a53'));
  p.polyline([[3,8],[8,3],[13,8],[8,12],[3,8]],amethyst);
  for(const [x,y] of [[2,2],[13,2],[2,13],[13,13]]) p.rect(x,y,1,1,glint);
  p.rect(16,0,16,8,bronze);
  p.rect(16,0,16,2,gold);
  p.rect(16,5,16,1,brass);
  p.rect(16,8,16,8,hex('#342640'));
  p.rect(16,8,16,1,amethyst);
  p.rect(16,15,16,1,brass);
  p.rect(18,11,12,1,bronze);
  for(const x of [18,29]) p.dot(x,12,gold);
  p.rect(0,16,16,16,hex('#352443'));
  p.poly([[8,18],[13,24],[8,29],[3,24]],amethyst);
  p.poly([[8,18],[8,24],[3,24]],hex('#c6a2e0'));
  p.poly([[8,24],[13,24],[8,29]],hex('#5b397a'));
});
write('textures/block/display_ring.png',encodePNG(32,32,baseTexture));

const bandTexture=pixels(32,24,p=>{
  const ramps=[
    ['#644252','#bb8057','#f3c98b','#ffe4ab'],
    ['#3c285b','#8352ad','#c691dc','#ebc7fa'],
    ['#20323d','#3a696b','#80bab0','#dff3db'],
  ];
  ramps.forEach((r,i)=>{
    const [dark,mid,bright,shine]=r.map(hex), y=i*8;
    p.rect(0,y,32,8,mid);
    p.rect(0,y,15,1,bright);
    p.rect(0,y+1,15,1,mid);
    p.rect(0,y+2,15,1,dark);
    p.rect(0,y+5,15,2,dark);
    p.rect(16,y,16,8,brass);
    p.rect(16,y,16,1,gold);
    p.rect(16,y+1,16,1,bright);
    p.rect(17,y+1,1,1,shine);
    p.rect(16,y+3,16,2,bronze);
  });
});
write('textures/block/display_ring_bands.png',encodePNG(32,24,bandTexture));

const ringIcon=pixels(size,size,p=>{
  p.place(16,15);
  p.poly([[-11,9],[0,6],[11,9],[11,12],[0,15],[-11,12]],ink);
  p.poly([[-10,9],[0,7],[10,9],[0,12]],brass);
  p.line(-9,11,0,14,gold); p.line(0,14,9,11,bronze);
  p.place(16,13);
  p.arc(0,0,10,2.4,0,Math.PI*2,brass);
  p.arc(0,0,10,1.1,-3.05,-.11,gold);
  p.polyline([[-8,8],[-10,3],[-8,-2],[-2,-8],[3,-10],[7,-9],[9,-6],[7,0],[1,6],[-5,9],[-8,8]],amethyst);
  p.polyline([[-9,-5],[-6,-7],[-1,-7],[5,-4],[9,0],[10,4],[8,7],[4,8],[-2,6],[-7,3],[-10,-1],[-9,-5]],hex('#80bab0'));
  star(p,0,0,4,glint);
});
write('textures/item/display_ring.png',encodePNG(size,size,ringIcon));

function box(from,to,top,side) {
  return {from,to,faces:Object.fromEntries(['down','up','north','south','west','east'].map(face=>
    [face,{uv:face==='up'||face==='down'?top:side,texture:'#base'}]))};
}
json('models/block/display_ring.json',{
  textures:{base:'madoka_magic:block/display_ring',particle:'madoka_magic:block/display_ring'},
  elements:[
    box([2,0,2],[14,1,14],[0,0,8,8],[8,0,16,4]),
    box([3,1,3],[13,2.5,13],[0,0,8,8],[8,4,16,8]),
    box([5,2.5,5],[11,3,11],[0,8,8,16],[8,0,16,4]),
  ],
});
if(process.argv[3]) {
  const path=resolve(process.argv[3]);
  mkdirSync(dirname(path),{recursive:true});
  writeFileSync(path,preview.png());
}
