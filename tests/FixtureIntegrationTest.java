package io.freemytv;

import android.app.Instrumentation;
import android.content.*;
import android.graphics.BitmapFactory;
import android.os.*;
import java.util.*;

/** Emulator-only provider + artwork + weather/price integration, also a live offline UI dev session. */
public final class FixtureIntegrationTest extends Instrumentation {
    private Bundle args;
    private volatile boolean finishRequested;
    @Override public void onCreate(Bundle args) { super.onCreate(args); this.args = args == null ? new Bundle() : args; start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        BroadcastReceiver stop = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) { finishRequested = true; }
        };
        Context c = getTargetContext();
        int code = 0;
        try {
            if (!Build.PRODUCT.contains("sdk") || !Build.FINGERPRINT.contains("generic"))
                throw new AssertionError("Use a dedicated emulator; this runner changes fixture preferences");
            OfflineNetwork.install();
            String scenario = args.getString("scenario", "populated");
            c.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putString("recommendations", "apps")
                    .putString("shelf", "io.freemytv.fixture").putBoolean("quotes", true)
                    .putBoolean("coin0", true).putBoolean("coin1", true).putBoolean("coin2", true).putBoolean("weather", true)
                    .putString("city", "Fixture City").putString("theme", "light").commit();
            NativeRecs nativeRecs = NativeRecs.get(c);
            HomeData data = HomeData.get(c);
            long refreshStarted = System.currentTimeMillis();
            runOnMainSync(() -> { nativeRecs.invalidate(); data.settingsChanged(); data.refreshIfStale(); });
            long deadline = SystemClock.elapsedRealtime() + 90000;
            int expected = scenario.equals("populated") ? 7 : 0;
            do {
                if (nativeRecs.now().size() == expected && NativeRecs.status(c).optLong("at") >= refreshStarted
                        && data.weatherAt > 0 && data.pricesAt > 0) break;
                SystemClock.sleep(200);
            } while (SystemClock.elapsedRealtime() < deadline);
            check(nativeRecs.now().size() == expected, "expected " + expected + " provider picks, got " + nativeRecs.now().size() + ": " + NativeRecs.status(c));
            check(NativeRecs.status(c).optLong("at") >= refreshStarted, "provider refresh completed in this run");
            check(NativeRecs.status(c).optString("error").isEmpty(), "provider queries completed without errors");
            check(data.weather.temp == 23 && data.weather.hours.size() == 6, "mock forecast parsed");
            check(data.prices[0] == 65000 && data.changes[0] > 0, "mock prices parsed");
            check(OfflineNetwork.weatherRequests.get() > 0 && OfflineNetwork.priceRequests.get() > 0, "weather and price requests used offline transport");
            if (expected > 0) {
                try (android.database.Cursor rows = c.getContentResolver().query(android.media.tv.TvContract.PreviewPrograms.CONTENT_URI,
                        new String[] {"package_name"}, null, null, null)) {
                    int outsideShelf = 0;
                    while (rows != null && rows.moveToNext()) if (rows.getString(0).equals("io.freemytv.fixture.extra")) outsideShelf++;
                    check(outsideShelf == 6, "second publisher exists but is excluded from the shelf feed");
                }
                check(nativeRecs.now("movie").size() == 3, "three provider movies");
                check(nativeRecs.now("tv").size() == 3, "three provider shows including Watch Next");
                check(nativeRecs.now("continue").size() == 1, "one Watch Next entry");
                Recs.Pick pick = nativeRecs.now().get(0);
                check(pick.pkg.equals("io.freemytv.fixture"), "provider preserved publisher identity");
                check(nativeRecs.intent(pick).resolveActivity(c.getPackageManager()) != null, "content link resolves to fixture player");
                try (java.io.InputStream in = c.getContentResolver().openInputStream(android.net.Uri.parse(pick.backdrop))) {
                    check(BitmapFactory.decodeStream(in) != null, "cross-app content artwork decodes");
                }
                Recs.Pick missing = null;
                for (Recs.Pick item : nativeRecs.now()) if (item.title.equals("Missing Artwork")) missing = item;
                check(missing != null, "missing-artwork item stays in feed");
                try (java.io.InputStream in = c.getContentResolver().openInputStream(android.net.Uri.parse(missing.backdrop))) {
                    throw new AssertionError("missing artwork should not return image data");
                } catch (java.io.FileNotFoundException expectedMissing) { }
                check(c.getPackageManager().getApplicationIcon(pick.pkg) != null, "publisher icon is available for artwork fallback");
                runOnMainSync(() -> { Recs.get(c).setLight(true); nativeRecs.prefetchAll(); });
                deadline = SystemClock.elapsedRealtime() + 90000;
                boolean artworkReady;
                do {
                    artworkReady = true;
                    for (Recs.Pick visible : nativeRecs.now().subList(0, 3))
                        if (nativeRecs.backdrop(visible) == null) artworkReady = false;
                    if (artworkReady) break;
                    SystemClock.sleep(200);
                } while (SystemClock.elapsedRealtime() < deadline);
                check(artworkReady, "first three card backdrops fully prepared for rendering");
            }
            IntentFilter filter = new IntentFilter("io.freemytv.tests.FINISH");
            if (Build.VERSION.SDK_INT >= 33) c.registerReceiver(stop, filter, Context.RECEIVER_EXPORTED);
            else c.registerReceiver(stop, filter);
            startActivitySync(new Intent(c, HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            Bundle ready = new Bundle(); ready.putString("stream", "FIXTURE_READY " + scenario + " picks=" + expected + " weather=23 prices=65000\n");
            sendStatus(1, ready);
            long hold = Long.parseLong(args.getString("hold_ms", "0"));
            deadline = SystemClock.elapsedRealtime() + hold;
            while (!finishRequested && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(200);
            result.putString("stream", "Fixture provider, artwork, weather and prices checks passed: " + scenario + "\n");
            code = -1;
        } catch (Throwable e) { result.putString("stream", "FAILED: " + e + "\n"); android.util.Log.e("FixtureIntegration", "failed", e); }
        finally { try { c.unregisterReceiver(stop); } catch (RuntimeException ignored) {} }
        finish(code, result);
    }
    private static void check(boolean ok, String text) { if (!ok) throw new AssertionError(text); }
}
