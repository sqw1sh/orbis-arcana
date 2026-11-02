// Generates the structure templates (NBT) of Orbis Arcana (§12). Run from the project root: node tools/structures.cjs
// Templates are built in code so they stay reviewable and reproducible; the design pass may replace them with
// hand-built ones saved from the game.
const fs = require("fs");
const zlib = require("zlib");

const DATA_VERSION = 5023; // Minecraft 26.3
const OUT = "src/main/resources/data/orbisarcana/structure/";

// ---- NBT writer (big-endian, gzip) ----
const T = { byte: 1, int: 3, string: 8, list: 9, compound: 10 };
const B = v => ({ t: T.byte, v });
const I = v => ({ t: T.int, v });
const S = v => ({ t: T.string, v });
const L = (type, items) => ({ t: T.list, type, v: items });
const C = obj => ({ t: T.compound, v: obj });
/** Plain JS values to tags: numbers are ints, strings strings, arrays of objects lists of compounds. */
const tag = x => {
  if (x && typeof x === "object" && "t" in x) return x;
  if (typeof x === "number") return I(x);
  if (typeof x === "string") return S(x);
  if (Array.isArray(x)) return L(T.compound, x.map(tag));
  return C(Object.fromEntries(Object.entries(x).map(([k, v]) => [k, tag(v)])));
};
function encode(name, root) {
  const parts = [];
  const u8 = n => { const b = Buffer.alloc(1); b.writeInt8(n); parts.push(b); };
  const u16 = n => { const b = Buffer.alloc(2); b.writeUInt16BE(n); parts.push(b); };
  const i32 = n => { const b = Buffer.alloc(4); b.writeInt32BE(n); parts.push(b); };
  const str = s => { const b = Buffer.from(s, "utf8"); u16(b.length); parts.push(b); };
  const payload = t => {
    switch (t.t) {
      case T.byte: u8(t.v); break;
      case T.int: i32(t.v); break;
      case T.string: str(t.v); break;
      case T.list: u8(t.v.length ? t.type : 0); i32(t.v.length); t.v.forEach(payload); break;
      case T.compound:
        for (const [k, v] of Object.entries(t.v)) { u8(v.t); str(k); payload(v); }
        u8(0);
        break;
    }
  };
  u8(T.compound); str(name); payload(root);
  return zlib.gzipSync(Buffer.concat(parts));
}

// ---- template builder ----
class Template {
  constructor() { this.blocks = new Map(); this.palette = []; this.keys = new Map(); }
  set(x, y, z, name, props = {}, nbt = null) {
    const key = name + JSON.stringify(props);
    if (!this.keys.has(key)) { this.keys.set(key, this.palette.length); this.palette.push({ name, props }); }
    this.blocks.set(`${x},${y},${z}`, { x, y, z, state: this.keys.get(key), nbt });
  }
  get(x, y, z) { const b = this.blocks.get(`${x},${y},${z}`); return b && this.palette[b.state].name; }
  save(file) {
    let mx = 0, my = 0, mz = 0;
    for (const b of this.blocks.values()) { mx = Math.max(mx, b.x); my = Math.max(my, b.y); mz = Math.max(mz, b.z); }
    const root = C({
      DataVersion: I(DATA_VERSION),
      size: L(T.int, [I(mx + 1), I(my + 1), I(mz + 1)]),
      palette: L(T.compound, this.palette.map(p => C(Object.assign({ id: S(p.name) },
        Object.keys(p.props).length ? { properties: C(Object.fromEntries(Object.entries(p.props).map(([k, v]) => [k, S(String(v))]))) } : {})))),
      blocks: L(T.compound, [...this.blocks.values()].map(b => C(Object.assign(
        { pos: L(T.int, [I(b.x), I(b.y), I(b.z)]), state: I(b.state) }, b.nbt ? { nbt: tag(b.nbt) } : {})))),
      entities: L(T.compound, [])
    });
    fs.writeFileSync(OUT + file, encode("", root));
    console.log(file, `${mx + 1}x${my + 1}x${mz + 1}`, this.blocks.size, "blocks");
  }
}

/** Deterministic noise in [0,1) so the ruin is the same on every run. */
const hash = (x, y, z, salt = 0) => {
  let h = (x * 374761393 + y * 668265263 + z * 2147483647 + salt * 974711) | 0;
  h = Math.imul(h ^ (h >>> 13), 1274126177);
  return ((h ^ (h >>> 16)) >>> 0) / 4294967296;
};
const chest = (loot, facing = "north", waterlogged = false) => [
  "minecraft:chest", { facing, type: "single", waterlogged }, { id: "minecraft:chest", LootTable: loot }];

// ---- Ruined Observatory: a broken stone tower on a mountain, a glyph puzzle seals the vault upstairs ----
function observatory() {
  const t = new Template();
  const cx = 6, cz = 6, R = 4.5;
  const stone = (x, y, z) => {
    const r = hash(x, y, z, 1);
    return r < 0.2 ? "minecraft:cracked_stone_bricks" : r < 0.4 ? "minecraft:mossy_stone_bricks" : "minecraft:stone_bricks";
  };
  for (let x = 0; x <= 12; x++) for (let z = 0; z <= 12; z++) {
    const d = Math.hypot(x - cx, z - cz);
    if (d > 5.6) continue;
    // foundation, buried in the slope
    for (let y = 0; y <= 2; y++) t.set(x, y, z, y < 2 ? "minecraft:cobbled_deepslate" : stone(x, y, z));
    // ground floor
    t.set(x, 3, z, d <= 3.5 ? "minecraft:polished_andesite" : stone(x, 3, z));
    for (let y = 4; y <= 15; y++) {
      const wall = d > 3.5 && d <= R;
      const ruined = y >= 11 && hash(x, y, z, 2) < 0.25 + (y - 11) * 0.15;
      if (wall && y <= 14 && !ruined) {
        const window = (y === 6 || y === 11) && (x === cx || z === cz);
        t.set(x, y, z, window ? "minecraft:air" : stone(x, y, z));
      } else if (d <= 3.5 || (wall && ruined)) {
        t.set(x, y, z, "minecraft:air");
      }
    }
    // upper floor
    if (d <= 3.5) t.set(x, 9, z, "minecraft:polished_andesite");
    // broken roof rim
    if (d > 2.5 && d <= 3.5 && hash(x, 15, z, 3) < 0.4) t.set(x, 14, z, "minecraft:stone_brick_slab", { type: "bottom", waterlogged: false });
  }
  // door (south)
  for (let y = 4; y <= 5; y++) for (const z of [10, 11]) t.set(cx, y, z, "minecraft:air");
  t.set(cx, 3, 11, "minecraft:stone_bricks");
  // ladder to the upper floor (east wall) and its hole
  for (let y = 4; y <= 9; y++) t.set(9, y, cz, "minecraft:ladder", { facing: "west", waterlogged: false });
  t.set(10, 10, cz, "minecraft:stone_bricks");
  // the puzzle: three rings on the ground floor, the key glyph set in the north wall above them
  t.set(4, 4, 6, "orbisarcana:glyph_ring", { glyph: 0, key: false });
  t.set(8, 4, 6, "orbisarcana:glyph_ring", { glyph: 1, key: false });
  t.set(6, 4, 4, "orbisarcana:glyph_ring", { glyph: 3, key: false });
  t.set(6, 6, 2, "orbisarcana:glyph_ring", { glyph: 2, key: true });
  t.set(6, 5, 2, "minecraft:chiseled_stone_bricks");
  t.set(8, 4, 8, ...chest("orbisarcana:chests/observatory_supply", "west"));
  t.set(4, 4, 8, "minecraft:lectern", { facing: "east", has_book: false, powered: false });
  t.set(4, 4, 4, "minecraft:cobweb");
  // the sealed vault upstairs
  t.set(6, 10, 6, ...chest("orbisarcana:chests/observatory_vault", "south"));
  for (const [x, y, z] of [[5, 10, 6], [7, 10, 6], [6, 10, 5], [6, 10, 7], [6, 11, 6], [6, 9, 6]]) t.set(x, y, z, "orbisarcana:arcane_seal");
  // the old telescope and star glass
  t.set(4, 10, 4, "minecraft:waxed_oxidized_copper");
  t.set(4, 11, 4, "minecraft:lightning_rod", { facing: "north", powered: false, waterlogged: false });
  t.set(8, 10, 4, "minecraft:amethyst_cluster", { facing: "up", waterlogged: false });
  t.set(8, 10, 8, "minecraft:lantern", { hanging: false, waterlogged: false });
  for (const [x, z] of [[5, 3], [7, 3], [3, 7]]) t.set(x, 13, z, "minecraft:tinted_glass");
  t.save("ruined_observatory.nbt");
}

// ---- Flooded Scriptorium: a drowned archive half sunk into the sea floor, trapped before its chest ----
function scriptorium() {
  const t = new Template();
  const W = 12, D = 10, H = 6;
  for (let x = 0; x <= W; x++) for (let z = 0; z <= D; z++) for (let y = 0; y <= H; y++) {
    const shell = x === 0 || x === W || z === 0 || z === D || y === 0 || y === H;
    if (shell) {
      const lamp = y === H && x % 4 === 2 && z % 4 === 1;
      const r = hash(x, y, z, 5);
      t.set(x, y, z, lamp ? "minecraft:sea_lantern" : y === 0 ? "minecraft:dark_prismarine"
        : r < 0.15 ? "minecraft:mossy_stone_bricks" : r < 0.3 ? "minecraft:prismarine" : "minecraft:prismarine_bricks");
    } else {
      t.set(x, y, z, "minecraft:water", { level: 0 });
    }
  }
  // entrance in the west wall (above the sea floor once placed)
  for (let y = 4; y <= 5; y++) for (let z = 4; z <= 6; z++) t.set(0, y, z, "minecraft:water", { level: 0 });
  // shelves along the long walls, gaps every few blocks
  for (let x = 2; x <= W - 3; x++) for (const z of [1, D - 1]) for (let y = 1; y <= 3; y++) {
    if (x % 4 === 0) continue;
    t.set(x, y, z, hash(x, y, z, 6) < 0.3 ? "minecraft:chiseled_bookshelf" : "minecraft:bookshelf",
      hash(x, y, z, 6) < 0.3 ? { facing: z === 1 ? "south" : "north", slot_0_occupied: true, slot_1_occupied: false, slot_2_occupied: true,
        slot_3_occupied: false, slot_4_occupied: true, slot_5_occupied: false } : {});
  }
  // reading desks
  for (const [x, z] of [[3, 4], [3, 6], [6, 4], [6, 6]]) t.set(x, 1, z, "minecraft:dark_oak_slab", { type: "top", waterlogged: true });
  // the chest-lectern at the far end, behind a trap
  t.set(11, 1, 5, ...chest("orbisarcana:chests/scriptorium", "west", true));
  t.set(11, 1, 3, "minecraft:lectern", { facing: "west", has_book: false, powered: false });
  t.set(11, 2, 5, "minecraft:sea_lantern");
  // pressure plates wash away underwater: a waterlogged sculk sensor hears the swimmer and fires the dispensers beside it
  t.set(9, 1, 5, "minecraft:sculk_sensor", { power: 0, sculk_sensor_phase: "inactive", waterlogged: true });
  const trap = () => ({ id: "minecraft:dispenser", Items: [
    { Slot: B(0), id: "minecraft:splash_potion", count: 2, components: { "minecraft:potion_contents": { potion: "minecraft:harming" } } },
    { Slot: B(1), id: "minecraft:splash_potion", count: 2, components: { "minecraft:potion_contents": { potion: "minecraft:poison" } } }] });
  t.set(9, 1, 4, "minecraft:dispenser", { facing: "west", triggered: false }, trap());
  t.set(9, 1, 6, "minecraft:dispenser", { facing: "west", triggered: false }, trap());
  t.set(5, 1, 8, ...chest("orbisarcana:chests/scriptorium_shelf", "north", true));
  t.save("flooded_scriptorium.nbt");
}

observatory();
scriptorium();
