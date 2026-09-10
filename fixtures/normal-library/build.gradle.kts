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

// Intentionally no x2c block: code generation is opt-in. This AAR keeps its XML/resources and
// exercises the runtime SystemX2cResourceProvider fallback.
// An isolated demo switch avoids disabling the two dynamic plugins in the same APK.
val normalDemoGeneration = providers.gradleProperty("x2c.normal.enable")
if (normalDemoGeneration.isPresent) {
    x2c {
        pluginMode.set(false)
        x2cEnable.set(normalDemoGeneration.map { value ->
            when (value) {
                "true" -> true
                "false" -> false
                else -> error("x2c.normal.enable must be true or false")
            }
        })
    }
}
