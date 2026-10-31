# Прогресс разработки

Фазы — §20.2 концепта.

## S0. Настройка — ✅ (31.10.2026)
- [x] Проект NeoForge 26.3 (ModDevGradle), JDK 25, MIT
- [x] Главный класс, клиентский entrypoint, SYNCED-конфиг, DeferredRegister'ы
- [x] `core`: `Rarity`, `MasteryMath`, `ManaMath` + JUnit (5 тестов)
- [x] Задача `deployJar` → Prism-инстанс
- [x] Dedicated server стартует с модом (runServer → Done)
- [ ] Проверка клиента в Prism (пользователь)
- [x] git + CI: https://github.com/sqw1sh/orbis-arcana (GitHub Actions: build + тесты + jar-артефакт)
- [ ] GeckoLib / Player Animation Library — подключить, когда понадобятся (S3/S4), проверив сборки под 26.3

## S1. Данные и мана — 🔶 в работе
Часть 1 (31.10.2026):
- [x] Атрибуты `orbisarcana:max_mana` (100) и `mana_regen` (множитель 1.0), добавлены игроку
- [x] Attachment `resources` (мана + КД), сохраняется, копируется при смерти, синхронизируется владельцу ≤ 4 раз/с
- [x] ManaService: реген 2/с в бою, 5/с через 5 с вне боя; бой = нанёс/получил урон; смерть → 50% маны (фикс: штраф в PlayerRespawnEvent)
- [x] HUD: полоса маны справа от хотбара (цифры над полосой)
- [x] `/orbis mana get|set|fill [игроки]` (уровень прав 2)
- [x] `core/ManaRegen` + JUnit
- [x] Проверка в клиенте (пользователь) — найден и исправлен баг с маной после смерти

Часть 2 (31.10.2026):
- [x] Attachments `knowledge` (мастерство, отключённые вехи, `schema`, архив неизвестных spellId), `attunement` (доминанта, резонанс + пик, таланты, Дисгармония, КД смены), `loadout` (3×8 + выбор)
- [x] Все attachments: сохранение, `copyOnDeath`, синхронизация владельцу (пока полный стейт, не дельты)
- [x] `/orbis spell learn|forget|mastery|list`, `/orbis school set|resonance|reset|info`
- [x] JUnit на кодеки всех attachments (MDG `unitTest` включён — в тестах доступны классы MC)
- [x] spellId валидируется по реестру заклинаний (S2)
- [ ] GameTest-инфраструктура → перенесена в S2 (тесты каста)
- [ ] Проверка в клиенте (пользователь)

## S2. Каст-ядро — 🔶 в работе
Часть 1 (31.10.2026):
- [x] Датапак-реестр `orbisarcana:spell` (`SpellDefinition`, §15.4), синхронизируется клиенту; валидация вех/`primary_stat`/редкости с понятными ошибками
- [x] 4 тестовых заклинания (JSON): fireball (DIR), life_bloom (GROUND), healing_dew (TARGET), wind_step (SELF)
- [x] Архив/восстановление неизвестных spellId при входе; подсказки и проверка заклинаний в `/orbis spell`
- [x] `core`: `CastRules` (проверки + GCD 0.25 с), `ActiveCast` (прогресс, мощность, потеря маны), enum-ы Targeting/CastMode/XpCategory/CastDenial
- [x] JUnit: 17 тестов (в т.ч. парсинг всех JSON-заклинаний)

Часть 2 (31.10.2026):
- [x] Реестр `orbisarcana:spell_behavior` + поведения projectile, zone, heal, dash (простые вехи работают через params)
- [x] Типы урона по школам (`<school>_magic`, без is_fire), теги `orbisarcana:magic`, `c:is_magic`
- [x] `SpellProjectile` (без гравитации, частицы вместо модели до S3, не сохраняется)
- [x] CastService: проверки, снимок параметров с мастерством, резерв маны и возврат при прерывании, КД, GCD, замедление ×0.65 и сброс спринта, прерывание уроном ≥15% (шанс 50%) / смертью / атакой / сменой измерения / выходом, потеря цели, лимит 10 запросов/с
- [x] Сеть: CastRequest, CastStop (C2S); CastStarted, CastEnded, CastDenied, CooldownSet (S2C)
- [x] Клиент: V — каст (удержание для CHARGED), Alt + колесо — выбор из изученных; HUD: выбранное заклинание + КД, полоса каста, причина отказа
- [x] runServer и runClient стартуют без ошибок
- [ ] Проверка каста в игре (пользователь)

Часть 3 (следующая):
- GameTest-инфраструктура + тест каста каждого из 4 заклинаний
- Отправка КД клиенту при входе; CHANNEL/TOGGLE (сейчас отказ DISABLED)
- Вехи, которым нужна логика (огненная земля, 2 заряда шага ветра) — S5
