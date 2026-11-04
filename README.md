# Orbis Arcana

Magic mod for **Minecraft Java 26.3 (NeoForge 26.3.0.100+)**. Spells are found as scrolls and learned for good. Casting draws a procedural magic circle and puts the caster in a casting pose. Spells land with volumetric effects. Every spell grows with practice and changes at mastery 5, 10 and 15. License: MIT.

**Requires** [GeckoLib](https://modrinth.com/mod/geckolib) and [Player Animation Library](https://modrinth.com/mod/player-animation-library).

## Features

- **67 spells in 10 schools and 8 rarities** — Fire, Earth, Water, Air, Ice, Lightning, Nature and the higher schools Light, Dark, Astral. Instant, charged, channelled and toggled casts; projectiles, beams, cones, waves, zones, walls, traps, dashes, teleports, auras, curses, summons.
- **Mastery** — useful casts (damage, healing, control) level a spell from 1 to 15; milestones at 5/10/15 add new effects.
- **Schools and resonance** — bind to a school at the Resonance Altar; resonance unlocks talents and the school's **Echo**, a passive power of its own (Heat, Flow, Bulwark, Impulse, Frost, Conductivity, Sprouting, Radiance, Dusk, Constellation).
- **Spell visuals** — a particle and volumetric effects engine: textured spheres, beams, cones, shockwave rings, spirals and ribbon trails with scrolling energy, plus camera shake on heavy hits. Each spell has its own effects, a release sound, an impact sound per school, and a casting pose seen in first and third person.
- **Magic circles** — layered procedural circles for every spell, shader-pack compatible (Iris), with a particle fallback and plenty of client options.
- **Foci** — wands, staves and tomes in three tiers, as animated 3D models (like the accessories, the Resonance Altar and the creatures); any item can become a focus through the `orbisarcana:focus` data component.
- **World** — Aether Crystal ore, Aether Ink, the Ruined Observatory (glyph puzzle and guardians) and the Flooded Scriptorium; scrolls in vanilla chests, from librarians, wandering traders, witches and evokers.
- **Accessibility** — subtitles for all casting sounds, auto-cast when charged, press instead of hold.
- **Languages** — English, Russian.

## Optional mods

| Mod | What you get |
|---|---|
| [Curios](https://modrinth.com/mod/curios) | 12 accessories: school rings, amulets, Ring of Echo, Scribe's Ring |
| [JEI](https://modrinth.com/mod/jei) | A separate entry for every spell scroll, information pages |
| [Jade](https://modrinth.com/mod/jade) | Your school at the altar, glyph ring hints |
| [Iris](https://modrinth.com/mod/iris) | Circles and spell effects render under shader packs |

## For addon authors

The API lives in `com.orbisarcana.api` (also published as the `orbisarcana-<version>-api.jar` artifact): services through `OrbisArcanaAPI.get()`, new schools with their own Echo (`School`, `SchoolEcho`), events (`SpellCastEvent`, `SpellLearnEvent`, `MasteryLevelUpEvent`, `AttunementChangeEvent`), the focus component. A spell made of existing behaviors is pure data: `data/<namespace>/orbisarcana/spell/<id>.json`. The `orbisarcana:composite` behavior builds a spell from parts. It takes a shape (bolt, beam, cone, burst, nova, target, self, zone, aura, wall, meteor), a list of effects (damage, ignite, slow, freeze, knockback, pull, lift, weaken, blind, heal, shield, speed) and its visuals. `node tools/new-spell.cjs` scaffolds such a spell: the JSON, a magic circle and the names. See [`example-addon/`](example-addon/) for a complete addon with a school, an Echo, a custom behavior and a spell.

## Building

```bash
./gradlew build
```

```bash
./gradlew :runGameTestServer
```

```bash
./gradlew runClient
```

Requires JDK 25. Dev runs with Curios, JEI and Jade: `./gradlew runClient -PwithCompat`.

Design documents (in Russian): [`docs/concept.md`](docs/concept.md), roadmap in [`docs/ROADMAP.md`](docs/ROADMAP.md), art pipeline in [`docs/ART.md`](docs/ART.md), progress in [`docs/PROGRESS.md`](docs/PROGRESS.md), decisions in [`docs/DECISIONS.md`](docs/DECISIONS.md), balance sheet in [`docs/BALANCE.md`](docs/BALANCE.md).
