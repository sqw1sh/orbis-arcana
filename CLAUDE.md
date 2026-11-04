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
- `./gradlew :runClientShot -PwithCompat -Pshots=jei,jade` — клиент с JEI, Jade и Curios (`-PwithCompat`).
- `./gradlew runClient -PwithCurios` — dev-запуск с Curios (по умолчанию без него: Curios ломает мок-игроков GameTest).
- `node tools/new-spell.cjs <id> --school fire --shape bolt|beam|cone|burst|nova|target|self|zone|aura|wall|meteor --effects damage,ignite [--rarity common|uncommon|rare|epic|legendary|mythic|ancient|divine] [--mode charged|instant|channel] --ru ... --en ... --ru-desc ... --en-desc ...` — заготовка составного заклинания (JSON, круг, lang); формат `compose` — `spell/SpellCompose`.
- `node tools/balance.cjs` — баланс-таблица `docs/BALANCE.md` (коридоры §14.11, DPS школ против меча); запускать после правки чисел заклинаний.
- `node tools/lang-check.cjs` — аудит локализации RU/EN (запускать после добавления текстов).
- `node tools/textures.cjs` — пересобрать PNG-текстуры предметов и блоков.
- `node tools/art.cjs [--check]` — Blockbench → ресурсы: текстуры и item-JSON из `art/models/*.bbmodel`, проверка, что экспорт GeckoLib не устарел (конвейер — `docs/ART.md`).
- `node tools/sfx.cjs [группа|группа/имя]` — синтез звуков по рецептам `art/sounds/<группа>/*.json` → WAV → OGG (ffmpeg из PATH, env `FFMPEG` или winget-ссылки). Группы: charge, release, impact, hit (по школам), spell (свои звуки заклинаний, `release_sound`/`impact_sound` в `compose.visuals`), cast, toggle, ui, mastery; звук попадания играет сам хук урона `spell/Sfx`, тяжёлый удар — `Sfx.impact(level, pos, school, scale)`; новые события — в `sounds.json` и `ModSounds`.
- `./gradlew :runClientShot -Pshots=geckolib,pose` — проверка GeckoLib-модели в руке и позы PAL.
- `node tools/caster-poses.cjs` — позы выпуска отдельных заклинаний и тогглов (в `caster.json` и риг `caster.bbmodel`); какому заклинанию какая — `CasterAnimations.POSES`.
- `node tools/gui.cjs` — пересобрать спрайты UI-кита (`textures/gui/sprites/ui`, стиль подсказок `arcane`).
- `node tools/circles.cjs` — пересобрать атлас магических кругов (`textures/effect/circle_atlas.png`, ~25 с); раскладка — `client/circle/CircleAtlas`.
- `./gradlew :runClientShot -Pshots=gallery_setup,gallery` — галерея кругов всех редкостей (ночь, тёмная площадка).
- `node tools/vfx.cjs` — пересобрать лист спрайтов эффектов (`textures/vfx/sheet.png`).
- `./gradlew :runClientShot -Pshots=gallery_setup,vfx` — эффекты (VFX) на тёмной площадке.
- `node tools/vfx-energy.cjs` — бесшовные энергетические текстуры объёмных эффектов (`textures/vfx/energy`).
- `node tools/vfx-effects.cjs` — объёмные эффекты (раздел MESHES: sphere/cylinder/cone/ring/spiral/trail, `client/vfx/VfxMesh`) и стоковые эффекты (`art/vfx/particles`, затем `node tools/art.cjs`); на сервере — `Fx.<ВИД>.play(level, x, y, z, count, dx, dy, dz[, tint])` вместо `sendParticles`. Проверка: `-Pshots=gallery_setup,stock`.
- 3D-модели: спецификации `art/specs/<id>.json` (генераторы `node tools/specs/<foci|accessories|misc|world>.cjs`, общие помощники — `tools/specs/lib.cjs`) → в Blockbench (MCP `risky_eval`) `eval(require('fs').readFileSync('<repo>/tools/blockbench/build-model.js','utf8'))('<repo>','<id>')` → `.bbmodel` + GeckoLib-экспорт → `node tools/art.cjs`. Положения 3D-предметов в руке/GUI — `node tools/foci-display.cjs`.
- `node tools/structures.cjs` — пересобрать NBT-шаблоны структур (обсерватория, скрипторий).
- `./gradlew :runGameTestServer` — GameTest-ы мода; без `:` запускаются и тесты `example-addon` (мод + аддон, 99).
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
- Классы Curios/JEI/Jade трогать ТОЛЬКО из `compat/<мод>` (вызов статического метода compat-класса из общего кода безопасен, ссылка на тип мода — нет) (даже в dev-коде): любой класс-подписчик с такой ссылкой роняет клиент без Curios. В lang-строках с аргументами `%` писать как `%%`.
- Время суток: команда `time set` (у ServerLevel нет setDayTime).
- В Bash нет python — для JSON-скриптов использовать node.
- Теги типов сущностей: `entity.getType().builtInRegistryHolder().is(tag)`. `PushReaction`: PUSH_PULL/PUSH/POPPED/IMMOVEABLE/IGNORE_ENTITY.
- I-фреймы мобов: `LivingEntity.damageCooldownTime` (публичное поле), а не `Entity#setInvulnerableTime`.
- GeckoLib 5.5.7 (пакет `com.geckolib`, maven Modrinth): ассеты `assets/<ns>/geckolib/models|animations/<тип>/<id>.(geo|animation).json`, текстура `textures/<тип>/<id>.png`; 3D-предмет = `items/<id>.json` с `{"type":"minecraft:special","model":{"type":"geckolib:geckolib"}}`; `createGeoRenderer` вызывается лениво только на клиенте. Исходники: cloudsmith `com/geckolib/geckolib-neoforge-26.3/5.5.7/*-sources.jar`.
- Player Animation Library 1.2.8 (mod id `player_animation_library`): анимации в `assets/<ns>/player_animations/*.json`, ключ = `<ns>:<имя в файле>` (Blockbench даёт префикс `animation.`); слой — `PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory`, запуск — `PlayerAnimationController#triggerAnimation`. Экспорт Blockbench инвертирует X-поворот (редактор −150 → файл 150); в редакторе рига X+ поднимает руку вперёд, вбок — −Z левая / +Z правая. Плавные переходы — `replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(t, EasingType...), id)`; руки от первого лица — защищённое поле `firstPersonMode` контроллера (`FirstPersonMode.THIRD_PERSON_MODEL`). Проверка поз: `./gradlew :runClientShot -Pshots=gallery_setup,pose -Ppose=a,b`.
- GUI-спрайты: `assets/<ns>/textures/gui/sprites/<путь>.png` + `.png.mcmeta` `{"gui":{"scaling":{"type":"nine_slice","width","height","border","stretch_inner":true}}}`; рисовать `g.blitSprite(RenderPipelines.GUI_TEXTURED, id, x, y, w, h[, argb])`, частично — вариант `(id, texW, texH, u, v, x, y, w, h)`. Стиль подсказок предмета — компонент `DataComponents.TOOLTIP_STYLE` (спрайты `tooltip/<id>_background|_frame`, 100×100, бордер 9/10). Слои HUD при F1 скрываются сами (`options.hideGui` нет).
- Свой мировой пайплайн с текстурой: `RenderPipeline.builder(GLOBALS_SNIPPET)` + `BindGroupLayouts.PROJECTION/DYNAMIC_TRANSFORMS/SAMPLER0`, шейдер `core/position_tex_color`, формат `POSITION_TEX_COLOR`; текстура — `RenderSetup.builder(p).withTexture("Sampler0", id)`. Под Iris: формат BLOCK + программа TEXTURED теряет цвет вершин, BEACON_BEAM — сильный блум. 3D-сцена в GUI — свой `PictureInPictureRenderer` + `RegisterPictureInPictureRenderersEvent`, `g.submitPictureInPictureRenderState`.
- VFX (`client/vfx`): свой движок частиц, эффекты — Snowstorm JSON из Blockbench в `assets/<ns>/orbisarcana/vfx/`; сервер запускает их через `spell/Vfx.play/attach` (пакет `VfxPlay`). Камера в `SubmitCustomGeometryEvent`: `event.getLevelRenderState().cameraRenderState` (`pos`, `orientation`). Молanг-цвет 8 знаков в Bedrock — AARRGGBB. Фирменные эффекты заклинаний — тоже в `tools/vfx-effects.cjs`; лучи — спрайт `beam`, эмиттер в середине, forward = отрезок, scale = длина, ширина `w / variable.emitter_scale`; направленные частицы (`lookat_direction`) требуют скорость ≥ 0.01.
- Иконки предметов 26.3 рисуются в текстуру 16×16 и обрезаются: 3D-модель в GUI должна помещаться (длинные — по диагонали, голова в угол). PAL THIRD_PERSON_MODEL от первого лица рисует предмет с transforms третьего лица — для длинных (посох) лучше FirstPersonMode.VANILLA. В Blockbench JS: `Animator.buildFile(false, Animation.all.map(a => a.name))`, геометрия — `Codecs.bedrock.compile()`.
- GeckoLib-блок: `BlockEntity implements GeoBlockEntity`, рендер `new GeoBlockRenderer<>(ctx, type)` в `EntityRenderersEvent.RegisterRenderers#registerBlockEntityRenderer`, блок `RenderShape.INVISIBLE` + `noOcclusion()`, block-модель только с `particle`; предмет блока — `ModelBlockItem` (`DefaultedBlockGeoModel`), `useBlockDescriptionPrefix()`. Существа: `GeoEntity` + `new GeoEntityRenderer<>(ctx, type)`, перед моделью — -Z.
- Руда поверх ванильного камня: модель из двух элементов 16³ (`#base` = `minecraft:block/stone`, `#overlay` — свой PNG с прозрачностью), прозрачность работает без настройки слоя; `light_emission` у элемента — свечение кристаллов.
- `new ClipContext(..., CollisionContext.empty())`, если сущности может не быть (ClipContext с null-entity падает).

## Архитектура (§15)
`api/ core/ registry/ spell/ school/ progression/ mana/ data/ network/ item/ block/ world/ client/ compat/ gametest/`
- `core/` — чистая Java без Minecraft, покрыта JUnit (`src/test`).
- Клиентский код только в `client/` и `OrbisArcanaClient`.
- Сервер — авторитет; клиент шлёт только намерения.
