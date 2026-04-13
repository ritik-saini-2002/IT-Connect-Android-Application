plugins {
    kotlin("jvm") version "2.1.0"
    application
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "com.itconnect.server"
version = "1.0.0"

kotlin {
    jvmToolchain(17)
}

val ktorVersion = "2.3.12"

repositories {
    mavenCentral()
}

dependencies {
    // ── Ktor server (Netty engine) ────────────────────────────────────────────
    // Netty uses non-blocking NIO; outperforms the CIO engine for large binary
    // payloads because it delegates to Netty's highly-optimised channel pipeline.
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    implementation("io.ktor:ktor-server-call-logging:$ktorVersion")
    implementation("io.ktor:ktor-server-status-pages:$ktorVersion")
    // Partial-content (HTTP 206) — enables resume and range requests
    implementation("io.ktor:ktor-server-partial-content:$ktorVersion")

    // ── Serialization ────────────────────────────────────────────────────────
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // ── Coroutines ───────────────────────────────────────────────────────────
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")

    // ── Logging ──────────────────────────────────────────────────────────────
    implementation("ch.qos.logback:logback-classic:1.4.14")
}

application {
    mainClass.set("com.itconnect.server.MainKt")
}

// Build a self-contained fat JAR:  ./gradlew shadowJar
// Run directly:                    java -jar build/libs/itconnect-file-server-1.0.0-all.jar
tasks.shadowJar {
    archiveClassifier.set("")
    mergeServiceFiles()
}
