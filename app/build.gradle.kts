import java.io.File

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose) // 1.4.0 (U1)
}

// Release signing reuses the HPre release identity so every HKey upgrade
// keeps the same signature. Credentials come from ~/.gradle/gradle.properties
// (hpreSigning.*) or CI env vars; the keystore file is never committed.
val hpreStoreFile = providers.gradleProperty("hpreSigning.storeFile").orNull
    ?: System.getenv("HPRE_SIGNING_STORE_FILE")
val hpreStorePassword = providers.gradleProperty("hpreSigning.storePassword").orNull
    ?: System.getenv("HPRE_SIGNING_STORE_PASSWORD")
val hpreKeyAlias = providers.gradleProperty("hpreSigning.keyAlias").orNull
    ?: System.getenv("HPRE_SIGNING_KEY_ALIAS")
val hpreKeyPassword = providers.gradleProperty("hpreSigning.keyPassword").orNull
    ?: System.getenv("HPRE_SIGNING_KEY_PASSWORD")
val hpreSigningReady = !hpreStoreFile.isNullOrBlank() && !hpreStorePassword.isNullOrBlank() &&
    !hpreKeyAlias.isNullOrBlank() && !hpreKeyPassword.isNullOrBlank() &&
    File(hpreStoreFile).exists()

android {
    namespace = "com.hkey.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.hkey.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 23
        versionName = "1.4.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hpreSigningReady) {
            create("release") {
                storeFile = file(hpreStoreFile)
                storePassword = hpreStorePassword
                keyAlias = hpreKeyAlias
                keyPassword = hpreKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true   // 5.x: R8
            isShrinkResources = true // 5.x: bỏ resource không dùng
            // 1.9: thiếu cấu hình ký -> KHÔNG rơi về debug key (APK release ký
            // debug không update đè được bản đã cài); assembleRelease fail rõ.
            if (hpreSigningReady) signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    buildFeatures {
        buildConfig = true // chỉ phục vụ log đo thời gian ở bản debug
        compose = true     // 1.4.0 (U1)
    }
    if (!hpreSigningReady) {
        tasks.register("requireReleaseSigning") {
            doLast {
                throw GradleException(
                    "Release cần khóa ký thật: đặt hpreSigning.storeFile/storePassword/" +
                        "keyAlias/keyPassword trong ~/.gradle/gradle.properties hoặc env " +
                        "HPRE_SIGNING_*. Không build APK release bằng debug key."
                )
            }
        }
        // assembleRelease do AGP tạo sau -> matching/configureEach, không named()
        tasks.matching { it.name == "assembleRelease" }
            .configureEach { dependsOn("requireReleaseSigning") }
    }

    // 4.x: vi_model.bin lưu KHÔNG nén trong APK -> openRawResourceFd + mmap
    androidResources {
        noCompress.add("bin")
    }

    // 1.4.0: Robolectric unit test đọc resources/layout thật (T0)
    testOptions {
        unitTests.isIncludeAndroidResources = true
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
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    // 1.4.0 (U1): màn cài đặt Compose — BOM ghim phiên bản chung
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    debugImplementation(libs.compose.ui.tooling)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
}

// 1.4.0 (U3): copy CHANGELOG.md -> res/raw cho màn Giới thiệu
tasks.register("copyChangelog", Copy::class) {
    from(rootProject.file("CHANGELOG.md"))
    into("src/main/res/raw")
    rename { "changelog.md" }
}
tasks.named("preBuild") { dependsOn("copyChangelog") }
