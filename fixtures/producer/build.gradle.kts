plugins {
    id("com.android.library") version "8.11.1"
    id("dev.x2c.codegen")
}

dependencies {
    // The dynamic payload compiles against the runtime but does not package it; the host owns it.
    compileOnly(project(":x2c-runtime"))
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
    pluginMode.set(true)
    generatedPackage.set("dev.x2c.fixture.generated")
    assetLockFile.set(layout.projectDirectory.file("x2c-assets.lock.json"))
    customViewsFile.set(layout.projectDirectory.file("x2c-custom-views.json"))
    minApi.set(21)
}
