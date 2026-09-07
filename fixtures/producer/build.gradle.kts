plugins {
    id("com.android.library") version "8.11.1"
    id("dev.x2c.codegen")
    id("dev.x2c.activity-plugin")
}

val x2cPluginBuild = providers.gradleProperty("x2c.pluginMode")
    .map { value ->
        when (value.trim().lowercase()) {
            "true" -> true
            "false" -> false
            else -> error(
                "Gradle property x2c.pluginMode must be exactly true or false, but was: $value",
            )
        }
    }
    .orElse(true)
val isX2cPluginBuild = x2cPluginBuild.get()

dependencies {
    if (isX2cPluginBuild) {
        // The marked business base and host runtimes become a plugin-private/parent-first split.
        compileOnly(project(":fixtures:business-base"))
        compileOnly(project(":x2c-runtime"))
        compileOnly(project(":x2c-plugin-runtime"))
        compileOnly(project(":x2c-plugin-base"))
    } else {
        // A normal AAR exposes its native Activity base and X2C runtime to the host app.
        api(project(":fixtures:business-base"))
        api(project(":x2c-runtime"))
        // The marker is CLASS-retained and has no normal-mode runtime role.
        compileOnly(project(":x2c-plugin-api"))
    }
}

android {
    namespace = "dev.x2c.fixture.producer"
    compileSdk = 36

    defaultConfig {
        minSdk = 21
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        checkReleaseBuilds = false
    }

    sourceSets {
        getByName("main") {
            manifest.srcFile(
                if (isX2cPluginBuild) {
                    "src/main/AndroidManifest.xml"
                } else {
                    "src/normal/AndroidManifest.xml"
                },
            )
            if (!isX2cPluginBuild) {
                res.srcDir("src/normal/res")
            }
        }
    }
}

x2c {
    // One Provider controls generated IDs, dependency ownership, Manifest and artifact selection.
    pluginMode.set(x2cPluginBuild)
    pluginId.set("dev.x2c.fixture.layout-showcase")
    generatedPackage.set("dev.x2c.fixture.generated")
    assetLockFile.set(layout.projectDirectory.file("x2c-assets.lock.json"))
    customViewsFile.set(layout.projectDirectory.file("x2c-custom-views.json"))
    minApi.set(21)
}
