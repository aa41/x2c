package dev.x2c.plugin.api;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ContentProvider;

/**
 * Generated once per plugin ClassLoader. All component instances are constructed by direct
 * bytecode; only loading this fixed registry class requires one bootstrap lookup.
 */
public interface PluginComponentRegistry extends PluginActivityRegistry {
    boolean containsService(String className);

    Service createService(String className);

    boolean containsReceiver(String className);

    BroadcastReceiver createReceiver(String className);

    boolean containsProvider(String className);

    PluginProviderInfo providerInfo(String className);

    /** Returns a defensive array containing every provider declared by this plugin. */
    PluginProviderInfo[] providers();

    ContentProvider createProvider(String className);
}
