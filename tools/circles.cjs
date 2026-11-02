// Generates the magic circle atlas (docs/ART.md §3, roadmap V3): assets/orbisarcana/textures/effect/circle_atlas.png,
// 2048x2048, white with alpha (tinted per vertex in game). Lines are drawn "neon": a sharp anti-aliased core plus a soft
// glow, from signed distance shapes. Layout (must match client/circle/CircleAtlas.java):
//   rows 0-2, 512x512 cells: plates fire, earth, water, air / ice, lightning, nature, light / dark, astral, ornament, seal
//   y 1536: rune band A (32 glyphs of 64x64)   y 1600: rune band B (other order, framed)
//   y 1664: tube profile 2048x32                y 1696: filigree band 2048x48 (16 repeats)
//   y 1744: tick band 2048x32 (64 repeats)      y 1792: flare 128x128 at x 0, sparkle 128x128 at x 128
// Run from the project root: node tools/circles.cjs (takes a minute: every pixel tests every shape).
const fs = require("fs");
const { png } = require("./lib/png.cjs");
const S = require("./lib/sdf.cjs");

const OUT = "src/main/resources/assets/orbisarcana/textures/effect/circle_atlas.png";
const W = 2048, H = 2048;
const alpha = new Float32Array(W * H);

/** Paints passes into the region: each pass is { shape, a (peak alpha), core (px), glow (px) }; layers combine as screen. */
function paint(x0, y0, w, h, passes) {
  for (let y = y0; y < y0 + h; y++) for (let x = x0; x < x0 + w; x++) {
    let keep = 1 - alpha[y * W + x];
    for (const p of passes) {
      const d = p.shape(x + 0.5, y + 0.5);
      const core = Math.max(0, Math.min(1, 0.5 - d / (p.core ?? 1)));
      const glow = p.glow ? 0.2 * Math.exp(-Math.max(0, d) / (p.glow * 0.6)) : 0;
      const a = Math.min(1, (p.a ?? 1) * Math.max(core, glow));
      keep *= 1 - a;
    }
    alpha[y * W + x] = 1 - keep;
  }
}

// ---- rune alphabet: 32 glyphs in a unit box (docs/ART.md §3 meanings, in order) ----
// l = polyline, c = ring, d = dot, a = arc (degrees, 0 = right, clockwise)
const RUNES = [
  [["l", .5, .1, .5, .9], ["l", .2, .38, .5, .1, .8, .38]],                                   // сила
  [["l", .2, .2, .8, .2, .8, .8, .2, .8], ["d", .5, .5, .08]],                                // контроль
  [["c", .32, .5, .2], ["c", .68, .5, .2]],                                                   // связь
  [["l", .5, .1, .85, .3, .85, .65, .5, .9, .15, .65, .15, .3, .5, .1]],                      // защита
  [["l", .15, .5, .85, .5], ["l", .6, .25, .85, .5, .6, .75], ["d", .2, .5, .07]],            // перенос
  [["l", .2, .8, .5, .2, .8, .8], ["l", .35, .6, .65, .6]],                                   // ускорение
  [["l", .2, .2, .8, .8], ["l", .2, .8, .5, .5], ["a", .5, .5, .3, 180, 270]],                // замедление
  [["l", .2, .2, .8, .8], ["l", .8, .2, .2, .8], ["c", .5, .5, .12]],                         // разрушение
  [["l", .5, .15, .5, .85], ["l", .15, .5, .85, .5], ["c", .5, .5, .3]],                      // созидание
  [["l", .5, .15, .5, .85], ["l", .3, .35, .7, .35], ["a", .5, .62, .2, 0, 180]],             // исцеление
  [["l", .2, .85, .2, .3, .5, .12, .8, .3, .8, .85], ["d", .5, .55, .08]],                    // призыв
  [["l", .2, .15, .8, .15], ["l", .5, .15, .5, .85], ["l", .3, .85, .7, .85], ["l", .3, .5, .7, .5]], // изгнание
  [["a", .5, .5, .32, 200, 340], ["a", .5, .5, .32, 20, 160], ["d", .5, .5, .1]],             // взгляд
  [["l", .15, .5, .85, .5], ["a", .5, .5, .3, 180, 360], ["l", .3, .7, .7, .7]],              // сокрытие
  [["l", .15, .2, .5, .55, .85, .2], ["l", .5, .55, .5, .9]],                                 // притяжение
  [["l", .15, .8, .5, .45, .85, .8], ["l", .5, .45, .5, .1]],                                 // отталкивание
  [["l", .3, .15, .3, .85], ["l", .3, .3, .75, .5, .3, .7]],                                  // начало
  [["l", .7, .15, .7, .85], ["l", .7, .3, .25, .5, .7, .7]],                                  // конец
  [["a", .5, .5, .32, 300, 240], ["l", .5, .12, .62, .22], ["l", .5, .12, .62, .02]],         // цикл
  [["l", .25, .85, .25, .15, .75, .15, .75, .85], ["l", .25, .5, .75, .5]],                   // порог
  [["c", .5, .3, .15], ["l", .5, .45, .5, .88], ["l", .5, .7, .7, .7], ["l", .5, .82, .65, .82]], // ключ
  [["c", .5, .5, .32], ["l", .26, .26, .74, .74], ["l", .74, .26, .26, .74]],                 // печать
  [["l", .2, .85, .4, .15, .6, .85, .8, .15]],                                                // путь
  [["l", .5, .1, .5, .9], ["a", .5, .5, .25, 90, 270], ["a", .5, .5, .25, 270, 90]],          // зеркало
  [["l", .5, .1, .62, .4, .9, .5, .62, .6, .5, .9, .38, .6, .1, .5, .38, .4, .5, .1]],        // искра
  [["a", .3, .35, .2, 180, 360], ["a", .7, .35, .2, 0, 180], ["a", .3, .7, .2, 180, 360], ["a", .7, .7, .2, 0, 180]], // поток
  [["l", .5, .1, .5, .6], ["l", .5, .6, .2, .9], ["l", .5, .6, .8, .9], ["l", .5, .6, .5, .9]], // корень
  [["l", .7, .1, .3, .5, .7, .5, .3, .9]],                                                    // буря
  [["l", .15, .4, .85, .4], ["l", .15, .6, .85, .6], ["d", .5, .2, .07], ["d", .5, .8, .07]], // покой
  [["a", .5, .5, .35, 45, 315], ["a", .62, .5, .26, 60, 300]],                                // тень
  [["c", .5, .5, .16], ["l", .5, .08, .5, .26], ["l", .5, .74, .5, .92], ["l", .08, .5, .26, .5], ["l", .74, .5, .92, .5]], // свет
  [["l", .5, .08, .5, .92], ["l", .08, .5, .92, .5], ["l", .22, .22, .78, .78], ["l", .78, .22, .22, .78]], // звезда
];

/** The glyph as shapes in a cell (x0, y0, size) with stroke w. */
function glyph(i, x0, y0, size, w) {
  const m = size * 0.16, s = size - 2 * m, X = v => x0 + m + v * s, Y = v => y0 + m + v * s;
  const parts = RUNES[i % RUNES.length].map(it => {
    switch (it[0]) {
      case "l": { const pts = []; for (let k = 1; k < it.length; k += 2) pts.push([X(it[k]), Y(it[k + 1])]); return S.polyline(pts, w); }
      case "c": return S.ring(X(it[1]), Y(it[2]), it[3] * s, w);
      case "d": return S.circle(X(it[1]), Y(it[2]), it[3] * s);
      default: return S.arc(X(it[1]), Y(it[2]), it[3] * s, w, (it[4] * Math.PI) / 180, (it[5] * Math.PI) / 180);
    }
  });
  return S.union(...parts);
}

/** A ring of glyphs around (cx, cy) at radius r, each upright towards the centre. */
function glyphRing(cx, cy, r, count, size, w, offset = 0) {
  const parts = [];
  for (let i = 0; i < count; i++) {
    const ang = offset + (i * 2 * Math.PI) / count;
    const g = glyph((i * 7 + offset * 13) | 0, cx - size / 2, cy - r - size / 2, size, w);
    parts.push(S.rotated(cx, cy, ang, g));
  }
  return S.union(...parts);
}

// ---- plates (512 cells, centre 256, outer radius ~244) ----
const C = 256, R = 244;
function plate(index, build) {
  const x0 = (index % 4) * 512, y0 = Math.floor(index / 4) * 512;
  const shapes = build(x0 + C, y0 + C);
  paint(x0, y0, 512, 512, shapes.map(s => ({ ...s, shape: S.bounded(x0 + C, y0 + C, R + 6, s.shape) })));
  console.log(`plate ${index}`);
}
const rim = (cx, cy) => [
  { shape: S.ring(cx, cy, R, 3), a: 1, core: 1.2, glow: 7 },
  { shape: S.ring(cx, cy, R - 10, 1.4), a: 0.8, glow: 4 },
  { shape: glyphRing(cx, cy, R - 30, 24, 30, 2.2), a: 0.9, glow: 4 },
  { shape: S.ring(cx, cy, R - 50, 1.4), a: 0.8, glow: 4 },
];
const SCHOOLS = {
  fire: (cx, cy) => [...rim(cx, cy),
    { shape: S.union(S.outline(S.ngon(cx, cy, 186, 3), 3), S.outline(S.ngon(cx, cy, 186, 3, Math.PI / 2), 3)), a: 1, glow: 6 },
    { shape: S.repeat(cx, cy, 6, S.polyline([[cx, cy - 92], [cx - 22, cy - 130], [cx, cy - 170], [cx + 22, cy - 130], [cx, cy - 92]], 2.5), Math.PI / 6), a: 0.9, glow: 5 },
    { shape: S.union(S.ring(cx, cy, 92, 2.5), S.ring(cx, cy, 60, 1.6)), a: 1, glow: 5 },
    { shape: S.polyline([[cx, cy + 34], [cx - 26, cy], [cx - 8, cy - 18], [cx, cy - 44], [cx + 8, cy - 18], [cx + 26, cy], [cx, cy + 34]], 2.5), a: 1, glow: 6 }],
  earth: (cx, cy) => [...rim(cx, cy),
    { shape: S.union(S.outline(S.ngon(cx, cy, 186, 4, -Math.PI / 4), 3), S.outline(S.ngon(cx, cy, 186, 4, 0), 3)), a: 1, glow: 6 },
    { shape: S.union(S.outline(S.ngon(cx, cy, 120, 4, -Math.PI / 4), 2), S.outline(S.ngon(cx, cy, 84, 4, 0), 2)), a: 0.9, glow: 5 },
    { shape: S.repeat(cx, cy, 8, S.outline([[cx - 12, cy - 140], [cx + 12, cy - 140], [cx + 12, cy - 158], [cx - 12, cy - 158]], 2), Math.PI / 8), a: 0.85, glow: 4 },
    { shape: S.outline([[cx - 30, cy + 26], [cx, cy - 34], [cx + 30, cy + 26]], 3), a: 1, glow: 6 }],
  water: (cx, cy) => [...rim(cx, cy),
    { shape: (x, y) => { const r = Math.hypot(x - cx, y - cy), a = Math.atan2(y - cy, x - cx); return Math.abs(r - (170 + 8 * Math.sin(a * 12))) - 1.5; }, a: 1, glow: 6 },
    { shape: (x, y) => { const r = Math.hypot(x - cx, y - cy), a = Math.atan2(y - cy, x - cx); return Math.abs(r - (140 + 6 * Math.sin(a * 12 + Math.PI))) - 1.2; }, a: 0.8, glow: 4 },
    { shape: S.repeat(cx, cy, 6, S.ring(cx, cy - 60, 60, 2)), a: 0.95, glow: 5 },
    { shape: S.ring(cx, cy, 60, 2.5), a: 1, glow: 6 },
    { shape: S.repeat(cx, cy, 6, S.union(S.ring(cx, cy - 112, 9, 2), S.segment(cx, cy - 121, cx, cy - 134, 2)), Math.PI / 6), a: 0.9, glow: 4 }],
  air: (cx, cy) => [...rim(cx, cy),
    { shape: S.repeat(cx, cy, 3, (x, y) => { // triskele arms: an Archimedean spiral piece
      const dx = x - cx, dy = y - cy, r = Math.hypot(dx, dy); let a = Math.atan2(dy, dx); let best = Infinity;
      for (let k = -1; k <= 1; k++) { const t = (a + k * 2 * Math.PI); const rr = 30 + 26 * t; if (t >= 0 && t <= 2 * Math.PI * 0.95) best = Math.min(best, Math.abs(r - rr)); }
      return best - 1.8; }), a: 1, glow: 6 },
    { shape: S.repeat(cx, cy, 12, S.arc(cx, cy, 182, 2, -Math.PI / 2 - 0.18, -Math.PI / 2 + 0.12)), a: 0.9, glow: 5 },
    { shape: S.ring(cx, cy, 30, 2), a: 1, glow: 5 }],
  ice: (cx, cy) => [...rim(cx, cy),
    { shape: S.repeat(cx, cy, 6, S.union(S.segment(cx, cy, cx, cy - 184, 3), S.segment(cx, cy - 110, cx - 34, cy - 144, 2.2), S.segment(cx, cy - 110, cx + 34, cy - 144, 2.2),
      S.segment(cx, cy - 60, cx - 22, cy - 82, 2), S.segment(cx, cy - 60, cx + 22, cy - 82, 2))), a: 1, glow: 6 },
    { shape: S.union(S.outline(S.ngon(cx, cy, 186, 6, 0), 2), S.outline(S.ngon(cx, cy, 70, 6, 0), 2)), a: 0.9, glow: 5 },
    { shape: S.repeat(cx, cy, 6, S.outline([[cx, cy - 150], [cx + 9, cy - 162], [cx, cy - 174], [cx - 9, cy - 162]], 1.8)), a: 0.9, glow: 4 }],
  lightning: (cx, cy) => [...rim(cx, cy),
    { shape: S.star(cx, cy, 186, 8, 3, 2.4), a: 1, glow: 6 },
    { shape: S.repeat(cx, cy, 4, S.polyline([[cx, cy - 40], [cx - 16, cy - 84], [cx + 10, cy - 92], [cx - 6, cy - 140]], 2.6), Math.PI / 4), a: 1, glow: 6 },
    { shape: S.union(S.ring(cx, cy, 40, 2.2), S.outline(S.ngon(cx, cy, 120, 8, -Math.PI / 2 + Math.PI / 8), 1.6)), a: 0.9, glow: 5 }],
  nature: (cx, cy) => [...rim(cx, cy),
    { shape: S.union(S.ring(cx, cy, 56, 2), S.repeat(cx, cy, 6, S.ring(cx, cy - 56, 56, 2))), a: 1, glow: 5 },
    { shape: S.repeat(cx, cy, 8, (x, y) => { const lx = x - cx, ly = y - (cy - 150); return (Math.hypot(lx / 14, ly / 36) - 1) * 14; }, Math.PI / 8), a: 0.35, glow: 0, core: 1 },
    { shape: S.repeat(cx, cy, 8, (x, y) => Math.abs((Math.hypot((x - cx) / 14, (y - (cy - 150)) / 36) - 1) * 14) - 1.2, Math.PI / 8), a: 1, glow: 5 },
    { shape: S.ring(cx, cy, 168, 1.6), a: 0.8, glow: 4 }],
  light: (cx, cy) => [...rim(cx, cy),
    { shape: S.repeat(cx, cy, 12, S.segment(cx, cy - 60, cx, cy - 186, 2.4)), a: 0.95, glow: 6 },
    { shape: S.repeat(cx, cy, 12, S.segment(cx, cy - 70, cx, cy - 130, 1.6), Math.PI / 12), a: 0.75, glow: 4 },
    { shape: S.union(S.ring(cx, cy, 56, 3), S.ring(cx, cy, 34, 1.6)), a: 1, glow: 7 },
    { shape: S.repeat(cx, cy, 4, S.outline([[cx, cy - 150], [cx + 12, cy - 170], [cx, cy - 190], [cx - 12, cy - 170]], 2)), a: 1, glow: 5 }],
  dark: (cx, cy) => [...rim(cx, cy),
    { shape: S.star(cx, cy, 186, 7, 3, 2.4), a: 1, glow: 6 },
    { shape: S.repeat(cx, cy, 7, S.minus(S.circle(cx, cy - 150, 16), S.circle(cx + 7, cy - 155, 14))), a: 0.9, glow: 4 },
    { shape: S.union(S.ring(cx, cy, 64, 2.4), S.minus(S.circle(cx, cy, 30), S.circle(cx + 12, cy - 8, 26))), a: 1, glow: 6 }],
  astral: (cx, cy) => {
    const pts = [[-120, -60], [-60, -110], [10, -90], [70, -130], [120, -40], [80, 40], [130, 100], [20, 120], [-50, 80], [-130, 40]].map(([x, y]) => [cx + x, cy + y]);
    return [...rim(cx, cy),
      { shape: S.polyline([...pts, pts[0]], 1.4), a: 0.7, glow: 3 },
      { shape: S.union(...pts.map(([x, y]) => S.circle(x, y, 5))), a: 1, glow: 6 },
      { shape: S.polygon([[cx, cy - 70], [cx + 12, cy - 12], [cx + 70, cy], [cx + 12, cy + 12], [cx, cy + 70], [cx - 12, cy + 12], [cx - 70, cy], [cx - 12, cy - 12]]), a: 0.9, glow: 7, core: 1 },
      { shape: (x, y) => Math.abs(Math.hypot((x - cx) / 1, (y - cy) / 0.45) - 175) - 1.4, a: 0.8, glow: 4 },
      { shape: (x, y) => Math.abs(Math.hypot((x - cx) / 0.45, (y - cy) / 1) - 175) - 1.4, a: 0.8, glow: 4 }];
  },
};
const ORDER = ["fire", "earth", "water", "air", "ice", "lightning", "nature", "light", "dark", "astral"];
ORDER.forEach((name, i) => plate(i, SCHOOLS[name]));
// 10: rarity ornament (concentric rings, ticks, diamonds) — layered under higher rarities
plate(10, (cx, cy) => [
  { shape: S.union(S.ring(cx, cy, R, 2.4), S.ring(cx, cy, R - 14, 1.2), S.ring(cx, cy, 150, 1.2)), a: 1, glow: 5 },
  { shape: S.repeat(cx, cy, 72, S.segment(cx, cy - R + 2, cx, cy - R + 12, 1.2)), a: 0.8, glow: 2 },
  { shape: S.repeat(cx, cy, 8, S.outline([[cx, cy - 160], [cx + 10, cy - 174], [cx, cy - 188], [cx - 10, cy - 174]], 2)), a: 1, glow: 5 },
  { shape: S.repeat(cx, cy, 8, S.arc(cx, cy, 200, 1.6, -Math.PI / 2 + 0.1, -Math.PI / 2 + 0.68), Math.PI / 8), a: 0.85, glow: 4 }]);
// 11: dominant seal (double ring, six diamonds, glyphs)
plate(11, (cx, cy) => [
  { shape: S.union(S.ring(cx, cy, R, 2.4), S.ring(cx, cy, R - 34, 2.4)), a: 1, glow: 6 },
  { shape: glyphRing(cx, cy, R - 17, 18, 24, 2), a: 0.95, glow: 4 },
  { shape: S.repeat(cx, cy, 6, S.polygon([[cx, cy - R - 4], [cx + 9, cy - R + 17], [cx, cy - R + 38], [cx - 9, cy - R + 17]])), a: 1, glow: 6, core: 1 }]);

// ---- strips ----
function band(y0, framed, offset) {
  const parts = [];
  for (let i = 0; i < 32; i++) parts.push(glyph(i * (offset ? 11 : 1) + offset, i * 64, y0, 64, 3.2));
  const passes = [{ shape: S.union(...parts), a: 1, glow: 4 }];
  if (framed) passes.push({ shape: S.union(S.segment(0, y0 + 3, W, y0 + 3, 2), S.segment(0, y0 + 61, W, y0 + 61, 2)), a: 0.9, glow: 3 });
  paint(0, y0, W, 64, passes);
}
band(1536, false, 0);
band(1600, true, 5);
// tube profile: bright core across the middle, soft falloff to the edges (v across the tube)
for (let y = 0; y < 32; y++) for (let x = 0; x < W; x++) {
  const t = Math.abs(y + 0.5 - 16) / 16;
  alpha[(1664 + y) * W + x] = Math.min(1, Math.exp(-t * t * 9) * 0.85 + (t < 0.18 ? 0.25 : 0));
}
// filigree: diamonds joined by arcs, 16 repeats of 128 px
paint(0, 1696, W, 48, [{ shape: (x, y) => {
  const lx = ((x % 128) + 128) % 128, ly = y - 1720;
  const d1 = S.outline([[64, -14], [76, 0], [64, 14], [52, 0]], 2)(lx, ly);
  const d2 = S.arc(32, 14, 26, 1.6, Math.PI * 1.1, Math.PI * 1.9)(lx, ly);
  const d3 = S.arc(96, -14, 26, 1.6, Math.PI * 0.1, Math.PI * 0.9)(lx, ly);
  const d4 = Math.min(S.segment(0, -20, 128, -20, 1.2)(lx, ly), S.segment(0, 20, 128, 20, 1.2)(lx, ly));
  return Math.min(d1, d2, d3, d4);
}, a: 1, glow: 3 }]);
// ticks: one long and three short per 32 px
paint(0, 1744, W, 32, [{ shape: (x, y) => {
  const lx = ((x % 32) + 32) % 32, ly = y - 1744;
  return Math.min(S.segment(16, 4, 16, 28, 2)(lx, ly), S.segment(4, 12, 4, 20, 1.4)(lx, ly), S.segment(28, 12, 28, 20, 1.4)(lx, ly), S.segment(0, 16, 32, 16, 1)(lx, ly));
}, a: 1, glow: 2 }]);
// flare (soft radial) and sparkle (four-point star)
for (let y = 0; y < 128; y++) for (let x = 0; x < 128; x++) {
  const d = Math.hypot(x + 0.5 - 64, y + 0.5 - 64) / 64;
  alpha[(1792 + y) * W + x] = Math.max(0, Math.min(1, Math.exp(-d * d * 6) * 1.1 - 0.02));
}
paint(128, 1792, 128, 128, [{ shape: S.polygon([[192, 1796], [198, 1850], [252, 1856], [198, 1862], [192, 1916], [186, 1862], [132, 1856], [186, 1850]]), a: 1, glow: 8, core: 1 }]);

// ---- write: white RGB, alpha from the painted coverage ----
const rgba = Buffer.alloc(W * H * 4, 255);
for (let i = 0; i < W * H; i++) rgba[i * 4 + 3] = Math.round(Math.min(1, alpha[i]) * 255);
fs.mkdirSync(require("path").dirname(OUT), { recursive: true });
fs.writeFileSync(OUT, png(W, H, rgba));
console.log(`wrote ${OUT}`);
