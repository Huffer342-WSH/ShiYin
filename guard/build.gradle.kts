plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.Huffer342.shiyin.guard"
    buildToolsVersion = "37.0.0"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.Huffer342.shiyin.guard"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

val guardDebugApk = configurations.create("guardDebugApk") {
    isCanBeConsumed = true
    isCanBeResolved = false
    attributes {
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named("shiyin-guard-apk"))
    }
}

abstract class StripTestOnlyManifestTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val mergedManifest: RegularFileProperty

    @get:OutputFile
    abstract val updatedManifest: RegularFileProperty

    @TaskAction
    fun strip() {
        val text = mergedManifest.get().asFile.readText()
        updatedManifest.get().asFile.writeText(
            text.replace("android:testOnly=\"true\"", "")
        )
    }
}

androidComponents {
    onVariants(selector().withBuildType("debug")) { variant ->
        val stripTestOnly = tasks.register<StripTestOnlyManifestTask>(
            "strip${variant.name.replaceFirstChar { it.uppercase() }}TestOnly"
        )
        variant.artifacts.use(stripTestOnly)
            .wiredWithFiles(
                StripTestOnlyManifestTask::mergedManifest,
                StripTestOnlyManifestTask::updatedManifest
            )
            .toTransform(com.android.build.api.artifact.SingleArtifact.MERGED_MANIFEST)

        val apkDir = variant.artifacts.get(com.android.build.api.artifact.SingleArtifact.APK)
        artifacts.add(guardDebugApk.name, apkDir)
    }
}

dependencies {
    testImplementation(libs.junit)
    testImplementation(libs.json)
}
