// Shared helpers of the model spec generators in tools/specs: materials, rings of bars, crystals, pulses.
const fs = require("fs"), path = require("path");
const ROOT = path.join(__dirname, "..", "..");
const put = (id, s) => fs.writeFileSync(path.join(ROOT, "art", "specs", id + ".json"), JSON.stringify(s, null, 2) + "\n");

const metal = (base, light, dark) => ({ pattern: "metal", base, light, dark });
const gem = (base, light, dark) => ({ pattern: "gem", base, light, dark });
const glow = (base, light, dark) => ({ pattern: "glow", base, light, dark });
const GOLD = metal("#d8a838", "#fff0a0", "#7c5a14"), SILVER = metal("#b8c0cc", "#f4f8ff", "#5a6270");

/** n bars on a circle of radius r around (cx, cy) in the XY plane; every other bar a little shallower (no coplanar faces). */
function ringXY(cx, cy, r, n, thick, depth, mat, z = 0) {
  const seg = (2 * Math.PI * r) / n * 1.04, cubes = [];
  for (let i = 0; i < n; i++) {
    const a = (i / n) * 2 * Math.PI, x = cx + r * Math.cos(a), y = cy + r * Math.sin(a), d = i % 2 ? depth : depth * 0.86;
    cubes.push({ from: [x - seg / 2, y - thick / 2, z - d / 2], to: [x + seg / 2, y + thick / 2, z + d / 2], origin: [x, y, z],
      rotation: [0, 0, +(a * 180 / Math.PI + 90).toFixed(2)], mat });
  }
  return cubes;
}

/** A stepped crystal around (cx, cy, cz), turned 45 degrees about Y, its glowing core strictly inside. */
function crystal(cx, cy, cz, w, h, mat = "gem", core = "core") {
  const r = { origin: [cx, cy, cz], rotation: [0, 45, 0] };
  const box = (hw, y0, y1, m) => ({ from: [cx - hw, cy + y0, cz - hw], to: [cx + hw, cy + y1, cz + hw], ...r, mat: m });
  return [box(w, -h, h, mat), box(w * 0.66, h, h * 1.5, mat), box(w * 0.66, -h * 1.45, -h, mat), box(w * 0.5, -h * 0.7, h * 0.7, core)];
}

const pulse = (bone, amount = 1.12, length = 2.4) => ({ [bone]: { scale: [[0, [1, 1, 1]], [length / 2, [amount, amount, amount]], [length, [1, 1, 1]]] } });

module.exports = { put, metal, gem, glow, GOLD, SILVER, ringXY, crystal, pulse };
