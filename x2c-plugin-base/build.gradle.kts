plugins {
    id("com.android.library") version "8.11.1"
    `maven-publish`
}

group = "dev.x2c"
version = "0.1.0-SNAPSHOT"

android {
    namespace = "dev.x2c.plugin.base"
    compileSdk = 36

    defaultConfig {
        minSdk = 21
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    lint {
        checkReleaseBuilds = false
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

dependencies {
    api(project(":x2c-plugin-runtime"))
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("pluginBase") {
                from(components["release"])
                artifactId = "x2c-plugin-base"
            }
        }
    }
}
