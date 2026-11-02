// Generates the UI kit of Orbis Arcana (docs/ART.md §2): GUI sprites for panels, buttons, tabs, bars, the HUD mana orb,
// the radial menu, frames, hint icons and the school sigils. Run from the project root: node tools/gui.cjs
// Output: assets/orbisarcana/textures/gui/sprites/ui/*.png (+ .mcmeta for nine-slice / stretch scaling), sprite ids
// orbisarcana:ui/<name>. Frames are drawn pixel-exact at GUI scale 1; round shapes and icons are signed-distance shapes
// rendered with 4x4 supersampling, so they stay smooth without blurring. White sprites are tinted in code.
const fs = require("fs");
const { png } = require("./lib/png.cjs");

const OUT = "src/main/resources/assets/orbisarcana/textures/gui/sprites/ui/";
const hex = s => [parseInt(s.slice(1, 3), 16), parseInt(s.slice(3, 5), 16), parseInt(s.slice(5, 7), 16), s.length > 7 ? parseInt(s.slice(7, 9), 16) : 255];

// ---- tokens (docs/ART.md) ----
const T = {
  void: "#07090f", slate: "#0e141e", slateHi: "#131b27", surface: "#16202c", line: "#2a3646", lineHi: "#3a4a5e",
  gold: "#d9b26a", goldDark: "#8a6a35", goldLight: "#f3d99a", teal: "#5fe3c8", tealDark: "#1f6b5e",
  mana: "#58a8ff", manaDark: "#1d3f8a", manaLight: "#b8dcff", danger: "#ff6b6b", dangerDark: "#4a1518", white: "#ffffff",
};

// ---- canvas ----
function canvas(w, h) {
  const data = Buffer.alloc(w * h * 4);
  const c = {
    w, h, data,
    /** Alpha-blends colour (hex or rgba array) at (x, y) with extra coverage a (0..1). */
    put(x, y, col, a = 1) {
      if (x < 0 || y < 0 || x >= w || y >= h || a <= 0) return;
      const [r, g, b, al] = typeof col === "string" ? hex(col) : col;
      const sa = (al / 255) * Math.min(1, a), i = (y * w + x) * 4, da = data[i + 3] / 255, oa = sa + da * (1 - sa);
      if (oa <= 0) return;
      data[i] = Math.round((r * sa + data[i] * da * (1 - sa)) / oa);
      data[i + 1] = Math.round((g * sa + data[i + 1] * da * (1 - sa)) / oa);
      data[i + 2] = Math.round((b * sa + data[i + 2] * da * (1 - sa)) / oa);
      data[i + 3] = Math.round(oa * 255);
    },
    rect(x, y, rw, rh, col) { for (let j = y; j < y + rh; j++) for (let i = x; i < x + rw; i++) c.put(i, j, col); },
    /** 1-px rectangle outline inset by `inset`. */
    frame(inset, col) {
      c.rect(inset, inset, w - 2 * inset, 1, col); c.rect(inset, h - 1 - inset, w - 2 * inset, 1, col);
      c.rect(inset, inset, 1, h - 2 * inset, col); c.rect(w - 1 - inset, inset, 1, h - 2 * inset, col);
    },
    /** Paints an SDF shape: sdf(x, y) < 0 inside; colour may be a function of (x, y). Supersampled 4x4. */
    shape(sdf, col, alpha = 1) {
      for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
        let cover = 0;
        for (let sy = 0; sy < 4; sy++) for (let sx = 0; sx < 4; sx++) if (sdf(x + (sx + 0.5) / 4, y + (sy + 0.5) / 4) < 0) cover++;
        if (cover) c.put(x, y, typeof col === "function" ? col(x + 0.5, y + 0.5) : col, alpha * cover / 16);
      }
    },
    /** Removes coverage inside the shape (for holes and cut-outs). */
    erase(sdf) {
      for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
        let cover = 0;
        for (let sy = 0; sy < 4; sy++) for (let sx = 0; sx < 4; sx++) if (sdf(x + (sx + 0.5) / 4, y + (sy + 0.5) / 4) < 0) cover++;
        const i = (y * w + x) * 4 + 3;
        data[i] = Math.round(data[i] * (1 - cover / 16));
      }
    },
  };
  return c;
}

// ---- signed distance shapes ----
const circle = (cx, cy, r) => (x, y) => Math.hypot(x - cx, y - cy) - r;
const ring = (cx, cy, r, width) => (x, y) => Math.abs(Math.hypot(x - cx, y - cy) - r) - width / 2;
const segment = (ax, ay, bx, by, width) => (x, y) => {
  const px = x - ax, py = y - ay, dx = bx - ax, dy = by - ay;
  const t = Math.max(0, Math.min(1, (px * dx + py * dy) / (dx * dx + dy * dy)));
  return Math.hypot(px - dx * t, py - dy * t) - width / 2;
};
const polygon = pts => (x, y) => {
  let inside = false;
  for (let i = 0, j = pts.length - 1; i < pts.length; j = i++) {
    const [xi, yi] = pts[i], [xj, yj] = pts[j];
    if ((yi > y) !== (yj > y) && x < ((xj - xi) * (y - yi)) / (yj - yi) + xi) inside = !inside;
  }
  return inside ? -1 : 1;
};
const union = (...s) => (x, y) => Math.min(...s.map(f => f(x, y)));
const minus = (a, b) => (x, y) => Math.max(a(x, y), -b(x, y));
/** Ring arc between angles a0..a1 (degrees, 0 = right, clockwise in screen space). */
const arc = (cx, cy, r, width, a0, a1) => (x, y) => {
  let a = (Math.atan2(y - cy, x - cx) * 180) / Math.PI;
  if (a < 0) a += 360;
  const inArc = a0 <= a1 ? a >= a0 && a <= a1 : a >= a0 || a <= a1;
  return inArc ? ring(cx, cy, r, width)(x, y) : 1;
};
const rotated = (cx, cy, deg, f) => {
  const c = Math.cos((-deg * Math.PI) / 180), s = Math.sin((-deg * Math.PI) / 180);
  return (x, y) => f(cx + (x - cx) * c - (y - cy) * s, cy + (x - cx) * s + (y - cy) * c);
};
const ellipse = (cx, cy, rx, ry) => (x, y) => (Math.hypot((x - cx) / rx, (y - cy) / ry) - 1) * Math.min(rx, ry);

// ---- output ----
const written = [];
function save(name, c, scaling) {
  const file = OUT + name + ".png";
  fs.mkdirSync(require("path").dirname(file), { recursive: true });
  fs.writeFileSync(file, png(c.w, c.h, c.data));
  const meta = file + ".mcmeta";
  if (scaling) fs.writeFileSync(meta, JSON.stringify({ gui: { scaling } }, null, 2) + "\n");
  else if (fs.existsSync(meta)) fs.unlinkSync(meta);
  written.push(name);
}
const nine = (c, border) => ({ type: "nine_slice", width: c.w, height: c.h, border, stretch_inner: true });

// ---- panels ----
/** Main panel: dark edge, gold frame, inner shadow, slate body with a lighter top band, gold diamonds in the corners. */
function panel() {
  const c = canvas(32, 32);
  c.rect(0, 0, 32, 32, T.void);
  c.frame(1, T.goldDark);
  c.rect(2, 2, 28, 28, T.slate);
  c.rect(2, 2, 28, 3, T.slateHi);
  c.frame(2, "#0a0f17");
  for (const [x, y] of [[3, 3], [28, 3], [3, 28], [28, 28]]) {
    c.put(x, y, T.gold); c.put(x - 1, y, T.goldDark); c.put(x + 1, y, T.goldDark); c.put(x, y - 1, T.goldDark); c.put(x, y + 1, T.goldDark);
  }
  c.rect(1, 1, 3, 1, T.gold); c.rect(1, 1, 1, 3, T.gold);
  c.rect(28, 1, 3, 1, T.gold); c.rect(30, 1, 1, 3, T.gold);
  c.rect(1, 30, 3, 1, T.gold); c.rect(1, 28, 1, 3, T.gold);
  c.rect(28, 30, 3, 1, T.gold); c.rect(30, 28, 1, 3, T.gold);
  save("panel", c, nine(c, 6));
}

/** Translucent HUD card: thin line frame, gold corner ticks. */
function card() {
  const c = canvas(16, 16);
  c.rect(0, 0, 16, 16, T.void + "c8");
  c.frame(0, T.line);
  c.rect(1, 1, 14, 1, "#1a2433c8");
  for (const [x, y, dx, dy] of [[0, 0, 1, 1], [15, 0, -1, 1], [0, 15, 1, -1], [15, 15, -1, -1]]) {
    c.put(x, y, T.gold); c.put(x + dx, y, T.goldDark); c.put(x, y + dy, T.goldDark);
  }
  save("card", c, nine(c, 3));
}

/** Inset well for lists, slots and fields. */
function inset() {
  const c = canvas(12, 12);
  c.rect(0, 0, 12, 12, T.surface);
  c.frame(0, T.line);
  c.rect(1, 1, 10, 1, "#0d131c");
  save("inset", c, nine(c, 3));
}

/** Buttons: normal, hover (teal edge), primary (teal body), disabled. */
function buttons() {
  const variants = {
    button: { body: "#1a2532", top: "#223042", edge: T.lineHi, bottom: "#0c121a" },
    button_hover: { body: "#1f3140", top: "#2a4152", edge: T.teal, bottom: "#0c121a" },
    button_primary: { body: "#15453f", top: "#1d5a52", edge: T.teal, bottom: "#0a2622" },
    button_primary_hover: { body: "#1b5a52", top: "#257067", edge: "#a6f5e4", bottom: "#0a2622" },
    button_disabled: { body: "#121922", top: "#151d27", edge: T.line, bottom: "#0c121a" },
  };
  for (const [name, v] of Object.entries(variants)) {
    const c = canvas(20, 20);
    c.rect(1, 1, 18, 18, v.body);
    c.rect(1, 1, 18, 2, v.top);
    c.rect(1, 18, 18, 1, v.bottom);
    c.frame(0, v.edge);
    c.put(0, 0, [0, 0, 0, 0]); c.put(19, 0, [0, 0, 0, 0]); c.put(0, 19, [0, 0, 0, 0]); c.put(19, 19, [0, 0, 0, 0]);
    // rounded corner: clear the corner pixels instead of blending over them
    for (const [x, y] of [[0, 0], [19, 0], [0, 19], [19, 19]]) c.data[(y * 20 + x) * 4 + 3] = 0;
    save(name, c, nine(c, 3));
  }
}

/** Tabs: inactive (flat), hover, selected (gold top edge, joins the panel below). */
function tabs() {
  const variants = {
    tab: { body: "#101722", edge: T.line, top: T.line },
    tab_hover: { body: "#16202c", edge: T.lineHi, top: T.teal },
    tab_selected: { body: T.slate, edge: T.goldDark, top: T.gold },
  };
  for (const [name, v] of Object.entries(variants)) {
    const c = canvas(20, 16);
    c.rect(1, 1, 18, 15, v.body);
    c.rect(0, 1, 1, 15, v.edge); c.rect(19, 1, 1, 15, v.edge);
    c.rect(1, 0, 18, 1, v.top);
    c.rect(1, 1, 18, 1, name === "tab_selected" ? "#2a2416" : v.body);
    save(name, c, nine(c, 3));
  }
}

/** Bars: frame (nine-slice) and fills (stretched): mana gradient, white (tinted) and danger. */
function bars() {
  const f = canvas(16, 8);
  f.rect(0, 0, 16, 8, T.void + "e0");
  f.frame(0, T.goldDark);
  f.rect(1, 1, 14, 1, "#05070bff");
  f.put(0, 0, T.gold); f.put(15, 0, T.gold); f.put(0, 7, T.gold); f.put(15, 7, T.gold);
  save("bar_frame", f, nine(f, 2));

  const fill = (name, top, mid, bottom, light) => {
    const c = canvas(8, 6);
    c.rect(0, 0, 8, 1, light); c.rect(0, 1, 8, 2, top); c.rect(0, 3, 8, 2, mid); c.rect(0, 5, 8, 1, bottom);
    save(name, c, { type: "stretch" });
  };
  fill("bar_fill_mana", "#7fbcff", T.mana, T.manaDark, T.manaLight);
  fill("bar_fill", "#e6e6e6", "#c4c4c4", "#7a7a7a", T.white);
  fill("bar_fill_danger", "#ff8a8a", T.danger, "#8a2a2a", "#ffd0d0");

  // a bright 1-px spark at the leading edge of a filling bar
  const s = canvas(3, 8);
  s.rect(1, 0, 1, 8, T.white + "e0"); s.rect(0, 1, 1, 6, T.white + "60"); s.rect(2, 1, 1, 6, T.white + "60");
  save("bar_spark", s);
}

/** Rarity frame (white, tinted in code) with corner brackets, and the slot well behind spell icons. */
function frames() {
  const f = canvas(20, 20);
  f.frame(0, "#ffffffb0");
  for (const [x, y, dx, dy] of [[0, 0, 1, 1], [19, 0, -1, 1], [0, 19, 1, -1], [19, 19, -1, -1]]) {
    for (let k = 0; k < 4; k++) { f.put(x + dx * k, y, T.white); f.put(x, y + dy * k, T.white); }
    f.put(x + dx, y + dy, T.white);
  }
  save("frame", f, nine(f, 4));

  const s = canvas(20, 20);
  s.rect(1, 1, 18, 18, "#0b1018");
  s.frame(0, T.line);
  s.rect(1, 1, 18, 1, "#05080d");
  save("slot", s, nine(s, 3));
}

/** Small hint chip behind cast denials: dark red body, danger edge. */
function chip() {
  const c = canvas(12, 12);
  c.rect(1, 1, 10, 10, T.dangerDark + "e8");
  c.frame(0, "#a33a3a");
  c.rect(1, 1, 10, 1, "#6a2226");
  for (const [x, y] of [[0, 0], [11, 0], [0, 11], [11, 11]]) c.data[(y * 12 + x) * 4 + 3] = 0;
  save("chip", c, nine(c, 3));
}

/** 9x9 hint icons (white, tinted in code). */
function icons() {
  const make = (name, ...shapes) => { const c = canvas(9, 9); for (const s of shapes) c.shape(s, T.white); save("icon/" + name, c); };
  make("mana", union(circle(4.5, 5.8, 2.6), polygon([[4.5, 0.6], [2.1, 4.9], [6.9, 4.9]])));
  make("cooldown", union(segment(1.5, 1, 7.5, 1, 1.2), segment(1.5, 8, 7.5, 8, 1.2), polygon([[2, 1.5], [7, 1.5], [4.5, 4.5]]),
    polygon([[4.5, 4.5], [2.2, 7.6], [6.8, 7.6]])));
  make("target", union(ring(4.5, 4.5, 3, 1.1), segment(4.5, 0, 4.5, 2.2, 1.1), segment(4.5, 6.8, 4.5, 9, 1.1),
    segment(0, 4.5, 2.2, 4.5, 1.1), segment(6.8, 4.5, 9, 4.5, 1.1)));
  make("range", union(segment(1.2, 4.5, 7.8, 4.5, 1.1), polygon([[0.3, 4.5], [2.8, 2.2], [2.8, 6.8]]), polygon([[8.7, 4.5], [6.2, 2.2], [6.2, 6.8]])));
  make("lock", union(minus(polygon([[1.5, 4], [7.5, 4], [7.5, 8.5], [1.5, 8.5]]), circle(4.5, 6.2, 0.8)), arc(4.5, 3.6, 2.1, 1.1, 180, 360)));
  make("busy", union(circle(1.8, 4.5, 1), circle(4.5, 4.5, 1), circle(7.2, 4.5, 1)));
  make("charge", polygon([[4.5, 0.6], [8.2, 4.5], [4.5, 8.4], [0.8, 4.5]]));
  make("charge_empty", minus(polygon([[4.5, 0.6], [8.2, 4.5], [4.5, 8.4], [0.8, 4.5]]), polygon([[4.5, 2.4], [6.4, 4.5], [4.5, 6.6], [2.6, 4.5]])));
}

/** School sigils, 16x16 white with a dimmer inner detail (tinted with the school colour in code). */
function sigils() {
  const make = (name, body, detail) => {
    const c = canvas(16, 16);
    c.shape(body, T.white);
    if (detail) c.erase(detail);
    save("school/" + name, c);
  };
  make("fire", polygon([[8, 0.8], [10.6, 4.4], [12.8, 8.2], [12.6, 11.8], [10.4, 14.8], [5.6, 14.8], [3.4, 11.8], [3.2, 8.6], [5, 5.6], [6.4, 7.8], [7, 4.8]]),
    polygon([[8, 7.6], [10, 10.6], [9.6, 13.2], [6.4, 13.2], [6, 10.8]]));
  const arm = deg => rotated(8, 8, deg, union(segment(8, 8, 8, 1.2, 1.4), segment(8, 3.6, 6.2, 2, 1.1), segment(8, 3.6, 9.8, 2, 1.1)));
  make("ice", union(...[0, 60, 120, 180, 240, 300].map(arm), circle(8, 8, 1.6)));
  make("lightning", polygon([[10.4, 0.6], [3.6, 9], [7.6, 9], [5.6, 15.4], [12.6, 6.2], [8.4, 6.2]]));
  make("water", union(circle(8, 10.4, 4.6), polygon([[8, 1], [3.8, 9], [12.2, 9]])), circle(6.4, 10.8, 1.3));
  make("earth", union(polygon([[0.6, 14.5], [5.8, 4.6], [8.8, 9.2], [10.8, 6.4], [15.4, 14.5]]), segment(0.8, 14.8, 15.2, 14.8, 1)),
    polygon([[5.8, 7.2], [7.4, 9.8], [4.2, 9.8]]));
  make("nature", union(rotated(8, 8, -45, ellipse(8, 7.4, 3.8, 6.4)), segment(3, 13.4, 6.4, 10, 1.2)),
    union(rotated(8, 8, -45, segment(8, 3.4, 8, 12, 0.8))));
  make("air", union(segment(1.5, 5, 10.5, 5, 1.4), arc(10.5, 3, 2, 1.4, 270, 90), segment(1.5, 8.5, 12.5, 8.5, 1.4), arc(12.5, 10.5, 2, 1.4, 270, 90),
    segment(3.5, 12, 8.5, 12, 1.4)));
  make("light", union(circle(8, 8, 3.2), ...[0, 45, 90, 135, 180, 225, 270, 315].map(a => rotated(8, 8, a, segment(8, 1, 8, 3.4, 1.3)))));
  make("dark", minus(circle(8, 8, 6.4), circle(10.8, 6, 5.2)));
  make("astral", union(polygon([[8, 0.4], [9.6, 6.4], [15.6, 8], [9.6, 9.6], [8, 15.6], [6.4, 9.6], [0.4, 8], [6.4, 6.4]]), circle(13, 3, 0.9), circle(3, 13, 0.7)));
}

/** Radial menu: the ring (192x192, drawn 1:1), the centre disc (84x84) and the octagonal slot (bg + tinted edge + glow). */
function radial() {
  const S = 192, C = S / 2;
  const r = canvas(S, S);
  r.shape(minus(circle(C, C, 92), circle(C, C, 44)), T.void, 0.72);
  r.shape(ring(C, C, 92, 1.4), T.goldDark);
  r.shape(ring(C, C, 92.6, 0.7), T.gold);
  r.shape(ring(C, C, 88, 0.6), T.line);
  r.shape(ring(C, C, 45, 1.2), T.goldDark);
  // rune ticks around the rim, longer at the sector boundaries
  for (let a = 0; a < 360; a += 5) {
    const major = (a + 22.5) % 45 === 0, len = major ? 5 : a % 15 === 0 ? 2.5 : 1.2;
    r.shape(rotated(C, C, a, segment(C, C - 88 + 1, C, C - 88 + 1 + len, major ? 1 : 0.6)), major ? T.gold : T.lineHi);
  }
  for (let a = 22.5; a < 360; a += 45) {
    r.shape(rotated(C, C, a, polygon([[C, C - 94.5], [C + 2.2, C - 92], [C, C - 89.5], [C - 2.2, C - 92]])), T.gold);
  }
  save("radial_ring", r);

  const c = canvas(84, 84);
  c.shape(circle(42, 42, 41), T.slate, 0.95);
  c.shape(ring(42, 42, 41, 1.2), T.gold);
  c.shape(ring(42, 42, 38, 0.6), T.goldDark);
  save("radial_center", c);

  const oct = (cx, cy, rad) => polygon([...Array(8)].map((_, i) => {
    const a = ((i * 45 + 22.5) * Math.PI) / 180;
    return [cx + Math.cos(a) * rad, cy + Math.sin(a) * rad];
  }));
  const bg = canvas(30, 30);
  bg.shape(oct(15, 15, 15), T.void, 0.92);
  bg.shape(oct(15, 15, 13.4), T.surface);
  bg.shape(minus(oct(15, 15, 13.4), oct(15, 16.2, 13.4)), "#22303f");
  save("radial_slot", bg);
  const edge = canvas(30, 30);
  edge.shape(minus(oct(15, 15, 15), oct(15, 15, 13.6)), T.white);
  save("radial_slot_edge", edge);
  const glow = canvas(40, 40);
  glow.shape(circle(20, 20, 19.5), (x, y) => [255, 255, 255, Math.round(255 * Math.max(0, 1 - Math.hypot(x - 20, y - 20) / 19.5) ** 2)]);
  save("glow", glow);
  // page dot (white, tinted): filled for the current set, ring for the others
  const dot = canvas(7, 7);
  dot.shape(circle(3.5, 3.5, 3), T.white);
  save("dot", dot);
  const dotEmpty = canvas(7, 7);
  dotEmpty.shape(ring(3.5, 3.5, 2.6, 1), T.white);
  save("dot_empty", dotEmpty);
}

/** Tooltip style orbisarcana:arcane (items/screens): sprites tooltip/arcane_background and tooltip/arcane_frame, laid out
 *  like the vanilla ones (100x100; the visible box starts 9 px in, the rest of the margin stays transparent). */
function tooltip() {
  const b = canvas(100, 100);
  b.rect(9, 9, 82, 82, T.void + "f2");
  b.rect(10, 10, 80, 2, "#121a26f2");
  save("../tooltip/arcane_background", b, { type: "nine_slice", width: 100, height: 100, border: 9 });
  const f = canvas(100, 100);
  for (let i = 9; i < 91; i++) { f.put(i, 9, T.goldDark); f.put(i, 90, T.goldDark); f.put(9, i, T.goldDark); f.put(90, i, T.goldDark); }
  for (const [x, y, dx, dy] of [[9, 9, 1, 1], [90, 9, -1, 1], [9, 90, 1, -1], [90, 90, -1, -1]]) {
    for (let k = 0; k < 4; k++) { f.put(x + dx * k, y, T.gold); f.put(x, y + dy * k, T.gold); }
  }
  save("../tooltip/arcane_frame", f, { type: "nine_slice", width: 100, height: 100, border: 10, stretch_inner: true });
}

panel(); card(); inset(); buttons(); tabs(); bars(); frames(); chip(); icons(); sigils(); radial(); tooltip();
console.log(`wrote ${written.length} sprites to ${OUT}`);
