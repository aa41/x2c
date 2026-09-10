package dev.x2c.plugin.runtime;

import android.content.Context;
import dev.x2c.plugin.api.PluginComponentRegistry;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Installs the generated component registry once for an isolated plugin ClassLoader.
 *
 * <p>The fixed registry bootstrap is the only reflective operation. Component construction and
 * lifecycle dispatch use generated direct-constructor bytecode.</p>
 */
public final class PluginComponentManager {
    public static final String GENERATED_REGISTRY_CLASS =
            "dev.x2c.generated.plugin.ComponentRegistry";
    private static final Object INSTALL_LOCK = new Object();
    private static final Set<String> INSTALLED_PLUGIN_IDS = new HashSet<String>();
    private static final Set<String> INSTALLING_PLUGIN_IDS = new HashSet<String>();

    private PluginComponentManager() {}

    public static PluginComponentRegistry loadAndInstall(
            Context context, ClassLoader pluginClassLoader) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(pluginClassLoader, "pluginClassLoader");
        try {
            Class<?> type = pluginClassLoader.loadClass(GENERATED_REGISTRY_CLASS);
            if (!PluginComponentRegistry.class.isAssignableFrom(type)) {
                throw new IllegalStateException(
                        "Generated registry uses a private or incompatible runtime copy: " + type);
            }
            Constructor<?> constructor = type.getConstructor();
            PluginComponentRegistry registry =
                    (PluginComponentRegistry) constructor.newInstance();
            install(context, pluginClassLoader, registry);
            return registry;
        } catch (ClassNotFoundException error) {
            throw new IllegalStateException(
                    "Plugin component registry is missing; apply dev.x2c.activity-plugin", error);
        } catch (NoSuchMethodException | InstantiationException | IllegalAccessException error) {
            throw new IllegalStateException("Cannot construct generated plugin component registry", error);
        } catch (InvocationTargetException error) {
            throw new IllegalStateException(
                    "Plugin component registry constructor failed", error.getCause());
        }
    }

    public static void install(
            Context context,
            ClassLoader pluginClassLoader,
            PluginComponentRegistry registry) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(pluginClassLoader, "pluginClassLoader");
        Objects.requireNonNull(registry, "registry");
        if (registry.getClass().getClassLoader() != pluginClassLoader) {
            throw new IllegalArgumentException(
                    "Registry was not loaded by the supplied plugin ClassLoader");
        }
        int runtimeAbi = registry.runtimeAbiVersion();
        if (runtimeAbi != PluginComponentRegistry.CURRENT_RUNTIME_ABI) {
            throw new IllegalStateException(
                    "Plugin component runtime ABI mismatch: host="
                            + PluginComponentRegistry.CURRENT_RUNTIME_ABI
                            + ", plugin=" + runtimeAbi);
        }
        String pluginId = requirePluginId(registry.pluginId());
        dev.x2c.runtime.X2C.requireInitialized(context);
        dev.x2c.runtime.X2C.registerPluginClassLoader(pluginClassLoader);

        synchronized (INSTALL_LOCK) {
            if (INSTALLED_PLUGIN_IDS.contains(pluginId)
                    || INSTALLING_PLUGIN_IDS.contains(pluginId)) {
                throw new IllegalStateException(
                        "Plugin ID is already installed for this process: " + pluginId);
            }
            INSTALLING_PLUGIN_IDS.add(pluginId);
            boolean activityInstalled = false;
            boolean serviceInstalled = false;
            boolean receiverInstalled = false;
            try {
                PluginActivityManager.installActivities(pluginClassLoader, registry);
                activityInstalled = true;
                PluginServiceManager.install(registry);
                serviceInstalled = true;
                PluginReceiverManager.install(registry);
                receiverInstalled = true;
                PluginProviderManager.install(context, pluginClassLoader, registry);
                INSTALLED_PLUGIN_IDS.add(pluginId);
            } catch (RuntimeException | Error failure) {
                if (receiverInstalled) PluginReceiverManager.uninstall(pluginId);
                if (serviceInstalled) PluginServiceManager.uninstall(pluginId);
                if (activityInstalled) PluginActivityManager.uninstallActivities(pluginId);
                throw failure;
            } finally {
                INSTALLING_PLUGIN_IDS.remove(pluginId);
            }
        }
    }

    /**
     * Dynamic component teardown is intentionally unsupported.
     *
     * <p>The runtime cannot prove that every framework-owned Activity, pending broadcast,
     * ContentProvider call, and Service start/bind transition has quiesced. Removing only the
     * registries could therefore strand a live container or dispatch into the wrong ClassLoader.
     * Install an upgraded plugin in a fresh application process instead.</p>
     */
    @Deprecated
    public static void uninstall(String pluginId) {
        throw new UnsupportedOperationException(
                "Hot uninstall is unsafe and unsupported; restart the application process "
                        + "before replacing plugin " + requirePluginId(pluginId));
    }

    private static String requirePluginId(String pluginId) {
        if (pluginId == null || pluginId.trim().isEmpty()) {
            throw new IllegalArgumentException("pluginId must not be blank");
        }
        return pluginId;
    }
}
