#!/usr/bin/env bash
# Keep instrumentation's offline network transport alive for interactive screenshots / D-pad development.
set -euo pipefail
source "$(dirname "$0")/common.sh"
cd "$DEV_ROOT"
bash dev/emulator.sh wait
require_emulator
"${ADB[@]}" shell svc wifi disable
"${ADB[@]}" shell pm grant io.freemytv android.permission.READ_TV_LISTINGS
bash dev/seed.sh populated
trap '"${ADB[@]}" shell am force-stop io.freemytv >/dev/null 2>&1 || true' EXIT
"${ADB[@]}" shell am instrument -w -r -e scenario populated -e hold_ms 86400000 io.freemytv.tests/io.freemytv.FixtureIntegrationTest | tee "$DEV_STATE/session.log"
