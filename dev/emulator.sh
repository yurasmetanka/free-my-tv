#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/common.sh"
case "${1:-start}" in
  setup)
    CLI="$ANDROID_HOME/cmdline-tools/19.0/bin"
    [ -x "$CLI/sdkmanager" ] || CLI="$ANDROID_HOME/cmdline-tools/latest/bin"
    proxy_args=()
    if [ -n "${HTTPS_PROXY:-}" ]; then
      read -r proxy_host proxy_port < <(python3 - <<'PY'
import os, urllib.parse
u=urllib.parse.urlparse(os.environ['HTTPS_PROXY'])
print(u.hostname, u.port or 80)
PY
)
      proxy_args=(--proxy=http "--proxy_host=$proxy_host" "--proxy_port=$proxy_port")
    fi
    set +o pipefail
    yes | "$CLI/sdkmanager" --sdk_root="$ANDROID_HOME" "${proxy_args[@]}" --licenses >"$DEV_STATE/licenses.log" 2>&1
    sdk_status=${PIPESTATUS[1]}
    set -o pipefail
    [ "$sdk_status" -eq 0 ] || { cat "$DEV_STATE/licenses.log"; exit "$sdk_status"; }
    "$CLI/sdkmanager" --sdk_root="$ANDROID_HOME" "${proxy_args[@]}" emulator 'system-images;android-28;android-tv;x86'
    if [ ! -f "$ANDROID_AVD_HOME/$DEV_AVD.ini" ]; then
      printf 'no\n' | "$CLI/avdmanager" create avd --name "$DEV_AVD" --package 'system-images;android-28;android-tv;x86' --path "$ANDROID_AVD_HOME/$DEV_AVD.avd"
      python3 - "$ANDROID_AVD_HOME/$DEV_AVD.avd/config.ini" <<'PY'
import sys
from pathlib import Path
p=Path(sys.argv[1]); v=dict(line.split('=',1) for line in p.read_text().splitlines() if '=' in line)
v.update({'hw.lcd.width':'1280','hw.lcd.height':'720','hw.lcd.density':'213',
          'hw.initialOrientation':'landscape','hw.dPad':'yes','hw.mainKeys':'yes',
          'skin.name':'1280x720','skin.path':'1280x720','showDeviceFrame':'no'})
p.write_text(''.join(k+'='+value+'\n' for k,value in v.items()))
PY
    fi
    echo "Prepared $DEV_AVD. Run bash dev/emulator.sh start in a separate terminal."
    ;;
  start)
    [ -f "$ANDROID_AVD_HOME/$DEV_AVD.ini" ] || { echo 'Run bash dev/emulator.sh setup first.' >&2; exit 1; }
    port="${DEV_SERIAL#emulator-}"
    [[ "$port" =~ ^[0-9]+$ ]] && (( port % 2 == 0 )) || { echo 'DEV_SERIAL must be emulator-<even port>.' >&2; exit 1; }
    # Run in foreground so the terminal/session owns the emulator lifetime.
    echo "$$" > "$DEV_STATE/$DEV_AVD.pid"
    exec "$ANDROID_HOME/emulator/emulator" -avd "$DEV_AVD" -port "$port" -no-window -no-audio -no-snapshot -no-boot-anim \
      -accel "${DEV_ACCEL:-off}" -gpu swiftshader -feature -Vulkan -memory 2048 -cores 2
    ;;
  wait)
    # First software boot can take several minutes; each individual ADB call is bounded.
    for attempt in $(seq 1 120); do
      if [ "$(timeout 5 "${ADB[@]}" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = 1 ]; then
        require_emulator; echo "$DEV_SERIAL booted"; exit 0
      fi
      sleep 3
    done
    echo 'Emulator boot timed out; inspect its terminal output.' >&2; exit 1
    ;;
  stop)
    pid_file="$DEV_STATE/$DEV_AVD.pid"
    if [ -f "$pid_file" ]; then
      read -r emulator_pid < "$pid_file"
      [[ "$emulator_pid" =~ ^[0-9]+$ ]] || { echo 'Invalid emulator PID file.' >&2; exit 1; }
      command_line=$(ps -p "$emulator_pid" -o args= || true)
      if [[ "$command_line" = *emulator* && "$command_line" = *"-avd $DEV_AVD "* ]]; then
        kill "$emulator_pid"
        rm -f "$pid_file"
        exit 0
      fi
    fi
    require_emulator
    "${ADB[@]}" emu kill
    ;;

  *) echo 'Usage: bash dev/emulator.sh {setup|start|wait|stop}' >&2; exit 2 ;;
esac
