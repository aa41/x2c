package dev.x2c.fixture.consumer;

import android.app.Application;
import android.util.Log;
import dev.x2c.runtime.X2C;
import dev.x2c.runtime.X2cImages;

/** Installs the dynamic payload before any Activity can be instantiated. */
public final class HostApplication extends Application {
    private static final String TAG = "X2cPluginHost";
    private static volatile Throwable pluginFailure;

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            X2C.init(this);
            X2cImages.setLoader(new VerifiedHttpImageLoader());
            DynamicLibraryLoader.install(this);
        } catch (Throwable error) {
            pluginFailure = error;
            Log.e(TAG, "Dynamic plugin installation failed", error);
        }
    }

    public static Throwable getPluginFailure() {
        return pluginFailure;
    }
}
