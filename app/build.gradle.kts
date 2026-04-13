plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.jetbrains.kotlin.compose)
    alias(libs.plugins.google.devtools.ksp)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace  = "com.example.ritik_2"
    compileSdk = 35

    defaultConfig {
        applicationId             = "com.example.itconnect"
        minSdk                    = 26
        targetSdk                 = 35
        versionCode               = 2
        versionName               = "3.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Read PocketBase config from local.properties (never commit credentials)
        val props = com.android.build.gradle.internal.cxx.configure
            .gradleLocalProperties(rootDir, providers)

        buildConfigField("String", "PB_HOST",             "\"${props.getProperty("pb.host",             "192.168.1.100")}\"")
        buildConfigField("String", "PB_PORT",             "\"${props.getProperty("pb.port",             "8090")}\"")
        buildConfigField("String", "PB_ADMIN_EMAIL",      "\"${props.getProperty("pb.admin.email",      "")}\"")
        buildConfigField("String", "PB_ADMIN_PASSWORD",   "\"${props.getProperty("pb.admin.password",   "")}\"")
    }

    buildTypes {
        debug {
            isDebuggable        = true
            isMinifyEnabled     = false
            // FIX: Removed applicationIdSuffix = ".debug"
            // This was causing "reinstall APK" errors when cloning repo to another
            // computer because debug builds had package "com.example.itconnect.debug"
            // while release builds had "com.example.itconnect" — Android treats these
            // as different apps so you had to uninstall the old one first.
        }
        release {
            isMinifyEnabled   = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
            // Keeps mapping.txt for deobfuscating crash reports
            ndk { debugSymbolLevel = "FULL" }
        }
    }

    buildFeatures {
        compose     = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_18
        targetCompatibility = JavaVersion.VERSION_18
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_18)
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/NOTICE.md"
            excludes += "/META-INF/LICENSE.md"
            excludes += "/META-INF/LICENSE"
            excludes += "/META-INF/NOTICE"
            // smbj / BouncyCastle JAR signature files must be stripped
            excludes += "/META-INF/*.SF"
            excludes += "/META-INF/*.DSA"
            excludes += "/META-INF/*.RSA"
        }
    }
}

dependencies {
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    // Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.security.crypto)

    // Compose
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.animation)
    implementation(libs.androidx.foundation)
    implementation(libs.androidx.runtime.livedata)

    // Lifecycle
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // Network
    implementation(libs.okhttp)
    implementation(libs.gson)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)

    // Serialization
    implementation(libs.kotlinx.serialization.json)

    // Image Loading
    implementation(libs.coil.compose)

    // PocketBase
    implementation(libs.pocketbase.kotlin)
    implementation(libs.ktor.client.android)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)

    // SMB — jcifs-ng for browsing/metadata, smbj for high-speed transfers
    implementation(libs.jcifs.ng)
    implementation(libs.smbj)

    // Debug
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.ui.test.junit4)
}