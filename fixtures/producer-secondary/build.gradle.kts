plugins {
    id("com.android.library") version "8.11.1"
    id("dev.x2c.codegen")
}

dependencies {
    compileOnly(project(":x2c-runtime"))
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
    pluginMode.set(true)
    generatedPackage.set("dev.x2c.fixture.secondary.generated")
    assetLockFile.set(layout.projectDirectory.file("x2c-assets.lock.json"))
    minApi.set(21)
}
