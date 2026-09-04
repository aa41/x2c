plugins {
    id("com.android.library") version "8.11.1"
    id("dev.x2c.codegen")
    id("dev.x2c.activity-plugin")
}

dependencies {
    // The marked business base is selected from compileOnly and copied as a plugin-private closure.
    compileOnly(project(":fixtures:business-base"))
    // The dynamic payload compiles against the runtime but does not package it; the host owns it.
    compileOnly(project(":x2c-runtime"))
    compileOnly(project(":x2c-plugin-runtime"))
    compileOnly(project(":x2c-plugin-base"))
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
}

x2c {
    // pluginMode defaults to true and can be supplied with -Px2c.pluginMode=true.
    // false is rejected while the component transform plugin remains applied.
    pluginId.set("dev.x2c.fixture.layout-showcase")
    generatedPackage.set("dev.x2c.fixture.generated")
    assetLockFile.set(layout.projectDirectory.file("x2c-assets.lock.json"))
    customViewsFile.set(layout.projectDirectory.file("x2c-custom-views.json"))
    minApi.set(21)
}
