plugins {
    `java-library`
    `maven-publish`
}

group = "dev.x2c"
version = "0.1.0-SNAPSHOT"

val androidSdkDirectory = System.getenv("ANDROID_HOME")
    ?: System.getenv("ANDROID_SDK_ROOT")
    ?: "${System.getProperty("user.home")}/Library/Android/sdk"

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(17))
    withSourcesJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(8)
}

dependencies {
    compileOnly(files("$androidSdkDirectory/platforms/android-36/android.jar"))
}

publishing {
    publications {
        create<MavenPublication>("pluginApi") {
            from(components["java"])
            artifactId = "x2c-plugin-api"
        }
    }
}
