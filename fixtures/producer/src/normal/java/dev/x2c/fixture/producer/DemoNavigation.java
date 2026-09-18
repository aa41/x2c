package dev.x2c.fixture.producer;

import android.app.Activity;
import android.widget.Toast;

/** Normal AAR adapter; Android creates declared components without the plugin router. */
final class DemoNavigation {
  private DemoNavigation() {}

  static void openComponentShowcase(Activity activity) {
    Toast.makeText(
            activity,
            "Normal AAR mode uses Manifest-declared framework components",
            Toast.LENGTH_SHORT)
        .show();
  }
}
