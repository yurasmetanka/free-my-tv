package io.freemytv;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/** Emulator-only regression checks for the Android ports; never switches a launcher or disables packages. */
public final class AndroidUpdatesTest extends Instrumentation {
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }

    @Override public void onStart() {
        Bundle result = new Bundle();
        Context c = getTargetContext();
        int code = 0;
        Activity activity = null;
        SharedPreferences prefs = c.getSharedPreferences("settings", Context.MODE_PRIVATE);
        Map<String, ?> saved = prefs.getAll();
        File photo = null;
        boolean emulator = Build.HARDWARE.contains("ranchu") || Build.HARDWARE.contains("goldfish");
        try {
            check(emulator, "dedicated emulator required");
            OfflineNetwork.install();
            prefs.edit().putString("recommendations", "off").putString("language", "en")
                    .putString("theme", "light").putString("shelf", "").putBoolean("nowPlaying", false).commit();
            Ui.applyLanguage();
            checkSafety();
            checkWeather();
            checkContinueWatching(c);
            int version = Prefs.version;
            SleepTimer.set(c, 30);
            check(SleepTimer.minutesLeft(c) == 30 && Prefs.version == version, "sleep timer schedules without rebuilding home");
            SleepTimer.step(c, -1);
            check(SleepTimer.minutesLeft(c) == 25, "sleep timer steps down five minutes");
            SleepTimer.set(c, 0);
            check(SleepTimer.minutesLeft(c) == 0, "sleep timer cancels its alarm");
            activity = startActivitySync(new Intent(c, HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            final Activity host = activity;
            final Throwable[] failure = new Throwable[1];
            runOnMainSync(() -> {
                try { checkHome(host); }
                catch (Throwable e) { failure[0] = e; }
            });
            if (failure[0] != null) throw new AssertionError("Home integration", failure[0]);
            runOnMainSync(host::finish);
            activity = startActivitySync(new Intent(c, SettingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            final Activity settings = activity;
            runOnMainSync(() -> {
                try { checkSettings(settings); }
                catch (Throwable e) { failure[0] = e; }
            });
            if (failure[0] != null) throw new AssertionError("Settings integration", failure[0]);
            // Real image import, crop, and catalogue enumeration on this emulator's disposable app data.
            File inbox = Photos.privateInbox(c); inbox.mkdirs();
            photo = new File(inbox, "port-test.jpg");
            Bitmap image = Bitmap.createBitmap(2400, 1350, Bitmap.Config.ARGB_8888);
            image.eraseColor(0xFF436A85);
            try (FileOutputStream out = new FileOutputStream(photo)) { image.compress(Bitmap.CompressFormat.JPEG, 90, out); }
            image.recycle();
            Photos.importInbox(c, null);
            long deadline = SystemClock.elapsedRealtime() + 15000;
            while (Photos.files(c).isEmpty() && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(100);
            check(!Photos.files(c).isEmpty(), "personal photo imported");
            check(Photos.picks(c).get(0).photo != null, "personal photo preserved in shared model");
            for (String language : Ui.languages()) {
                Prefs.setLanguage(language);
                check(!Ui.tr("Recommendations").isEmpty(), "native recommendation label in " + language);
            }
            result.putString("stream", "Android updates passed: switch guards, DST, daily forecast, shelf scroll/reorder, "
                    + "native carousel, Continue Watching progress/metadata/dismissal, source transition, top buttons, settings, personal photos, languages.\n");
            code = -1;
        } catch (Throwable e) {
            android.util.Log.e("AndroidUpdates", "failed", e);
            result.putString("stream", "FAILED: " + e + "\n");
        } finally {
            if (emulator) SleepTimer.stop(c);
            if (activity != null) { final Activity a = activity; runOnMainSync(a::finish); }
            if (photo != null) Photos.removeAll(c);
            SharedPreferences.Editor edit = prefs.edit().clear();
            for (Map.Entry<String, ?> entry : saved.entrySet()) {
                Object value = entry.getValue(); String key = entry.getKey();
                if (value instanceof String) edit.putString(key, (String) value);
                else if (value instanceof Boolean) edit.putBoolean(key, (Boolean) value);
                else if (value instanceof Integer) edit.putInt(key, (Integer) value);
                else if (value instanceof Long) edit.putLong(key, (Long) value);
                else if (value instanceof Float) edit.putFloat(key, (Float) value);
                else if (value instanceof java.util.Set) edit.putStringSet(key, new java.util.HashSet<>((java.util.Set<String>) value));
            }
            if (emulator) { edit.commit(); Ui.applyLanguage(); }
        }
        finish(code, result);
    }

    private static void checkSafety() throws Exception {
        for (String pkg : new String[] {"com.android.permissioncontroller", "com.android.providers.tv",
                "com.amazon.tv.launcher", "com.mediatek.tv.factory", "io.freemytv"})
            check(!(Boolean) call(HomeMode.class, "mayTurnOff", new Class<?>[] {String.class}, pkg), "protected " + pkg);
        List<?> targets = (List<?>) call(HomeMode.class, "targets", new Class<?>[] {String.class, String.class, boolean.class},
                "com.google.android.apps.tv.launcherx/.Home", "com.test.dream/.Dream", true);
        check(targets.indexOf("com.google.android.tungsten.setupwraith") < targets.indexOf("com.google.android.apps.tv.launcherx"),
                "setup app disabled before stock launcher");
        boolean alone = Build.VERSION.SDK_INT >= 30 && Build.VERSION.SDK_INT <= 32;
        check(HomeMode.DREAM.endsWith(alone ? ".ScreensaverAlone" : ".Screensaver"), "screensaver process matches Android version");
    }

    private static void checkWeather() throws Exception {
        JSONArray dates = new JSONArray(), highs = new JSONArray(), lows = new JSONArray(), codes = new JSONArray();
        SimpleDateFormat date = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        date.setTimeZone(TimeZone.getTimeZone("UTC"));
        for (int i = 0; i < 6; i++) {
            dates.put(date.format(System.currentTimeMillis() + (i + 1) * 86400000L));
            highs.put(i == 2 ? JSONObject.NULL : 24 + i); lows.put(12 + i); codes.put(1);
        }
        JSONObject body = new JSONObject().put("timezone", "Europe/Warsaw").put("utc_offset_seconds", 7200)
                .put("current", new JSONObject().put("temperature_2m", 23).put("weather_code", 1))
                .put("daily", new JSONObject().put("time", dates).put("temperature_2m_max", highs)
                        .put("temperature_2m_min", lows).put("weather_code", codes)
                        .put("sunrise", new JSONArray().put("2026-10-25T06:00"))
                        .put("sunset", new JSONArray().put("2026-10-25T18:00")));
        HomeData.Weather weather = (HomeData.Weather) call(HomeData.class, "parseWeather", new Class<?>[] {String.class}, body.toString());
        SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US); iso.setTimeZone(TimeZone.getTimeZone("UTC"));
        check(weather.sunrise == iso.parse("2026-10-25T04:00").getTime(), "Open-Meteo fixed offset across DST");
        check(weather.days.size() == 5 && Double.isNaN(weather.dayMax.get(2)), "five days survive a null temperature");
    }

    private static void checkContinueWatching(Context c) throws Exception {
        List<Recs.Pick> original = new ArrayList<>();
        for (int i = 0; i < 8; i++) { Recs.Pick item = new Recs.Pick(); item.id = i; original.add(item); }
        List<Recs.Pick> rotated = new ArrayList<>(original);
        NativeRecs.rotateRecommendations(rotated, null, 3600_000L);
        check(rotated.size() == 8 && rotated.get(0) == original.get(3)
                && new java.util.HashSet<>(rotated).size() == 8, "hourly rotation retains every card and advances a page");
        List<Recs.Pick> sameHour = new ArrayList<>(original);
        NativeRecs.rotateRecommendations(sameHour, "movie", 3600_000L + 59999);
        check(sameHour.equals(rotated), "feed rereads do not shuffle within the hour");
        NativeRecs.rotateRecommendations(original, "continue", 3600_000L);
        check(original.get(0).id == 0 && original.get(7).id == 7, "Continue Watching order never rotates");
        List<Recs.Pick> shortRow = new ArrayList<>(original.subList(0, 3));
        NativeRecs.rotateRecommendations(shortRow, "tv", 3600_000L);
        check(shortRow.get(0).id == 1, "three-card rows also rotate");
        NativeRecs.rotateRecommendations(new ArrayList<>(), null, 3600_000L);
        Recs.Pick p = new Recs.Pick();
        p.resume = true; p.pkg = "test.watch"; p.title = "A series"; p.app = "Player";
        p.duration = 90 * 60_000L; p.position = 30 * 60_000L;
        p.episode = NativeRecs.episode("2", "5", "An episode");
        check("S2 E5".equals(p.episode), "season/episode display numbers");
        check("E7".equals(NativeRecs.episode(null, "7", null)), "episode without season");
        check("Pilot".equals(NativeRecs.episode(null, null, "Pilot")), "episode title fallback");
        check(Math.abs(NativeRecs.progress(p) - 1f / 3) < .001f, "progress ratio");
        check("Player · 1 h 0 min left · S2 E5".equals(NativeRecs.metadata(p)), "app, remaining time and episode metadata");
        p.position = p.duration - 500;
        check("1 min left".equals(NativeRecs.timeLeft(p)), "sub-minute remaining time");
        for (long position : new long[] {-1, 0, 999, p.duration, Long.MAX_VALUE}) {
            p.position = position;
            check(NativeRecs.progress(p) < 0 && NativeRecs.timeLeft(p) == null, "invalid or completed progress omitted");
        }
        p.duration = 0; p.position = 2000;
        check(NativeRecs.progress(p) < 0, "unknown duration omitted");
        int version = Prefs.version;
        NativeRecs.get(c).dismiss(p);
        check(Prefs.watchHidden().contains(NativeRecs.hideKey(p)) && Prefs.version == version, "persistent local dismissal without settings reset");
        p.id = 999;
        check(Prefs.watchHidden().contains(NativeRecs.hideKey(p)), "publisher ID change preserves dismissal");
        p.pkg = "other.app";
        check(!Prefs.watchHidden().contains(NativeRecs.hideKey(p)), "dismissal isolated to publishing app");
        Prefs.setWatchHidden(Collections.emptySet());
        check(Prefs.watchHidden().isEmpty(), "dismissed entries can be restored");
    }

    private static void checkHome(Activity activity) throws Exception {
        Prefs.setRecommendations("apps");
        HomeView home = new HomeView(activity);
        try {
            home.measure(View.MeasureSpec.makeMeasureSpec(1280, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY)); home.layout(0, 0, 1280, 720);
            check((Integer) field(home, "tabCount") == 4, "native four tabs retained");
            set(home, "zone", field(HomeView.class, "Z_SHELF"));
            call(home, "setShelf", new Class<?>[] {List.class}, Collections.emptyList());
            call(home, "moveLight", new Class<?>[] {int.class, int.class}, 0, -1);
            check(field(home, "zone").equals(field(HomeView.class, "Z_TOP")), "empty shelf reaches top controls");
            List<HomeApps.Item> shelf = new ArrayList<>(); List<String> packages = new ArrayList<>();
            for (int i = 0; i < 12; i++) { String pkg = "test.app" + i; packages.add(pkg); shelf.add(HomeApps.app(pkg, "App " + i)); }
            Prefs.setShelf(packages);
            HomeApps.Item last = shelf.get(11);
            call(home, "setShelf", new Class<?>[] {List.class}, shelf);
            set(home, "zone", field(HomeView.class, "Z_SHELF")); set(home, "shelfIndex", 11);
            call(home, "retarget", new Class<?>[] {boolean.class}, true);
            check((Integer) field(home, "shelfFirst") > 0, "shelf scrolls beyond first page");
            call(home, "startReorder", new Class<?>[0]);
            call(home, "shiftReorder", new Class<?>[] {int.class}, -1);
            check(((List<?>) field(home, "shelf")).get(10) == last, "held shelf app moves left");
            call(home, "cancelReorder", new Class<?>[] {boolean.class}, true);
            check(((List<?>) field(home, "shelf")).get(11) == last, "Back restores shelf order");
            // Fixture cards exercise the shared row without depending on a phone image having Android TV's provider.
            List<Recs.Pick> cards = new ArrayList<>(); Spring[] focus = new Spring[8], size = new Spring[8]; RectF[] rects = new RectF[8];
            for (int i = 0; i < 8; i++) {
                Recs.Pick pick = new Recs.Pick(); pick.id = i; pick.source = "preview"; pick.kind = "movie";
                pick.pkg = "test.app0"; pick.title = "Fixture " + i; pick.meta = "Film"; cards.add(pick);
                focus[i] = new Spring(.32f, .78f, 0); size[i] = new Spring(.32f, 1, i == 0 ? 1 : 0); rects[i] = new RectF();
            }
            Recs.Pick watching = cards.get(0);
            watching.resume = true; watching.source = "watch-next"; watching.kind = "tv";
            watching.app = "Fixture Player"; watching.duration = 3600000; watching.position = 1200000;
            watching.episode = NativeRecs.episode("2", "5", null); watching.meta = NativeRecs.metadata(watching);
            set(home, "cards", cards); set(home, "cardFocus", focus); set(home, "cardSize", size); set(home, "cardRects", rects);
            call(home, "prepareCardLabels", new Class<?>[0]);
            set(home, "zone", field(HomeView.class, "Z_CARDS")); set(home, "cardIndex", 0);
            check((Boolean) call(home, "canDismiss", new Class<?>[] {int.class}, 0), "Watch Next cards offer dismissal");
            check(!(Boolean) call(home, "canDismiss", new Class<?>[] {int.class}, 1), "preview cards have no dismissal menu");
            call(home, "revealCard", new Class<?>[] {boolean.class}, true);
            Bitmap rendered = Bitmap.createBitmap(1280, 720, Bitmap.Config.ARGB_8888);
            call(home, "drawLightCard", new Class<?>[] {Canvas.class, int.class}, new Canvas(rendered), 0);
            File capture = new File(activity.getExternalFilesDir(null), "continue-watching-test.png");
            try (FileOutputStream out = new FileOutputStream(capture)) { rendered.compress(Bitmap.CompressFormat.PNG, 100, out); }
            RectF card = ((RectF[]) field(home, "cardRects"))[0];
            int y = (int) card.bottom - 1;
            check(rendered.getPixel(Math.round(card.left + 25), y) == 0xFFFF0033, "red progress line flush with card bottom");
            check(rendered.getPixel(Math.round(card.right - 25), y) == 0xFFDADADF, "no remainder track over the card");
            rendered.recycle();
            for (int i = 0; i < 7; i++) call(home, "moveLight", new Class<?>[] {int.class, int.class}, 1, 0);
            check((Integer) field(home, "cardIndex") == 7 && (Integer) field(home, "cardStart") == 5, "native carousel reaches eighth card");
            set(home, "cardIndex", 0);
            long now = SystemClock.uptimeMillis();
            home.onKey(new android.view.KeyEvent(now, now, android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_DPAD_CENTER, 0));
            home.onKey(new android.view.KeyEvent(now, now + 600, android.view.KeyEvent.ACTION_DOWN,
                    android.view.KeyEvent.KEYCODE_DPAD_CENTER, 1, 0, -1, 0, android.view.KeyEvent.FLAG_LONG_PRESS));
            android.app.AlertDialog menu = (android.app.AlertDialog) field(home, "watchingMenu");
            check(menu != null && menu.isShowing(), "hold OK opens Continue Watching menu");
            home.onKey(new android.view.KeyEvent(now, now + 650, android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_DPAD_CENTER, 0));
            check(!(Boolean) field(home, "okDown") && !(Boolean) field(home, "okHeld"), "hold release does not launch playback");
            menu.getListView().performItemClick(null, 1, 1);
            check(Prefs.watchHidden().contains(NativeRecs.hideKey(watching)), "dismiss menu persists the selected title");
            check(field(home, "zone").equals(field(HomeView.class, "Z_SHELF")), "empty dismissed row returns focus to shelf");
            Prefs.setWatchHidden(Collections.emptySet());
            call(home, "setLightButtons", new Class<?>[] {List.class}, Collections.emptyList());
            Prefs.setRecommendations("tmdb");
            HomeView discover = new HomeView(activity);
            try { check((Integer) field(discover, "tabCount") == 3, "Discover has three tabs"); }
            finally { discover.destroy(); }
            check(((String[]) call(home, "tabs", new Class<?>[0])).length == 4, "paused native view retains four tabs after source switch");
            Bitmap input = Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888);
            input.eraseColor(0xFF254C72);
            Bitmap output = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888);
            call(home, "roundCardImage", new Class<?>[] {Canvas.class, Bitmap.class, BitmapShader.class, RectF.class, float.class},
                    new Canvas(output), input, new BitmapShader(input, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP),
                    new RectF(10, 10, 90, 90), 16f);
            check(output.getPixel(50, 50) == 0xFF254C72 && output.getPixel(10, 10) == 0,
                    "rounded carousel artwork draws with transparent corners");
            input.recycle(); output.recycle();
            DreamView dream = new DreamView(activity); dream.layout(0, 0, 1280, 720); dream.stop();
        } finally { home.destroy(); }
    }

    private static void checkSettings(Activity activity) throws Exception {
        Object view = field(activity, "view");
        set(view, "screen", field(view.getClass(), "HOME_SETTINGS"));
        String[] labels = (String[]) call(view, "settingLabels", new Class<?>[0]);
        String[] values = (String[]) call(view, "settingValues", new Class<?>[0]);
        check(labels.length == 12 && values.length == labels.length, "settings rows fit optional features and recommendations");
        check(Arrays.asList(labels).contains("Now Playing widget") && Arrays.asList(labels).contains("Recommendations"), "new and existing settings coexist");
        set(view, "pickScroll", 1); set(view, "focus", 9);
        check((Integer) call(view, "rowCount", new Class<?>[0]) <= 10, "settings scroll to final source row");
        Prefs.setWatchHidden(Collections.singleton("test.app|Hidden title"));
        set(view, "pickScroll", 2); set(view, "focus", 9);
        call(view, "activate", new Class<?>[0]);
        check(Prefs.watchHidden().isEmpty(), "Home Settings restores dismissed titles");
    }

    private static Object field(Object object, String name) throws Exception {
        Field f = (object instanceof Class ? (Class<?>) object : object.getClass()).getDeclaredField(name); f.setAccessible(true);
        return f.get(object instanceof Class ? null : object);
    }
    private static void set(Object object, String name, Object value) throws Exception {
        Field f = object.getClass().getDeclaredField(name); f.setAccessible(true); f.set(object, value);
    }
    private static Object call(Object object, String name, Class<?>[] types, Object... args) throws Exception {
        Method m = (object instanceof Class ? (Class<?>) object : object.getClass()).getDeclaredMethod(name, types); m.setAccessible(true);
        return m.invoke(object instanceof Class ? null : object, args);
    }
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
}
