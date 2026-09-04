plugins {
    id("com.android.library") version "8.11.1"
    `maven-publish`
}

group = "dev.x2c"
version = "0.1.0-SNAPSHOT"

android {
    namespace = "dev.x2c.plugin.runtime"
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

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("pluginRuntime") {
                from(components["release"])
                artifactId = "x2c-plugin-runtime"
            }
        }
    }
}

dependencies {
    api(project(":x2c-plugin-api"))
}

val compileRuntimeCoreTest by tasks.registering(JavaCompile::class) {
    source(
        layout.projectDirectory.file(
            "../x2c-plugin-api/src/main/java/dev/x2c/plugin/api/PluginLaunchMode.java",
        ),
        layout.projectDirectory.file(
            "src/main/java/dev/x2c/plugin/runtime/PluginSlotAllocator.java",
        ),
        layout.projectDirectory.file(
            "src/test/java/dev/x2c/plugin/runtime/PluginSlotAllocatorTest.java",
        ),
    )
    classpath = files()
    destinationDirectory.set(layout.buildDirectory.dir("runtime-core-test/classes"))
    options.release.set(8)
}

val runtimeCoreTest by tasks.registering(JavaExec::class) {
    group = "verification"
    dependsOn(compileRuntimeCoreTest)
    classpath = files(compileRuntimeCoreTest.flatMap { it.destinationDirectory })
    mainClass.set("dev.x2c.plugin.runtime.PluginSlotAllocatorTest")
}

tasks.named("check") {
    dependsOn(runtimeCoreTest)
}
