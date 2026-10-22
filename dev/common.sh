#!/usr/bin/env bash
# Sourced by dev commands; no production app settings are changed here.
DEV_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
: "${ANDROID_HOME:=${ANDROID_SDK_ROOT:-}}"
[ -d "$ANDROID_HOME/build-tools" ] || { echo 'Set ANDROID_HOME to your Android SDK (see dev/README.md).' >&2; exit 1; }
export ANDROID_HOME
export PATH="${JAVA_HOME:+$JAVA_HOME/bin:}$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"
export ANDROID_AVD_HOME="${ANDROID_AVD_HOME:-$DEV_ROOT/.local/dev/avd}"
export ANDROID_EMULATOR_HOME="${ANDROID_EMULATOR_HOME:-$DEV_ROOT/.local/dev/emulator}"
DEV_STATE="$DEV_ROOT/.local/dev"
DEV_SERIAL="${DEV_SERIAL:-emulator-5556}"
DEV_AVD="${DEV_AVD:-freemytv-dev}"
mkdir -p "$DEV_STATE" "$ANDROID_AVD_HOME" "$ANDROID_EMULATOR_HOME"
ADB=(adb -s "$DEV_SERIAL")
require_emulator() {
  [ "$("${ADB[@]}" shell getprop ro.kernel.qemu | tr -d '\r')" = 1 ] || {
    echo 'Refusing to change a physical device. Select a dedicated emulator with DEV_SERIAL.' >&2; exit 1;
  }
}
