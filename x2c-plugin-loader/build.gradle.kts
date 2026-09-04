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
    api(project(":x2c-runtime"))
    implementation(project(":x2c-plugin-api"))
    compileOnly(files("$androidSdkDirectory/platforms/android-36/android.jar"))
}

val loaderTest by tasks.registering(JavaExec::class) {
    group = "verification"
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("dev.x2c.plugin.loader.PluginDescriptorSelfTest")
}

tasks.test { enabled = false }
tasks.check { dependsOn(loaderTest) }

publishing {
    publications {
        create<MavenPublication>("pluginLoader") {
            from(components["java"])
            artifactId = "x2c-plugin-loader"
        }
    }
}
