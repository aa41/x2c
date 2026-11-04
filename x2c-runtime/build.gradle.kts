plugins {
    `java-library`
    `maven-publish`
}

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

val runtimeCoreTest by tasks.registering(JavaExec::class) {
    group = "verification"
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("dev.x2c.runtime.PluginHostConfigTest")
}

// Minimal Android doubles live outside every published source set. Exercise registration and
// callback identity without executing the throwing methods in the compile-only android.jar.
val compileLifecycleTest by tasks.registering(JavaCompile::class) {
    dependsOn(tasks.classes)
    source(layout.projectDirectory.dir("src/lifecycleTest/java"))
    classpath = sourceSets.main.get().output
    destinationDirectory.set(layout.buildDirectory.dir("lifecycle-test/classes"))
}

val runtimeLifecycleTest by tasks.registering(JavaExec::class) {
    group = "verification"
    dependsOn(compileLifecycleTest)
    classpath = files(compileLifecycleTest.flatMap { it.destinationDirectory }) +
        sourceSets.main.get().output
    mainClass.set("dev.x2c.runtime.ActivityLifecycleRegistryTest")
}

tasks.check {
    dependsOn(runtimeCoreTest, runtimeLifecycleTest)
}

publishing {
    publications {
        create<MavenPublication>("runtime") {
            from(components["java"])
            artifactId = "x2c-runtime"
        }
    }
}
