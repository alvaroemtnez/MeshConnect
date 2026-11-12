plugins {
    // AGP 9 (Gradle 9.8.1, Kotlin 2.4.21, as HFPredict): it compiles Kotlin itself (no kotlin.android
    // plugin), and a Kotlin Multiplatform module uses its own Android plugin, not
    // com.android.library (see shared/build.gradle.kts).
    id("com.android.application") version "9.5.1" apply false
    id("com.android.kotlin.multiplatform.library") version "9.5.1" apply false
    id("org.jetbrains.kotlin.multiplatform") version "2.4.21" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.4.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
    // 2.3.12, not 2.3.13: the maintainers flag a regression in 2.3.13 (google/ksp#3286).
    id("com.google.devtools.ksp") version "2.3.12" apply false
}

// Reproducible-build hygiene (carried over from reticulum-mobile-app):
// archive tasks run with file timestamps zeroed and entries sorted so two
// builds of the same source tree produce byte-identical APKs.
allprojects {
    tasks.withType<AbstractArchiveTask>().configureEach {
        isPreserveFileTimestamps = false
        isReproducibleFileOrder = true
    }
}
