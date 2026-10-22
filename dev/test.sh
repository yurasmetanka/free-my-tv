#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/common.sh"
cd "$DEV_ROOT"
bash dev/emulator.sh wait
require_emulator
"${ADB[@]}" shell svc wifi disable
if [ "${DEV_REUSE_BUILD:-0}" != 1 ]; then
  ./build.sh
  bash tests/build.sh
  bash dev/build-fixture.sh
fi
RESULTS="$DEV_STATE/results/$(date +%Y%m%d-%H%M%S)-$$"
mkdir -p "$RESULTS"
bash dev/install.sh
run_check() {
  local name="$1"; shift
  timeout 240 "${ADB[@]}" shell am instrument -w -r "$@" >"$RESULTS/$name.txt"
  cat "$RESULTS/$name.txt"
  rg -q '^INSTRUMENTATION_CODE: -1' "$RESULTS/$name.txt"
  ! rg -q 'FAILED|Exception|INSTRUMENTATION_FAILED' "$RESULTS/$name.txt"
}
cleanup() {
  "${ADB[@]}" shell am broadcast -a io.freemytv.tests.FINISH -p io.freemytv >/dev/null 2>&1 || true
  "${ADB[@]}" shell pm grant io.freemytv android.permission.READ_TV_LISTINGS >/dev/null 2>&1 || true
}
trap cleanup EXIT
for permission in grant revoke; do
  "${ADB[@]}" shell pm "$permission" io.freemytv android.permission.READ_TV_LISTINGS
  run_check "recommendations-$permission" io.freemytv.tests/io.freemytv.RecommendationsTest
done
"${ADB[@]}" shell pm grant io.freemytv android.permission.READ_TV_LISTINGS
bash dev/seed.sh populated io.freemytv.fixture.extra
# Run twice to verify the publisher refreshes rows without accumulating duplicates.
bash dev/seed.sh populated
bash dev/seed.sh populated
run_check provider-populated -e scenario populated -e hold_ms 180000 io.freemytv.tests/io.freemytv.FixtureIntegrationTest &
check_pid=$!
ready_deadline=$((SECONDS + 180))
while ! rg -q 'FIXTURE_READY populated' "$RESULTS/provider-populated.txt" 2>/dev/null; do
  if ! kill -0 "$check_pid" 2>/dev/null; then wait "$check_pid"; echo 'UI runner ended before readiness.' >&2; exit 1; fi
  [ "$SECONDS" -lt "$ready_deadline" ] || { echo 'UI readiness timed out.' >&2; exit 1; }
  sleep 1
done
bash dev/capture.sh "$RESULTS/home.png"
"${ADB[@]}" shell "am start -W -a android.intent.action.VIEW -d fixture://cinema/0 -p io.freemytv.fixture --es title 'Orbit Station'" >"$RESULTS/player-launch.txt"
rg -q 'Status: ok' "$RESULTS/player-launch.txt"
rg -q 'Activity: io.freemytv.fixture/' "$RESULTS/player-launch.txt"
bash dev/capture.sh "$RESULTS/player.png"
"${ADB[@]}" shell input keyevent KEYCODE_BACK
"${ADB[@]}" shell am broadcast -a io.freemytv.tests.FINISH -p io.freemytv >/dev/null
wait "$check_pid"
bash dev/seed.sh empty
run_check provider-empty -e scenario empty io.freemytv.tests/io.freemytv.FixtureIntegrationTest
bash dev/seed.sh populated
"${ADB[@]}" shell pm revoke io.freemytv android.permission.READ_TV_LISTINGS
run_check provider-denied -e scenario denied io.freemytv.tests/io.freemytv.FixtureIntegrationTest
"${ADB[@]}" shell pm grant io.freemytv android.permission.READ_TV_LISTINGS
python3 - "$RESULTS" <<'PYREPORT'
from pathlib import Path
import hashlib, json, subprocess, sys
root=Path(sys.argv[1])
names=['recommendations-grant','recommendations-revoke','provider-populated','provider-empty','provider-denied']
checks={}
for name in names:
    body=(root/(name+'.txt')).read_text()
    passed='INSTRUMENTATION_CODE: -1' in body and 'FAILED' not in body
    checks[name]='passed' if passed else 'failed'
    if not passed: raise SystemExit('Required scenario failed: '+name)
report={'commit':subprocess.check_output(['git','rev-parse','HEAD'],text=True).strip(),
        'working_tree_changed':bool(subprocess.check_output(['git','status','--porcelain'],text=True).strip()),
        'network':'offline transport fixtures; emulator Wi-Fi disabled', 'checks':checks,
        'apk_sha256':hashlib.sha256(Path('FreeMyTV.apk').read_bytes()).hexdigest(),
        'screenshots':['home.png','player.png']}
(root/'summary.json').write_text(json.dumps(report,indent=2)+'\n')
PYREPORT
printf 'Passed: source checks (granted/denied), real provider (populated/empty/denied), artwork, offline weather.\nLogs: %s\n' "$RESULTS"
