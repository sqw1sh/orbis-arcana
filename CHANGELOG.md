# Changelog

## 1.1.0 — visual overhaul

For Minecraft 26.3, NeoForge 26.3.0.100+. **New required dependencies:** GeckoLib 5.5.7+ and Player Animation Library 1.2.8+.

### Spell visuals
- New effects engine: Snowstorm particle effects plus volumetric shapes. The shapes are textured spheres, beams, cones, shockwave rings, spirals and ribbon trails with scrolling energy textures, plus fresnel glow and camera shake. Works under Iris shader packs. An effects quality option (Low to Ultra) sets the particle count and limit.
- All 30 spells redone on the new engine, each with its own effects.
- Magic circles 3.0: a geometry plate per school and more layers by rarity (runes, ornament, floating tier, crystals, armillary rings). The circles phase in, brighten as the spell charges and flash on release.

### Animation and models
- Casting animations seen by every nearby player: a charge and release pose for each targeting type and signature poses for many spells (dash, teleport, heal, judgment, guard, dome, thrust, toggle). In first person, the arms are animated (can be turned off).
- 3D animated models for every focus, Curios accessory, misc item, the Resonance Altar, the Guardian Treant and the Shadow Decoy.
- Foci are held like a sword; tomes are held in one hand by the clasp.

### Sound
- A synthesised sound set with no vanilla sounds in casting. Each school has a charge loop and its own release, impact and hit sounds. There are also interface sounds and mastery sounds. Every sound has subtitles.

### Interface
- New UI kit and an arcane tooltip style for all mod items. The mana bar HUD, spell card, radial menu and Resonance Altar screen are redesigned.
- The grimoire is one window with five tabs. Overview shows the caster model. Spells has search and school filters. Schools and Talents are reworked. The 3D Atlas previews each spell with its circle.

### Content
- New spell: Searing Bolt (Fire, common).
- Aether Crystal ore restyled: vanilla stone or deepslate with glowing purple shards.

### For addons and pack makers
- `orbisarcana:composite` behavior. A spell is built from a shape, effects and visuals in JSON, with no Java. Milestones and mastery scale it like any other spell.
- `tools/new-spell.cjs` scaffolds a composite spell. Template visuals `tmpl_*` are tinted by school colour.
- Effects are data: `assets/<ns>/orbisarcana/vfx/*.json`, replaceable by resource packs.

## 1.0.0 — first release

For Minecraft 26.3, NeoForge 26.3.0.100+.

### Magic
- 30 spells in 10 schools (Fire, Earth, Water, Air, Ice, Lightning, Nature, Light, Dark, Astral), each with milestones at mastery 5, 10 and 15.
- Four cast modes: instant, charged, channelled, toggled. Mana with in-combat and calm regeneration; casts can be interrupted by heavy hits.
- Spell scrolls are learned permanently; duplicates become mastery XP or Aether Ink. Higher schools need a Seal of Comprehension.
- Radial menu with three pages, grimoire, school screen, cast HUD.
- Procedural magic circles for every spell, compatible with Iris shader packs.

### Schools
- Resonance Altar: bind, switch with a ritual (Dissonance), reset talents.
- Resonance up to 10 with talent points; a talent tree and an Echo for every school.

### Items and world
- Foci: wand, staff and tome in three tiers; the `orbisarcana:focus` component turns any item into a focus.
- Mana potion, Aether Shard, Aether Ink, three Seals of Comprehension, the "First Seals" guide book (given on first login with a starter scroll).
- Aether Crystal ore in the Overworld.
- Structures: Ruined Observatory (mountains: glyph puzzle, sealed vault, guardians) and Flooded Scriptorium (oceans and swamps: trapped archive).
- Scrolls in vanilla chests by school and region, from witches and evokers, from librarians and wandering traders.
- Own creatures: Guardian Treant (Thorn Seed) and Shadow Decoy (Shadow Step).

### Compatibility (all optional)
- Curios: 12 accessories. JEI: per-spell scroll entries and information pages. Jade: altar, glyph rings, seals. Iris: circle pipeline.

### For addons
- Public API (`com.orbisarcana.api`, separate api jar): services, school registry with addon Echoes, cast/learn/mastery/attunement events, `reportHit` / `reportHeal` for custom behaviors, the focus component.
- `example-addon/`: a complete example (Blood school, Bloodlust Echo, life_drain behavior, Blood Bolt spell).

### Polish
- Casting sounds with subtitles (a release sound per school), accessibility options (auto-cast, press instead of hold), English and Russian.
