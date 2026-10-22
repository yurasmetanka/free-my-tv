#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/common.sh"
require_emulator
scenario="${1:-populated}"
[[ "$scenario" = populated || "$scenario" = empty ]] || { echo 'Use populated or empty.' >&2; exit 2; }
publisher="${2:-io.freemytv.fixture}"
[[ "$publisher" = io.freemytv.fixture || "$publisher" = io.freemytv.fixture.extra ]] || { echo 'Unknown fixture publisher.' >&2; exit 2; }
run_id="seed-$$-$(date +%s)"
"${ADB[@]}" shell am force-stop "$publisher"
"${ADB[@]}" shell am start -W -n "$publisher/io.freemytv.fixture.Seed" --es scenario "$scenario" --es run_id "$run_id"
for attempt in $(seq 1 60); do
  log=$("${ADB[@]}" logcat -d -s FixtureCinema:I)
  if [[ "$log" = *"SEED_OK $run_id $scenario"* ]]; then echo "Seeded $scenario"; exit 0; fi
  if [[ "$log" = *"SEED_FAILED $run_id"* ]]; then echo "$log" >&2; exit 1; fi
  sleep 1
done
echo 'Fixture seeding timed out.' >&2; exit 1
