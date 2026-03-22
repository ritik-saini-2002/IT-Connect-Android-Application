plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.jetbrains.kotlin.compose)
    alias(libs.plugins.google.gms.google.services)
    alias(libs.plugins.google.devtools.ksp)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.google.firebase.crashlytics)  // ← ADD THIS — fixes crash
}

android {
    namespace = "com.example.ritik_2"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.ritik_2"
        minSdk = 30
        targetSdk = 35
        versionCode = 1
        versionName = "1.5.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        viewBinding = true
        compose = true
    }

    // ✅ With Kotlin 2.x + jetbrains.kotlin.compose plugin,
    // DO NOT set kotlinCompilerExtensionVersion manually.
    // The plugin handles it automatically.
    // composeOptions block is NOT needed.

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/NOTICE.md"           // ← avoids jcifs conflicts
            excludes += "/META-INF/LICENSE.md"
        }
    }
}

dependencies {

    // ── Compose BOM ───────────────────────────────────────────
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    // ── Core ──────────────────────────────────────────────────
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.gridlayout)
    implementation(libs.androidx.documentfile)

    // ── Compose UI ────────────────────────────────────────────
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.runtime.livedata)
    implementation(libs.androidx.animation)
    implementation(libs.androidx.foundation.android)
    implementation(libs.androidx.animation.core.android)

    // ── Activity Compose ──────────────────────────────────────
    implementation(libs.androidx.activity.compose)

    // ── Lifecycle ─────────────────────────────────────────────
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // ── Navigation ────────────────────────────────────────────
    implementation(libs.androidx.navigation.compose)

    // ── Room (KSP instead of kapt) ────────────────────────────
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)                // ← KSP (not kapt)

    // ── Hilt ──────────────────────────────────────────────────
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)                 // ← KSP (not kapt)
    implementation(libs.androidx.hilt.navigation.compose)

    // ── OkHttp + Gson (REQUIRED for pccontrol package) ────────
    implementation(libs.okhttp)                     // ← NEW
    implementation(libs.gson)                       // ← NEW (already hardcoded but now in toml)

    // ── Coroutines ────────────────────────────────────────────
    implementation(libs.kotlinx.coroutines.android)

    // ── Image Loading ─────────────────────────────────────────
    implementation(libs.coil.compose)
    implementation(libs.accompanist.coil)

    // ── Firebase ──────────────────────────────────────────────
    implementation(platform("com.google.firebase:firebase-bom:33.0.0"))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore.ktx)
    implementation(libs.firebase.storage)
    implementation(libs.firebase.database.ktx)
    implementation(libs.firebase.inappmessaging.display)
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.functions.ktx)
    implementation("com.google.firebase:firebase-crashlytics-ktx")  // works with plugin above

    // ── GMS ───────────────────────────────────────────────────
    implementation("com.google.android.gms:play-services-auth:20.7.0")
    implementation("com.google.android.gms:play-services-auth-api-phone:18.0.1")

    // ── SMB / jCIFS ───────────────────────────────────────────
    implementation(libs.jcifs.ng)

    // ── Debug ─────────────────────────────────────────────────
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    // ── Testing ───────────────────────────────────────────────
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.ui.test.junit4)
}