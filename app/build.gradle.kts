plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.lexforge"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.lexforge"
        minSdk = 26
        targetSdk = 35
        versionCode = 6
        versionName = "0.3.2"
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
        }
        release {
            ndk { abiFilters.clear(); abiFilters += "arm64-v8a" }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // v0.1 用 debug 签名，保证能直接 adb install；正式发版换自有 keystore
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // 关键：词典包必须不压缩，否则 openFd() 拿不到真实偏移，mmap 会失败
    androidResources {
        noCompress += listOf("lexd")
    }

    packaging {
        resources.excludes += setOf(
            "META-INF/*.version", "META-INF/LICENSE*", "META-INF/NOTICE*",
            "META-INF/DEPENDENCIES", "META-INF/AL2.0", "META-INF/LGPL2.1",
        )
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core:search"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.mlkit.text.recognition)
    debugImplementation(libs.compose.ui.tooling.preview)
}
