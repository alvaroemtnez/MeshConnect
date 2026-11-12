#!/usr/bin/env bash
# tools/emulator.sh - headless AOSP emulator from .tools/ (no Google services).
#
#   tools/emulator.sh start    create the AVD on first use, boot it headless, wait for boot
#   tools/emulator.sh stop     shut it down
#   tools/emulator.sh shot F   save a screenshot to F
#
# Renderer: swangle_indirect. swiftshader_indirect segfaults (exit 139) as the
# graphics composer starts on this image.
#
# Needs /dev/kvm and, once:
#   eval "$(./build.sh env)"
#   android --no-metrics --sdk=$ANDROID_HOME sdk install emulator system-images/android-37.0/default/x86_64
#
# Android 17 (API 37), what the app targets since 1.2.0, so the target-SDK rules (boot start,
# foreground service types, the local network permission) apply. A new AVD name, so an
# existing API 34 "meshconnect" AVD and its data are left alone.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
eval "$("$ROOT/build.sh" env 2>/dev/null)"
export ANDROID_AVD_HOME="$ANDROID_USER_HOME/avd"
AVD=meshconnect-37
IMG="system-images/android-37.0/default/x86_64"
ADB="$ANDROID_HOME/platform-tools/adb"

create() {
    [ -d "$ANDROID_HOME/$IMG" ] || { echo "Install $IMG first (see header)"; exit 1; }
    mkdir -p "$ANDROID_AVD_HOME/$AVD.avd"
    cat > "$ANDROID_AVD_HOME/$AVD.ini" <<INI
avd.ini.encoding=UTF-8
path=$ANDROID_AVD_HOME/$AVD.avd
target=android-37.0
INI
    cat > "$ANDROID_AVD_HOME/$AVD.avd/config.ini" <<INI
image.sysdir.1=$IMG/
abi.type=x86_64
hw.cpu.arch=x86_64
hw.cpu.ncore=4
hw.ramSize=4096
hw.lcd.width=1080
hw.lcd.height=2340
hw.lcd.density=440
hw.keyboard=yes
hw.gpu.enabled=yes
hw.gpu.mode=swangle_indirect
hw.camera.back=emulated
disk.dataPartition.size=6G
fastboot.forceColdBoot=no
INI
}

case "${1:-start}" in
    start)
        [ -f "$ANDROID_AVD_HOME/$AVD.avd/config.ini" ] || create
        nohup "$ANDROID_HOME/emulator/emulator" -avd "$AVD" -no-window -no-audio -no-boot-anim -gpu swangle_indirect \
            -no-snapshot-save > "$ROOT/.tools/emulator.log" 2>&1 &
        "$ADB" wait-for-device
        until [ "$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = 1 ]; do sleep 2; done
        echo "emulator booted"
        ;;
    stop) "$ADB" emu kill ;;
    shot) "$ADB" exec-out screencap -p > "$2" ;;
    *) sed -n '2,10p' "$0" ;;
esac
