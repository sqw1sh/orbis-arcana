# Orbis Arcana

Magic mod for **Minecraft Java 26.3 (NeoForge 26.3.0.100+)**. Spells are found as scrolls and learned for good; casting draws a procedural magic circle; every spell grows with practice and changes at mastery 5, 10 and 15. License: MIT.

## Features

- **30 spells in 10 schools** — Fire, Earth, Water, Air, Ice, Lightning, Nature and the higher schools Light, Dark, Astral. Instant, charged, channelled and toggled casts; projectiles, beams, cones, waves, zones, walls, traps, dashes, teleports, auras, curses, summons.
- **Mastery** — useful casts (damage, healing, control) level a spell from 1 to 15; milestones at 5/10/15 add new effects.
- **Schools and resonance** — bind to a school at the Resonance Altar; resonance unlocks talents and the school's **Echo**, a passive power of its own (Heat, Flow, Bulwark, Impulse, Frost, Conductivity, Sprouting, Radiance, Dusk, Constellation).
- **Magic circles** — layered procedural circles for every spell, shader-pack compatible (Iris), with a particle fallback and plenty of client options.
- **Foci** — wands, staves and tomes in three tiers; any item can become a focus through the `orbisarcana:focus` data component.
- **World** — Aether Crystal ore, Aether Ink, the Ruined Observatory (glyph puzzle and guardians) and the Flooded Scriptorium; scrolls in vanilla chests, from librarians, wandering traders, witches and evokers.
- **Accessibility** — subtitles for all casting sounds, auto-cast when charged, press instead of hold.
- **Languages** — English, Russian.

## Optional mods

| Mod | What you get |
|---|---|
| [Curios](https://modrinth.com/mod/curios) | 12 accessories: school rings, amulets, Ring of Echo, Scribe's Ring |
| [JEI](https://modrinth.com/mod/jei) | A separate entry for every spell scroll, information pages |
| [Jade](https://modrinth.com/mod/jade) | Your school at the altar, glyph ring hints |
| [Iris](https://modrinth.com/mod/iris) | Circles render under shader packs |

## For addon authors

The API lives in `com.orbisarcana.api` (also published as the `orbisarcana-<version>-api.jar` artifact): services through `OrbisArcanaAPI.get()`, new schools with their own Echo (`School`, `SchoolEcho`), events (`SpellCastEvent`, `SpellLearnEvent`, `MasteryLevelUpEvent`, `AttunementChangeEvent`), the focus component. A spell made of existing behaviors is pure data: `data/<namespace>/orbisarcana/spell/<id>.json`. See [`example-addon/`](example-addon/) for a complete addon with a school, an Echo, a custom behavior and a spell.

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

Design documents (in Russian): [`docs/concept.md`](docs/concept.md), progress in [`docs/PROGRESS.md`](docs/PROGRESS.md), decisions in [`docs/DECISIONS.md`](docs/DECISIONS.md), balance sheet in [`docs/BALANCE.md`](docs/BALANCE.md).
