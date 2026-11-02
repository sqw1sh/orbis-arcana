# Orbis Arcana — заметки для Claude

Магический мод для **Minecraft 26.3 / NeoForge 26.3.0.100 / Java 25**, mod id `orbisarcana`, пакет `com.orbisarcana`, MIT.
Полный концепт: `docs/concept.md` (читать только нужный раздел через Grep по `## N.` — файл большой).
Прогресс по фазам: `docs/PROGRESS.md`. Принятые отклонения/решения: `docs/DECISIONS.md`.

## Правила работы (от пользователя)
- Работать поэтапно: 1–несколько пунктов за раз, затем краткий отчёт на русском + предложение следующих шагов.
- Экономить токены: не перечитывать концепт целиком, держать актуальными PROGRESS/DECISIONS.

## Окружение
- JDK: `C:/Users/sqw1sh/.jdks/jdk-25.0.5+8` (прописан в `~/.gradle/gradle.properties` как `org.gradle.java.home`, чтобы не ломать CI).
  Для запуска `./gradlew` в Bash нужен `export JAVA_HOME="/c/Users/sqw1sh/.jdks/jdk-25.0.5+8"`.
- Шаблон: NeoForgeMDKs/MDK-26.3-ModDevGradle, MDG 2.0.148, Gradle 9.2.1.
- Тестовый инстанс Prism: `C:\Users\sqw1sh\AppData\Roaming\PrismLauncher\instances\Test Mod 26.3` (моды в `minecraft/mods`).
- Blockbench 5.2.2 + MCP-плагин jasonjgardner (`http://localhost:3000/bb-mcp`, конфиг в `.mcp.json`).
  Скиллы Blockbench: `.claude/skills/blockbench-*` (начинать с `blockbench-use`). Blockbench должен быть открыт.

## Команды
- `./gradlew build` — компиляция + JUnit (ядро `core`).
- `./gradlew deployJar` — собрать и положить jar в Prism-инстанс (старые `orbisarcana-*.jar` удаляются).
- `./gradlew runClient` / `runServer` — dev-запуски (папка `run/`, eula уже принята).
- `./gradlew runClientShot` — визуальная проверка: грузит `run/saves/devtest`, ставит стенд кругов, делает скриншоты `run/screenshots/devshot_<mode>.png` и выходит (`client/DevShot`). Iris/Sodium/шейдерпак лежат в `run/mods`, `run/shaderpacks` (локально). Скриншоты смотреть через Read.
- `node tools/structures.cjs` — пересобрать NBT-шаблоны структур (обсерватория, скрипторий).
- `./gradlew runGameTestServer` — все GameTest-ы (`gametest/OrbisGameTests`), код выхода ≠ 0 при падении. Итог в логе: `All N required tests passed`.

## Особенности API 26.3 (найдено на практике)
- `ResourceLocation` → `net.minecraft.resources.Identifier` (`Identifier.fromNamespaceAndPath`).
- `ModConfig.Type`: LOCAL, CLIENT, SYNCED, STARTUP (нет SERVER/COMMON). Серверный конфиг = `SYNCED`.
- `DeferredRegister.registerSimpleBlock/registerSimpleItem(name, props -> ...)`.
- `EntityType.PLAYER` → `EntityTypes.PLAYER`; `GuiGraphics` → `GuiGraphicsExtractor` (`fill`, `fillGradient`, `text`, `blit`).
- `LivingDamageEvent.Post#getHealthDamage()`; права команд: `.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))`.
- Attachments умеют синхронизацию сами: `.sync(predicate, streamCodec)` + `player.syncData(type)`.
- Исходники для поиска API: распаковать `build/moddev/artifacts/minecraft-patched-*-sources.jar` и neoforge-sources из `~/.gradle/caches` во временную папку.
- JUnit с классами MC: в build.gradle включён `neoForge.unitTest` (тесты стартуют FML, ~10 с).
- `IdentifierArgument.id()` вместо ResourceLocationArgument; `SharedSuggestionProvider.suggestResource`.
- НЕ менять attachments в `PlayerEvent.Clone`: NeoForge копирует их своим Clone-листенером и может перезаписать. Пост-смертные правки — в `PlayerEvent.PlayerRespawnEvent`.
- Датапак-реестры: `NewDatapackRegistryEvent#worldRegistry(key, codec, networkCodec)`. Путь JSON: `data/<ns>/orbisarcana/spell/`.
- Рабочая папка JUnit под MDG — не корень проекта; путь к ресурсам — системное свойство `orbisarcana.resources`.
- Ввод на SDL: `InputConstants.Type.KEYBOARD` + `InputConstants.KEY_V` (сканкоды), GLFW нет. Экран: `mc.gui.screen()`. Категория клавиш: `new KeyMapping.Category(id)` + `registerCategory`, lang `key.category.<ns>.<path>`.
- Скорость игроку: `entity.syncVelocity = true` (бывший hurtMarked); `needsSync` — только для трекинга.
- Клиентские хендлеры пакетов: `RegisterClientPayloadHandlersEvent`; сервер регистрирует `playToClient(type, codec)` без хендлера. Отправка с клиента: `ClientPacketDistributor.sendToServer`.
- Сущности: `DeferredRegister.createEntities` + `registerEntityType(name, factory, category, builder -> ...)`.
- GameTest 26.3: функции в реестре `Registries.TEST_FUNCTION`, тесты — `RegisterGameTestsEvent#registerTest(id, data -> new FunctionGameTestInstance(fnKey, data), new TestData<>(env, structure, maxTicks, setup, required))`. Мок-игрок: `helper.makeMockServerPlayerInLevel()` (каналов мода нет → слать пакеты через `network/Net`). Мок-игрок в креативе и неуязвим до `connection.markClientLoaded()` — урон в тестах через `damageSources().fellOutOfWorld()`.
- Рендер 26.3: API пайплайнов в `com.mojang.renderpearl.api.pipeline` (внутри jar MC; javap по `build/moddev/artifacts/minecraft-patched-*.jar`). Свой геометрии: состояние в `ExtractLevelRenderStateEvent` → `renderState.setRenderData(ContextKey)`, рисовать в `SubmitCustomGeometryEvent#getSubmitNodeCollector().submitCustomGeometry(pose, renderType, (pose, buf) -> ...)`. Пайплайн регистрировать в `RegisterRenderPipelinesEvent`.
- Клиентские JSON-ресурсы: `SimpleJsonResourceReloadListener` + `AddClientReloadListenersEvent#addListener(id, listener)`.
- Iris: свои пайплайны регистрировать через `IrisApi.assignPipeline(pipeline, IrisProgram.X)` (иначе под шейдерами не рисуются). В dev Iris падает на строгой валидации GPU — `-Dneoforge.disableBlaze3DValidation=true`.
- Dev-клиент: в `run/options.txt` `onboardAccessibility:false`, в `run/config/neoforge-client.toml` `showLoadWarnings = false` — иначе quickPlay висит на экранах.
- Ворлдген 26.3: конфигурации фич в `data/<ns>/worldgen/feature/` (не configured_feature), без обёртки `config`, `state` — строка-id. Биом-модификаторы: `data/<ns>/neoforge/biome_modifier/`.
- Лут-таблицы 26.3: функции — `"modifier": [{"type": ...}]`, условие пула/записи — `"condition": {"type": ...}` (старые `functions`/`conditions` молча игнорируются!). GLM: файлы в `data/<ns>/loot_modifiers/` (без индекса), поле `condition`. Торговля жителей data-driven: `data/<ns>/villager_trade/*.json` + теги `data/minecraft/tags/villager_trade/<профессия>/level_N.json`.
- NBT-шаблоны 26.3 (DataVersion 5023): в палитре ключи `id` и `properties` (не `Name`/`Properties` — иначе все блоки молча становятся воздухом). `ServerLevel#getStructureTemplateManager()`.
- В Bash нет python — для JSON-скриптов использовать node.
- Теги типов сущностей: `entity.getType().builtInRegistryHolder().is(tag)`. `PushReaction`: PUSH_PULL/PUSH/POPPED/IMMOVEABLE/IGNORE_ENTITY.
- I-фреймы мобов: `LivingEntity.damageCooldownTime` (публичное поле), а не `Entity#setInvulnerableTime`.
- `new ClipContext(..., CollisionContext.empty())`, если сущности может не быть (ClipContext с null-entity падает).

## Архитектура (§15)
`api/ core/ registry/ spell/ school/ progression/ mana/ data/ network/ item/ block/ world/ client/ compat/ gametest/`
- `core/` — чистая Java без Minecraft, покрыта JUnit (`src/test`).
- Клиентский код только в `client/` и `OrbisArcanaClient`.
- Сервер — авторитет; клиент шлёт только намерения.
