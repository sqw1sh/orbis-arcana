// Generates the 16x16 textures of Orbis Arcana. Run from the project root: node tools/textures.cjs
// Blocks are procedural stone with overlays; the altar, items and creatures take their textures from the 3D models.
// Output: src/main/resources/assets/orbisarcana/textures/block/*.png
const fs = require("fs");

const OUT = "src/main/resources/assets/orbisarcana/textures/";

const { png } = require("./lib/png.cjs");

// ---- image helpers ----
const hex = s => [parseInt(s.slice(1, 3), 16), parseInt(s.slice(3, 5), 16), parseInt(s.slice(5, 7), 16), s.length > 7 ? parseInt(s.slice(7, 9), 16) : 255];
class Img {
  constructor(w = 16, h = 16) { this.w = w; this.h = h; this.px = Buffer.alloc(w * h * 4); }
  set(x, y, c) { if (x < 0 || y < 0 || x >= this.w || y >= this.h || !c) return; const [r, g, b, a] = typeof c === "string" ? hex(c) : c; const i = (y * this.w + x) * 4; this.px[i] = r; this.px[i + 1] = g; this.px[i + 2] = b; this.px[i + 3] = a; }
  get(x, y) { const i = (y * this.w + x) * 4; return [this.px[i], this.px[i + 1], this.px[i + 2], this.px[i + 3]]; }
  save(path) { fs.writeFileSync(OUT + path + ".png", png(this.w, this.h, this.px)); }
}
const hash = (x, y, s) => { let h = (x * 374761393 + y * 668265263 + s * 974711) | 0; h = Math.imul(h ^ (h >>> 13), 1274126177); return ((h ^ (h >>> 16)) >>> 0) / 4294967296; };
const pick = (list, r) => list[Math.min(list.length - 1, Math.floor(r * list.length))];

// item textures come from the 3D models (art/specs, tools/specs/*.cjs, tools/art.cjs)

// ---- blocks ----
const DEEPSLATE = ["#3c3c44", "#45454d", "#4d4d55", "#36363d", "#505058"];
function noise(palette, salt, streaks = false) {
  const img = new Img();
  for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
    let r = hash(x, y, salt);
    if (streaks) r = (r + hash(0, y, salt + 1)) / 2;
    img.set(x, y, pick(palette, r));
  }
  return img;
}
// Aether ore: only the crystals, drawn over vanilla stone and deepslate by the block model (so the stone matches
// every other ore and follows resource packs). Upright shards in clusters, magenta like aether, never diamond-blue:
// [x, y, height] of each shard; a dark rim sets them off the stone.
const SHARDS = [[3,5,3],[4,4,4],[5,6,2],[10,3,3],[11,2,4],[12,4,2],[11,11,3],[12,10,4],[13,12,2],[4,12,2],[5,11,3],[8,8,3],[7,9,2]];
const AETHER = { rim: "#3a1236", body: "#d85ad0", light: "#ffc8f4", dark: "#8a2a86", glint: "#ffffff" };
function aetherOverlay() {
  const img = new Img();
  for (const [x, top, h] of SHARDS) {
    const bottom = top + h;
    for (let y = top - 1; y <= bottom; y++) for (const dx of [-1, 1]) if (img.get(x + dx, y)[3] === 0) img.set(x + dx, y, AETHER.rim);
    img.set(x, top - 1, AETHER.rim);
    img.set(x, bottom, AETHER.rim);
    for (let y = top; y < bottom; y++) img.set(x, y, y === top ? AETHER.light : y === bottom - 1 ? AETHER.dark : AETHER.body);
  }
  for (const [x, y] of [[4, 4], [11, 2], [12, 10], [8, 8]]) img.set(x, y, AETHER.glint);
  return img;
}
const GLYPHS = [
  // star, eye, moon, spiral
  ["....X.....", "....X.....", "..X.X.X...", "...XXX....", "XXXX.XXXX.", "...XXX....", "..X.X.X...", "....X.....", "....X.....", ".........."],
  ["..........", "...XXXX...", "..X....X..", ".X..XX..X.", "X..X..X..X", "X..X..X..X", ".X..XX..X.", "..X....X..", "...XXXX...", ".........."],
  ["...XXXX...", ".XX.......", "XX........", "X.........", "X.........", "X.........", "XX........", ".XX.......", "...XXXX...", ".........."],
  ["XXXXXXXX..", "X.........", "X.XXXXXX..", "X.X....X..", "X.X.XX.X..", "X.X.X..X..", "X.X.XXXX..", "X.X.......", "X.XXXXXXXX", ".........."],
];
function blocks() {
  aetherOverlay().save("block/aether_crystal_overlay");
  GLYPHS.forEach((glyph, i) => {
    const img = noise(DEEPSLATE, 10 + i);
    for (let k = 0; k < 16; k++) { img.set(k, 0, "#26262c"); img.set(k, 15, "#26262c"); img.set(0, k, "#26262c"); img.set(15, k, "#26262c"); }
    glyph.forEach((row, y) => [...row].forEach((ch, x) => { if (ch === "X") img.set(x + 3, y + 3, i === 0 ? "#bfe8ff" : "#d29bff"); }));
    img.save(`block/glyph_ring_${i}`);
  });
  const seal = noise(["#2a1638", "#331a45", "#3b1f52", "#2e1940"], 20);
  for (let k = 0; k < 16; k++) { seal.set(k, 0, "#1a0d24"); seal.set(k, 15, "#1a0d24"); seal.set(0, k, "#1a0d24"); seal.set(15, k, "#1a0d24"); }
  for (let k = 3; k <= 12; k++) { seal.set(k, k, "#d29bff"); seal.set(15 - k, k, "#d29bff"); }
  for (const [x, y] of [[7, 2], [8, 2], [2, 7], [2, 8], [13, 7], [13, 8], [7, 13], [8, 13]]) seal.set(x, y, "#f0d8ff");
  seal.save("block/arcane_seal");
}

fs.mkdirSync(OUT + "block", { recursive: true });
blocks();
console.log("textures written");
