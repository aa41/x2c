package dev.x2c.plugin.runtime;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import dev.x2c.plugin.api.PluginComponentRegistry;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** Explicit, non-exported BroadcastReceiver router. */
public final class PluginReceiverManager {
    private static final String EXTRA_PLUGIN_ID = "dev.x2c.plugin.receiver.PLUGIN_ID";
    private static final String EXTRA_TARGET_RECEIVER = "dev.x2c.plugin.receiver.TARGET";
    private static final String EXTRA_PLUGIN_INTENT = "dev.x2c.plugin.receiver.INTENT";
    private static final Object LOCK = new Object();
    private static final Map<String, PluginComponentRegistry> PLUGINS =
            new HashMap<String, PluginComponentRegistry>();

    private PluginReceiverManager() {}

    static void install(PluginComponentRegistry registry) {
        synchronized (LOCK) {
            PluginComponentRegistry previous = PLUGINS.get(registry.pluginId());
            if (previous != null && previous != registry) {
                throw new IllegalStateException(
                        "Plugin Receivers are already installed: " + registry.pluginId());
            }
            PLUGINS.put(registry.pluginId(), registry);
        }
    }

    static void uninstall(String pluginId) {
        synchronized (LOCK) {
            PLUGINS.remove(pluginId);
        }
    }

    public static void sendBroadcast(
            Context context, String pluginId, String receiverClassName, Intent pluginIntent) {
        sendBroadcast(context, pluginId, receiverClassName, pluginIntent, null);
    }

    public static void sendBroadcast(
            Context context,
            String pluginId,
            String receiverClassName,
            Intent pluginIntent,
            String receiverPermission) {
        Objects.requireNonNull(context, "context");
        Intent routed = containerIntent(context, pluginId, receiverClassName, pluginIntent);
        if (receiverPermission == null) context.sendBroadcast(routed);
        else context.sendBroadcast(routed, receiverPermission);
    }

    public static void sendOrderedBroadcast(
            Context context,
            String pluginId,
            String receiverClassName,
            Intent pluginIntent,
            String receiverPermission,
            BroadcastReceiver resultReceiver,
            int initialCode,
            String initialData,
            Bundle initialExtras) {
        Objects.requireNonNull(context, "context");
        context.sendOrderedBroadcast(
                containerIntent(context, pluginId, receiverClassName, pluginIntent),
                receiverPermission,
                resultReceiver,
                null,
                initialCode,
                initialData,
                initialExtras);
    }

    static void dispatch(BroadcastReceiver container, Context context, Intent routedIntent) {
        String pluginId = required(routedIntent, EXTRA_PLUGIN_ID);
        String target = required(routedIntent, EXTRA_TARGET_RECEIVER);
        PluginComponentRegistry registry = registry(pluginId);
        if (!registry.containsReceiver(target)) {
            throw new SecurityException("Unknown routed plugin Receiver: " + target);
        }
        BroadcastReceiver raw = registry.createReceiver(target);
        if (!(raw instanceof PluginReceiver)) {
            throw new IllegalStateException(
                    "Receiver was not transformed to PluginReceiver: " + raw.getClass().getName());
        }
        PluginReceiver receiver = (PluginReceiver) raw;
        receiver.attachPlugin(container, pluginId, target);
        Intent pluginIntent = routedIntent.getParcelableExtra(EXTRA_PLUGIN_INTENT);
        if (pluginIntent == null) pluginIntent = new Intent();
        pluginIntent.setExtrasClassLoader(raw.getClass().getClassLoader());
        receiver.performReceive(
                new PluginContext(context, pluginId, raw.getClass().getClassLoader()), pluginIntent);
    }

    private static Intent containerIntent(
            Context context, String pluginId, String target, Intent pluginIntent) {
        Objects.requireNonNull(pluginIntent, "pluginIntent");
        PluginComponentRegistry registry = registry(pluginId);
        if (!registry.containsReceiver(target)) {
            throw new IllegalArgumentException("Unknown plugin Receiver: " + target);
        }
        Intent result = new Intent(context, PluginContainerReceiver.class);
        result.putExtra(EXTRA_PLUGIN_ID, pluginId);
        result.putExtra(EXTRA_TARGET_RECEIVER, target);
        result.putExtra(EXTRA_PLUGIN_INTENT, new Intent(pluginIntent));
        return result;
    }

    private static PluginComponentRegistry registry(String pluginId) {
        synchronized (LOCK) {
            PluginComponentRegistry registry = PLUGINS.get(pluginId);
            if (registry == null) throw new IllegalStateException("Plugin is not installed: " + pluginId);
            return registry;
        }
    }

    static String targetForPlugin(String pluginId, Intent intent) {
        if (intent == null || intent.getComponent() == null) return null;
        String target = intent.getComponent().getClassName();
        return registry(pluginId).containsReceiver(target) ? target : null;
    }

    private static String required(Intent intent, String name) {
        String value = intent == null ? null : intent.getStringExtra(name);
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Missing plugin Receiver route: " + name);
        }
        return value;
    }
}
