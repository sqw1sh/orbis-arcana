// Builds a GeckoLib model inside Blockbench from a spec (art/specs/<id>.json), roadmap V7. Run from Blockbench
// (MCP risky_eval): eval(require('fs').readFileSync('<repo>/tools/blockbench/build-model.js', 'utf8'))('<repo>', '<id>')
// It creates a GeckoLib project, the bones and cubes, a texture painted procedurally per material (every face gets its
// own area at one texel density, shaded by the direction it faces and outlined), the animations, and saves
// art/models/<id>.bbmodel, and exports the GeckoLib geometry and animations; then run node tools/art.cjs.
//
// Spec: { "id", "type": "Item"|"Entity"|"Block", "density": px per unit (default 2),
//         "materials": { "<name>": { "pattern": "wood"|"metal"|"gem"|"leather"|"cloth"|"paper"|"glow"|"stone",
//                                     "base": "#rrggbb", "light": "#rrggbb", "dark": "#rrggbb" } },
//         "bones": [ { "name", "pivot": [x,y,z], "parent"?, "rotation"?: [x,y,z],
//                      "cubes": [ { "from": [x,y,z], "to": [x,y,z], "mat", "rotation"?: [x,y,z], "origin"?: [x,y,z], "inflate"? } ] } ],
//         "animations": { "<name>": { "length", "loop": true|false,
//                                      "bones": { "<bone>": { "rotation"|"position"|"scale": [[t, [x,y,z]], ...] } } } } }
(function () {
    return function build(repo, id) {
        const fs = require('fs');
        const spec = JSON.parse(fs.readFileSync(repo + '/art/specs/' + id + '.json', 'utf8'));
        const density = spec.density || 2;

        // ---- project ----
        newProject(Formats.geckolib_model);
        Project.name = id;
        Project.model_identifier = id;
        Project.geckolib_modid = 'orbisarcana';
        Project.geckolib_model_type = spec.type || 'Item';
        Project.box_uv = false;

        // ---- geometry ----
        const groups = {};
        const cubes = [];
        for (const b of spec.bones) {
            const parent = b.parent ? groups[b.parent] : 'root';
            const g = new Group({ name: b.name, origin: b.pivot || [0, 0, 0], rotation: b.rotation || [0, 0, 0] });
            g.addTo(parent).init();
            groups[b.name] = g;
            (b.cubes || []).forEach((c, i) => {
                const cube = new Cube({ name: b.name + '_' + i, from: c.from, to: c.to, origin: c.origin || c.pivot || b.pivot || [0, 0, 0],
                    rotation: c.rotation || [0, 0, 0], inflate: c.inflate || 0, box_uv: false });
                cube.addTo(g).init();
                cubes.push({ cube, mat: c.mat });
            });
        }

        // ---- UV packing: every face its own rectangle at one density (shelf packing) ----
        const faceSize = (cube, dir) => {
            const s = cube.size();
            switch (dir) {
                case 'north': case 'south': return [s[0], s[1]];
                case 'east': case 'west': return [s[2], s[1]];
                default: return [s[0], s[2]];
            }
        };
        const dirs = ['north', 'south', 'east', 'west', 'up', 'down'];
        const rects = [];
        for (const { cube, mat } of cubes) for (const dir of dirs) {
            const [w, h] = faceSize(cube, dir);
            rects.push({ cube, mat, dir, w: Math.max(1, Math.round(w * density)), h: Math.max(1, Math.round(h * density)) });
        }
        let size = 32, placed = false;
        while (!placed) {
            let x = 0, y = 0, rowH = 0;
            placed = true;
            for (const r of [...rects].sort((a, b) => b.h - a.h)) {
                if (x + r.w > size) { x = 0; y += rowH; rowH = 0; }
                if (r.w > size || y + r.h > size) { placed = false; break; }
                r.x = x; r.y = y;
                x += r.w; rowH = Math.max(rowH, r.h);
            }
            if (!placed) size *= 2;
        }
        Project.texture_width = size;
        Project.texture_height = size;

        // ---- painting ----
        const canvas = document.createElement('canvas');
        canvas.width = canvas.height = size;
        const ctx = canvas.getContext('2d');
        const img = ctx.createImageData(size, size);
        const hex = h => [parseInt(h.slice(1, 3), 16), parseInt(h.slice(3, 5), 16), parseInt(h.slice(5, 7), 16)];
        const hash = (x, y, s) => { let v = (x * 374761393 + y * 668265263 + s * 974711) | 0; v = Math.imul(v ^ (v >>> 13), 1274126177); return ((v ^ (v >>> 16)) >>> 0) / 4294967296; };
        const shade = { up: 1.12, north: 1.0, south: 0.96, east: 0.9, west: 0.86, down: 0.7 };
        let seed = 1;
        for (const r of rects) {
            const m = spec.materials[r.mat];
            if (!m) throw new Error('unknown material ' + r.mat);
            const base = hex(m.base), light = hex(m.light || m.base), dark = hex(m.dark || m.base);
            seed++;
            for (let py = 0; py < r.h; py++) for (let px = 0; px < r.w; px++) {
                const u = (px + 0.5) / r.w, v = (py + 0.5) / r.h, n = hash(px, py, seed);
                let t; // 0 = dark, 0.5 = base, 1 = light
                switch (m.pattern) {
                    case 'wood': t = 0.42 + 0.25 * Math.sin((r.dir === 'up' || r.dir === 'down' ? u : u * 3 + hash(px, 0, seed) * 2) * 6.3 + n) * 0.5 + (n - 0.5) * 0.22; break;
                    case 'metal': t = 0.35 + 0.55 * (1 - v) + (px === 0 || py === 0 ? 0.2 : 0) + (n - 0.5) * 0.08; break;
                    case 'gem': t = 0.3 + 0.6 * Math.max(0, 1 - Math.hypot(u - 0.35, v - 0.3) * 1.6) + ((px + py) % 3 === 0 ? 0.1 : 0) + (n - 0.5) * 0.06; break;
                    case 'leather': t = 0.4 + (n - 0.5) * 0.3 + (py % 3 === 0 && px % 2 === 0 ? -0.15 : 0); break;
                    case 'cloth': t = 0.45 + ((px + py) % 2 === 0 ? 0.06 : -0.06) + (n - 0.5) * 0.15; break;
                    case 'paper': t = 0.62 + (py % 2 === 0 ? -0.08 : 0.04) + (n - 0.5) * 0.08; break;
                    case 'stone': t = 0.4 + (n - 0.5) * 0.4; break;
                    case 'glow': t = 0.55 + 0.45 * Math.max(0, 1 - Math.hypot(u - 0.5, v - 0.5) * 1.8); break;
                    default: t = 0.5;
                }
                // a darker rim on each face gives the blocky parts crisp edges
                const edge = (px === 0 || py === 0 || px === r.w - 1 || py === r.h - 1) && r.w > 2 && r.h > 2 && m.pattern !== 'glow';
                if (edge) t -= 0.18;
                t = Math.max(0, Math.min(1, t));
                const col = t < 0.5 ? dark.map((d, k) => d + (base[k] - d) * (t / 0.5)) : base.map((b, k) => b + (light[k] - b) * ((t - 0.5) / 0.5));
                const s = m.pattern === 'glow' ? 1 : shade[r.dir];
                const i = ((r.y + py) * size + r.x + px) * 4;
                img.data[i] = Math.min(255, col[0] * s);
                img.data[i + 1] = Math.min(255, col[1] * s);
                img.data[i + 2] = Math.min(255, col[2] * s);
                img.data[i + 3] = 255;
            }
        }
        ctx.putImageData(img, 0, 0);
        const texture = new Texture({ name: id }).fromDataURL(canvas.toDataURL()).add(false);
        for (const r of rects) {
            r.cube.faces[r.dir].uv = [r.x, r.y, r.x + r.w, r.y + r.h];
            r.cube.faces[r.dir].texture = texture.uuid;
        }

        // ---- animations ----
        for (const [name, a] of Object.entries(spec.animations || {})) {
            const anim = new Animation({ name: 'animation.' + name, length: a.length, loop: a.loop ? 'loop' : 'once', snapping: 20 }).add(false);
            for (const [bone, channels] of Object.entries(a.bones)) {
                const animator = anim.getBoneAnimator(groups[bone]);
                for (const [channel, keys] of Object.entries(channels)) {
                    for (const [t, v] of keys) animator.addKeyframe({ channel, time: t, data_points: [{ x: v[0], y: v[1], z: v[2] }], interpolation: 'catmullrom' });
                }
            }
        }

        Canvas.updateAll();
        fs.writeFileSync(repo + '/art/models/' + id + '.bbmodel', Codecs.project.compile());

        // ---- GeckoLib export (Bedrock geometry; the plugin's animation format), after the .bbmodel so art.cjs sees it fresh ----
        const type = (spec.type || 'Item').toLowerCase();
        const geckolib = repo + '/src/main/resources/assets/orbisarcana/geckolib';
        fs.mkdirSync(geckolib + '/models/' + type, { recursive: true });
        let geo = Codecs.bedrock.compile();
        fs.writeFileSync(geckolib + '/models/' + type + '/' + id + '.geo.json', typeof geo === 'string' ? geo : JSON.stringify(geo, null, '\t'));
        if (Animation.all.length) {
            fs.mkdirSync(geckolib + '/animations/' + type, { recursive: true });
            const anim = Animator.buildFile(false, Animation.all.map(a => a.name));
            anim.geckolib_format_version = 2;
            fs.writeFileSync(geckolib + '/animations/' + type + '/' + id + '.animation.json', JSON.stringify(anim, null, '\t'));
        }
        return { id, cubes: cubes.length, texture: size, faces: rects.length };
    };
})()
