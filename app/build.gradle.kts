plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "id.my.appworld.coffeepos"
    compileSdk = 34

    defaultConfig {
        applicationId = "id.my.appworld.coffeepos"
        minSdk = 26            // Android 8.0+ (tablet Anda: Android 10)
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    // Kunci tetap di repo -> setiap APK baru ditandatangani sama, jadi bisa dipasang menimpa versi lama
    signingConfigs {
        getByName("debug") {
            storeFile = rootProject.file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    lint {
        checkReleaseBuilds = false     // lint tidak menghalangi pembuatan APK
        abortOnError = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}
