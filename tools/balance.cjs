// Balance sheet of the spell catalogue (§14.11, §S9). Run from the project root: node tools/balance.cjs
// For every spell: damage to ONE target per cast at mastery 1 and 15, cast/cooldown/cost against the rarity corridors
// of §14.11. For every school: a 60 s single-target fight (greedy rotation of its attack spells, mana-limited) at
// mastery 15 in the dominant school, compared with the iron-sword benchmark (6 damage / 0.625 s = 9.6 DPS; a mage
// should not beat it by more than 20%). Writes docs/BALANCE.md. Exit code 1 if a spell breaks its corridor or a
// school beats the sword by more than 20%.
const fs = require("fs");

const DIR = "src/main/resources/data/orbisarcana/orbisarcana/spell/";
// MasteryMath / SchoolRules / ManaRegen / CastRules constants
const STAT_PER_LEVEL = 0.03, MAX_COST_REDUCTION = 0.12, MAX_CAST_TIME_REDUCTION = 0.10;
const DOMINANT_POWER = 1.10, FULL_SEAL_POWER = 0.05, DOMINANT_COST = 0.95;
const MAX_MANA = 100, COMBAT_REGEN = 2.0, GCD = 5;
const SWORD_DPS = 6 / 0.625, MAX_DPS = SWORD_DPS * 1.2;
const regular = lvl => { let n = 0; for (let l = 2; l <= lvl; l++) if (l % 5 !== 0) n++; return n; };
const progress = lvl => (lvl - 1) / 14;
const BURN_DPS = 1; // vanilla fire

// §14.11 corridors: mana, cast seconds, cooldown seconds, single-target damage
const CORRIDOR = {
  common: { mana: [8, 12], cast: [0, 0.8], cd: [1, 3], dmg: [5, 7] },
  uncommon: { mana: [12, 18], cast: [0.5, 1], cd: [4, 8], dmg: [6, 9] },
  rare: { mana: [15, 25], cast: [0.6, 1.2], cd: [6, 12], dmg: [8, 11] },
  epic: { mana: [25, 35], cast: [1, 1.5], cd: [10, 20], dmg: [10, 14] },
  mythic: { mana: [40, 55], cast: [2, 3], cd: [30, 60], dmg: [14, 20] },
};
// Spells whose role puts them outside a corridor on purpose (§14 table): not flagged. Channels deal their damage over the
// whole hold, so a per-cast number does not compare; a toggle's cooldown only starts when it is switched off.
const EXEMPT = {
  root_shackles: "cd", water_prison: "dmg", ice_shard: "dmg",
  tidal_stream: "cast,dmg", dawn_ray: "cast,dmg", current_shell: "cast,mana,cd", flame_dash: "cast", wind_step: "cast", granite_oath: "cast",
  shadow_step: "cast", shadow_dart: "cast", storm_anchor: "cast", guardian_orbit: "cast", purifying_aura: "cast,mana",
};

function params(def, lvl) {
  const p = { ...def.params };
  for (const [m, ms] of Object.entries(def.milestones || {})) if (lvl >= +m) Object.assign(p, ms.params || {});
  const prim = def.primary_stat || "damage";
  if (prim in p) p[prim] *= 1 + STAT_PER_LEVEL * regular(lvl);
  return p;
}

/** Damage to one target per cast; null for spells that do not damage (heals, shields, movement, control). */
function damage(def, p, castTicks, direct = false) {
  switch (def.behavior.split(":")[1]) {
    case "projectile": return p.damage * (p.count || 1) + (direct ? 0 : (p.ignite_ticks || 0) / 20 * BURN_DPS);
    case "discharge": case "spike": case "anchor": return p.damage;
    case "shard": return p.damage * (p.count || 1);
    case "dart": return p.damage * (p.count || 1) * (1 + (p.echo_hit || 0));
    case "wave": return p.damage * (1 + ((p.waves || 1) - 1) * (p.extra_wave_share || 1));
    case "blade": return p.damage * (p.returns > 0 ? 2 : 1);
    case "judgment": return p.damage + (p.linger_ticks || 0) / 10 * p.damage * 0.25;
    case "ray": return p.damage_per_pulse * Math.floor(castTicks / 5);
    case "cone": return p.damage_per_pulse * Math.floor(castTicks / 5) + (p.final_wave > 0 ? p.damage_per_pulse * 3 : 0);
    case "vortex": return p.damage_per_pulse * p.duration_ticks / 10;
    case "curse": case "seed": case "shift": return p.damage_per_second * p.duration_ticks / 20 + (p.collapse_damage || 0);
    case "roots": case "prison": return (p.damage_per_second || 0) * p.hold_ticks / 20 || null;
    // composite (V9): one target takes the damage once per bolt of a volley (pierce and chains hit others), burning after
    case "composite": {
      if (!(p.damage > 0)) return null;
      const bolts = def.compose && def.compose.shape === "bolt" ? (p.count || 1) : 1;
      return p.damage * bolts + (direct ? 0 : (p.ignite_ticks || 0) / 20 * BURN_DPS);
    }
    default: return null;
  }
}

const spells = fs.readdirSync(DIR).map(f => ({ id: f.replace(".json", ""), def: JSON.parse(fs.readFileSync(DIR + f, "utf8")) }));
const rows = [];
const problems = [];
for (const { id, def } of spells) {
  const rarity = def.rarity.split(":")[1];
  const c = CORRIDOR[rarity];
  const castSec = (def.cast.ticks || 0) / 20, cdSec = (def.cooldown_ticks || 0) / 20;
  const d1 = damage(def, params(def, 1), def.cast.ticks || 0);
  const d15 = damage(def, params(def, 15), def.cast.ticks || 0);
  const exempt = (EXEMPT[id] || "").split(",");
  const out = [];
  const check = (key, v, [lo, hi]) => { if (!exempt.includes(key) && (v < lo - 1e-9 || v > hi + 1e-9)) out.push(`${key} ${+v.toFixed(2)} ∉ [${lo}; ${hi}]`); };
  if (def.cast.mode !== "toggle") check("mana", def.cost, c.mana);
  if (def.cast.mode !== "channel") check("cast", castSec, c.cast);
  check("cd", cdSec, c.cd);
  // the corridor is about the hit itself: burning after it is not counted
  const direct1 = damage(def, params(def, 1), def.cast.ticks || 0, true);
  if (direct1 !== null) check("dmg", direct1, c.dmg);
  out.forEach(o => problems.push(`${id}: ${o}`));
  rows.push({ id, school: def.school.split(":")[1], rarity, mode: def.cast.mode, castSec, cdSec, cost: def.cost, d1, d15, notes: out.join("; ") });
}

/** A 60 s fight of a school's attack spells at mastery 15 in the dominant school (resonance 10). */
function fight(school) {
  const power = DOMINANT_POWER + FULL_SEAL_POWER;
  const list = rows.filter(r => r.school === school && r.d15 !== null && r.mode !== "toggle").map(r => {
    const def = spells.find(s => s.id === r.id).def;
    const ticks = Math.max(def.cast.ticks || 0, 0) * (1 - MAX_CAST_TIME_REDUCTION);
    return { id: r.id, dmg: r.d15 * power, ticks: Math.round(ticks) + GCD, cd: def.cooldown_ticks || 0,
      cost: def.cost * (1 - MAX_COST_REDUCTION) * DOMINANT_COST, ready: 0 };
  });
  let mana = MAX_MANA, t = 0, total = 0;
  const casts = {};
  while (t < 1200) {
    const ok = list.filter(s => s.ready <= t && s.cost <= mana).sort((a, b) => b.dmg / b.ticks - a.dmg / a.ticks);
    if (!ok.length) { t++; mana = Math.min(MAX_MANA, mana + COMBAT_REGEN / 20); continue; }
    const s = ok[0];
    mana -= s.cost; total += s.dmg; s.ready = t + s.ticks + s.cd; casts[s.id] = (casts[s.id] || 0) + 1;
    t += s.ticks; mana = Math.min(MAX_MANA, mana + COMBAT_REGEN / 20 * s.ticks);
  }
  return { dps: total / 60, casts };
}
const schools = [...new Set(rows.map(r => r.school))];
const fights = schools.map(s => ({ school: s, ...fight(s) }));
for (const f of fights) if (f.dps > MAX_DPS) problems.push(`${f.school}: ${f.dps.toFixed(1)} DPS > ${MAX_DPS.toFixed(1)} (sword +20%)`);

const n = v => v === null ? "—" : (+v.toFixed(1)).toString();
let md = `# Баланс каталога (генерируется: \`node tools/balance.cjs\`)

Эталон §14.11: железный меч ${SWORD_DPS.toFixed(1)} DPS, маг не выше +20% (${MAX_DPS.toFixed(1)} DPS). Урон — по одной цели за каст,
без бонусов школы; ур. 15 — с вехами и +3%/уровень к основной характеристике. Огонь горения — 1/с.

| Заклинание | Школа | Редкость | Режим | Каст, с | КД, с | Мана | Урон ур.1 | Урон ур.15 | Вне коридора §14.11 |
|---|---|---|---|---:|---:|---:|---:|---:|---|
`;
for (const r of rows.sort((a, b) => a.school.localeCompare(b.school) || a.id.localeCompare(b.id))) {
  md += `| ${r.id} | ${r.school} | ${r.rarity} | ${r.mode} | ${n(r.castSec)} | ${n(r.cdSec)} | ${r.cost} | ${n(r.d1)} | ${n(r.d15)} | ${r.notes || (EXEMPT[r.id] ? "роль: " + EXEMPT[r.id] : "")} |\n`;
}
md += `
## Бой 60 с по одной цели (мастерство 15, доминанта, резонанс 10, мана ${MAX_MANA} + ${COMBAT_REGEN}/с в бою)

| Школа | DPS | % от меча | Касты |
|---|---:|---:|---|
`;
for (const f of fights) md += `| ${f.school} | ${f.dps.toFixed(1)} | ${Math.round(f.dps / SWORD_DPS * 100)}% | ${Object.entries(f.casts).map(([k, v]) => `${k}×${v}`).join(", ")} |\n`;
md += `\n${problems.length ? "## Замечания\n\n" + problems.map(p => "- " + p).join("\n") : "Замечаний нет."}\n`;
fs.writeFileSync("docs/BALANCE.md", md);
console.log(md);
process.exit(problems.length ? 1 : 0);
