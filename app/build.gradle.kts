import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// Release signing credentials live in keystore.properties at the repository root, which is
// git-ignored. Without that file the release build is produced unsigned, so contributors can
// still build; only the maintainer can produce the signed APK published in GitHub releases.
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

android {
    namespace = "com.swrneko.glyphmeter"
    compileSdk = 37
    compileSdkMinor = 2
    defaultConfig {
        applicationId = "com.swrneko.glyphmeter"
        minSdk = 33
        targetSdk = 36
        versionCode = 4
        versionName = "0.2.1"
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
    signingConfigs {
        if (keystoreProperties.isNotEmpty()) {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            if (keystoreProperties.isNotEmpty()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}
dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:layout"))
    implementation(project(":core:animation"))
    implementation(project(":core:hardware"))
    // core:hardware only has the Glyph SDK as compileOnly (AGP 9.4 forbids bundling a local
    // .aar file inside another AAR), so the classes never reach a consumer's runtime
    // classpath on their own. The app module must supply them itself for NothingGlyphDisplay
    // to work on real hardware. Resolved through the same libs.glyph.matrix.sdk alias as
    // core:hardware — see gradle/libs.versions.toml and the Ivy repo in settings.gradle.kts
    // for the single source of truth on file location and version.
    implementation(libs.glyph.matrix.sdk) {
        artifact { type = "aar" }
    }

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
    testImplementation(libs.hilt.android.testing)
    kspTest(libs.hilt.android.compiler)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

// Guards against the failure mode where the implementation(libs.glyph.matrix.sdk) line above
// gets removed as "redundant" because :core:hardware already depends on it — it only has it
// as compileOnly, so that removal would compile fine and only blow up at runtime on real
// Nothing hardware with NoClassDefFoundError: com.nothing.ketchum.Glyph. Checked eagerly
// against the *declared* dependency (not a resolved configuration/artifact) so it runs during
// configuration, for every invocation, with no dependency resolution and no configuration-cache
// serialization concerns.
val glyphSdkDependency = libs.glyph.matrix.sdk.get()
val hasGlyphSdkDependency = configurations.getByName("implementation").dependencies.any { dependency ->
    dependency.group == glyphSdkDependency.group && dependency.name == glyphSdkDependency.name
}
check(hasGlyphSdkDependency) {
    "Glyph SDK (libs.glyph.matrix.sdk) is not declared as an implementation dependency of :app. " +
        ":core:hardware only depends on it as compileOnly (AGP forbids bundling a local .aar " +
        "inside another .aar), so :app must declare implementation(libs.glyph.matrix.sdk) itself " +
        "or NothingGlyphDisplay will fail at runtime with NoClassDefFoundError on real hardware."
}
