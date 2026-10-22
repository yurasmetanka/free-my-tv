# Android TV development infrastructure

The default cycle is offline and runs on a **dedicated disposable Android TV emulator**. No streaming accounts, API keys, developer certificates, or real TV are needed. Production APK behavior is unchanged.

## Prerequisites

Linux x86-64, Bash, Python 3, ripgrep, GNU `timeout`, JDK 17+, Android SDK command-line tools, platform android-34, Build-Tools 34.0.0, platform-tools, zip/unzip. Export `JAVA_HOME` and `ANDROID_HOME`. In this cloud environment use `source /workspace/setup/env.sh` to activate the installed tools.

```sh
# Once: downloads the verified SDK emulator/image and creates a 1280x720 TV AVD.
bash dev/emulator.sh setup

# Terminal 1: owns the emulator process (software CPU/GPU by default).
bash dev/emulator.sh start

# Terminal 2: builds, installs, seeds and runs the checks.
bash dev/test.sh
```

Software first boot can take several minutes; no KVM is required. On a Linux host with working KVM, set `DEV_ACCEL=on`. By default these commands use `freemytv-dev` on `emulator-5556`, leaving an existing emulator on 5554 alone. To select a different dedicated emulator, export `DEV_SERIAL=emulator-5554` in every terminal. ADB mutation scripts refuse physical devices. `bash dev/emulator.sh stop` stops the selected emulator.

Installation verifies APK signatures and reuses an installed APK only after pulling it and comparing its bytes with the local artifact. `DEV_REUSE_BUILD=1 bash dev/test.sh` skips compilation when intentionally reusing already built APKs; the default always builds first.

AVDs, screenshots and per-run logs live in ignored `.local/dev/`, outside `out/` so builds do not erase them. An SDK proxy uses the inherited HTTPS proxy; keep the environment's CA trust enabled. If Java needs the host's trust store, configure `JAVA_TOOL_OPTIONS=-Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts` where that file exists. ADB may require a writable `~/.android`; do not redirect HOME to work around a sandbox restriction.

## What is real and what is mocked

- **Real:** separately installed, launchable `io.freemytv.fixture` (Fixture Cinema) and `io.freemytv.fixture.extra` (Fixture Extra) apps, Android TV Preview Programs and Watch Next provider, package ownership, ContentResolver queries, media types, content intents, cross-app artwork URIs, launcher filtering, artwork download/cache and the home activity.
- **Generated fixtures:** six previews per app (movies, shows, a clip and deliberately missing artwork), one Continue Watching entry per app, and locally drawn PNG artwork. The mock player displays the received content link. Reseeding replaces only the fixture app's rows; it does not accumulate duplicates or touch other publishers.
- **Offline network:** test instrumentation installs a process-local HTTPS URL handler before launching the home screen. Open-Meteo and Binance requests receive known forecast/price JSON through `HttpURLConnection`; the production parsers and UI still run. Other HTTPS requests fail explicitly. TLS verification is not disabled and nothing is added to the release app, manifest, or build config. This is an application transport mock, not proof of DNS, TLS or real API compatibility. The fixture app itself needs no internet permission.
- **Checks:** existing recommendation tests with permission granted/denied; real provider feeds with populated, empty and permission-denied scenarios; exact category counts; exclusion of the second publisher outside the selected shelf; resolvable content link; actual artwork decoding/cache; forecast and price values; confirmed request execution; landscape home/player screenshots; actual mock-player launch. Host commands check instrumentation result codes and fail on runner failures, including when `am instrument` exits zero.

The offline test/session commands disable emulator Wi-Fi, so a passing run cannot depend on a public API.

The fixtures temporarily populate the launcher's settings/cache and custom shelf on the disposable emulator. They are not installed on, or included with, production releases.

## Interactive UI development

After a successful `dev/test.sh`, run:

```sh
bash dev/session.sh
```

Wait for `FIXTURE_READY populated picks=7 weather=23 prices=65000`. The process holds instrumentation open so the offline transport stays installed while you inspect the real launcher, navigate its tabs, or capture images. It ends after 24 hours or Ctrl+C. Use a second terminal:

```sh
adb -s emulator-5556 shell input keyevent KEYCODE_DPAD_UP
adb -s emulator-5556 shell input keyevent KEYCODE_DPAD_RIGHT
adb -s emulator-5556 shell input keyevent KEYCODE_DPAD_CENTER
adb -s emulator-5556 shell input keyevent KEYCODE_BACK
bash dev/capture.sh .local/dev/home.png
# End the session cleanly:
adb -s emulator-5556 shell am broadcast -a io.freemytv.tests.FINISH -p io.freemytv
```

When instrumentation ends its process is stopped, and the mock transport is gone. Start `dev/session.sh` again for offline screenshots; launching the release activity alone uses its normal network implementation. To remove fixture preferences/cache, stop the session and run `adb -s emulator-5556 shell pm clear io.freemytv` on the disposable emulator. This deletes app data.

`bash dev/seed.sh empty` and `bash dev/seed.sh populated` change provider data while the UI session runs; the launcher's real ContentObserver should refresh it. Rows can be inspected through the TV provider, and publisher failures are tagged `FixtureCinema` in logcat.

## Real-network/device testing

Do not require public internet for the offline development cycle. The cloud host's proxy/allowlist is separate from Android guest networking: emulator Wi-Fi connectivity does not establish API access. Real weather, Binance and TMDB checks need their destinations allowed and a supported proxy/CA route inside Android; do not disable TLS validation or bypass the environment's proxy. Those live API checks are not claimed by this suite.

Physical TVs remain necessary for performance, firmware permissions, remote buttons, manufacturer launcher switching, real service publication/deep links, and actual video playback. The fixture player does not decode video.

## CI

`.github/workflows/android.yml` runs the same offline suite on pushes to this fork’s `main` and pull requests. Its `android-tv-smoke` artifact contains the app and development APKs, per-scenario runner output, and home/player screenshots. The workflow uses the GitHub Ubuntu runner’s Android SDK and JDK 17; software emulation keeps it independent of KVM availability. A local passing run does not establish that a remote Actions run has completed.
