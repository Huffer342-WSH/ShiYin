plugins { alias(libs.plugins.android.application) }

android {
    namespace = "com.Huffer342.shiyin.alarmprobe"
    compileSdk { version = release(37) }
    defaultConfig {
        applicationId = "com.Huffer342.shiyin.alarmprobe"
        minSdk = 34
        targetSdk = 37
        versionCode = 1
        versionName = "probe-1"
    }
    flavorDimensions += "target"
    productFlavors {
        create("sdk34") { dimension = "target"; targetSdk = 34 }
        create("sdk37") { dimension = "target"; targetSdk = 37 }
    }
    sourceSets.getByName("main").assets.directories += "../../test-fixtures/audio"
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}
dependencies { testImplementation("junit:junit:4.13.2") }
