package dev.x2c.compatibility;

import android.os.Bundle;
import dev.x2c.compatibility.base.CompatibilityBusinessBaseActivity;
import dev.x2c.plugin.api.X2cPluginActivity;

@X2cPluginActivity
public final class CompatibilityActivity extends CompatibilityBusinessBaseActivity {
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(CompatibilityProbe.create(this));
    }
}
