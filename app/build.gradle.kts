import java.io.File

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
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
        versionCode = 9
        versionName = "1.0.8"

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
            isMinifyEnabled = false
            signingConfig = if (hpreSigningReady) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
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
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    testImplementation(libs.junit)
}
