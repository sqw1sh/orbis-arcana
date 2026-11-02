# Changelog

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
