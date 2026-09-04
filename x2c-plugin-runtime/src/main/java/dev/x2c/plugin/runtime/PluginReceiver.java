package dev.x2c.plugin.runtime;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

/** Compile-time replacement root for plugin BroadcastReceiver inheritance chains. */
public abstract class PluginReceiver extends BroadcastReceiver {
    private BroadcastReceiver container;
    private String pluginId;
    private String targetClassName;

    final void attachPlugin(
            BroadcastReceiver container, String pluginId, String targetClassName) {
        if (this.container != null) {
            throw new IllegalStateException("Plugin Receiver is already attached: " + targetClassName);
        }
        this.container = container;
        this.pluginId = pluginId;
        this.targetClassName = targetClassName;
    }

    public final String getPluginId() {
        requireContainer();
        return pluginId;
    }

    public final String getPluginReceiverClassName() {
        requireContainer();
        return targetClassName;
    }

    final void performReceive(Context context, Intent intent) {
        onReceive(context, intent);
    }

    public final void abortPluginBroadcast() { requireContainer().abortBroadcast(); }
    public final void clearPluginAbortBroadcast() { requireContainer().clearAbortBroadcast(); }
    public final boolean getPluginAbortBroadcast() { return requireContainer().getAbortBroadcast(); }
    public final int getPluginResultCode() { return requireContainer().getResultCode(); }
    public final String getPluginResultData() { return requireContainer().getResultData(); }
    public final Bundle getPluginResultExtras(boolean makeMap) {
        return requireContainer().getResultExtras(makeMap);
    }
    public final PendingResult goPluginAsync() { return requireContainer().goAsync(); }
    public final boolean isPluginInitialStickyBroadcast() {
        return requireContainer().isInitialStickyBroadcast();
    }
    public final boolean isPluginOrderedBroadcast() {
        return requireContainer().isOrderedBroadcast();
    }
    public final void setPluginResult(int code, String data, Bundle extras) {
        requireContainer().setResult(code, data, extras);
    }
    public final void setPluginResultCode(int code) { requireContainer().setResultCode(code); }
    public final void setPluginResultData(String data) { requireContainer().setResultData(data); }
    public final void setPluginResultExtras(Bundle extras) {
        requireContainer().setResultExtras(extras);
    }

    private BroadcastReceiver requireContainer() {
        if (container == null) {
            throw new IllegalStateException("Plugin Receiver has not been attached to a container");
        }
        return container;
    }
}
