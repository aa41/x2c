pluginManagement {
    includeBuild("x2c-gradle-plugin")
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "com.android.application" || requested.id.id == "com.android.library") {
                useModule("com.android.tools.build:gradle:${requested.version ?: "8.11.1"}")
            }
        }
    }
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
    }
}

rootProject.name = "x2c-plugin"
include(
    ":x2c-runtime",
    ":fixtures:producer",
    ":fixtures:producer-secondary",
    ":fixtures:consumer",
    ":fixtures:normal-library",
    ":fixtures:normal-app",
)
