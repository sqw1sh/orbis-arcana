// Display transforms of the 3D foci (roadmap V7): models/item/<tier>_<type>.json for every tier, one set per type.
// The GeckoLib model stands on the item origin, which every context puts at the display pivot (hand, slot centre).
// Found in game: rotation X 90 stands an item upright in the third-person hand, and the hand's +Z is then "up", so
// translation Z moves the grip into the fist. Icons are drawn into a 16x16 texture and clipped: a long focus lies on
// the diagonal with its head in the upper-right corner. Run after changing a value: node tools/foci-display.cjs
const fs = require("fs");
const both = (r, t, s, mirror = true) => ({ r: { rotation: r, translation: t, scale: [s, s, s] },
  l: { rotation: mirror ? [r[0], -r[1], -r[2]] : r, translation: mirror ? [-t[0], t[1], t[2]] : t, scale: [s, s, s] } });
const set = (tp, fp, gui, ground, fixed) => ({
  thirdperson_righthand: tp.r, thirdperson_lefthand: tp.l, firstperson_righthand: fp.r, firstperson_lefthand: fp.l,
  gui, ground, fixed,
});
const D = {
  // grip ~2.5 units above the origin: resting in the palm, tilted forward
  wand: set(both([55, 0, 0], [0, 0.5, -1.5], 0.9), both([-10, -10, 8], [0.5, -3.5, -1], 0.75),
    { rotation: [0, 0, -45], translation: [-5.3, -5.3, 0], scale: [1, 1, 1] },
    { rotation: [0, 0, 0], translation: [0, 2, 0], scale: [0.5, 0.5, 0.5] },
    { rotation: [0, 0, -45], translation: [-5.3, -5.3, 0], scale: [1, 1, 1] }),
  // upright like a walking staff, the fist around the grip ~13.5 units up
  staff: set(both([90, 0, 0], [0, 0, -12.5], 1), both([-12, -15, 6], [2, -5, -2], 0.48),
    { rotation: [0, 0, -45], translation: [-4.9, -4.9, 0], scale: [0.62, 0.62, 0.62] },
    { rotation: [0, 0, 0], translation: [0, 0, 0], scale: [0.35, 0.35, 0.35] },
    { rotation: [0, 0, -45], translation: [-4.9, -4.9, 0], scale: [0.62, 0.62, 0.62] }),
  // carried by its lower edge, the cover facing out
  tome: set(both([90, 0, 0], [0, 0.5, -2.5], 0.7), both([0, -70, 10], [1, -1, 0], 0.6),
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
