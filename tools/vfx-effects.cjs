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
    // a stream: looping (attached effects run while their entity lives) or, with "once", for that many seconds
    c["minecraft:emitter_rate_steady"] = { spawn_rate: o.steady[0], max_particles: o.steady[1] };
    if (o.once) c["minecraft:emitter_lifetime_once"] = { active_time: o.once };
    else c["minecraft:emitter_lifetime_looping"] = { active_time: 1 };
  } else {
    c["minecraft:emitter_rate_instant"] = { num_particles: o.count ?? 10 };
    c["minecraft:emitter_lifetime_once"] = { active_time: 0.05 };
  }
  if (o.local) c["minecraft:emitter_local_space"] = { position: true, rotation: false };
  if (o.sphere !== undefined) c["minecraft:emitter_shape_sphere"] = { radius: o.sphere, surface_only: !!o.surface, direction: o.dir ?? "outwards" };
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

Object.assign(EFFECTS, {
  tmpl_trail: { cell: [2, 0], count: 2, sphere: 0.05, dir: [0, 0, -1], life: [0.15, 0.25], speed: [0.5, 1.5], drag: 3, facing: "lookat_direction",
    size: [overLife(0.14, 0.05), overLife(1.1, 0.4)], color: [[0, 1], [1, 0]] },
  tmpl_burst: { cell: [1, 0], count: 14, sphere: 0.3, life: [0.3, 0.6], speed: [2, 5], drag: 4, size: [overLife(0.35, 0.08, 0.5), overLife(0.35, 0.08, 0.5)],
    color: [[0, 1], [0.6, 0.7], [1, 0]] },
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

const WATER = [[0, 0.55, 0.82, 1, 0.85], [0.5, 0.3, 0.6, 1, 0.7], [1, 0.18, 0.42, 0.95, 0]];
const LEAVES = [[0, 0.65, 1, 0.45, 1], [0.7, 0.4, 0.85, 0.3, 1], [1, 0.3, 0.6, 0.2, 0]];
Object.assign(EFFECTS, {
  // Tidal Stream (every other tick of the channel, pointing where the caster looks): fast streaks of water fanning out
  // in a cone, and drops and spray that fall away
  water_jet: { cell: [2, 0], count: 9, sphere: 0.1, dir: ["math.random(-0.22, 0.22)", "math.random(-0.18, 0.18)", "1"], life: [0.35, 0.5],
    speed: [11, 15], drag: 1.5, accel: [0, -4, 0], facing: "lookat_direction", size: [overLife(0.1, 0.05), overLife(0.9, 0.5, 0.4)], color: WATER },
  water_spray: { cell: [5, 3], count: 7, sphere: 0.15, dir: ["math.random(-0.3, 0.3)", "math.random(-0.2, 0.3)", "1"], life: [0.4, 0.7],
    speed: [6, 11], drag: 1.2, accel: [0, -12, 0], size: [overLife(0.14, 0.08, 0.5), overLife(0.18, 0.1, 0.5)], color: WATER },
  // ...its last wave: a wall of water bursting up and a ring
  water_wave: { cell: [5, 3], count: 40, disc: true, dir: ["math.random(-0.5, 0.5)", "1", "math.random(-0.5, 0.5)"], life: [0.6, 1],
    speed: [3, 6], accel: [0, -14, 0], drag: 0.5, size: [overLife(0.22, 0.12, 0.5), overLife(0.28, 0.14, 0.5)], color: WATER },
  // Wind Blade (every tick at the blade, flat, pointing along its flight): a pale crescent and wisps peeling off
  wind_crescent: { cell: [1, 5], count: 1, sphere: 0, dir: [0, 0, 1], life: [0.1, 0.12], speed: [0.01, 0.01], facing: "emitter_transform_xz",
    size: ["1.6", "1.6"], color: [[0, 0.92, 1, 0.97, 0.85], [1, 0.8, 1, 0.92, 0]] },
  wind_wisps: { cell: [2, 0], count: 4, sphere: 0.5, dir: [0, 0, -1], life: [0.2, 0.35], speed: [2, 4], drag: 3, facing: "lookat_direction",
    size: [overLife(0.05, 0.02), overLife(0.7, 0.2, 0.4)], color: [[0, 0.9, 1, 0.95, 0.6], [1, 0.8, 1, 0.9, 0]] },
  // Thorn Seed: a glowing seed shedding leaves in flight; where it lands, a green flash, leaves bursting up and thorns
  // spearing out of the ground
  seed_glow: { cell: [1, 0], count: 2, sphere: 0, life: [0.1, 0.12], speed: [0, 0], size: ["0.45", "0.45"],
    color: [[0, 0.75, 1, 0.5, 0.9], [1, 0.5, 0.9, 0.3, 0]] },
  bloom_burst: { cell: [4, 3], blend: true, count: 30, sphere: 0.4, dir: ["math.random(-0.6, 0.6)", "1", "math.random(-0.6, 0.6)"], life: [0.9, 1.6],
    speed: [2, 5], accel: [0, -4, 0], drag: 2, spin: 300, size: [overLife(0.24, 0.16, 0.5), overLife(0.24, 0.16, 0.5)], color: LEAVES },
  bloom_flash: { cell: [1, 0], count: 1, sphere: 0, life: [0.2, 0.24], speed: [0, 0], size: [overLife(0.8, 3.2), overLife(0.8, 3.2)],
    color: [[0, 0.75, 1, 0.55, 0.8], [1, 0.4, 0.9, 0.3, 0]] },
  thorn_spikes: { cell: [0, 5], blend: true, count: 5, disc: true, life: [0.8, 1.1], speed: [0, 0], facing: "lookat_y",
    size: [`0.45 * math.min(1, ${LIFE} * 6) * (1 - math.max(0, ${LIFE} - 0.8) * 5)`, `1.3 * math.min(1, ${LIFE} * 6) * (1 - math.max(0, ${LIFE} - 0.8) * 5)`],
    color: [[0, 0.42, 0.55, 0.25, 1], [0.85, 0.36, 0.45, 0.2, 1], [1, 0.3, 0.38, 0.18, 0]] },
});

/** A beam drawn along its emitter's forward, scaled to its length; its width stays put (divided by the scale). */
const beam = (width, life, color) => ({ cell: [2, 5], count: 1, sphere: 0, dir: [0, 0, 1], life, speed: [0.01, 0.01], facing: "lookat_direction",
  size: [`${width} / variable.emitter_scale`, "1.02"], color });
const VIOLET = [[0, 0.75, 0.45, 1, 1], [0.5, 0.55, 0.25, 0.9, 0.8], [1, 0.35, 0.12, 0.6, 0]];
const SHADE = [[0, 0.2, 0.08, 0.3, 0], [0.15, 0.16, 0.06, 0.26, 0.85], [0.7, 0.1, 0.04, 0.18, 0.5], [1, 0.05, 0.02, 0.1, 0]];
Object.assign(EFFECTS, {
  // Dawn Ray (every other tick of the channel, from the hand to where it stops): a white-gold core in a wide golden
  // glow, a flare where it hits; the finale is a burst of dawn
  ray_core: beam(0.16, [0.12, 0.14], [[0, 1, 0.98, 0.88, 1], [1, 1, 0.92, 0.7, 0.6]]),
  ray_glow: beam(0.9, [0.12, 0.14], [[0, 1, 0.82, 0.4, 0.45], [0.5, 1, 0.78, 0.35, 0.38], [1, 1, 0.7, 0.3, 0.2]]),
  ray_hit: { cell: [1, 0], count: 1, sphere: 0, life: [0.12, 0.14], speed: [0, 0], size: ["0.9", "0.9"],
    color: [[0, 1, 0.95, 0.75, 0.9], [1, 1, 0.8, 0.4, 0.3]] },
  dawn_burst: { cell: [1, 0], count: 1, sphere: 0, life: [0.35, 0.4], speed: [0, 0], size: [overLife(1, 5.5), overLife(1, 5.5)],
    color: [[0, 1, 0.97, 0.85, 1], [0.4, 1, 0.82, 0.45, 0.7], [1, 1, 0.65, 0.2, 0]] },
  dawn_ring: { cell: [4, 0], count: 1, sphere: 0, life: [0.5, 0.55], speed: [0, 0], size: [overLife(0.8, 7), overLife(0.8, 7)],
    color: [[0, 1, 0.92, 0.6, 1], [1, 1, 0.7, 0.25, 0]] },
  // Shadow Dart (every tick at the dart, along its flight): a violet streak trailing dark smoke; on the target the
  // shadows rush in and a violet ring flares
  dart_core: { cell: [2, 0], count: 1, sphere: 0, dir: [0, 0, 1], life: [0.1, 0.12], speed: [0.01, 0.01], facing: "lookat_direction",
    size: ["0.18", "1.3"], color: VIOLET },
  dart_shade: { flipbook: [0, 2, 8], blend: true, count: 3, sphere: 0.1, life: [0.5, 0.8], speed: [0.1, 0.3], drag: 2, spin: 60,
    size: [overLife(0.3, 0.7, 0.4), overLife(0.3, 0.7, 0.4)], color: SHADE },
  dusk_implode: { flipbook: [0, 2, 8], blend: true, count: 14, sphere: 1, dir: "inwards", life: [0.35, 0.5], speed: [2, 3], drag: 2.5, spin: 90,
    size: [overLife(0.7, 0.2, 0.4), overLife(0.7, 0.2, 0.4)], color: SHADE },
  dusk_ring: { cell: [4, 0], count: 1, sphere: 0, life: [0.3, 0.35], speed: [0, 0], size: [overLife(0.4, 2.4), overLife(0.4, 2.4)], color: VIOLET },
  // Star Spike (once, along the whole line): a beam of starlight, white-lavender in a blue-violet haze, slower to fade
  star_core: beam(0.14, [0.35, 0.4], [[0, 1, 0.97, 1, 1], [0.5, 0.85, 0.8, 1, 0.8], [1, 0.6, 0.55, 1, 0]]),
  star_glow: beam(0.8, [0.45, 0.5], [[0, 0.55, 0.45, 1, 0.5], [0.5, 0.45, 0.35, 0.95, 0.35], [1, 0.3, 0.2, 0.8, 0]]),
});

const ASH = [[0, 0.3, 0.28, 0.27, 0], [0.15, 0.25, 0.23, 0.22, 0.8], [1, 0.18, 0.17, 0.16, 0]];
const FROSTY = [[0, 0.85, 0.97, 1, 0], [0.2, 0.8, 0.95, 1, 0.9], [1, 0.6, 0.85, 1, 0]];
const ELECTRIC = [[0, 1, 1, 1, 1], [0.4, 0.65, 0.85, 1, 0.9], [1, 0.35, 0.55, 1, 0]];
Object.assign(EFFECTS, {
  // Ash Spiral (every other tick, stretched over the vortex): a ring of flame licking up at its edge, embers and ash
  // whirling up inside; the pillar of milestone 15 is a column of fire
  ash_ring: { flipbook: [0, 1, 8], count: 10, disc: true, ringOnly: true, dir: [0, 1, 0], life: [0.4, 0.7], speed: [0.5, 1.5], accel: [0, 1.5, 0], drag: 2, spin: 60,
    size: [overLife(0.5, 0.18, 0.4), overLife(0.65, 0.22, 0.4)], color: FIRE_SOFT },
  ash_flakes: { cell: [6, 0], blend: true, count: 6, disc: true, dir: [0, 1, 0], life: [1, 1.6], speed: [0.8, 2], accel: [0, 0.6, 0], drag: 1.2, spin: 360,
    size: [overLife(0.12, 0.08, 0.5), overLife(0.12, 0.08, 0.5)], color: ASH },
  fire_pillar: { flipbook: [0, 1, 8], count: 40, sphere: 0.35, dir: ["math.random(-0.15, 0.15)", "1", "math.random(-0.15, 0.15)"], life: [0.5, 0.9],
    speed: [4, 9], drag: 2.5, spin: 90, size: [overLife(0.8, 0.3, 0.4), overLife(1, 0.35, 0.4)], color: FIRE },
  // Flame Dash and Wind Step: a stream for a third of a second that follows the caster (attached)
  dash_flames: { flipbook: [0, 1, 8], steady: [70, 40], once: 0.35, sphere: 0.4, life: [0.3, 0.55], speed: [0.2, 0.6], accel: [0, 1.5, 0], drag: 2, spin: 60,
    size: [overLife(0.5, 0.15, 0.4), overLife(0.6, 0.2, 0.4)], color: FIRE_SOFT },
  dash_wind: { cell: [2, 0], steady: [50, 30], once: 0.35, sphere: 0.5, dir: [0, 0, -1], life: [0.2, 0.35], speed: [3, 6], drag: 3, facing: "lookat_direction",
    size: [overLife(0.05, 0.02), overLife(0.8, 0.25, 0.4)], color: [[0, 0.9, 1, 0.95, 0.7], [1, 0.8, 1, 0.9, 0]] },
  // Frost Pattern (every few ticks, stretched over the pattern): frost crystals lying flat and glinting, a cold mist;
  // when it freezes, a white ring runs out over the ground
  frost_field: { cell: [7, 3], count: 8, disc: true, life: [0.6, 1], speed: [0, 0], facing: "emitter_transform_xz", spin: 0,
    size: [`0.35 * math.sin(${LIFE} * 180)`, `0.35 * math.sin(${LIFE} * 180)`], color: FROSTY },
  frost_mist: { cell: [0, 0], count: 4, disc: true, life: [1, 1.6], speed: [0.05, 0.2], accel: [0, 0.1, 0], drag: 1,
    size: [overLife(0.6, 1.2, 0.4), overLife(0.6, 1.2, 0.4)], color: [[0, 0.75, 0.92, 1, 0], [0.3, 0.75, 0.92, 1, 0.22], [1, 0.7, 0.9, 1, 0]] },
  frost_ring: { cell: [4, 0], count: 1, sphere: 0, life: [0.5, 0.55], speed: [0, 0], facing: "emitter_transform_xz",
    size: [overLife(0.5, 6), overLife(0.5, 6)], color: [[0, 0.95, 1, 1, 1], [1, 0.6, 0.85, 1, 0]] },
  // Current Shell (every other tick, around the caster): short arcs crackling over the body; a blue ring when it bursts
  shell_arcs: { cell: [2, 0], count: 3, sphere: 0.6, life: [0.08, 0.15], speed: [0.01, 0.01], facing: "lookat_direction",
    size: [overLife(0.07, 0.04), overLife(0.55, 0.3, 0.5)], color: ELECTRIC },
  shock_ring: { cell: [4, 0], count: 1, sphere: 0, life: [0.3, 0.35], speed: [0, 0], size: [overLife(0.6, 4.5), overLife(0.6, 4.5)], color: ELECTRIC },
  // Storm Anchor: a rune glowing flat on the ground while it waits (re-sent every few ticks)
  anchor_rune: { cell: [6, 3], count: 1, sphere: 0, life: [0.45, 0.5], speed: [0, 0], facing: "emitter_transform_xz", spin: 0,
    size: [`0.9 + 0.1 * math.sin(${LIFE} * 180)`, `0.9 + 0.1 * math.sin(${LIFE} * 180)`], color: [[0, 0.5, 0.75, 1, 0], [0.3, 0.6, 0.85, 1, 0.8], [1, 0.4, 0.65, 1, 0]] },
});

const GOLDEN = [[0, 1, 0.9, 0.55, 0], [0.25, 1, 0.85, 0.45, 0.9], [1, 1, 0.7, 0.3, 0]];
const HEAL = [[0, 0.6, 1, 0.6, 0], [0.2, 0.55, 1, 0.65, 0.85], [1, 0.4, 0.9, 0.6, 0]];
const PETALS = [[0, 1, 0.75, 0.88, 1], [0.8, 0.95, 0.65, 0.85, 1], [1, 0.9, 0.6, 0.8, 0]];
Object.assign(EFFECTS, {
  // Granite Oath: stone plates rising around the caster (upright spikes on a ring), dust
  stone_guard: { cell: [0, 5], blend: true, count: 7, disc: true, ringOnly: true, life: [0.7, 0.9], speed: [0, 0], facing: "lookat_y",
    size: [`0.6 * math.min(1, ${LIFE} * 5) * (1 - math.max(0, ${LIFE} - 0.75) * 4)`, `1.2 * math.min(1, ${LIFE} * 5) * (1 - math.max(0, ${LIFE} - 0.75) * 4)`], color: STONE },
  // Healing Dew: drops falling onto the target, a soft green glow, motes rising
  heal_drops: { cell: [5, 3], count: 10, disc: true, dir: [0, -1, 0], life: [0.5, 0.7], speed: [1, 2], accel: [0, -6, 0],
    size: [overLife(0.14, 0.1, 0.4), overLife(0.18, 0.12, 0.4)], color: [[0, 0.6, 1, 0.85, 0], [0.2, 0.55, 1, 0.8, 0.9], [1, 0.4, 0.9, 0.8, 0]] },
  heal_glow: { cell: [1, 0], count: 1, sphere: 0, life: [0.45, 0.5], speed: [0, 0], size: [overLife(0.6, 1.8), overLife(0.6, 1.8)], color: HEAL },
  // Gust Dome: wind streaming over the dome's skin; its burst throws a ring
  dome_wind: { cell: [2, 0], count: 10, sphere: 1, surface: true, dir: [0, 1, 0], life: [0.25, 0.4], speed: [2, 4], drag: 2, facing: "lookat_direction",
    size: [overLife(0.05, 0.02), overLife(0.8, 0.25, 0.4)], color: [[0, 0.9, 1, 0.95, 0.55], [1, 0.8, 1, 0.9, 0]] },
  // Life Bloom: petals drifting up from the meadow
  petals: { cell: [4, 3], blend: true, count: 6, disc: true, dir: [0, 1, 0], life: [1.2, 2], speed: [0.3, 0.8], accel: [0, -0.2, 0], drag: 1.5, spin: 200,
    size: [overLife(0.16, 0.12, 0.5), overLife(0.16, 0.12, 0.5)], color: PETALS },
  // Heaven's Judgment: a golden rune marking the ground where the column falls
  judgment_rune: { cell: [6, 3], count: 1, sphere: 0, life: [0.5, 0.55], speed: [0, 0], facing: "emitter_transform_xz",
    size: [`1.6 + 0.1 * math.sin(${LIFE} * 180)`, `1.6 + 0.1 * math.sin(${LIFE} * 180)`], color: GOLDEN },
  // Purifying Aura: a golden ring on the ground around the caster (re-sent while it is on), scaled to its radius
  aura_ring: { cell: [4, 0], count: 1, sphere: 0, life: [0.5, 0.55], speed: [0, 0], facing: "emitter_transform_xz",
    size: ["2", "2"], color: [[0, 1, 0.9, 0.55, 0], [0.3, 1, 0.88, 0.5, 0.55], [1, 1, 0.8, 0.4, 0]] },
  // Guardian Orbit: a star of the orbit (re-sent every tick at its place), and the burst when it catches something
  orbit_star: { cell: [3, 0], count: 1, sphere: 0, life: [0.1, 0.12], speed: [0, 0], spin: 0,
    size: ["0.5", "0.5"], color: [[0, 1, 0.97, 1, 1], [1, 0.75, 0.7, 1, 0.6]] },
  star_burst: { cell: [3, 0], count: 12, sphere: 0.1, life: [0.35, 0.6], speed: [2, 5], drag: 4, spin: 180,
    size: [overLife(0.35, 0.05), overLife(0.35, 0.05)], color: [[0, 1, 0.97, 1, 1], [0.6, 0.75, 0.65, 1, 0.8], [1, 0.5, 0.4, 1, 0]] },
});

// ---- volumetric effects (client/vfx/VfxMesh): textured shapes with scrolling energy textures (tools/vfx-energy.cjs) ----
const MESHES = {
  // Fireball in flight: a sphere of churning flame, a rim of heat around it, a ribbon of fire streaming behind
  fireball_orb: { shape: "sphere", texture: "textures/vfx/energy/flame", loop: true, life: 1, segments: 28, rings: 14, radius: 0.45,
    uv: [2, 1], scroll: [0.3, -1.8], spin: 140, fresnel: -0.8, color: [[0, [1, 0.85, 0.5, 1]]] },
  fireball_shell: { shape: "sphere", texture: "textures/vfx/energy/energy", loop: true, life: 1, segments: 28, rings: 14, radius: 0.62,
    uv: [2, 1], scroll: [-0.5, -1.2], spin: -90, fresnel: 2, color: [[0, [1, 0.45, 0.1, 0.9]]] },
  fireball_trail: { shape: "trail", texture: "textures/vfx/energy/flame", loop: true, life: 1, samples: 9, width: [[0, 0.95], [1, 0.1]],
    uv: [1.5, 1], scroll: [-3, 0], color: [[0, [1, 0.6, 0.18, 0.9]]] },
  // Fireball impact: a sphere of fire swelling and burning out, a shockwave ring racing along the ground, a jolt
  fireball_nova: { shape: "sphere", texture: "textures/vfx/energy/flame", life: 0.5, segments: 36, rings: 18,
    radius: [[0, 0.3], [0.3, 2], [1, 2.6]], uv: [3, 1.5], scroll: [0.4, -2.5], spin: 60, fresnel: -0.5,
    color: [[0, [1, 0.95, 0.75, 1]], [0.4, [1, 0.55, 0.15, 0.85]], [1, [0.6, 0.15, 0.03, 0]]] },
  fireball_wave: { shape: "ring", texture: "textures/vfx/energy/energy", life: 0.5, segments: 40, radius: [[0, 0.4], [1, 5]],
    thickness: [[0, 1.2], [1, 0.3]], uv: [6, 1], scroll: [0.8, 0], color: [[0, [1, 0.8, 0.4, 1]], [0.5, [1, 0.45, 0.1, 0.7]], [1, [0.6, 0.15, 0.03, 0]]],
    shake: { strength: 1.4, radius: 14, time: 0.35 } },
};
const E = n => "textures/vfx/energy/" + n;
/** Colour keys that fade in at the start and out at the end of a life, holding rgba between. */
const hold = (rgba, inT = 0.1, outT = 0.8) => [[0, [...rgba.slice(0, 3), 0]], [inT, rgba], [outT, rgba], [1, [...rgba.slice(0, 3), 0]]];
/** Colour keys that start at rgba and fade out (bursts). */
const burst = (rgba, end) => [[0, rgba], [0.5, [...end.slice(0, 3), rgba[3] * 0.6]], [1, [...end.slice(0, 3), 0]]];
Object.assign(MESHES, {
  // ---- fire ----
  // Ash Spiral (once, life = the vortex): three arms of flame winding up, a ring of fire on the ground
  ash_vortex: { shape: "spiral", texture: E("flame"), life: 1, segments: 40, arms: 3, turns: 1.4, radius: 1, radius_top: 0.25, height: 2.6,
    thickness: 0.7, uv: [3, 1], scroll: [-1.5, 0], spin: 220, color: hold([1, 0.6, 0.18, 0.85]) },
  ash_floor: { shape: "ring", texture: E("flame"), life: 1, segments: 40, radius: 0.55, thickness: 1.1, uv: [6, 1], scroll: [0.6, -1], spin: 90,
    color: hold([1, 0.45, 0.1, 0.7]) },
  fire_column: { shape: "cylinder", texture: E("flame"), life: 0.7, segments: 24, rings: 6, radius: [[0, 0.3], [0.2, 1.1], [1, 1.4]], height: [[0, 1], [0.2, 7], [1, 8]],
    uv: [3, 2], scroll: [0, -3], spin: 120, fresnel: -0.6, color: burst([1, 0.85, 0.45, 1], [0.7, 0.2, 0.05, 0]), shake: { strength: 1.2, radius: 14, time: 0.4 } },
  // ---- ice ----
  // Ice Shard (every tick, pointing along its flight): a crystal cone; when it breaks, a sphere of frost bursting
  ice_cone: { shape: "cone", texture: E("ice"), life: 0.1, axis: "forward", segments: 6, rings: 1, radius: 0.2, radius_top: 0.01, height: 1.3,
    uv: [1, 1], color: [[0, [0.75, 0.95, 1, 0.95]]] },
  ice_nova: { shape: "sphere", texture: E("ice"), life: 0.35, segments: 24, rings: 12, radius: [[0, 0.2], [1, 1.4]], uv: [2, 1], spin: 90, fresnel: 1.2,
    color: burst([0.85, 0.97, 1, 1], [0.5, 0.8, 1, 0]) },
  frost_wave: { shape: "ring", texture: E("ice"), life: 0.55, segments: 40, radius: [[0, 0.5], [1, 6]], thickness: [[0, 1.5], [1, 0.4]], uv: [8, 1],
    color: burst([0.85, 0.97, 1, 1], [0.5, 0.8, 1, 0]), shake: { strength: 0.8, radius: 12, time: 0.3 } },
  // Frost Pattern (once, life = the pattern): frozen ground glowing with cracks
  frost_floor: { shape: "ring", texture: E("ice"), life: 1, segments: 40, radius: 0.5, thickness: 1, uv: [4, 1], spin: 6, color: hold([0.7, 0.92, 1, 0.55]) },
  // ---- lightning ----
  lightning_nova: { shape: "sphere", texture: E("electric"), life: 0.25, segments: 20, rings: 10, radius: [[0, 0.2], [1, 1.5]], uv: [2, 1], scroll: [3, 2],
    fresnel: 0.8, color: burst([0.85, 0.95, 1, 1], [0.4, 0.6, 1, 0]) },
  // Current Shell (re-sent while on): a cage of electricity around the body
  shell_cage: { shape: "sphere", texture: E("electric"), life: 0.55, segments: 20, rings: 10, radius: 1.05, uv: [2, 1], scroll: [2.5, 1.5], spin: 150, fresnel: 1.2,
    color: hold([0.65, 0.85, 1, 0.9], 0.2, 0.75) },
  storm_ring: { shape: "ring", texture: E("electric"), life: 0.45, segments: 40, radius: [[0, 0.4], [1, 4]], thickness: [[0, 1], [1, 0.3]], uv: [8, 1], scroll: [2, 0],
    color: burst([0.85, 0.95, 1, 1], [0.4, 0.6, 1, 0]), shake: { strength: 1.6, radius: 16, time: 0.35 } },
  // ---- earth ----
  earth_wave: { shape: "ring", texture: E("smoke"), life: 0.6, segments: 40, radius: [[0, 0.5], [1, 5]], thickness: [[0, 1.6], [1, 0.5]], uv: [8, 1],
    additive: false, color: burst([0.55, 0.47, 0.38, 0.9], [0.45, 0.4, 0.34, 0]), shake: { strength: 1.5, radius: 14, time: 0.4 } },
  // ---- water ----
  // Tidal Stream (every other tick, along the look): a cone of rushing water from the hand
  water_cone: { shape: "cone", texture: E("water"), life: 0.12, axis: "forward", segments: 20, rings: 4, radius: 0.15, radius_top: 1.6, height: 6,
    uv: [2, 2], scroll: [0, -6], spin: 120, fresnel: 0.6, color: [[0, [0.45, 0.75, 1, 0.75]]] },
  // Water Prison (re-sent while it holds): a sphere of water around the prisoner
  water_sphere: { shape: "sphere", texture: E("water"), life: 0.6, segments: 24, rings: 12, radius: 1, uv: [2, 1], scroll: [0.3, -0.6], spin: 40, fresnel: 1.3,
    color: hold([0.4, 0.72, 1, 0.85], 0.2, 0.75) },
  water_burst: { shape: "sphere", texture: E("water"), life: 0.4, segments: 24, rings: 12, radius: [[0, 0.8], [1, 2.6]], uv: [2, 1], scroll: [0, -2], fresnel: 0.8,
    color: burst([0.55, 0.85, 1, 0.9], [0.3, 0.6, 1, 0]) },
  // ---- air ----
  // Gust Dome (re-sent while it stands): the dome of wind, streaming round
  wind_dome: { shape: "sphere", texture: E("wind"), life: 0.5, segments: 32, rings: 16, radius: 1, uv: [3, 2], scroll: [-1.2, 0], spin: 60, fresnel: 1.5,
    color: hold([0.85, 1, 0.95, 0.55], 0.2, 0.75) },
  wind_wave: { shape: "ring", texture: E("wind"), life: 0.45, segments: 40, radius: [[0, 0.5], [1, 5.5]], thickness: [[0, 1.4], [1, 0.4]], uv: [8, 1], scroll: [2, 0],
    color: burst([0.92, 1, 0.97, 0.9], [0.8, 1, 0.9, 0]), shake: { strength: 1, radius: 12, time: 0.3 } },
  // Flame Dash and Wind Step: a ribbon left behind the caster
  dash_trail: { shape: "trail", texture: E("wind"), life: 0.45, samples: 8, width: [[0, 1.6], [1, 0.2]], uv: [1, 1], scroll: [-2, 0], color: [[0, [0.9, 1, 0.95, 0.75]]] },
  // ---- nature ----
  // Life Bloom (once, life = the zone): a living meadow glowing on the ground
  meadow_floor: { shape: "ring", texture: E("energy"), life: 1, segments: 40, radius: 0.5, thickness: 1, uv: [4, 1], scroll: [0.15, -0.3], spin: 8,
    color: hold([0.5, 1, 0.45, 0.45]) },
  bloom_wave: { shape: "ring", texture: E("energy"), life: 0.6, segments: 40, radius: [[0, 0.4], [1, 4.5]], thickness: [[0, 1.2], [1, 0.3]], uv: [6, 1],
    color: burst([0.6, 1, 0.5, 0.9], [0.3, 0.8, 0.3, 0]) },
  // Root Shackles: roots winding up around the bound one
  root_coil: { shape: "spiral", texture: E("smoke"), life: 1.2, additive: false, segments: 30, arms: 3, turns: 1.2, radius: 0.55, radius_top: 0.35,
    height: [[0, 0.1], [0.2, 1.6], [1, 1.6]], thickness: 0.25, uv: [2, 1], color: hold([0.36, 0.26, 0.15, 1], 0.05, 0.8) },
  // ---- light ----
  // Dawn Ray (every other tick, from the hand along the beam): a cylinder of light with rays running along it
  light_beam: { shape: "cylinder", texture: E("light"), life: 0.12, axis: "forward", stretch: true, segments: 16, rings: 3, radius: 0.32, height: 1,
    uv: [2, 4], scroll: [0, -8], spin: 200, fresnel: -0.7, color: [[0, [1, 0.88, 0.5, 0.8]]] },
  // Heaven's Judgment: a column of light from the sky
  judgment_column: { shape: "cylinder", texture: E("light"), life: 0.8, segments: 28, rings: 6, radius: [[0, 0.2], [0.15, 1.6], [1, 2]], height: 16,
    uv: [3, 3], scroll: [0, 6], spin: 60, fresnel: -0.5, color: burst([1, 0.95, 0.7, 1], [1, 0.75, 0.3, 0]), shake: { strength: 1.8, radius: 18, time: 0.45 } },
  // Purifying Aura (re-sent while on): a band of runes turning around the caster
  aura_band: { shape: "cylinder", texture: E("runes"), life: 0.55, segments: 32, rings: 2, radius: 1, height: 0.6, uv: [4, 1], spin: 40,
    color: hold([1, 0.88, 0.5, 0.8], 0.2, 0.75) },
  // ---- dark ----
  // Shadow Dart (every tick, along its flight): a shard of shadow
  dart_cone: { shape: "cone", texture: E("smoke"), life: 0.1, axis: "forward", segments: 8, rings: 1, radius: 0.16, radius_top: 0.01, height: 1.4,
    uv: [1, 1], scroll: [0, -4], color: [[0, [0.65, 0.35, 1, 0.9]]] },
  // shadows collapsing into a point (dart hits, Shadow Step, curses)
  dusk_sphere: { shape: "sphere", texture: E("smoke"), life: 0.4, additive: false, segments: 20, rings: 10, radius: [[0, 1.5], [1, 0.05]], uv: [2, 1], scroll: [0, 2],
    spin: -200, fresnel: 1, color: [[0, [0.18, 0.06, 0.28, 0]], [0.3, [0.16, 0.05, 0.25, 0.85]], [1, [0.1, 0.02, 0.15, 0.9]]] },
  // Withering Curse (re-sent while it lasts): a turning ring of runes over the head
  curse_halo: { shape: "ring", texture: E("runes"), life: 0.85, segments: 32, radius: 0.45, thickness: 0.3, uv: [3, 1], spin: 90, color: hold([0.7, 0.35, 1, 0.9]) },
  // ---- astral ----
  // Star Spike: a beam of starlight along the line
  star_beam: { shape: "cylinder", texture: E("stars"), life: 0.45, axis: "forward", stretch: true, segments: 16, rings: 3, radius: [[0, 0.1], [0.15, 0.35], [1, 0.05]], height: 1,
    uv: [2, 6], scroll: [0, -4], spin: 90, fresnel: -0.5, color: burst([0.85, 0.8, 1, 1], [0.5, 0.35, 1, 0]) },
  // Constellation Shift and Guardian Orbit: a sphere of stars swelling, a ring of runes
  star_nova: { shape: "sphere", texture: E("stars"), life: 0.5, segments: 24, rings: 12, radius: [[0, 0.3], [1, 2.4]], uv: [2, 1], spin: 60, fresnel: 1,
    color: burst([0.8, 0.75, 1, 1], [0.45, 0.3, 1, 0]) },
  astral_ring: { shape: "ring", texture: E("runes"), life: 0.7, segments: 40, radius: [[0, 0.5], [1, 3.5]], thickness: [[0, 0.6], [1, 0.3]], uv: [6, 1], spin: 60,
    color: burst([0.8, 0.7, 1, 1], [0.5, 0.35, 1, 0]), shake: { strength: 0.8, radius: 12, time: 0.3 } },
});

// ---- templates for spells made by tools/new-spell.cjs: white, tinted with the school's colour by the spell ----
Object.assign(MESHES, {
  tmpl_orb: { shape: "sphere", texture: E("energy"), life: 0.1, segments: 16, rings: 8, radius: 0.3, uv: [2, 1], scroll: [0.5, -2], spin: 200, fresnel: -0.8,
    color: [[0, [1, 1, 1, 0.95]]] },
  tmpl_beam: { shape: "cylinder", texture: E("energy"), life: 0.35, axis: "forward", stretch: true, segments: 14, rings: 3, radius: [[0, 0.1], [0.15, 0.3], [1, 0.05]],
    height: 1, uv: [2, 5], scroll: [0, -6], spin: 120, fresnel: -0.6, color: burst([1, 1, 1, 1], [1, 1, 1, 0]) },
  tmpl_cone: { shape: "cone", texture: E("energy"), life: 0.3, axis: "forward", segments: 18, rings: 3, radius: 0.15, radius_top: 2.4, height: 6, uv: [2, 2],
    scroll: [0, -5], spin: 90, fresnel: 0.5, color: burst([1, 1, 1, 0.8], [1, 1, 1, 0]) },
  tmpl_nova: { shape: "sphere", texture: E("energy"), life: 0.4, segments: 24, rings: 12, radius: [[0, 0.2], [1, 1.8]], uv: [2, 1], scroll: [0, -2], spin: 60,
    fresnel: 0.8, color: burst([1, 1, 1, 1], [1, 1, 1, 0]) },
  tmpl_wave: { shape: "ring", texture: E("energy"), life: 0.45, segments: 40, radius: [[0, 0.4], [1, 3.5]], thickness: [[0, 1], [1, 0.3]], uv: [6, 1],
    scroll: [1, 0], color: burst([1, 1, 1, 1], [1, 1, 1, 0]) },
});

// ---- V10 fire pack: signature effects of the 17 new Fire spells (own colours, played untinted) ----
const MAGMA = [[0, 1, 0.8, 0.35, 1], [0.3, 1, 0.42, 0.06, 0.95], [0.7, 0.7, 0.12, 0.03, 0.8], [1, 0.25, 0.05, 0.02, 0]];
Object.assign(EFFECTS, {
  // Cinder Volley (every tick, behind each cinder) and its pop on hit
  cinder_trail: { cell: [2, 0], count: 3, sphere: 0.05, dir: [0, 0, -1], life: [0.2, 0.35], speed: [0.5, 1.5], drag: 3, facing: "lookat_direction",
    size: [overLife(0.1, 0.03), overLife(0.7, 0.2)], color: [[0, 1, 0.9, 0.5, 1], [0.6, 1, 0.45, 0.08, 0.8], [1, 0.5, 0.1, 0.02, 0]] },
  cinder_pop: { cell: [7, 0], count: 14, sphere: 0.2, life: [0.4, 0.8], speed: [2, 5], accel: [0, -6, 0], drag: 2,
    size: [overLife(0.12, 0.04, 0.5), overLife(0.12, 0.04, 0.5)], color: MAGMA },
  // Flare: a blinding white star bursting before the face
  flare_flash: { cell: [3, 0], count: 2, sphere: 0, life: [0.25, 0.3], speed: [0, 0], spin: 120,
    size: [overLife(0.5, 3.6), overLife(0.5, 3.6)], color: [[0, 1, 1, 0.95, 1], [0.4, 1, 0.9, 0.6, 0.8], [1, 1, 0.6, 0.2, 0]] },
  // Magma Burst: a spout of molten rock and globs raining back down
  magma_spout: { flipbook: [0, 1, 8], count: 22, disc: true, dir: [0, 1, 0], life: [0.5, 0.9], speed: [4, 8], accel: [0, -4, 0], drag: 1.5, spin: 90,
    size: [overLife(0.7, 0.25, 0.4), overLife(0.8, 0.3, 0.4)], color: MAGMA },
  magma_globs: { cell: [7, 0], count: 26, disc: true, dir: [0, 1, 0], life: [0.8, 1.3], speed: [4, 9], accel: [0, -14, 0], drag: 0.5,
    size: [overLife(0.2, 0.08, 0.6), overLife(0.2, 0.08, 0.6)], color: MAGMA },
  // Fire Wall (every pulse, every 1.5 blocks along the wall): tongues of flame leaping up, embers
  wall_flames: { flipbook: [0, 1, 8], count: 7, sphere: 0.6, dir: [0, 1, 0], life: [0.55, 0.9], speed: [1.2, 2.6], accel: [0, 2, 0], drag: 1.8, spin: 60,
    size: [overLife(0.75, 0.25, 0.4), overLife(1, 0.35, 0.4)], color: FIRE },
  wall_embers: { cell: [7, 0], count: 4, sphere: 0.6, dir: [0, 1, 0], life: [0.8, 1.4], speed: [1, 3], accel: [0, 1, 0], drag: 1.2,
    size: [overLife(0.08, 0.03, 0.5), overLife(0.08, 0.03, 0.5)], color: MAGMA },
  // Scorched Earth (every pulse, over the zone): low flames licking up from the glowing ground
  scorch_flames: { flipbook: [0, 1, 8], count: 12, disc: true, dir: [0, 1, 0], life: [0.4, 0.7], speed: [0.3, 1], accel: [0, 1.5, 0], drag: 2, spin: 60,
    size: [overLife(0.45, 0.15, 0.4), overLife(0.55, 0.2, 0.4)], color: FIRE_SOFT },
  // Meteor and Fallen Sun in flight (every tick): a mane of flame and soot streaming back
  meteor_trail: { flipbook: [0, 1, 8], count: 6, sphere: 0.5, dir: [0, 0, -1], life: [0.35, 0.65], speed: [0.5, 1.5], accel: [0, 1, 0], drag: 2, spin: 90,
    size: [overLife(1, 0.35, 0.4), overLife(1.2, 0.4, 0.4)], color: FIRE },
  meteor_smoke: { flipbook: [0, 2, 8], blend: true, count: 2, sphere: 0.6, life: [1, 1.6], speed: [0.1, 0.4], accel: [0, 0.5, 0], drag: 1.5, spin: 40,
    size: [overLife(0.8, 2, 0.4), overLife(0.8, 2, 0.4)], color: SOOT },
});
Object.assign(MESHES, {
  // Ember Spray (once) and Dragon's Breath (every pulse): a cone of churning flame
  fire_cone: { shape: "cone", texture: E("flame"), life: 0.35, axis: "forward", segments: 20, rings: 4, radius: 0.15, radius_top: 2.2, height: 6, uv: [2, 2],
    scroll: [0.3, -4], spin: 120, fresnel: 0.4, color: burst([1, 0.85, 0.45, 0.9], [0.8, 0.2, 0.04, 0]) },
  // Scorching Ray (every pulse): a thin white-gold ray; Flame Lash: a thick, short-lived whip of fire
  fire_ray: { shape: "cylinder", texture: E("flame"), life: 0.3, axis: "forward", stretch: true, segments: 12, rings: 3, radius: [[0, 0.06], [0.2, 0.16], [1, 0.04]],
    height: 1, uv: [2, 6], scroll: [0, -8], spin: 160, fresnel: -0.7, color: burst([1, 0.95, 0.7, 1], [1, 0.45, 0.1, 0]) },
  fire_lash: { shape: "cylinder", texture: E("flame"), life: 0.25, axis: "forward", stretch: true, segments: 14, rings: 3, radius: [[0, 0.3], [0.3, 0.22], [1, 0.02]],
    height: 1, uv: [1, 4], scroll: [0, -10], spin: 300, fresnel: -0.4, color: burst([1, 0.7, 0.25, 1], [0.8, 0.2, 0.04, 0]) },
  // Cinder Volley (every tick): a small glowing cinder
  cinder_orb: { shape: "sphere", texture: E("flame"), life: 0.1, segments: 12, rings: 6, radius: 0.16, uv: [2, 1], scroll: [0.4, -2], spin: 200, fresnel: -0.8,
    color: [[0, [1, 0.75, 0.35, 1]]] },
  // Blazing Ring: a ring of flame racing out along the ground
  flame_ring: { shape: "ring", texture: E("flame"), life: 0.6, segments: 48, radius: [[0, 0.5], [1, 4]], thickness: [[0, 1.6], [1, 0.6]], uv: [8, 1],
    scroll: [1, -1.5], spin: 60, color: burst([1, 0.8, 0.35, 1], [0.8, 0.2, 0.04, 0]), shake: { strength: 0.6, radius: 10, time: 0.25 } },
  // Ember Ward: a rim of embers closing around the caster
  ember_shroud: { shape: "sphere", texture: E("flame"), life: 0.8, segments: 28, rings: 14, radius: [[0, 1.6], [0.3, 1.05], [1, 1.1]], uv: [3, 1.5],
    scroll: [0.3, -1.2], spin: 90, fresnel: 2, color: hold([1, 0.5, 0.12, 0.85], 0.15, 0.6) },
  // Scorched Earth (every pulse): the ground glowing red-hot
  scorch_floor: { shape: "ring", texture: E("flame"), life: 0.55, segments: 40, radius: 1.25, thickness: 2.5, uv: [6, 2], scroll: [0.2, -0.6], spin: 20,
    color: hold([1, 0.38, 0.08, 0.6], 0.2, 0.75) },
  // Immolation (every pulse, at the caster): a mantle of flame
  immolation_mantle: { shape: "cylinder", texture: E("flame"), life: 0.55, segments: 28, rings: 6, radius: [[0, 0.9], [1, 1.05]], height: 2.3, uv: [3, 1.5],
    scroll: [0, -2.5], spin: 140, fresnel: 1.4, color: hold([1, 0.55, 0.15, 0.75], 0.2, 0.75) },
  // Meteor in flight (every tick): a boulder of churning fire; Fallen Sun: a small white-gold sun
  meteor_core: { shape: "sphere", texture: E("flame"), life: 0.1, segments: 24, rings: 12, radius: 0.9, uv: [2, 1], scroll: [0.3, -1.5], spin: 120, fresnel: -0.6,
    color: [[0, [1, 0.55, 0.18, 1]]] },
  sun_core: { shape: "sphere", texture: E("flame"), life: 0.1, segments: 32, rings: 16, radius: 1.8, uv: [3, 1.5], scroll: [0.4, -1.2], spin: 90, fresnel: -0.8,
    color: [[0, [1, 0.92, 0.6, 1]]] },
});

// V10 fire pack: a flare at the hand on release, and the apogee (mastery 15) — a golden crown of flame and a golden star
Object.assign(EFFECTS, {
  hand_flare: { cell: [2, 0], count: 10, sphere: 0.1, dir: [0, 0, 1], life: [0.15, 0.3], speed: [2, 5], drag: 5, facing: "lookat_direction",
    size: [overLife(0.06, 0.02), overLife(0.45, 0.1)], color: [[0, 1, 0.95, 0.7, 1], [0.6, 1, 0.55, 0.15, 0.8], [1, 0.6, 0.15, 0.02, 0]] },
  apogee_flare: { cell: [3, 0], count: 3, sphere: 0, life: [0.35, 0.45], speed: [0, 0], spin: 60,
    size: [overLife(0.8, 4.5), overLife(0.8, 4.5)], color: [[0, 1, 0.95, 0.6, 1], [0.4, 1, 0.8, 0.3, 0.8], [1, 1, 0.55, 0.1, 0]] },
});
Object.assign(MESHES, {
  apogee_crown: { shape: "cylinder", texture: E("flame"), life: 0.7, segments: 32, rings: 4, radius: [[0, 0.6], [1, 2.6]], height: [[0, 0.2], [0.3, 1.2], [1, 0.3]],
    uv: [6, 1], scroll: [0.6, -2], spin: 90, fresnel: 1.2, color: burst([1, 0.85, 0.35, 1], [1, 0.45, 0.05, 0]) },
});

for (const [id, m] of Object.entries(MESHES)) fs.writeFileSync(path.join(OUT, id + ".json"), JSON.stringify({ "orbisarcana:mesh": m }, null, "\t") + "\n");

for (const [id, o] of Object.entries(EFFECTS)) fs.writeFileSync(path.join(OUT, id + ".json"), JSON.stringify(effect(id, o), null, "\t") + "\n");
console.log(`${Object.keys(EFFECTS).length} effects and ${Object.keys(MESHES).length} volumetric effects written`);
