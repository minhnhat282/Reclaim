plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.minhnhat.reclaim"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.minhnhat.reclaim"
        minSdk = 30
        // Giữ 30 như MNLocker (cài thẳng, không lên Play Store). Mọi nhánh theo phiên bản Android
        // nằm trong platform/Compat.kt nên có thể nâng targetSdk bất cứ lúc nào mà không phải sửa nơi khác.
        targetSdk = 30
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Dùng cá nhân: ký bằng khóa debug để cài thẳng được
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // targetSdk 30 bị lint cảnh báo "quá cũ" (chỉ liên quan Play Store)
    lint { checkReleaseBuilds = false }
}

dependencies {
    // Ứng dụng không dùng thư viện ngoài. Chỉ có JUnit cho unit test của PolicyEvaluator.
    testImplementation("junit:junit:4.13.2")
}
