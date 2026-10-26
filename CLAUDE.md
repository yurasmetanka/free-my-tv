# Free My TV: notes for Claude

A home screen for Google TV and Android TV that replaces the stock one, plus a settings app that switches between
Free My TV and the TV's original home screen. A plain Android app in Java, built without Gradle by `build.sh`.

The person you're helping may not be technical. Keep it short and friendly, say what you're about to do on their TV
before you do it, and let them do the taps on the TV.

## Installing it for someone

1. **Ask** for the TV's IP address and their city, if they didn't say.
2. **Tools:** a JDK 17+, the Android SDK command-line tools (build-tools 34+, platform android-34) and adb. Check
   what's already there (`java -version`, `$ANDROID_HOME`, `adb version`) and install only what's missing.
   - macOS: `brew install --cask temurin android-commandlinetools android-platform-tools`, then
     `sdkmanager "build-tools;34.0.0" "platforms;android-34"`.
   - Linux: OpenJDK 17, Android's command-line tools and adb from the package manager, then the same `sdkmanager` line.
   - Windows: WSL, then as on Linux.
3. **Settings:** run `./build.sh` once; it creates `config.properties`. Fill in `latitude` and `longitude` (decimal
   degrees) and `city` for their city, `tmdb_region` (two-letter country), and `temperature` to match.
4. **Android TV recommendations:** *Home Settings* → *Recommendations* chooses *From your apps* (the default),
   *Discover (TMDB)* or *Off*. App feeds read Android TV Preview Programs and Watch Next from shelf apps only;
   allow TV-listing access when prompted. App-feed tabs are Explore, Movies, Shows and Continue Watching;
   movie/show categories use the publisher’s media type, while music and clips stay in Explore.
   Only apps that publish entries contribute. Three cards fit onscreen,
   the selected card expands, and the row scrolls to the rest. Cards open the publishing app's content link;
   unavailable artwork uses its app logo. No key is needed for app feeds. Discover keeps the original TMDB feed,
   installed-service matching and three hourly picks, with Movies and Shows tabs. Both sources share the expanding card row. Existing installs start with
   app feeds too; their TMDB configuration stays available when they select Discover. Off shows the clock.
   **The TMDB key is theirs and secret.** For Discover, ask them to create one
   (free: themoviedb.org → Settings → API) and paste it after `tmdb_key=` in `config.properties` themselves.
   Don't ask for it in the chat, never print it, never commit it. If they paste it in the chat anyway, put it only
   in `config.properties`.
5. **Build:** `./build.sh` → `FreeMyTV.apk`. Keep `freemytv.keystore`: updates must be signed with the same key.
6. **Install:** `adb connect <IP>:5555`. The TV asks *Allow USB debugging?*: tell them to accept and tick *Always
   allow*. Then `adb install -r FreeMyTV.apk`. On TCL, also `adb shell "appops set io.freemytv AUTO_START allow || appops set io.freemytv APP_AUTO_START allow"`
   (some firmware calls the operation `APP_AUTO_START`; this tries both):
   TCL's `TclAppBoot` blocks background jobs by default, so the switch (and the watchdog) never run. The log shows
   `forbid bind service ... ModeJob`.
7. **Tell them what the switch does** before they make it: the home screen and screensaver change, a short list of
   promo apps is turned off (never uninstalled), and *Original* brings it all back. On TCL, the remote's Netflix,
   YouTube and Prime buttons keep working through a small accessibility service that only reads those buttons
   (*Free My TV* → *Remote Buttons* picks what each opens); *Original* turns it off.
8. **They switch on the TV:** *Free My TV* → *Home Screen* → *Free My TV*. The app uses the TV's own ADB on
   127.0.0.1 with its own key, so the TV asks once more to allow debugging. Don't switch modes from the computer.
   If the TV's Wi-Fi was saved during its first setup, the switch stops before changing anything and asks them to
   forget the network and connect again in Settings (Network & Internet); then the switch goes through. Saved
   networks out of range don't show in Settings: find the ones with `cname=com.google.android.tungsten.setupwraith`
   in `adb shell dumpsys wifi` (each block starts with its `ID:`), and with their OK remove them with
   `adb shell cmd wifi forget-network <id>`.
9. **Check:** `adb logcat -s FreeMyTV` shows what the switch did.

## Adapting it to another TV

Read first, change after. Read-only commands that tell you what the TV has:

```bash
adb shell getprop ro.product.brand; adb shell getprop ro.product.model; adb shell getprop ro.build.version.release
adb shell cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME
adb shell cmd role get-role-holders android.app.role.HOME
adb shell settings get secure screensaver_components; adb shell settings get secure screensaver_default_component
adb shell pm list packages -s     # system apps (-3 for the ones they installed)
```

Then, in the code:

- `HomeMode.DEBLOAT`: add the brand's promo, demo, store-mode and telemetry apps, one comment per line. Only those.
- `HomeMode.PROTECTED`: add the brand's system pieces: picture, sound, inputs and HDMI-CEC, the tuner and live TV,
  remote and Bluetooth pairing, setup, updates, system UI. When in doubt, protect it.
- `HomeApps.SHELF`, `LIVE_TV_APPS` and `HIDDEN`: the shelf apps, the brand's live TV app, apps to hide from All Apps.
- Build, install, have them switch on the TV, read `adb logcat -s FreeMyTV`, then have them switch back to
  *Original* once and confirm everything returned.

For TVs without Google TV or Android TV, use a Google TV streamer on HDMI.

**Uninstalled while in Free My TV mode?** Reinstall and choose Original. The disabled-package record also lives in
the TV's global settings. Do not bulk-enable packages: preserve those the owner had already disabled.

## The code

- Java 8 language level, minSdk 26 (Android 8), targetSdk 33. No Gradle, no libraries: only the Android SDK.
  Android 8 and 9 have no RenderNode (`Recording` wraps it, with a Picture there) and no `cmd role`. On Android 8 to 11
  (`Ui.LEGACY`) the home screen skips the GPU layers and draws blurred shadows from cached bitmaps (`Shadows`);
  all versions round images with image-owned shaders instead of clipPath: their GPUs (a Mi Box 3's Mali-450) choked on those.
  Android 11 to 12L (API 30–32) run the screensaver as `ScreensaverAlone`, in the main process; Android 8–10 and
  13+ use `Screensaver` in `:home`. The versioned resource booleans and `HomeMode.DREAM` must agree.
  `ScreensaverAlone` reloads cross-process preferences when it starts. Keep both services in step.
  To check for newer APIs, compile against `platforms/android-26/android.jar`: only AGSL (`RuntimeShader`, guarded
  by a try/catch), `BitmapShader.setFilterMode` (guarded), `Recording.Node`, `Ui.Weighted`, and guarded
  `Activity.setTranslucent` should come up.
- The home screen is drawn by hand on one Canvas (`HomeView`); the glass and the sky are AGSL shaders (`Shaders`,
  Android 13+, with a fallback for 8 to 12). Keep it at 60 fps: no allocations in `onDraw`, no work on the UI thread.
- `HomeMode` runs every switch as a background job (`ModeJob`) in the main process; the UI is in the `:home`
  process. `Watchdog` goes back to the stock home screen after 3 crashes in 5 minutes.
- On-screen text: `Ui.tr("English")`, `Ui.trf("Pattern {name}", "name", value)`, and `Ui.plural` use JSON catalogues
  in `app/assets/i18n`. Preserve named placeholders and date formats. Run `python3 tools/translate.py --check`.
  Native-source strings retain their existing PT/ES/DE/RU translations; other catalogues may use English fallback.
- Settings come from `config.properties` through the generated `out/gen/io/freemytv/Config.java` (never edit that).
  What the person changes on the TV (`Prefs`: Home Settings, Shelf, Remote Buttons) wins over those first values.
- The remote's app buttons (`RemoteKeys`, `RemoteKeyService`) use each brand's key codes: they run only on TCL and
  Vestel TVs, the service is on only in Free My TV mode, and *Original* takes it out of the enabled accessibility
  services. The system waits for the service on every key, so it lives alone in the `:keys` process: what it reads is
  in `remote-keys.txt` (`Prefs.keyApp`), never in SharedPreferences, and `onKeyEvent` does no heavy work.
  Vestel (F&U and other store brands; MediaTek, Android TV 14 tested) maps its Netflix button to `KEYCODE_STEM_1`, a
  global key (`dumpsys window policy` lists them) that the key filter sees first; its YouTube, Prime Video and Google
  Play buttons stay with the system. The stock lists needed no Vestel entries.
- After any change, `./build.sh` must end with `Built FreeMyTV.apk`.
- Recommendation source selection is in `Recs`; native feeds are in `NativeRecs`, optional TMDB discovery in
  `TmdbRecs`. Build instrumentation checks with `bash tests/build.sh` after building the app; see `tests/README.md`
  for device execution and permission scenarios. Do not describe a compiled test APK as executed tests.

## Development infrastructure

See `dev/README.md` for the dedicated Android TV emulator, real fixture publisher apps, offline weather transport, screenshots and CI. `bash dev/test.sh` builds and runs the suite; `bash dev/session.sh` keeps a populated offline launcher open for UI work. These commands must only target a disposable emulator. Mocks live in the test APK, never the release app. Do not install fixture apps on a user’s physical TV.

## Always

- Never turn off an app that saved a Wi-Fi network: Android deletes its networks and Original can't bring them
  back (`wifiOwners` in `HomeMode`).
- Never commit `config.properties`, `*.keystore` or any key.
- Never uninstall or clear the data of system apps; only `pm disable-user --user 0`, through `HomeMode`, so
  *Original* can bring it back.
- Don't change the TV's picture, sound, network or account settings.
- Keep the way back working: the *Original* mode and the watchdog.

## Android fork updates

See `dev/ANDROID_UPDATES.md` for the Android source port through `90908dc`. Keep this fork Android-only and crypto-free.
Preserve `Recs` source selection and the shared expanding carousel. `NativeRecs` now adapts PR #42 into the existing Continue Watching tab: app-published progress, episode metadata,
time left, hold-OK dismissal, and a Home Settings restore action. Dismissals are local; publisher data is never edited.
Watchlist entries are excluded. The PR’s separate top-content mode and preview-channel discovery were not imported. The private KinoWatch backup
stays local and is not applied here. Hold OK on a shelf app to reorder; hold OK in All Apps to add/remove it.

Personal photos (`Photos`/`PhotoInbox`), Now Playing, sleep timer/overlay, selectable top buttons, forecast mode,
Android 8 compatibility, and generic builds are available. Never perform a real launcher switch or test sleep on
someone's TV from the computer. The launcher-switch safety checks in `HomeMode` must stay in place: setup-app order,
Wi-Fi ownership, core/factory/input guards, existing disabled-package state, and recoverable restoration.

Build and run `AndroidUpdatesTest` only on a dedicated disposable emulator; it imports/removes test photos and
schedules/cancels a timer. It does not disable apps or switch the home role. Phone-emulator UI checks do not establish
Android TV-provider or real-TV behavior. The full provider suite still needs an Android TV image.
