package dev.x2c.compatibility;

import android.content.Context;
import android.view.View;
import dev.x2c.runtime.X2C;
import dev.x2c.runtime.X2cResources;

public final class CompatibilityProbe {
    private CompatibilityProbe() {}

    public static View create(Context context) {
        X2cResources resources = X2C.resources(context, CompatibilityProbe.class);
        return resources.getView(context, "compatibility_screen");
    }
}
