plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.jetbrains.kotlin.compose)
    alias(libs.plugins.google.gms.google.services)
    alias(libs.plugins.google.devtools.ksp)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.google.firebase.crashlytics)
    alias(libs.plugins.kotlin.serialization)
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
        compose     = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/NOTICE.md"
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
    implementation(libs.androidx.compose.foundation)

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
    implementation(libs.androidx.activity.compose)

    // ── Lifecycle ─────────────────────────────────────────────
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // ── Navigation ────────────────────────────────────────────
    implementation(libs.androidx.navigation.compose)

    // ── Room ──────────────────────────────────────────────────
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // ── Hilt ──────────────────────────────────────────────────
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // ── Network ───────────────────────────────────────────────
    implementation(libs.okhttp)
    implementation(libs.gson)

    // ── Coroutines ────────────────────────────────────────────
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)

    // ── Serialization ─────────────────────────────────────────
    implementation(libs.kotlinx.serialization.json)

    // ── Image Loading ─────────────────────────────────────────
    implementation(libs.coil.compose)
    implementation(libs.accompanist.coil)

    // ── Security ──────────────────────────────────────────────
    implementation(libs.androidx.security.crypto)

    // ── Firebase (Storage + Crashlytics only) ─────────────────
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.storage)           // kept for other activities
    implementation(libs.firebase.messaging)         // kept for FCM if needed later
    //implementation(libs.firebase.crashlytics.ktx)

    // ── GMS ───────────────────────────────────────────────────
    implementation(libs.play.services.auth)

    // ── PocketBase SDK ────────────────────────────────────────
    implementation(libs.pocketbase.kotlin)
    implementation(libs.ktor.client.android)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)

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