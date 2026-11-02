// Blockbench → resources: the .bbmodel files in art/models are the sources of the GeckoLib models (docs/ART.md).
// Geometry and animations are exported from Blockbench by the GeckoLib plugin (or its MCP tools) to
//   assets/<modid>/geckolib/models/<type>/<id>.geo.json and geckolib/animations/<type>/<id>.animation.json;
// this script takes the rest from the .bbmodel: the texture (textures/<type>/<id>.png) and, for items, the item
// definition (items/<id>.json, GeckoLib special renderer) with its display model (models/item/<id>.json).
// It fails (exit 1) when an export is missing or older than its .bbmodel, so stale models never get committed.
// Usage: node tools/art.cjs [--check]   (--check writes nothing)
const fs = require('fs');
const path = require('path');

const root = path.join(__dirname, '..');
const artDir = path.join(root, 'art', 'models');
const assets = path.join(root, 'src', 'main', 'resources', 'assets');
const check = process.argv.includes('--check');
let problems = 0;

function write(file, data) {
    const old = fs.existsSync(file) ? fs.readFileSync(file) : null;
    if (old && Buffer.compare(old, Buffer.from(data)) === 0) return;
    if (check) { console.log(`stale: ${path.relative(root, file)}`); problems++; return; }
    fs.mkdirSync(path.dirname(file), { recursive: true });
    fs.writeFileSync(file, data);
    console.log(`wrote ${path.relative(root, file)}`);
}

for (const name of fs.existsSync(artDir) ? fs.readdirSync(artDir).filter(f => f.endsWith('.bbmodel')) : []) {
    const src = path.join(artDir, name);
    const bb = JSON.parse(fs.readFileSync(src, 'utf8'));
    const modid = bb.geckolib_modid, id = bb.model_identifier, type = (bb.geckolib_model_type || '').toLowerCase();
    if (!modid || !id || !type) { console.log(`${name}: no GeckoLib mod id / object id / model type`); problems++; continue; }
    if (`${id}.bbmodel` !== name) { console.log(`${name}: object id is "${id}", the file must be ${id}.bbmodel`); problems++; }
    const ns = path.join(assets, modid);

    const exports = [path.join(ns, 'geckolib', 'models', type, `${id}.geo.json`)];
    if ((bb.animations || []).length) exports.push(path.join(ns, 'geckolib', 'animations', type, `${id}.animation.json`));
    const srcTime = fs.statSync(src).mtimeMs;
    for (const e of exports) {
        if (!fs.existsSync(e)) { console.log(`${name}: missing export ${path.relative(root, e)}`); problems++; }
        else if (fs.statSync(e).mtimeMs + 2000 < srcTime) { console.log(`${name}: ${path.relative(root, e)} is older than the .bbmodel, export again`); problems++; }
    }

    const tex = (bb.textures || [])[0];
    if (!tex || !String(tex.source).startsWith('data:image/png;base64,')) { console.log(`${name}: no embedded PNG texture`); problems++; continue; }
    write(path.join(ns, 'textures', type, `${id}.png`), Buffer.from(tex.source.split(',')[1], 'base64'));

    if (type === 'item') {
        const json = o => JSON.stringify(o, null, 2) + '\n';
        write(path.join(ns, 'items', `${id}.json`), json({
            model: { type: 'minecraft:special', base: `${modid}:item/${id}`, model: { type: 'geckolib:geckolib' } }
        }));
        const display = path.join(ns, 'models', 'item', `${id}.json`);
        // The display model is only created once: hand/GUI transforms are tuned by hand afterwards
        if (!fs.existsSync(display)) write(display, json({
            parent: 'minecraft:item/handheld', textures: { particle: `${modid}:${type}/${id}` }
        }));
    }
}

if (problems) { console.log(`${problems} problem(s)`); process.exit(1); }
console.log('art: ok');
