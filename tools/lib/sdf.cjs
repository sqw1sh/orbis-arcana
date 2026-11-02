// Signed distance shapes for the procedural textures (circles, runes). Distances are in pixels; negative inside.
// Shapes are plain functions (x, y) -> distance; combine with union/minus.
const circle = (cx, cy, r) => (x, y) => Math.hypot(x - cx, y - cy) - r;
const ring = (cx, cy, r, w) => (x, y) => Math.abs(Math.hypot(x - cx, y - cy) - r) - w / 2;
const segment = (ax, ay, bx, by, w) => (x, y) => {
  const px = x - ax, py = y - ay, dx = bx - ax, dy = by - ay;
  const t = Math.max(0, Math.min(1, (px * dx + py * dy) / (dx * dx + dy * dy || 1)));
  return Math.hypot(px - dx * t, py - dy * t) - w / 2;
};
/** Open polyline of stroke width w through points [[x, y], ...]. */
const polyline = (pts, w) => {
  const segs = [];
  for (let i = 1; i < pts.length; i++) segs.push(segment(pts[i - 1][0], pts[i - 1][1], pts[i][0], pts[i][1], w));
  return union(...segs);
};
/** Closed polygon outline of stroke width w. */
const outline = (pts, w) => polyline([...pts, pts[0]], w);
/** Regular polygon points: n corners, radius r, first corner at angle a0 (radians, 0 = right). */
const ngon = (cx, cy, r, n, a0 = -Math.PI / 2) => [...Array(n)].map((_, i) => [cx + r * Math.cos(a0 + (i * 2 * Math.PI) / n), cy + r * Math.sin(a0 + (i * 2 * Math.PI) / n)]);
/** Star polygon {n/k}: connects every k-th corner. */
const star = (cx, cy, r, n, k, w, a0 = -Math.PI / 2) => {
  const p = ngon(cx, cy, r, n, a0), segs = [];
  for (let i = 0; i < n; i++) segs.push(segment(p[i][0], p[i][1], p[(i + k) % n][0], p[(i + k) % n][1], w));
  return union(...segs);
};
/** Arc of a ring between angles a0..a1 (radians, clockwise in image space). */
const arc = (cx, cy, r, w, a0, a1) => (x, y) => {
  let a = Math.atan2(y - cy, x - cx);
  const norm = v => ((v % (2 * Math.PI)) + 2 * Math.PI) % (2 * Math.PI);
  const s = norm(a0), e = norm(a1), t = norm(a);
  const inside = s <= e ? t >= s && t <= e : t >= s || t <= e;
  if (inside) return Math.abs(Math.hypot(x - cx, y - cy) - r) - w / 2;
  const ex = (ang) => Math.hypot(x - (cx + r * Math.cos(ang)), y - (cy + r * Math.sin(ang)));
  return Math.min(ex(a0), ex(a1)) - w / 2;
};
/** Filled convex/concave polygon with exact distance (negative inside). */
const polygon = pts => (x, y) => {
  let d = Infinity, inside = false;
  for (let i = 0, j = pts.length - 1; i < pts.length; j = i++) {
    const [xi, yi] = pts[i], [xj, yj] = pts[j];
    if ((yi > y) !== (yj > y) && x < ((xj - xi) * (y - yi)) / (yj - yi) + xi) inside = !inside;
    d = Math.min(d, segment(xi, yi, xj, yj, 0)(x, y));
  }
  return inside ? -d : d;
};
const union = (...s) => (x, y) => { let d = Infinity; for (const f of s) { const v = f(x, y); if (v < d) d = v; } return d; };
const minus = (a, b) => (x, y) => Math.max(a(x, y), -b(x, y));
/** Copies of shape f rotated n times around (cx, cy). */
const repeat = (cx, cy, n, f, phase = 0) => {
  const fs = [...Array(n)].map((_, i) => rotated(cx, cy, phase + (i * 2 * Math.PI) / n, f));
  return union(...fs);
};
const rotated = (cx, cy, ang, f) => {
  const c = Math.cos(-ang), s = Math.sin(-ang);
  return (x, y) => f(cx + (x - cx) * c - (y - cy) * s, cy + (x - cx) * s + (y - cy) * c);
};
/** Bounding radius test so far-away pixels skip expensive shapes: d is at least (dist to centre - r). */
const bounded = (cx, cy, r, f) => (x, y) => {
  const out = Math.hypot(x - cx, y - cy) - r;
  return out > 12 ? out : f(x, y);
};

module.exports = { circle, ring, segment, polyline, outline, ngon, star, arc, polygon, union, minus, repeat, rotated, bounded };
