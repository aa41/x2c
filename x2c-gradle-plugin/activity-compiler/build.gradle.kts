plugins {
    `java-library`
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

dependencies {
    implementation("org.ow2.asm:asm:9.8")
}

publishing {
    publications {
        create<MavenPublication>("activityCompiler") {
            from(components["java"])
            artifactId = "activity-compiler"
        }
    }
}

val compilerTest by tasks.registering(JavaExec::class) {
    group = "verification"
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("dev.x2c.plugin.compiler.ActivityJarTransformerSelfTest")
}

tasks.test { enabled = false }
tasks.check { dependsOn(compilerTest) }
