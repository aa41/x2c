package dev.x2c.fixture.consumer;

import android.app.Application;
import android.content.Context;
import dev.x2c.runtime.X2C;
import dev.x2c.runtime.X2cImages;

/** Installs the dynamic payload before any Activity can be instantiated. */
public final class HostApplication extends Application {
    private static volatile Throwable pluginFailure;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        try {
            X2C.init(base);
            X2cImages.setLoader(new VerifiedHttpImageLoader());
            DynamicLibraryLoader.install(base);
        } catch (Throwable error) {
            pluginFailure = error;
        }
    }

    public static Throwable getPluginFailure() {
        return pluginFailure;
    }
}
