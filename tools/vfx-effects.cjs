// Writes the stock VFX effects and the spells' signature effects (roadmap V8) as Snowstorm particle files into art/vfx/particles (Blockbench opens them;
// node tools/art.cjs copies them into the mod). Each sends 10 particles from a box of half-size 1 in white, so the
// server (spell/Fx) scales count and spread like a vanilla burst and tints them. Sprites: textures/vfx/sheet.png,
// 64x64 cells (tools/vfx.cjs). Run from the project root: node tools/vfx-effects.cjs
const fs = require("fs");
const path = require("path");

const OUT = path.join(__dirname, "..", "art", "vfx", "particles");
const CELL = 64;
const LIFE = "variable.particle_age / variable.particle_lifetime";
const rnd = (a, b) => `math.random(${a}, ${b})`;
/** Linear over the particle's life from a to b, with per-particle variation r (0..1 of a). */
const overLife = (a, b, r = 0) => r ? `math.lerp(${a}, ${b}, ${LIFE}) * (1 + ${r} * (variable.particle_random_1 - 0.5))` : `math.lerp(${a}, ${b}, ${LIFE})`;

/** Alpha curve stops [t, a] in white; rgb stops [t, r, g, b, a] keep their colour. */
function gradient(stops) {
  const g = {};
  for (const s of stops) g[s[0].toFixed(2)] = s.length === 2 ? [1, 1, 1, s[1]] : s.slice(1);
  return { color: { gradient: g, interpolant: LIFE } };
}

function effect(id, o) {
  const c = {};
  if (o.steady) {
    // a looping stream (attached effects: it runs while its entity lives)
    c["minecraft:emitter_rate_steady"] = { spawn_rate: o.steady[0], max_particles: o.steady[1] };
    c["minecraft:emitter_lifetime_looping"] = { active_time: 1 };
  } else {
    c["minecraft:emitter_rate_instant"] = { num_particles: o.count ?? 10 };
    c["minecraft:emitter_lifetime_once"] = { active_time: 0.05 };
  }
  if (o.local) c["minecraft:emitter_local_space"] = { position: true, rotation: false };
  if (o.sphere !== undefined) c["minecraft:emitter_shape_sphere"] = { radius: o.sphere, direction: o.dir ?? "outwards" };
  else if (o.disc) c["minecraft:emitter_shape_disc"] = { radius: 1, plane_normal: [0, 1, 0], surface_only: !!o.ringOnly, direction: o.dir ?? "outwards" };
  else c["minecraft:emitter_shape_box"] = { half_dimensions: [1, 1, 1], direction: o.dir ?? "outwards" };
  c["minecraft:particle_lifetime_expression"] = { max_lifetime: rnd(...o.life) };
  c["minecraft:particle_initial_speed"] = typeof o.speed === "string" ? o.speed : rnd(...o.speed);
  c["minecraft:particle_motion_dynamic"] = { linear_acceleration: o.accel ?? [0, 0, 0], linear_drag_coefficient: o.drag ?? 0 };
  if (o.spin) c["minecraft:particle_initial_spin"] = { rotation: rnd(0, 360), rotation_rate: rnd(-o.spin, o.spin) };
  const uv = { texture_width: 512, texture_height: 512 };
  if (o.flipbook) {
    const [col, row, frames] = o.flipbook;
    uv.uv = [0, 0];
    uv.uv_size = [512, 512];
    uv.flipbook = { base_UV: [col * CELL, row * CELL], size_UV: [CELL * (o.wide ?? 1), CELL], step_UV: [CELL * (o.wide ?? 1), 0], max_frame: frames, stretch_to_lifetime: true };
  } else {
    const [col, row] = o.cell;
    uv.uv = [col * CELL, row * CELL];
    uv.uv_size = [CELL * (o.wide ?? 1), CELL];
  }
  c["minecraft:particle_appearance_billboard"] = { size: o.size, facing_camera_mode: o.facing ?? "lookat_xyz", uv };
  c["minecraft:particle_appearance_tinting"] = gradient(o.color);
  return { format_version: "1.10.0", particle_effect: { description: { identifier: `orbisarcana:${id}`,
    basic_render_parameters: { material: o.blend ? "particles_blend" : "particles_add", texture: "textures/vfx/sheet" } }, components: c } };
}

const EFFECTS = {
  // soft glowing motes drifting up and fading
  mote: { cell: [1, 0], life: [0.8, 1.4], speed: [0.05, 0.25], accel: [0, 0.35, 0], drag: 1.5, spin: 0,
    size: [overLife(0.22, 0.06, 0.6), overLife(0.22, 0.06, 0.6)], color: [[0, 0], [0.15, 0.95], [0.7, 0.6], [1, 0]] },
  // stars that flare and fade in place
  twinkle: { cell: [3, 0], life: [0.5, 0.9], speed: [0, 0.1], drag: 3, spin: 90,
    size: [`0.34 * math.sin(${LIFE} * 180)`, `0.34 * math.sin(${LIFE} * 180)`], color: [[0, 1], [0.6, 0.9], [1, 0]] },
  // fast streaks thrown out, slowing down, along their flight
  spark: { cell: [2, 0], life: [0.2, 0.45], speed: [3, 7], drag: 6, accel: [0, -2, 0], facing: "lookat_direction",
    size: [overLife(0.05, 0.02), overLife(0.4, 0.1, 0.5)], color: [[0, 1], [0.5, 0.9], [1, 0]] },
  // lightning arc segments flickering out
  arc: { flipbook: [0, 4, 4], wide: 2, life: [0.12, 0.25], speed: [0, 0.3], spin: 0,
    size: [overLife(0.8, 0.6, 0.6), overLife(0.4, 0.3, 0.6)], color: [[0, 1], [0.5, 1], [1, 0]] },
  // smoke puffs rising and growing (blended: grey on light, dark on dark)
  smoke: { flipbook: [0, 2, 8], blend: true, life: [0.9, 1.6], speed: [0.1, 0.4], accel: [0, 0.6, 0], drag: 1.2, spin: 40,
    size: [overLife(0.35, 0.9, 0.5), overLife(0.35, 0.9, 0.5)], color: [[0, 0], [0.12, 0.7], [0.6, 0.45], [1, 0]] },
  // tongues of flame rising, from white-gold to deep orange
  flame: { flipbook: [0, 1, 8], life: [0.45, 0.8], speed: [0.1, 0.4], accel: [0, 1.6, 0], drag: 2,
    size: [overLife(0.42, 0.18, 0.5), overLife(0.5, 0.24, 0.5)],
    color: [[0, 1, 0.92, 0.7, 0.9], [0.35, 1, 0.62, 0.2, 0.85], [0.75, 0.9, 0.28, 0.08, 0.5], [1, 0.4, 0.08, 0.02, 0]] },
  // ice shards turning and sinking, with glints
  frost: { cell: [1, 3], life: [0.6, 1.1], speed: [0.4, 1.4], accel: [0, -2.2, 0], drag: 2.5, spin: 240,
    size: [overLife(0.2, 0.08, 0.6), overLife(0.2, 0.08, 0.6)], color: [[0, 1], [0.7, 0.85], [1, 0]] },
  // water drops thrown up, falling under gravity
  splash: { cell: [5, 3], life: [0.4, 0.8], speed: [1.5, 3.5], dir: [0, 1, 0], accel: [0, -14, 0], drag: 0.6,
    size: [overLife(0.16, 0.1, 0.5), overLife(0.2, 0.12, 0.5)], color: [[0, 0.9], [0.7, 0.8], [1, 0]] },
  // leaves fluttering down, turning
  leaf: { cell: [4, 3], blend: true, life: [1, 1.8], speed: [0.3, 1], accel: [0, -1.2, 0], drag: 3, spin: 200,
    size: [overLife(0.2, 0.14, 0.5), overLife(0.2, 0.14, 0.5)], color: [[0, 1], [0.8, 1], [1, 0]] },
  // stone chips thrown out and dropping
  debris: { cell: [2, 3], blend: true, life: [0.5, 1], speed: [2, 4.5], accel: [0, -18, 0], drag: 0.8, spin: 400,
    size: [overLife(0.18, 0.12, 0.6), overLife(0.18, 0.12, 0.6)], color: [[0, 1], [0.85, 1], [1, 0]] },
  // dark wisps of shadow, curling up
  shadow: { flipbook: [0, 2, 8], blend: true, life: [0.7, 1.3], speed: [0.2, 0.6], accel: [0, 0.9, 0], drag: 1.6, spin: 90,
    size: [overLife(0.3, 0.7, 0.5), overLife(0.3, 0.7, 0.5)], color: [[0, 0], [0.15, 0.9], [0.6, 0.6], [1, 0]] },
  // a flat ring growing along the ground
  shockwave: { cell: [4, 0], count: 2, life: [0.35, 0.45], speed: [0, 0], facing: "emitter_transform_xz",
    size: [overLife(0.4, 3.2), overLife(0.4, 3.2)], color: [[0, 1], [0.4, 0.8], [1, 0]] },
  // a crescent of wind streaks sweeping around the point
  slash: { cell: [2, 0], disc: true, ringOnly: true, dir: "outwards", life: [0.18, 0.3], speed: [1, 2], drag: 4, facing: "lookat_direction",
    size: [overLife(0.06, 0.02), overLife(0.6, 0.2, 0.4)], color: [[0, 1], [0.5, 0.8], [1, 0]] },
  // rune circles rising and turning
  rune: { cell: [6, 3], count: 4, life: [0.9, 1.4], speed: [0.1, 0.3], dir: [0, 1, 0], accel: [0, 0.4, 0], drag: 1, spin: 120,
    size: [overLife(0.3, 0.5), overLife(0.3, 0.5)], color: [[0, 0], [0.2, 1], [0.7, 0.7], [1, 0]] },
};

// ---- signature effects of single spells (roadmap V8): played by their behaviour, in their own colours ----
const FIRE = [[0, 1, 0.97, 0.82, 1], [0.25, 1, 0.75, 0.3, 0.95], [0.6, 0.95, 0.38, 0.08, 0.7], [1, 0.45, 0.08, 0.02, 0]];
/** The same, gentler: many of these overlap in a trail without burning out to white. */
const FIRE_SOFT = [[0, 1, 0.9, 0.6, 0.55], [0.25, 1, 0.62, 0.2, 0.5], [0.6, 0.9, 0.3, 0.06, 0.35], [1, 0.4, 0.07, 0.02, 0]];
const SOOT = [[0, 0.32, 0.27, 0.24, 0], [0.15, 0.26, 0.22, 0.2, 0.75], [0.7, 0.16, 0.14, 0.13, 0.45], [1, 0.1, 0.09, 0.09, 0]];
Object.assign(EFFECTS, {
  // Fireball in flight: a white-hot core that rides with it, a mane of flame streaming back, dripping embers, soot
  fireball_core: { cell: [1, 0], local: true, steady: [40, 10], sphere: 0.02, life: [0.1, 0.18], speed: [0, 0.05],
    size: [overLife(0.55, 0.42, 0.15), overLife(0.55, 0.42, 0.15)], color: [[0, 1, 0.95, 0.75, 0.9], [0.5, 1, 0.7, 0.3, 0.7], [1, 1, 0.5, 0.12, 0]] },
  fireball_flames: { flipbook: [0, 1, 8], steady: [90, 90], sphere: 0.22, life: [0.3, 0.55], speed: [0.2, 0.6], accel: [0, 0.8, 0], drag: 2.5, spin: 60,
    size: [overLife(0.5, 0.14, 0.4), overLife(0.58, 0.18, 0.4)], color: FIRE_SOFT },
  fireball_embers: { cell: [7, 0], steady: [30, 40], sphere: 0.3, life: [0.5, 1], speed: [0.6, 1.8], accel: [0, -3, 0], drag: 1.2,
    size: [overLife(0.1, 0.03, 0.5), overLife(0.1, 0.03, 0.5)], color: [[0, 1, 0.9, 0.55, 1], [0.6, 1, 0.5, 0.12, 0.9], [1, 0.6, 0.15, 0.03, 0]] },
  fireball_smoke: { flipbook: [0, 2, 8], blend: true, steady: [12, 20], sphere: 0.25, life: [0.9, 1.4], speed: [0.1, 0.3], accel: [0, 0.6, 0], drag: 1.5, spin: 40,
    size: [overLife(0.3, 0.9, 0.4), overLife(0.3, 0.9, 0.4)], color: SOOT },
  // Fireball impact: a flash, a ball of flame bursting out, a ring along the ground, sparks thrown far, a column of
  // smoke and cinders settling
  fireball_flash: { cell: [1, 0], count: 1, sphere: 0, life: [0.12, 0.14], speed: [0, 0],
    size: [overLife(1, 3.4), overLife(1, 3.4)], color: [[0, 1, 0.97, 0.85, 0.85], [0.4, 1, 0.7, 0.3, 0.5], [1, 1, 0.45, 0.1, 0]] },
  // the ball of fire: soft glows, not overlapping into white, cooling from gold to ember red
  fireball_blast: { cell: [1, 0], count: 26, sphere: 0.35, life: [0.45, 0.8], speed: [2.5, 6], accel: [0, 2, 0], drag: 4.5,
    size: [overLife(1.1, 0.5, 0.5), overLife(1.1, 0.5, 0.5)],
    color: [[0, 1, 0.82, 0.45, 0.5], [0.3, 1, 0.48, 0.1, 0.45], [0.7, 0.75, 0.18, 0.04, 0.28], [1, 0.3, 0.05, 0.02, 0]] },
  // tongues of flame licking out of it
  fireball_tongues: { flipbook: [0, 1, 8], count: 16, sphere: 0.4, life: [0.35, 0.6], speed: [2, 4.5], accel: [0, 3, 0], drag: 4, spin: 90,
    size: [overLife(0.5, 0.18, 0.4), overLife(0.6, 0.22, 0.4)], color: FIRE },
  fireball_ring: { cell: [4, 0], count: 2, sphere: 0, life: [0.42, 0.5], speed: [0, 0], facing: "emitter_transform_xz",
    size: [overLife(0.6, 6), overLife(0.6, 6)], color: [[0, 1, 0.85, 0.5, 1], [0.5, 1, 0.45, 0.1, 0.6], [1, 0.6, 0.15, 0.02, 0]] },
  fireball_sparks: { cell: [2, 0], count: 36, sphere: 0.3, life: [0.45, 0.95], speed: [6, 13], accel: [0, -9, 0], drag: 1.8, facing: "lookat_direction",
    size: [overLife(0.07, 0.03), overLife(0.6, 0.15, 0.5)], color: [[0, 1, 0.95, 0.7, 1], [0.6, 1, 0.55, 0.15, 0.9], [1, 0.8, 0.2, 0.03, 0]] },
  fireball_column: { flipbook: [0, 2, 8], blend: true, count: 16, sphere: 0.6, life: [1.6, 2.4], speed: [0.4, 1.4], dir: [0, 1, 0], accel: [0, 1.4, 0], drag: 1.3, spin: 30,
    size: [overLife(0.8, 2.3, 0.4), overLife(0.8, 2.3, 0.4)], color: SOOT },
  fireball_cinders: { cell: [7, 0], count: 26, sphere: 1, life: [1.4, 2.4], speed: [0.1, 0.5], accel: [0, 0.5, 0], drag: 1.5,
    size: [`0.07 * (0.6 + 0.4 * math.sin(variable.particle_age * 900 * variable.particle_random_2))`, `0.07 * (0.6 + 0.4 * math.sin(variable.particle_age * 900 * variable.particle_random_2))`],
    color: [[0, 1, 0.75, 0.3, 0], [0.1, 1, 0.7, 0.25, 1], [0.8, 0.9, 0.3, 0.05, 0.8], [1, 0.5, 0.1, 0.02, 0]] },
});

const ICE = [[0, 0.95, 1, 1, 1], [0.5, 0.7, 0.92, 1, 0.8], [1, 0.45, 0.75, 1, 0]];
const STONE = [[0, 0.46, 0.42, 0.38, 1], [0.85, 0.4, 0.37, 0.33, 1], [1, 0.34, 0.31, 0.28, 0]];
const DUST = [[0, 0.55, 0.47, 0.38, 0], [0.15, 0.5, 0.43, 0.35, 0.7], [1, 0.42, 0.36, 0.3, 0]];
Object.assign(EFFECTS, {
  // Ice Shard in flight (played every tick at the shard, pointing along its flight): a white-cyan streak and frost mist
  ice_streak: { cell: [2, 0], count: 1, sphere: 0, dir: [0, 0, 1], life: [0.1, 0.12], speed: [0.001, 0.001], facing: "lookat_direction",
    size: [overLife(0.16, 0.1), overLife(1.3, 1)], color: ICE },
  ice_mist: { cell: [0, 0], count: 2, sphere: 0.1, life: [0.35, 0.6], speed: [0.05, 0.2], drag: 2,
    size: [overLife(0.25, 0.6, 0.4), overLife(0.25, 0.6, 0.4)], color: [[0, 0.7, 0.9, 1, 0.3], [1, 0.6, 0.85, 1, 0]] },
  // ...and when it hits: a cold flash, shards scattering under gravity, a puff of frost
  ice_shatter: { cell: [0, 3], count: 18, sphere: 0.2, life: [0.4, 0.8], speed: [2.5, 6], accel: [0, -12, 0], drag: 1.2, spin: 500,
    size: [overLife(0.22, 0.12, 0.5), overLife(0.22, 0.12, 0.5)], color: ICE },
  ice_flash: { cell: [1, 0], count: 1, sphere: 0, life: [0.12, 0.14], speed: [0, 0],
    size: [overLife(0.4, 1.8), overLife(0.4, 1.8)], color: [[0, 0.9, 1, 1, 0.9], [1, 0.5, 0.8, 1, 0]] },
  ice_puff: { flipbook: [0, 2, 8], count: 6, sphere: 0.3, life: [0.6, 1], speed: [0.5, 1.5], drag: 3, spin: 40,
    size: [overLife(0.4, 1, 0.4), overLife(0.4, 1, 0.4)], color: [[0, 0.8, 0.95, 1, 0], [0.15, 0.8, 0.95, 1, 0.45], [1, 0.7, 0.9, 1, 0]] },
  // Discharge: a lightning bolt drawn segment by segment (each pointing along its segment, scaled to its length): a
  // thin white core and a wide blue glow that flicker; at the target a blue-white flash and sparks
  bolt: { cell: [2, 0], count: 1, sphere: 0, dir: [0, 0, 1], life: [0.2, 0.26], speed: [0.01, 0.01], facing: "lookat_direction",
    size: ["0.13", "1.2"], color: [[0, 1, 1, 1, 1], [0.25, 0.85, 0.95, 1, 0.5], [0.45, 1, 1, 1, 1], [1, 0.6, 0.8, 1, 0]] },
  bolt_glow: { cell: [2, 0], count: 1, sphere: 0, dir: [0, 0, 1], life: [0.24, 0.3], speed: [0.01, 0.01], facing: "lookat_direction",
    size: ["0.55", "1.3"], color: [[0, 0.45, 0.7, 1, 0.8], [0.3, 0.4, 0.65, 1, 0.45], [0.5, 0.45, 0.7, 1, 0.75], [1, 0.3, 0.5, 1, 0]] },
  discharge_hit: { cell: [1, 0], count: 2, sphere: 0, life: [0.14, 0.18], speed: [0, 0],
    size: [overLife(0.6, 2.2), overLife(0.6, 2.2)], color: [[0, 0.85, 0.95, 1, 0.9], [1, 0.4, 0.6, 1, 0]] },
  discharge_sparks: { cell: [2, 0], count: 16, sphere: 0.2, life: [0.2, 0.45], speed: [4, 9], accel: [0, -6, 0], drag: 4, facing: "lookat_direction",
    size: [overLife(0.05, 0.02), overLife(0.45, 0.12, 0.5)], color: [[0, 1, 1, 1, 1], [0.5, 0.7, 0.85, 1, 0.9], [1, 0.4, 0.6, 1, 0]] },
  // Stone Spear: stone spikes thrusting up from the ground (upright, turning to the camera), dust thrown along it;
  // the eruption at the end of the line is bigger
  stone_spikes: { cell: [0, 5], blend: true, count: 3, disc: true, life: [0.55, 0.75], speed: [0, 0], facing: "lookat_y", spin: 0,
    size: [`0.75 * math.min(1, ${LIFE} * 5) * (1 - math.max(0, ${LIFE} - 0.75) * 4)`, `1.7 * math.min(1, ${LIFE} * 5) * (1 - math.max(0, ${LIFE} - 0.75) * 4)`],
    color: STONE },
  earth_dust: { flipbook: [0, 2, 8], blend: true, count: 6, disc: true, ringOnly: true, life: [0.7, 1.1], speed: [1, 2.2], accel: [0, 0.3, 0], drag: 3, spin: 30,
    size: [overLife(0.4, 1.1, 0.4), overLife(0.4, 1.1, 0.4)], color: DUST },
});

for (const [id, o] of Object.entries(EFFECTS)) fs.writeFileSync(path.join(OUT, id + ".json"), JSON.stringify(effect(id, o), null, "\t") + "\n");
console.log(`${Object.keys(EFFECTS).length} effects written`);
