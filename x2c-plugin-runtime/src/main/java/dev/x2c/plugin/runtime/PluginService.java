package dev.x2c.plugin.runtime;

import android.annotation.TargetApi;
import android.app.Application;
import android.app.Notification;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.Configuration;
import android.os.Build;
import android.os.IBinder;

/** Compile-time replacement root for plugin Service inheritance chains. */
public abstract class PluginService extends Service {
    private PluginContainerService container;
    private String pluginId;
    private String targetClassName;
    private boolean attachingBaseContext;
    private boolean baseContextAttached;

    final void attachPlugin(
            PluginContainerService container, String pluginId, String targetClassName) {
        if (this.container != null) {
            throw new IllegalStateException("Plugin Service is already attached: " + targetClassName);
        }
        this.container = container;
        this.pluginId = pluginId;
        this.targetClassName = targetClassName;
        attachingBaseContext = true;
        try {
            attachBaseContext(new PluginContext(
                    container, pluginId, getClass().getClassLoader()));
        } finally {
            attachingBaseContext = false;
        }
        if (!baseContextAttached) {
            throw new IllegalStateException(
                    "Plugin Service attachBaseContext() must call super: " + targetClassName);
        }
    }

    @Override protected void attachBaseContext(Context newBase) {
        if (!attachingBaseContext) {
            throw new IllegalStateException(
                    "Plugin Service base Context may only be attached by the host container");
        }
        if (baseContextAttached) {
            throw new IllegalStateException(
                    "Plugin Service base Context is already attached: " + targetClassName);
        }
        super.attachBaseContext(newBase);
        baseContextAttached = true;
    }

    public final Service getContainerService() {
        return requireContainer();
    }

    public final String getPluginId() {
        requireContainer();
        return pluginId;
    }

    public final String getPluginServiceClassName() {
        requireContainer();
        return targetClassName;
    }

    @Override public ClassLoader getClassLoader() {
        return getClass().getClassLoader();
    }

    public final Application getPluginApplication() {
        requireContainer();
        return dev.x2c.runtime.X2C.hostApplication();
    }

    public final void stopPluginSelf() {
        requireContainer().stopSelf();
    }

    public final void stopPluginSelf(int startId) {
        requireContainer().stopSelf(startId);
    }

    public final boolean stopPluginSelfResult(int startId) {
        return requireContainer().stopSelfResult(startId);
    }

    public final void startPluginForeground(int id, Notification notification) {
        requireContainer().startForeground(id, notification);
    }

    @TargetApi(29)
    public final void startPluginForeground(
            int id, Notification notification, int foregroundServiceType) {
        requireApi(29, "Service.startForeground(id, notification, type)");
        requireContainer().startForeground(id, notification, foregroundServiceType);
    }

    public final void stopPluginForeground(boolean removeNotification) {
        requireContainer().stopForeground(removeNotification);
    }

    @TargetApi(24)
    public final void stopPluginForeground(int flags) {
        requireApi(24, "Service.stopForeground(flags)");
        requireContainer().stopForeground(flags);
    }

    @Override public ComponentName startService(Intent intent) {
        return PluginServiceManager.startFromPlugin(this, intent, false);
    }

    @Override public ComponentName startForegroundService(Intent intent) {
        return PluginServiceManager.startFromPlugin(this, intent, true);
    }

    @Override public boolean stopService(Intent intent) {
        return PluginServiceManager.stopFromPlugin(this, intent);
    }

    @Override public boolean bindService(
            Intent intent, ServiceConnection connection, int flags) {
        return PluginServiceManager.bindFromPlugin(this, intent, connection, flags);
    }

    @Override public void unbindService(ServiceConnection connection) {
        if (!PluginServiceManager.unbindFromPlugin(this, connection)) {
            requireContainer().unbindService(connection);
        }
    }

    final void performCreate() { onCreate(); }
    final int performStartCommand(Intent intent, int flags, int startId) {
        return onStartCommand(intent, flags, startId);
    }
    final IBinder performBind(Intent intent) { return onBind(intent); }
    final boolean performUnbind(Intent intent) { return onUnbind(intent); }
    final void performRebind(Intent intent) { onRebind(intent); }
    final void performTaskRemoved(Intent intent) { onTaskRemoved(intent); }
    final void performDestroy() { onDestroy(); }
    final void performConfigurationChanged(Configuration configuration) {
        onConfigurationChanged(configuration);
    }
    final void performLowMemory() { onLowMemory(); }
    final void performTrimMemory(int level) { onTrimMemory(level); }

    @Override public void onCreate() {}
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        return START_NOT_STICKY;
    }
    @Override public boolean onUnbind(Intent intent) { return false; }
    @Override public void onRebind(Intent intent) {}
    @Override public void onTaskRemoved(Intent rootIntent) {}
    @Override public void onDestroy() {}
    @Override public void onConfigurationChanged(Configuration newConfig) {}
    @Override public void onLowMemory() {}
    @Override public void onTrimMemory(int level) {}

    final PluginContainerService requireContainer() {
        if (container == null) {
            throw new IllegalStateException("Plugin Service has not been attached to a container");
        }
        return container;
    }

    private static void requireApi(int api, String operation) {
        if (Build.VERSION.SDK_INT < api) {
            throw new UnsupportedOperationException(operation + " requires Android API " + api + '+');
        }
    }
}
