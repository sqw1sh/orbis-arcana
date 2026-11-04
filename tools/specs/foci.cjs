// Specs of the 9 foci (3 types x 3 tiers) as 3D models (roadmap V7): art/specs/<id>.json for tools/blockbench/build-model.js. Run: node tools/specs/foci.cjs
const fs = require("fs"), path = require("path");
const ROOT = path.join(__dirname, "..", "..");
const put = (id, s) => { const f = path.join(ROOT, "art", "specs", id + ".json"); fs.mkdirSync(path.dirname(f), { recursive: true }); fs.writeFileSync(f, JSON.stringify(s, null, 2) + "\n"); };

const TIERS = {
  apprentice: {
    wood: { pattern: "wood", base: "#b08458", light: "#d6ad7c", dark: "#6e4a2c" },
    grip: { pattern: "leather", base: "#5c3b24", light: "#7d5434", dark: "#36210f" },
    trim: { pattern: "metal", base: "#c47a46", light: "#f2b582", dark: "#6e3c1e" },
    gem: { pattern: "gem", base: "#8fd0f6", light: "#f4fdff", dark: "#3c78b8" },
    core: { pattern: "glow", base: "#bfe8ff", light: "#ffffff", dark: "#7ab8e8" },
    cover: { pattern: "leather", base: "#6a4428", light: "#8c5e3a", dark: "#3c2412" },
  },
  amethyst: {
    wood: { pattern: "wood", base: "#4e3446", light: "#6e4c62", dark: "#2a1a26" },
    grip: { pattern: "cloth", base: "#5e2e86", light: "#8450b0", dark: "#341650" },
    trim: { pattern: "metal", base: "#d8a838", light: "#fff0a0", dark: "#7c5a14" },
    gem: { pattern: "gem", base: "#a868f0", light: "#f2dcff", dark: "#4c2290" },
    core: { pattern: "glow", base: "#e2c4ff", light: "#ffffff", dark: "#b07ae8" },
    cover: { pattern: "cloth", base: "#4e2470", light: "#7040a0", dark: "#2a1040" },
  },
  netherite: {
    wood: { pattern: "metal", base: "#3c3438", light: "#625860", dark: "#18141a" },
    grip: { pattern: "leather", base: "#2a2224", light: "#40363a", dark: "#120c0e" },
    trim: { pattern: "metal", base: "#d8a838", light: "#fff0a0", dark: "#7c5a14" },
    gem: { pattern: "gem", base: "#ff6a22", light: "#ffe6a0", dark: "#8a1a08" },
    core: { pattern: "glow", base: "#ffb060", light: "#ffffff", dark: "#ff6a20" },
    cover: { pattern: "metal", base: "#36302f", light: "#5a5254", dark: "#161214" },
  },
};
const paper = { pattern: "paper", base: "#e8dcc0", light: "#fbf4e0", dark: "#b8a888" };
const tierIndex = { apprentice: 1, amethyst: 2, netherite: 3 };

/** A ring of n bars around (cx, cy) in the XY plane (vertical), radius r. */
/** A crystal around (0, cy, 0): a square prism turned 45 deg with tapering steps up and down, a glowing core inside. */
function crystal(cy, w, h) {
  const r = { origin: [0, cy, 0], rotation: [0, 45, 0] };
  const box = (hw, y0, y1, mat) => ({ from: [-hw, cy + y0, -hw], to: [hw, cy + y1, hw], ...r, mat });
  return [box(w, -h, h, "gem"), box(w * 0.68, h, h + h * 0.45, "gem"), box(w * 0.36, h + h * 0.45, h + h * 0.8, "gem"),
    box(w * 0.68, -h - h * 0.4, -h, "gem"), box(w * 0.36, -h - h * 0.7, -h - h * 0.4, "gem"), box(w * 0.5, -h * 0.75, h * 0.75, "core")];
}

function ringXY(cx, cy, r, n, thick, depth, mat) {
  const cubes = [];
  const seg = (2 * Math.PI * r) / n * 1.04;
  for (let i = 0; i < n; i++) {
    const a = (i / n) * 2 * Math.PI, x = cx + r * Math.cos(a), y = cy + r * Math.sin(a);
    const d = i % 2 ? depth : depth * 0.86;
    cubes.push({ from: [x - seg / 2, y - thick / 2, -d / 2], to: [x + seg / 2, y + thick / 2, d / 2], origin: [x, y, 0], rotation: [0, 0, +(a * 180 / Math.PI + 90).toFixed(2)], mat });
  }
  return cubes;
}

for (const [tier, m] of Object.entries(TIERS)) {
  const t = tierIndex[tier];

  // ---- wand: 15 units, crystal head on its own bone ----
  const wandCubes = [
    { from: [-1, -0.8, -1], to: [1, 0, 1], mat: "trim" },
    { from: [-0.8, 0, -0.8], to: [0.8, 5, 0.8], mat: "grip" },
    { from: [-0.95, 1.4, -0.95], to: [0.95, 1.9, 0.95], mat: "trim" },
    { from: [-0.95, 3.4, -0.95], to: [0.95, 3.9, 0.95], mat: "trim" },
    { from: [-1.15, 5, -1.15], to: [1.15, 6, 1.15], mat: "trim" },
    { from: [-0.65, 6, -0.65], to: [0.65, 9.5, 0.65], mat: "wood" },
    { from: [-0.5, 9.5, -0.5], to: [0.5, 12.4, 0.5], mat: "wood" },
    { from: [-0.9, 12.4, -0.9], to: [0.9, 13.2, 0.9], mat: "trim" },
  ];
  if (t >= 2) for (let k = 0; k < 3; k++) wandCubes.push({ from: [-0.18, 13, 1.12], to: [0.18, 15.3, 1.46], origin: [0, 13, 0], rotation: [0, k * 120, 0], mat: "trim" }); // clear of the crystal (corner radius ~0.88)
  const wandHead = crystal(0, 0.62, 0.75);
  put(`${tier}_wand`, {
    id: `${tier}_wand`, type: "Item", density: 4, materials: m,
    bones: [{ name: "wand", pivot: [0, 0, 0], cubes: wandCubes },
      { name: "head", parent: "wand", pivot: [0, 14.6, 0], cubes: wandHead.map(c => ({ ...c, from: [c.from[0], c.from[1] + 14.6, c.from[2]], to: [c.to[0], c.to[1] + 14.6, c.to[2]], origin: [0, 14.6, 0] })) }],
    animations: { idle: { length: 4, loop: true, bones: { head: { rotation: [[0, [0, 0, 0]], [2, [0, 180, 0]], [4, [0, 360, 0]]], position: [[0, [0, 0, 0]], [2, [0, 0.4, 0]], [4, [0, 0, 0]]] } } } },
  });

  // ---- staff: 31 units, ring head with a crystal, orbiting shards from tier 2 ----
  const staffCubes = [
    { from: [-1, 0, -1], to: [1, 1.6, 1], mat: "trim" },
    { from: [-0.75, 1.6, -0.75], to: [0.75, 10.5, 0.75], mat: "wood" },
    { from: [-1, 10.5, -1], to: [1, 11.1, 1], mat: "trim" },
    { from: [-0.95, 11.1, -0.95], to: [0.95, 16, 0.95], mat: "grip" },
    { from: [-1, 16, -1], to: [1, 16.6, 1], mat: "trim" },
    { from: [-0.75, 16.6, -0.75], to: [0.75, 24, 0.75], mat: "wood" },
    { from: [-1.35, 24, -1.35], to: [1.35, 25.4, 1.35], mat: "trim" },
    // two arms rising from the collar to the ring
    { from: [-2.7, 25, -0.45], to: [-1.9, 28.4, 0.45], origin: [-2.3, 25, 0], rotation: [0, 0, 22], mat: "trim" },
    { from: [1.9, 25, -0.45], to: [2.7, 28.4, 0.45], origin: [2.3, 25, 0], rotation: [0, 0, -22], mat: "trim" },
    ...ringXY(0, 29.4, 3.3, 16, 1.0, 1.0, "trim"),
  ];
  const sy = 29.4;
  const staffHead = crystal(sy, 0.85, 1.15);
  // detail: a thin inner ring, gem studs on the outer ring, a spiral of bands on the upper shaft, a spike under the foot
  staffCubes.push(...ringXY(0, sy, 2.45, 16, 0.35, 0.5, "trim"));
  for (const deg of [45, 135, 225, 315]) {
    const a = deg * Math.PI / 180, x = 3.3 * Math.cos(a), y = sy + 3.3 * Math.sin(a);
    staffCubes.push({ from: [x - 0.45, y - 0.45, -0.68], to: [x + 0.45, y + 0.45, 0.68], origin: [x, y, 0], rotation: [0, 0, 45], mat: "gem" });
  }
  for (let k = 0; k < 3; k++) staffCubes.push({ from: [-0.85, 18.2 + k * 1.9, -0.85], to: [0.85, 18.6 + k * 1.9, 0.85], origin: [0, 18.4 + k * 1.9, 0], rotation: [0, 45, 0], mat: "trim" });
  staffCubes.push({ from: [-0.5, -1.4, -0.5], to: [0.5, 0, 0.5], origin: [0, -0.7, 0], rotation: [0, 45, 0], mat: "trim" });
  const staffBones = [{ name: "staff", pivot: [0, 0, 0], cubes: staffCubes }, { name: "crystal", parent: "staff", pivot: [0, sy, 0], cubes: staffHead }];
  const staffAnimBones = { crystal: { rotation: [[0, [0, 0, 0]], [3, [0, 180, 0]], [6, [0, 360, 0]]], position: [[0, [0, -0.2, 0]], [3, [0, 0.3, 0]], [6, [0, -0.2, 0]]] } };
  if (t >= 2) {
    const shards = [];
    for (let k = 0; k < (t === 3 ? 4 : 3); k++) {
      const a = (k / (t === 3 ? 4 : 3)) * 2 * Math.PI, x = 1.75 * Math.cos(a), z = 1.75 * Math.sin(a); // between the crystal (corner ~1.2) and the inner ring (~2.28)
      shards.push({ from: [x - 0.18, sy - 0.45, z - 0.18], to: [x + 0.18, sy + 0.45, z + 0.18], origin: [x, sy, z], rotation: [0, 45, 0], mat: "gem" });
    }
    staffBones.push({ name: "orbit", parent: "staff", pivot: [0, sy, 0], cubes: shards });
    staffAnimBones.orbit = { rotation: [[0, [0, 0, 0]], [2, [0, -180, 0]], [4, [0, -360, 0]]] };
  }
  if (t === 3) staffCubes.push(...[0, 90, 180, 270].map(r => ({ from: [-0.3, 32.4, -0.3], to: [0.3, 34.2, 0.3], origin: [0, 29.4, 0], rotation: [0, 0, r + 45], mat: "trim" })));
  put(`${tier}_staff`, { id: `${tier}_staff`, type: "Item", density: 3, materials: m, bones: staffBones,
    animations: { idle: { length: t >= 2 ? 12 : 6, loop: true, bones: staffAnimBones } } });

  // ---- tome: a book 9 x 11 x 3.2, hovering emblem on the cover ----
  const tomeCubes = [
    { from: [-4.5, 0, 1.1], to: [4.5, 11, 1.6], mat: "cover" },
    { from: [-4.5, 0, -1.6], to: [4.5, 11, -1.1], mat: "cover" },
    { from: [-5, 0, -1.6], to: [-4.4, 11, 1.6], mat: "cover" },
    { from: [-4.3, 0.4, -1.1], to: [4.2, 10.6, 1.1], mat: "paper" },
    { from: [4.2, 4.6, -1.75], to: [4.95, 6.4, 1.75], mat: "trim" },
    { from: [-5.2, 1.6, -1.8], to: [-4.3, 2.2, 1.8], mat: "trim" },
    { from: [-5.2, 8.8, -1.8], to: [-4.3, 9.4, 1.8], mat: "trim" },
  ];
  for (const [x, y] of [[3.3, 9.8], [3.3, 0], [-4.4, 9.8], [-4.4, 0]]) {
    tomeCubes.push({ from: [x, y, 1.5], to: [x + 1.3, y + 1.2, 1.8], mat: "trim" }, { from: [x, y, -1.8], to: [x + 1.3, y + 1.2, -1.5], mat: "trim" });
  }
  // emblem frame on the cover
  tomeCubes.push({ from: [-1.9, 3.4, 1.55], to: [1.9, 3.9, 1.85], mat: "trim" }, { from: [-1.9, 7.1, 1.55], to: [1.9, 7.6, 1.85], mat: "trim" },
    { from: [-1.9, 3.9, 1.55], to: [-1.4, 7.1, 1.85], mat: "trim" }, { from: [1.4, 3.9, 1.55], to: [1.9, 7.1, 1.85], mat: "trim" });
  if (t === 3) tomeCubes.push({ from: [-4.5, 0, 1.6], to: [4.5, 0.8, 1.9], mat: "trim" }, { from: [-4.5, 10.2, 1.6], to: [4.5, 11, 1.9], mat: "trim" });
  const ey = 5.5;
  put(`${tier}_tome`, {
    id: `${tier}_tome`, type: "Item", density: 3, materials: { ...m, paper },
    bones: [{ name: "book", pivot: [0, 0, 0], cubes: tomeCubes },
      { name: "emblem", parent: "book", pivot: [0, ey, 1.9], cubes: [
        { from: [-0.9, ey - 0.9, 1.7], to: [0.9, ey + 0.9, 2.3], origin: [0, ey, 2], rotation: [0, 0, 45], mat: "gem" },
        { from: [-0.4, ey - 0.4, 2.2], to: [0.4, ey + 0.4, 2.45], origin: [0, ey, 2.3], rotation: [0, 0, 45], mat: "core" }] }],
    animations: { idle: { length: 3, loop: true, bones: { emblem: { scale: [[0, [1, 1, 1]], [1.5, [1.12, 1.12, 1]], [3, [1, 1, 1]]] } } } },
  });
}
console.log("foci specs written");
