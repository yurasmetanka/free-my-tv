package io.freemytv.fixture;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.graphics.*;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import java.io.*;

/** Generated artwork is served through Android's resolver, with no network or copyrighted posters. */
public final class Artwork extends ContentProvider {
    @Override public boolean onCreate() { return true; }
    @Override public String getType(Uri uri) { return "image/png"; }
    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode) || "missing".equals(uri.getLastPathSegment())) throw new FileNotFoundException(uri.toString());
        try {
            ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
            new Thread(() -> {
                Bitmap b = Bitmap.createBitmap(960, 540, Bitmap.Config.ARGB_8888);
                try (OutputStream out = new ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])) {
                    Canvas c = new Canvas(b);
                    Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
                    int n = Integer.parseInt(uri.getLastPathSegment());
                    int[] colors = {0xff193c68, 0xff805139, 0xff314e45, 0xff3a426e};
                    p.setShader(new LinearGradient(0, 0, 960, 540, colors[n % colors.length], 0xff101426, Shader.TileMode.CLAMP));
                    c.drawRect(0, 0, 960, 540, p); p.setShader(null);
                    p.setColor(0xfff9d6a4); c.drawCircle(730, 170, 95, p);
                    p.setColor(0xff233443); c.drawRect(0, 400, 960, 540, p);
                    p.setColor(Color.WHITE); p.setTextSize(52); c.drawText("FIXTURE CINEMA", 55, 430, p);
                    p.setTextSize(28); c.drawText("Offline artwork / " + n, 58, 480, p);
                    b.compress(Bitmap.CompressFormat.PNG, 100, out);
                } catch (Exception e) { android.util.Log.e("FixtureCinema", "artwork", e); }
                finally { b.recycle(); }
            }, "fixture-art").start();
            return pipe[0];
        } catch (IOException e) { throw new FileNotFoundException(e.toString()); }
    }
    @Override public Cursor query(Uri u, String[] p, String s, String[] a, String o) { return null; }
    @Override public Uri insert(Uri u, ContentValues v) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri u, ContentValues v, String s, String[] a) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri u, String s, String[] a) { throw new UnsupportedOperationException(); }
}
