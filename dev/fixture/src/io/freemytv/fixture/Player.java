package io.freemytv.fixture;
import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.TextView;

/** A content-link destination rather than a video service. Back returns to the launcher. */
public final class Player extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        TextView text = new TextView(this);
        text.setText("Fixture Cinema\n\n" + getIntent().getStringExtra("title") + "\n" + getIntent().getDataString() + "\n\nContent link opened. Press Back to return.");
        text.setTextColor(Color.WHITE); text.setBackgroundColor(0xff15263d);
        text.setTextSize(28); text.setGravity(Gravity.CENTER); setContentView(text);
        android.util.Log.i("FixtureCinema", "PLAYER_OPEN " + getIntent().getDataString());
    }
}
