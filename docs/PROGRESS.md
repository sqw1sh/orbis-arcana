# Прогресс разработки

Фазы — §20.2 концепта.

## S0. Настройка — ✅ (31.10.2026)
- [x] Проект NeoForge 26.3 (ModDevGradle), JDK 25, MIT
- [x] Главный класс, клиентский entrypoint, SYNCED-конфиг, DeferredRegister'ы
- [x] `core`: `Rarity`, `MasteryMath`, `ManaMath` + JUnit (5 тестов)
- [x] Задача `deployJar` → Prism-инстанс
- [x] Dedicated server стартует с модом (runServer → Done)
- [ ] Проверка клиента в Prism (пользователь)
- [ ] git init + CI (шаблон `.github/workflows/build.yml` из MDK лежит, не настроен)
- [ ] GeckoLib / Player Animation Library — подключить, когда понадобятся (S3/S4), проверив сборки под 26.3

## S1. Данные и мана — ⏳ следующая
- Attachments `knowledge`, `attunement`, `loadout`, `resources` (§15.3), копирование при смерти
- ManaService (100 маны, реген 2/5 в с), атрибуты `max_mana`, `mana_regen`
- Синхронизация маны клиенту, HUD-полоса
- Команды `/orbis mana`, `/orbis spell learn|forget|mastery`
