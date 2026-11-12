import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform")
    // AGP 9's plugin for the Android target of a Kotlin Multiplatform module. Using
    // com.android.library here relies on APIs AGP 9 deprecates and AGP 10 removes.
    id("com.android.kotlin.multiplatform.library")
    kotlin("plugin.serialization")
}

kotlin {
    android {
        namespace = "io.github.thatsfguy.meshcore.shared"
        compileSdk = 37
        minSdk = 26  // Android 8.0 — BLE APIs stable

        // No `jvmToolchain(17)` pin — run on whatever JDK (>=17) is present and
        // pin the *output* bytecode to 17 (same F-Droid-friendly setup as
        // reticulum-mobile-app). Applies to Kotlin and Java alike.
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }

        // Host (JVM) tests are opt-in with this plugin: src/androidHostTest, run by
        // :shared:testAndroidHostTest. Android stubs return defaults, as before.
        withHostTest {
            isReturnDefaultValues = true
        }
    }

    applyDefaultHierarchyTemplate()

    // Android only. MeshConnect dropped upstream's iOS targets (iosApp, the CryptoKit
    // bridge, iosMain) on 2026-11-09; commonMain stays platform-neutral so the protocol
    // and engine keep merging cleanly from upstream.

    sourceSets {
        commonMain {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
            }
        }
        androidMain {
            dependencies {
                // Bouncy Castle for Ed25519 (advert verify, identity keygen)
                implementation("org.bouncycastle:bcprov-jdk18on:1.78.1")
            }
        }
        // No generated accessor for this one; by name, as Google's migration guide does.
        getByName("androidHostTest") {
            dependencies {
                implementation(kotlin("test"))
                implementation(kotlin("test-junit"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
                implementation("junit:junit:4.13.2")
            }
        }
    }
}
