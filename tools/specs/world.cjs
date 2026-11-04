// Specs of the block and creatures drawn by GeckoLib (roadmap V7): the Resonance Altar (block entity), the Guardian
// Treant and the Shadow Decoy. Blocks stand on the origin at the centre of their bottom face, creatures on their feet;
// creatures face -Z (Blockbench north). Moving parts keep clear of the rest. Run: node tools/specs/world.cjs
const { put, metal, gem, glow, GOLD, crystal, pulse } = require("./lib.cjs");

const deepslate = { pattern: "stone", base: "#46464e", light: "#64646e", dark: "#26262c" };
const bark = { pattern: "wood", base: "#5a3d26", light: "#7a5636", dark: "#2e1e12" };
const moss = { pattern: "cloth", base: "#3c7a30", light: "#5aa440", dark: "#1e4418" };
const leaves = { pattern: "leather", base: "#2f6b28", light: "#4fa33c", dark: "#17401a" };

/** n bars on a horizontal circle of radius r at height y; every other bar a little lower (no coplanar faces). */
function ringXZ(y, r, n, thick, height, mat, keep = () => true) {
  const seg = (2 * Math.PI * r) / n * 1.04, out = [];
  for (let i = 0; i < n; i++) {
    if (!keep(i)) continue;
    const a = (i / n) * 2 * Math.PI, x = r * Math.cos(a), z = r * Math.sin(a), h = i % 2 ? height : height * 0.86;
    out.push({ from: [x - seg / 2, y - h / 2, z - thick / 2], to: [x + seg / 2, y + h / 2, z + thick / 2], origin: [x, y, z],
      rotation: [0, -(a * 180 / Math.PI + 90), 0], mat });
  }
  return out;
}

// ---- Resonance Altar: a stepped deepslate pedestal with gold bands, amethyst on the corners, a glowing rune plate,
// a crystal floating and turning above it and a broken ring of runes circling the other way ----
{
  const corners = [-1, 1].flatMap(sx => [-1, 1].map(sz => [sx * 7.2, sz * 7.2]));
  put("resonance_altar", {
    id: "resonance_altar", type: "Block", density: 2,
    materials: { stone: deepslate, gold: GOLD, gem: gem("#a868f0", "#f2dcff", "#4c2290"), core: glow("#e2c4ff", "#ffffff", "#b07ae8"),
      rune: glow("#b47cff", "#f4e8ff", "#6a30c0") },
    bones: [{ name: "altar", pivot: [0, 0, 0], cubes: [
      { from: [-8, 0, -8], to: [8, 3, 8], mat: "stone" },
      { from: [-7, 2.9, -7], to: [7, 4.2, 7], mat: "stone" },
      { from: [-7.25, 3.2, -7.25], to: [7.25, 3.8, 7.25], mat: "gold" },
      { from: [-4.8, 4, -4.8], to: [4.8, 11.2, 4.8], mat: "stone" },
      { from: [-5.1, 6.6, -5.1], to: [5.1, 7.6, 5.1], mat: "gold" },
      { from: [-6.2, 11, -6.2], to: [6.2, 13, 6.2], mat: "stone" },
      { from: [-6.5, 11.9, -6.5], to: [6.5, 12.6, 6.5], mat: "gold" },
      { from: [-3.6, 12.9, -3.6], to: [3.6, 13.25, 3.6], mat: "rune" },
      ...corners.flatMap(([x, z]) => [
        { from: [x - 0.7, 4, z - 0.7], to: [x + 0.7, 9.5, z + 0.7], mat: "stone" },
        { from: [x - 0.8, 9.4, z - 0.8], to: [x + 0.8, 10, z + 0.8], mat: "gold" },
        ...crystal(x, 11.0, z, 0.55, 0.6, "gem", "core") ]),
    ] },
    { name: "crystal", parent: "altar", pivot: [0, 19, 0], cubes: crystal(0, 19, 0, 1.6, 2.2) },
    { name: "runes", parent: "altar", pivot: [0, 16.4, 0], cubes: ringXZ(16.4, 5.2, 20, 0.4, 0.9, "rune", i => i % 5 !== 4) }],
    animations: { idle: { length: 12, loop: true, bones: {
      crystal: { rotation: [[0, [0, 0, 0]], [6, [0, 180, 0]], [12, [0, 360, 0]]],
        position: [[0, [0, 0, 0]], [3, [0, 0.6, 0]], [6, [0, 0, 0]], [9, [0, 0.6, 0]], [12, [0, 0, 0]]] },
      runes: { rotation: [[0, [0, 0, 0]], [6, [0, -180, 0]], [12, [0, -360, 0]]] } } } },
  });
}

// ---- Guardian Treant: a walking trunk with root legs, long branch arms, a leafy crown and a glowing heart ----
{
  const arm = s => [ // s = 1 right (+X), -1 left
    { from: [s > 0 ? 7.6 : -11.6, 9, -2.4], to: [s > 0 ? 11.6 : -7.6, 30, 2.4], mat: "bark" },
    { from: [s > 0 ? 8.1 : -11.1, 6, -2], to: [s > 0 ? 11.1 : -8.1, 9.2, 2], mat: "root" },
    { from: [s > 0 ? 11.4 : -13.4, 20, -0.8], to: [s > 0 ? 13.4 : -11.4, 21.4, 0.8], mat: "bark" },
    { from: [s > 0 ? 7.3 : -11.9, 26.5, -2.7], to: [s > 0 ? 11.9 : -7.3, 30.4, 2.7], mat: "moss" },
  ];
  const leg = s => [{ from: [s > 0 ? 1.2 : -5.2, 0, -2.6], to: [s > 0 ? 5.2 : -1.2, 12.5, 2.6], mat: "root" },
    { from: [s > 0 ? 0.8 : -5.6, 0, -3.4], to: [s > 0 ? 5.6 : -0.8, 1.6, 2.2], mat: "bark" }];
  put("guardian_treant", {
    id: "guardian_treant", type: "Entity", density: 2,
    materials: { bark, root: { pattern: "wood", base: "#4a3220", light: "#664630", dark: "#241810" }, moss, leaves,
      eye: glow("#b8ff80", "#ffffff", "#60c040"), heart: glow("#7cff5a", "#e8ffd8", "#30a020") },
    bones: [
      { name: "root", pivot: [0, 0, 0], cubes: [] },
      { name: "body", parent: "root", pivot: [0, 12, 0], cubes: [
        { from: [-7, 12, -4.5], to: [7, 30, 4.5], mat: "bark" },
        { from: [-6, 11, -3.6], to: [6, 13, 3.6], mat: "root" },
        { from: [-7.3, 27.2, -4.8], to: [7.3, 30.3, 4.8], mat: "moss" } ] },
      { name: "heart", parent: "body", pivot: [0, 21, -4.7], cubes: [{ from: [-1.6, 19, -5.0], to: [1.6, 23, -4.4], mat: "heart" }] },
      { name: "head", parent: "body", pivot: [0, 30, 0], cubes: [
        { from: [-4.5, 30, -4], to: [4.5, 37.5, 4], mat: "bark" },
        { from: [-3, 33, -4.35], to: [-1.2, 34.6, -3.9], mat: "eye" },
        { from: [1.2, 33, -4.35], to: [3, 34.6, -3.9], mat: "eye" } ] },
      { name: "crown", parent: "head", pivot: [0, 37, 0], cubes: [
        { from: [-6, 37, -5.5], to: [6, 41, 5.5], mat: "leaves" },
        { from: [-4.2, 40.6, -4.2], to: [4.2, 43, 4.2], mat: "leaves" },
        { from: [-6.6, 36.6, -2], to: [-5.4, 39, 2], mat: "leaves" } ] },
      { name: "right_arm", parent: "body", pivot: [9.6, 29, 0], cubes: arm(1) },
      { name: "left_arm", parent: "body", pivot: [-9.6, 29, 0], cubes: arm(-1) },
      { name: "right_leg", parent: "root", pivot: [3.2, 12, 0], cubes: leg(1) },
      { name: "left_leg", parent: "root", pivot: [-3.2, 12, 0], cubes: leg(-1) },
    ],
    animations: {
      idle: { length: 4, loop: true, bones: { ...pulse("heart", 1.15, 4),
        crown: { rotation: [[0, [0, 0, -2]], [2, [0, 0, 2]], [4, [0, 0, -2]]] },
        right_arm: { rotation: [[0, [0, 0, 0]], [2, [3, 0, 2]], [4, [0, 0, 0]]] },
        left_arm: { rotation: [[0, [0, 0, 0]], [2, [3, 0, -2]], [4, [0, 0, 0]]] } } },
      walk: { length: 1.6, loop: true, bones: {
        right_leg: { rotation: [[0, [25, 0, 0]], [0.8, [-25, 0, 0]], [1.6, [25, 0, 0]]] },
        left_leg: { rotation: [[0, [-25, 0, 0]], [0.8, [25, 0, 0]], [1.6, [-25, 0, 0]]] },
        right_arm: { rotation: [[0, [-18, 0, 0]], [0.8, [18, 0, 0]], [1.6, [-18, 0, 0]]] },
        left_arm: { rotation: [[0, [18, 0, 0]], [0.8, [-18, 0, 0]], [1.6, [18, 0, 0]]] },
        body: { rotation: [[0, [0, 0, -2]], [0.8, [0, 0, 2]], [1.6, [0, 0, -2]]] } } },
      attack: { length: 0.5, loop: false, bones: {
        right_arm: { rotation: [[0, [0, 0, 0]], [0.2, [130, 0, -10]], [0.35, [-20, 0, 0]], [0.5, [0, 0, 0]]] },
        left_arm: { rotation: [[0, [0, 0, 0]], [0.2, [130, 0, 10]], [0.35, [-20, 0, 0]], [0.5, [0, 0, 0]]] },
        body: { rotation: [[0, [0, 0, 0]], [0.2, [-8, 0, 0]], [0.35, [12, 0, 0]], [0.5, [0, 0, 0]]] } } },
    },
  });
}

// ---- Shadow Decoy: a hooded shadow of the caster, its legs fading into a wisp; it floats and its sleeves drift ----
put("shadow_decoy", {
  id: "shadow_decoy", type: "Entity", density: 3,
  materials: { shade: { pattern: "cloth", base: "#2a1e3c", light: "#4a3668", dark: "#0e0818" },
    wisp: { pattern: "cloth", base: "#3a2a58", light: "#5e4a88", dark: "#1a1028" }, eye: glow("#c890ff", "#ffffff", "#7a3ad0") },
  bones: [
    { name: "root", pivot: [0, 0, 0], cubes: [] },
    { name: "body", parent: "root", pivot: [0, 12, 0], cubes: [
      { from: [-4, 12, -2], to: [4, 24, 2], mat: "shade" },
      { from: [-3.4, 5, -1.6], to: [3.4, 12.2, 1.6], mat: "wisp" },
      { from: [-2.2, 1, -1.1], to: [2.2, 5.2, 1.1], mat: "wisp" } ] },
    { name: "head", parent: "body", pivot: [0, 24, 0], cubes: [
      { from: [-4, 24, -4], to: [4, 32, 4], mat: "shade" },
      { from: [-4.5, 26, -3.6], to: [4.5, 32.6, 4.5], mat: "wisp" },
      { from: [-2.8, 27.4, -4.3], to: [-1.0, 28.4, -3.9], mat: "eye" },
      { from: [1.0, 27.4, -4.3], to: [2.8, 28.4, -3.9], mat: "eye" } ] },
    { name: "right_arm", parent: "body", pivot: [5.2, 22.5, 0], cubes: [{ from: [4.2, 11, -1.8], to: [7.8, 24, 1.8], mat: "shade" }] },
    { name: "left_arm", parent: "body", pivot: [-5.2, 22.5, 0], cubes: [{ from: [-7.8, 11, -1.8], to: [-4.2, 24, 1.8], mat: "shade" }] },
  ],
  animations: { idle: { length: 3, loop: true, bones: {
    root: { position: [[0, [0, 0, 0]], [1.5, [0, 0.8, 0]], [3, [0, 0, 0]]] },
    right_arm: { rotation: [[0, [0, 0, 4]], [1.5, [-6, 0, 8]], [3, [0, 0, 4]]] },
    left_arm: { rotation: [[0, [0, 0, -4]], [1.5, [-6, 0, -8]], [3, [0, 0, -4]]] },
    head: { rotation: [[0, [0, -4, 0]], [1.5, [3, 4, 0]], [3, [0, -4, 0]]] } } } },
});
console.log("world specs written");
