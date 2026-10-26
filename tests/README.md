# Android recommendation checks

No Gradle or test libraries. Build the app first, then build and install the separate instrumentation APK with the same signing key:

```sh
./build.sh
bash tests/build.sh
adb install --no-incremental -r FreeMyTV.apk
adb install --no-incremental -r out/tests/checks.apk
adb shell am instrument -w io.freemytv.tests/io.freemytv.RecommendationsTest
```

The checks cover the key-independent default, source persistence, Off, shelf removal and filtering, Movies, Shows and Watch Next (clips/music stay in Explore), intents staying inside the publishing app, and package resolution staying off the UI thread for both custom and built-in shelves. Repeated UI filtering and screensaver tab reads use cached eligibility. Without a TMDB key, they also verify that Discover has no picks. They run in the home process, temporarily change the app's shelf and recommendation preferences, and restore both before finishing. No provider data or TV settings are changed.

Run with TV-listing permission granted to exercise the filters, and with it denied to check that cached app recommendations are hidden. Restore the original permission afterward. Reopen the home screen when finished. The test APK can be removed with `adb uninstall io.freemytv.tests`.

Also check on the TV: switching sources and restarting, all four app-feed tabs and all three Discover tabs, scrolling to both ends, selected-card expansion, empty feeds, artwork and app-logo placeholders, content links, and the screensaver.

For real provider integration and an interactive offline UI environment, see [dev/README.md](../dev/README.md). `FixtureIntegrationTest` and `OfflineNetwork` are compiled only into the test APK. Their runner changes fixture settings on an emulator and is not intended for a physical TV.

`AndroidUpdatesTest` runs only on a disposable emulator. It covers launcher-switch package guards/order, the screensaver component, weather DST and null daily values, sleep alarm scheduling/cancellation, shelf scrolling and reorder/cancel, the shared native carousel, source transitions, top-button removal, settings scrolling, rounded artwork rendering, personal photo import, and language loading. It does not switch the emulator's launcher or disable packages. It creates and removes photos in the disposable app's storage; do not use it on an emulator with personal app data.

```sh
adb -s emulator-5556 shell am instrument -w -r io.freemytv.tests/io.freemytv.AndroidUpdatesTest
```

The UI checks can run on a phone emulator image using injected cards. The full `FixtureIntegrationTest` suite still requires an Android TV image with the TV provider and TV-listing permission. Neither compiled tests nor injected-card checks establish actual streaming-app publication or firmware behavior.
