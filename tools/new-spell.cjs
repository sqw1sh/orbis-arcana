// Scaffolds a new spell of the composite behaviour (roadmap V9) in one pass: the spell JSON with params in the middle
// of its rarity's corridor (§14.11) and three milestones, RU/EN names, descriptions and milestone texts, a magic circle
// copied from a spell of the same school, and the visuals (templates tinted with the school's colour). The generic
// GameTests (every composite spell hits, every milestone changes its params) cover it without new code.
//
// Usage: node tools/new-spell.cjs <id> --school fire --shape bolt|beam|cone|burst|nova|target|self
//          --effects damage,ignite [--rarity common] [--affects enemies|allies|self] [--mode charged|instant]
//          --ru "Название" --en "Name" --ru-desc "Описание." --en-desc "Description."
// Then: node tools/balance.cjs, node tools/lang-check.cjs, ./gradlew :runGameTestServer. Tune the JSON by hand afterwards.
const fs = require("fs");
const path = require("path");

const ROOT = path.join(__dirname, "..");
const SPELLS = path.join(ROOT, "src/main/resources/data/orbisarcana/orbisarcana/spell");
const CIRCLES = path.join(ROOT, "src/main/resources/assets/orbisarcana/orbisarcana/circle");
const LANG = path.join(ROOT, "src/main/resources/assets/orbisarcana/lang");

const SCHOOLS = { fire: 0xFF6A2A, earth: 0xC09050, water: 0x3FA9F5, air: 0xC8F5EE, ice: 0x9EE4FF, lightning: 0xFFE95A,
  nature: 0x6BD66B, light: 0xFFF2B0, dark: 0x9B5CD6, astral: 0x7A8CFF };
const CIRCLE_OF = { fire: "fireball", earth: "stone_spear", water: "tidal_stream", air: "wind_blade", ice: "ice_shard", lightning: "discharge",
  nature: "thorn_seed", light: "dawn_ray", dark: "shadow_dart", astral: "star_spike" };
// middle of each rarity's corridor (§14.11): mana, cast seconds, cooldown seconds, single-target damage
const CORRIDOR = { common: [10, 0.5, 2, 6], uncommon: [15, 0.75, 6, 7.5], rare: [20, 0.9, 9, 9.5], epic: [30, 1.25, 15, 12], mythic: [47, 2.5, 45, 17] };
const SHAPE = {
  bolt: { targeting: "directional", params: { speed: 1.6, range: 28, count: 1, spread_deg: 0, pierce: 0, chain: 0, chain_range: 5, explode_radius: 0 } },
  beam: { targeting: "directional", params: { range: 20, pierce: 0 } },
  cone: { targeting: "directional", params: { range: 6, angle_deg: 50 } },
  burst: { targeting: "ground", params: { radius: 3 } },
  nova: { targeting: "self", params: { radius: 4 } },
  target: { targeting: "target", params: {} },
  self: { targeting: "self", params: {} },
};
const EFFECT = { damage: ["damage", null], ignite: ["ignite_ticks", 60], slow: ["slow_ticks", 60], freeze: ["freeze_ticks", 40], knockback: ["knockback", 1],
  pull: ["pull", 0.8], lift: ["lift", 0.6], weaken: ["weaken_ticks", 80], blind: ["blind_ticks", 40], heal: ["heal", 6] };
// how a milestone reads in the grimoire
const SAY = {
  count: [n => `${n} снаряда веером`, n => `${n} bolts in a fan`],
  pierce: [n => `пробивает ${n} цели`, n => `pierces ${n} targets`],
  explode_radius: [n => `взрыв радиусом ${n} при попадании`, n => `explodes in a ${n}-block radius on hit`],
  chain: [n => `перескакивает на ${n} цели`, n => `jumps on to ${n} more targets`],
  angle_deg: [n => `конус шире (${n}°)`, n => `wider cone (${n}°)`],
  range: [n => `дальность ${n} блоков`, n => `${n}-block range`],
  radius: [n => `радиус ${n} блоков`, n => `${n}-block radius`],
  ticks: [(n, e) => `${e.ru} дольше (${n / 20} с)`, (n, e) => `longer ${e.en} (${n / 20} s)`],
  amount: [(n, e) => `сильнее ${e.ru} (${n})`, (n, e) => `stronger ${e.en} (${n})`],
};
const EFFECT_WORD = { ignite_ticks: { ru: "поджог", en: "burning" }, slow_ticks: { ru: "замедление", en: "slow" }, freeze_ticks: { ru: "заморозка", en: "freeze" },
  weaken_ticks: { ru: "слабость", en: "weakness" }, blind_ticks: { ru: "слепота", en: "blindness" }, knockback: { ru: "отбрасывание", en: "knockback" },
  pull: { ru: "притяжение", en: "pull" }, lift: { ru: "подброс", en: "lift" }, heal: { ru: "лечение", en: "healing" } };

function args() {
  const a = process.argv.slice(2), out = { id: a[0] };
  for (let i = 1; i < a.length; i += 2) out[a[i].replace(/^--/, "")] = a[i + 1];
  return out;
}

const o = args();
const fail = msg => { console.error("new-spell: " + msg); process.exit(1); };
if (!o.id || !/^[a-z0-9_]+$/.test(o.id)) fail("first argument: the spell id (a-z, 0-9, _)");
if (!SCHOOLS[o.school]) fail("--school one of " + Object.keys(SCHOOLS));
if (!SHAPE[o.shape]) fail("--shape one of " + Object.keys(SHAPE));
const rarity = o.rarity || "common";
if (!CORRIDOR[rarity]) fail("--rarity one of " + Object.keys(CORRIDOR));
const effects = (o.effects || "damage").split(",").map(e => e.trim());
for (const e of effects) if (!EFFECT[e]) fail("unknown effect " + e + ", one of " + Object.keys(EFFECT));
for (const k of ["ru", "en", "ru-desc", "en-desc"]) if (!o[k]) fail(`--${k} is required`);
const file = path.join(SPELLS, o.id + ".json");
if (fs.existsSync(file)) fail(o.id + " already exists");

const [mana, castSec, cdSec, dmg] = CORRIDOR[rarity];
const area = ["burst", "nova", "cone"].includes(o.shape);
const params = { ...SHAPE[o.shape].params };
for (const e of effects) {
  const [p, def] = EFFECT[e];
  params[p] = e === "damage" ? Math.round(dmg * (area ? 0.75 : 1) * 10) / 10 : e === "heal" ? Math.round(dmg * 10) / 10 : def;
  if (e === "slow") params.slow_level = 2;
}
const primary = effects.includes("damage") ? "damage" : effects.includes("heal") ? "heal" : EFFECT[effects[0]][0];

// milestones: 5 and 15 strengthen the shape, 10 the effects (only params the spell declares)
const ms = {}, text = {};
const set = (lvl, key, value, say) => { ms[lvl] = { ...(ms[lvl] || {}), [key]: value }; text[lvl] = say; return true; };
const shapeBonus = (lvl, big) => {
  const sh = o.shape;
  if (sh === "bolt") return big ? set(lvl, "explode_radius", 2.5, "explode_radius") : (set(lvl, "count", 2, "count"), (ms[lvl].spread_deg = 10), true);
  if (sh === "beam") return set(lvl, "pierce", big ? 4 : 2, "pierce");
  if (sh === "cone") return big ? set(lvl, "range", params.range + 3, "range") : set(lvl, "angle_deg", params.angle_deg + 20, "angle_deg");
  if (sh === "burst" || sh === "nova") return set(lvl, "radius", params.radius + (big ? 2 : 1), "radius");
  return null;
};
const effectBonus = lvl => {
  const p = Object.keys(params).find(k => EFFECT_WORD[k] && k !== primary) || Object.keys(params).find(k => EFFECT_WORD[k]);
  if (!p) return null;
  return set(lvl, p, Math.round(params[p] * 1.5 * 10) / 10, p.endsWith("_ticks") ? "ticks:" + p : "amount:" + p);
};
shapeBonus(5, false) ?? effectBonus(5);
effectBonus(10) ?? shapeBonus(10, false);
if (!shapeBonus(15, true)) { const p = Object.keys(params).find(k => EFFECT_WORD[k]) || primary; set(15, p, Math.round(params[p] * 2 * 10) / 10, (p.endsWith("_ticks") ? "ticks:" : "amount:") + p); }
for (const lvl of Object.keys(ms)) for (const k of Object.keys(ms[lvl])) if (!(k in params)) delete ms[lvl][k];

const tint = SCHOOLS[o.school];
const V = n => "orbisarcana:" + n;
const vis = {
  bolt: { travel: [V("tmpl_orb"), V("tmpl_trail")], impact: [V("tmpl_nova"), V("tmpl_burst")] },
  beam: { travel: [V("tmpl_beam")], impact: [V("tmpl_burst")] },
  cone: { travel: [V("tmpl_cone")], impact: [V("tmpl_burst")] },
  burst: { impact: [V("tmpl_nova"), V("tmpl_wave"), V("tmpl_burst")] },
  nova: { impact: [V("tmpl_wave"), V("tmpl_nova")] },
  target: { impact: [V("tmpl_nova"), V("tmpl_burst")] },
  self: { impact: [V("tmpl_nova")] },
}[o.shape];

const mode = o.mode || (castSec > 0 && o.shape !== "self" ? "charged" : "instant");
const ticks = Math.round(castSec * 20);
const spell = {
  school: "orbisarcana:" + o.school,
  rarity: "orbisarcana:" + rarity,
  behavior: "orbisarcana:composite",
  targeting: SHAPE[o.shape].targeting,
  cast: mode === "instant" ? { mode: "instant" } : { mode: "charged", ticks, min_ticks: Math.max(1, Math.round(ticks * 0.6)) },
  cost: mana,
  cooldown_ticks: Math.round(cdSec * 20),
  primary_stat: primary,
  params,
  milestones: Object.fromEntries(Object.entries(ms).map(([l, p]) => [l, { params: p }])),
  circle: "orbisarcana:" + o.id,
  xp_category: effects.includes("heal") ? "support" : effects.includes("damage") ? "attack" : "control",
  ...(o.affects === "allies" || effects.includes("heal") ? { allow_self: true } : {}),
  compose: { shape: o.shape, ...(o.affects ? { affects: o.affects } : effects.includes("heal") ? { affects: "allies" } : {}),
    effects: effects.map(type => ({ type })), visuals: { ...vis, tint } },
};
fs.writeFileSync(file, JSON.stringify(spell, null, 2) + "\n");

// circle: the school's template circle
fs.copyFileSync(path.join(CIRCLES, CIRCLE_OF[o.school] + ".json"), path.join(CIRCLES, o.id + ".json"));

// lang: name, description, milestones
for (const [lang, li, name, desc] of [["ru_ru", 0, o.ru, o["ru-desc"]], ["en_us", 1, o.en, o["en-desc"]]]) {
  const f = path.join(LANG, lang + ".json");
  const json = JSON.parse(fs.readFileSync(f, "utf8"));
  json[`spell.orbisarcana.${o.id}`] = name;
  json[`spell.orbisarcana.${o.id}.desc`] = desc;
  for (const [lvl, say] of Object.entries(text)) {
    const [kind, p] = say.split(":");
    const value = ms[lvl][p || kind];
    const word = EFFECT_WORD[p] || { ru: p, en: p };
    json[`spell.orbisarcana.${o.id}.m${lvl}`] = SAY[kind][li](value, word);
  }
  fs.writeFileSync(f, JSON.stringify(json, null, 2) + "\n");
}
console.log(`spell ${o.id}: ${rarity} ${o.school} ${o.shape} (${effects.join(", ")}), milestones ${Object.keys(ms).join("/")} -> ${path.relative(ROOT, file)}`);
