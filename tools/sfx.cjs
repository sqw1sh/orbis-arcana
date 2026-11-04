// Sound synthesis for the mod's SFX (roadmap V6): every sound is a recipe in art/sounds/*.json, rendered into layered
// mono audio, saved as art/sounds/out/<name>.wav and encoded to assets/orbisarcana/sounds/<name>.ogg (libvorbis, 44.1 kHz)
// with ffmpeg (PATH, env FFMPEG, or the winget link). Mono, because Minecraft only positions mono sounds in 3D.
//
// Recipe: { "length": s, "loudness": dB RMS (default -16), "variants": n, "loop": bool, "layers": [layer...],
//           "reverb": {"size": 0..1, "mix": 0..1}, "compress": {"threshold": dB, "ratio": n, "attack": s, "release": s}, "echo": {"delay": s, "feedback": 0..1, "mix": 0..1} }
// Layer:  { "type": "sine"|"triangle"|"saw"|"square"|"noise"|"fm"|"crackle",
//           "freq": [startHz, endHz] (or a number), "curve": "exp"|"lin", "detune": cents, "vibrato": [Hz, cents],
//           "start": s, "length": s, "attack": s, "release": s, "decay": s (exponential fade time constant), "gain": 0..1,
//           "tremolo": [Hz, depth], "drive": saturation amount (1 = gentle, 4 = gritty),
//           "lowpass" / "highpass": Hz or [startHz, endHz], "bandpass": Hz or [startHz, endHz], "q": resonance (default 0.8),
//           fm: "ratio" (modulator/carrier), "index": [start, end];  crackle: "density" (pops per second),
//           "repeat": {"count": n, "every": s, "jitter": s, "freq": [lo, hi]} — the layer is played n times (glints, bubbles) }
// "variants" renders <name>_1..n with another random seed and a slightly different pitch (±5%), for sounds.json lists.
// "loop" makes the end blend into the start, so the sound loops without a click.
// Usage: node tools/sfx.cjs [name...]   (no names: every recipe)
const fs = require('fs');
const path = require('path');
const { execFileSync } = require('child_process');

const RATE = 44100;
const root = path.join(__dirname, '..');
const srcDir = path.join(root, 'art', 'sounds');
const wavDir = path.join(srcDir, 'out');
const oggDir = path.join(root, 'src', 'main', 'resources', 'assets', 'orbisarcana', 'sounds');

function rng(seed) {
    let s = seed >>> 0 || 1;
    return () => ((s = (s * 1664525 + 1013904223) >>> 0) / 4294967296);
}

/** RBJ biquad filter; set(type, freq, q) may be called per sample for sweeps. */
function biquad() {
    let b0 = 1, b1 = 0, b2 = 0, a1 = 0, a2 = 0, x1 = 0, x2 = 0, y1 = 0, y2 = 0, last = '';
    return {
        set(type, f, q) {
            const key = type + Math.round(f) + q;
            if (key === last) return;
            last = key;
            const w = 2 * Math.PI * Math.min(f, RATE * 0.45) / RATE, c = Math.cos(w), s = Math.sin(w), al = s / (2 * q);
            let n0, n1, n2;
            if (type === 'lowpass') { n0 = (1 - c) / 2; n1 = 1 - c; n2 = (1 - c) / 2; }
            else if (type === 'highpass') { n0 = (1 + c) / 2; n1 = -(1 + c); n2 = (1 + c) / 2; }
            else { n0 = al; n1 = 0; n2 = -al; }
            const d0 = 1 + al;
            b0 = n0 / d0; b1 = n1 / d0; b2 = n2 / d0; a1 = (-2 * c) / d0; a2 = (1 - al) / d0;
        },
        run(x) {
            const y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2;
            x2 = x1; x1 = x; y2 = y1; y1 = y;
            return y;
        },
    };
}

const sweep = (v, t) => Array.isArray(v) ? v[0] * Math.pow(v[1] / v[0], t) : v;

function renderLayer(out, l, random, pitch) {
    const reps = l.repeat ? l.repeat.count : 1;
    for (let r = 0; r < reps; r++) {
        const offset = l.repeat ? r * l.repeat.every + (random() - 0.5) * 2 * (l.repeat.jitter || 0) : 0;
        let freq = l.freq ?? [440, 440];
        if (!Array.isArray(freq)) freq = [freq, freq];
        if (l.repeat && l.repeat.freq) {
            const f = l.repeat.freq[0] * Math.pow(l.repeat.freq[1] / l.repeat.freq[0], random());
            freq = [f * freq[0] / 440, f * freq[1] / 440];
        }
        renderOne(out, l, random, pitch, Math.max(0, (l.start || 0) + offset), freq);
    }
}

function renderOne(out, l, random, pitch, startSec, freq) {
    const start = Math.floor(startSec * RATE);
    const n = Math.floor((l.length || out.length / RATE - startSec) * RATE);
    const det = Math.pow(2, (l.detune || 0) / 1200);
    const [f0, f1] = freq.map(f => f * pitch * det);
    const attack = Math.max(1, (l.attack ?? 0.01) * RATE), release = Math.max(1, (l.release ?? 0.1) * RATE);
    const filters = [];
    for (const type of ['lowpass', 'highpass', 'bandpass']) if (l[type] != null) filters.push([type, l[type], biquad()]);
    const q = l.q ?? 0.8;
    let phase = 0, mphase = 0;
    for (let i = 0; i < n && start + i < out.length; i++) {
        const t = i / n;
        let f = l.curve === 'lin' ? f0 + (f1 - f0) * t : f0 * Math.pow(f1 / f0, t);
        if (l.vibrato) f *= Math.pow(2, Math.sin(2 * Math.PI * l.vibrato[0] * i / RATE) * l.vibrato[1] / 1200);
        let x;
        switch (l.type) {
            case 'noise': x = random() * 2 - 1; break;
            case 'crackle': x = random() < (l.density || 30) / RATE ? (random() * 2 - 1) * 4 : 0; break;
            case 'fm': {
                const idx = sweep(l.index ?? [2, 0.5], t);
                mphase = (mphase + f * (l.ratio ?? 2) / RATE) % 1;
                phase = (phase + f / RATE) % 1;
                x = Math.sin(2 * Math.PI * phase + idx * Math.sin(2 * Math.PI * mphase));
                break;
            }
            default: {
                phase = (phase + f / RATE) % 1;
                switch (l.type) {
                    case 'saw': x = 2 * phase - 1; break;
                    case 'square': x = phase < 0.5 ? 1 : -1; break;
                    case 'triangle': x = 1 - 4 * Math.abs(phase - 0.5); break;
                    default: x = Math.sin(2 * Math.PI * phase);
                }
            }
        }
        for (const [type, v, bq] of filters) {
            bq.set(type, sweep(v, t), type === 'bandpass' ? q * 2 : q);
            x = bq.run(x);
        }
        if (l.drive) x = Math.tanh(x * l.drive) / Math.tanh(l.drive);
        let env = Math.min(1, i / attack) * Math.min(1, (n - i) / release);
        if (l.decay) env *= Math.exp(-i / RATE / l.decay);
        if (l.tremolo) env *= 1 - l.tremolo[1] * (0.5 + 0.5 * Math.sin(2 * Math.PI * l.tremolo[0] * i / RATE));
        out[start + i] += x * env * (l.gain ?? 0.5);
    }
}

/** Small Schroeder reverb: four combs in parallel, two allpasses in series. */
function reverb(x, size, mix) {
    const combs = [1116, 1188, 1277, 1356].map(d => ({ d: Math.round(d * (0.6 + size)), buf: null, i: 0 }));
    const out = new Float32Array(x.length);
    const fb = 0.72 + 0.2 * size;
    for (const c of combs) {
        c.buf = new Float32Array(c.d);
        for (let n = 0; n < x.length; n++) {
            const y = c.buf[c.i];
            c.buf[c.i] = x[n] + y * fb;
            c.i = (c.i + 1) % c.d;
            out[n] += y * 0.25;
        }
    }
    for (const d of [556, 441]) {
        const buf = new Float32Array(d);
        let i = 0;
        for (let n = 0; n < out.length; n++) {
            const y = buf[i], v = out[n];
            buf[i] = v + y * 0.5;
            out[n] = y - v * 0.5;
            i = (i + 1) % d;
        }
    }
    for (let n = 0; n < x.length; n++) x[n] = x[n] * (1 - mix * 0.5) + out[n] * mix;
}

/** Feed-forward compressor ({threshold dB, ratio, attack s, release s}): evens the body out so hits feel dense and loud. */
function compress(x, c) {
    const th = Math.pow(10, (c.threshold ?? -18) / 20), ratio = c.ratio ?? 4;
    const at = Math.exp(-1 / ((c.attack ?? 0.005) * RATE)), rl = Math.exp(-1 / ((c.release ?? 0.15) * RATE));
    let env = 0;
    for (let i = 0; i < x.length; i++) {
        const a = Math.abs(x[i]);
        env = a > env ? at * env + (1 - at) * a : rl * env + (1 - rl) * a;
        if (env > th) x[i] *= Math.pow(env / th, 1 / ratio - 1);
    }
}

function render(recipe, seed, pitch) {
    let out = new Float32Array(Math.ceil(recipe.length * RATE));
    const random = rng(seed);
    for (const l of recipe.layers) renderLayer(out, l, random, pitch);
    if (recipe.echo) {
        const d = Math.floor(recipe.echo.delay * RATE), wet = new Float32Array(out.length);
        for (let i = d; i < out.length; i++) wet[i] = out[i - d] + wet[i - d] * recipe.echo.feedback;
        for (let i = 0; i < out.length; i++) out[i] += wet[i] * recipe.echo.mix;
    }
    if (recipe.reverb) reverb(out, recipe.reverb.size ?? 0.5, recipe.reverb.mix ?? 0.3);
    if (recipe.compress) compress(out, recipe.compress);
    if (recipe.loop) {
        // blend the last 0.25 s into the start and cut it off: the end meets the start seamlessly
        const fade = Math.min(Math.floor(0.25 * RATE), Math.floor(out.length / 4));
        const looped = out.slice(0, out.length - fade);
        for (let i = 0; i < fade; i++) {
            const k = i / fade;
            looped[i] = looped[i] * k + out[out.length - fade + i] * (1 - k);
        }
        out = looped;
    }
    // DC blocker, then level by loudness (RMS, dB) rather than by peak: sounds made of short pops would otherwise come
    // out much quieter; a soft limiter keeps the peaks under full scale
    let px = 0, py = 0;
    for (let i = 0; i < out.length; i++) { const y = out[i] - px + 0.995 * py; px = out[i]; py = y; out[i] = y; }
    let sum = 0;
    for (const x of out) sum += x * x;
    const rms = Math.sqrt(sum / Math.max(1, out.length));
    const k = rms ? Math.pow(10, (recipe.loudness ?? -16) / 20) / rms : 0, fade = recipe.loop ? 0 : Math.min(out.length, RATE / 100);
    for (let i = 0; i < out.length; i++) {
        const x = out[i] * k;
        out[i] = 0.95 * Math.tanh(x) * (fade ? Math.min(1, (out.length - i) / fade) : 1);
    }
    return out;
}

function wav(samples) {
    const b = Buffer.alloc(44 + samples.length * 2);
    b.write('RIFF', 0); b.writeUInt32LE(36 + samples.length * 2, 4); b.write('WAVEfmt ', 8);
    b.writeUInt32LE(16, 16); b.writeUInt16LE(1, 20); b.writeUInt16LE(1, 22); b.writeUInt32LE(RATE, 24);
    b.writeUInt32LE(RATE * 2, 28); b.writeUInt16LE(2, 32); b.writeUInt16LE(16, 34);
    b.write('data', 36); b.writeUInt32LE(samples.length * 2, 40);
    samples.forEach((x, i) => b.writeInt16LE(Math.round(Math.max(-1, Math.min(1, x)) * 32767), 44 + i * 2));
    return b;
}

function ffmpeg() {
    // a fresh winget install is not on the PATH of already running shells
    const winget = process.env.LOCALAPPDATA && path.join(process.env.LOCALAPPDATA, 'Microsoft', 'WinGet', 'Links', 'ffmpeg.exe');
    for (const exe of [process.env.FFMPEG, 'ffmpeg', winget].filter(Boolean)) {
        try { execFileSync(exe, ['-version'], { stdio: 'ignore' }); return exe; } catch {}
    }
    return null;
}

/** Recipes are art/sounds/<group>/<name>.json; the sound name is <group>/<name> (also its path under sounds/). */
function recipes(dir, prefix = '') {
    const out = [];
    for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
        if (e.isDirectory() && e.name !== 'out') out.push(...recipes(path.join(dir, e.name), prefix + e.name + '/'));
        else if (e.name.endsWith('.json')) out.push([prefix + e.name.replace(/\.json$/, ''), path.join(dir, e.name)]);
    }
    return out;
}

const only = process.argv.slice(2);
const encoder = ffmpeg();
if (!encoder) console.log('ffmpeg not found: writing WAV only (install ffmpeg or set FFMPEG to encode OGG)');
let count = 0;
for (const [name, file] of fs.existsSync(srcDir) ? recipes(srcDir) : []) {
    if (only.length && !only.some(o => name === o || name.startsWith(o + '/'))) continue;
    const recipe = JSON.parse(fs.readFileSync(file, 'utf8'));
    const variants = recipe.variants || 1;
    for (let v = 1; v <= variants; v++) {
        const out = variants > 1 ? `${name}_${v}` : name;
        const pitch = variants > 1 ? 1 + ((v - 1) / Math.max(1, variants - 1) - 0.5) * 0.1 : 1;
        const wavFile = path.join(wavDir, `${out}.wav`);
        fs.mkdirSync(path.dirname(wavFile), { recursive: true });
        fs.writeFileSync(wavFile, wav(render(recipe, 1000 + v * 7919 + name.length * 131, pitch)));
        if (encoder) {
            const ogg = path.join(oggDir, `${out}.ogg`);
            fs.mkdirSync(path.dirname(ogg), { recursive: true });
            execFileSync(encoder, ['-y', '-loglevel', 'error', '-i', wavFile, '-ac', '1', '-ar', String(RATE), '-c:a', 'libvorbis', '-q:a', '5', ogg]);
        }
        count++;
    }
}
console.log(`${count} sound file(s) ${encoder ? 'encoded to OGG' : 'written as WAV'}`);
