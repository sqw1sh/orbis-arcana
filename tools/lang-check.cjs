// Checks the RU/EN lang files (§S9): keys present in one file only, keys the code or data need but neither file has,
// and format strings whose placeholders differ between languages. Run from the project root: node tools/lang-check.cjs
// Exit code 1 if anything is missing.
const fs = require("fs");
const path = require("path");

const LANG = "src/main/resources/assets/orbisarcana/lang/";
const read = f => JSON.parse(fs.readFileSync(LANG + f, "utf8"));
const en = read("en_us.json");
const ru = read("ru_ru.json");
const walk = d => fs.readdirSync(d, { withFileTypes: true }).flatMap(e => e.isDirectory() ? walk(path.join(d, e.name)) : [path.join(d, e.name)]);

const needed = new Set();
const need = k => needed.add(k);

// literal keys in code
for (const f of walk("src/main/java").filter(f => f.endsWith(".java"))) {
  const s = fs.readFileSync(f, "utf8");
  for (const m of s.matchAll(/(?:translatable|translatableEscape|Component\.keybind)\("([a-z0-9_.]+)"/g)) need(m[1]);
  for (const m of s.matchAll(/new KeyMapping\("([a-z0-9_.]+)"/g)) need(m[1]);
  for (const m of s.matchAll(/RangedAttribute\("([a-z0-9_.]+)"/g)) need(m[1]);
}
// keys built as prefix + id in code: the ids come from the enums and data themselves
const enumIds = (file, name) => {
  const src = fs.readFileSync("src/main/java/com/orbisarcana/core/" + file, "utf8");
  const body = src.slice(src.indexOf("enum " + name));
  return [...body.slice(body.indexOf("{") + 1, body.indexOf(";")).matchAll(/^\s*([A-Z0-9_]+)/gm)].map(m => m[1].toLowerCase());
};
for (const id of enumIds("CastDenial.java", "CastDenial")) need(`cast.orbisarcana.denied.${id}`);
for (const id of enumIds("CastMode.java", "CastMode")) need(`cast_mode.orbisarcana.${id}`);
for (const id of enumIds("Targeting.java", "Targeting")) need(`targeting.orbisarcana.${id}`);
for (const id of enumIds("LearnRules.java", "Denial")) need(`message.orbisarcana.learn_denied.${id}`);
for (const m of fs.readFileSync("src/main/java/com/orbisarcana/core/Rarity.java", "utf8").matchAll(/^\s*[A-Z]+\("([a-z]+)"/gm)) need(`rarity.orbisarcana.${m[1]}`);
for (const r of ["talent", "echo2", "seal", "none"]) need(`message.orbisarcana.resonance.reward.${r}`);
for (const l of [5, 10, 15]) need(`screen.orbisarcana.grimoire.milestone.${l}`);

// spells: name, description, milestones, primary stat
for (const f of fs.readdirSync("src/main/resources/data/orbisarcana/orbisarcana/spell")) {
  const id = f.replace(".json", "");
  const def = JSON.parse(fs.readFileSync("src/main/resources/data/orbisarcana/orbisarcana/spell/" + f, "utf8"));
  need(`spell.orbisarcana.${id}`);
  need(`spell.orbisarcana.${id}.desc`);
  for (const lvl of Object.keys(def.milestones || {})) need(`spell.orbisarcana.${id}.m${lvl}`);
  need(`stat.orbisarcana.${def.primary_stat || "damage"}`);
}
// schools and their talent trees
const schools = ["fire", "earth", "water", "air", "ice", "lightning", "nature", "light", "dark", "astral"];
for (const s of schools) {
  need(`school.orbisarcana.${s}`);
  for (const node of ["echo", "root", "a1", "a2", "b1", "b2"]) { need(`talent.orbisarcana.${s}.${node}`); need(`talent.orbisarcana.${s}.${node}.desc`); }
}
// registered items, blocks and entities
const reg = fs.readFileSync("src/main/java/com/orbisarcana/registry/ModItems.java", "utf8")
  + fs.readFileSync("src/main/java/com/orbisarcana/registry/ModBlocks.java", "utf8");
// block items named with useBlockDescriptionPrefix() show the block's name
for (const m of reg.matchAll(/register(?:Simple)?Item\("([a-z0-9_]+)"[^;]*;/g)) if (!m[0].includes("useBlockDescriptionPrefix")) need(`item.orbisarcana.${m[1]}`);
for (const m of reg.matchAll(/(?:accessory|focus)\("([a-z0-9_]+)"/g)) need(`item.orbisarcana.${m[1]}`);
for (const m of reg.matchAll(/seal\("([a-z0-9_]+)"\)/g)) need(`item.orbisarcana.seal_${m[1]}`);
for (const m of reg.matchAll(/register(?:Simple)?Block\(\s*"([a-z0-9_]+)"/g)) need(`block.orbisarcana.${m[1]}`);
for (const m of fs.readFileSync("src/main/java/com/orbisarcana/registry/ModEntities.java", "utf8").matchAll(/registerEntityType\(\s*"([a-z0-9_]+)"/g)) need(`entity.orbisarcana.${m[1]}`);
// sounds: subtitles
const sounds = "src/main/resources/assets/orbisarcana/sounds.json";
if (fs.existsSync(sounds)) for (const v of Object.values(JSON.parse(fs.readFileSync(sounds, "utf8")))) if (v.subtitle) need(v.subtitle);

// temporary blocks have no item, so no name is ever shown; spell projectile is invisible
const ignored = new Set(["entity.orbisarcana.spell_projectile"]);
const problems = [];
for (const k of [...needed].sort()) {
  // a literal ending in "." is the prefix of a family checked above
  if (ignored.has(k) || k.endsWith(".") || k.endsWith("_")) continue;
  if (!(k in en)) problems.push(`missing in en_us: ${k}`);
  if (!(k in ru)) problems.push(`missing in ru_ru: ${k}`);
}
for (const k of Object.keys(en)) if (!(k in ru)) problems.push(`only in en_us: ${k}`);
for (const k of Object.keys(ru)) if (!(k in en)) problems.push(`only in ru_ru: ${k}`);
const placeholders = s => (s.match(/%(\d+\$)?[sd%]/g) || []).filter(p => p !== "%%").map(p => p.replace(/\d+\$/, "")).sort().join(",");
for (const k of Object.keys(en)) if (k in ru && placeholders(en[k]) !== placeholders(ru[k])) problems.push(`placeholders differ: ${k} (en ${placeholders(en[k]) || "-"}, ru ${placeholders(ru[k]) || "-"})`);
for (const [name, file] of [["en_us", en], ["ru_ru", ru]]) {
  for (const [k, v] of Object.entries(file)) if (/(^|[^%])%[^%sd0-9]/.test(v) && /%(\d+\$)?s/.test(v)) problems.push(`bare % next to arguments in ${name}: ${k}`);
}
// proofreading: typos a reader would notice
const plain = v => v.replace(/§./g, "").replace(/%(\d+\$)?[sd%]/g, " ");
for (const [name, file] of [["en_us", en], ["ru_ru", ru]]) {
  for (const [k, v] of Object.entries(file)) {
    const text = plain(v);
    // a Latin letter inside a Cyrillic word (or the other way round): "кoльцо" with a Latin "o"
    for (const w of text.split(/[^A-Za-zА-Яа-яЁё]+/)) if (/[А-Яа-яЁё]/.test(w) && /[A-Za-z]/.test(w)) problems.push(`mixed alphabets in ${name}: ${k} ("${w}")`);
    if (/ {2}/.test(v.replace(/\n/g, ""))) problems.push(`double space in ${name}: ${k}`);
    // " · lv. 5" style suffixes start with their own separator on purpose
    if (/^\s|\s$/.test(v) && !/^ · /.test(v)) problems.push(`leading/trailing space in ${name}: ${k}`);
  }
}
// left untranslated: the Russian text equals the English one and has English words in it
// key names and the mod's own name stay as they are
const keepsEnglish = /^(key|orbisarcana\.configuration\.title|itemGroup\.orbisarcana$)/;
for (const [k, v] of Object.entries(ru)) if (en[k] === v && /[A-Za-z]{4,}/.test(plain(v)) && !keepsEnglish.test(k)) problems.push(`untranslated in ru_ru: ${k}`);

console.log(`${needed.size} keys needed, ${Object.keys(en).length} en_us, ${Object.keys(ru).length} ru_ru`);
problems.forEach(p => console.log(p));
console.log(problems.length ? `${problems.length} problem(s)` : "lang OK");
process.exit(problems.length ? 1 : 0);
