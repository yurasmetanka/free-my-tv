#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
: "${ANDROID_HOME:=${ANDROID_SDK_ROOT:-}}"
[ -d "$ANDROID_HOME/build-tools" ] || { echo 'Set ANDROID_HOME to an installed SDK.' >&2; exit 1; }
BT="$ANDROID_HOME/build-tools/$(ls "$ANDROID_HOME/build-tools" | sort -V | tail -1)"
AJ="$(ls -d "$ANDROID_HOME"/platforms/android-*/android.jar | sort -V | tail -1)"
[ -f freemytv.keystore ] || { echo 'Run ./build.sh first.' >&2; exit 1; }
mkdir -p out/fixture/classes
"${JAVA_HOME:+$JAVA_HOME/bin/}javac" -encoding UTF-8 -nowarn -source 8 -target 8 -bootclasspath "$AJ:$BT/core-lambda-stubs.jar" -classpath "$AJ" -d out/fixture/classes $(find dev/fixture/src -name '*.java')
"$BT/d8" --release --min-api 28 --lib "$AJ" --output out/fixture $(find out/fixture/classes -name '*.class')
for variant in fixture fixture-extra; do
  python3 - "$variant" <<'PYMANIFEST'
import sys
from pathlib import Path
s=Path('dev/fixture/AndroidManifest.xml').read_text()
if sys.argv[1] == 'fixture-extra':
    s=s.replace('package="io.freemytv.fixture"', 'package="io.freemytv.fixture.extra"')
    s=s.replace('android:authorities="io.freemytv.fixture.art"', 'android:authorities="io.freemytv.fixture.extra.art"')
    s=s.replace('android:label="Fixture Cinema"', 'android:label="Fixture Extra"')
Path('out/fixture/manifest.xml').write_text(s)
PYMANIFEST
"$BT/aapt2" link -o out/fixture/base.apk -I "$AJ" --manifest out/fixture/manifest.xml --min-sdk-version 28 --target-sdk-version 33
(cd out/fixture && zip -qj base.apk classes.dex)
"$BT/zipalign" -f -p 4 out/fixture/base.apk out/fixture/aligned.apk
"$BT/apksigner" sign --ks freemytv.keystore --ks-pass pass:freemytv --key-pass pass:freemytv --out "out/fixture/$variant.apk" out/fixture/aligned.apk
"$BT/apksigner" verify "out/fixture/$variant.apk"
done
echo 'Built out/fixture/fixture.apk and fixture-extra.apk (development only)'
