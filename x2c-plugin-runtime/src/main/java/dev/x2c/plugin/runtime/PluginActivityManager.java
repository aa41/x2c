package dev.x2c.plugin.runtime;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import dev.x2c.plugin.api.PluginActivityInfo;
import dev.x2c.plugin.api.PluginActivityRegistry;
import dev.x2c.plugin.api.PluginLaunchMode;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** Host-owned registry, container allocator, and Activity launch entry point. */
public final class PluginActivityManager {
    static final String EXTRA_PLUGIN_ID = "dev.x2c.plugin.extra.PLUGIN_ID";
    static final String EXTRA_TARGET_ACTIVITY = "dev.x2c.plugin.extra.TARGET_ACTIVITY";
    static final String EXTRA_PLUGIN_INTENT = "dev.x2c.plugin.extra.PLUGIN_INTENT";
    private static final int SLOTS_PER_LAUNCH_MODE = 8;
    private static final Object LOCK = new Object();
    private static final Map<String, InstalledPlugin> PLUGINS =
            new HashMap<String, InstalledPlugin>();
    private static final PluginSlotAllocator SLOTS =
            new PluginSlotAllocator(SLOTS_PER_LAUNCH_MODE);

    private PluginActivityManager() {}

    static void installActivities(
            ClassLoader pluginClassLoader, PluginActivityRegistry registry) {
        Objects.requireNonNull(pluginClassLoader, "pluginClassLoader");
        Objects.requireNonNull(registry, "registry");
        String pluginId = requireText(registry.pluginId(), "pluginId");
        if (registry.getClass().getClassLoader() != pluginClassLoader) {
            throw new IllegalArgumentException("Registry was not loaded by the supplied plugin ClassLoader");
        }
        synchronized (LOCK) {
            InstalledPlugin previous = PLUGINS.get(pluginId);
            if (previous != null) {
                if (previous.classLoader == pluginClassLoader && previous.registry == registry) return;
                throw new IllegalStateException("Plugin ID is already installed: " + pluginId);
            }
            PLUGINS.put(pluginId, new InstalledPlugin(pluginClassLoader, registry));
        }
    }

    static void uninstallActivities(String pluginId) {
        requireText(pluginId, "pluginId");
        synchronized (LOCK) {
            PLUGINS.remove(pluginId);
            SLOTS.removePlugin(pluginId);
        }
    }

    @SuppressLint("IntentWithNullActionLaunch") // This is payload data, not the launched Intent.
    public static void startActivity(
            Context context, String pluginId, String activityClassName) {
        startActivity(context, pluginId, activityClassName, new Intent(), null);
    }

    public static void startActivity(
            Context context,
            String pluginId,
            String activityClassName,
            Intent pluginIntent,
            Bundle options) {
        Context launcher = launchContext(context);
        Intent containerIntent = containerIntent(
                launcher, pluginId, activityClassName, pluginIntent);
        if (!(launcher instanceof Activity)) {
            containerIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        launcher.startActivity(containerIntent, options);
    }

    /** Starts any installed plugin Activity and forwards its result to the host or plugin caller. */
    public static void startActivityForResult(
            Activity source,
            String pluginId,
            String activityClassName,
            Intent pluginIntent,
            int requestCode,
            Bundle options) {
        Objects.requireNonNull(source, "source");
        Activity launcher = source instanceof PluginActivity
                ? ((PluginActivity) source).requireContainer() : source;
        Intent containerIntent = containerIntent(
                launcher, pluginId, activityClassName, pluginIntent);
        launcher.startActivityForResult(containerIntent, requestCode, options);
    }

    static void startFromPlugin(PluginActivity source, Intent intent, Bundle options) {
        String target = pluginTarget(source, intent);
        if (target == null) {
            source.requireContainer().startActivity(intent, options);
        } else {
            startActivity(source.requireContainer(), source.getPluginId(), target, intent, options);
        }
    }

    static void startFromPluginForResult(
            PluginActivity source, Intent intent, int requestCode, Bundle options) {
        String target = pluginTarget(source, intent);
        if (target == null) {
            source.requireContainer().startActivityForResult(intent, requestCode, options);
            return;
        }
        Intent containerIntent = containerIntent(
                source.requireContainer(), source.getPluginId(), target, intent);
        source.requireContainer().startActivityForResult(containerIntent, requestCode, options);
    }

    static PluginActivity createDelegate(PluginContainerActivity container, Intent containerIntent) {
        String pluginId = requireExtra(containerIntent, EXTRA_PLUGIN_ID);
        String target = requireExtra(containerIntent, EXTRA_TARGET_ACTIVITY);
        Intent pluginIntent = requirePluginIntent(containerIntent);
        InstalledPlugin installed = requirePlugin(pluginId);
        PluginActivityInfo info = installed.registry.info(target);
        if (!target.equals(info.className)) {
            throw new IllegalStateException("Registry returned mismatched Activity metadata for " + target);
        }
        Activity raw = installed.registry.create(target);
        if (!(raw instanceof PluginActivity)) {
            throw new IllegalStateException(
                    "Activity was not transformed to PluginActivity: " + raw.getClass().getName());
        }
        PluginActivity activity = (PluginActivity) raw;
        activity.attachPlugin(container, pluginId, target, pluginIntent);
        return activity;
    }

    static Intent requirePluginIntent(Intent containerIntent) {
        Intent pluginIntent = containerIntent.getParcelableExtra(EXTRA_PLUGIN_INTENT);
        return pluginIntent == null ? new Intent() : pluginIntent;
    }

    private static Intent containerIntent(
            Context context, String pluginId, String target, Intent pluginIntent) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(pluginIntent, "pluginIntent");
        InstalledPlugin installed = requirePlugin(requireText(pluginId, "pluginId"));
        PluginActivityInfo info = installed.registry.info(requireText(target, "activityClassName"));
        if (!target.equals(info.className)) {
            throw new IllegalArgumentException("Unknown plugin Activity: " + target);
        }
        Class<? extends PluginContainerActivity> container = allocateContainer(
                pluginId, target, info.launchMode);
        Intent result = new Intent(context, container);
        result.setFlags(pluginIntent.getFlags());
        result.putExtra(EXTRA_PLUGIN_ID, pluginId);
        result.putExtra(EXTRA_TARGET_ACTIVITY, target);
        result.putExtra(EXTRA_PLUGIN_INTENT, new Intent(pluginIntent));
        return result;
    }

    private static Context launchContext(Context context) {
        Objects.requireNonNull(context, "context");
        return context instanceof PluginActivity
                ? ((PluginActivity) context).requireContainer() : context;
    }

    private static String pluginTarget(PluginActivity source, Intent intent) {
        return targetForPlugin(source.getPluginId(), intent);
    }

    static String targetForPlugin(String pluginId, Intent intent) {
        Objects.requireNonNull(intent, "intent");
        ComponentName component = intent.getComponent();
        if (component == null) return null;
        String target = component.getClassName();
        InstalledPlugin installed = requirePlugin(pluginId);
        return installed.registry.contains(target) ? target : null;
    }

    private static InstalledPlugin requirePlugin(String pluginId) {
        synchronized (LOCK) {
            InstalledPlugin installed = PLUGINS.get(pluginId);
            if (installed == null) {
                throw new IllegalStateException("Plugin is not installed: " + pluginId);
            }
            return installed;
        }
    }

    private static Class<? extends PluginContainerActivity> allocateContainer(
            String pluginId, String target, PluginLaunchMode mode) {
        return PluginContainerPool.container(mode, SLOTS.allocate(pluginId, target, mode));
    }

    private static String requireExtra(Intent intent, String name) {
        return requireText(intent.getStringExtra(name), name);
    }

    private static String requireText(String value, String label) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
        return value;
    }

    private static final class InstalledPlugin {
        final ClassLoader classLoader;
        final PluginActivityRegistry registry;

        InstalledPlugin(ClassLoader classLoader, PluginActivityRegistry registry) {
            this.classLoader = classLoader;
            this.registry = registry;
        }
    }
}
