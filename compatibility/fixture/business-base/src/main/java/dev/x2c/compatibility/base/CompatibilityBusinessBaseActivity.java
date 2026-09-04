package dev.x2c.compatibility.base;

import android.app.Activity;
import android.os.Bundle;
import dev.x2c.plugin.api.X2cPluginBase;

/** Resource-free external Android library used by every plugin compatibility build. */
@X2cPluginBase
public abstract class CompatibilityBusinessBaseActivity extends Activity {
    private int createCount;

    @Override protected void onCreate(Bundle state) {
        createCount++;
        super.onCreate(state);
    }

    protected final int businessCreateCount() {
        return createCount;
    }
}
