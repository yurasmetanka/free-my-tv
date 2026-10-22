#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/common.sh"
require_emulator
output="${1:-$DEV_STATE/home.png}"
mkdir -p "$(dirname "$output")"
timeout 90 "${ADB[@]}" exec-out screencap -p > "$output"
python3 - "$output" <<'PY'
from pathlib import Path
import struct, sys
p=Path(sys.argv[1]); data=p.read_bytes()
if data[:8] != b'\x89PNG\r\n\x1a\n' or len(data) < 24:
    raise SystemExit('Screenshot failed: no valid PNG')
w,h=struct.unpack('>II',data[16:24])
if w <= h: raise SystemExit(f'Expected landscape TV screenshot, got {w}x{h}')
print(f'Screenshot: {p} ({w}x{h})')
PY
