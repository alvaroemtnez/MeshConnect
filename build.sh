#!/usr/bin/env bash
# build.sh - Build MeshConnect from the command line, no Android Studio needed.
#
# Usage: ./build.sh [command]   (default: debug)
#
#   debug       build the debug APK -> dist/MeshConnect-debug.apk
#   release     build the release APK (+ AAB), signed if MESHCONNECT_KEYSTORE is set
#   install     build the debug APK and install it on the connected phone
#   run         install, then launch the app and follow its log
#   logcat      follow the log of the running app
#   test        run the shared + app JVM unit tests
#   gradle ...  run any Gradle task with the bundled toolchain
#   env         print exports for using gradlew/adb directly: eval "$(./build.sh env)"
#   setup       only download the toolchain into .tools/
#   clean       remove build outputs
#   distclean   also remove .tools/ (the downloaded toolchain and Gradle caches)
#
# Everything (JDK, Android SDK, Gradle and their caches) is
# downloaded into .tools/; nothing is installed system-wide and nothing is
# read from ~/.gradle, ~/.android or ~/.m2. Needs only bash, curl, tar, unzip
# and sha256sum from the host. Works on immutable distros (Silverblue) and
# inside a plain container (that is how CI runs it).
#
# Release signing (both optional; without them the APK is left unsigned):
#   MESHCONNECT_KEYSTORE        path to the .jks
#   MESHCONNECT_KEY_ALIAS       key alias
#   MESHCONNECT_KEYSTORE_PASS   store password (else apksigner prompts)
#   MESHCONNECT_KEY_PASS        key password (defaults to the store password)
# Version: VERSION_NAME / VERSION_CODE env vars override the git-derived one.

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
DIST_DIR="$ROOT_DIR/dist"
TOOLS_DIR="$ROOT_DIR/.tools"
APP_GRADLE="$ROOT_DIR/androidApp/build.gradle.kts"
APP_NAME="MeshConnect"

# AGP 8.x + Kotlin 2.0 are tested against JDK 17.
JDK_VERSION=17
# https://developer.android.com/studio#command-line-tools-only
CMDLINE_TOOLS_VERSION=16111833
CMDLINE_TOOLS_SHA256=0877a1d048fe4a24efe2eff536ca4223f7adeb58648bb81909d33c446918cfa8

JDK_DIR="$TOOLS_DIR/jdk-$JDK_VERSION"
SDK_DIR="$TOOLS_DIR/android-sdk"
ADB="$SDK_DIR/platform-tools/adb"
ANDROID_CLI="$SDK_DIR/cmdline-tools/latest/bin/android"

# Make every tool use the copies in .tools/, regardless of what the system has.
export JAVA_HOME="$JDK_DIR"
export ANDROID_HOME="$SDK_DIR"
unset ANDROID_SDK_ROOT
export GRADLE_USER_HOME="$TOOLS_DIR/gradle"
# Instead of ~/.android: AGP's debug keystore, the Android CLI and its caches.
# (adb ignores it and keeps its key in ~/.android/adbkey.)
export ANDROID_USER_HOME="$TOOLS_DIR/android-user"
# Robolectric's android-all jars, instead of ~/.m2 (read by androidApp/build.gradle.kts).
export MESHCONNECT_ROBOLECTRIC_DIR="$TOOLS_DIR/robolectric"
# build.sh signs releases itself (apksigner); keep AGP from doing it too.
unset RELEASE_KEYSTORE
export PATH="$JDK_DIR/bin:$SDK_DIR/platform-tools:$PATH"

info() { printf '\033[1;34m==>\033[0m %s\n' "$*"; }
die() { printf '\033[1;31mError:\033[0m %s\n' "$*" >&2; exit 1; }

require() {
    local cmd
    for cmd in "$@"; do
        command -v "$cmd" >/dev/null || die "'$cmd' not found, please install it."
    done
}

# Read a value from androidApp/build.gradle.kts, e.g. gradle_value compileSdk -> 34.
gradle_value() {
    local value
    value="$(sed -n "s/^ *$1 *= *\"\{0,1\}\([0-9.]*\)\"\{0,1\}.*/\1/p" "$APP_GRADLE" | head -n 1)"
    [ -n "$value" ] || die "Could not read $1 from $APP_GRADLE."
    echo "$value"
}

# Download $1 to $2 and check it against the SHA-256 $3.
fetch_verified() {
    local url="$1" out="$2" sha="$3"
    curl -fL --retry 3 --progress-bar "$url" -o "$out"
    echo "$sha  $out" | sha256sum -c --quiet - || die "Checksum mismatch for $url"
}

setup_jdk() {
    [ -x "$JDK_DIR/bin/java" ] && return
    local tmp meta url sha
    tmp="$(mktemp -d)"
    # The assets API gives the download link together with its SHA-256.
    meta="$(curl -fsSL "https://api.adoptium.net/v3/assets/latest/$JDK_VERSION/hotspot?os=linux&architecture=x64&image_type=jdk&vendor=eclipse")"
    url="$(echo "$meta" | sed -n 's/.*"link": *"\([^"]*\.tar\.gz\)".*/\1/p' | head -n 1)"
    sha="$(echo "$meta" | sed -n 's/.*"checksum": *"\([0-9a-f]\{64\}\)".*/\1/p' | head -n 1)"
    [ -n "$url" ] && [ -n "$sha" ] || die "Could not find a Temurin JDK $JDK_VERSION download."
    info "Downloading Temurin JDK $JDK_VERSION ($(basename "$url"))"
    fetch_verified "$url" "$tmp/jdk.tar.gz" "$sha"
    mkdir -p "$tmp/out" "$TOOLS_DIR"
    tar -xf "$tmp/jdk.tar.gz" -C "$tmp/out" --strip-components=1
    mv "$tmp/out" "$JDK_DIR"
    rm -rf "$tmp"
}

# The Android CLI (`android sdk`), which replaces sdkmanager. Usage metrics are off.
android_cli() {
    "$ANDROID_CLI" --no-metrics --sdk="$SDK_DIR" "$@"
}

# Install SDK package $1 (e.g. "platform-tools") unless $SDK_DIR/$1 exists.
# Installing accepts its licence (the Android SDK terms, developer.android.com/studio/terms).
sdk_package() {
    [ -d "$SDK_DIR/$1" ] && return
    info "Installing SDK package $1"
    android_cli sdk install "$1"
}

setup_sdk() {
    if [ ! -x "$ANDROID_CLI" ]; then
        info "Downloading Android command line tools"
        local tmp
        tmp="$(mktemp -d)"
        fetch_verified "https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_TOOLS_VERSION}_latest.zip" \
            "$tmp/cmdline-tools.zip" "$CMDLINE_TOOLS_SHA256"
        unzip -q "$tmp/cmdline-tools.zip" -d "$tmp"
        mkdir -p "$SDK_DIR/cmdline-tools"
        mv "$tmp/cmdline-tools" "$SDK_DIR/cmdline-tools/latest"
        rm -rf "$tmp"
    fi

    # The SDK packages follow androidApp/build.gradle.kts, so bumping
    # compileSdk or buildToolsVersion there is enough; the next build
    # installs them. Since API 37 platform packages carry a minor version.
    local api platform
    api="$(gradle_value compileSdk)"
    platform="platforms/android-$api"
    [ "$api" -ge 37 ] && platform="$platform.0"
    sdk_package platform-tools
    sdk_package "$platform"
    sdk_package "build-tools/$(gradle_value buildToolsVersion)"

    # For running ./gradlew by hand and for editor tooling; ignored by git.
    echo "sdk.dir=$SDK_DIR" > "$ROOT_DIR/local.properties"
}

setup() {
    [ "$(uname -s)" = Linux ] || die "Only Linux is supported."
    # Google ships the SDK build tools (aapt2, d8...) for x86_64 Linux only.
    [ "$(uname -m)" = x86_64 ] || die "Unsupported architecture $(uname -m), the Android SDK needs x86_64."
    require curl tar unzip sha256sum
    setup_jdk
    setup_sdk
}

gradle() {
    local extra=()
    [ -n "${VERSION_NAME:-}" ] && extra+=("-PversionName=$VERSION_NAME")
    [ -n "${VERSION_CODE:-}" ] && extra+=("-PversionCode=$VERSION_CODE")
    (cd "$ROOT_DIR" && ./gradlew --no-daemon "${extra[@]}" "$@")
}

app_id() {
    sed -n 's/^ *applicationId *= *"\(.*\)".*/\1/p' "$APP_GRADLE" | head -n 1
}

# The debug build installs next to the release one, as <applicationId>.debug.
debug_app_id() {
    echo "$(app_id).debug"
}

build_debug() {
    setup
    info "Building debug APK"
    gradle :androidApp:assembleDebug
    mkdir -p "$DIST_DIR"
    cp "$ROOT_DIR/androidApp/build/outputs/apk/debug/androidApp-debug.apk" "$DIST_DIR/$APP_NAME-debug.apk"
    info "Done: $DIST_DIR/$APP_NAME-debug.apk"
}

build_release() {
    setup
    info "Building release APK and AAB"
    gradle :androidApp:assembleRelease :androidApp:bundleRelease

    local ver unsigned aab bt
    ver="${VERSION_NAME:-dev}"
    unsigned="$ROOT_DIR/androidApp/build/outputs/apk/release/androidApp-release-unsigned.apk"
    aab="$ROOT_DIR/androidApp/build/outputs/bundle/release/androidApp-release.aab"
    bt="$SDK_DIR/build-tools/$(gradle_value buildToolsVersion)"
    mkdir -p "$DIST_DIR"

    if [ -z "${MESHCONNECT_KEYSTORE:-}" ]; then
        cp "$unsigned" "$DIST_DIR/$APP_NAME-$ver-release-unsigned.apk"
        cp "$aab" "$DIST_DIR/$APP_NAME-$ver-release-unsigned.aab"
        info "MESHCONNECT_KEYSTORE not set, left unsigned in $DIST_DIR/"
        return
    fi

    local out="$DIST_DIR/$APP_NAME-$ver-release.apk"
    local args=(sign --ks "$MESHCONNECT_KEYSTORE" --out "$out")
    [ -n "${MESHCONNECT_KEY_ALIAS:-}" ] && args+=(--ks-key-alias "$MESHCONNECT_KEY_ALIAS")
    # Passwords are read from the environment if set, otherwise apksigner asks for them.
    [ -n "${MESHCONNECT_KEYSTORE_PASS:-}" ] && args+=(--ks-pass env:MESHCONNECT_KEYSTORE_PASS)
    if [ -n "${MESHCONNECT_KEY_PASS:-}" ]; then
        args+=(--key-pass env:MESHCONNECT_KEY_PASS)
    elif [ -n "${MESHCONNECT_KEYSTORE_PASS:-}" ]; then
        args+=(--key-pass env:MESHCONNECT_KEYSTORE_PASS)
    fi

    info "Aligning and signing release APK with $MESHCONNECT_KEYSTORE"
    "$bt/zipalign" -f -p 4 "$unsigned" "$DIST_DIR/aligned.apk"
    "$bt/apksigner" "${args[@]}" "$DIST_DIR/aligned.apk"
    rm -f "$DIST_DIR/aligned.apk" "$out.idsig"

    # The AAB is signed with jarsigner (apksigner does not sign bundles).
    local jargs=(-keystore "$MESHCONNECT_KEYSTORE" -signedjar "$DIST_DIR/$APP_NAME-$ver-release.aab")
    [ -n "${MESHCONNECT_KEYSTORE_PASS:-}" ] && jargs+=(-storepass:env MESHCONNECT_KEYSTORE_PASS)
    if [ -n "${MESHCONNECT_KEY_PASS:-}" ]; then
        jargs+=(-keypass:env MESHCONNECT_KEY_PASS)
    elif [ -n "${MESHCONNECT_KEYSTORE_PASS:-}" ]; then
        jargs+=(-keypass:env MESHCONNECT_KEYSTORE_PASS)
    fi
    "$JDK_DIR/bin/jarsigner" "${jargs[@]}" "$aab" "${MESHCONNECT_KEY_ALIAS:-}" >/dev/null
    info "Done: $out"
}

install_debug() {
    build_debug
    info "Installing on connected device"
    local out
    # --no-streaming: push then `pm install`, which survives flaky USB tunnels.
    # -d: local builds may carry a lower versionCode than a tag-built install.
    if ! out="$("$ADB" install -r -d --no-streaming "$DIST_DIR/$APP_NAME-debug.apk" 2>&1)"; then
        echo "$out" >&2
        # After distclean the debug key is new, and Android refuses the update.
        # Uninstalling wipes the app's data (message database), so leave that to the user.
        case "$out" in
            *INSTALL_FAILED_UPDATE_INCOMPATIBLE*)
                die "Signed with a different debug key. Run: $ADB uninstall $(debug_app_id)" ;;
        esac
        die "adb install failed."
    fi
}

logcat() {
    local pid
    pid="$("$ADB" shell pidof -s "$(debug_app_id)" || true)"
    [ -n "$pid" ] || die "$(debug_app_id) is not running."
    "$ADB" logcat --pid="$pid"
}

run_app() {
    install_debug
    info "Launching $(debug_app_id)"
    "$ADB" shell monkey -p "$(debug_app_id)" -c android.intent.category.LAUNCHER 1 >/dev/null
    sleep 1
    logcat
}

check() {
    setup
    mkdir -p "$MESHCONNECT_ROBOLECTRIC_DIR/.m2/repository"
    gradle :shared:testDebugUnitTest :androidApp:testDebugUnitTest
}

print_env() {
    setup >&2
    printf 'export JAVA_HOME=%q ANDROID_HOME=%q GRADLE_USER_HOME=%q ANDROID_USER_HOME=%q MESHCONNECT_ROBOLECTRIC_DIR=%q\n' \
        "$JAVA_HOME" "$ANDROID_HOME" "$GRADLE_USER_HOME" "$ANDROID_USER_HOME" "$MESHCONNECT_ROBOLECTRIC_DIR"
    printf 'export PATH=%q:"$PATH"\n' "$JDK_DIR/bin:$SDK_DIR/platform-tools:$SDK_DIR/cmdline-tools/latest/bin"
    printf 'unset ANDROID_SDK_ROOT\n'
}

# Remove build outputs.
clean() {
    rm -rf "$ROOT_DIR/build" "$ROOT_DIR/androidApp/build" "$ROOT_DIR/shared/build" \
        "$ROOT_DIR/.gradle" "$ROOT_DIR/.kotlin" "$DIST_DIR"
}

# Remove build outputs and the downloaded toolchain.
distclean() {
    clean
    rm -rf "$TOOLS_DIR" "$ROOT_DIR/local.properties"
}

case "${1:-debug}" in
    debug) build_debug ;;
    release) build_release ;;
    install) install_debug ;;
    run) run_app ;;
    logcat) logcat ;;
    test) check ;;
    gradle) shift; setup; mkdir -p "$MESHCONNECT_ROBOLECTRIC_DIR/.m2/repository"; gradle "$@" ;;
    env) print_env ;;
    setup) setup ;;
    clean) clean ;;
    distclean) distclean ;;
    -h|--help|help) sed -n '2,28p' "$0" | sed 's/^# \{0,1\}//' ;;
    *) die "Unknown command '$1'. Run ./build.sh help." ;;
esac
