// Original 32px, four-frame witch motifs, drawn with PixelFX (MIT).
// Usage: node tools/render-external-witch-particles.mjs <PixelFX checkout> [contact PNG]
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

if (!process.argv[2]) throw new Error('Provide the PixelFX checkout path.');
const upstream = resolve(process.argv[2]);
const load = path => import(pathToFileURL(join(upstream, path)).href);
const { createFrame, painterFor, composite, hex } = await load('src/core/index.js');
const { canvas, upscale } = await load('tools/lib/fxlib.mjs');
const assets = resolve(dirname(fileURLToPath(import.meta.url)), '../src/main/resources/assets/madoka_magic');
const styles = JSON.parse(readFileSync(join(assets, 'visuals/witch_styles.json'), 'utf8'));
const themes = Object.entries(styles).filter(([,style])=>style.orbit).sort((a,b)=>a[1].orbit.row-b[1].orbit.row);
const side=32, frames=4, columns=8, rows=12;
if (themes.length !== rows || themes.some(([,s],i)=>s.orbit.row!==i)) throw new Error('Expected twelve distinct atlas rows.');
const ink=hex('#181521'), pale=hex('#fff0ce'), gold=hex('#e8b77a'), iron=hex('#716b7e');
const draw = {
  brunhilde(p, motif, k, C) {
    if (!motif) {
      p.poly([[-12,-5],[-4,-11],[5,-10],[11,-5],[10,0],[-10,1]],C[0]);
      for (const [x,y,lean] of [[-7,-4,-2],[0,-7,1],[7,-3,2]]) {
        p.poly([[x-4,y],[x+3,y-2],[x+4,y+5],[x+lean,y+14],[x-3,y+7]],ink);
        p.poly([[x-2,y+1],[x+2,y],[x+2,y+5],[x+lean,y+11],[x-1,y+6]],C[2]);
        p.polyline([[x-1,y+1],[x,y+5],[x+lean,y+8]],C[3]);
      }
      p.dot(-8+k%2,-8,C[2]); p.dot(8,11-k%2,C[3]);
    } else {
      p.poly([[-10,-4],[-5,-10],[5,-11],[11,-3],[8,8],[-2,12],[-10,5]],ink);
      p.poly([[-8,-3],[-4,-8],[4,-9],[8,-3],[6,6],[-2,9],[-8,4]],C[0]);
      p.polyline([[-6,-6],[-1,-2],[-4,2],[1,6],[0,9]],C[2]);
      p.polyline([[5,-7],[2,-2],[7,2]],C[3]); p.dot(-1,-2,k%2?C[2]:pale);
      p.dot(-5,2,ink); p.dot(4,5,ink); p.dot(3,-4,ink);
    }
  },
  sieglinde(p, motif, k, C) {
    if (!motif) {
      p.poly([[-12,-5],[-10,-10],[-5,-12],[0,-8],[5,-12],[10,-10],[12,-5],[10,2],[0,12],[-10,2]],ink);
      p.poly([[-10,-5],[-8,-8],[-5,-9],[-1,-5],[-3,0],[-1,4],[-4,5],[-1,9],[-8,1]],C[1]);
      p.poly([[2,-6],[5,-9],[8,-8],[10,-5],[8,1],[2,8],[0,4],[2,0],[0,-3]],C[2]);
      p.polyline([[-8,-5],[-6,-7],[-4,-6]],C[3]); p.dot(5,-6,k%2?C[2]:C[3]);
    } else {
      p.poly([[-2,-12],[3,-7],[6,-1],[2,5],[4,11],[-2,13],[-7,6],[-5,0]],ink);
      p.poly([[-1,-9],[2,-5],[3,0],[0,4],[2,8],[-2,10],[-4,5],[-3,0]],C[2]);
      p.polyline([[-1,-6],[0,-1],[-2,2]],pale); p.line(-9,7,-6,5,gold); p.dot(7,8-k,gold);
    }
  },
  annegret(p, motif, k, C) {
    const cream=hex('#fff0d5'), cake=hex('#bf764c'), jam=hex('#d34b78');
    if (!motif) {
      p.poly([[-12,-4],[6,-10],[12,-4],[12,10],[-10,13],[-12,10]],ink);
      p.poly([[-10,0],[10,-3],[10,8],[-10,11]],cake);
      p.poly([[-10,3],[10,0],[10,3],[-10,6]],jam);
      p.poly([[-10,0],[10,-3],[10,-1],[-10,2]],cream);
      p.poly([[-11,-4],[5,-8],[10,-4],[8,-1],[-9,2]],cream);
      p.poly([[-4,-8],[-2,-12],[2,-12],[4,-9],[1,-5],[-2,-5]],jam);
      p.dot(-1,-10,pale); p.dot(k-3,6,gold);
    } else {
      p.poly([[-13,-6],[-6,-3],[-5,-7],[5,-7],[6,-3],[13,-6],[11,0],[13,6],[6,3],[5,7],[-5,7],[-6,3],[-13,6],[-11,0]],ink);
      p.poly([[-11,-3],[-6,-1],[-6,1],[-11,4],[-9,0]],C[2]);
      p.poly([[11,-3],[6,-1],[6,1],[11,4],[9,0]],C[2]);
      p.rect(-4,-5,9,11,C[1]); p.line(-3,-3,3,3,cream);
      p.line(-3,0,0,3,cream); p.line(0,-3,3,0,cream);
      p.dot(-2+k,-2,cream);
    }
  },
  netherite_monstrosity(p, motif, k, C) {
    if (!motif) {
      p.poly([[-13,-8],[11,-8],[13,-5],[6,-1],[3,-1],[3,7],[8,9],[8,12],[-9,12],[-9,9],[-4,7],[-4,-1],[-10,-2]],ink);
      p.poly([[-10,-6],[10,-6],[5,-3],[1,-3],[1,8],[5,10],[-6,10],[-2,8],[-2,-3],[-8,-4]],iron);
      p.rect(-3,-2,6,10,iron); p.line(-3,-2,-3,7,C[2]);
      p.line(-9,-6,8,-6,C[3]); p.line(0,-2,0,5,C[2]); p.dot(0,k-1,pale);
    } else {
      p.poly([[-8,-8],[3,-11],[10,-5],[8,6],[0,11],[-9,5]],ink);
      p.poly([[-6,-6],[2,-8],[7,-4],[5,4],[0,7],[-6,3]],C[0]);
      p.poly([[-4,-3],[0,-5],[4,-2],[2,3],[-2,4],[-4,1]],C[2]);
      p.polyline([[-5,-6],[-1,-7],[2,-6]],C[3]); p.dot(k-2,0,pale);
    }
  },
  ignis(p, motif, k, C) {
    const shift=[0,1,0,-1][k];
    if (!motif) {
      p.poly([[-10,7],[-11,0],[-7,-7],[-6,-1],[-2,-6],[shift,-13],[5,-8],[4,-3],[10,-8],[11,2],[8,9],[1,13],[-6,11]],ink);
      p.poly([[-8,6],[-8,0],[-5,-3],[-5,2],[0,-5],[shift,-10],[3,-6],[2,0],[8,-4],[8,3],[6,7],[0,10],[-5,8]],C[1]);
      p.poly([[-4,6],[-3,1],[0,-3],[1,2],[4,0],[5,5],[1,9]],C[2]);
      p.poly([[-1,6],[0,2],[2,6],[0,8]],C[3]);
    } else {
      p.poly([[-7,11],[-11,5],[-8,-2],[-2,-6],[4+shift,-11],[3,-4],[8,-2],[10,4],[5,10],[-1,12]],ink);
      p.polyline([[-6,8],[-8,4],[-5,0],[0,-2],[3+shift,-7]],C[2]);
      p.polyline([[-6,8],[-1,9],[5,5],[4,1],[0,2],[-1,5]],C[1]);
      p.dot(1+shift,4,C[3]); p.dot(8,-8+k%2,C[2]);
    }
  },
  ender_guardian(p, motif, k, C) {
    const stone=hex('#66716c'), rim=hex('#b7c4a4');
    if (!motif) {
      p.poly([[-12,12],[-12,-4],[-8,-10],[0,-13],[8,-10],[12,-4],[12,12]],ink);
      p.poly([[-10,10],[-10,-4],[-6,-8],[0,-10],[6,-8],[10,-4],[10,10]],stone);
      p.poly([[-7,10],[-7,-2],[-4,-6],[0,-7],[4,-6],[7,-2],[7,10]],ink);
      p.line(0,-6,0,9,C[2]); p.polyline([[-9,-3],[-5,-8],[0,-10],[5,-8],[9,-3]],rim);
      p.polyline([[-10,3],[-4,6],[4,1],[10,4]],gold); p.dot(0,k-1,C[3]);
    } else {
      for(const [x,y] of [[-5,5],[5,-5]]) {
        p.poly([[x-7,y-1],[x-2,y-6],[x+2,y-6],[x+6,y-2],[x+6,y+2],[x+1,y+7],[x-3,y+7],[x-7,y+3]],ink);
        p.polyline([[x-5,y-1],[x-1,y-4],[x+1,y-4],[x+4,y-1],[x+4,y+1],[x,y+5],[x-2,y+5],[x-5,y+2],[x-5,y-1]],rim);
      }
      p.line(-3,3,3,-3,C[2]); p.dot(k-1,1-k,C[3]);
    }
  },
  the_harbinger(p, motif, k, C) {
    if (!motif) {
      p.circle(0,0,12,ink); p.arc(0,0,10,3,-2.5,2.4,C[1]);
      p.arc(0,0,11,1,-2.1,0.2,C[3]); p.circle(-2,0,8,ink);
      p.polyline([[-11,0],[-6,2],[-1,0],[4,1],[11,-1]],C[2]);
      p.dot([10,9,6,1][k],[-4,-6,-9,-10][k],pale);
    } else {
      p.arc(0,0,10,2,-2.3,-0.3,gold); p.arc(0,0,10,2,0.3,1.6,C[2]);
      p.arc(0,0,10,2,2.1,2.8,gold); p.line(-12,5,12,-5,C[1]);
      p.poly([[-2,-4],[0,-9],[2,-4],[7,-2],[2,0],[0,5],[-2,0],[-6,-2]],ink);
      p.line(0,-6,0,2,C[3]); p.line(-3,-2,3,-2,C[3]); p.dot(8-k,7,gold);
    }
  },
  the_leviathan(p, motif, k, C) {
    const purple=hex('#8e6cb8'), violet=hex('#c7a8dc');
    if (!motif) {
      p.circle(0,0,12,ink); p.arc(0,0,10,3,-2.6,1.8,purple);
      p.arc(0,0,7,2,-0.2,3.9,C[1]); p.arc(0,0,4,2,2.6,6.7,violet);
      p.circle(0,0,2,ink); p.arc(0,0,11,1,-2.3,-0.8,C[3]); p.dot(k-2,-7,violet);
    } else {
      p.poly([[-8,12],[-11,7],[-10,0],[-6,-4],[2,-5],[5,-8],[3,-11],[7,-10],[10,-6],[7,0],[0,2],[-3,5],[-2,8],[3,9],[7,6],[6,11],[0,13]],ink);
      p.polyline([[-7,9],[-8,4],[-5,-1],[3,-2],[7,-5],[6,-7]],purple);
      p.polyline([[-5,7],[-3,10],[2,11],[5,9]],violet);
      for(let i=0;i<4;i++)p.dot(-7+i*3,2+Math.round(Math.sin(i)*2),i===k?C[3]:C[1]);
    }
  },
  amethyst_crab(p, motif, k, C) {
    if (!motif) {
      for(const [x,y,w,h] of [[-7,3,5,9],[6,4,5,11],[0,-1,6,12]]) {
        p.poly([[x-w,y],[x-w,y-h*.55],[x,y-h],[x+w,y-h*.55],[x+w,y+6],[x,y+9]],ink);
        p.poly([[x-w+2,y],[x-w+2,y-h*.55+1],[x,y-h+3],[x,y+6]],C[2]);
        p.poly([[x+1,y-h+3],[x+w-2,y-h*.55+1],[x+w-2,y+4],[x+1,y+6]],C[1]);
        p.line(x,y-h+3,x,y+5,C[3]);
      }
      p.dot(-3+k,0,pale);
    } else {
      p.poly([[-12,-6],[-4,-12],[-1,-6],[-4,-2],[-2,3],[-5,11],[-11,6]],ink);
      p.poly([[-10,-5],[-5,-9],[-4,-6],[-6,-2],[-4,3],[-6,8],[-9,4]],C[2]);
      p.poly([[4,-10],[10,-6],[12,2],[8,10],[2,7],[4,2],[1,-2]],ink);
      p.poly([[5,-7],[8,-4],[9,2],[7,7],[5,5],[6,1],[4,-2]],C[1]);
      p.line(-9,-3,-7,2,C[3]); p.dot(6,k,C[3]);
    }
  },
  ancient_remnant(p, motif, k, C) {
    if (!motif) {
      p.poly([[-10,-12],[8,-12],[10,-7],[5,-5],[5,8],[10,10],[10,13],[-10,13],[-10,10],[-6,8],[-6,-5],[-11,-7]],ink);
      p.rect(-8,-10,15,3,C[2]); p.rect(-4,-6,7,14,C[1]); p.rect(-8,10,16,1,C[2]);
      p.line(-2,-5,-2,6,C[3]); p.line(2,-5,2,7,C[0]);
      p.polyline([[-7,2],[-2,0],[1,2],[6,-1]],ink); p.dot(7,5+k%2,gold);
    } else {
      p.poly([[-9,-12],[9,-12],[8,-8],[5,-7],[5,-4],[1,0],[5,4],[5,7],[8,8],[9,12],[-9,12],[-8,8],[-5,7],[-5,4],[-1,0],[-5,-4],[-5,-7],[-8,-8]],ink);
      p.rect(-7,-10,15,2,C[2]); p.rect(-7,9,15,2,C[2]);
      p.poly([[-4,-7],[4,-7],[3,-4],[0,-1],[-3,-4]],C[1]);
      p.poly([[-4,7],[0,3],[4,7]],gold); p.dot(0,k-1,pale);
    }
  },
  maledictus(p, motif, k, C) {
    if (!motif) {
      p.poly([[-9,11],[-7,1],[-3,-7],[4,-13],[9,-10],[10,-4],[6,3],[-2,8]],ink);
      p.poly([[-7,7],[-5,0],[-1,-6],[4,-10],[6,-10],[5,-4],[1,3]],C[0]);
      p.poly([[-5,5],[-3,-1],[1,-7],[5,-10],[3,-4],[-1,2]],C[1]);
      p.line(-10,13,6,-10,C[2]);
      for(let i=0;i<5;i++)p.line(-5+i*2,6-i*3,2+i,-3-i*2,i===k?C[3]:C[1]);
    } else {
      const lean=[-1,0,1,0][k];
      p.line(0,-12,0,9,ink); p.line(-11,-5+lean,11,-5-lean,ink);
      p.line(0,-10,0,8,C[2]); p.line(-10,-4+lean,10,-4-lean,C[3]);
      for(const [x,y] of [[-8,lean],[8,-lean]]) {
        p.line(x,-3+y,x,5+y,C[1]);
        p.poly([[x-5,5+y],[x+5,5+y],[x+3,9+y],[x-3,9+y]],ink);
        p.polyline([[x-4,5+y],[x,7+y],[x+4,5+y]],C[2]);
      }
      p.poly([[-5,10],[0,8],[5,10],[5,12],[-5,12]],ink); p.line(-3,10,3,10,C[2]);
    }
  },
  scylla(p, motif, k, C) {
    if (!motif) {
      p.poly([[-10,6],[-12,-1],[-8,-8],[-2,-12],[6,-10],[11,-4],[11,3],[6,9],[0,12]],ink);
      p.poly([[-8,4],[-9,-1],[-6,-6],[-1,-9],[5,-7],[8,-3],[8,2],[4,6],[0,8]],C[1]);
      p.polyline([[-6,2],[-6,-2],[-2,-6],[3,-5],[5,-1],[3,2],[0,2],[-1,0]],C[3]);
      p.line(-8,5,0,10,C[2]); p.dot(3-k,-4,pale);
    } else {
      for(let i=0;i<3;i++) {
        const y=-6+i*6, offset=[0,1,0,-1][(k+i)%4];
        p.polyline([[-12,y],[-8,y-3+offset],[-3,y-2],[2,y+2],[7,y+2-offset],[12,y-1]],ink);
        p.polyline([[-11,y],[-7,y-2+offset],[-3,y-1],[2,y+3],[7,y+3-offset],[11,y]],i===1?C[3]:C[1]);
      }
      p.dot(-8+k*4,-11,C[2]);
    }
  },
};

function render(theme, motif, frameIndex) {
  const k=((frameIndex%frames)+frames)%frames;
  const s=styles[theme], C=[s.secondary,s.primary,s.crown,'#fff0da'].map(hex);
  const frame=createFrame(side,side), p=painterFor(frame);
  p.place(side/2,side/2+[0,-1,0,1][k]);
  draw[theme](p,motif,k,C);
  return composite(frame,new Uint8ClampedArray(side*side*4),null);
}

const atlas=canvas(columns*side,rows*side,[0,0,0,0]);
const preview=canvas(730,rows*82,[25,22,34,255]);
for(const [row,[theme]] of themes.entries()) {
  if(!draw[theme])throw new Error('No drawing recipe: '+theme);
  preview.text(theme.toUpperCase().replaceAll('_',' '),4,row*82+5,1);
  for(let motif=0;motif<2;motif++) {
    const signatures=new Set();
    for(let k=0;k<frames;k++) {
      const pixels=render(theme,motif,k);
      let visible=0;
      for(let y=0;y<side;y++)for(let x=0;x<side;x++) {
        const alpha=pixels[(y*side+x)*4+3];
        if(alpha)visible++;
        if(alpha&&(x===0||x===side-1||y===0||y===side-1))throw new Error(`Clipped motif: ${theme}/${motif}`);
      }
      if(visible<20)throw new Error('Empty motif: '+theme);
      signatures.add(Buffer.from(pixels).toString('base64'));
      const column=motif*frames+k;
      atlas.paste(pixels,side,side,column*side,row*side);
      preview.rect(174+column*68,row*82+16,64,64,[41,35,53,255]);
      preview.paste(upscale(pixels,side,side,2),side*2,side*2,174+column*68,row*82+16);
    }
    if(signatures.size<2)throw new Error(`Static loop: ${theme}/${motif}`);
    if(!Buffer.from(render(theme,motif,0)).equals(Buffer.from(render(theme,motif,frames))))throw new Error('Loop seam: '+theme);
  }
}
const output=join(assets,'textures/particle/external_witch_orbits.png');
for(let row=0;row<rows;row++)for(let column=0;column<columns;column++) {
  if(atlas.data[((row*side)*(columns*side)+column*side)*4+3]!==0)throw new Error('Atlas background must remain transparent');
}
mkdirSync(dirname(output),{recursive:true});
writeFileSync(output,atlas.png());
writeFileSync(output+'.mcmeta',JSON.stringify({texture:{blur:false,clamp:true}},null,2)+'\n');
if(process.argv[3]) {
  const contact=resolve(process.argv[3]); mkdirSync(dirname(contact),{recursive:true}); writeFileSync(contact,preview.png());
}
console.log(`Validated 24 four-frame motifs, transparent margins and loop periods; wrote ${output}`);
