plugins {
    `java-library`
    `maven-publish`
}

group = "dev.x2c"
version = "0.1.0-SNAPSHOT"

val androidSdkDirectory = System.getenv("ANDROID_HOME")
    ?: System.getenv("ANDROID_SDK_ROOT")
    ?: "${System.getProperty("user.home")}/Library/Android/sdk"
val androidJar = file("$androidSdkDirectory/platforms/android-36/android.jar")

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(17))
    withSourcesJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(8)
}

dependencies {
    compileOnly(files(androidJar))
}

publishing {
    publications {
        create<MavenPublication>("runtime") {
            from(components["java"])
            artifactId = "x2c-runtime"
        }
    }
}
