package dev.x2c.plugin.runtime;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Build;
import android.os.Handler;

/** Context facade that routes explicit component Intents within one installed plugin. */
final class PluginContext extends ContextWrapper {
    private final String pluginId;
    private final ClassLoader pluginClassLoader;

    PluginContext(Context base, String pluginId, ClassLoader pluginClassLoader) {
        super(base);
        this.pluginId = pluginId;
        this.pluginClassLoader = pluginClassLoader;
    }

    @Override public Context getApplicationContext() {
        Context application = getBaseContext().getApplicationContext();
        if (application == null || application == getBaseContext()) return this;
        return new PluginContext(application, pluginId, pluginClassLoader);
    }

    @Override public ClassLoader getClassLoader() {
        return pluginClassLoader;
    }

    @Override public void startActivity(Intent intent) {
        startActivity(intent, null);
    }

    @Override public void startActivity(Intent intent, Bundle options) {
        String target = PluginActivityManager.targetForPlugin(pluginId, intent);
        if (target == null) super.startActivity(intent, options);
        else PluginActivityManager.startActivity(this, pluginId, target, intent, options);
    }

    @Override public ComponentName startService(Intent intent) {
        String target = PluginServiceManager.targetForPlugin(pluginId, intent);
        return target == null
                ? super.startService(intent)
                : PluginServiceManager.startService(this, pluginId, target, intent);
    }

    @Override public ComponentName startForegroundService(Intent intent) {
        String target = PluginServiceManager.targetForPlugin(pluginId, intent);
        if (target != null) {
            return PluginServiceManager.startForegroundService(this, pluginId, target, intent);
        }
        return Build.VERSION.SDK_INT >= 26
                ? super.startForegroundService(intent)
                : super.startService(intent);
    }

    @Override public boolean stopService(Intent intent) {
        String target = PluginServiceManager.targetForPlugin(pluginId, intent);
        return target == null
                ? super.stopService(intent)
                : PluginServiceManager.stopService(this, pluginId, target);
    }

    @Override public boolean bindService(
            Intent intent, ServiceConnection connection, int flags) {
        String target = PluginServiceManager.targetForPlugin(pluginId, intent);
        return target == null
                ? super.bindService(intent, connection, flags)
                : PluginServiceManager.bindService(
                        this, pluginId, target, intent, connection, flags);
    }

    @Override public void unbindService(ServiceConnection connection) {
        if (!PluginServiceManager.unbindIfPlugin(this, connection)) {
            super.unbindService(connection);
        }
    }

    @Override public void sendBroadcast(Intent intent) {
        sendBroadcast(intent, null);
    }

    @Override public void sendBroadcast(Intent intent, String receiverPermission) {
        String target = PluginReceiverManager.targetForPlugin(pluginId, intent);
        if (target == null) super.sendBroadcast(intent, receiverPermission);
        else PluginReceiverManager.sendBroadcast(
                this, pluginId, target, intent, receiverPermission);
    }

    @Override public void sendOrderedBroadcast(
            Intent intent,
            String receiverPermission,
            BroadcastReceiver resultReceiver,
            Handler scheduler,
            int initialCode,
            String initialData,
            Bundle initialExtras) {
        String target = PluginReceiverManager.targetForPlugin(pluginId, intent);
        if (target == null) {
            super.sendOrderedBroadcast(
                    intent, receiverPermission, resultReceiver, scheduler,
                    initialCode, initialData, initialExtras);
            return;
        }
        if (scheduler != null) {
            throw new IllegalArgumentException(
                    "A custom Handler is not supported for routed plugin ordered broadcasts");
        }
        PluginReceiverManager.sendOrderedBroadcast(
                this, pluginId, target, intent, receiverPermission,
                resultReceiver, initialCode, initialData, initialExtras);
    }
}
