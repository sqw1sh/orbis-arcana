// Specs of the remaining mod items as 3D models (roadmap V7): spell scroll, aether shard, aether ink, mana potion,
// the three seals and the book "First Seals". Models are centred on the origin; no two faces share a plane, and only
// whole bones or parts with room around them move. Run: node tools/specs/misc.cjs
const { put, metal, gem, glow, GOLD, SILVER, ringXY, crystal, pulse } = require("./lib.cjs");
const fs = require("fs"), path = require("path");

const paper = { pattern: "paper", base: "#e8dcc0", light: "#fbf4e0", dark: "#b8a888" };
const wood = { pattern: "wood", base: "#8a5a35", light: "#b07a4a", dark: "#4a2c16" };
const stone = { pattern: "stone", base: "#5a6670", light: "#84909a", dark: "#2e363c" };
const glass = gem("#c8dce8", "#f4fbff", "#7c94a4");

/** A rough cylinder along axis "x" or "y": a box and a slightly shorter one turned 45 degrees (no shared end faces). */
function rod(axis, c, r, half, mat) {
  const [cx, cy, cz] = c, box = (h, rr) => axis === "x"
    ? { from: [cx - h, cy - rr, cz - rr], to: [cx + h, cy + rr, cz + rr] }
    : { from: [cx - rr, cy - h, cz - rr], to: [cx + rr, cy + h, cz + rr] };
  return [{ ...box(half, r), mat },
    { ...box(half - 0.08, r * 0.8), origin: c, rotation: axis === "x" ? [45, 0, 0] : [0, 45, 0], mat }];
}

/** A round disc in the XY plane: rows as wide as the circle at their height, every other row a little thinner. */
function disc(c, r, depth, mat, rows = 8) {
  const [cx, cy, cz] = c, step = (2 * r) / rows, out = [];
  for (let i = 0; i < rows; i++) {
    const y0 = cy - r + i * step, mid = y0 + step / 2 - cy, hw = Math.sqrt(Math.max(0, r * r - mid * mid)) * 1.02, d = depth / 2 - (i % 2) * 0.03;
    out.push({ from: [cx - hw, y0 - 0.01, cz - d], to: [cx + hw, y0 + step + 0.01, cz + d], mat });
  }
  return out;
}

const wobble = (bone, deg = 3, length = 3) => ({ [bone]: { rotation: [[0, [0, 0, -deg]], [length / 2, [0, 0, deg]], [length, [0, 0, -deg]]] } });

// ---- spell scroll: a parchment roll with wooden knobs, a ribbon and a glowing wax seal, the sheet half unrolled ----
{
  const ry = 2.6;
  const runes = [-1.4, -2.6, -3.6].map((y, i) => ({ from: [-3.2 + i * 0.5, y, 1.2], to: [3.0 - i * 0.7, y + 0.35, 1.4], mat: "ink" }));
  put("spell_scroll", {
    id: "spell_scroll", type: "Item", density: 4,
    materials: { paper, wood, ink: glow("#8a2be2", "#d0a8ff", "#4a1080"), ribbon: { pattern: "cloth", base: "#9a2030", light: "#c84050", dark: "#501018" },
      seal: glow("#c08aff", "#ffffff", "#7a3ad0") },
    bones: [{ name: "scroll", pivot: [0, 0, 0], cubes: [
      ...rod("x", [0, ry, 0], 1.4, 4.6, "paper"),
      { from: [-4.3, -4.2, 0.95], to: [4.3, ry, 1.25], mat: "paper" },
      ...runes,
      ...rod("x", [-5.1, ry, 0], 0.75, 0.7, "wood"), ...rod("x", [5.1, ry, 0], 0.75, 0.7, "wood"),
      ...rod("x", [-6.0, ry, 0], 0.95, 0.3, "wood"), ...rod("x", [6.0, ry, 0], 0.95, 0.3, "wood"),
      ...rod("x", [0, ry, 0], 1.55, 0.55, "ribbon"),
    ] }, { name: "seal", parent: "scroll", pivot: [0, ry, 1.65], cubes: disc([0, ry, 1.62], 0.8, 0.45, "seal") }],
    animations: { idle: { length: 2.4, loop: true, bones: pulse("seal", 1.12, 2.4) } },
  });
}

// ---- aether shard: a crystal cluster on a lump of stone, the whole cluster turning slowly ----
put("aether_shard", {
  id: "aether_shard", type: "Item", density: 4,
  materials: { stone, gem: gem("#d85ad0", "#ffc8f4", "#8a2a86"), core: glow("#ffc8f4", "#ffffff", "#d85ad0") },
  bones: [{ name: "cluster", pivot: [0, 0, 0], cubes: [
    { from: [-2.6, -3.6, -1.7], to: [2.6, -2.3, 1.7], mat: "stone" },
    { from: [-1.9, -3.9, -1.2], to: [2.1, -3.4, 1.3], mat: "stone" },
    ...crystal(0, 0.2, 0, 1.25, 1.8) ] },
    { name: "left", parent: "cluster", pivot: [-1.5, -2.6, 0], rotation: [0, 0, 28], cubes: crystal(-1.5, -1.2, 0, 0.6, 0.85) },
    { name: "right", parent: "cluster", pivot: [1.6, -2.6, 0.3], rotation: [12, 0, -32], cubes: crystal(1.6, -1.4, 0.3, 0.5, 0.7) }],
  animations: { idle: { length: 8, loop: true, bones: { cluster: { rotation: [[0, [0, 0, 0]], [4, [0, 180, 0]], [8, [0, 360, 0]]] } } } },
});

// ---- aether ink: a squat bottle of glowing violet ink, glass rims, a cork and a label ----
put("aether_ink", {
  id: "aether_ink", type: "Item", density: 4,
  materials: { glass, ink: glow("#7a3ad0", "#d0a8ff", "#3a1068"), wood, paper },
  bones: [{ name: "bottle", pivot: [0, -4, 0], cubes: [
    ...rod("y", [0, -1.6, 0], 2.3, 2.3, "ink"),
    ...rod("y", [0, -3.75, 0], 2.45, 0.35, "glass"), ...rod("y", [0, 0.55, 0], 2.45, 0.35, "glass"),
    ...rod("y", [0, 1.5, 0], 1.0, 1.0, "glass"),
    ...rod("y", [0, 2.6, 0], 1.2, 0.75, "wood"),
    { from: [-1.5, -2.8, 2.3], to: [1.5, -0.6, 2.72], mat: "paper" },
  ] }],
  animations: { idle: { length: 3, loop: true, bones: wobble("bottle", 3) } },
});

// ---- mana potion: a round flask of glowing blue mana, a glass neck and a cork ----
put("mana_potion", {
  id: "mana_potion", type: "Item", density: 4,
  materials: { glass, mana: glow("#2f6cff", "#c8dcff", "#10308a"), wood },
  bones: [{ name: "flask", pivot: [0, -4.5, 0], cubes: [
    ...rod("y", [0, -2.3, 0], 2.5, 1.7, "mana"),
    ...rod("y", [0, -4.15, 0], 1.8, 0.45, "mana"),
    ...rod("y", [0, -0.3, 0], 1.85, 0.6, "mana"),
    ...rod("y", [0, 1.4, 0], 0.85, 1.3, "glass"),
    ...rod("y", [0, 2.55, 0], 1.1, 0.25, "glass"),
    ...rod("y", [0, 3.2, 0], 0.7, 0.6, "wood"),
  ] }],
  animations: { idle: { length: 3, loop: true, bones: wobble("flask", 3) } },
});

// ---- seals: a round medallion with a rim and a raised emblem that pulses ----
function seal(school, materials, emblem) {
  const id = `seal_${school}`;
  put(id, {
    id, type: "Item", density: 4, materials,
    bones: [{ name: "seal", pivot: [0, 0, 0], cubes: [...disc([0, 0, 0], 3.0, 1.0, "disc"), ...ringXY(0, 0, 3.1, 18, 0.7, 1.4, "rim")] },
      { name: "emblem", parent: "seal", pivot: [0, 0, 0.7], cubes: emblem }],
    animations: { idle: { length: 2.4, loop: true, bones: pulse("emblem", 1.08, 2.4) } },
  });
}
// emblem parts over the disc; crossing parts stand out by different amounts (dz), so their front faces never meet
const front = (from, to, mat, rot = 0, dz = 0) => ({ from: [from[0], from[1], 0.45], to: [to[0], to[1], 0.85 + dz], origin: [0, 0, 0.65], rotation: [0, 0, rot], mat });
seal("light", { disc: GOLD, rim: metal("#f0d070", "#fffbe0", "#9a7420"), mark: glow("#fff4c0", "#ffffff", "#ffd860") }, [
  front([-0.9, -0.9], [0.9, 0.9], "mark", 45, 0.3),
  ...[0, 45, 90, 135].map((deg, i) => front([-0.22, -2.3 + (i % 2) * 0.4], [0.22, 2.3 - (i % 2) * 0.4], "mark", deg, i * 0.06)),
]);
seal("dark", { disc: metal("#3a2450", "#6a4a88", "#180c26"), rim: metal("#5a5868", "#9a98b0", "#26242e"), mark: glow("#b070f0", "#f0dcff", "#5a20a0") },
  // a crescent: an arc of bars, open to the right, and a small star in its hollow
  ringXY(-0.3, 0, 1.7, 14, 0.6, 0.4, "mark", 0.65).filter((c, i) => i >= 4 && i <= 10)
    .concat([front([0.5, -0.3], [1.1, 0.3], "mark", 45)]));
seal("astral", { disc: metal("#1c2a6a", "#3a54b0", "#0a1238"), rim: SILVER, mark: glow("#bfe0ff", "#ffffff", "#5a8ae0") }, [
  front([-0.3, -2.4], [0.3, 2.4], "mark", 0, 0.08), front([-2.4, -0.3], [2.4, 0.3], "mark"),
  front([-0.25, -1.4], [0.25, 1.4], "mark", 45, -0.06), front([-0.25, -1.4], [0.25, 1.4], "mark", -45, -0.12),
  front([-0.6, -0.6], [0.6, 0.6], "mark", 45, 0.25),
]);

// ---- First Seals: the apprentice tome's shape in red leather and gold, a seal on the cover ----
{
  const tome = JSON.parse(fs.readFileSync(path.join(__dirname, "..", "..", "art", "specs", "apprentice_tome.json"), "utf8"));
  put("first_seals", { ...tome, id: "first_seals", materials: { ...tome.materials,
    cover: { pattern: "leather", base: "#6a2424", light: "#8c3a3a", dark: "#3a1010" }, trim: GOLD,
    gem: gem("#c08aff", "#f0dcff", "#5a20a0"), core: glow("#e2c4ff", "#ffffff", "#b07ae8") } });
}
console.log("misc specs written");
