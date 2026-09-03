plugins {
    id("com.android.application") version "8.11.1"
}

dependencies {
    implementation(project(":fixtures:normal-library"))
}

android {
    namespace = "dev.x2c.fixture.normalapp"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.x2c.fixture.normalapp"
        minSdk = 21
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    lint {
        checkReleaseBuilds = false
    }
}
