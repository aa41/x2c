package dev.x2c.fixture.normalapp;

import android.app.Application;
import android.content.Context;
import dev.x2c.runtime.X2C;

/** Performs the single process-wide X2C host initialization. */
public final class NormalApplication extends Application {
    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        X2C.init(base);
    }
}
