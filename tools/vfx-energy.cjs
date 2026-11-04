// Energy textures for the volumetric effects (roadmap V8): 256x256 strips that tile in both directions, so they can
// scroll over spheres, beams, rings and trails. White with alpha (and some inner shading), tinted by the effect.
// Output: assets/orbisarcana/textures/vfx/energy/<name>.png. Run from the project root: node tools/vfx-energy.cjs
const fs = require("fs");
const path = require("path");
const { png } = require("./lib/png.cjs");

const N = 256;
const OUT = "src/main/resources/assets/orbisarcana/textures/vfx/energy";

function hash(x, y, s) { let h = (x * 374761393 + y * 668265263 + s * 974711) | 0; h = Math.imul(h ^ (h >>> 13), 1274126177); return ((h ^ (h >>> 16)) >>> 0) / 4294967296; }
const mod = (a, n) => ((a % n) + n) % n;
/** Value noise that repeats every px × py lattice cells. */
function noise(x, y, px, py, s) {
  const xi = Math.floor(x), yi = Math.floor(y), xf = x - xi, yf = y - yi;
  const sm = t => t * t * (3 - 2 * t);
  const h = (a, b) => hash(mod(a, px), mod(b, py), s);
  const a = h(xi, yi), b = h(xi + 1, yi), c = h(xi, yi + 1), d = h(xi + 1, yi + 1);
  return a + (b - a) * sm(xf) + (c - a) * sm(yf) + (a - b - c + d) * sm(xf) * sm(yf);
}
/** Tileable fBm over u, v in [0, 1): fx × fy base cells, doubling each octave. */
function fbm(u, v, fx, fy, s, oct = 4) {
  let sum = 0, amp = 0.5, norm = 0;
  for (let o = 0; o < oct; o++) {
    const kx = fx << o, ky = fy << o;
    sum += amp * noise(u * kx, v * ky, kx, ky, s + o * 31);
    norm += amp;
    amp *= 0.5;
  }
  return sum / norm;
}
const clamp = x => Math.max(0, Math.min(1, x));
const smooth = (a, b, x) => { const t = clamp((x - a) / (b - a)); return t * t * (3 - 2 * t); };

/** Tileable Voronoi: distance to the nearest and second nearest point of a cells × cells jittered grid. */
function voronoi(u, v, cells, s) {
  const x = u * cells, y = v * cells, xi = Math.floor(x), yi = Math.floor(y);
  let d1 = 9, d2 = 9;
  for (let j = -1; j <= 1; j++) for (let i = -1; i <= 1; i++) {
    const cx = xi + i, cy = yi + j;
    const px = cx + hash(mod(cx, cells), mod(cy, cells), s), py = cy + hash(mod(cx, cells), mod(cy, cells), s + 7);
    const d = Math.hypot(px - x, py - y);
    if (d < d1) { d2 = d1; d1 = d; } else if (d < d2) d2 = d;
  }
  return [d1, d2];
}

/** Each texture: f(u, v) -> [alpha, brightness]. v runs along beams and up spheres; u around them. */
const TEXTURES = {
  // tongues of flame licking along v
  flame: (u, v) => {
    const warp = fbm(u, v, 4, 2, 9);
    const tongues = fbm((u + warp * 0.12) % 1, v, 10, 2, 1);
    const a = smooth(0.45, 0.8, tongues * 0.75 + warp * 0.4);
    return [a, 0.7 + 0.3 * tongues];
  },
  // swirling filaments of raw magic
  energy: (u, v) => {
    const warp = fbm(u, v, 3, 3, 4);
    const r = 1 - Math.abs(fbm(u + warp * 0.3, v + warp * 0.3, 4, 4, 12) - 0.5) * 2;
    return [smooth(0.55, 0.95, r) * 0.9 + 0.1 * warp, 0.8 + 0.2 * r];
  },
  // thin branching crackle
  electric: (u, v) => {
    const r1 = 1 - Math.abs(fbm(u, v, 6, 6, 21, 5) - 0.5) * 2, r2 = 1 - Math.abs(fbm(u, v, 12, 12, 27, 4) - 0.5) * 2;
    return [Math.max(smooth(0.93, 0.995, r1), 0.6 * smooth(0.95, 0.997, r2)), 1];
  },
  // water caustics
  water: (u, v) => {
    const [d1, d2] = voronoi((u + fbm(u, v, 3, 3, 5) * 0.06) % 1, v, 8, 33);
    const edge = 1 - smooth(0, 0.12, d2 - d1);
    return [0.25 + 0.75 * edge, 0.7 + 0.3 * edge];
  },
  // wind streaks running along u
  wind: (u, v) => {
    const s = fbm(u, v, 2, 16, 41, 3), streak = smooth(0.5, 0.85, s) * smooth(0.2, 0.6, fbm(u, v, 4, 4, 47, 2));
    return [streak, 1];
  },
  // a band of runes between two lines, repeating along u
  runes: (u, v) => {
    const line = Math.max(smooth(0.035, 0, Math.abs(v - 0.18)), smooth(0.035, 0, Math.abs(v - 0.82)));
    const cell = Math.floor(u * 12), cu = u * 12 - cell, cv = (v - 0.3) / 0.4;
    let glyph = 0;
    if (cv > 0 && cv < 1 && cu > 0.15 && cu < 0.85) {
      for (let k = 0; k < 4; k++) {
        const kind = Math.floor(hash(cell, k, 61) * 4), w = 0.07;
        const x = 0.15 + hash(cell, k, 62) * 0.7, y = hash(cell, k, 63);
        if (kind === 0 && Math.abs(cu - x) < w) glyph = 1;
        if (kind === 1 && Math.abs(cv - y) < w * 1.4 && cu > 0.2 && cu < 0.8) glyph = 1;
        if (kind === 2 && Math.abs((cu - 0.15) / 0.7 - cv * (hash(cell, k, 64) > 0.5 ? 1 : -1) - (hash(cell, k, 64) > 0.5 ? 0 : 1)) < w * 1.4) glyph = 1;
        if (kind === 3 && Math.abs(Math.hypot(cu - 0.5, cv - 0.5) - 0.3) < w) glyph = 1;
      }
    }
    return [Math.max(line, glyph) * 0.95 + 0.05, 1];
  },
  // a starry veil: sparse stars over a faint nebula
  stars: (u, v) => {
    const neb = fbm(u, v, 3, 3, 71) * 0.35;
    const gx = Math.floor(u * 32), gy = Math.floor(v * 32), h = hash(gx, gy, 72);
    const sx = (gx + 0.5 + (hash(gx, gy, 73) - 0.5) * 0.6) / 32, sy = (gy + 0.5 + (hash(gx, gy, 74) - 0.5) * 0.6) / 32;
    const star = h > 0.82 ? Math.exp(-Math.pow(Math.hypot(u - sx, v - sy) * 32 / (0.12 + h * 0.15), 2)) : 0;
    return [clamp(neb + star), 0.7 + 0.3 * star];
  },
  // soft billows of smoke
  smoke: (u, v) => {
    const n = fbm(u, v, 4, 4, 81);
    return [smooth(0.3, 0.8, n) * 0.85, 0.6 + 0.4 * n];
  },
  // cracked ice facets
  ice: (u, v) => {
    const [d1, d2] = voronoi(u, v, 6, 91);
    const crack = 1 - smooth(0, 0.05, d2 - d1);
    return [0.3 + 0.7 * Math.max(crack, 0.35 * (1 - d1)), 0.8 + 0.2 * crack];
  },
  // a hexagonal lattice for shields and domes
  hex: (u, v) => {
    const x = u * 8, y = v * 8 * 1.1547;
    const q = [x - y * 0.5, y], cells = [];
    for (const [ox, oy] of [[0, 0], [1, 0], [0, 1], [1, 1], [-1, 0], [0, -1], [1, -1], [-1, 1]]) {
      const cx = Math.round(q[0]) + ox, cy = Math.round(q[1]) + oy;
      cells.push(Math.hypot(cx + cy * 0.5 - x, cy - y));
    }
    cells.sort((a, b) => a - b);
    const edge = 1 - smooth(0, 0.08, cells[1] - cells[0]);
    return [0.12 + 0.88 * edge, 1];
  },
  // soft rays of light running along v
  light: (u, v) => {
    const r = fbm(u, v, 12, 1, 101, 3);
    return [0.25 + 0.75 * smooth(0.4, 0.8, r), 0.85 + 0.15 * r];
  },
};

fs.mkdirSync(OUT, { recursive: true });
for (const [name, f] of Object.entries(TEXTURES)) {
  const px = Buffer.alloc(N * N * 4);
  for (let y = 0; y < N; y++) for (let x = 0; x < N; x++) {
    const [a, l] = f(x / N, y / N);
    const i = (y * N + x) * 4, lum = Math.round(clamp(l) * 255);
    px[i] = px[i + 1] = px[i + 2] = lum;
    px[i + 3] = Math.round(clamp(a) * 255);
  }
  fs.writeFileSync(path.join(OUT, name + ".png"), png(N, N, px));
}
console.log(`${Object.keys(TEXTURES).length} energy textures written`);
