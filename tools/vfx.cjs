// Generates the VFX sprite sheet (roadmap V4): textures/vfx/sheet.png, 512x512, 64x64 cells, white with alpha (effects
// tint it). Written both to the mod's assets and to art/vfx (the pack root of the Blockbench particle files, so the
// Blockbench preview shows the same sprites). Cells (column, row) — keep in sync with docs/ART.md:
//   row 0: 0 soft dot, 1 glow, 2 spark streak, 3 star (4 points), 4 ring, 5 smoke puff A, 6 smoke puff B, 7 ember
//   row 1: flame flipbook, 8 frames      row 2: smoke flipbook, 8 frames
//   row 3: 0-3 shards, 4 leaf, 5 droplet, 6 rune circle, 7 cross spark
//   row 4: lightning arc segments, 4 variants (2 cells wide each)
// Run from the project root: node tools/vfx.cjs
const fs = require("fs");
const path = require("path");
const { png } = require("./lib/png.cjs");
const S = require("./lib/sdf.cjs");

const N = 512, C = 64;
const alpha = new Float32Array(N * N);

/** Deterministic value noise in [0, 1). */
function hash(x, y, s) { let h = (x * 374761393 + y * 668265263 + s * 974711) | 0; h = Math.imul(h ^ (h >>> 13), 1274126177); return ((h ^ (h >>> 16)) >>> 0) / 4294967296; }
function noise(x, y, s) {
  const xi = Math.floor(x), yi = Math.floor(y), xf = x - xi, yf = y - yi;
  const sm = t => t * t * (3 - 2 * t), a = hash(xi, yi, s), b = hash(xi + 1, yi, s), c = hash(xi, yi + 1, s), d = hash(xi + 1, yi + 1, s);
  return a + (b - a) * sm(xf) + (c - a) * sm(yf) + (a - b - c + d) * sm(xf) * sm(yf);
}
function fbm(x, y, s) { let v = 0, a = 0.5; for (let o = 0; o < 4; o++) { v += a * noise(x, y, s + o * 17); x *= 2; y *= 2; a *= 0.5; } return v; }

/** Fills cell (col, row) with f(u, v) in [0, 1] where u, v are -1..1 across the cell (w cells wide). */
function cell(col, row, f, w = 1) {
  for (let y = 0; y < C; y++) for (let x = 0; x < C * w; x++) {
    const u = ((x + 0.5) / (C * w)) * 2 - 1, v = ((y + 0.5) / C) * 2 - 1;
    alpha[(row * C + y) * N + col * C + x] = Math.max(0, Math.min(1, f(u, v, x, y)));
  }
}
/** Neon stroke of an SDF in cell pixels: sharp core + glow. */
const neon = (d, core = 1.2, glow = 4) => Math.max(Math.min(1, 0.5 - d / core), 0.35 * Math.exp(-Math.max(0, d) / glow));

// row 0
cell(0, 0, (u, v) => { const r = Math.hypot(u, v); return Math.exp(-r * r * 9); });
cell(1, 0, (u, v) => { const r = Math.hypot(u, v); return Math.exp(-r * r * 4) * 0.8 + Math.exp(-r * r * 30) * 0.4; });
cell(2, 0, (u, v) => Math.exp(-u * u * 40) * Math.max(0, 1 - Math.abs(v)) ** 1.5);
cell(3, 0, (u, v) => { const a = Math.abs(u), b = Math.abs(v); return Math.exp(-(a * b) * 60) * Math.max(0, 1 - Math.hypot(u, v)) ** 1.2 + Math.exp(-(u * u + v * v) * 20) * 0.5; });
cell(4, 0, (u, v, x, y) => neon(S.ring(32, 32, 26, 2.5)(x + 0.5, y + 0.5)));
cell(5, 0, (u, v) => { const r = Math.hypot(u, v); return Math.max(0, 1 - r) ** 1.2 * (0.55 + 0.45 * fbm(u * 3 + 5, v * 3 + 5, 3)); });
cell(6, 0, (u, v) => { const r = Math.hypot(u * 1.1, v); return Math.max(0, 1 - r) ** 1.4 * (0.4 + 0.6 * fbm(u * 4 + 9, v * 4 + 2, 8)); });
cell(7, 0, (u, v) => { const r = Math.hypot(u, v); return Math.exp(-r * r * 14) + 0.25 * Math.exp(-r * r * 3); });

// row 1: flame flipbook (a tongue of fire that rises, flickers and thins out)
for (let f = 0; f < 8; f++) {
  const t = f / 7;
  cell(f, 1, (u, v) => {
    const y = (v + 1) / 2;                       // 0 top .. 1 bottom
    const width = (0.55 - 0.25 * t) * Math.pow(y, 0.6) * (1.2 - 0.5 * t);
    const sway = 0.12 * Math.sin(y * 6 + f * 1.3);
    const d = Math.abs(u - sway * (1 - y)) / Math.max(0.02, width);
    const body = Math.max(0, 1 - d) * Math.min(1, y * 3) * Math.min(1, (1 - y) * 5) * (1 - t * 0.6); // fades out before the cell edges: no hard rim when drawn large
    return body * (0.65 + 0.35 * fbm(u * 3, v * 3 - f * 0.7, 21));
  });
}
// row 2: smoke flipbook (a puff that grows, swirls and fades)
for (let f = 0; f < 8; f++) {
  const t = f / 7;
  cell(f, 2, (u, v) => {
    const r = Math.hypot(u, v) / (0.55 + 0.45 * t);
    return Math.max(0, 1 - r) ** 1.3 * (0.35 + 0.65 * fbm(u * 2.5 + f * 0.4, v * 2.5 + f * 0.3, 41)) * (1 - 0.7 * t);
  });
}
// row 3: shards, leaf, droplet, rune circle, cross spark
const shards = [[[32, 4], [42, 30], [34, 60], [24, 34]], [[30, 6], [46, 22], [36, 58], [22, 40]], [[34, 2], [40, 40], [28, 62], [26, 24]], [[20, 10], [48, 20], [40, 54], [24, 44]]];
shards.forEach((pts, i) => cell(i, 3, (u, v, x, y) => { const d = S.polygon(pts)(x + 0.5, y + 0.5); return d < 0 ? 0.55 + 0.45 * Math.min(1, -d / 6) : neon(d, 1, 2); }));
cell(4, 3, (u, v, x, y) => { const d = (Math.hypot((x - 32) / 12, (y - 32) / 26) - 1) * 12; return d < 0 ? 0.85 - 0.5 * Math.exp(-Math.abs(x - 32) * 0.8) : neon(d, 1, 1.5); });
cell(5, 3, (u, v, x, y) => { const d = S.union(S.circle(32, 40, 15), S.polygon([[32, 6], [19, 34], [45, 34]]))(x + 0.5, y + 0.5); return d < 0 ? 0.7 + 0.3 * Math.min(1, -d / 8) : neon(d, 1, 2); });
cell(6, 3, (u, v, x, y) => neon(S.union(S.ring(32, 32, 24, 2), S.star(32, 32, 20, 6, 2, 1.6))(x + 0.5, y + 0.5)));
cell(7, 3, (u, v) => Math.exp(-u * u * 120) * Math.max(0, 1 - Math.abs(v)) + Math.exp(-v * v * 120) * Math.max(0, 1 - Math.abs(u)));

// row 4: lightning arcs, 128x64 (jagged polyline with branches)
for (let k = 0; k < 4; k++) {
  const pts = [[2, 32]];
  for (let i = 1; i < 12; i++) pts.push([2 + i * 11, 32 + (hash(i, k, 77) - 0.5) * 36 * Math.sin((i / 12) * Math.PI)]);
  pts.push([126, 32]);
  const branch = [pts[5], [pts[5][0] + 16, pts[5][1] + (k % 2 ? 18 : -18)], [pts[5][0] + 30, pts[5][1] + (k % 2 ? 22 : -24)]];
  const d = S.union(S.polyline(pts, 2.2), S.polyline(branch, 1.4));
  cell(k * 2, 4, (u, v, x, y) => neon(d(x + 0.5, y + 0.5), 1.4, 5), 2);
}

const rgba = Buffer.alloc(N * N * 4, 255);
for (let i = 0; i < N * N; i++) rgba[i * 4 + 3] = Math.round(alpha[i] * 255);
const out = png(N, N, rgba);
for (const file of ["src/main/resources/assets/orbisarcana/textures/vfx/sheet.png", "art/vfx/textures/vfx/sheet.png"]) {
  fs.mkdirSync(path.dirname(file), { recursive: true });
  fs.writeFileSync(file, out);
}
console.log("wrote textures/vfx/sheet.png");
