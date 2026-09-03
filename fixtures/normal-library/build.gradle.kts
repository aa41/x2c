plugins {
    id("com.android.library") version "8.11.1"
    id("dev.x2c.codegen")
}

dependencies {
    // A normal AAR exposes the shared runtime transitively to its consuming application.
    api(project(":x2c-runtime"))
}

android {
    namespace = "dev.x2c.fixture.normal"
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
    // Integration mode keeps Android resources in the AAR and binds R2 to the final host IDs.
    pluginMode.set(false)
    generatedPackage.set("dev.x2c.fixture.normal.generated")
    minApi.set(21)
}
