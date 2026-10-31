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
- `./gradlew runClient` / `runServer` / `runGameTestServer` — dev-запуски (папка `run/`, eula уже принята).

## Особенности API 26.3 (найдено на практике)
- `ResourceLocation` → `net.minecraft.resources.Identifier` (`Identifier.fromNamespaceAndPath`).
- `ModConfig.Type`: LOCAL, CLIENT, SYNCED, STARTUP (нет SERVER/COMMON). Серверный конфиг = `SYNCED`.
- `DeferredRegister.registerSimpleBlock/registerSimpleItem(name, props -> ...)`.

## Архитектура (§15)
`api/ core/ registry/ spell/ school/ progression/ mana/ data/ network/ item/ block/ world/ client/ compat/ gametest/`
- `core/` — чистая Java без Minecraft, покрыта JUnit (`src/test`).
- Клиентский код только в `client/` и `OrbisArcanaClient`.
- Сервер — авторитет; клиент шлёт только намерения.
