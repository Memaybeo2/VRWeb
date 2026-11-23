// File: app/build.gradle.kts
// Cấu hình ứng dụng: tên gói, phiên bản Android, thư viện sử dụng.

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.vrbox.webmirror"
    compileSdk = 36                      // Android 16

    defaultConfig {
        applicationId = "com.vrbox.webmirror"
        minSdk = 26                      // Android 8.0 trở lên
        targetSdk = 36                   // Nhắm tới Android 16
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-ktx:1.9.3")
}
