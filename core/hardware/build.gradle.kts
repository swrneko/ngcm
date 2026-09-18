plugins {
    alias(libs.plugins.android.library)
}
android {
    namespace = "com.swrneko.glyphmeter.hardware"
    compileSdk = 37
    compileSdkMinor = 2
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
    api(project(":core:layout"))
    // Kept compileOnly: AGP 9.4 no longer allows a direct local .aar file dependency to be
    // packaged inside another AAR (bundleDebugAar fails with "Direct local .aar file
    // dependencies are not supported when building an AAR"). The application module
    // supplies the same artifact at runtime (see app/build.gradle.kts) — both modules
    // resolve libs.glyph.matrix.sdk, so there is exactly one place (gradle/libs.versions.toml
    // + the Ivy repo in settings.gradle.kts) that says where the vendored .aar lives and
    // which version it is.
    compileOnly(libs.glyph.matrix.sdk) {
        artifact { type = "aar" }
    }
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
