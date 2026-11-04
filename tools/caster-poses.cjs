// Spell-specific caster poses (roadmap V8) for Player Animation Library: written into the PAL file
// assets/orbisarcana/player_animations/caster.json and into the rig art/animations/caster.bbmodel (editor values:
// X and Y negated, as Blockbench exports them). Values here are the PAL file's: X < 0 raises an arm forward and bends
// the torso or head forward; Z > 0 lifts the right arm sideways, Z < 0 the left. Each pose starts and ends at rest.
// client/anim/CasterAnimations plays release_<pose> for the spells that name one, toggle_on when a toggle starts.
// Run from the project root: node tools/caster-poses.cjs
const fs = require("fs");
const crypto = require("crypto");

const PAL = "src/main/resources/assets/orbisarcana/player_animations/caster.json";
const RIG = "art/animations/caster.bbmodel";

/** Keys: [time, [x, y, z]] per bone; every pose returns to rest at its length. */
const POSES = {
  // Flame Dash, Wind Step: a lunge, leaning in, arms swept back, a long stride
  release_dash: { length: 0.45, bones: {
    torso: [[0, [-8, 0, 0]], [0.08, [-26, 0, 0]], [0.3, [-18, 0, 0]]],
    head: [[0.08, [12, 0, 0]], [0.3, [8, 0, 0]]],
    right_arm: [[0, [-20, 0, 10]], [0.08, [40, 0, 18]], [0.3, [30, 0, 12]]],
    left_arm: [[0, [-20, 0, -10]], [0.08, [40, 0, -18]], [0.3, [30, 0, -12]]],
    right_leg: [[0.08, [-34, 0, 0]], [0.3, [-20, 0, 0]]],
    left_leg: [[0.08, [28, 0, 0]], [0.3, [16, 0, 0]]] } },
  // Shadow Step: a crouch with crossed arms, then the arms thrown open as the caster vanishes
  release_step: { length: 0.5, bones: {
    torso: [[0, [-12, 0, 0]], [0.06, [-30, 0, 0]], [0.2, [-6, 0, 0]]],
    head: [[0.06, [-18, 0, 0]], [0.2, [6, 0, 0]]],
    right_arm: [[0, [-40, -20, 0]], [0.06, [-70, -40, 0]], [0.2, [-25, 0, 85]]],
    left_arm: [[0, [-40, 20, 0]], [0.06, [-70, 40, 0]], [0.2, [-25, 0, -85]]],
    right_leg: [[0.06, [-22, 0, 4]], [0.2, [0, 0, 6]]],
    left_leg: [[0.06, [18, 0, -4]], [0.2, [0, 0, -6]]] } },
  // Healing Dew: both hands held out to the one healed, gently
  release_heal: { length: 0.6, bones: {
    torso: [[0.1, [-6, 0, 0]], [0.35, [-4, 0, 0]]],
    head: [[0.1, [-8, 0, 0]], [0.35, [-6, 0, 0]]],
    right_arm: [[0, [-40, -10, 8]], [0.1, [-72, -22, 0]], [0.35, [-66, -18, 0]]],
    left_arm: [[0, [-40, 10, -8]], [0.1, [-72, 22, 0]], [0.35, [-66, 18, 0]]] } },
  // Heaven's Judgment: the right arm raised to the sky, held, then brought down at the ground
  release_judgment: { length: 0.75, bones: {
    torso: [[0.12, [8, 0, 0]], [0.38, [8, 0, 0]], [0.48, [-14, 0, 0]]],
    head: [[0.12, [24, 0, 0]], [0.38, [20, 0, 0]], [0.48, [-16, 0, 0]]],
    right_arm: [[0, [-90, 0, 8]], [0.12, [-176, 0, 6]], [0.38, [-172, 0, 6]], [0.48, [-58, 0, 6]]],
    left_arm: [[0.12, [-20, 0, -40]], [0.38, [-20, 0, -42]], [0.48, [-10, 0, -24]]] } },
  // Granite Oath: arms crossed before the chest, feet braced
  release_guard: { length: 0.65, bones: {
    torso: [[0.1, [-8, 0, 0]], [0.4, [-6, 0, 0]]],
    head: [[0.1, [-6, 0, 0]], [0.4, [-4, 0, 0]]],
    right_arm: [[0, [-50, 0, 10]], [0.1, [-92, -48, 0]], [0.4, [-88, -44, 0]]],
    left_arm: [[0, [-50, 0, -10]], [0.1, [-92, 48, 0]], [0.4, [-88, 44, 0]]],
    right_leg: [[0.1, [0, 0, 10]], [0.4, [0, 0, 10]]],
    left_leg: [[0.1, [0, 0, -10]], [0.4, [0, 0, -10]]] } },
  // Gust Dome: arms crossed, then swept wide and up as the dome bursts out
  release_dome: { length: 0.6, bones: {
    torso: [[0, [-10, 0, 0]], [0.16, [6, 0, 0]]],
    head: [[0.16, [10, 0, 0]]],
    right_arm: [[0, [-80, -40, 0]], [0.16, [-110, 0, 96]], [0.32, [-90, 0, 80]]],
    left_arm: [[0, [-80, 40, 0]], [0.16, [-110, 0, -96]], [0.32, [-90, 0, -80]]] } },
  // Discharge: a quick palm thrust with a twist of the body, the other arm drawn back
  release_thrust: { length: 0.38, bones: {
    torso: [[0, [-4, 12, 0]], [0.06, [-10, -18, 0]], [0.18, [-8, -14, 0]]],
    right_arm: [[0, [-60, 20, 10]], [0.06, [-98, -6, 2]], [0.18, [-94, -4, 2]]],
    left_arm: [[0.06, [32, 0, -14]], [0.18, [24, 0, -12]]],
    right_leg: [[0.06, [-14, 0, 0]]],
    left_leg: [[0.06, [12, 0, 0]]] } },
  // a toggle switched on (Purifying Aura, Current Shell): arms rising from the sides, the head lifting
  toggle_on: { length: 0.6, bones: {
    head: [[0.15, [14, 0, 0]], [0.35, [10, 0, 0]]],
    torso: [[0.15, [5, 0, 0]]],
    right_arm: [[0, [-10, 0, 30]], [0.15, [-20, 0, 120]], [0.35, [-15, 0, 105]]],
    left_arm: [[0, [-10, 0, -30]], [0.15, [-20, 0, -120]], [0.35, [-15, 0, -105]]] } },
};

const fmt = n => Math.round(n * 1000) / 1000;
const withRest = (keys, length) => keys.at(-1)[0] < length ? [...keys, [length, [0, 0, 0]]] : keys;

// PAL file
const pal = JSON.parse(fs.readFileSync(PAL, "utf8"));
for (const [name, pose] of Object.entries(POSES)) {
  const bones = {};
  for (const [bone, keys] of Object.entries(pose.bones)) {
    const rotation = {};
    const full = keys[0][0] > 0 ? [[0, [0, 0, 0]], ...keys] : keys;
    for (const [t, v] of withRest(full, pose.length)) rotation[fmt(t).toFixed(Number.isInteger(fmt(t) * 10) ? 1 : 2)] = { post: { vector: v }, lerp_mode: "catmullrom" };
    bones[bone] = { rotation };
  }
  pal.animations["animation." + name] = { animation_length: pose.length, bones };
}
fs.writeFileSync(PAL, JSON.stringify(pal, null, "\t") + "\n");

// Blockbench rig: the same keys, editor-side
const rig = JSON.parse(fs.readFileSync(RIG, "utf8"));
const groups = Object.fromEntries((rig.groups || []).map(g => [g.name, g.uuid]));
const uuid = () => crypto.randomUUID();
for (const [name, pose] of Object.entries(POSES)) {
  const animators = {};
  for (const [bone, keys] of Object.entries(pose.bones)) {
    const full = keys[0][0] > 0 ? [[0, [0, 0, 0]], ...keys] : keys;
    animators[groups[bone]] = { name: bone, type: "bone", rotation_global: false, quaternion_interpolation: false,
      keyframes: withRest(full, pose.length).map(([t, v]) => ({ channel: "rotation", data_points: [{ x: String(-v[0]), y: String(-v[1]), z: String(v[2]) }],
        uuid: uuid(), time: t, color: -1, interpolation: "catmullrom" })) };
  }
  const anim = { uuid: uuid(), name: "animation." + name, loop: "once", override: false, length: pose.length, snapping: 20, selected: false,
    saved: true, path: "", scope: 0, anim_time_update: "", blend_weight: "", start_delay: "", loop_delay: "", animators };
  const at = rig.animations.findIndex(a => a.name === anim.name);
  if (at >= 0) rig.animations[at] = anim; else rig.animations.push(anim);
}
fs.writeFileSync(RIG, JSON.stringify(rig));
console.log(`${Object.keys(POSES).length} caster poses written`);
