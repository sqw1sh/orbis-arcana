# Changelog

## 1.2.0 — Fire, Ice and Lightning

For Minecraft 26.3, NeoForge 26.3.0.100+, GeckoLib 5.5.7+, Player Animation Library 1.2.8+. 84 spells (was 30), in all eight rarities.

Every new spell has its own magic circle, its own release and impact sounds, its own effects, an apogee look at mastery 15 and three milestones. Many also have their own release pose.

### Fire (22 spells)
- New: Ember Spray, Kindle, Heat Wave, Flame Lash, Cinder Volley, Magma Burst, Scorching Ray (channel), Flare, Blazing Ring, Ember Ward, Fire Wall, Scorched Earth, Dragon's Breath (channel), Immolation, Meteor (legendary), Inferno Pillar (mythic), Primordial Flame (ancient), Fallen Sun (divine).
- Talents: third nodes Cinderfield (enemies killed by your fire leave burning ground) and Flame Skin (melee attackers catch fire).

### Ice (21 spells)
- New: Frost Bolt, Chill Touch, Frost Breath, Ice Lance, Hailstorm, Frost Nova, Glacial Spikes, Ice Armor, Frostbite, Cold Ray (channel), Blizzard, Deep Freeze, Frost Vortex (channel), Winter's Embrace, Ice Comet (legendary), Glacial Rampart (mythic), Eternal Glacier (ancient), Absolute Zero (divine).
- Talents: third nodes Permafrost (your freezes last 50% longer) and Frozen Heart (below half health, enemies near you are slowed).

### Lightning (20 spells)
- New: Spark, Static Shock, Arc Lash, Thunderclap, Chain Lightning, Ball Lightning, Static Field, Surge, Lightning Strike, Arc Beam (channel), Storm Barrier, Thunderstorm, Voltaic Aura, Thunderer's Spear (legendary), Tempest (mythic), Primordial Thunder (ancient), Heaven's Wrath (divine).
- Talents: third nodes Superconductor (one more jump, 1.5x jump range) and Static Shield (every 4th hit you take is absorbed).

### Talent trees
- Fire, Ice and Lightning branches have a third node. There are still three points, so you take one whole branch or both halfway. Other schools keep two nodes per branch until their pack.

### Rarities and loot
- Legendary, ancient and divine spells are in the game now.
- Legendary scrolls come from the observatory vault, the scriptorium, Nether loot and the higher-school tables. Ancient ones come from vaults and from the Warden and the Elder Guardian. Divine ones drop from the Wither. End cities can hold all three.

### Fixes
- Composite spells respect the Embers talent (+30% burning) and Splitting (+1 jump).

### For addons and pack makers
- The `orbisarcana:composite` behavior has new shapes: `zone` (on the ground), `aura` (follows the caster), `wall` (across the aim) and `meteor` (falls from the sky). It also supports channels (`pulse_ticks`) and burning ground (`ground_fire_ticks`).
- New composite effects: `shield` and `speed`.
- New composite visuals:
  - `release_sound` and `impact_sound`: own sound events;
  - `apogee_travel` and `apogee_impact`: extra effects at mastery 15;
  - `chain`: a bolt's jump drawn along its length.
- `tools/new-spell.cjs` knows every shape, channels and all eight rarities. `tools/balance.cjs` has corridors for all eight.

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
