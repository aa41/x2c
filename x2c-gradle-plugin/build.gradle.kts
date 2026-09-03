plugins {
    `java-gradle-plugin`
    `maven-publish`
}

group = "dev.x2c"
version = "0.1.0-SNAPSHOT"

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(17))
    withSourcesJar()
}

tasks.named<JavaCompile>("compileJava") {
    options.release.set(8)
}

// AGP 8.11's API artifact is Java 11 bytecode, but javac can safely use it while emitting the
// Java 8 plugin bytecode required by Gradle 5 / AGP 3.5.
configurations.named("compileClasspath") {
    attributes.attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 11)
}

gradlePlugin {
    plugins {
        create("x2cCodegen") {
            id = "dev.x2c.codegen"
            implementationClass = "dev.x2c.gradle.X2cCodegenPlugin"
            displayName = "X2C resource-to-class plugin"
            description = "Compiles an allowlisted Android resource subset into Java classes and JAR artifacts."
        }
    }
}

dependencies {
    // Only the legacy public API surface shared with AGP 3.5 is referenced by production code.
    // Compiling against the current baseline keeps dependency resolution reproducible while the
    // Java 8 bytecode/API contract is verified separately against AGP 3.5.4.
    compileOnly("com.android.tools.build:gradle:8.11.1")
    implementation("com.google.code.gson:gson:2.11.0")

}

val compilerTest by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Runs the dependency-free resource compiler test suite."
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("dev.x2c.compiler.ResourceCompilerSelfTest")
    javaLauncher.set(javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(17))
    })
}

tasks.test { enabled = false }
tasks.check { dependsOn(compilerTest) }
