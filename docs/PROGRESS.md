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
- [x] ManaService: реген 2/с в бою, 5/с через 5 с вне боя; бой = нанёс/получил урон; смерть → 50% маны
- [x] HUD: полоса маны справа от хотбара (цифры над полосой)
- [x] `/orbis mana get|set|fill [игроки]` (уровень прав 2)
- [x] `core/ManaRegen` + JUnit
- [ ] Проверка в клиенте (пользователь): полоса, реген, смерть, перезаход

Часть 2 (следующая):
- Attachments `knowledge`, `attunement`, `loadout` (+ `schema`, архив неизвестных spellId)
- `/orbis spell learn|forget|mastery`, `/orbis school set|resonance|reset`
- GameTest: данные переживают смерть
