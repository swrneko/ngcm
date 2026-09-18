# Glyph Charging Meter Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Android-приложение, которое во время зарядки показывает уровень заряда на Glyph-интерфейсе Nothing Phone (3a)/(3a) Pro с субсегментной плавностью и проигрывает анимации при подключении питания.

**Architecture:** Пять Gradle-модулей. Три из них (`:core:model`, `:core:layout`, `:core:animation`) — чистые JVM-библиотеки на Kotlin без единой зависимости от Android, поэтому тестируются обычным JUnit за миллисекунды. Четвёртый (`:core:hardware`) — единственное место, знающее о `com.nothing.ketchum`. Пятый (`:app`) — Compose UI, DataStore, foreground service и подсистема доступа к Glyph.

**Tech Stack:** Kotlin 2.4.20, AGP 9.4.0, Gradle 9.7.1, Jetpack Compose (BOM 2026.09.00), Material 3 1.4.0, Hilt 2.60.1, DataStore, Shizuku 13.1.5, JDK 17.

**Spec:** `docs/superpowers/specs/2026-09-18-glyph-charging-meter-design.md`

## Global Constraints

- `compileSdk = 37`, `targetSdk = 36`, `minSdk = 33`. minSdk 33 — потому что `AndroidManifest.xml` внутри `glyph-matrix-sdk-2.0.aar` объявляет `minSdkVersion 33`; ниже собрать невозможно. targetSdk 36 — Android 16, на котором построен Nothing OS 4.0.
- JDK 17. AGP 9.4.0 требует минимум 17, обновляться не нужно.
- Версия плагина `org.jetbrains.kotlin.plugin.compose` ВСЕГДА равна версии Kotlin (2.4.20).
- Material 3 Expressive API в 1.4.0 требуют `@OptIn(ExperimentalMaterial3ExpressiveApi::class)`. Отдельного артефакта `material3-expressive` не существует.
- Package name: `com.swrneko.glyphmeter`.
- Весь код, идентификаторы, комментарии и строковые ресурсы — на английском. Документация проекта — на русском.
- Диапазон яркости сегмента: `0..4095`. Константа `MAX_LIGHT = 4095`, `MIN_VISIBLE_LIGHT = 800` (из `GlyphManager.DEFAULT_MIN_LIGHT`).
- Модули `:core:model`, `:core:layout`, `:core:animation` НЕ ИМЕЮТ права зависеть от Android SDK и от `com.nothing.ketchum`. Это проверяется явным тестом в Task 15.
- Каждая задача заканчивается коммитом. Формат сообщения свободный, на русском. НИКОГДА не добавлять `Co-Authored-By: Claude` или любое упоминание AI.

## Проверенные факты о Glyph SDK

Получены анализом байткода `sdk/glyph-matrix-sdk-2.0.aar` из
https://github.com/Nothing-Developer-Programme/Glyph-Developer-Kit (2026-09-18).
Реализация должна на них опираться; перепроверять не нужно.

- `Glyph.DEVICE_24111` — общий идентификатор Phone (3a) и (3a) Pro. `Common.isTargetDevice24111(String)` его проверяет.
- `Glyph.Code_24111`: `A_1..A_11` = индексы 20..30, `B_1..B_5` = 31..35, `C_1..C_20` = 0..19. Всего 36 сегментов.
- `GlyphFrame.Builder.buildChannel(int index, int light)` выполняет ровно `channel.set(index, light)`. ArrayList преаллоцирован под размер устройства, поэтому индекс вне `0..35` даст `IndexOutOfBoundsException`.
- Builder обязан быть создан с идентификатором устройства: либо `GlyphManager.getGlyphFrameBuilder()`, либо `GlyphFrame.Builder(deviceId)`. Конструктор без аргументов оставит устройство пустым.
- `GlyphFrame.getChannel()` возвращает `int[]` — полный массив яркостей.
- `GlyphManager`: `getInstance(Context)`, `init(Callback)`, `register(String)`, `openSession()`, `closeSession()`, `unInit()`, `toggle(GlyphFrame)`, `animate(GlyphFrame)`, `turnOff()`, `displayProgress(GlyphFrame, int[, boolean])`.
- `openSession`, `closeSession`, `displayProgress`, `setFrameColors` объявляют `throws GlyphException`. `toggle`, `animate`, `turnOff` — не объявляют.
- `displayProgress` на `DEVICE_24111` требует, чтобы в кадре был подсвечен ровно один из якорей `A_1` (20), `B_1` (31) или `C_1` (0), иначе бросает `GlyphException`.
- Точный диапазон аргумента `progress` у `displayProgress` НЕ ПОДТВЕРЖДЁН (в байткоде видна арифметика вида `progress * 1000 / segmentCount`). Основной путь рендеринга его не использует. Уточняется на устройстве в Task 14.

---

### Task 1: Bootstrap проекта и сборка

Создаёт компилируемый скелет из пяти модулей с подключённым Glyph SDK. Результат: `./gradlew assembleDebug` проходит.

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `gradle/libs.versions.toml`
- Create: `gradle.properties`
- Create: `gradle/wrapper/gradle-wrapper.properties`
- Create: `core/model/build.gradle.kts`
- Create: `core/layout/build.gradle.kts`
- Create: `core/animation/build.gradle.kts`
- Create: `core/hardware/build.gradle.kts`
- Create: `core/hardware/libs/glyph-matrix-sdk-2.0.aar`
- Create: `core/hardware/src/main/AndroidManifest.xml`
- Create: `app/build.gradle.kts`
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/kotlin/com/swrneko/glyphmeter/GlyphMeterApp.kt`
- Create: `app/src/main/kotlin/com/swrneko/glyphmeter/ui/MainActivity.kt`
- Create: `.gitignore`
- Create: `local.properties` (не коммитится)

**Interfaces:**
- Consumes: ничего.
- Produces: собирающийся проект; алиасы version catalog, которыми пользуются все последующие задачи.

- [ ] **Step 1: Установить Android SDK**

На машине нет ни Android SDK, ни `gradle`. Gradle придёт через wrapper, SDK нужно поставить руками.

```bash
mkdir -p ~/Android/Sdk/cmdline-tools
cd /tmp
curl -fsSL -o cmdline-tools.zip https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip
unzip -q cmdline-tools.zip -d /tmp/cmdline-tools-extract
mv /tmp/cmdline-tools-extract/cmdline-tools ~/Android/Sdk/cmdline-tools/latest
yes | ~/Android/Sdk/cmdline-tools/latest/bin/sdkmanager --licenses
~/Android/Sdk/cmdline-tools/latest/bin/sdkmanager "platforms;android-37" "build-tools;37.0.0" "platform-tools"
```

Если версия архива `commandlinetools-linux-*` недоступна, взять актуальную ссылку с https://developer.android.com/studio#command-line-tools-only. Если `platforms;android-37` ещё не публикуется — выполнить `sdkmanager --list | grep 'platforms;android-3'` и взять максимальный доступный, одновременно понизив `compileSdk` в version catalog до него.

Проверка: `~/Android/Sdk/cmdline-tools/latest/bin/sdkmanager --list_installed` показывает `platforms;android-37`.

- [ ] **Step 2: Положить Glyph SDK в репозиторий**

`.aar` не публикуется в Maven, поэтому вендорим его.

```bash
cd /home/swrneko/Documents/Projects/ngcm
mkdir -p core/hardware/libs
curl -fsSL -o core/hardware/libs/glyph-matrix-sdk-2.0.aar \
  https://github.com/Nothing-Developer-Programme/Glyph-Developer-Kit/raw/main/sdk/glyph-matrix-sdk-2.0.aar
unzip -l core/hardware/libs/glyph-matrix-sdk-2.0.aar | grep classes.jar
```

Ожидается строка с `classes.jar` размером около 120 КБ.

- [ ] **Step 3: Написать `gradle/libs.versions.toml`**

```toml
[versions]
agp = "9.4.0"
kotlin = "2.4.20"
ksp = "2.3.12"
hilt = "2.60.1"
hiltNavigationCompose = "1.4.0"
composeBom = "2026.09.00"
material3 = "1.4.0"
coreKtx = "1.19.0"
lifecycle = "2.11.0"
activityCompose = "1.13.0"
datastore = "1.2.1"
navigationCompose = "2.10.1"
shizuku = "13.1.5"
junit = "4.13.2"
coroutines = "1.11.0"
androidxTestExtJunit = "1.3.0"
turbine = "1.2.1"
robolectric = "4.17"

[libraries]
androidx-core-ktx = { module = "androidx.core:core-ktx", version.ref = "coreKtx" }
androidx-lifecycle-runtime-ktx = { module = "androidx.lifecycle:lifecycle-runtime-ktx", version.ref = "lifecycle" }
androidx-lifecycle-viewmodel-compose = { module = "androidx.lifecycle:lifecycle-viewmodel-compose", version.ref = "lifecycle" }
androidx-lifecycle-service = { module = "androidx.lifecycle:lifecycle-service", version.ref = "lifecycle" }
androidx-activity-compose = { module = "androidx.activity:activity-compose", version.ref = "activityCompose" }
androidx-datastore-preferences = { module = "androidx.datastore:datastore-preferences", version.ref = "datastore" }
androidx-navigation-compose = { module = "androidx.navigation:navigation-compose", version.ref = "navigationCompose" }
androidx-compose-bom = { module = "androidx.compose:compose-bom", version.ref = "composeBom" }
androidx-compose-ui = { module = "androidx.compose.ui:ui" }
androidx-compose-ui-graphics = { module = "androidx.compose.ui:ui-graphics" }
androidx-compose-ui-tooling = { module = "androidx.compose.ui:ui-tooling" }
androidx-compose-ui-tooling-preview = { module = "androidx.compose.ui:ui-tooling-preview" }
androidx-compose-material3 = { module = "androidx.compose.material3:material3", version.ref = "material3" }
androidx-compose-material-icons-extended = { module = "androidx.compose.material:material-icons-extended" }
hilt-android = { module = "com.google.dagger:hilt-android", version.ref = "hilt" }
hilt-android-compiler = { module = "com.google.dagger:hilt-android-compiler", version.ref = "hilt" }
androidx-hilt-navigation-compose = { module = "androidx.hilt:hilt-navigation-compose", version.ref = "hiltNavigationCompose" }
shizuku-api = { module = "dev.rikka.shizuku:api", version.ref = "shizuku" }
shizuku-provider = { module = "dev.rikka.shizuku:provider", version.ref = "shizuku" }
kotlinx-coroutines-android = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-android", version.ref = "coroutines" }
junit = { module = "junit:junit", version.ref = "junit" }
kotlinx-coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "coroutines" }
turbine = { module = "app.cash.turbine:turbine", version.ref = "turbine" }
robolectric = { module = "org.robolectric:robolectric", version.ref = "robolectric" }
androidx-test-ext-junit = { module = "androidx.test.ext:junit", version.ref = "androidxTestExtJunit" }
androidx-compose-ui-test-junit4 = { module = "androidx.compose.ui:ui-test-junit4" }
androidx-compose-ui-test-manifest = { module = "androidx.compose.ui:ui-test-manifest" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
android-library = { id = "com.android.library", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
hilt = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
```

Примечание по KSP: совместимость `KSP 2.3.12 ↔ Kotlin 2.4.20` по release notes не подтверждена. Если сборка ругнётся на версию компилятора — понизить Kotlin до 2.3.x либо взять KSP из соответствующей линии.

- [ ] **Step 4: Написать корневые файлы сборки**

`settings.gradle.kts`:

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://maven.rikka.dev/") // Shizuku
    }
}
rootProject.name = "GlyphMeter"
include(":app", ":core:model", ":core:layout", ":core:animation", ":core:hardware")
```

`build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}
```

`gradle.properties`:

```properties
org.gradle.jvmargs=-Xmx4096m -Dfile.encoding=UTF-8
org.gradle.parallel=true
org.gradle.caching=true
org.gradle.configuration-cache=true
android.useAndroidX=true
android.nonTransitiveRClass=true
kotlin.code.style=official
```

`gradle/wrapper/gradle-wrapper.properties`:

```properties
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-9.7.1-bin.zip
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
```

`local.properties` (в `.gitignore`):

```properties
sdk.dir=/home/swrneko/Android/Sdk
```

`.gitignore`:

```
local.properties
.gradle/
build/
*.iml
.idea/
.kotlin/
```

Wrapper-скрипты и `gradle-wrapper.jar` создать командой (нужен временно установленный gradle или скачать jar):

```bash
curl -fsSL -o gradle/wrapper/gradle-wrapper.jar \
  https://raw.githubusercontent.com/gradle/gradle/v9.7.1/gradle/wrapper/gradle-wrapper.jar
curl -fsSL -o gradlew https://raw.githubusercontent.com/gradle/gradle/v9.7.1/gradlew
chmod +x gradlew
```

- [ ] **Step 5: Написать build-файлы модулей**

`core/model/build.gradle.kts` (то же самое для `core/layout` и `core/animation`, меняются только зависимости):

```kotlin
plugins {
    alias(libs.plugins.kotlin.jvm)
}
kotlin {
    jvmToolchain(17)
}
dependencies {
    testImplementation(libs.junit)
}
```

`core/layout/build.gradle.kts` — то же плюс:

```kotlin
dependencies {
    api(project(":core:model"))
    testImplementation(libs.junit)
}
```

`core/animation/build.gradle.kts` — то же плюс:

```kotlin
dependencies {
    api(project(":core:model"))
    api(project(":core:layout"))
    testImplementation(libs.junit)
}
```

`core/hardware/build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}
android {
    namespace = "com.swrneko.glyphmeter.hardware"
    compileSdk = 37
    defaultConfig {
        minSdk = 33
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        jvmToolchain(17)
    }
}
dependencies {
    api(project(":core:model"))
    implementation(files("libs/glyph-matrix-sdk-2.0.aar"))
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
```

`core/hardware/src/main/AndroidManifest.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android" />
```

`app/build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}
android {
    namespace = "com.swrneko.glyphmeter"
    compileSdk = 37
    defaultConfig {
        applicationId = "com.swrneko.glyphmeter"
        minSdk = 33
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures {
        compose = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        jvmToolchain(17)
    }
    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
}
dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:layout"))
    implementation(project(":core:animation"))
    implementation(project(":core:hardware"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.robolectric)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
```

- [ ] **Step 6: Написать манифест приложения и точки входа**

`app/src/main/AndroidManifest.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="com.nothing.ketchum.permission.ENABLE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <uses-permission android:name="android.permission.WRITE_SECURE_SETTINGS"
        tools:ignore="ProtectedPermissions" />

    <application
        android:name=".GlyphMeterApp"
        android:label="@string/app_name"
        android:icon="@mipmap/ic_launcher"
        android:supportsRtl="true"
        android:theme="@style/Theme.GlyphMeter">

        <meta-data android:name="NothingKey" android:value="test" />

        <activity
            android:name=".ui.MainActivity"
            android:exported="true"
            android:theme="@style/Theme.GlyphMeter">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

Добавить `xmlns:tools="http://schemas.android.com/tools"` в корневой тег `manifest`.

`GlyphMeterApp.kt`:

```kotlin
package com.swrneko.glyphmeter

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class GlyphMeterApp : Application()
```

`MainActivity.kt`:

```kotlin
package com.swrneko.glyphmeter.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { Text("Glyph Meter") }
    }
}
```

Создать `app/src/main/res/values/strings.xml` с `app_name` = `Glyph Meter` и `app/src/main/res/values/themes.xml` со стилем `Theme.GlyphMeter`, наследующим `android:Theme.Material.NoActionBar`.

- [ ] **Step 7: Собрать**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`. Если падает на KSP/Kotlin — применить примечание из Step 3.

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "Каркас проекта и подключение Glyph SDK"
```

---

### Task 2: Модель кадра и раскладка устройства

**Files:**
- Create: `core/model/src/main/kotlin/com/swrneko/glyphmeter/model/GlyphFrameData.kt`
- Create: `core/model/src/main/kotlin/com/swrneko/glyphmeter/model/DeviceLayout.kt`
- Create: `core/model/src/main/kotlin/com/swrneko/glyphmeter/model/Light.kt`
- Create: `core/layout/src/main/kotlin/com/swrneko/glyphmeter/layout/DeviceLayouts.kt`
- Test: `core/model/src/test/kotlin/com/swrneko/glyphmeter/model/GlyphFrameDataTest.kt`
- Test: `core/layout/src/test/kotlin/com/swrneko/glyphmeter/layout/DeviceLayoutsTest.kt`

**Interfaces:**
- Consumes: ничего.
- Produces: `GlyphFrameData(segments: IntArray)` с `contentEquals`-семантикой и фабрикой `GlyphFrameData.off(size: Int)`; `DeviceLayout`, `GlyphZone`; объект `DeviceLayouts` с `all: List<DeviceLayout>`, `byDeviceId(String): DeviceLayout?` и константой `PHONE_3A`; `Light.MAX = 4095`, `Light.MIN_VISIBLE = 800`.

- [ ] **Step 1: Написать падающий тест на модель кадра**

`GlyphFrameDataTest.kt`:

```kotlin
package com.swrneko.glyphmeter.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class GlyphFrameDataTest {

    @Test
    fun `off produces a frame of the requested size with all segments dark`() {
        val frame = GlyphFrameData.off(36)

        assertEquals(36, frame.size)
        assertEquals(List(36) { 0 }, frame.segments.toList())
    }

    @Test
    fun `frames with identical segment values are equal`() {
        val a = GlyphFrameData(intArrayOf(0, 100, 4095))
        val b = GlyphFrameData(intArrayOf(0, 100, 4095))

        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `frames with different segment values are not equal`() {
        val a = GlyphFrameData(intArrayOf(0, 100, 4095))
        val b = GlyphFrameData(intArrayOf(0, 101, 4095))

        assertNotEquals(a, b)
    }
}
```

Почему так: `IntArray` по умолчанию сравнивается по ссылке, и без явного `contentEquals` каждый тест на равенство кадров молча провалится. Это первое, что нужно зафиксировать.

- [ ] **Step 2: Запустить и убедиться, что падает**

Run: `./gradlew :core:model:test`
Expected: FAIL, `Unresolved reference: GlyphFrameData`.

- [ ] **Step 3: Реализовать модель кадра**

`GlyphFrameData.kt`:

```kotlin
package com.swrneko.glyphmeter.model

/**
 * One frame of the Glyph interface: a brightness value per addressable segment.
 *
 * Index i maps directly onto the Nothing SDK channel index, so a frame can be
 * handed to `GlyphFrame.Builder.buildChannel(i, segments[i])` without translation.
 */
class GlyphFrameData(val segments: IntArray) {

    val size: Int get() = segments.size

    operator fun get(index: Int): Int = segments[index]

    override fun equals(other: Any?): Boolean =
        this === other || (other is GlyphFrameData && segments.contentEquals(other.segments))

    override fun hashCode(): Int = segments.contentHashCode()

    override fun toString(): String = "GlyphFrameData(${segments.joinToString()})"

    companion object {
        fun off(size: Int): GlyphFrameData = GlyphFrameData(IntArray(size))
    }
}
```

`Light.kt`:

```kotlin
package com.swrneko.glyphmeter.model

/** Brightness limits of a single Glyph segment, taken from the Nothing SDK. */
object Light {
    /** Highest value the SDK accepts. `GlyphManager.DEFAULT_MAX_LIGHT` is 4096, so the top value is 4095. */
    const val MAX: Int = 4095

    /** `GlyphManager.DEFAULT_MIN_LIGHT`. Below this an LED is effectively invisible. */
    const val MIN_VISIBLE: Int = 800
}
```

- [ ] **Step 4: Запустить тесты модели**

Run: `./gradlew :core:model:test`
Expected: PASS.

- [ ] **Step 5: Написать падающий тест на раскладку**

`DeviceLayoutsTest.kt`:

```kotlin
package com.swrneko.glyphmeter.layout

import com.swrneko.glyphmeter.model.DeviceLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceLayoutsTest {

    @Test
    fun `phone 3a has thirty six segments`() {
        assertEquals(36, DeviceLayouts.PHONE_3A.segmentCount)
    }

    @Test
    fun `phone 3a zones match the SDK constant table`() {
        val layout = DeviceLayouts.PHONE_3A

        assertEquals((0..19).toList(), layout.zone("C").indices)
        assertEquals((20..30).toList(), layout.zone("A").indices)
        assertEquals((31..35).toList(), layout.zone("B").indices)
    }

    @Test
    fun `phone 3a uses the twenty segment strip as its meter`() {
        val layout = DeviceLayouts.PHONE_3A

        assertEquals("C", layout.meterZoneId)
        assertEquals(20, layout.meterZone.indices.size)
    }

    @Test
    fun `every layout covers each segment index exactly once`() {
        for (layout in DeviceLayouts.all) {
            val covered = layout.zones.flatMap { it.indices }

            assertEquals(
                "layout ${layout.deviceId} has duplicate or missing indices",
                (0 until layout.segmentCount).toList(),
                covered.sorted(),
            )
        }
    }

    @Test
    fun `every layout declares a meter zone that exists`() {
        for (layout in DeviceLayouts.all) {
            assertTrue(
                "layout ${layout.deviceId} points at a missing meter zone",
                layout.zones.any { it.id == layout.meterZoneId },
            )
        }
    }

    @Test
    fun `lookup by device id finds a known device and rejects an unknown one`() {
        assertNotNull(DeviceLayouts.byDeviceId("24111"))
        assertNull(DeviceLayouts.byDeviceId("not-a-nothing-phone"))
    }
}
```

- [ ] **Step 6: Запустить и убедиться, что падает**

Run: `./gradlew :core:layout:test`
Expected: FAIL, `Unresolved reference: DeviceLayouts`.

- [ ] **Step 7: Реализовать раскладку**

`DeviceLayout.kt` (модуль `:core:model`):

```kotlin
package com.swrneko.glyphmeter.model

/**
 * A contiguous, ordered group of Glyph segments.
 *
 * [indices] is in visual order: the first entry is where an animation or a meter
 * starts filling from.
 */
data class GlyphZone(
    val id: String,
    val indices: List<Int>,
)

/**
 * Physical description of one phone's Glyph interface.
 *
 * Adding a new phone means adding one entry to `DeviceLayouts.all`; no logic changes.
 */
data class DeviceLayout(
    /** Value of the matching `Glyph.DEVICE_*` constant, without the `DEVICE_` prefix. */
    val deviceId: String,
    val displayName: String,
    val segmentCount: Int,
    val zones: List<GlyphZone>,
    /** Zone used as the battery meter. */
    val meterZoneId: String,
    /**
     * Segment that `GlyphManager.displayProgress` insists on having lit for this device.
     * Used only by the stepped fallback renderer.
     */
    val progressAnchorIndex: Int,
    /** True when this layout was verified against real hardware. */
    val hardwareVerified: Boolean,
) {
    fun zone(id: String): GlyphZone =
        zones.firstOrNull { it.id == id }
            ?: error("layout $deviceId has no zone $id")

    val meterZone: GlyphZone get() = zone(meterZoneId)
}
```

`DeviceLayouts.kt` (модуль `:core:layout`):

```kotlin
package com.swrneko.glyphmeter.layout

import com.swrneko.glyphmeter.model.DeviceLayout
import com.swrneko.glyphmeter.model.GlyphZone

/**
 * Table of known Glyph layouts.
 *
 * Index values are taken from the `Glyph.Code_*` classes inside
 * `glyph-matrix-sdk-2.0.aar`, read out of the bytecode rather than the README.
 */
object DeviceLayouts {

    /**
     * Nothing Phone (3a) and (3a) Pro. Both report the same device id.
     *
     * Verified: A_1..A_11 = 20..30, B_1..B_5 = 31..35, C_1..C_20 = 0..19.
     * Zone C is the long strip and runs bottom-left to top-right, which makes it
     * the natural battery meter.
     */
    val PHONE_3A: DeviceLayout = DeviceLayout(
        deviceId = "24111",
        displayName = "Phone (3a) / (3a) Pro",
        segmentCount = 36,
        zones = listOf(
            GlyphZone(id = "C", indices = (0..19).toList()),
            GlyphZone(id = "A", indices = (20..30).toList()),
            GlyphZone(id = "B", indices = (31..35).toList()),
        ),
        meterZoneId = "C",
        progressAnchorIndex = 0,
        hardwareVerified = true,
    )

    val all: List<DeviceLayout> = listOf(PHONE_3A)

    fun byDeviceId(deviceId: String): DeviceLayout? =
        all.firstOrNull { it.deviceId == deviceId }
}
```

Другие модели (Phone (1), (2), (2a), (4a), (4b)) в первой версии НЕ добавляются: их индексы не проверены на железе, а таблица — единственный источник правды. Они добавляются отдельной задачей после проверки байткода соответствующих `Code_*` классов.

- [ ] **Step 8: Запустить тесты**

Run: `./gradlew :core:model:test :core:layout:test`
Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add -A
git commit -m "Модель кадра глифа и таблица раскладок устройств"
```

---

### Task 3: Рендер шкалы заряда с дробным сегментом

Ядро идеи проекта. Чистая функция, никакого Android.

**Files:**
- Create: `core/layout/src/main/kotlin/com/swrneko/glyphmeter/layout/MeterRenderer.kt`
- Test: `core/layout/src/test/kotlin/com/swrneko/glyphmeter/layout/MeterRendererTest.kt`

**Interfaces:**
- Consumes: `DeviceLayout`, `GlyphFrameData`, `Light` (Task 2).
- Produces: `MeterRenderer.smooth(level: Float, layout: DeviceLayout, brightness: Int): GlyphFrameData` и `MeterRenderer.stepped(level: Float, layout: DeviceLayout, brightness: Int): GlyphFrameData`. `level` в диапазоне `0f..1f`, `brightness` в `0..Light.MAX`.

- [ ] **Step 1: Написать падающий тест**

`MeterRendererTest.kt`:

```kotlin
package com.swrneko.glyphmeter.layout

import com.swrneko.glyphmeter.model.Light
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MeterRendererTest {

    private val layout = DeviceLayouts.PHONE_3A
    private val meter = layout.meterZone.indices
    private val full = Light.MAX

    @Test
    fun `an empty battery leaves every segment dark`() {
        val frame = MeterRenderer.smooth(level = 0f, layout = layout, brightness = full)

        assertEquals(List(36) { 0 }, frame.segments.toList())
    }

    @Test
    fun `a full battery lights the whole meter zone and nothing else`() {
        val frame = MeterRenderer.smooth(level = 1f, layout = layout, brightness = full)

        for (index in meter) {
            assertEquals("segment $index", full, frame[index])
        }
        for (index in layout.zone("A").indices + layout.zone("B").indices) {
            assertEquals("segment $index", 0, frame[index])
        }
    }

    @Test
    fun `a level on an exact segment boundary lights whole segments only`() {
        // 20 segments, so 25% is exactly 5 of them.
        val frame = MeterRenderer.smooth(level = 0.25f, layout = layout, brightness = full)

        assertEquals(listOf(full, full, full, full, full), meter.take(5).map { frame[it] })
        assertEquals(List(15) { 0 }, meter.drop(5).map { frame[it] })
    }

    @Test
    fun `a level between segments lights the next segment partially`() {
        // 63% of 20 segments is 12.6: twelve full, the thirteenth at 60 percent.
        val frame = MeterRenderer.smooth(level = 0.63f, layout = layout, brightness = full)

        assertEquals(List(12) { full }, meter.take(12).map { frame[it] })

        val partial = frame[meter[12]]
        val expected = Light.MIN_VISIBLE + ((full - Light.MIN_VISIBLE) * 0.6f).toInt()
        assertTrue("partial was $partial, expected near $expected", kotlin.math.abs(partial - expected) <= 2)

        assertEquals(List(7) { 0 }, meter.drop(13).map { frame[it] })
    }

    @Test
    fun `a barely started segment is still bright enough to see`() {
        // One percent into a segment must not produce an invisible glow.
        val frame = MeterRenderer.smooth(level = 0.0005f, layout = layout, brightness = full)

        assertTrue(frame[meter[0]] >= Light.MIN_VISIBLE)
    }

    @Test
    fun `the meter never decreases as the battery rises`() {
        var previous = List(36) { 0 }

        for (percent in 0..100) {
            val frame = MeterRenderer.smooth(percent / 100f, layout, full)
            val current = frame.segments.toList()

            for (index in meter) {
                assertTrue(
                    "segment $index dropped at $percent percent",
                    current[index] >= previous[index],
                )
            }
            previous = current
        }
    }

    @Test
    fun `brightness scales the whole meter`() {
        val frame = MeterRenderer.smooth(level = 0.25f, layout = layout, brightness = 1000)

        assertEquals(List(5) { 1000 }, meter.take(5).map { frame[it] })
    }

    @Test
    fun `a level outside the valid range is clamped`() {
        assertEquals(
            MeterRenderer.smooth(0f, layout, full),
            MeterRenderer.smooth(-5f, layout, full),
        )
        assertEquals(
            MeterRenderer.smooth(1f, layout, full),
            MeterRenderer.smooth(5f, layout, full),
        )
    }

    @Test
    fun `the stepped renderer uses whole segments only`() {
        val frame = MeterRenderer.stepped(level = 0.63f, layout = layout, brightness = full)

        val lit = meter.map { frame[it] }

        assertTrue("stepped output must be all-or-nothing", lit.all { it == 0 || it == full })
        assertEquals(12, lit.count { it == full })
    }

    @Test
    fun `the stepped renderer always lights the progress anchor when anything is lit`() {
        val frame = MeterRenderer.stepped(level = 0.1f, layout = layout, brightness = full)

        assertTrue(frame[layout.progressAnchorIndex] > 0)
    }
}
```

- [ ] **Step 2: Запустить и убедиться, что падает**

Run: `./gradlew :core:layout:test --tests '*MeterRendererTest*'`
Expected: FAIL, `Unresolved reference: MeterRenderer`.

- [ ] **Step 3: Реализовать рендерер**

```kotlin
package com.swrneko.glyphmeter.layout

import com.swrneko.glyphmeter.model.DeviceLayout
import com.swrneko.glyphmeter.model.GlyphFrameData
import com.swrneko.glyphmeter.model.Light
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Turns a battery level into a Glyph frame.
 *
 * The Nothing SDK's own `displayProgress` fills a zone one whole segment at a time,
 * which on Phone (3a) means twenty steps, or five percent of charge per step.
 * [smooth] instead drives per-segment brightness: whole segments burn at full
 * brightness and the next one glows in proportion to the remainder, which makes the
 * scale effectively continuous.
 */
object MeterRenderer {

    fun smooth(level: Float, layout: DeviceLayout, brightness: Int): GlyphFrameData {
        val segments = IntArray(layout.segmentCount)
        val meter = layout.meterZone.indices
        val clampedLevel = level.coerceIn(0f, 1f)
        val clampedBrightness = brightness.coerceIn(0, Light.MAX)

        val exact = clampedLevel * meter.size
        val wholeSegments = floor(exact).toInt()
        val remainder = exact - wholeSegments

        for (i in 0 until wholeSegments) {
            segments[meter[i]] = clampedBrightness
        }

        // A segment that has only just started filling still has to be visible, so the
        // partial value is mapped onto [MIN_VISIBLE, brightness] rather than onto
        // [0, brightness]. Without this the leading segment would be dark for the first
        // fifth of its range and the scale would look like it lags behind the battery.
        if (wholeSegments < meter.size && remainder > 0f) {
            val floorLight = minOf(Light.MIN_VISIBLE, clampedBrightness)
            val span = clampedBrightness - floorLight
            segments[meter[wholeSegments]] = floorLight + (span * remainder).roundToInt()
        }

        return GlyphFrameData(segments)
    }

    /**
     * All-or-nothing fallback, used when per-segment brightness is unavailable.
     *
     * Also lights [DeviceLayout.progressAnchorIndex], because `displayProgress` throws
     * a `GlyphException` unless that segment is present in the frame.
     */
    fun stepped(level: Float, layout: DeviceLayout, brightness: Int): GlyphFrameData {
        val segments = IntArray(layout.segmentCount)
        val meter = layout.meterZone.indices
        val clampedLevel = level.coerceIn(0f, 1f)
        val clampedBrightness = brightness.coerceIn(0, Light.MAX)

        val wholeSegments = floor(clampedLevel * meter.size).toInt()

        for (i in 0 until wholeSegments) {
            segments[meter[i]] = clampedBrightness
        }
        if (wholeSegments > 0) {
            segments[layout.progressAnchorIndex] = clampedBrightness
        }

        return GlyphFrameData(segments)
    }
}
```

- [ ] **Step 4: Запустить тесты**

Run: `./gradlew :core:layout:test`
Expected: PASS, все десять тестов `MeterRendererTest`.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "Рендер шкалы заряда с субсегментной плавностью"
```

---

### Task 4: Кривые сглаживания и плавный переход уровня

**Files:**
- Create: `core/animation/src/main/kotlin/com/swrneko/glyphmeter/animation/Easing.kt`
- Create: `core/animation/src/main/kotlin/com/swrneko/glyphmeter/animation/FrameSource.kt`
- Create: `core/animation/src/main/kotlin/com/swrneko/glyphmeter/animation/MeterTransition.kt`
- Test: `core/animation/src/test/kotlin/com/swrneko/glyphmeter/animation/EasingTest.kt`
- Test: `core/animation/src/test/kotlin/com/swrneko/glyphmeter/animation/MeterTransitionTest.kt`

**Interfaces:**
- Consumes: `MeterRenderer` (Task 3), `DeviceLayout`, `GlyphFrameData`.
- Produces: `fun interface Easing { fun transform(t: Float): Float }` с реализациями `Easing.Linear` и `Easing.EaseOutCubic`; `interface FrameSource { val durationMillis: Long; fun frameAt(elapsedMillis: Long): GlyphFrameData }`; класс `MeterTransition(fromLevel, toLevel, layout, brightness, durationMillis, easing)`, реализующий `FrameSource`.

- [ ] **Step 1: Написать падающий тест на сглаживание**

`EasingTest.kt`:

```kotlin
package com.swrneko.glyphmeter.animation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EasingTest {

    @Test
    fun `linear easing returns its input`() {
        assertEquals(0f, Easing.Linear.transform(0f), 0.0001f)
        assertEquals(0.5f, Easing.Linear.transform(0.5f), 0.0001f)
        assertEquals(1f, Easing.Linear.transform(1f), 0.0001f)
    }

    @Test
    fun `ease out cubic starts and ends where linear does`() {
        assertEquals(0f, Easing.EaseOutCubic.transform(0f), 0.0001f)
        assertEquals(1f, Easing.EaseOutCubic.transform(1f), 0.0001f)
    }

    @Test
    fun `ease out cubic moves fast early and slow late`() {
        assertTrue(Easing.EaseOutCubic.transform(0.25f) > 0.25f)
        assertTrue(Easing.EaseOutCubic.transform(0.9f) > 0.9f)
    }

    @Test
    fun `ease out cubic never goes backwards`() {
        var previous = -1f

        for (step in 0..100) {
            val value = Easing.EaseOutCubic.transform(step / 100f)
            assertTrue("dropped at $step", value >= previous)
            previous = value
        }
    }
}
```

- [ ] **Step 2: Запустить и убедиться, что падает**

Run: `./gradlew :core:animation:test`
Expected: FAIL, `Unresolved reference: Easing`.

- [ ] **Step 3: Реализовать сглаживание и интерфейс источника кадров**

`Easing.kt`:

```kotlin
package com.swrneko.glyphmeter.animation

fun interface Easing {
    /** Maps normalised time in 0..1 onto normalised progress in 0..1. */
    fun transform(t: Float): Float

    companion object {
        val Linear = Easing { t -> t.coerceIn(0f, 1f) }

        /** Decelerating curve: quick to react, gentle to settle. */
        val EaseOutCubic = Easing { t ->
            val clamped = t.coerceIn(0f, 1f)
            val inverted = 1f - clamped
            1f - inverted * inverted * inverted
        }
    }
}
```

`FrameSource.kt`:

```kotlin
package com.swrneko.glyphmeter.animation

import com.swrneko.glyphmeter.model.GlyphFrameData

/**
 * A finite sequence of Glyph frames expressed as a function of elapsed time.
 *
 * Time is passed in rather than read from a clock, so every animation is testable
 * without waiting for anything.
 */
interface FrameSource {
    val durationMillis: Long

    /** Frame to show [elapsedMillis] after the animation started. Values past the end clamp. */
    fun frameAt(elapsedMillis: Long): GlyphFrameData
}
```

- [ ] **Step 4: Написать падающий тест на переход уровня**

`MeterTransitionTest.kt`:

```kotlin
package com.swrneko.glyphmeter.animation

import com.swrneko.glyphmeter.layout.DeviceLayouts
import com.swrneko.glyphmeter.layout.MeterRenderer
import com.swrneko.glyphmeter.model.Light
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MeterTransitionTest {

    private val layout = DeviceLayouts.PHONE_3A
    private val meter = layout.meterZone.indices

    private fun transition(from: Float, to: Float) = MeterTransition(
        fromLevel = from,
        toLevel = to,
        layout = layout,
        brightness = Light.MAX,
        durationMillis = 500,
        easing = Easing.EaseOutCubic,
    )

    @Test
    fun `it starts at the old level`() {
        val subject = transition(from = 0.5f, to = 0.6f)

        assertEquals(MeterRenderer.smooth(0.5f, layout, Light.MAX), subject.frameAt(0))
    }

    @Test
    fun `it ends at the new level`() {
        val subject = transition(from = 0.5f, to = 0.6f)

        assertEquals(MeterRenderer.smooth(0.6f, layout, Light.MAX), subject.frameAt(500))
    }

    @Test
    fun `time past the end stays at the new level`() {
        val subject = transition(from = 0.5f, to = 0.6f)

        assertEquals(subject.frameAt(500), subject.frameAt(10_000))
    }

    @Test
    fun `it passes through intermediate levels instead of jumping`() {
        val subject = transition(from = 0f, to = 1f)

        val midpoint = subject.frameAt(250)
        val litSegments = meter.count { midpoint[it] > 0 }

        assertTrue("expected a partly filled meter, got $litSegments segments", litSegments in 1..19)
    }

    @Test
    fun `a rising transition never shows the meter going backwards`() {
        val subject = transition(from = 0.2f, to = 0.8f)
        var previous = subject.frameAt(0)

        for (elapsed in 0L..500L step 10L) {
            val current = subject.frameAt(elapsed)
            for (index in meter) {
                assertTrue("segment $index dropped at ${elapsed}ms", current[index] >= previous[index])
            }
            previous = current
        }
    }

    @Test
    fun `a zero length transition reports the new level immediately`() {
        val subject = MeterTransition(
            fromLevel = 0.2f,
            toLevel = 0.9f,
            layout = layout,
            brightness = Light.MAX,
            durationMillis = 0,
            easing = Easing.EaseOutCubic,
        )

        assertEquals(MeterRenderer.smooth(0.9f, layout, Light.MAX), subject.frameAt(0))
    }
}
```

- [ ] **Step 5: Запустить и убедиться, что падает**

Run: `./gradlew :core:animation:test --tests '*MeterTransitionTest*'`
Expected: FAIL, `Unresolved reference: MeterTransition`.

- [ ] **Step 6: Реализовать переход**

`MeterTransition.kt`:

```kotlin
package com.swrneko.glyphmeter.animation

import com.swrneko.glyphmeter.layout.MeterRenderer
import com.swrneko.glyphmeter.model.DeviceLayout
import com.swrneko.glyphmeter.model.GlyphFrameData

/**
 * Slides the meter from one battery level to another instead of snapping to it.
 *
 * This is the second, independent layer of smoothness: [MeterRenderer] makes a single
 * level look continuous, and this makes a change of level look continuous too.
 */
class MeterTransition(
    private val fromLevel: Float,
    private val toLevel: Float,
    private val layout: DeviceLayout,
    private val brightness: Int,
    override val durationMillis: Long,
    private val easing: Easing = Easing.EaseOutCubic,
) : FrameSource {

    override fun frameAt(elapsedMillis: Long): GlyphFrameData {
        val progress = when {
            durationMillis <= 0L -> 1f
            else -> (elapsedMillis.toFloat() / durationMillis).coerceIn(0f, 1f)
        }
        val eased = easing.transform(progress)
        val level = fromLevel + (toLevel - fromLevel) * eased

        return MeterRenderer.smooth(level, layout, brightness)
    }
}
```

- [ ] **Step 7: Запустить тесты**

Run: `./gradlew :core:animation:test`
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "Кривые сглаживания и плавный переход шкалы"
```

---

### Task 5: Пресеты анимаций подключения

**Files:**
- Create: `core/animation/src/main/kotlin/com/swrneko/glyphmeter/animation/AnimationPreset.kt`
- Create: `core/animation/src/main/kotlin/com/swrneko/glyphmeter/animation/PresetFrameSource.kt`
- Test: `core/animation/src/test/kotlin/com/swrneko/glyphmeter/animation/PresetFrameSourceTest.kt`

**Interfaces:**
- Consumes: `FrameSource`, `Easing`, `DeviceLayout`, `GlyphFrameData`, `Light`.
- Produces: `enum class PresetKind { FILL_UP, WAVE, BREATHE, CHASE, FLASH, FADE_OUT }`; `data class AnimationPreset(val id: String, val kind: PresetKind, val durationMillis: Long)`; `object AnimationPresets` с `all: List<AnimationPreset>` и `byId(String): AnimationPreset?`; класс `PresetFrameSource(preset, layout, brightness)`, реализующий `FrameSource`.

- [ ] **Step 1: Написать падающий тест**

`PresetFrameSourceTest.kt`:

```kotlin
package com.swrneko.glyphmeter.animation

import com.swrneko.glyphmeter.layout.DeviceLayouts
import com.swrneko.glyphmeter.model.Light
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PresetFrameSourceTest {

    private val layout = DeviceLayouts.PHONE_3A

    private fun source(preset: AnimationPreset) =
        PresetFrameSource(preset = preset, layout = layout, brightness = Light.MAX)

    @Test
    fun `every preset is registered under a unique id`() {
        val ids = AnimationPresets.all.map { it.id }

        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `lookup finds a known preset and rejects an unknown one`() {
        assertNotNull(AnimationPresets.byId("fill_up"))
        assertNull(AnimationPresets.byId("no_such_preset"))
    }

    @Test
    fun `every preset produces frames sized for the device`() {
        for (preset in AnimationPresets.all) {
            val frame = source(preset).frameAt(preset.durationMillis / 2)

            assertEquals("preset ${preset.id}", 36, frame.size)
        }
    }

    @Test
    fun `every preset keeps brightness inside the legal range`() {
        for (preset in AnimationPresets.all) {
            val subject = source(preset)

            for (elapsed in 0L..preset.durationMillis step 10L) {
                for (value in subject.frameAt(elapsed).segments) {
                    assertTrue("preset ${preset.id} produced $value", value in 0..Light.MAX)
                }
            }
        }
    }

    @Test
    fun `every preset ends dark so the meter can take over cleanly`() {
        for (preset in AnimationPresets.all) {
            val last = source(preset).frameAt(preset.durationMillis)

            assertEquals(
                "preset ${preset.id} left segments lit",
                List(36) { 0 },
                last.segments.toList(),
            )
        }
    }

    @Test
    fun `every preset lights something in the middle`() {
        for (preset in AnimationPresets.all) {
            val middle = source(preset).frameAt(preset.durationMillis / 2)

            assertTrue("preset ${preset.id} is dark all the way through", middle.segments.any { it > 0 })
        }
    }

    @Test
    fun `fill up lights the meter from the bottom upward`() {
        val preset = AnimationPresets.byId("fill_up")!!
        val subject = source(preset)
        val meter = layout.meterZone.indices

        val early = subject.frameAt(preset.durationMillis / 5)
        val lit = meter.filter { early[it] > 0 }

        assertTrue("expected the low end lit first, got $lit", lit.isNotEmpty())
        assertEquals(meter.take(lit.size), lit)
    }

    @Test
    fun `wave touches every zone at some point`() {
        val preset = AnimationPresets.byId("wave")!!
        val subject = source(preset)
        val touched = mutableSetOf<Int>()

        for (elapsed in 0L..preset.durationMillis step 10L) {
            val frame = subject.frameAt(elapsed)
            for (index in 0 until frame.size) {
                if (frame[index] > 0) touched += index
            }
        }

        assertEquals((0 until 36).toSet(), touched)
    }

    @Test
    fun `time past the end is dark rather than an error`() {
        for (preset in AnimationPresets.all) {
            val frame = source(preset).frameAt(preset.durationMillis + 100_000)

            assertEquals("preset ${preset.id}", List(36) { 0 }, frame.segments.toList())
        }
    }
}
```

- [ ] **Step 2: Запустить и убедиться, что падает**

Run: `./gradlew :core:animation:test --tests '*PresetFrameSourceTest*'`
Expected: FAIL, `Unresolved reference: AnimationPreset`.

- [ ] **Step 3: Реализовать описание пресетов**

`AnimationPreset.kt`:

```kotlin
package com.swrneko.glyphmeter.animation

/** How a preset animates. The renderer switches on this; presets themselves are data. */
enum class PresetKind {
    /** Meter zone fills from its first segment to its last, then fades. */
    FILL_UP,

    /** A lit band sweeps across every zone in order. */
    WAVE,

    /** The whole interface breathes up and back down. */
    BREATHE,

    /** A single segment runs around the meter zone twice. */
    CHASE,

    /** Two short full-brightness pulses. */
    FLASH,

    /** Everything lit, fading to dark. Used when power is unplugged. */
    FADE_OUT,
}

data class AnimationPreset(
    val id: String,
    val kind: PresetKind,
    val durationMillis: Long,
)

object AnimationPresets {

    val FILL_UP = AnimationPreset(id = "fill_up", kind = PresetKind.FILL_UP, durationMillis = 1200)
    val WAVE = AnimationPreset(id = "wave", kind = PresetKind.WAVE, durationMillis = 1400)
    val BREATHE = AnimationPreset(id = "breathe", kind = PresetKind.BREATHE, durationMillis = 1600)
    val CHASE = AnimationPreset(id = "chase", kind = PresetKind.CHASE, durationMillis = 1000)
    val FLASH = AnimationPreset(id = "flash", kind = PresetKind.FLASH, durationMillis = 600)
    val FADE_OUT = AnimationPreset(id = "fade_out", kind = PresetKind.FADE_OUT, durationMillis = 700)

    val all: List<AnimationPreset> = listOf(FILL_UP, WAVE, BREATHE, CHASE, FLASH, FADE_OUT)

    /** Presets offered as a plug-in animation. [FADE_OUT] is reserved for unplugging. */
    val selectable: List<AnimationPreset> = listOf(FILL_UP, WAVE, BREATHE, CHASE, FLASH)

    fun byId(id: String): AnimationPreset? = all.firstOrNull { it.id == id }
}
```

- [ ] **Step 4: Реализовать рендерер пресетов**

`PresetFrameSource.kt`:

```kotlin
package com.swrneko.glyphmeter.animation

import com.swrneko.glyphmeter.model.DeviceLayout
import com.swrneko.glyphmeter.model.GlyphFrameData
import com.swrneko.glyphmeter.model.Light
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Renders an [AnimationPreset] into frames.
 *
 * One renderer for all presets, because a preset is data. Every preset ends fully dark
 * so the meter can take over without a visible seam.
 */
class PresetFrameSource(
    private val preset: AnimationPreset,
    private val layout: DeviceLayout,
    private val brightness: Int,
) : FrameSource {

    override val durationMillis: Long get() = preset.durationMillis

    /** Segment order used by sweeps: the meter zone first, then the remaining zones. */
    private val sweepOrder: List<Int> = buildList {
        addAll(layout.meterZone.indices)
        for (zone in layout.zones) {
            if (zone.id != layout.meterZoneId) addAll(zone.indices)
        }
    }

    override fun frameAt(elapsedMillis: Long): GlyphFrameData {
        if (elapsedMillis >= durationMillis) return GlyphFrameData.off(layout.segmentCount)

        val t = when {
            durationMillis <= 0L -> 1f
            else -> (elapsedMillis.toFloat() / durationMillis).coerceIn(0f, 1f)
        }
        val segments = IntArray(layout.segmentCount)
        val peak = brightness.coerceIn(0, Light.MAX)

        when (preset.kind) {
            PresetKind.FILL_UP -> renderFillUp(segments, t, peak)
            PresetKind.WAVE -> renderSweep(segments, t, peak, bandWidth = 5f, passes = 1)
            PresetKind.BREATHE -> renderBreathe(segments, t, peak)
            PresetKind.CHASE -> renderSweep(segments, t, peak, bandWidth = 2f, passes = 2)
            PresetKind.FLASH -> renderFlash(segments, t, peak)
            PresetKind.FADE_OUT -> renderFadeOut(segments, t, peak)
        }

        return GlyphFrameData(segments)
    }

    /** Fills the meter zone over the first 70 percent, then fades the whole thing out. */
    private fun renderFillUp(segments: IntArray, t: Float, peak: Int) {
        val meter = layout.meterZone.indices
        val fillPhase = 0.7f

        if (t <= fillPhase) {
            val filled = (t / fillPhase * meter.size).toInt().coerceAtMost(meter.size)
            for (i in 0 until filled) segments[meter[i]] = peak
        } else {
            val fade = 1f - (t - fillPhase) / (1f - fillPhase)
            val level = (peak * fade).roundToInt().coerceAtLeast(0)
            for (index in meter) segments[index] = level
        }
    }

    /** Moves a lit band of [bandWidth] segments along [sweepOrder], [passes] times. */
    private fun renderSweep(segments: IntArray, t: Float, peak: Int, bandWidth: Float, passes: Int) {
        val total = sweepOrder.size
        // Travel far enough past the end that the band leaves the strip before time runs out.
        val travel = (total + bandWidth) * passes
        val head = t * travel

        for ((position, index) in sweepOrder.withIndex()) {
            val offsetInPass = ((head - position) % (total + bandWidth) + (total + bandWidth)) % (total + bandWidth)
            if (offsetInPass in 0f..bandWidth) {
                val falloff = 1f - offsetInPass / bandWidth
                segments[index] = (peak * falloff).roundToInt().coerceIn(0, peak)
            }
        }
    }

    /** One half sine over the full duration, applied to every segment at once. */
    private fun renderBreathe(segments: IntArray, t: Float, peak: Int) {
        val level = (peak * sin(t * PI).toFloat()).roundToInt().coerceIn(0, peak)
        segments.fill(level)
    }

    /** Two pulses, with the trailing edge of the second landing exactly on darkness. */
    private fun renderFlash(segments: IntArray, t: Float, peak: Int) {
        val pulse = abs(sin(t * 2f * PI.toFloat()))
        val level = (peak * pulse).roundToInt().coerceIn(0, peak)
        segments.fill(level)
    }

    /** Linear fade from full to dark. */
    private fun renderFadeOut(segments: IntArray, t: Float, peak: Int) {
        val level = (peak * (1f - t)).roundToInt().coerceIn(0, peak)
        segments.fill(level)
    }
}
```

- [ ] **Step 5: Запустить тесты**

Run: `./gradlew :core:animation:test`
Expected: PASS.

Если `wave touches every zone` падает: это значит, что полоса не доходит до конца `sweepOrder` за отведённое время. Увеличить `travel` в `renderSweep`, а не ослаблять тест — тест проверяет ровно то поведение, ради которого пресет существует.

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "Пресеты анимаций подключения зарядки"
```

---

### Task 6: Слой железа — интерфейс, фейк и реализация на Glyph SDK

**Files:**
- Create: `core/hardware/src/main/kotlin/com/swrneko/glyphmeter/hardware/GlyphDisplay.kt`
- Create: `core/hardware/src/main/kotlin/com/swrneko/glyphmeter/hardware/RenderCapability.kt`
- Create: `core/hardware/src/main/kotlin/com/swrneko/glyphmeter/hardware/NothingGlyphDisplay.kt`
- Create: `core/hardware/src/main/kotlin/com/swrneko/glyphmeter/hardware/FakeGlyphDisplay.kt`
- Test: `core/hardware/src/test/kotlin/com/swrneko/glyphmeter/hardware/FakeGlyphDisplayTest.kt`

`FakeGlyphDisplay` лежит в `src/main`, а не в `src/test`, потому что им пользуются тесты модуля `:app`.

**Interfaces:**
- Consumes: `GlyphFrameData`, `DeviceLayout`.
- Produces:
  ```kotlin
  interface GlyphDisplay {
      val capability: StateFlow<RenderCapability>
      suspend fun connect(layout: DeviceLayout): Result<Unit>
      fun render(frame: GlyphFrameData)
      fun turnOff()
      fun disconnect()
  }
  enum class RenderCapability { UNKNOWN, PER_SEGMENT, STEPPED_ONLY, UNAVAILABLE }
  ```
  `FakeGlyphDisplay` дополнительно даёт `val rendered: List<GlyphFrameData>`, `var connectResult: Result<Unit>`, `var failPerSegment: Boolean`, `val isConnected: Boolean`, `val turnOffCount: Int`.

- [ ] **Step 1: Написать интерфейс и перечисление**

`RenderCapability.kt`:

```kotlin
package com.swrneko.glyphmeter.hardware

enum class RenderCapability {
    /** Not probed yet. */
    UNKNOWN,

    /** `buildChannel(index, light)` works: full sub-segment smoothness. */
    PER_SEGMENT,

    /** Per-segment brightness is gone; only whole-segment steps are possible. */
    STEPPED_ONLY,

    /** The Glyph service refused us. Nothing can be drawn. */
    UNAVAILABLE,
}
```

`GlyphDisplay.kt`:

```kotlin
package com.swrneko.glyphmeter.hardware

import com.swrneko.glyphmeter.model.DeviceLayout
import com.swrneko.glyphmeter.model.GlyphFrameData
import kotlinx.coroutines.flow.StateFlow

/**
 * The only thing in the project allowed to know that `com.nothing.ketchum` exists.
 *
 * Everything above this interface deals in frames of brightness values.
 */
interface GlyphDisplay {

    val capability: StateFlow<RenderCapability>

    /** Binds to the Glyph service and opens a session. Safe to call when already connected. */
    suspend fun connect(layout: DeviceLayout): Result<Unit>

    /** Shows one frame. Does nothing when disconnected. */
    fun render(frame: GlyphFrameData)

    fun turnOff()

    fun disconnect()
}
```

- [ ] **Step 2: Написать падающий тест на фейк**

`FakeGlyphDisplayTest.kt`:

```kotlin
package com.swrneko.glyphmeter.hardware

import com.swrneko.glyphmeter.model.DeviceLayout
import com.swrneko.glyphmeter.model.GlyphFrameData
import com.swrneko.glyphmeter.model.GlyphZone
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeGlyphDisplayTest {

    private val layout = DeviceLayout(
        deviceId = "test",
        displayName = "Test",
        segmentCount = 4,
        zones = listOf(GlyphZone(id = "C", indices = listOf(0, 1, 2, 3))),
        meterZoneId = "C",
        progressAnchorIndex = 0,
        hardwareVerified = false,
    )

    @Test
    fun `it records the frames it was asked to show`() = runTest {
        val display = FakeGlyphDisplay()
        display.connect(layout)

        display.render(GlyphFrameData(intArrayOf(1, 2, 3, 4)))
        display.render(GlyphFrameData(intArrayOf(5, 6, 7, 8)))

        assertEquals(
            listOf(
                GlyphFrameData(intArrayOf(1, 2, 3, 4)),
                GlyphFrameData(intArrayOf(5, 6, 7, 8)),
            ),
            display.rendered,
        )
    }

    @Test
    fun `it ignores frames while disconnected`() {
        val display = FakeGlyphDisplay()

        display.render(GlyphFrameData(intArrayOf(1, 2, 3, 4)))

        assertTrue(display.rendered.isEmpty())
    }

    @Test
    fun `a successful connect reports per segment support`() = runTest {
        val display = FakeGlyphDisplay()

        val result = display.connect(layout)

        assertTrue(result.isSuccess)
        assertTrue(display.isConnected)
        assertEquals(RenderCapability.PER_SEGMENT, display.capability.value)
    }

    @Test
    fun `losing per segment support downgrades the capability`() = runTest {
        val display = FakeGlyphDisplay()
        display.failPerSegment = true

        display.connect(layout)

        assertEquals(RenderCapability.STEPPED_ONLY, display.capability.value)
    }

    @Test
    fun `a refused connect reports the failure and stays unavailable`() = runTest {
        val display = FakeGlyphDisplay()
        display.connectResult = Result.failure(IllegalStateException("no glyph service"))

        val result = display.connect(layout)

        assertTrue(result.isFailure)
        assertFalse(display.isConnected)
        assertEquals(RenderCapability.UNAVAILABLE, display.capability.value)
    }

    @Test
    fun `disconnect stops recording and counts a turn off`() = runTest {
        val display = FakeGlyphDisplay()
        display.connect(layout)

        display.turnOff()
        display.disconnect()
        display.render(GlyphFrameData(intArrayOf(1, 1, 1, 1)))

        assertEquals(1, display.turnOffCount)
        assertFalse(display.isConnected)
        assertTrue(display.rendered.isEmpty())
    }
}
```

- [ ] **Step 3: Запустить и убедиться, что падает**

Run: `./gradlew :core:hardware:test`
Expected: FAIL, `Unresolved reference: FakeGlyphDisplay`.

- [ ] **Step 4: Реализовать фейк**

`FakeGlyphDisplay.kt`:

```kotlin
package com.swrneko.glyphmeter.hardware

import com.swrneko.glyphmeter.model.DeviceLayout
import com.swrneko.glyphmeter.model.GlyphFrameData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory [GlyphDisplay] for tests.
 *
 * Lives in main rather than test sources because the app module's tests need it too.
 */
class FakeGlyphDisplay : GlyphDisplay {

    private val _capability = MutableStateFlow(RenderCapability.UNKNOWN)
    override val capability: StateFlow<RenderCapability> = _capability.asStateFlow()

    private val _rendered = mutableListOf<GlyphFrameData>()
    val rendered: List<GlyphFrameData> get() = _rendered.toList()

    var isConnected: Boolean = false
        private set

    var turnOffCount: Int = 0
        private set

    /** Result the next [connect] should produce. */
    var connectResult: Result<Unit> = Result.success(Unit)

    /** When true, a successful connect reports [RenderCapability.STEPPED_ONLY]. */
    var failPerSegment: Boolean = false

    fun clearRendered() = _rendered.clear()

    override suspend fun connect(layout: DeviceLayout): Result<Unit> {
        if (connectResult.isFailure) {
            isConnected = false
            _capability.value = RenderCapability.UNAVAILABLE
            return connectResult
        }
        isConnected = true
        _capability.value =
            if (failPerSegment) RenderCapability.STEPPED_ONLY else RenderCapability.PER_SEGMENT
        return connectResult
    }

    override fun render(frame: GlyphFrameData) {
        if (isConnected) _rendered += frame
    }

    override fun turnOff() {
        turnOffCount++
        _rendered.clear()
    }

    override fun disconnect() {
        isConnected = false
        _capability.value = RenderCapability.UNKNOWN
    }
}
```

- [ ] **Step 5: Запустить тесты фейка**

Run: `./gradlew :core:hardware:test`
Expected: PASS.

- [ ] **Step 6: Реализовать настоящий слой на Glyph SDK**

`NothingGlyphDisplay.kt`. Автоматических тестов у него нет: он проверяется на устройстве в Task 14. Все факты об SDK взяты из раздела «Проверенные факты» этого плана.

```kotlin
package com.swrneko.glyphmeter.hardware

import android.content.Context
import android.util.Log
import com.nothing.ketchum.Glyph
import com.nothing.ketchum.GlyphException
import com.nothing.ketchum.GlyphFrame
import com.nothing.ketchum.GlyphManager
import com.swrneko.glyphmeter.model.DeviceLayout
import com.swrneko.glyphmeter.model.GlyphFrameData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

private const val TAG = "NothingGlyphDisplay"
private const val SERVICE_CONNECT_TIMEOUT_MS = 5_000L

/**
 * Real [GlyphDisplay], backed by `glyph-matrix-sdk-2.0.aar`.
 *
 * A frame maps onto the SDK one index at a time: `buildChannel(i, light)` performs
 * `channel.set(i, light)` internally, so [GlyphFrameData] needs no translation.
 *
 * The per-segment overload is undocumented. If a Nothing OS update removes it, the
 * first render throws and this class permanently downgrades to
 * [RenderCapability.STEPPED_ONLY] rather than going dark.
 */
class NothingGlyphDisplay(private val context: Context) : GlyphDisplay {

    private val _capability = MutableStateFlow(RenderCapability.UNKNOWN)
    override val capability: StateFlow<RenderCapability> = _capability.asStateFlow()

    private var manager: GlyphManager? = null
    private var layout: DeviceLayout? = null
    private var sessionOpen = false

    override suspend fun connect(layout: DeviceLayout): Result<Unit> {
        if (sessionOpen && this.layout == layout) return Result.success(Unit)

        this.layout = layout
        val deviceId = resolveDeviceId(layout)
            ?: return fail("unsupported device id ${layout.deviceId}")

        val instance = GlyphManager.getInstance(context.applicationContext)
            ?: return fail("GlyphManager.getInstance returned null")

        val bound = withTimeoutOrNull(SERVICE_CONNECT_TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                instance.init(object : GlyphManager.Callback {
                    override fun onServiceConnected(name: android.content.ComponentName?) {
                        if (continuation.isActive) continuation.resume(true)
                    }

                    override fun onServiceDisconnected(name: android.content.ComponentName?) {
                        sessionOpen = false
                        _capability.value = RenderCapability.UNAVAILABLE
                    }
                })
            }
        } ?: return fail("timed out waiting for the Glyph service")

        if (!bound) return fail("Glyph service refused the connection")

        if (!instance.register(deviceId)) {
            return fail("register($deviceId) was rejected; check the Glyph permission and debug mode")
        }

        return try {
            instance.openSession()
            manager = instance
            sessionOpen = true
            _capability.value = RenderCapability.PER_SEGMENT
            Result.success(Unit)
        } catch (e: GlyphException) {
            fail("openSession failed: ${e.message}")
        }
    }

    override fun render(frame: GlyphFrameData) {
        val manager = manager ?: return
        val layout = layout ?: return
        if (!sessionOpen) return
        if (frame.size != layout.segmentCount) {
            Log.w(TAG, "frame of ${frame.size} segments does not fit ${layout.segmentCount}")
            return
        }

        when (_capability.value) {
            RenderCapability.PER_SEGMENT -> renderPerSegment(manager, frame, layout)
            RenderCapability.STEPPED_ONLY -> renderStepped(manager, frame, layout)
            else -> Unit
        }
    }

    private fun renderPerSegment(manager: GlyphManager, frame: GlyphFrameData, layout: DeviceLayout) {
        try {
            val builder = manager.glyphFrameBuilder
            for (index in 0 until layout.segmentCount) {
                builder.buildChannel(index, frame[index])
            }
            manager.toggle(builder.build())
        } catch (e: Throwable) {
            // The undocumented overload is gone, or the channel list is a different size.
            Log.w(TAG, "per-segment rendering failed, falling back to stepped output", e)
            _capability.value = RenderCapability.STEPPED_ONLY
            renderStepped(manager, frame, layout)
        }
    }

    private fun renderStepped(manager: GlyphManager, frame: GlyphFrameData, layout: DeviceLayout) {
        val meter = layout.meterZone.indices
        val lit = meter.count { frame[it] > 0 }
        val percent = (lit * 100) / meter.size

        try {
            val builder = manager.glyphFrameBuilder
            builder.buildChannel(layout.progressAnchorIndex)
            manager.displayProgress(builder.build(), percent)
        } catch (e: Throwable) {
            Log.e(TAG, "stepped rendering failed too", e)
            _capability.value = RenderCapability.UNAVAILABLE
        }
    }

    override fun turnOff() {
        runCatching { manager?.turnOff() }
            .onFailure { Log.w(TAG, "turnOff failed", it) }
    }

    override fun disconnect() {
        runCatching {
            if (sessionOpen) manager?.closeSession()
            manager?.unInit()
        }.onFailure { Log.w(TAG, "disconnect failed", it) }

        sessionOpen = false
        manager = null
        _capability.value = RenderCapability.UNKNOWN
    }

    private fun fail(reason: String): Result<Unit> {
        Log.w(TAG, reason)
        sessionOpen = false
        _capability.value = RenderCapability.UNAVAILABLE
        return Result.failure(IllegalStateException(reason))
    }

    private fun resolveDeviceId(layout: DeviceLayout): String? = when (layout.deviceId) {
        "24111" -> Glyph.DEVICE_24111
        else -> null
    }
}
```

Замечание по стёртому режиму: `renderStepped` пересчитывает процент из кадра, а не берёт уровень заряда. Это сделано намеренно — слой железа не знает про батарею, он умеет только показывать кадры.

Замечание про `displayProgress`: диапазон аргумента не подтверждён. Здесь передаётся 0..100. Если на устройстве шкала окажется неверной, поправить именно здесь и записать выясненное в спеку.

- [ ] **Step 7: Собрать модуль**

Run: `./gradlew :core:hardware:assembleDebug :core:hardware:test`
Expected: BUILD SUCCESSFUL, тесты фейка проходят.

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "Слой железа: интерфейс дисплея, фейк и реализация на Glyph SDK"
```

---

### Task 7: Источник состояния зарядки

**Files:**
- Create: `app/src/main/kotlin/com/swrneko/glyphmeter/charging/ChargingState.kt`
- Create: `app/src/main/kotlin/com/swrneko/glyphmeter/charging/ChargingStateSource.kt`
- Create: `app/src/main/kotlin/com/swrneko/glyphmeter/charging/SystemChargingStateSource.kt`
- Create: `app/src/main/kotlin/com/swrneko/glyphmeter/charging/FakeChargingStateSource.kt`
- Test: `app/src/test/kotlin/com/swrneko/glyphmeter/charging/ChargingStateTest.kt`

**Interfaces:**
- Consumes: ничего из предыдущих задач.
- Produces:
  ```kotlin
  enum class PowerSource { NONE, WIRED, WIRELESS, DOCK }
  data class ChargingState(val isCharging: Boolean, val level: Float, val source: PowerSource)
  interface ChargingStateSource { val state: Flow<ChargingState> }
  ```
  `ChargingState.Companion.fromBatteryIntent(intent: Intent): ChargingState`. `FakeChargingStateSource` с `suspend fun emit(state: ChargingState)`.

- [ ] **Step 1: Написать падающий тест**

`ChargingStateTest.kt` использует Robolectric, потому что разбирается `Intent` из `BatteryManager`.

```kotlin
package com.swrneko.glyphmeter.charging

import android.content.Intent
import android.os.BatteryManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ChargingStateTest {

    private fun batteryIntent(
        level: Int,
        scale: Int = 100,
        status: Int = BatteryManager.BATTERY_STATUS_CHARGING,
        plugged: Int = BatteryManager.BATTERY_PLUGGED_AC,
    ): Intent = Intent(Intent.ACTION_BATTERY_CHANGED).apply {
        putExtra(BatteryManager.EXTRA_LEVEL, level)
        putExtra(BatteryManager.EXTRA_SCALE, scale)
        putExtra(BatteryManager.EXTRA_STATUS, status)
        putExtra(BatteryManager.EXTRA_PLUGGED, plugged)
    }

    @Test
    fun `it reads the level as a fraction of the scale`() {
        val state = ChargingState.fromBatteryIntent(batteryIntent(level = 63))

        assertEquals(0.63f, state.level, 0.001f)
    }

    @Test
    fun `it copes with a scale that is not one hundred`() {
        val state = ChargingState.fromBatteryIntent(batteryIntent(level = 128, scale = 256))

        assertEquals(0.5f, state.level, 0.001f)
    }

    @Test
    fun `a missing scale does not produce a division by zero`() {
        val intent = Intent(Intent.ACTION_BATTERY_CHANGED).apply {
            putExtra(BatteryManager.EXTRA_LEVEL, 50)
            putExtra(BatteryManager.EXTRA_SCALE, 0)
        }

        val state = ChargingState.fromBatteryIntent(intent)

        assertEquals(0f, state.level, 0.001f)
    }

    @Test
    fun `a full battery on the charger still counts as charging`() {
        val state = ChargingState.fromBatteryIntent(
            batteryIntent(level = 100, status = BatteryManager.BATTERY_STATUS_FULL),
        )

        assertTrue(state.isCharging)
    }

    @Test
    fun `an unplugged battery is not charging and has no source`() {
        val state = ChargingState.fromBatteryIntent(
            batteryIntent(
                level = 50,
                status = BatteryManager.BATTERY_STATUS_DISCHARGING,
                plugged = 0,
            ),
        )

        assertFalse(state.isCharging)
        assertEquals(PowerSource.NONE, state.source)
    }

    @Test
    fun `it tells wired from wireless from dock`() {
        assertEquals(
            PowerSource.WIRED,
            ChargingState.fromBatteryIntent(batteryIntent(50, plugged = BatteryManager.BATTERY_PLUGGED_AC)).source,
        )
        assertEquals(
            PowerSource.WIRED,
            ChargingState.fromBatteryIntent(batteryIntent(50, plugged = BatteryManager.BATTERY_PLUGGED_USB)).source,
        )
        assertEquals(
            PowerSource.WIRELESS,
            ChargingState.fromBatteryIntent(batteryIntent(50, plugged = BatteryManager.BATTERY_PLUGGED_WIRELESS)).source,
        )
        assertEquals(
            PowerSource.DOCK,
            ChargingState.fromBatteryIntent(batteryIntent(50, plugged = BatteryManager.BATTERY_PLUGGED_DOCK)).source,
        )
    }

    @Test
    fun `level is clamped into the valid range`() {
        val state = ChargingState.fromBatteryIntent(batteryIntent(level = 250, scale = 100))

        assertEquals(1f, state.level, 0.001f)
    }
}
```

- [ ] **Step 2: Запустить и убедиться, что падает**

Run: `./gradlew :app:testDebugUnitTest --tests '*ChargingStateTest*'`
Expected: FAIL, `Unresolved reference: ChargingState`.

Чтобы Robolectric работал, добавить в `app/build.gradle.kts` внутрь блока `android`:

```kotlin
testOptions {
    unitTests {
        isIncludeAndroidResources = true
    }
}
```

- [ ] **Step 3: Реализовать состояние и разбор Intent**

`ChargingState.kt`:

```kotlin
package com.swrneko.glyphmeter.charging

import android.content.Intent
import android.os.BatteryManager

enum class PowerSource { NONE, WIRED, WIRELESS, DOCK }

/**
 * Everything the app needs to know about the battery at one moment.
 *
 * [level] is a fraction in 0..1 rather than a percentage, because that is what the
 * meter renderer takes.
 */
data class ChargingState(
    val isCharging: Boolean,
    val level: Float,
    val source: PowerSource,
) {
    companion object {

        val Unknown = ChargingState(isCharging = false, level = 0f, source = PowerSource.NONE)

        fun fromBatteryIntent(intent: Intent): ChargingState {
            val rawLevel = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val level = if (rawLevel < 0 || scale <= 0) 0f else (rawLevel.toFloat() / scale).coerceIn(0f, 1f)

            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

            val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
            val source = when {
                !isCharging -> PowerSource.NONE
                plugged == BatteryManager.BATTERY_PLUGGED_WIRELESS -> PowerSource.WIRELESS
                plugged == BatteryManager.BATTERY_PLUGGED_DOCK -> PowerSource.DOCK
                plugged == BatteryManager.BATTERY_PLUGGED_AC || plugged == BatteryManager.BATTERY_PLUGGED_USB -> PowerSource.WIRED
                else -> PowerSource.NONE
            }

            return ChargingState(isCharging = isCharging, level = level, source = source)
        }
    }
}
```

`ChargingStateSource.kt`:

```kotlin
package com.swrneko.glyphmeter.charging

import kotlinx.coroutines.flow.Flow

/** Stream of battery states. Emits the current state immediately on collection. */
interface ChargingStateSource {
    val state: Flow<ChargingState>
}
```

`SystemChargingStateSource.kt`:

```kotlin
package com.swrneko.glyphmeter.charging

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SystemChargingStateSource @Inject constructor(
    private val context: Context,
) : ChargingStateSource {

    override val state: Flow<ChargingState> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                intent ?: return
                trySend(ChargingState.fromBatteryIntent(intent))
            }
        }

        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        // registerReceiver with ACTION_BATTERY_CHANGED returns the sticky current value,
        // so the flow emits the present state without waiting for a change.
        val sticky = context.registerReceiver(receiver, filter)
        if (sticky != null) trySend(ChargingState.fromBatteryIntent(sticky))

        awaitClose { context.unregisterReceiver(receiver) }
    }.distinctUntilChanged()
}
```

`FakeChargingStateSource.kt`:

```kotlin
package com.swrneko.glyphmeter.charging

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/** Test double letting a test drive battery events by hand. */
class FakeChargingStateSource : ChargingStateSource {

    private val _state = MutableSharedFlow<ChargingState>(replay = 1, extraBufferCapacity = 16)
    override val state: Flow<ChargingState> = _state

    suspend fun emit(state: ChargingState) = _state.emit(state)

    suspend fun emit(isCharging: Boolean, level: Float, source: PowerSource = PowerSource.WIRED) =
        emit(ChargingState(isCharging = isCharging, level = level, source = source))
}
```

- [ ] **Step 4: Запустить тесты**

Run: `./gradlew :app:testDebugUnitTest --tests '*ChargingStateTest*'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "Источник состояния зарядки"
```

---

### Task 8: Настройки на DataStore

**Files:**
- Create: `app/src/main/kotlin/com/swrneko/glyphmeter/settings/GlyphSettings.kt`
- Create: `app/src/main/kotlin/com/swrneko/glyphmeter/settings/SettingsRepository.kt`
- Create: `app/src/main/kotlin/com/swrneko/glyphmeter/settings/DataStoreSettingsRepository.kt`
- Create: `app/src/main/kotlin/com/swrneko/glyphmeter/settings/FakeSettingsRepository.kt`
- Test: `app/src/test/kotlin/com/swrneko/glyphmeter/settings/GlyphSettingsTest.kt`

**Interfaces:**
- Consumes: `AnimationPresets` (Task 5).
- Produces:
  ```kotlin
  enum class MeterMode { ALWAYS_ON, ON_EVENT }
  data class GlyphSettings(
      val enabled: Boolean,
      val meterMode: MeterMode,
      val brightness: Int,
      val showDurationMillis: Long,
      val repeatStepPercent: Int,
      val wiredPresetId: String,
      val wirelessPresetId: String,
      val fullPresetId: String,
      val dimWhenFaceUp: Boolean,
  ) { companion object { val Default: GlyphSettings } }
  interface SettingsRepository {
      val settings: Flow<GlyphSettings>
      suspend fun update(transform: (GlyphSettings) -> GlyphSettings)
  }
  ```

- [ ] **Step 1: Написать падающий тест**

`GlyphSettingsTest.kt`:

```kotlin
package com.swrneko.glyphmeter.settings

import com.swrneko.glyphmeter.animation.AnimationPresets
import com.swrneko.glyphmeter.model.Light
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GlyphSettingsTest {

    @Test
    fun `the defaults name presets that actually exist`() {
        val defaults = GlyphSettings.Default

        assertNotNull(AnimationPresets.byId(defaults.wiredPresetId))
        assertNotNull(AnimationPresets.byId(defaults.wirelessPresetId))
        assertNotNull(AnimationPresets.byId(defaults.fullPresetId))
    }

    @Test
    fun `the default brightness is legal and visible`() {
        val brightness = GlyphSettings.Default.brightness

        assertTrue(brightness in Light.MIN_VISIBLE..Light.MAX)
    }

    @Test
    fun `the default repeat step is a sane percentage`() {
        assertTrue(GlyphSettings.Default.repeatStepPercent in 1..50)
    }

    @Test
    fun `the fake repository starts at the defaults`() = runTest {
        val repository = FakeSettingsRepository()

        assertEquals(GlyphSettings.Default, repository.settings.first())
    }

    @Test
    fun `an update is visible to the next reader`() = runTest {
        val repository = FakeSettingsRepository()

        repository.update { it.copy(meterMode = MeterMode.ALWAYS_ON, brightness = 2000) }

        val stored = repository.settings.first()
        assertEquals(MeterMode.ALWAYS_ON, stored.meterMode)
        assertEquals(2000, stored.brightness)
    }
}
```

- [ ] **Step 2: Запустить и убедиться, что падает**

Run: `./gradlew :app:testDebugUnitTest --tests '*GlyphSettingsTest*'`
Expected: FAIL, `Unresolved reference: GlyphSettings`.

- [ ] **Step 3: Реализовать модель настроек и репозитории**

`GlyphSettings.kt`:

```kotlin
package com.swrneko.glyphmeter.settings

import com.swrneko.glyphmeter.animation.AnimationPresets
import com.swrneko.glyphmeter.model.Light

enum class MeterMode {
    /** Meter stays lit for as long as the phone is charging. */
    ALWAYS_ON,

    /** Meter appears on plug-in and after each step of charge, then fades. */
    ON_EVENT,
}

data class GlyphSettings(
    val enabled: Boolean,
    val meterMode: MeterMode,
    /** Peak segment brightness, 0..Light.MAX. */
    val brightness: Int,
    /** How long the meter stays up in [MeterMode.ON_EVENT]. */
    val showDurationMillis: Long,
    /** Charge gain, in percent, that re-triggers the meter in [MeterMode.ON_EVENT]. */
    val repeatStepPercent: Int,
    val wiredPresetId: String,
    val wirelessPresetId: String,
    val fullPresetId: String,
    /** Skip lighting up while the phone lies screen-up. */
    val dimWhenFaceUp: Boolean,
) {
    companion object {
        val Default = GlyphSettings(
            enabled = true,
            meterMode = MeterMode.ON_EVENT,
            brightness = Light.MAX,
            showDurationMillis = 5_000,
            repeatStepPercent = 5,
            wiredPresetId = AnimationPresets.FILL_UP.id,
            wirelessPresetId = AnimationPresets.WAVE.id,
            fullPresetId = AnimationPresets.FLASH.id,
            dimWhenFaceUp = false,
        )
    }
}
```

`SettingsRepository.kt`:

```kotlin
package com.swrneko.glyphmeter.settings

import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val settings: Flow<GlyphSettings>
    suspend fun update(transform: (GlyphSettings) -> GlyphSettings)
}
```

`DataStoreSettingsRepository.kt`:

```kotlin
package com.swrneko.glyphmeter.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.swrneko.glyphmeter.model.Light
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore by preferencesDataStore(name = "glyph_settings")

@Singleton
class DataStoreSettingsRepository @Inject constructor(
    private val context: Context,
) : SettingsRepository {

    private object Keys {
        val Enabled = booleanPreferencesKey("enabled")
        val MeterModeName = stringPreferencesKey("meter_mode")
        val Brightness = intPreferencesKey("brightness")
        val ShowDuration = longPreferencesKey("show_duration_ms")
        val RepeatStep = intPreferencesKey("repeat_step_percent")
        val WiredPreset = stringPreferencesKey("wired_preset")
        val WirelessPreset = stringPreferencesKey("wireless_preset")
        val FullPreset = stringPreferencesKey("full_preset")
        val DimWhenFaceUp = booleanPreferencesKey("dim_when_face_up")
    }

    override val settings: Flow<GlyphSettings> =
        context.settingsDataStore.data.map { it.toSettings() }

    override suspend fun update(transform: (GlyphSettings) -> GlyphSettings) {
        context.settingsDataStore.edit { preferences ->
            val updated = transform(preferences.toSettings())

            preferences[Keys.Enabled] = updated.enabled
            preferences[Keys.MeterModeName] = updated.meterMode.name
            preferences[Keys.Brightness] = updated.brightness.coerceIn(0, Light.MAX)
            preferences[Keys.ShowDuration] = updated.showDurationMillis.coerceAtLeast(0)
            preferences[Keys.RepeatStep] = updated.repeatStepPercent.coerceIn(1, 50)
            preferences[Keys.WiredPreset] = updated.wiredPresetId
            preferences[Keys.WirelessPreset] = updated.wirelessPresetId
            preferences[Keys.FullPreset] = updated.fullPresetId
            preferences[Keys.DimWhenFaceUp] = updated.dimWhenFaceUp
        }
    }

    private fun Preferences.toSettings(): GlyphSettings {
        val defaults = GlyphSettings.Default
        val modeName = this[Keys.MeterModeName]

        return GlyphSettings(
            enabled = this[Keys.Enabled] ?: defaults.enabled,
            meterMode = MeterMode.entries.firstOrNull { it.name == modeName } ?: defaults.meterMode,
            brightness = this[Keys.Brightness] ?: defaults.brightness,
            showDurationMillis = this[Keys.ShowDuration] ?: defaults.showDurationMillis,
            repeatStepPercent = this[Keys.RepeatStep] ?: defaults.repeatStepPercent,
            wiredPresetId = this[Keys.WiredPreset] ?: defaults.wiredPresetId,
            wirelessPresetId = this[Keys.WirelessPreset] ?: defaults.wirelessPresetId,
            fullPresetId = this[Keys.FullPreset] ?: defaults.fullPresetId,
            dimWhenFaceUp = this[Keys.DimWhenFaceUp] ?: defaults.dimWhenFaceUp,
        )
    }
}
```

`FakeSettingsRepository.kt`:

```kotlin
package com.swrneko.glyphmeter.settings

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class FakeSettingsRepository(
    initial: GlyphSettings = GlyphSettings.Default,
) : SettingsRepository {

    private val _settings = MutableStateFlow(initial)
    override val settings: Flow<GlyphSettings> = _settings.asStateFlow()

    override suspend fun update(transform: (GlyphSettings) -> GlyphSettings) {
        _settings.update(transform)
    }
}
```

- [ ] **Step 4: Запустить тесты**

Run: `./gradlew :app:testDebugUnitTest --tests '*GlyphSettingsTest*'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "Настройки приложения на DataStore"
```

---

### Task 9: Подсистема доступа к Glyph

**Files:**
- Create: `app/src/main/kotlin/com/swrneko/glyphmeter/access/GlyphAccessState.kt`
- Create: `app/src/main/kotlin/com/swrneko/glyphmeter/access/DebugModeWriter.kt`
- Create: `app/src/main/kotlin/com/swrneko/glyphmeter/access/SecureSettingsDebugModeWriter.kt`
- Create: `app/src/main/kotlin/com/swrneko/glyphmeter/access/ShizukuDebugModeWriter.kt`
- Create: `app/src/main/kotlin/com/swrneko/glyphmeter/access/GlyphAccessManager.kt`
- Test: `app/src/test/kotlin/com/swrneko/glyphmeter/access/GlyphAccessManagerTest.kt`

**Interfaces:**
- Consumes: ничего из предыдущих задач.
- Produces:
  ```kotlin
  enum class GlyphAccessState { CHECKING, WORKING, MANAGED_BY_APP, NEEDS_SETUP, UNSUPPORTED_DEVICE }
  interface DebugModeWriter {
      val isAvailable: Boolean
      fun isDebugModeOn(): Boolean
      fun enableDebugMode(): Boolean
  }
  class GlyphAccessManager(
      private val writers: List<DebugModeWriter>,
      private val isDeviceSupported: () -> Boolean,
      private val requiresDebugMode: () -> Boolean,
  ) { fun evaluate(): GlyphAccessState; val adbGrantCommand: String }
  ```

- [ ] **Step 1: Написать падающий тест**

`GlyphAccessManagerTest.kt`:

```kotlin
package com.swrneko.glyphmeter.access

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GlyphAccessManagerTest {

    private class StubWriter(
        override val isAvailable: Boolean,
        private var debugOn: Boolean,
        private val canEnable: Boolean,
    ) : DebugModeWriter {
        var enableCalls = 0
            private set

        override fun isDebugModeOn(): Boolean = debugOn

        override fun enableDebugMode(): Boolean {
            enableCalls++
            if (canEnable) debugOn = true
            return canEnable
        }
    }

    private fun manager(
        writers: List<DebugModeWriter>,
        supported: Boolean = true,
        needsDebugMode: Boolean = true,
    ) = GlyphAccessManager(
        writers = writers,
        isDeviceSupported = { supported },
        requiresDebugMode = { needsDebugMode },
    )

    @Test
    fun `an unsupported phone is reported before anything else is checked`() {
        val state = manager(writers = emptyList(), supported = false).evaluate()

        assertEquals(GlyphAccessState.UNSUPPORTED_DEVICE, state)
    }

    @Test
    fun `a phone that does not need debug mode just works`() {
        val state = manager(writers = emptyList(), needsDebugMode = false).evaluate()

        assertEquals(GlyphAccessState.WORKING, state)
    }

    @Test
    fun `debug mode already on without a writer counts as working`() {
        val writer = StubWriter(isAvailable = false, debugOn = true, canEnable = false)

        val state = manager(listOf(writer)).evaluate()

        assertEquals(GlyphAccessState.WORKING, state)
    }

    @Test
    fun `an available writer turns debug mode on by itself`() {
        val writer = StubWriter(isAvailable = true, debugOn = false, canEnable = true)

        val state = manager(listOf(writer)).evaluate()

        assertEquals(GlyphAccessState.MANAGED_BY_APP, state)
        assertEquals(1, writer.enableCalls)
    }

    @Test
    fun `with no usable writer the user is asked to set things up`() {
        val writer = StubWriter(isAvailable = false, debugOn = false, canEnable = false)

        val state = manager(listOf(writer)).evaluate()

        assertEquals(GlyphAccessState.NEEDS_SETUP, state)
        assertEquals(0, writer.enableCalls)
    }

    @Test
    fun `a writer that claims to be available but fails falls through to the next one`() {
        val broken = StubWriter(isAvailable = true, debugOn = false, canEnable = false)
        val working = StubWriter(isAvailable = true, debugOn = false, canEnable = true)

        val state = manager(listOf(broken, working)).evaluate()

        assertEquals(GlyphAccessState.MANAGED_BY_APP, state)
        assertEquals(1, broken.enableCalls)
        assertEquals(1, working.enableCalls)
    }

    @Test
    fun `every writer failing leaves the user with the setup instructions`() {
        val first = StubWriter(isAvailable = true, debugOn = false, canEnable = false)
        val second = StubWriter(isAvailable = true, debugOn = false, canEnable = false)

        val state = manager(listOf(first, second)).evaluate()

        assertEquals(GlyphAccessState.NEEDS_SETUP, state)
    }

    @Test
    fun `the adb command names the real package and permission`() {
        val command = manager(writers = emptyList()).adbGrantCommand

        assertTrue(command, command.contains("com.swrneko.glyphmeter"))
        assertTrue(command, command.contains("android.permission.WRITE_SECURE_SETTINGS"))
        assertTrue(command, command.startsWith("adb shell pm grant "))
    }
}
```

- [ ] **Step 2: Запустить и убедиться, что падает**

Run: `./gradlew :app:testDebugUnitTest --tests '*GlyphAccessManagerTest*'`
Expected: FAIL, `Unresolved reference: GlyphAccessManager`.

- [ ] **Step 3: Реализовать состояние и интерфейс писателя**

`GlyphAccessState.kt`:

```kotlin
package com.swrneko.glyphmeter.access

enum class GlyphAccessState {
    /** Not evaluated yet. */
    CHECKING,

    /** The Glyph interface answers without any setup. Nothing OS 4.0 and newer. */
    WORKING,

    /** The app holds WRITE_SECURE_SETTINGS and keeps debug mode on by itself, forever. */
    MANAGED_BY_APP,

    /** One-time setup is needed. Show the instructions. */
    NEEDS_SETUP,

    /** Not a Nothing phone this app knows how to drive. */
    UNSUPPORTED_DEVICE,
}
```

`DebugModeWriter.kt`:

```kotlin
package com.swrneko.glyphmeter.access

/** Something that can read and set the Glyph debug flag. */
interface DebugModeWriter {
    /** True when this writer currently has the rights it needs. */
    val isAvailable: Boolean

    fun isDebugModeOn(): Boolean

    /** Returns true when debug mode is on afterwards. */
    fun enableDebugMode(): Boolean
}
```

- [ ] **Step 4: Реализовать менеджер доступа**

`GlyphAccessManager.kt`:

```kotlin
package com.swrneko.glyphmeter.access

/**
 * Works out whether the Glyph interface is usable, and makes it usable when it can.
 *
 * The order matters. On Nothing OS 4.0 Nothing removed the developer-key restriction
 * entirely, so a phone that already works must never be shown adb instructions.
 */
class GlyphAccessManager(
    private val writers: List<DebugModeWriter>,
    private val isDeviceSupported: () -> Boolean,
    private val requiresDebugMode: () -> Boolean,
) {

    /** Command the user runs once, after which the app keeps debug mode on by itself. */
    val adbGrantCommand: String =
        "adb shell pm grant $PACKAGE_NAME android.permission.WRITE_SECURE_SETTINGS"

    fun evaluate(): GlyphAccessState {
        if (!isDeviceSupported()) return GlyphAccessState.UNSUPPORTED_DEVICE
        if (!requiresDebugMode()) return GlyphAccessState.WORKING

        if (writers.any { it.isDebugModeOn() }) return GlyphAccessState.WORKING

        for (writer in writers) {
            if (!writer.isAvailable) continue
            if (writer.enableDebugMode()) return GlyphAccessState.MANAGED_BY_APP
        }

        return GlyphAccessState.NEEDS_SETUP
    }

    private companion object {
        const val PACKAGE_NAME = "com.swrneko.glyphmeter"
    }
}
```

- [ ] **Step 5: Реализовать двух настоящих писателей**

`SecureSettingsDebugModeWriter.kt`:

```kotlin
package com.swrneko.glyphmeter.access

import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log
import javax.inject.Inject

internal const val GLYPH_DEBUG_SETTING = "nt_glyph_interface_debug_enable"

/**
 * Writes the flag directly, which needs WRITE_SECURE_SETTINGS.
 *
 * Android never grants that permission to an ordinary app, so the user grants it once
 * over adb. After that it is permanent, and the 48-hour expiry of debug mode stops
 * mattering because the app simply turns it back on.
 */
class SecureSettingsDebugModeWriter @Inject constructor(
    private val context: Context,
) : DebugModeWriter {

    override val isAvailable: Boolean
        get() = context.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) ==
            PackageManager.PERMISSION_GRANTED

    override fun isDebugModeOn(): Boolean =
        Settings.Global.getInt(context.contentResolver, GLYPH_DEBUG_SETTING, 0) == 1

    override fun enableDebugMode(): Boolean = try {
        Settings.Global.putInt(context.contentResolver, GLYPH_DEBUG_SETTING, 1)
        isDebugModeOn()
    } catch (e: SecurityException) {
        Log.w("SecureSettingsWriter", "no permission to write $GLYPH_DEBUG_SETTING", e)
        false
    }
}
```

`ShizukuDebugModeWriter.kt`:

```kotlin
package com.swrneko.glyphmeter.access

import android.content.pm.PackageManager
import android.util.Log
import rikka.shizuku.Shizuku
import javax.inject.Inject

/**
 * Fallback for users with no computer to hand.
 *
 * Shizuku is started once over wireless debugging from the phone itself, but it has to
 * be restarted after every reboot, which is why the adb grant is the primary route.
 */
class ShizukuDebugModeWriter @Inject constructor() : DebugModeWriter {

    override val isAvailable: Boolean
        get() = try {
            Shizuku.pingBinder() &&
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Throwable) {
            false
        }

    override fun isDebugModeOn(): Boolean = runShell("settings get global $GLYPH_DEBUG_SETTING")
        ?.trim() == "1"

    override fun enableDebugMode(): Boolean {
        runShell("settings put global $GLYPH_DEBUG_SETTING 1") ?: return false
        return isDebugModeOn()
    }

    private fun runShell(command: String): String? = try {
        val process = Shizuku.newProcess(arrayOf("sh", "-c", command), null, null)
        val output = process.inputStream.bufferedReader().readText()
        process.waitFor()
        output
    } catch (e: Throwable) {
        Log.w("ShizukuWriter", "shell command failed: $command", e)
        null
    }
}
```

Примечание: `Shizuku.newProcess` — скрытый API библиотеки и помечен как `@hide`. Если сборка на нём падает, заменить на `ShizukuBinderWrapper` поверх `IContentProvider`, либо оставить Shizuku-путь отключённым (`isAvailable = false`) и вести пользователя только по adb. Отсутствие Shizuku не блокирует релиз: основной путь от него не зависит.

- [ ] **Step 6: Запустить тесты**

Run: `./gradlew :app:testDebugUnitTest --tests '*GlyphAccessManagerTest*'`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "Подсистема доступа к Glyph и автовключение debug-режима"
```

---

### Task 10: Оркестратор показа

Сердце поведения. Чистый класс без Android, живущий в `:app`, но не трогающий `Context` — поэтому тестируется на фейках и виртуальном времени.

**Files:**
- Create: `app/src/main/kotlin/com/swrneko/glyphmeter/orchestration/GlyphOrchestrator.kt`
- Test: `app/src/test/kotlin/com/swrneko/glyphmeter/orchestration/GlyphOrchestratorTest.kt`

**Interfaces:**
- Consumes: `GlyphDisplay`, `RenderCapability` (Task 6); `ChargingStateSource`, `ChargingState`, `PowerSource` (Task 7); `SettingsRepository`, `GlyphSettings`, `MeterMode` (Task 8); `DeviceLayout`, `MeterRenderer`, `MeterTransition`, `PresetFrameSource`, `AnimationPresets` (Tasks 2–5).
- Produces: `class GlyphOrchestrator(display, chargingSource, settingsRepository, layout, frameIntervalMillis)` с методом `suspend fun run()`, который работает, пока жива корутина.

- [ ] **Step 1: Написать падающий тест**

`GlyphOrchestratorTest.kt`:

```kotlin
package com.swrneko.glyphmeter.orchestration

import app.cash.turbine.test
import com.swrneko.glyphmeter.charging.FakeChargingStateSource
import com.swrneko.glyphmeter.charging.PowerSource
import com.swrneko.glyphmeter.hardware.FakeGlyphDisplay
import com.swrneko.glyphmeter.layout.DeviceLayouts
import com.swrneko.glyphmeter.settings.FakeSettingsRepository
import com.swrneko.glyphmeter.settings.GlyphSettings
import com.swrneko.glyphmeter.settings.MeterMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GlyphOrchestratorTest {

    private val layout = DeviceLayouts.PHONE_3A
    private val meter = layout.meterZone.indices

    private fun orchestrator(
        display: FakeGlyphDisplay,
        charging: FakeChargingStateSource,
        settings: FakeSettingsRepository,
    ) = GlyphOrchestrator(
        display = display,
        chargingSource = charging,
        settingsRepository = settings,
        layout = layout,
        frameIntervalMillis = 16,
    )

    @Test
    fun `it connects to the display when it starts`() = runTest {
        val display = FakeGlyphDisplay()
        val job = launch { orchestrator(display, FakeChargingStateSource(), FakeSettingsRepository()).run() }
        runCurrent()

        assertTrue(display.isConnected)

        job.cancelAndJoin()
    }

    @Test
    fun `plugging in plays the wired preset`() = runTest {
        val display = FakeGlyphDisplay()
        val charging = FakeChargingStateSource()
        val settings = FakeSettingsRepository(GlyphSettings.Default.copy(meterMode = MeterMode.ON_EVENT))
        val job = launch { orchestrator(display, charging, settings).run() }
        runCurrent()

        charging.emit(isCharging = true, level = 0.5f, source = PowerSource.WIRED)
        advanceTimeBy(300)
        runCurrent()

        assertTrue("expected frames during the plug-in animation", display.rendered.isNotEmpty())

        job.cancelAndJoin()
    }

    @Test
    fun `in on event mode the meter goes dark after the show duration`() = runTest {
        val display = FakeGlyphDisplay()
        val charging = FakeChargingStateSource()
        val settings = FakeSettingsRepository(
            GlyphSettings.Default.copy(meterMode = MeterMode.ON_EVENT, showDurationMillis = 2_000),
        )
        val job = launch { orchestrator(display, charging, settings).run() }
        runCurrent()

        charging.emit(isCharging = true, level = 0.5f, source = PowerSource.WIRED)
        advanceTimeBy(30_000)
        runCurrent()

        assertTrue("meter should have been turned off", display.turnOffCount >= 1)

        job.cancelAndJoin()
    }

    @Test
    fun `in always on mode the meter keeps being redrawn`() = runTest {
        val display = FakeGlyphDisplay()
        val charging = FakeChargingStateSource()
        val settings = FakeSettingsRepository(GlyphSettings.Default.copy(meterMode = MeterMode.ALWAYS_ON))
        val job = launch { orchestrator(display, charging, settings).run() }
        runCurrent()

        charging.emit(isCharging = true, level = 0.5f, source = PowerSource.WIRED)
        advanceTimeBy(10_000)
        runCurrent()
        display.clearRendered()
        advanceTimeBy(1_000)
        runCurrent()

        assertTrue("always-on mode must keep drawing", display.rendered.isNotEmpty())

        job.cancelAndJoin()
    }

    @Test
    fun `unplugging turns the glyph off`() = runTest {
        val display = FakeGlyphDisplay()
        val charging = FakeChargingStateSource()
        val settings = FakeSettingsRepository(GlyphSettings.Default.copy(meterMode = MeterMode.ALWAYS_ON))
        val job = launch { orchestrator(display, charging, settings).run() }
        runCurrent()

        charging.emit(isCharging = true, level = 0.5f, source = PowerSource.WIRED)
        advanceTimeBy(3_000)
        runCurrent()

        val before = display.turnOffCount
        charging.emit(isCharging = false, level = 0.5f, source = PowerSource.NONE)
        advanceTimeBy(3_000)
        runCurrent()

        assertTrue("unplugging must end in darkness", display.turnOffCount > before)

        job.cancelAndJoin()
    }

    @Test
    fun `a disabled app draws nothing`() = runTest {
        val display = FakeGlyphDisplay()
        val charging = FakeChargingStateSource()
        val settings = FakeSettingsRepository(GlyphSettings.Default.copy(enabled = false))
        val job = launch { orchestrator(display, charging, settings).run() }
        runCurrent()

        charging.emit(isCharging = true, level = 0.5f, source = PowerSource.WIRED)
        advanceTimeBy(10_000)
        runCurrent()

        assertTrue(display.rendered.isEmpty())

        job.cancelAndJoin()
    }

    @Test
    fun `in on event mode a small charge gain does not retrigger the meter`() = runTest {
        val display = FakeGlyphDisplay()
        val charging = FakeChargingStateSource()
        val settings = FakeSettingsRepository(
            GlyphSettings.Default.copy(
                meterMode = MeterMode.ON_EVENT,
                showDurationMillis = 1_000,
                repeatStepPercent = 5,
            ),
        )
        val job = launch { orchestrator(display, charging, settings).run() }
        runCurrent()

        charging.emit(isCharging = true, level = 0.50f, source = PowerSource.WIRED)
        advanceTimeBy(20_000)
        runCurrent()
        display.clearRendered()

        charging.emit(isCharging = true, level = 0.52f, source = PowerSource.WIRED)
        advanceTimeBy(5_000)
        runCurrent()

        assertTrue("a two percent gain must stay quiet", display.rendered.isEmpty())

        job.cancelAndJoin()
    }

    @Test
    fun `in on event mode a full step of charge retriggers the meter`() = runTest {
        val display = FakeGlyphDisplay()
        val charging = FakeChargingStateSource()
        val settings = FakeSettingsRepository(
            GlyphSettings.Default.copy(
                meterMode = MeterMode.ON_EVENT,
                showDurationMillis = 1_000,
                repeatStepPercent = 5,
            ),
        )
        val job = launch { orchestrator(display, charging, settings).run() }
        runCurrent()

        charging.emit(isCharging = true, level = 0.50f, source = PowerSource.WIRED)
        advanceTimeBy(20_000)
        runCurrent()
        display.clearRendered()

        charging.emit(isCharging = true, level = 0.56f, source = PowerSource.WIRED)
        advanceTimeBy(1_000)
        runCurrent()

        assertTrue("a six percent gain must wake the meter", display.rendered.isNotEmpty())

        job.cancelAndJoin()
    }

    @Test
    fun `the last frame of a rise matches the new level`() = runTest {
        val display = FakeGlyphDisplay()
        val charging = FakeChargingStateSource()
        val settings = FakeSettingsRepository(GlyphSettings.Default.copy(meterMode = MeterMode.ALWAYS_ON))
        val job = launch { orchestrator(display, charging, settings).run() }
        runCurrent()

        charging.emit(isCharging = true, level = 1f, source = PowerSource.WIRED)
        advanceTimeBy(10_000)
        runCurrent()

        val last = display.rendered.last()
        for (index in meter) {
            assertTrue("segment $index should be lit at full charge", last[index] > 0)
        }

        job.cancelAndJoin()
    }
}
```

- [ ] **Step 2: Запустить и убедиться, что падает**

Run: `./gradlew :app:testDebugUnitTest --tests '*GlyphOrchestratorTest*'`
Expected: FAIL, `Unresolved reference: GlyphOrchestrator`.

- [ ] **Step 3: Реализовать оркестратор**

```kotlin
package com.swrneko.glyphmeter.orchestration

import com.swrneko.glyphmeter.animation.AnimationPreset
import com.swrneko.glyphmeter.animation.AnimationPresets
import com.swrneko.glyphmeter.animation.Easing
import com.swrneko.glyphmeter.animation.MeterTransition
import com.swrneko.glyphmeter.animation.PresetFrameSource
import com.swrneko.glyphmeter.charging.ChargingState
import com.swrneko.glyphmeter.charging.ChargingStateSource
import com.swrneko.glyphmeter.charging.PowerSource
import com.swrneko.glyphmeter.hardware.GlyphDisplay
import com.swrneko.glyphmeter.model.DeviceLayout
import com.swrneko.glyphmeter.settings.GlyphSettings
import com.swrneko.glyphmeter.settings.MeterMode
import com.swrneko.glyphmeter.settings.SettingsRepository
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlin.math.abs
import kotlin.math.roundToInt

private const val LEVEL_TRANSITION_MILLIS = 500L

/**
 * Decides what the Glyph shows and when.
 *
 * Deliberately free of Android types so it can be driven by fakes on virtual time.
 * Owns no clock of its own: every wait goes through [delay], which `runTest` controls.
 */
class GlyphOrchestrator(
    private val display: GlyphDisplay,
    private val chargingSource: ChargingStateSource,
    private val settingsRepository: SettingsRepository,
    private val layout: DeviceLayout,
    private val frameIntervalMillis: Long = 16,
) {

    private var shownLevel: Float = 0f
    private var lastTriggerLevel: Float? = null
    private var wasCharging = false

    suspend fun run() {
        display.connect(layout).onFailure { return }

        combine(chargingSource.state, settingsRepository.settings) { charging, settings ->
            charging to settings
        }.collect { (charging, settings) ->
            handle(charging, settings)
        }
    }

    private suspend fun handle(charging: ChargingState, settings: GlyphSettings) {
        if (!settings.enabled) {
            if (wasCharging) stop()
            wasCharging = false
            return
        }

        if (!charging.isCharging) {
            if (wasCharging) {
                play(PresetFrameSource(AnimationPresets.FADE_OUT, layout, settings.brightness))
                stop()
            }
            wasCharging = false
            lastTriggerLevel = null
            return
        }

        val justPlugged = !wasCharging
        wasCharging = true

        if (justPlugged) {
            shownLevel = 0f
            lastTriggerLevel = charging.level
            play(PresetFrameSource(presetFor(charging, settings), layout, settings.brightness))
            showMeter(charging, settings)
            return
        }

        when (settings.meterMode) {
            MeterMode.ALWAYS_ON -> showMeter(charging, settings)

            MeterMode.ON_EVENT -> {
                val since = lastTriggerLevel ?: charging.level
                val gainedPercent = ((charging.level - since) * 100).roundToInt()

                if (abs(gainedPercent) >= settings.repeatStepPercent) {
                    lastTriggerLevel = charging.level
                    showMeter(charging, settings)
                }
            }
        }
    }

    /** Animates the meter to [charging]'s level, then holds or fades depending on the mode. */
    private suspend fun showMeter(charging: ChargingState, settings: GlyphSettings) {
        val transition = MeterTransition(
            fromLevel = shownLevel,
            toLevel = charging.level,
            layout = layout,
            brightness = settings.brightness,
            durationMillis = LEVEL_TRANSITION_MILLIS,
            easing = Easing.EaseOutCubic,
        )
        play(transition)
        shownLevel = charging.level

        if (settings.meterMode == MeterMode.ON_EVENT) {
            hold(settings.showDurationMillis, transition)
            play(PresetFrameSource(AnimationPresets.FADE_OUT, layout, settings.brightness))
            stop()
            shownLevel = 0f
        }
    }

    /** Keeps the final frame on screen without spinning the CPU. */
    private suspend fun hold(durationMillis: Long, source: MeterTransition) {
        display.render(source.frameAt(source.durationMillis))
        delay(durationMillis)
    }

    private suspend fun play(source: com.swrneko.glyphmeter.animation.FrameSource) {
        var elapsed = 0L
        while (elapsed <= source.durationMillis && currentCoroutineContext().isActive) {
            display.render(source.frameAt(elapsed))
            delay(frameIntervalMillis)
            elapsed += frameIntervalMillis
        }
    }

    private fun stop() {
        display.turnOff()
    }

    private fun presetFor(charging: ChargingState, settings: GlyphSettings): AnimationPreset {
        val id = when {
            charging.level >= 1f -> settings.fullPresetId
            charging.source == PowerSource.WIRELESS -> settings.wirelessPresetId
            else -> settings.wiredPresetId
        }
        return AnimationPresets.byId(id) ?: AnimationPresets.FILL_UP
    }
}
```

- [ ] **Step 4: Запустить тесты**

Run: `./gradlew :app:testDebugUnitTest --tests '*GlyphOrchestratorTest*'`
Expected: PASS, все девять тестов.

Подсказка при отладке: `combine` перезапускает `handle` при КАЖДОМ изменении настроек, в том числе когда пользователь двигает ползунок яркости во время зарядки. Если тест `a small charge gain does not retrigger the meter` падает — причина, скорее всего, в том, что `handle` считает повторную эмиссию настроек новым событием. Состояние (`shownLevel`, `lastTriggerLevel`, `wasCharging`) специально живёт в полях класса, а не внутри `collect`, именно чтобы это пережить.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "Оркестратор показа шкалы и анимаций"
```

---

### Task 11: Foreground service и запуск при загрузке

**Files:**
- Create: `app/src/main/kotlin/com/swrneko/glyphmeter/service/GlyphMeterService.kt`
- Create: `app/src/main/kotlin/com/swrneko/glyphmeter/service/BootReceiver.kt`
- Create: `app/src/main/kotlin/com/swrneko/glyphmeter/di/AppModule.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: `GlyphOrchestrator` (Task 10), `NothingGlyphDisplay` (Task 6), `SystemChargingStateSource` (Task 7), `DataStoreSettingsRepository` (Task 8), `SecureSettingsDebugModeWriter` и `ShizukuDebugModeWriter` (Task 9), `DeviceLayouts` (Task 2).
- Produces: `GlyphMeterService.start(context)` и `GlyphMeterService.stop(context)`; Hilt-биндинги для `GlyphDisplay`, `ChargingStateSource`, `SettingsRepository`, `GlyphAccessManager`, `DeviceLayout`.

- [ ] **Step 1: Написать Hilt-модуль**

`AppModule.kt`:

```kotlin
package com.swrneko.glyphmeter.di

import android.content.Context
import android.os.Build
import com.swrneko.glyphmeter.access.DebugModeWriter
import com.swrneko.glyphmeter.access.GlyphAccessManager
import com.swrneko.glyphmeter.access.SecureSettingsDebugModeWriter
import com.swrneko.glyphmeter.access.ShizukuDebugModeWriter
import com.swrneko.glyphmeter.charging.ChargingStateSource
import com.swrneko.glyphmeter.charging.SystemChargingStateSource
import com.swrneko.glyphmeter.hardware.GlyphDisplay
import com.swrneko.glyphmeter.hardware.NothingGlyphDisplay
import com.swrneko.glyphmeter.layout.DeviceLayouts
import com.swrneko.glyphmeter.model.DeviceLayout
import com.swrneko.glyphmeter.settings.DataStoreSettingsRepository
import com.swrneko.glyphmeter.settings.SettingsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideGlyphDisplay(@ApplicationContext context: Context): GlyphDisplay =
        NothingGlyphDisplay(context)

    @Provides
    @Singleton
    fun provideChargingStateSource(@ApplicationContext context: Context): ChargingStateSource =
        SystemChargingStateSource(context)

    @Provides
    @Singleton
    fun provideSettingsRepository(@ApplicationContext context: Context): SettingsRepository =
        DataStoreSettingsRepository(context)

    /**
     * Layout of the phone we are running on, or null when it is not a Glyph device we know.
     *
     * Nothing exposes the internal model code through `Build.DEVICE`; Phone (3a) and
     * (3a) Pro both report a device the SDK calls 24111.
     */
    @Provides
    @Singleton
    fun provideDeviceLayout(): DeviceLayout? = detectLayout()

    @Provides
    @Singleton
    fun provideAccessManager(
        @ApplicationContext context: Context,
        layout: DeviceLayout?,
    ): GlyphAccessManager {
        val writers: List<DebugModeWriter> = listOf(
            SecureSettingsDebugModeWriter(context),
            ShizukuDebugModeWriter(),
        )
        return GlyphAccessManager(
            writers = writers,
            isDeviceSupported = { layout != null },
            // Nothing dropped the developer-key requirement on Nothing OS 4.0, which is
            // built on Android 16 (API 36). Below that, debug mode is still needed.
            requiresDebugMode = { Build.VERSION.SDK_INT < 36 },
        )
    }

    private fun detectLayout(): DeviceLayout? {
        val fingerprint = listOf(Build.DEVICE, Build.MODEL, Build.PRODUCT)
            .joinToString(" ")
            .lowercase()

        // Phone (3a) and (3a) Pro ship under the internal name A059.
        if (fingerprint.contains("a059") || fingerprint.contains("3a")) {
            return DeviceLayouts.PHONE_3A
        }
        return null
    }
}
```

Замечание: определение модели по `Build` — эвристика. В Task 14 её нужно проверить на реальном телефоне и при необходимости уточнить строку, а не расширять её наугад. Если попытка `register` в `NothingGlyphDisplay` падает, ошибка будет видна как `UNAVAILABLE`, а не как тихая поломка.

- [ ] **Step 2: Написать сервис**

`GlyphMeterService.kt`:

```kotlin
package com.swrneko.glyphmeter.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Notification
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.swrneko.glyphmeter.R
import com.swrneko.glyphmeter.charging.ChargingStateSource
import com.swrneko.glyphmeter.hardware.GlyphDisplay
import com.swrneko.glyphmeter.model.DeviceLayout
import com.swrneko.glyphmeter.orchestration.GlyphOrchestrator
import com.swrneko.glyphmeter.settings.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val CHANNEL_ID = "glyph_meter_service"
private const val NOTIFICATION_ID = 1

@AndroidEntryPoint
class GlyphMeterService : LifecycleService() {

    @Inject lateinit var display: GlyphDisplay
    @Inject lateinit var chargingSource: ChargingStateSource
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var layout: DeviceLayout

    private var job: Job? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForegroundCompat()

        job = lifecycleScope.launch {
            GlyphOrchestrator(
                display = display,
                chargingSource = chargingSource,
                settingsRepository = settingsRepository,
                layout = layout,
            ).run()
        }
    }

    override fun onDestroy() {
        job?.cancel()
        display.turnOff()
        display.disconnect()
        super.onDestroy()
    }

    private fun startForegroundCompat() {
        val notification: Notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.service_notification_title))
            .setContentText(getString(R.string.service_notification_text))
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .build()

        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            },
        )
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.service_channel_name),
            NotificationManager.IMPORTANCE_MIN,
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        fun start(context: Context) {
            context.startForegroundService(Intent(context, GlyphMeterService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, GlyphMeterService::class.java))
        }
    }
}
```

Если Hilt не может внедрить `DeviceLayout` (провайдер возвращает nullable) — сервис должен просто не стартовать на неподдерживаемом телефоне. Объявить в модуле отдельный `@Provides fun provideRequiredLayout(layout: DeviceLayout?): DeviceLayout = layout ?: DeviceLayouts.PHONE_3A` нельзя: это подменит проблему. Вместо этого вызывающая сторона (`MainActivity`, `BootReceiver`) проверяет состояние доступа и не запускает сервис, если оно `UNSUPPORTED_DEVICE`. В сервисе поле объявить как `@Inject lateinit var layoutOrNull: Provider<DeviceLayout?>` и выйти через `stopSelf()`, если он null.

`BootReceiver.kt`:

```kotlin
package com.swrneko.glyphmeter.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.swrneko.glyphmeter.access.GlyphAccessManager
import com.swrneko.glyphmeter.access.GlyphAccessState
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Re-arms the app after a reboot.
 *
 * Evaluating access here is what makes the 48-hour debug-mode expiry invisible: if the
 * app holds WRITE_SECURE_SETTINGS it simply switches the flag back on.
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var accessManager: GlyphAccessManager

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        when (accessManager.evaluate()) {
            GlyphAccessState.WORKING, GlyphAccessState.MANAGED_BY_APP -> GlyphMeterService.start(context)
            else -> Unit
        }
    }
}
```

- [ ] **Step 3: Дописать манифест и строки**

В `app/src/main/AndroidManifest.xml` внутрь `<application>`:

```xml
<service
    android:name=".service.GlyphMeterService"
    android:exported="false"
    android:foregroundServiceType="specialUse">
    <property
        android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
        android:value="Drives the Glyph interface while the phone is charging" />
</service>

<receiver
    android:name=".service.BootReceiver"
    android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.BOOT_COMPLETED" />
    </intent-filter>
</receiver>
```

В `strings.xml` добавить `service_notification_title`, `service_notification_text`, `service_channel_name`. Создать `app/src/main/res/drawable/ic_notification.xml` — простой векторный значок.

- [ ] **Step 4: Собрать**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Прогнать все тесты**

Run: `./gradlew test`
Expected: PASS, ничего не сломалось.

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "Foreground service, запуск при загрузке и внедрение зависимостей"
```

---

### Task 12: Экран онбординга и состояния доступа

**Files:**
- Create: `app/src/main/kotlin/com/swrneko/glyphmeter/ui/theme/Theme.kt`
- Create: `app/src/main/kotlin/com/swrneko/glyphmeter/ui/onboarding/OnboardingScreen.kt`
- Create: `app/src/main/kotlin/com/swrneko/glyphmeter/ui/onboarding/OnboardingViewModel.kt`
- Test: `app/src/androidTest/kotlin/com/swrneko/glyphmeter/ui/onboarding/OnboardingScreenTest.kt`

**Interfaces:**
- Consumes: `GlyphAccessManager`, `GlyphAccessState` (Task 9).
- Produces: `@Composable fun OnboardingScreen(state: GlyphAccessState, adbCommand: String, onCopyCommand: () -> Unit, onRecheck: () -> Unit, onContinue: () -> Unit)`; `GlyphMeterTheme`.

- [ ] **Step 1: Написать тему**

`Theme.kt`:

```kotlin
package com.swrneko.glyphmeter.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
fun GlyphMeterTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> darkColorScheme()
        else -> lightColorScheme()
    }

    MaterialTheme(colorScheme = colorScheme, content = content)
}
```

- [ ] **Step 2: Написать падающий UI-тест**

`OnboardingScreenTest.kt`:

```kotlin
package com.swrneko.glyphmeter.ui.onboarding

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.swrneko.glyphmeter.access.GlyphAccessState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class OnboardingScreenTest {

    @get:Rule val compose = createComposeRule()

    private val command = "adb shell pm grant com.swrneko.glyphmeter android.permission.WRITE_SECURE_SETTINGS"

    @Test
    fun a_working_phone_is_never_shown_the_adb_command() {
        compose.setContent {
            OnboardingScreen(
                state = GlyphAccessState.WORKING,
                adbCommand = command,
                onCopyCommand = {},
                onRecheck = {},
                onContinue = {},
            )
        }

        compose.onNodeWithTag("adb_command").assertDoesNotExist()
    }

    @Test
    fun a_phone_needing_setup_is_shown_the_adb_command() {
        compose.setContent {
            OnboardingScreen(
                state = GlyphAccessState.NEEDS_SETUP,
                adbCommand = command,
                onCopyCommand = {},
                onRecheck = {},
                onContinue = {},
            )
        }

        compose.onNodeWithTag("adb_command").assertIsDisplayed()
    }

    @Test
    fun copying_the_command_reports_back() {
        var copied = false
        compose.setContent {
            OnboardingScreen(
                state = GlyphAccessState.NEEDS_SETUP,
                adbCommand = command,
                onCopyCommand = { copied = true },
                onRecheck = {},
                onContinue = {},
            )
        }

        compose.onNodeWithTag("copy_command").performClick()

        assertTrue(copied)
    }

    @Test
    fun an_unsupported_phone_offers_no_way_forward() {
        compose.setContent {
            OnboardingScreen(
                state = GlyphAccessState.UNSUPPORTED_DEVICE,
                adbCommand = command,
                onCopyCommand = {},
                onRecheck = {},
                onContinue = {},
            )
        }

        compose.onNodeWithTag("continue").assertDoesNotExist()
        compose.onNodeWithTag("adb_command").assertDoesNotExist()
    }
}
```

- [ ] **Step 3: Запустить и убедиться, что падает**

Run: `./gradlew :app:connectedDebugAndroidTest --tests '*OnboardingScreenTest*'`
Expected: FAIL, `Unresolved reference: OnboardingScreen`. Требуется подключённый телефон или эмулятор.

- [ ] **Step 4: Реализовать экран**

`OnboardingScreen.kt`. Основные требования, которые проверяют тесты:

- Состояния `WORKING` и `MANAGED_BY_APP` НЕ показывают ничего про adb, только подтверждение и кнопку с `testTag("continue")`.
- Состояние `NEEDS_SETUP` показывает объяснение, блок команды с `testTag("adb_command")` в моноширинном стиле, кнопку копирования с `testTag("copy_command")`, кнопку повторной проверки, и отдельным свёрнутым блоком — альтернативу через Shizuku.
- Состояние `UNSUPPORTED_DEVICE` показывает только объяснение, без команд и без кнопки продолжения.
- Состояние `CHECKING` показывает индикатор загрузки.

Строить на `Scaffold`, `Column`, `Card`, `Text`, `Button`, `OutlinedButton`, `CircularProgressIndicator`. Текст команды выводить в `Card` с `MaterialTheme.typography.bodyMedium` и `fontFamily = FontFamily.Monospace`. Копирование — через `LocalClipboard`.

Ключевая формулировка для `NEEDS_SETUP`, которую нужно донести пользователю: команда выдаётся ОДИН раз, после неё приложение включает debug-режим само и возвращаться к компьютеру не придётся.

- [ ] **Step 5: Написать ViewModel**

`OnboardingViewModel.kt`: держит `StateFlow<GlyphAccessState>`, начинает с `CHECKING`, вызывает `accessManager.evaluate()` в `viewModelScope` на старте и по `onRecheck()`.

- [ ] **Step 6: Запустить тесты**

Run: `./gradlew :app:connectedDebugAndroidTest --tests '*OnboardingScreenTest*'`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "Экран онбординга и диагностика доступа к Glyph"
```

---

### Task 13: Главный экран и настройки

**Files:**
- Create: `app/src/main/kotlin/com/swrneko/glyphmeter/ui/main/MainScreen.kt`
- Create: `app/src/main/kotlin/com/swrneko/glyphmeter/ui/main/MainViewModel.kt`
- Create: `app/src/main/kotlin/com/swrneko/glyphmeter/ui/main/GlyphPreview.kt`
- Create: `app/src/main/kotlin/com/swrneko/glyphmeter/ui/settings/SettingsScreen.kt`
- Create: `app/src/main/kotlin/com/swrneko/glyphmeter/ui/GlyphMeterNavHost.kt`
- Modify: `app/src/main/kotlin/com/swrneko/glyphmeter/ui/MainActivity.kt`
- Test: `app/src/androidTest/kotlin/com/swrneko/glyphmeter/ui/main/GlyphPreviewTest.kt`

**Interfaces:**
- Consumes: `GlyphSettings`, `MeterMode`, `SettingsRepository` (Task 8); `MeterRenderer`, `DeviceLayouts` (Tasks 2–3); `AnimationPresets`, `PresetFrameSource` (Task 5); `GlyphMeterService` (Task 11); `GlyphMeterTheme` (Task 12).
- Produces: `@Composable fun GlyphPreview(frame: GlyphFrameData, layout: DeviceLayout, modifier: Modifier)`; `MainScreen`; `SettingsScreen`; `GlyphMeterNavHost`.

- [ ] **Step 1: Написать падающий тест на превью**

`GlyphPreviewTest.kt`:

```kotlin
package com.swrneko.glyphmeter.ui.main

import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.swrneko.glyphmeter.layout.DeviceLayouts
import com.swrneko.glyphmeter.layout.MeterRenderer
import com.swrneko.glyphmeter.model.Light
import org.junit.Rule
import org.junit.Test

class GlyphPreviewTest {

    @get:Rule val compose = createComposeRule()

    @Test
    fun it_draws_a_preview_for_a_known_layout() {
        val layout = DeviceLayouts.PHONE_3A

        compose.setContent {
            GlyphPreview(
                frame = MeterRenderer.smooth(0.63f, layout, Light.MAX),
                layout = layout,
                modifier = Modifier,
            )
        }

        compose.onNodeWithTag("glyph_preview").assertIsDisplayed()
    }
}
```

- [ ] **Step 2: Запустить и убедиться, что падает**

Run: `./gradlew :app:connectedDebugAndroidTest --tests '*GlyphPreviewTest*'`
Expected: FAIL, `Unresolved reference: GlyphPreview`.

- [ ] **Step 3: Реализовать превью**

`GlyphPreview.kt` — `Canvas` с `Modifier.testTag("glyph_preview")`, рисующий схематичную заднюю панель телефона: длинная полоса зоны C вдоль левого края, дуга зоны A справа, короткий блок зоны B снизу. Яркость каждого сегмента берётся из `frame[index]` и переводится в альфу цвета `MaterialTheme.colorScheme.primary` как `frame[index] / Light.MAX.toFloat()`.

Геометрию зон описать данными в том же файле, по одной записи на `zone.id`, чтобы добавление новой раскладки не требовало правки рисования.

- [ ] **Step 4: Реализовать главный экран**

`MainScreen.kt`:
- Крупный переключатель «включено».
- Живое превью: в режиме простоя показывает текущий уровень заряда, при нажатии «проиграть» прогоняет выбранный пресет через `PresetFrameSource` на экране.
- Выбор режима (`SegmentedButton` из Material 3, два варианта: постоянно и по событию).
- Текущее состояние доступа к Glyph одной строкой, со ссылкой на онбординг, если что-то не так.
- Предупреждение, если `display.capability` равен `STEPPED_ONLY`: плавность снижена, потому что Nothing убрала поканальную яркость.
- Кнопка перехода в настройки.

`MainViewModel.kt` — собирает `SettingsRepository.settings`, `ChargingStateSource.state` и `GlyphDisplay.capability` в один `StateFlow<MainUiState>`; управляет запуском и остановкой `GlyphMeterService`.

- [ ] **Step 5: Реализовать экран настроек**

`SettingsScreen.kt`:
- Ползунок яркости, `Light.MIN_VISIBLE..Light.MAX`.
- Длительность показа, активна только в режиме «по событию».
- Шаг повторного показа в процентах, активен только в режиме «по событию».
- Три выбора пресета: проводная зарядка, беспроводная, 100%. Список из `AnimationPresets.selectable`, у каждого кнопка предпросмотра.
- Переключатель гашения при телефоне экраном вверх.

Каждая настройка пишется через `settingsRepository.update { ... }`.

- [ ] **Step 6: Связать навигацию**

`GlyphMeterNavHost.kt` — `NavHost` с тремя маршрутами: `onboarding`, `main`, `settings`. Стартовый маршрут выбирается по состоянию доступа: `WORKING` и `MANAGED_BY_APP` ведут на `main`, всё остальное на `onboarding`.

`MainActivity.kt` переписать так, чтобы он оборачивал `GlyphMeterNavHost` в `GlyphMeterTheme`.

- [ ] **Step 7: Запустить тесты и собрать**

Run: `./gradlew :app:assembleDebug :app:connectedDebugAndroidTest`
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "Главный экран, настройки и превью глифа"
```

---

### Task 14: Проверка на устройстве

Единственная задача, которую нельзя выполнить без Phone (3a) или (3a) Pro, подключённого по USB. Закрывает все непроверенные факты из спеки.

**Files:**
- Modify: `docs/superpowers/specs/2026-09-18-glyph-charging-meter-design.md` (раздел «Непроверенное»)
- Modify: `core/hardware/src/main/kotlin/com/swrneko/glyphmeter/hardware/NothingGlyphDisplay.kt` (при необходимости)
- Modify: `app/src/main/kotlin/com/swrneko/glyphmeter/di/AppModule.kt` (при необходимости)

**Interfaces:**
- Consumes: всё предыдущее.
- Produces: подтверждённые факты; ни одного нового публичного интерфейса.

- [ ] **Step 1: Поставить приложение и выдать доступ**

```bash
adb devices
./gradlew :app:installDebug
adb shell pm grant com.swrneko.glyphmeter android.permission.WRITE_SECURE_SETTINGS
adb shell settings get global nt_glyph_interface_debug_enable
```

Последняя команда должна напечатать `1` после первого запуска приложения. Если печатает `null` или `0` — значит `SecureSettingsDebugModeWriter` не сработал, и это надо чинить до всего остального.

- [ ] **Step 2: Проверить определение модели**

```bash
adb shell getprop ro.product.device
adb shell getprop ro.product.model
adb logcat -d | grep -i 'NothingGlyphDisplay'
```

Сравнить с эвристикой `detectLayout()` в `AppModule`. Если строка не совпадает — поправить эвристику под РЕАЛЬНОЕ значение, а не расширять её догадками.

- [ ] **Step 3: Проверить поканальную яркость**

Подключить зарядку и посмотреть на глиф. Ожидается: шкала растёт непрерывно, ведущий сегмент светится частично. В логах не должно быть `per-segment rendering failed`.

Проверить `display.capability` через главный экран приложения: должен быть режим полной плавности, а не предупреждение о снижении.

- [ ] **Step 4: Выяснить диапазон аргумента displayProgress**

Временно зафиксировать `_capability.value = RenderCapability.STEPPED_ONLY` в `NothingGlyphDisplay.connect`, пересобрать, и при заряде около 50% посмотреть, сколько сегментов зажглось.

- 10 сегментов из 20 → аргумент 0..100, как сейчас в коде. Ничего не менять.
- 1–2 сегмента → аргумент ожидает другой масштаб. Подобрать множитель, поправить `renderStepped`.

Вернуть код, записать выясненное в спеку в раздел «Проверенные факты» и убрать соответствующую строку из «Непроверенного».

- [ ] **Step 5: Проверить работу из фона**

Заблокировать экран, свернуть приложение, подождать 10 минут при подключённой зарядке, убедиться, что шкала продолжает обновляться.

```bash
adb shell dumpsys activity services com.swrneko.glyphmeter | grep -A5 GlyphMeterService
```

Если система убила сервис — записать это в спеку как подтверждённое ограничение «only foreground applications» и обсудить с пользователем, что делать дальше. Не пытаться обойти это молча.

- [ ] **Step 6: Прогнать оба режима и все пресеты**

Проверить: подключение провода, подключение беспроводной зарядки (если есть), достижение 100%, отключение. Каждый из пяти пресетов. Оба режима индикатора.

- [ ] **Step 7: Обновить спеку**

Перенести всё выясненное из «Непроверенного» в «Проверенные факты», с датой и способом проверки.

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "Проверка на Phone (3a) и уточнение фактов об SDK"
```

---

### Task 15: Границы модулей, README и документация проекта

**Files:**
- Create: `app/src/test/kotlin/com/swrneko/glyphmeter/ModuleBoundariesTest.kt`
- Create: `README.md`
- Create: `AGENTS.md`
- Create: `.claude/CLAUDE.md` (симлинк на `../AGENTS.md`)
- Create: `.claude/rules/ARCHITECTURE.md`
- Create: `LICENSE`

**Interfaces:**
- Consumes: всё предыдущее.
- Produces: тест, запрещающий чистым модулям зависеть от Android; документацию репозитория.

- [ ] **Step 1: Написать тест на границы модулей**

`ModuleBoundariesTest.kt`:

```kotlin
package com.swrneko.glyphmeter

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the one architectural rule that is easy to break by accident: the pure layers
 * must stay pure, or they stop being testable without an emulator.
 */
class ModuleBoundariesTest {

    private val projectRoot: File = File(System.getProperty("user.dir")!!).parentFile!!

    private val pureModules = listOf("core/model", "core/layout", "core/animation")

    private val forbiddenImports = listOf(
        "import android.",
        "import androidx.",
        "import com.nothing.ketchum",
    )

    @Test
    fun `the pure modules never import android or the nothing sdk`() {
        val violations = mutableListOf<String>()

        for (module in pureModules) {
            val sources = File(projectRoot, "$module/src")
            if (!sources.isDirectory) continue

            sources.walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .forEach { file ->
                    for (line in file.readLines()) {
                        if (forbiddenImports.any { line.trimStart().startsWith(it) }) {
                            violations += "${file.relativeTo(projectRoot)}: ${line.trim()}"
                        }
                    }
                }
        }

        assertTrue(
            "pure modules must not depend on Android:\n${violations.joinToString("\n")}",
            violations.isEmpty(),
        )
    }
}
```

Если `projectRoot` вычисляется неверно (зависит от того, откуда Gradle запускает тест), заменить на передачу пути через системное свойство: в `app/build.gradle.kts` добавить в блок `testOptions.unitTests.all { it.systemProperty("project.root", rootDir.absolutePath) }`.

- [ ] **Step 2: Запустить тест**

Run: `./gradlew :app:testDebugUnitTest --tests '*ModuleBoundariesTest*'`
Expected: PASS. Если падает — это настоящее нарушение, чинить нужно код, а не тест.

- [ ] **Step 3: Написать README**

На русском. Разделы:
- Что делает приложение и почему оно нужно именно на Phone (3a): встроенного индикатора заряда там нет.
- Чем оно отличается от аналогов: субсегментная плавность вместо двадцати ступеней.
- Поддерживаемые устройства, с честной пометкой, какие проверены на железе.
- Установка: скачать APK из releases, один раз выдать разрешение командой adb, дальше ничего не требуется.
- Отдельно: на Nothing OS 4.0 и новее команда не нужна вообще.
- Альтернатива через Shizuku.
- Сборка из исходников.
- Лицензия.

- [ ] **Step 4: Написать AGENTS.md и ARCHITECTURE.md**

`AGENTS.md` — по шаблону `~/.claude/templates/AGENTS.md.template`: одна-две строки о проекте, команды сборки и тестов, указатели на карту и спеку.

`.claude/rules/ARCHITECTURE.md` — по контракту из пользовательских правил: стек на 5–10 строк, однострочники команд, tree верхнего уровня, индекс модулей. Обязательно зафиксировать: PK/UUID-политика к проекту неприменима, базы данных нет.

```bash
mkdir -p .claude/rules
ln -s ../AGENTS.md .claude/CLAUDE.md
```

- [ ] **Step 5: Добавить лицензию**

MIT или Apache 2.0 на выбор пользователя.

- [ ] **Step 6: Прогнать всё**

Run: `./gradlew test assembleDebug`
Expected: BUILD SUCCESSFUL, все тесты зелёные.

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "Границы модулей, README и документация проекта"
```

---

## Проверка плана относительно спеки

Пройдено по каждому разделу спеки.

| Раздел спеки | Где реализуется |
|---|---|
| 2. Факты об SDK | Раздел «Проверенные факты» этого плана, Task 6, Task 14 |
| 3. Дробный сегмент | Task 3 |
| 3. Плавный переход уровня | Task 4 |
| 4. Стек | Task 1 |
| 5.1 Слой железа | Task 6 |
| 5.2 Слой раскладки | Task 2, Task 3 |
| 5.3 Слой анимации | Task 4, Task 5 |
| 5.4 Слой оркестрации | Task 7, Task 10, Task 11 |
| 6.1 Два режима индикатора | Task 8 (настройка), Task 10 (поведение) |
| 6.2 Анимации и превью | Task 5, Task 13 |
| 6.3 Три экрана | Task 12, Task 13 |
| 7.1 Три состояния доступа | Task 9 |
| 7.2 Путь через adb | Task 9, Task 12 |
| 7.3 Путь через Shizuku | Task 9 |
| 7.4 Диагностика | Task 9, Task 13 |
| 8. Обработка ошибок | Task 6 (падение на ступенчатый режим), Task 9 (доступ), Task 11 (неподдерживаемое устройство) |
| 9. Тестирование | Тесты внутри Tasks 2–10, Task 15 (границы модулей) |
| 10. Границы первой версии | Ничего за их пределами в плане нет |

Пробелов не найдено.
