# Баланс каталога (генерируется: `node tools/balance.cjs`)

Эталон §14.11: железный меч 9.6 DPS, маг не выше +20% (11.5 DPS). Урон — по одной цели за каст,
без бонусов школы; ур. 15 — с вехами и +3%/уровень к основной характеристике. Огонь горения — 1/с.

| Заклинание | Школа | Редкость | Режим | Каст, с | КД, с | Мана | Урон ур.1 | Урон ур.15 | Вне коридора §14.11 |
|---|---|---|---|---:|---:|---:|---:|---:|---|
| gust_dome | air | epic | charged | 1.1 | 15 | 26 | — | — |  |
| wind_blade | air | common | instant | 0 | 2 | 9 | 5 | 13.3 |  |
| wind_step | air | uncommon | instant | 0 | 6 | 12 | — | — | роль: cast |
| constellation_shift | astral | mythic | charged | 2.5 | 45 | 46 | 15 | 29.9 |  |
| guardian_orbit | astral | epic | instant | 0 | 18 | 26 | — | — | роль: cast |
| star_spike | astral | rare | charged | 0.9 | 7 | 18 | 9 | 12 |  |
| shadow_dart | dark | rare | instant | 0 | 6 | 16 | 9 | 35.9 | роль: cast |
| shadow_step | dark | mythic | instant | 0 | 30 | 40 | — | — | роль: cast |
| withering_curse | dark | epic | charged | 1.2 | 15 | 28 | 12 | 16 |  |
| fault_line | earth | epic | charged | 1.2 | 14 | 28 | 11 | 14.6 |  |
| granite_oath | earth | uncommon | instant | 0 | 8 | 14 | — | — | роль: cast |
| stone_spear | earth | common | charged | 0.7 | 2.5 | 10 | 6 | 19.2 |  |
| ash_spiral | fire | epic | charged | 1.3 | 15 | 30 | 12.8 | 21.3 |  |
| fireball | fire | common | charged | 0.7 | 2 | 10 | 9 | 19 |  |
| flame_dash | fire | rare | instant | 0 | 8 | 18 | — | — | роль: cast |
| searing_bolt | fire | common | instant | 0 | 2 | 10 | 9 | 20.5 |  |
| frost_pattern | ice | epic | charged | 1.2 | 15 | 28 | — | — |  |
| ice_bastion | ice | rare | charged | 0.9 | 12 | 20 | — | — |  |
| ice_shard | ice | common | instant | 0 | 1.5 | 8 | 4 | 16 | роль: dmg |
| dawn_ray | light | rare | channel | 3 | 8 | 18 | 18 | 23.9 | роль: cast,dmg |
| heavens_judgment | light | mythic | charged | 3 | 45 | 48 | 16 | 42.6 |  |
| purifying_aura | light | epic | toggle | 0 | 10 | 3 | — | — | роль: cast,mana |
| current_shell | lightning | epic | toggle | 0 | 5 | 3 | — | — | роль: cast,mana,cd |
| discharge | lightning | common | instant | 0 | 1.8 | 9 | 5 | 6.7 |  |
| storm_anchor | lightning | rare | instant | 0 | 6 | 16 | 8 | 10.6 | роль: cast |
| life_bloom | nature | rare | charged | 1 | 10 | 20 | — | — |  |
| root_shackles | nature | common | charged | 0.7 | 6 | 10 | — | 4 | роль: cd |
| thorn_seed | nature | epic | charged | 1.2 | 14 | 26 | 13.2 | 17.6 |  |
| healing_dew | water | uncommon | charged | 0.8 | 5 | 14 | — | — |  |
| tidal_stream | water | common | channel | 5 | 2 | 10 | 16 | 24.5 | роль: cast,dmg |
| water_prison | water | rare | charged | 0.9 | 10 | 20 | — | 5 | роль: dmg |

## Бой 60 с по одной цели (мастерство 15, доминанта, резонанс 10, мана 100 + 2/с в бою)

| Школа | DPS | % от меча | Касты |
|---|---:|---:|---|
| fire | 9.3 | 96% | searing_bolt×17, fireball×6, ash_spiral×1 |
| astral | 3.0 | 31% | constellation_shift×2, star_spike×8 |
| lightning | 3.6 | 38% | storm_anchor×4, discharge×22 |
| light | 4.4 | 46% | heavens_judgment×2, dawn_ray×6 |
| earth | 7.1 | 74% | stone_spear×17, fault_line×3 |
| ice | 9.8 | 102% | ice_shard×32 |
| air | 6.9 | 72% | wind_blade×27 |
| water | 4.7 | 49% | tidal_stream×9, water_prison×5 |
| nature | 2.0 | 21% | thorn_seed×4, root_shackles×9 |
| dark | 7.8 | 81% | shadow_dart×10, withering_curse×3 |

Замечаний нет.
