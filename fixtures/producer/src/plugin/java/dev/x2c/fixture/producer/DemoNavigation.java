package dev.x2c.fixture.producer;

import android.app.Activity;
import dev.x2c.plugin.runtime.PluginActivityManager;

/** Plugin delivery adapter; common business source does not depend on the plugin runtime. */
final class DemoNavigation {
  private DemoNavigation() {}

  static void openComponentShowcase(Activity activity) {
    PluginActivityManager.startActivity(
        activity,
        "dev.x2c.fixture.component-showcase",
        "dev.x2c.fixture.secondary.ComponentShowcaseActivity");
  }
}
