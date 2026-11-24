plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.Huffer342.shiyin"
    buildToolsVersion = "37.0.0"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.Huffer342.shiyin"
        minSdk = 24
        targetSdk = 37
        versionCode = 9
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

fun guardApkConfiguration(buildType: String) = configurations.create("guard${buildType.replaceFirstChar { it.uppercase() }}Apk") {
    isCanBeConsumed = false
    isCanBeResolved = true
    attributes {
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named("shiyin-guard-$buildType-apk"))
    }
}

val guardDebugApk = guardApkConfiguration("debug")
val guardReleaseApk = guardApkConfiguration("release")

abstract class PrepareGuardAssetTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val apkDirs: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun copyApk() {
        val out = outputDir.get().asFile
        out.deleteRecursively()
        out.mkdirs()
        val apk = apkDirs.asFileTree.files
            .filter { it.isFile && it.extension == "apk" }
            .minByOrNull { it.name }
            ?: error("Guard APK not found in ${apkDirs.files}")
        apk.copyTo(out.resolve("shiyin-guard.apk"), overwrite = true)
    }
}

androidComponents {
    onVariants { variant ->
        val prepareGuardAsset = tasks.register<PrepareGuardAssetTask>(
            "prepare${variant.name.replaceFirstChar { it.uppercase() }}GuardAsset"
        ) {
            apkDirs.from(if (variant.buildType == "release") guardReleaseApk else guardDebugApk)
        }
        variant.sources.assets?.addGeneratedSourceDirectory(
            prepareGuardAsset,
            PrepareGuardAssetTask::outputDir
        )
    }
}


dependencies {
    guardDebugApk(project(":guard"))
    guardReleaseApk(project(":guard"))
    coreLibraryDesugaring(libs.desugar.jdk)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    testImplementation(libs.json)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.icons)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
