package dev.x2c.fixture.secondary;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import dev.x2c.plugin.api.X2cPluginReceiver;
import dev.x2c.plugin.base.BasePluginReceiver;

/** Explicit normal/ordered/goAsync BroadcastReceiver example. */
@X2cPluginReceiver
public final class ComponentProbeReceiver extends BasePluginReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        boolean ordered = isOrderedBroadcast();
        ComponentState.receiver = "Receiver: " + (ordered ? "ordered" : "normal")
                + " · action=" + intent.getAction();
        if (ordered) {
            setResult(220, "x2c-ordered-result", null);
        }
        PendingResult pending = goAsync();
        new Handler(Looper.getMainLooper()).post(() -> {
            ComponentState.receiver += " · goAsync.finish=PASS";
            pending.finish();
        });
    }
}
