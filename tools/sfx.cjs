// Sound synthesis for the mod's SFX (docs/ART.md): every sound is a recipe in art/sounds/*.json, rendered by this
// script into layered audio (oscillators, filtered noise, envelopes, pitch sweeps, tremolo, echo), saved as
// art/sounds/out/<name>.wav and encoded to assets/orbisarcana/sounds/<name>.ogg with ffmpeg (libvorbis, mono, 44.1 kHz)
// when ffmpeg is on PATH (or in env FFMPEG). Mono, because Minecraft only positions mono sounds in 3D.
//
// Recipe: { "length": s, "gain": 0..1, "variants": n, "layers": [layer...], "echo": {"delay": s, "feedback": 0..1, "mix": 0..1} }
// Layer:  { "type": "sine"|"triangle"|"saw"|"square"|"noise", "freq": [startHz, endHz], "curve": "exp"|"lin",
//           "start": s, "length": s, "attack": s, "release": s, "gain": 0..1,
//           "lowpass": Hz, "highpass": Hz, "tremolo": [Hz, depth], "vibrato": [Hz, cents], "detune": cents }
// "variants" renders <name>_1..n with a slightly different seed and pitch (±4%), for sounds.json variation lists.
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

/** One-pole filters, enough for shaping noise and taming harsh oscillators. */
function onePole(cut) {
    const a = Math.exp(-2 * Math.PI * cut / RATE);
    let y = 0;
    return { low: x => (y = (1 - a) * x + a * y), high: x => x - (y = (1 - a) * x + a * y) };
}

function renderLayer(out, l, random, pitch) {
    const start = Math.floor((l.start || 0) * RATE);
    const n = Math.floor((l.length || out.length / RATE) * RATE);
    const [f0, f1] = (l.freq || [440, 440]).map(f => f * pitch * Math.pow(2, (l.detune || 0) / 1200));
    const attack = Math.max(1, (l.attack ?? 0.01) * RATE), release = Math.max(1, (l.release ?? 0.1) * RATE);
    const lp = l.lowpass ? onePole(l.lowpass) : null, hp = l.highpass ? onePole(l.highpass) : null;
    let phase = 0;
    for (let i = 0; i < n && start + i < out.length; i++) {
        const t = i / n;
        let f = l.curve === 'lin' ? f0 + (f1 - f0) * t : f0 * Math.pow(f1 / f0, t);
        if (l.vibrato) f *= Math.pow(2, Math.sin(2 * Math.PI * l.vibrato[0] * i / RATE) * l.vibrato[1] / 1200);
        phase = (phase + f / RATE) % 1;
        let x;
        switch (l.type) {
            case 'noise': x = random() * 2 - 1; break;
            case 'saw': x = 2 * phase - 1; break;
            case 'square': x = phase < 0.5 ? 1 : -1; break;
            case 'triangle': x = 1 - 4 * Math.abs(phase - 0.5); break;
            default: x = Math.sin(2 * Math.PI * phase);
        }
        if (lp) x = lp.low(x);
        if (hp) x = hp.high(x);
        let env = Math.min(1, i / attack) * Math.min(1, (n - i) / release);
        if (l.tremolo) env *= 1 - l.tremolo[1] * (0.5 + 0.5 * Math.sin(2 * Math.PI * l.tremolo[0] * i / RATE));
        out[start + i] += x * env * (l.gain ?? 0.5);
    }
}

function render(recipe, seed, pitch) {
    const out = new Float32Array(Math.ceil(recipe.length * RATE));
    const random = rng(seed);
    for (const l of recipe.layers) renderLayer(out, l, random, pitch);
    if (recipe.echo) {
        const d = Math.floor(recipe.echo.delay * RATE), wet = new Float32Array(out.length);
        for (let i = d; i < out.length; i++) wet[i] = out[i - d] + wet[i - d] * recipe.echo.feedback;
        for (let i = 0; i < out.length; i++) out[i] += wet[i] * recipe.echo.mix;
    }
    // normalise to the recipe gain, then a short fade so nothing clicks at the end
    let peak = 0;
    for (const x of out) peak = Math.max(peak, Math.abs(x));
    const k = peak ? (recipe.gain ?? 0.8) / peak : 0, fade = Math.min(out.length, RATE / 100);
    for (let i = 0; i < out.length; i++) out[i] *= k * Math.min(1, (out.length - i) / fade);
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

const only = process.argv.slice(2);
const recipes = fs.existsSync(srcDir) ? fs.readdirSync(srcDir).filter(f => f.endsWith('.json')) : [];
const encoder = ffmpeg();
if (!encoder) console.log('ffmpeg not found: writing WAV only (install ffmpeg or set FFMPEG to encode OGG)');
fs.mkdirSync(wavDir, { recursive: true });
for (const file of recipes) {
    const name = file.replace(/\.json$/, '');
    if (only.length && !only.includes(name)) continue;
    const recipe = JSON.parse(fs.readFileSync(path.join(srcDir, file), 'utf8'));
    const count = recipe.variants || 1;
    for (let v = 1; v <= count; v++) {
        const out = count > 1 ? `${name}_${v}` : name;
        const pitch = count > 1 ? 1 + ((v - 1) / Math.max(1, count - 1) - 0.5) * 0.08 : 1;
        const wavFile = path.join(wavDir, `${out}.wav`);
        fs.writeFileSync(wavFile, wav(render(recipe, 1000 + v * 7919, pitch)));
        if (encoder) {
            const ogg = path.join(oggDir, `${out}.ogg`);
            fs.mkdirSync(path.dirname(ogg), { recursive: true });
            execFileSync(encoder, ['-y', '-loglevel', 'error', '-i', wavFile, '-ac', '1', '-ar', String(RATE), '-c:a', 'libvorbis', '-q:a', '5', ogg]);
            console.log(`${out}.ogg`);
        } else console.log(`${out}.wav`);
    }
}
