package dev.x2c.fixture.secondary;

import dev.x2c.plugin.api.PluginLaunchMode;
import dev.x2c.plugin.api.X2cPluginActivity;

@X2cPluginActivity(launchMode = PluginLaunchMode.SINGLE_INSTANCE)
public final class SingleInstanceActivity extends LaunchModeActivityBase {
    @Override protected String launchModeName() { return "SINGLE_INSTANCE"; }
}
