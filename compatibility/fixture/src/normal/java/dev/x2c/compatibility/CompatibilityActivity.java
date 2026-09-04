package dev.x2c.compatibility;

import android.app.Activity;
import android.os.Bundle;

/** Framework-created Activity used by the normal AAR compatibility path. */
public final class CompatibilityActivity extends Activity {
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(CompatibilityProbe.create(this));
    }
}
