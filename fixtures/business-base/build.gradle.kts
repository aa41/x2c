plugins {
    id("com.android.library") version "8.11.1"
}

dependencies {
    compileOnly(project(":x2c-plugin-api"))
}

android {
    namespace = "dev.x2c.fixture.businessbase"
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
