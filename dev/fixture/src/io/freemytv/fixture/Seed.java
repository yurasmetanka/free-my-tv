package io.freemytv.fixture;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.media.tv.TvContract;
import android.net.Uri;
import android.os.Bundle;

/** Owns only this test app's provider rows. Repeated seeding replaces them. */
public final class Seed extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        new Thread(() -> {
            try {
                // TvProvider scopes writes to the caller and rejects custom SQL selections for publishers.
                getContentResolver().delete(TvContract.PreviewPrograms.CONTENT_URI, null, null);
                getContentResolver().delete(TvContract.WatchNextPrograms.CONTENT_URI, null, null);
                getContentResolver().delete(TvContract.Channels.CONTENT_URI, null, null);
                if (!"empty".equals(getIntent().getStringExtra("scenario"))) {
                    ContentValues channel = new ContentValues();
                    channel.put("type", TvContract.Channels.TYPE_PREVIEW);
                    channel.put("display_name", "Fixture Cinema");
                    channel.put("input_id", getPackageName() + "/fixture");
                    channel.put("app_link_intent_uri", new Intent(this, Player.class).toUri(Intent.URI_INTENT_SCHEME));
                    Uri uri = getContentResolver().insert(TvContract.Channels.CONTENT_URI, channel);
                    if (uri == null) throw new IllegalStateException("channel insert failed");
                    long id = Long.parseLong(uri.getLastPathSegment());
                    String[] titles = {"Orbit Station", "Mountain Letters", "City After Rain", "Wild Coast", "Studio Session", "Missing Artwork"};
                    int[] types = {TvContract.PreviewPrograms.TYPE_MOVIE, TvContract.PreviewPrograms.TYPE_TV_SERIES,
                        TvContract.PreviewPrograms.TYPE_MOVIE, TvContract.PreviewPrograms.TYPE_TV_EPISODE,
                        TvContract.PreviewPrograms.TYPE_CLIP, TvContract.PreviewPrograms.TYPE_MOVIE};
                    for (int i = 0; i < titles.length; i++) {
                        ContentValues p = program(titles[i], types[i], i);
                        p.put("channel_id", id);
                        p.put("weight", 100 - i);
                        if (getContentResolver().insert(TvContract.PreviewPrograms.CONTENT_URI, p) == null)
                            throw new IllegalStateException("preview insert failed");
                    }
                    ContentValues next = program("Continue: Mountain Letters", TvContract.PreviewPrograms.TYPE_TV_EPISODE, 7);
                    next.put("watch_next_type", TvContract.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE);
                    next.put("last_engagement_time_utc_millis", System.currentTimeMillis());
                    if (getContentResolver().insert(TvContract.WatchNextPrograms.CONTENT_URI, next) == null)
                        throw new IllegalStateException("watch-next insert failed");
                }
                getSharedPreferences("fixture", MODE_PRIVATE).edit().putString("seed", "ok").commit();
                android.util.Log.i("FixtureCinema", "SEED_OK " + getIntent().getStringExtra("run_id") + " " + getIntent().getStringExtra("scenario"));
            } catch (Throwable e) {
                android.util.Log.e("FixtureCinema", "SEED_FAILED " + getIntent().getStringExtra("run_id"), e);
                getSharedPreferences("fixture", MODE_PRIVATE).edit().putString("seed", "failed").commit();
            } finally { runOnUiThread(this::finish); }
        }, "fixture-seed").start();
    }

    private ContentValues program(String title, int type, int id) {
        ContentValues p = new ContentValues();
        p.put("title", title);
        p.put("type", type);
        p.put("intent_uri", new Intent(Intent.ACTION_VIEW, Uri.parse("fixture://cinema/" + id))
                .setPackage(getPackageName()).putExtra("title", title).toUri(Intent.URI_INTENT_SCHEME));
        p.put("poster_art_uri", "content://" + getPackageName() + ".art/" + (id == 5 ? "missing" : id));
        p.put("poster_art_aspect_ratio", TvContract.PreviewPrograms.ASPECT_RATIO_16_9);
        return p;
    }
}
