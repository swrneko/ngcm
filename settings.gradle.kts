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
        // Vendored Glyph SDK .aar (not published to Maven, see core/hardware/libs/).
        // Exposed as a synthetic Ivy repository so every module depends on the same
        // named coordinate (libs.glyph.matrix.sdk) instead of two hand-maintained
        // files(...) paths that Gradle has no way of keeping in sync.
        ivy {
            url = uri("core/hardware/libs")
            patternLayout {
                artifact("[module]-[revision].[ext]")
            }
            metadataSources {
                artifact()
            }
        }
    }
}
rootProject.name = "GlyphMeter"
include(":app", ":core:model", ":core:layout", ":core:animation", ":core:hardware")
