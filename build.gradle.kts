plugins {
    base
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
