# Android ports, 2026-10-26

Adapted Android changes from `simbasjungles/free-my-tv`, shared base `62e883b` through `90908dc`, onto this fork's `main` at `1b2df45`. This is a source port, not a merge of the original project's platform or crypto code. The fork remains the only configured remote.

## Included

- Launcher switching/restoration: setup-app ordering; core/factory/input protection; existing disabled-package preservation; restoration after reinstall; stock-launcher fallback; the boot-check repair (`129f20b`, `63def39`, `ae163a6`, `d8cd067`, `277b518`). `HomeMode` and `Adb` retain the reviewed source's final implementations.
- Android 12/12L screensaver process and migration; cross-process settings refresh (`f59c355`, `a7d56b1`, `6803d89`).
- App-list caching and Home-event debouncing; image-owned shaders; bounded pre-scaled symbols; anti-aliased images and bitmap glass on older Android (`3d38bf2`, `52bdcbe`, `6901adc`, `e9ffe2b`, `ade05dd`).
- Empty-shelf navigation, uninstalled-app cleanup, remote-button controls, scrolling shelf and 16:9 banners. Existing hold-OK shelf reordering remains, with cancel and TalkBack support.
- TMDB provider fixes and service additions, title links through optional Watchmode, animation/service exclusions. The updated backend lives in `TmdbRecs`; the source-selection facade remains in `Recs`.
- Weather DST correction and optional five-day forecast (`8e32530`, `195bb32`).
- Personal photos and USB/computer import, sleep timer, Now Playing, watch from screensaver, configurable top buttons, navigation sounds, 27 language catalogues, Android 8 compatibility, generic builds, and current Android manufacturer/input fixes.

## Fork adaptations

- Native recommendation sources, source defaults, four app tabs, safe publisher intents, expanding/scrolling carousel and selected-card backgrounds stay in place.
- The initial port left `NativeRecs.java` unchanged. A subsequent Continue Watching change adapts [PR #42](https://github.com/simbasjungles/free-my-tv/pull/42) metadata and local dismissal into our existing tab: bottom progress track, app/time-left/episode details, hold-OK menu, and Home Settings restore. It excludes WATCHLIST rows. It preserves the shelf-only feed, HTTPS artwork and existing carousel rather than importing the PR’s separate top-content mode or channel-name discovery.
- No webOS, Tizen, website, Binance, coin settings, price widgets, or coin assets were restored. Now Playing has its own text rendering instead of falling back to crypto prices.
- The carousel uses image-owned shaders while preserving center cropping and selection expansion. Its tab and top-button offsets account for the fourth native tab and optional buttons.
- Existing native-source translations in Portuguese, Spanish, German, and Russian move into the new catalogues. New languages fall back to English for native-specific labels until translated.
- Private configuration/signing keys stay local. The KinoWatch artwork sources remain in the existing local-only backup and are not applied by this port.

## Validation

Run `./build.sh`, `bash tests/build.sh`, `python3 tools/translate.py --check`, and `git diff --check`. Build the fixture publisher with `bash dev/build-fixture.sh`.

`AndroidUpdatesTest` exercises the port's integration with injected cards on a dedicated emulator, including real personal-photo decoding/import and alarm scheduling/cancellation. `RecommendationsTest` retains the original native-feed regressions. Full provider scenarios use `bash dev/test.sh` on an Android TV emulator; a phone image cannot validate TV-provider behavior. Firmware-specific launcher switching, HDMI, actual media-session playback, and older Android versions require device testing. No physical TV was changed in this port.
