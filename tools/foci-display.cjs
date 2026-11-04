// Display transforms of the 3D foci (roadmap V7): models/item/<tier>_<type>.json for every tier, one set per type.
// The GeckoLib model stands on the item origin, which every context puts at the display pivot (hand, slot centre).
// Found in game: in the third-person hand the model's +Y points forward and +Z up, so rotation X 90 stands an item
// upright and [0, -90, z] lays it forward like a sword, its flat side out (z < 0 lifts the tip). Icons are drawn into a 16x16 texture and clipped: a long focus lies on
// the diagonal with its head in the upper-right corner. Run after changing a value: node tools/foci-display.cjs
const fs = require("fs");
const both = (r, t, s, mirror = true) => ({ r: { rotation: r, translation: t, scale: [s, s, s] },
  l: { rotation: mirror ? [r[0], -r[1], -r[2]] : r, translation: mirror ? [-t[0], t[1], t[2]] : t, scale: [s, s, s] } });
const set = (tp, fp, gui, ground, fixed) => ({
  thirdperson_righthand: tp.r, thirdperson_lefthand: tp.l, firstperson_righthand: fp.r, firstperson_lefthand: fp.l,
  gui, ground, fixed,
});
const D = {
  // held like a sword (vanilla handheld: forward and a little up, the flat side out); grip ~2.5 units above the origin
  wand: set(both([0, -90, -10], [0, -3.8, -0.9], 1), both([-10, -10, 8], [0.5, -3.5, -1], 0.75),
    { rotation: [0, 0, -45], translation: [-5.3, -5.3, 0], scale: [1, 1, 1] },
    { rotation: [0, 0, 0], translation: [0, 2, 0], scale: [0.5, 0.5, 0.5] },
    { rotation: [0, 0, -45], translation: [-5.3, -5.3, 0], scale: [1, 1, 1] }),
  // held like a sword too, the fist around the grip ~13.5 units up
  staff: set(both([0, -90, -10], [0, -12.6, -2.4], 0.85), both([-12, -15, 6], [2, -5, -2], 0.48),
    { rotation: [0, 0, -45], translation: [-4.9, -4.9, 0], scale: [0.62, 0.62, 0.62] },
    { rotation: [0, 0, 0], translation: [0, 0, 0], scale: [0.35, 0.35, 0.35] },
    { rotation: [0, 0, -45], translation: [-4.9, -4.9, 0], scale: [0.62, 0.62, 0.62] }),
  // in one hand, upright, the fist around the middle of the spine (model -X), the front cover facing out
  tome: set(both([90, 90, 0], [0, 3.4, -3.8], 0.7), both([0, -70, 10], [1, -1, 0], 0.6),
    { rotation: [15, -25, 0], translation: [0, -5.6, 0], scale: [1, 1, 1] },
    { rotation: [0, 0, 0], translation: [0, 2, 0], scale: [0.5, 0.5, 0.5] },
    { rotation: [0, 0, 0], translation: [0, -5.5, 0], scale: [1, 1, 1] }),
};
for (const tier of ["apprentice", "amethyst", "netherite"]) for (const [type, display] of Object.entries(D)) {
  const id = `${tier}_${type}`;
  fs.writeFileSync(`src/main/resources/assets/orbisarcana/models/item/${id}.json`,
    JSON.stringify({ textures: { particle: `orbisarcana:item/${id}` }, display }, null, 2) + "\n");
}
console.log("foci display models written");
