# Карта проекта: Glyph Charging Meter

Android-приложение: показ уровня заряда на Glyph-интерфейсе Nothing Phone (3a) / (3a) Pro с субсегментной плавностью. Спецификация: `docs/superpowers/specs/2026-09-18-glyph-charging-meter-design.md`.

## Стек

- Kotlin 2.4, JDK 17 (toolchain), Android Gradle Plugin 9.4, Gradle Kotlin DSL
- Jetpack Compose (BOM 2026.09), Material 3, Navigation Compose
- Hilt (DI, через KSP), DataStore Preferences (настройки)
- Glyph Matrix SDK 2.0 (локальный `.aar`, `core/hardware/libs/`), Shizuku (запасной путь доступа)
- Тесты: JUnit 4, kotlinx-coroutines-test, Turbine, Robolectric 4.17 (`sdk=34`), Hilt testing, Compose UI test
- minSdk 33, targetSdk 36; версии — в `gradle/libs.versions.toml`

## Команды

```bash
./gradlew assembleDebug                      # собрать debug-APK (app/build/outputs/apk/debug)
./gradlew test                               # все юнит-тесты всех модулей
./gradlew :app:testDebugUnitTest --tests '*ModuleBoundariesTest*'   # проверка границ модулей
./gradlew test assembleDebug --rerun-tasks   # полный прогон без кэша задач
```

## Структура верхнего уровня

```
.
├── app/                 приложение: DI, сервис, доступ, оркестрация, UI (Compose)
├── core/
│   ├── model/           чистая JVM: Light, DeviceLayout, GlyphFrameData
│   ├── layout/          чистая JVM: раскладки устройств, MeterRenderer
│   ├── animation/       чистая JVM: пресеты, переходы, кадры от времени
│   └── hardware/        Android-библиотека: единственный слой с Glyph SDK
├── gradle/              libs.versions.toml (каталог версий)
├── docs/superpowers/    спецификация и планы
├── AGENTS.md            точка входа для агентов
└── .claude/rules/       эта карта
```

## Индекс модулей

Подробные документы `.claude/docs/modules/<slug>.md` пока не созданы; ниже только однострочные описания.

- `:core:model` — модели данных: `Light`, `DeviceLayout`, `GlyphZone`, `GlyphFrameData`. Чистая JVM.
- `:core:layout` — таблица раскладок устройств (`DeviceLayouts`, сейчас Phone (3a), `hardwareVerified = false` до проверки на устройстве) и `MeterRenderer` (плавный кадр уровня и единственный расчёт процента для ступенчатого запасного режима, `steppedPercent`). Чистая JVM.
- `:core:animation` — пресеты подключения, кривые, `MeterTransition`, `PresetFrameSource`; время приходит параметром. Чистая JVM.
- `:core:hardware` — `GlyphDisplay`, `NothingGlyphDisplay`, `FakeGlyphDisplay`, определение устройства. Единственный, кто знает `com.nothing.ketchum`; SDK как `compileOnly`.
- `:app` — пакет `com.swrneko.glyphmeter`:
  - `access` — состояния доступа к Glyph, debug-режим, политики запуска, writer'ы (secure settings, Shizuku);
  - `charging` — источник состояния зарядки (система и фейк);
  - `orientation` — источник «телефон лежит экраном вверх» (датчик и фейк), активен только во время зарядки;
  - `orchestration` — `GlyphOrchestrator`, сценарии показа;
  - `service` — foreground-сервис (`specialUse`), `BootReceiver`, контроллер сервиса;
  - `settings` — `GlyphSettings` и репозиторий на DataStore;
  - `di` — Hilt-модули, `@GlyphDispatcher`;
  - `ui` — навигация, главный экран, настройки, онбординг, превью глифа, тема.

## Зависимости модулей

`:app` -> `:core:hardware`, `:core:animation`, `:core:layout`, `:core:model`. `:core:hardware` -> `:core:layout` -> `:core:model`; `:core:animation` -> `:core:layout`. Чистые модули не зависят от Android (тест `ModuleBoundariesTest`).

## Данные и ключи

Базы данных с таблицами нет, только хранилище настроек «ключ-значение» (DataStore Preferences). Политика UUID для первичных и внешних ключей к проекту неприменима.

### Базовый образ

В проекте нет Docker и нет базового образа. Сборка и тесты выполняются напрямую на хосте через Gradle wrapper (нужен JDK 17 и Android SDK).
