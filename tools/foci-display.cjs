// Display transforms of the 3D foci (roadmap V7): models/item/<tier>_<type>.json for every tier, one set per type.
// The GeckoLib model stands on the item origin (bottom centre); the hand holds an item at its centre (8, 8, 8),
// so each type is moved to put its grip there. Run after changing a value: node tools/foci-display.cjs
const fs = require("fs");
const D = {
  // grip of the wand ~2.5 units up, of the staff ~13.5, the tome is held by its spine
  wand: {
    thirdperson_righthand: { rotation: [-20, 0, 0], translation: [0, 2.5, 1.5], scale: [0.9, 0.9, 0.9] },
    thirdperson_lefthand: { rotation: [-20, 0, 0], translation: [0, 2.5, 1.5], scale: [0.9, 0.9, 0.9] },
    firstperson_righthand: { rotation: [-15, -10, 10], translation: [0, 2, 0], scale: [0.75, 0.75, 0.75] },
    firstperson_lefthand: { rotation: [-15, 10, -10], translation: [0, 2, 0], scale: [0.75, 0.75, 0.75] },
    gui: { rotation: [0, 0, -45], translation: [-0.9, -0.9, 0], scale: [1.15, 1.15, 1.15] },
    ground: { rotation: [0, 0, 0], translation: [0, 2, 0], scale: [0.5, 0.5, 0.5] },
    fixed: { rotation: [0, 0, -45], translation: [-0.9, -0.9, 0], scale: [1.15, 1.15, 1.15] },
  },
  staff: {
    thirdperson_righthand: { rotation: [-10, 0, 0], translation: [0, -6, 1.5], scale: [1, 1, 1] },
    thirdperson_lefthand: { rotation: [-10, 0, 0], translation: [0, -6, 1.5], scale: [1, 1, 1] },
    firstperson_righthand: { rotation: [-12, -15, 6], translation: [2, -5, -2], scale: [0.48, 0.48, 0.48] },
    firstperson_lefthand: { rotation: [-12, 15, -6], translation: [-2, -5, -2], scale: [0.48, 0.48, 0.48] },
    // icons are drawn into a 16x16 texture and clipped: the ring head (y 29.4) is placed in the upper-right corner,
    // the foot may be cut off like a vanilla tool handle
    gui: { rotation: [0, 0, -45], translation: [-4.9, -4.9, 0], scale: [0.62, 0.62, 0.62] },
    ground: { rotation: [0, 0, 0], translation: [0, 0, 0], scale: [0.35, 0.35, 0.35] },
    fixed: { rotation: [0, 0, -45], translation: [-4.9, -4.9, 0], scale: [0.62, 0.62, 0.62] },
  },
  tome: {
    thirdperson_righthand: { rotation: [0, 90, 0], translation: [0, 0, 1], scale: [0.7, 0.7, 0.7] },
    thirdperson_lefthand: { rotation: [0, -90, 0], translation: [0, 0, 1], scale: [0.7, 0.7, 0.7] },
    firstperson_righthand: { rotation: [0, -70, 10], translation: [1, 1, 0], scale: [0.6, 0.6, 0.6] },
    firstperson_lefthand: { rotation: [0, 70, -10], translation: [-1, 1, 0], scale: [0.6, 0.6, 0.6] },
    gui: { rotation: [15, -25, 0], translation: [0, 2, 0], scale: [1.05, 1.05, 1.05] },
    ground: { rotation: [0, 0, 0], translation: [0, 2, 0], scale: [0.5, 0.5, 0.5] },
    fixed: { rotation: [0, 0, 0], translation: [0, 2, 0], scale: [1, 1, 1] },
  },
};
for (const tier of ["apprentice", "amethyst", "netherite"]) for (const [type, display] of Object.entries(D)) {
  const id = `${tier}_${type}`;
  fs.writeFileSync(`src/main/resources/assets/orbisarcana/models/item/${id}.json`,
    JSON.stringify({ textures: { particle: `orbisarcana:item/${id}` }, display }, null, 2) + "\n");
}
console.log("foci display models written");
