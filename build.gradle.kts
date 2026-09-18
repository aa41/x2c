plugins {
    base
}

val x2cVersion = file("VERSION").readText().trim()
require(x2cVersion.matches(Regex("[0-9]+\\.[0-9]+\\.[0-9]+(?:-[A-Za-z0-9.-]+)?"))) {
    "Invalid X2C version in VERSION: $x2cVersion"
}

allprojects {
    group = "dev.x2c"
    version = x2cVersion
}

tasks.register("verifyX2c") {
    group = "verification"
    description = "Builds and verifies the class JAR, DEX JAR, and Android consumer."
    dependsOn(
        ":fixtures:producer:x2cReleaseJar",
        ":fixtures:producer:x2cReleaseDexJar",
        ":fixtures:producer-secondary:x2cReleaseJar",
        ":fixtures:producer-secondary:x2cReleaseDexJar",
        ":fixtures:business-base:assembleRelease",
        ":fixtures:consumer:assembleRelease",
        ":fixtures:normal-library:assembleRelease",
    )
}
