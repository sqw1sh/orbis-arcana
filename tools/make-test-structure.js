// Generates data/orbisarcana/structure/test_platform.nbt: 9x6x9, stone floor at y=0, air above.
// Usage: node tools/make-test-structure.js
const fs = require("fs"), zlib = require("zlib"), path = require("path");
const DATA_VERSION = 5023; // Minecraft 26.3
const SX = 9, SY = 6, SZ = 9;

const out = [];
const u8 = v => out.push(Buffer.from([v & 0xff]));
const u16 = v => { const b = Buffer.alloc(2); b.writeUInt16BE(v); out.push(b); };
const i32 = v => { const b = Buffer.alloc(4); b.writeInt32BE(v); out.push(b); };
const str = s => { const b = Buffer.from(s, "utf8"); u16(b.length); out.push(b); };
const named = (type, name) => { u8(type); str(name); };

const compound = (fields) => { for (const f of fields) f(); u8(0); };
const intField = (name, v) => () => { named(3, name); i32(v); };
const strField = (name, v) => () => { named(8, name); str(v); };
const intList = (name, vals) => () => { named(9, name); u8(3); i32(vals.length); vals.forEach(i32); };
const compoundList = (name, items) => () => { named(9, name); u8(items.length ? 10 : 0); i32(items.length); items.forEach(compound); };

// palette: 0 = stone, 1 = air
const blocks = [];
for (let x = 0; x < SX; x++) for (let y = 0; y < SY; y++) for (let z = 0; z < SZ; z++) {
  blocks.push([intList("pos", [x, y, z]), intField("state", y === 0 ? 0 : 1)]);
}

named(10, "");
compound([
  intField("DataVersion", DATA_VERSION),
  intList("size", [SX, SY, SZ]),
  compoundList("palette", [[strField("Name", "minecraft:stone")], [strField("Name", "minecraft:air")]]),
  compoundList("blocks", blocks),
  compoundList("entities", []),
]);

const file = path.join(__dirname, "..", "src/main/resources/data/orbisarcana/structure/test_platform.nbt");
fs.writeFileSync(file, zlib.gzipSync(Buffer.concat(out)));
console.log("wrote", file);
