# Orbis Arcana — пример аддона

Мод `orbisexample` показывает, как расширить Orbis Arcana без форка (§16 концепта):

| Что | Где |
|---|---|
| Школа «Кровь» (цвет, Отзвук) | `ExampleAddon` — `DeferredRegister` на `OrbisArcanaAPI.SCHOOL_REGISTRY` |
| Отзвук «Жажда крови»: попадания лечат мага | `BloodlustEcho implements SchoolEcho` |
| Своё поведение заклинания `orbisexample:life_drain` | `LifeDrainBehavior` — регистрация в `SpellBehaviors.KEY`, опыт и Отзвук через `OrbisArcanaAPI.reportHit/reportHeal` |
| Заклинание «Кровавая стрела» — только данные | `data/orbisexample/orbisarcana/spell/blood_bolt.json` |
| Круг заклинания | `assets/orbisexample/orbisarcana/circle/blood_bolt.json` |
| Названия и описания | `assets/orbisexample/lang/*.json`: `school.<ns>.<школа>`, `talent.<ns>.<школа>.<узел>`, `spell.<ns>.<заклинание>[.desc/.m5/.m10/.m15]` |
| GameTest-ы | `ExampleGameTests` |

Заклинание из уже существующих поведений не требует кода вообще — достаточно JSON в датапаке.

## Команды

```bash
./gradlew :example-addon:runGameTestServer
```

```bash
./gradlew :example-addon:runClient
```

Аддон компилируется против полного мода (`implementation project(':')`). Если нужны только события, сервисы и школы — хватает артефакта `orbisarcana-<версия>-api.jar`.
