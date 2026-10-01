# Glyph Charging Meter

Android-приложение, показывающее уровень заряда на Glyph-интерфейсе телефонов Nothing (в первую очередь Phone (3a) / (3a) Pro). Kotlin, Jetpack Compose + Material 3, Hilt, DataStore, Glyph SDK 2.0.

## Запуск

```bash
./gradlew assembleDebug                 # сборка debug-APK
./gradlew test                          # все юнит-тесты (JVM и Robolectric)
./gradlew test assembleDebug --rerun-tasks   # полный прогон без кэша задач
```

## Документация

- Карта проекта (стек, команды, структура, индекс модулей): `.claude/rules/ARCHITECTURE.md`
- Операционная память (modules / features / bugs / decisions / runbooks): `.claude/docs/` (пока не создана)

@.claude/rules/ARCHITECTURE.md

## Соглашения

- `:core:model`, `:core:layout`, `:core:animation` — чистые JVM-библиотеки. Им запрещено зависеть от Android и от SDK Nothing; это проверяет `ModuleBoundariesTest`.
- О пакете `com.nothing.ketchum` знает только `:core:hardware`.
- Glyph SDK подключён к `:core:hardware` как `compileOnly`, а в рантайм его поставляет `:app` через `implementation(libs.glyph.matrix.sdk)`. Сборка падает на этапе конфигурации, если эта зависимость из `:app` пропала. Не удалять её как «дублирующую»: без неё приложение упадёт на живом телефоне с `NoClassDefFoundError`.
- Оркестратор показа работает на ОДНОМ общем однопоточном диспетчере `@GlyphDispatcher`, singleton из Hilt-графа (`AppModule`). Внутри две сотрудничающие корутины; при многопоточном исполнении глиф может остаться гореть после отключения зарядки. Не «оптимизировать» и не создавать собственный `limitedParallelism(1)`.
- В слоях анимации и в оркестраторе время подаётся параметром или идёт через `delay`; обращаться к системным часам там запрещено — на этом держится мгновенное тестирование под `runTest`.
- Тесты экранов и всё, что касается Android, — локальные юнит-тесты под Robolectric с `sdk=34` в `app/src/test/resources/robolectric.properties`: проект на JDK 17, а более новая песочница Robolectric требует JDK 21.
- Запуск сервиса решает одна функция-политика `shouldStartService` (плюс `shouldStartOnBoot` для загрузки). Сервис стартует при первом показе главного экрана, от переключателя и при загрузке телефона.
- Лицензия не выбрана — выбор за автором; файл `LICENSE` не добавлять без его решения.
