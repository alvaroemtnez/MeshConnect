import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform")
    id("com.android.library")
    kotlin("plugin.serialization")
}

kotlin {
    // No `jvmToolchain(17)` pin — run on whatever JDK (>=17) is present and
    // pin the *output* bytecode to 17 (same F-Droid-friendly setup as
    // reticulum-mobile-app).
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    applyDefaultHierarchyTemplate()

    // Android only. MeshConnect dropped upstream's iOS targets (iosApp, the CryptoKit
    // bridge, iosMain) on 2026-11-09; commonMain stays platform-neutral so the protocol
    // and engine keep merging cleanly from upstream.

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
            }
        }
        val androidMain by getting {
            dependencies {
                // Bouncy Castle for Ed25519 (advert verify, identity keygen)
                implementation("org.bouncycastle:bcprov-jdk18on:1.78.1")
            }
        }
        val androidUnitTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(kotlin("test-junit"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
                implementation("junit:junit:4.13.2")
            }
        }
    }
}

android {
    namespace = "io.github.thatsfguy.meshcore.shared"
    compileSdk = 37
    defaultConfig {
        minSdk = 26  // Android 8.0 — BLE APIs stable
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }
}
