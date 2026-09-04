import java.security.MessageDigest
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64
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
    implementation(project(":fixtures:business-base"))
    implementation(project(":x2c-runtime"))
    implementation(project(":x2c-plugin-runtime"))
    implementation(project(":x2c-plugin-base"))
    implementation(project(":x2c-plugin-loader"))
}

abstract class PrepareX2cDemoPayload : DefaultTask() {
    companion object {
        // Test-only publisher key. Production signing keys must remain outside source control/CI logs.
        private const val TEST_PRIVATE_KEY = "MIIEvwIBADANBgkqhkiG9w0BAQEFAASCBKkwggSlAgEAAoIBAQC1yf95F8CtsF2C1lMO/rZtp7CNDgbYI8PmR129bHkgtx4+9gM4u2pUbuLNOGaEuYN4EtlMu1bMZC+DMoKqPyFcVCk7TYGbEx4SyMcDZ6hAOw4KQfI6jvXhcMNrGw/h6wBjFnf7hgMGz+vZRXJ+12el6d/YwVrLaBw5zYkfMGOW5rPBZhCETvGWRtfsd0ON+MOIig/aB6V8jv/3UUs28yjBoIigtomuCQmQqiuV1F4H+4424Wh9uo+url1EGWHlS3lDYrAOi21v+l9Gvwv41deaPPZ3dSr4eXQcZCWLg347dC8nIZrW+2yAmY768oCqTKUgjtiZsr4vZ8TSyDBNHSiJAgMBAAECggEACPFIPtTVKr4kpXdlU9UiFHwVpmSwn8CHphvv3632ue4kH+ECeM+YXBGt2L5L5b7J+bXEqH6FNy38+9lX3efX3Ga79gG6rM4YkkA78P4QA+PbzZOfprOKYyYEyO+mfPyuUtGDwrTdkH/ZKGGfD6AJRls+QpeFX8Dt9YZ3trfuyDkY7NjYIsEKj2/o5l32tVNVpikgrF6LdoSuYjfxEpF6nAze0ZN0O8n7+5hohf417bLxl2nOGfd4nWzGJzua891fUqryV3ns4uDfLTUDO9rOEEyd1ehOZRL8M0H8WKH5wjhBswz68bNVilFAAMkF+90tCL9MdjtscBuh3aN3iwXuMQKBgQD02Df22adnX6+/2VtkiKWv416zJuqvbEEaPM/O3by9Nbn3hGQTI+UeirLsH4PH4lrmCLcxEWIA0PP+GFXHffiZxpHvsPKAGsvOGkUjdzC9RYbk7CvjWHzRni5+BRN1V6tAk7oQl0RxrrVpTa1sxi574yZz/Oc/HH1vv6vnbaSAGQKBgQC+ElJSRsFxBd265c7KTV+gCcG7xUUssOoZjMfqvnkUG0XG51DXI7J7jv3/f08YC4hQuWoMj6+7LIJVZz1Zlu65pYzJqZRAwYr0Ec9diK3p5NnzNWA5c0UkThf8x5Nt6RL+7eAFg8ZTdNolanxpzx8m36kdCLgHLQjtGUchZfM58QKBgQCFZoTfZukRpo3ADnIADX+QnGYNYCe/2lCCNh3XDPL3eB1RoX1Q/F6qTFF92xHWxxpVeOwuvNTTswUtLR8XaSgYyJrcqGEHsRHXwnJnB1qz1PzRH2guHxkqsG+OU7+tUE3LCnH29iPheJn4vMy/lh7fevyJd4Ka3S/uwdSsbA2TiQKBgQC7eRTLZFCuawvNqZeywGheUOFOlH/rWcA9XTdemWqRY2kKi+OXn/UOm/Z5iJd57v4QKSGprBu3sWoIVKEVeaKTZ++ahPHegUfN/rca2ZI4TeqPUYlMgR6kdYaBZFRNJ4P+AKBisBvw+yOjngmCz7E5RYfnRE9HfKr+OV2IWqdx8QKBgQCwJnAI4+fvzcO1fPf3g/ROjRCuN97urlABoDBUgLyqPolzKe9OwbdSY1aDj/yLbMKBn3X6ZtLzI7WAFrVWl4TGUowtZch/4dOJqVMDyOteBfbYl2nRCQf3MTWKb3+3AAMQTkA0NJxP0BasZOrJ9wI9I34d2VpTyai8i6ORFSgZig=="
    }

    @get:Inject
    abstract val fileSystemOperations: FileSystemOperations

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val inputJar: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val componentReport: RegularFileProperty

    @get:Input
    abstract val payloadDirectoryName: Property<String>

    @get:Input
    abstract val pluginId: Property<String>

    @get:Input
    abstract val versionCode: Property<Long>

    @get:Input
    abstract val versionName: Property<String>

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
        val digest = HexFormat.of().formatHex(
            MessageDigest.getInstance("SHA-256").digest(input.readBytes()),
        )
        val report = componentReport.get().asFile.readText(Charsets.UTF_8)
        val runtimeAbi = Regex("\\\"runtimeAbiVersion\\\":\\s*(\\d+)")
            .find(report)?.groupValues?.get(1)
            ?: error("Component report is missing runtimeAbiVersion")
        val dependencyClosureSha256 = Regex(
            "\\\"dependencyClosureSha256\\\":\\s*\\\"([0-9a-f]{64})\\\"",
        ).find(report)?.groupValues?.get(1)
            ?: error("Component report is missing dependencyClosureSha256")
        val descriptor = buildString {
            append("x2c-plugin-v2\n")
            append(pluginId.get()).append('\n')
            append(versionCode.get()).append('\n')
            append(versionName.get()).append('\n')
            append(runtimeAbi).append('\n')
            append(dependencyClosureSha256).append('\n')
            append(digest).append('\n')
        }.toByteArray(Charsets.UTF_8)
        payloadDirectory.resolve("codegen-dex.descriptor").writeBytes(descriptor)
        val privateKey = KeyFactory.getInstance("RSA").generatePrivate(
            PKCS8EncodedKeySpec(Base64.getDecoder().decode(TEST_PRIVATE_KEY)),
        )
        val signer = Signature.getInstance("SHA256withRSA")
        signer.initSign(privateKey)
        signer.update(descriptor)
        payloadDirectory.resolve("codegen-dex.sig").writeBytes(signer.sign())
    }
}

val layoutShowcaseDexJar = layout.projectDirectory.file(
    "../producer/build/outputs/x2c/release/codegen-dex.jar",
)
val componentShowcaseDexJar = layout.projectDirectory.file(
    "../producer-secondary/build/outputs/x2c/release/codegen-dex.jar",
)
val layoutShowcaseComponentReport = layout.projectDirectory.file(
    "../producer/build/reports/x2c/release/activities.json",
)
val componentShowcaseComponentReport = layout.projectDirectory.file(
    "../producer-secondary/build/reports/x2c/release/activities.json",
)
// These versions are part of the signed plugin identity. Increase the matching value whenever
// that payload changes; SignedPluginInstaller intentionally rejects version reuse with new bytes.
val layoutShowcaseVersionCode = 6L
val componentShowcaseVersionCode = 6L

android {
    namespace = "dev.x2c.fixture.consumer"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.x2c.fixture.consumer"
        minSdk = 21
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
        val prepareLayoutPayload = tasks.register<PrepareX2cDemoPayload>(
            "prepareX2c${capitalized}LayoutShowcasePayload",
        ) {
            dependsOn(":fixtures:producer:x2cReleaseDexJar")
            inputJar.set(layoutShowcaseDexJar)
            componentReport.set(layoutShowcaseComponentReport)
            payloadDirectoryName.set("x2c-layout-showcase")
            pluginId.set("dev.x2c.fixture.layout-showcase")
            versionCode.set(layoutShowcaseVersionCode)
            versionName.set("6.0.0-layout-showcase")
            outputDirectory.set(layout.buildDirectory.dir(
                "generated/x2cLayoutShowcaseAssets/${variant.name}",
            ))
        }
        variant.sources.assets?.addGeneratedSourceDirectory(
            prepareLayoutPayload,
            PrepareX2cDemoPayload::outputDirectory,
        )
        val prepareComponentPayload = tasks.register<PrepareX2cDemoPayload>(
            "prepareX2c${capitalized}ComponentShowcasePayload",
        ) {
            dependsOn(":fixtures:producer-secondary:x2cReleaseDexJar")
            inputJar.set(componentShowcaseDexJar)
            componentReport.set(componentShowcaseComponentReport)
            payloadDirectoryName.set("x2c-component-showcase")
            pluginId.set("dev.x2c.fixture.component-showcase")
            versionCode.set(componentShowcaseVersionCode)
            versionName.set("6.0.0-component-showcase")
            outputDirectory.set(layout.buildDirectory.dir(
                "generated/x2cComponentShowcaseAssets/${variant.name}",
            ))
        }
        variant.sources.assets?.addGeneratedSourceDirectory(
            prepareComponentPayload,
            PrepareX2cDemoPayload::outputDirectory,
        )
    }
}
