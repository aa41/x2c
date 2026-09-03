import java.security.MessageDigest
import java.util.HexFormat
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

plugins {
    id("com.android.application") version "8.11.1"
}

dependencies {
    implementation(project(":x2c-runtime"))
}

abstract class PrepareX2cDemoPayload : DefaultTask() {
    @get:Inject
    abstract val fileSystemOperations: FileSystemOperations

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val inputJar: RegularFileProperty

    @get:Input
    abstract val payloadDirectoryName: Property<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun prepare() {
        val input = inputJar.get().asFile
        val root = outputDirectory.get().asFile
        val payloadDirectory = root.resolve(payloadDirectoryName.get())
        fileSystemOperations.delete {
            delete(root)
        }
        payloadDirectory.mkdirs()
        val payload = payloadDirectory.resolve("codegen-dex.jar")
        input.copyTo(payload, overwrite = true)
        val digest = MessageDigest.getInstance("SHA-256").digest(input.readBytes())
        payloadDirectory.resolve("codegen-dex.sha256").writeText(
            HexFormat.of().formatHex(digest) + "\n",
        )
    }
}

val producerDexJar = layout.projectDirectory.file("../producer/build/outputs/x2c/release/codegen-dex.jar")
val secondaryProducerDexJar = layout.projectDirectory.file(
    "../producer-secondary/build/outputs/x2c/release/codegen-dex.jar",
)

android {
    namespace = "dev.x2c.fixture.consumer"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.x2c.fixture.consumer"
        // Direct dynamic Activity instantiation uses framework AppComponentFactory (API 28+).
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    lint {
        checkReleaseBuilds = false
    }
}

androidComponents {
    onVariants(selector().all()) { variant ->
        val capitalized = variant.name.replaceFirstChar { it.uppercase() }
        val preparePayload = tasks.register<PrepareX2cDemoPayload>(
            "prepareX2c${capitalized}DemoPayload",
        ) {
            dependsOn(":fixtures:producer:x2cReleaseDexJar")
            inputJar.set(producerDexJar)
            payloadDirectoryName.set("x2c-demo")
            outputDirectory.set(layout.buildDirectory.dir("generated/x2cDemoAssets/${variant.name}"))
        }
        variant.sources.assets?.addGeneratedSourceDirectory(
            preparePayload,
            PrepareX2cDemoPayload::outputDirectory,
        )
        val prepareSecondaryPayload = tasks.register<PrepareX2cDemoPayload>(
            "prepareX2c${capitalized}SecondaryDemoPayload",
        ) {
            dependsOn(":fixtures:producer-secondary:x2cReleaseDexJar")
            inputJar.set(secondaryProducerDexJar)
            payloadDirectoryName.set("x2c-demo-secondary")
            outputDirectory.set(layout.buildDirectory.dir(
                "generated/x2cSecondaryDemoAssets/${variant.name}",
            ))
        }
        variant.sources.assets?.addGeneratedSourceDirectory(
            prepareSecondaryPayload,
            PrepareX2cDemoPayload::outputDirectory,
        )
    }
}
