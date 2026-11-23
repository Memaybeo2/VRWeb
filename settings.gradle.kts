// File: settings.gradle.kts
// Khai báo kho tải thư viện và tên dự án. Người mới không cần sửa file này.

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "VRBoxWebMirror"
include(":app")
