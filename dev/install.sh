#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/common.sh"
cd "$DEV_ROOT"
require_emulator
BT="$ANDROID_HOME/build-tools/$(ls "$ANDROID_HOME/build-tools" | sort -V | tail -1)"
stage=$(mktemp -d "$DEV_STATE/install.XXXXXX")
trap 'rm -rf "$stage"' EXIT
apks=(FreeMyTV.apk out/tests/checks.apk out/fixture/fixture.apk out/fixture/fixture-extra.apk)
packages=(io.freemytv io.freemytv.tests io.freemytv.fixture io.freemytv.fixture.extra)
for i in "${!apks[@]}"; do
  apk="${apks[$i]}"; package="${packages[$i]}"
  "$BT/apksigner" verify "$apk"
  installed_path=$("${ADB[@]}" shell pm path "$package" | tr -d '\r' || true)
  if [[ "$installed_path" = package:/* && "$installed_path" != *$'\n'* ]] &&
      "${ADB[@]}" pull "${installed_path#package:}" "$stage/installed.apk" >/dev/null 2>&1 &&
      cmp -s "$apk" "$stage/installed.apk"; then
    echo "Reusing byte-identical installed $package"
  else
    timeout 240 "${ADB[@]}" install --no-incremental -r "$apk"
  fi
done
