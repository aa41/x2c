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

tasks.withType<JavaCompile>().configureEach {
    options.release.set(8)
}

configurations.named("compileClasspath") {
    attributes.attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 11)
}

gradlePlugin {
    plugins {
        create("x2cActivityPlugin") {
            id = "dev.x2c.activity-plugin"
            implementationClass = "dev.x2c.plugin.gradle.X2cActivityPlugin"
            displayName = "X2C Android component transformer"
            description = "Transforms annotated Android components into host-container delegates."
        }
    }
}

dependencies {
    implementation(project(":"))
    implementation(project(":activity-compiler"))
    compileOnly("com.android.tools.build:gradle:8.11.1")
}
