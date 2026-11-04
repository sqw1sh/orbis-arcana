// Generates the 16x16 textures of Orbis Arcana. Run from the project root: node tools/textures.cjs
// Blocks are procedural stone with overlays, entities are painted over vanilla UV layouts; item textures come from the
// 3D models. Output: src/main/resources/assets/orbisarcana/textures/{block,entity}/*.png
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
/** An ASCII sprite: rows are padded to 16 with '.', which is transparent. */
const hash = (x, y, s) => { let h = (x * 374761393 + y * 668265263 + s * 974711) | 0; h = Math.imul(h ^ (h >>> 13), 1274126177); return ((h ^ (h >>> 16)) >>> 0) / 4294967296; };
const pick = (list, r) => list[Math.min(list.length - 1, Math.floor(r * list.length))];

// item textures come from the 3D models (art/specs, tools/specs/*.cjs, tools/art.cjs)

// ---- blocks ----
const STONE = ["#6f6f6f", "#7a7a7a", "#7f7f7f", "#868686", "#747474"];
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
// Crystal clusters: [x, y, shade] with 0 highlight, 1 body, 2 shadow
const CRYSTALS = [[3,3,0],[3,4,1],[4,4,1],[4,5,2],[2,5,1],[3,5,2],[10,2,0],[11,2,1],[11,3,1],[10,3,1],[12,4,2],[11,4,2],[12,9,0],[12,10,1],[13,10,1],[11,11,1],[12,11,2],[13,11,2],[5,11,0],[5,12,1],[6,12,1],[4,12,1],[5,13,2],[6,13,2],[8,7,0],[8,8,2]];
function ore(base, salt, streaks) {
  const img = noise(base, salt, streaks);
  CRYSTALS.forEach(([x, y, k]) => img.set(x, y, ["#c8fbff", "#5fe0e8", "#2a8a9a"][k]));
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
  ore(STONE, 1).save("block/aether_crystal_ore");
  ore(DEEPSLATE, 2, true).save("block/deepslate_aether_crystal_ore");
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
  // resonance altar
  const top = noise(DEEPSLATE, 30);
  for (let a = 0; a < 64; a++) { const t = a / 64 * Math.PI * 2; top.set(Math.round(7.5 + Math.cos(t) * 5.5), Math.round(7.5 + Math.sin(t) * 5.5), "#b26ae6"); }
  for (const [x, y] of [[7, 4], [8, 4], [4, 7], [4, 8], [11, 7], [11, 8], [7, 11], [8, 11], [7, 7], [8, 8], [7, 8], [8, 7]]) top.set(x, y, "#f0d9ff");
  top.save("block/resonance_altar_top");
  const side = noise(DEEPSLATE, 31);
  for (let x = 0; x < 16; x++) { side.set(x, 0, "#26262c"); side.set(x, 4, "#6a3896"); side.set(x, 5, x % 3 === 1 ? "#f0d9ff" : "#b26ae6"); side.set(x, 6, "#6a3896"); }
  side.save("block/resonance_altar_side");
  noise(["#26262c", "#2e2e35", "#33333a"], 32).save("block/resonance_altar_bottom");
}

// ---- entities: painted over the UV layout of vanilla models (box faces), no vanilla pixels ----
/** Paints a model box at texture offset (u, v) of size w x h x d; paint(face, x, y, fw, fh) gives each pixel's colour. */
function box(img, u, v, w, h, d, paint) {
  const faces = { top: [u + d, v, w, d], bottom: [u + d + w, v, w, d], right: [u, v + d, d, h], front: [u + d, v + d, w, h],
    left: [u + d + w, v + d, d, h], back: [u + 2 * d + w, v + d, w, h] };
  for (const [face, [fx, fy, fw, fh]] of Object.entries(faces)) {
    for (let y = 0; y < fh; y++) for (let x = 0; x < fw; x++) img.set(fx + x, fy + y, paint(face, x, y, fw, fh));
  }
}
const BARK = ["#4a3220", "#5a3d26", "#664630", "#3e2a1b", "#71503a"];
const LEAVES = ["#2f6b28", "#3c8a30", "#4fa33c", "#27581f"];
function treant() {
  const img = new Img(128, 128);
  let salt = 0;
  const bark = (face, x, y) => {
    if (face === "top" && salt < 3) return pick(LEAVES, hash(x, y, 90 + salt));
    const grain = (hash(x, 0, 70 + salt) + hash(x, Math.floor(y / 4), 71 + salt)) / 2;
    if (hash(x, y, 72 + salt) < 0.08) return pick(LEAVES, hash(y, x, 73));
    return pick(BARK, grain);
  };
  salt = 0;
  box(img, 0, 0, 8, 10, 8, (face, x, y, w, h) => {
    if (face === "front") {
      if (y === 4 && (x === 1 || x === 2 || x === 5 || x === 6)) return "#b6ff7a";
      if (y === 3 && (x === 1 || x === 2 || x === 5 || x === 6)) return "#2a1a10";
      if (y === 7 && x >= 2 && x <= 5) return "#1e140c";
    }
    if (face !== "bottom" && y < 2) return pick(LEAVES, hash(x, y, 91));
    return bark(face, x, y);
  });
  box(img, 24, 0, 2, 4, 2, (f, x, y) => pick(BARK, hash(x, y, 74)));
  salt = 1; box(img, 0, 40, 18, 12, 11, bark);
  salt = 4; box(img, 0, 70, 9, 5, 6, bark);
  salt = 5; box(img, 60, 21, 4, 30, 6, (face, x, y) => y > 25 && face !== "top" ? pick(["#2e1f14", "#3a281a"], hash(x, y, 75)) : bark(face, x, y));
  salt = 6; box(img, 60, 58, 4, 30, 6, (face, x, y) => y > 25 && face !== "top" ? pick(["#2e1f14", "#3a281a"], hash(x, y, 76)) : bark(face, x, y));
  salt = 7; box(img, 37, 0, 6, 16, 5, bark);
  salt = 8; box(img, 60, 0, 6, 16, 5, bark);
  img.save("entity/guardian_treant");
}
function decoy() {
  const img = new Img(64, 64);
  const shadow = (face, x, y, w, h) => {
    const edge = x === 0 || y === 0 || x === w - 1 || y === h - 1;
    const r = hash(x, y, 80 + w * 7 + h);
    const [cr, cg, cb] = hex(pick(["#1a0f26", "#21132f", "#281838", "#160c20"], r));
    return [cr, cg, cb, edge ? 120 : 190];
  };
  box(img, 0, 0, 8, 8, 8, (face, x, y, w, h) => face === "front" && y === 4 && (x === 1 || x === 2 || x === 5 || x === 6) ? "#d29bff" : shadow(face, x, y, w, h));
  box(img, 16, 16, 8, 12, 4, shadow);
  for (const [u, v] of [[40, 16], [0, 16], [32, 48], [16, 48]]) box(img, u, v, 4, 12, 4, shadow);
  img.save("entity/shadow_decoy");
}

fs.mkdirSync(OUT + "entity", { recursive: true });
treant();
decoy();
fs.mkdirSync(OUT + "item", { recursive: true });
fs.mkdirSync(OUT + "block", { recursive: true });
blocks();
console.log("textures written");
