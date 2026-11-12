plugins {
    // AGP 9 compiles Kotlin itself: no kotlin("android"). The Kotlin version is the root's.
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

/**
 * Derive `(versionName, versionCode)` from the git tag when HEAD sits
 * exactly on a `v*` tag (MeshConnect scheme; upstream used `android-v*`):
 * `v1.2.3` → ("1.2.3", 10203), `v1.3.0-beta.1` → ("1.3.0-beta.1", 10300): a beta
 * carries its release's code, as in tools/release-version.sh. CI `-PversionName`/`-PversionCode`
 * always win; otherwise a local build gets `0.0.0-dev`.
 */
fun gitDerivedVersion(): Pair<String, Int>? {
    val tags = runCatching {
        val proc = ProcessBuilder("git", "tag", "--points-at", "HEAD", "--list", "v[0-9]*")
            .directory(rootDir).redirectErrorStream(true).start()
        val out = proc.inputStream.bufferedReader().readLines().map { it.trim() }
        if (proc.waitFor() == 0) out else emptyList()
    }.getOrDefault(emptyList())

    // A release tagged on its last beta's commit is the release: F-Droid builds `commit: vX.Y.Z`
    // and its APK must say X.Y.Z, as ours does, or the reproducible-build check fails.
    val pattern = Regex("""v(\d+)\.(\d+)\.(\d+)(-beta\.\d+)?""")
    val m = tags.mapNotNull { pattern.matchEntire(it) }.minByOrNull { it.groupValues[4].isNotEmpty() }
        ?: return null
    val (maj, min, pat, beta) = m.destructured
    return "$maj.$min.$pat$beta" to (maj.toInt() * 10_000 + min.toInt() * 100 + pat.toInt())
}

android {
    namespace = "io.alvaroedo.meshconnect"
    compileSdk = 37
    // build.sh installs the SDK packages named here and by compileSdk.
    buildToolsVersion = "37.0.0"

    defaultConfig {
        applicationId = "io.alvaroedo.meshconnect"
        minSdk = 26
        targetSdk = 37
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        val gitVersion = gitDerivedVersion()
        versionName = (project.findProperty("versionName") as? String)
            ?: gitVersion?.first
            ?: "0.0.0-dev"
        versionCode = (project.findProperty("versionCode") as? String)?.toInt()
            ?: gitVersion?.second
            ?: 1

        // SQLCipher statically links OpenSSL + SQLite, so libsqlcipher.so
        // is ~5 MB PER ABI — shipping all four took the APK from 14 MB to
        // 31 MB, and 11 MB of that is x86/x86_64 that no phone runs. ARM
        // only: arm64 for every current device, armeabi-v7a for old 32-bit
        // ones. (Consequence: no x86_64 emulator/Chromebook builds. The
        // project has no instrumented tests, so CI is unaffected.)

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    // English (default), Spanish, Valencian; drops the translations libraries bring for
    // every other language. The app's own locales_config.xml lists the same three.
    androidResources {
        localeFilters += listOf("en", "es", "ca")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    val releaseKeystore = System.getenv("RELEASE_KEYSTORE")
    if (releaseKeystore != null) {
        val storePass = System.getenv("RELEASE_STORE_PASSWORD")
        signingConfigs {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = storePass
                keyAlias = System.getenv("RELEASE_KEY_ALIAS")
                keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
                    ?.takeIf { it.isNotEmpty() }
                    ?: storePass
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
        }
        // Distinct applicationId so a debug build installs side-by-side
        // with a release install.
        debug {
            // x86_64 too, so the debug build runs on the headless AOSP
            // emulator (README → Emulator). Release stays ARM-only.
            ndk { abiFilters += "x86_64" }
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    // Reproducible-build hygiene (see root build.gradle.kts).
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    // Robolectric tests resolve the app's own strings and plurals (SharedTextResourcesTest).
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    buildFeatures {
        buildConfig = true
    }

    // Export Room schemas for diffing across migrations.
    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
    }
}

dependencies {
    implementation(project(":shared"))
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.compose.ui:ui:1.10.6")
    implementation("androidx.compose.material3:material3:1.4.0")
    implementation("androidx.compose.ui:ui-tooling-preview:1.10.6")
    implementation("androidx.navigation:navigation-compose:2.9.8")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.core:core-ktx:1.17.0")

    // QR code generation and decoding (contact/channel/community/settings QR).
    implementation("com.google.zxing:core:3.5.3")
    // The QR scanner's camera (QrScanActivity), as in Skypost.
    implementation("androidx.camera:camera-camera2:1.6.2")
    implementation("androidx.camera:camera-lifecycle:1.6.2")
    implementation("androidx.camera:camera-compose:1.6.2")

    // Maps: MapLibre renders vector tiles (OpenFreeMap, no API key) with
    // no Google Play Services and no telemetry. 13.x needs Kotlin 2.2+
    // (we are on 2.3). Tiles are the map's only
    // network use and are cached by MapLibre's ambient cache.
    implementation("org.maplibre.gl:android-sdk:13.7.1")

    // Home-screen widget (Glance), with Material You colours.
    implementation("androidx.glance:glance-appwidget:1.1.1")
    implementation("androidx.glance:glance-material3:1.1.1")

    // SQLCipher — the Room DB is encrypted at rest with a key sealed in
    // the Android Keystore (2026-07-31 security review, accepted-risk
    // item #1). Adds ~4 MB of native libs; the tradeoff is deliberate:
    // message history, contacts and GPS were previously cleartext SQLite
    // protected only by file-based encryption.
    implementation("net.zetetic:sqlcipher-android:4.6.1")
    implementation("androidx.sqlite:sqlite-ktx:2.6.2")

    // Room
    implementation("androidx.room:room-runtime:2.8.5")
    implementation("androidx.room:room-ktx:2.8.5")
    ksp("androidx.room:room-compiler:2.8.5")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    // Already inside the APK via :shared, which declares it as an
    // implementation dependency and so does not expose the types. The
    // conversation's date headings are decided in shared and need to
    // name a TimeZone at the call site; this puts the type on the
    // compile classpath without adding an artifact.
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")

    debugImplementation("androidx.compose.ui:ui-tooling:1.10.6")

    // On-device Compose tests. The conversation list's rules live in
    // layout, and layout is the one thing a local unit test cannot see:
    // every source pin guarding it passed through five separate reports
    // of a thread opening in the wrong place. See ThreadListScrollTest.
    androidTestImplementation("androidx.compose.ui:ui-test-junit4:1.10.6")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    debugImplementation("androidx.compose.ui:ui-test-manifest:1.10.6")

    testImplementation(kotlin("test"))
    testImplementation(kotlin("test-junit"))
    testImplementation("junit:junit:4.13.2")
    testImplementation("androidx.room:room-testing:2.8.5")
    testImplementation("org.robolectric:robolectric:4.13")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
}

// Robolectric fetches its android-all jars into ${user.home}/.m2; build.sh
// points the test JVM's home inside .tools/ so nothing lands in ~.
tasks.withType<Test>().configureEach {
    System.getenv("MESHCONNECT_ROBOLECTRIC_DIR")?.let { dir ->
        systemProperty("user.home", dir)
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}
