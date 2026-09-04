package dev.x2c.plugin.runtime;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Non-exported manifest receiver for explicit plugin Receiver dispatch. */
public final class PluginContainerReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        PluginReceiverManager.dispatch(this, context, intent);
    }
}
