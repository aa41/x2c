plugins {
    id("com.android.library") version "8.11.1"
    id("dev.x2c.codegen")
    id("dev.x2c.activity-plugin")
}

dependencies {
    compileOnly(project(":fixtures:business-base"))
    compileOnly(project(":x2c-runtime"))
    compileOnly(project(":x2c-plugin-runtime"))
    compileOnly(project(":x2c-plugin-base"))
}

android {
    namespace = "dev.x2c.fixture.secondary"
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
}

x2c {
    // pluginMode defaults to true and can be supplied with -Px2c.pluginMode=true.
    // This fixture intentionally keeps plugin-only dependency and Manifest configuration.
    pluginId.set("dev.x2c.fixture.component-showcase")
    generatedPackage.set("dev.x2c.fixture.secondary.generated")
    assetLockFile.set(layout.projectDirectory.file("x2c-assets.lock.json"))
    minApi.set(21)
}
