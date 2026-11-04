// Specs of the 12 accessories as 3D models (roadmap V7): art/specs/<id>.json for tools/blockbench/build-model.js.
// Rings stand in the XY plane with the bezel on top; amulets hang from a chain, the pendant on a swinging bone.
// Every model is centred on the origin (the slot centre in GUI). Run: node tools/specs/accessories.cjs
const { put, metal, gem, glow, GOLD, SILVER, ringXY, crystal, pulse } = require("./lib.cjs");

// ---- rings: band, bezel, gem; each school its metal, gem and a pair of shoulder accents ----
const RY = -1.4, R = 3.1; // band centre and radius: band from y -4.5 to 1.7, bezel up to ~4.4
function ring(id, materials, extra = {}) {
  const band = ringXY(0, RY, R, 16, 0.9, 1.5, "band");
  const top = RY + R; // top of the band centre line
  const bezel = [
    { from: [-1.5, top, -1.0], to: [1.5, top + 0.7, 1.0], mat: "band" },
    { from: [-1.1, top + 0.6, -0.75], to: [1.1, top + 1.0, 0.75], mat: "band" },
    // prongs stop short of the plate's side faces; a signet has none
    ...(extra.prongs === false ? [] : [-1, 1].flatMap(sx => [-1, 1].map(sz => ({ from: [sx * 1.15 - 0.2, top + 0.65, sz * 0.75 - 0.2], to: [sx * 1.15 + 0.2, top + 1.9, sz * 0.75 + 0.2], mat: "band" })))),
  ];
  // shoulders: accents on the band either side of the bezel
  const shoulders = [-1, 1].map(s => {
    const a = (90 + s * 32) * Math.PI / 180, x = R * Math.cos(a), y = RY + R * Math.sin(a);
    return { from: [x - 0.45, y - 0.6, -0.95], to: [x + 0.45, y + 0.6, 0.95], origin: [x, y, 0], rotation: [0, 0, s * 32], mat: "accent" };
  });
  const gemCubes = extra.gem || crystal(0, top + 2.1, 0, 0.75, 0.55);
  put(id, {
    id, type: "Item", density: 4, materials,
    bones: [{ name: "ring", pivot: [0, 0, 0], cubes: [...band, ...bezel, ...shoulders, ...(extra.cubes || [])] },
      { name: "gem", parent: "ring", pivot: extra.gemPivot || [0, top + 2.1, 0], cubes: gemCubes }, ...(extra.bones || [])],
    animations: { idle: { length: 2.4, loop: true, bones: { ...pulse("gem"), ...(extra.anim || {}) } } },
  });
}

const SCHOOLS = {
  flame: { band: GOLD, gem: gem("#ff6a22", "#ffe6a0", "#8a1a08"), core: glow("#ffb060", "#ffffff", "#ff6a20"), accent: glow("#ff8a30", "#ffe0a0", "#c83c10") },
  stone: { band: metal("#b07848", "#e8b888", "#5a3418"), gem: gem("#c89850", "#f8e0a8", "#6a4418"), core: glow("#f0c878", "#fff8e0", "#c08840"), accent: { pattern: "stone", base: "#7a7066", light: "#a49a8e", dark: "#4a423a" } },
  tide: { band: SILVER, gem: gem("#2f7bff", "#bfe0ff", "#0c2c80"), core: glow("#7ab8ff", "#ffffff", "#3a78e8"), accent: glow("#4aa0ff", "#c8e8ff", "#1a58c8") },
  wind: { band: SILVER, gem: gem("#c8f0e8", "#ffffff", "#6aa8a0"), core: glow("#e8fff8", "#ffffff", "#a8e0d8"), accent: metal("#e0f4f0", "#ffffff", "#8ab8b0") },
  frost: { band: metal("#c8dcec", "#ffffff", "#6a8aa4"), gem: gem("#9fe6ff", "#f4fdff", "#3c88b8"), core: glow("#d8f6ff", "#ffffff", "#8ad0f0"), accent: gem("#d0f4ff", "#ffffff", "#7ab8d8") },
  storm: { band: GOLD, gem: gem("#ffe14a", "#fffbd0", "#a07800"), core: glow("#fff4a0", "#ffffff", "#ffd030"), accent: glow("#a8c8ff", "#ffffff", "#5a78e8") },
  leaf: { band: metal("#c49a6a", "#f0c898", "#6a4a28"), gem: gem("#4cd65a", "#d8ffd0", "#146a20"), core: glow("#a0f0a0", "#ffffff", "#50c060"), accent: { pattern: "wood", base: "#5a8a3a", light: "#8ac060", dark: "#2a4a18" } },
};
for (const [name, m] of Object.entries(SCHOOLS)) ring(`ring_of_${name}`, m);

// echo: dark silver, twin gems apart (clear of each other and the prongs while pulsing), a small shard circling the bezel
ring("ring_of_echo", { band: metal("#5a5868", "#9a98b0", "#26242e"), gem: gem("#b07af0", "#f0dcff", "#4c2290"), core: glow("#e2c4ff", "#ffffff", "#b07ae8"), accent: gem("#8a5ad0", "#d8c0ff", "#3a1a70") }, {
  gem: [...crystal(-0.75, RY + R + 2.0, 0, 0.42, 0.45), ...crystal(0.75, RY + R + 2.0, 0, 0.42, 0.45)],
  bones: [{ name: "echo", parent: "ring", pivot: [0, RY + R + 2.0, 0], cubes: crystal(2.4, RY + R + 2.0, 0, 0.3, 0.3) }],
  anim: { echo: { rotation: [[0, [0, 0, 0]], [1.2, [0, 180, 0]], [2.4, [0, 360, 0]]] } },
});

// scribe: a flat signet with an ink seal and a small quill laid across it
ring("scribe_ring", { band: GOLD, gem: glow("#2a3a8a", "#6a7ad8", "#141c48"), core: glow("#4a5ac8", "#a8b4ff", "#2a3a8a"), accent: metal("#2a2a3a", "#4a4a60", "#141420"),
  quill: { pattern: "paper", base: "#eee8dc", light: "#ffffff", dark: "#b8b0a0" } }, {
  // the signet plate belongs to the ring (only the ink seal pulses); the quill's parts overlap instead of sharing faces
  prongs: false, gemPivot: [0, RY + R + 1.35, 0],
  gem: [{ from: [-0.55, RY + R + 1.25, -0.45], to: [0.55, RY + R + 1.45, 0.45], mat: "core" }],
  cubes: [
    { from: [-1.2, RY + R + 0.95, -0.95], to: [1.2, RY + R + 1.3, 0.95], mat: "gem" },
    { from: [-2.6, RY + R + 1.75, -0.25], to: [1.9, RY + R + 2.1, 0.25], origin: [0, RY + R + 1.9, 0], rotation: [0, 0, -18], mat: "quill" },
    { from: [-2.4, RY + R + 1.95, -0.12], to: [0.8, RY + R + 2.65, 0.12], origin: [0, RY + R + 1.9, 0], rotation: [0, 0, -18], mat: "quill" },
    { from: [1.7, RY + R + 1.8, -0.15], to: [2.6, RY + R + 2.05, 0.15], origin: [0, RY + R + 1.9, 0], rotation: [0, 0, -18], mat: "accent" },
  ],
});

// ---- amulets: chain, bail, pendant frame and a big gem ----
const PY = -2.2; // pendant centre
function amulet(id, materials, extra = {}) {
  const chain = [];
  for (const s of [-1, 1]) for (let k = 0; k < 6; k++) {
    const t = k / 5, x = s * (0.95 + 2.4 * t), y = PY + 3.6 + 4.2 * t;
    chain.push({ from: [x - 0.3, y - 0.45, -0.3 + (k % 2) * 0.05], to: [x + 0.3, y + 0.45, 0.3 - (k % 2) * 0.05], origin: [x, y, 0], rotation: [0, 0, s * -30], mat: "chain" });
  }
  const frame = [...ringXY(0, PY, 2.5, 14, 0.7, 1.0, "frame"),
    { from: [-0.35, PY + 2.7, -0.35], to: [0.35, PY + 3.6, 0.35], mat: "frame" }, ...(extra.frame || [])];
  put(id, {
    id, type: "Item", density: 4, materials,
    bones: [{ name: "amulet", pivot: [0, 0, 0], cubes: chain },
      { name: "pendant", parent: "amulet", pivot: [0, PY + 3.6, 0], cubes: frame },
      { name: "gem", parent: "pendant", pivot: [0, PY, 0], cubes: crystal(0, PY, 0, 1.05, 0.95) }, ...(extra.bones || [])],
    animations: { idle: { length: 3, loop: true, bones: {
      pendant: { rotation: [[0, [0, 0, -4]], [1.5, [0, 0, 4]], [3, [0, 0, -4]]] }, ...pulse("gem", 1.1, 3), ...(extra.anim || {}) } } },
  });
}
const chain = metal("#9aa0aa", "#e0e4ea", "#4a4e56");
amulet("mana_amulet", { chain, frame: SILVER, gem: gem("#3a8cff", "#d0ecff", "#0c2c80"), core: glow("#9ad0ff", "#ffffff", "#4a90f0") }, {
  frame: [0, 90, 180, 270].map(d => { const a = d * Math.PI / 180, x = 2.5 * Math.cos(a), y = PY + 2.5 * Math.sin(a);
    return { from: [x - 0.45, y - 0.45, -0.7], to: [x + 0.45, y + 0.45, 0.7], origin: [x, y, 0], rotation: [0, 0, 45], mat: "core" }; }),
});
amulet("flow_amulet", { chain, frame: GOLD, gem: gem("#2ad0c0", "#d0fff8", "#0a6a60"), core: glow("#8af0e0", "#ffffff", "#30c0b0") }, {
  bones: [{ name: "flow", parent: "pendant", pivot: [0, PY, 0], cubes: ringXY(0, PY, 3.2, 16, 0.35, 0.4, "core", 1.05).filter((c, i) => i % 4 !== 3) }],
  anim: { flow: { rotation: [[0, [0, 0, 0]], [1.5, [0, 0, -180]], [3, [0, 0, -360]]] } },
});
amulet("focus_amulet", { chain, frame: GOLD, gem: gem("#a868f0", "#f2dcff", "#4c2290"), core: glow("#e2c4ff", "#ffffff", "#b07ae8") }, {
  frame: [45, 135, 225, 315].map(d => { const a = d * Math.PI / 180, x = 2.15 * Math.cos(a), y = PY + 2.15 * Math.sin(a);
    return { from: [x - 0.25, y - 0.55, -0.3], to: [x + 0.25, y + 0.55, 0.3], origin: [x, y, 0], rotation: [0, 0, d + 90], mat: "frame" }; })
    .concat([{ from: [-0.35, PY - 4.0, -0.35], to: [0.35, PY - 2.8, 0.35], origin: [0, PY - 2.8, 0], rotation: [0, 45, 0], mat: "frame" }]),
});
console.log("accessory specs written");
