package dev.x2c.plugin.runtime;

import android.app.Service;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.IBinder;

/** Real manifest Service that owns the framework token and forwards to one plugin delegate. */
public abstract class PluginContainerService extends Service {
    private PluginService pluginService;

    @Override public void onCreate() {
        super.onCreate();
        pluginService = PluginServiceManager.createDelegate(this);
        pluginService.performCreate();
        PluginServiceManager.containerCreated(this, pluginService);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;
        PluginService delegate = requirePlugin();
        PluginServiceManager.verifyTarget(delegate, intent);
        int result = delegate.performStartCommand(
                PluginServiceManager.requirePluginIntent(intent), flags, startId);
        if (result != START_NOT_STICKY) {
            throw new IllegalStateException(
                    "Plugin Services must return START_NOT_STICKY; sticky process restart "
                            + "cannot safely restore a class-only plugin");
        }
        return result;
    }

    @Override public IBinder onBind(Intent intent) {
        PluginService delegate = requirePlugin();
        PluginServiceManager.verifyTarget(delegate, intent);
        return delegate.performBind(PluginServiceManager.requirePluginIntent(intent));
    }

    @Override public boolean onUnbind(Intent intent) {
        return requirePlugin().performUnbind(PluginServiceManager.requirePluginIntent(intent));
    }

    @Override public void onRebind(Intent intent) {
        requirePlugin().performRebind(PluginServiceManager.requirePluginIntent(intent));
    }

    @Override public void onTaskRemoved(Intent rootIntent) {
        if (pluginService != null) {
            pluginService.performTaskRemoved(PluginServiceManager.requirePluginIntent(rootIntent));
        }
    }

    @Override public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (pluginService != null) pluginService.performConfigurationChanged(newConfig);
    }

    @Override public void onLowMemory() {
        super.onLowMemory();
        if (pluginService != null) pluginService.performLowMemory();
    }

    @Override public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (pluginService != null) pluginService.performTrimMemory(level);
    }

    @Override public void onDestroy() {
        if (pluginService != null) pluginService.performDestroy();
        PluginServiceManager.containerDestroyed(this);
        super.onDestroy();
    }

    private PluginService requirePlugin() {
        if (pluginService == null) {
            throw new IllegalStateException("Plugin Service delegate has not been created");
        }
        return pluginService;
    }
}
