plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.kyy.launcher"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.kyy.launcher"
        minSdk = 26
        targetSdk = 34
        versionCode = 23
        versionName = "4.1"
    }
    // Подпись: если в окружении есть ключ (секреты GitHub, пароль один на хранилище и ключ) — подписываем им, иначе отладочным ключом.
    val ksPath = System.getenv("KEYSTORE_FILE")
    signingConfigs {
        if (ksPath != null && File(ksPath).exists()) {
            create("release") {
                storeFile = File(ksPath)
                storePassword = System.getenv("KEY_PASSWORD")
                keyAlias = "launcher"
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    lint { checkReleaseBuilds = false; abortOnError = false }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("androidx.graphics:graphics-shapes:1.0.0")
    implementation("androidx.compose.material:material-icons-core")
    implementation("com.google.android.material:material:1.12.0")
}
