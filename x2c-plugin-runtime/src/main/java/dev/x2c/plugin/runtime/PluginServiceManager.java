package dev.x2c.plugin.runtime;

import android.annotation.TargetApi;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.IBinder;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import dev.x2c.plugin.api.PluginComponentRegistry;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;

/** Routes started and bound Services through a fixed host manifest pool. */
public final class PluginServiceManager {
    static final String EXTRA_PLUGIN_ID = "dev.x2c.plugin.service.PLUGIN_ID";
    static final String EXTRA_TARGET_SERVICE = "dev.x2c.plugin.service.TARGET";
    static final String EXTRA_PLUGIN_INTENT = "dev.x2c.plugin.service.INTENT";
    private static final int CAPACITY = 8;
    private static final Object LOCK = new Object();
    private static final Map<String, PluginComponentRegistry> PLUGINS =
            new HashMap<String, PluginComponentRegistry>();
    private static final Map<String, Integer> SLOTS = new HashMap<String, Integer>();
    private static final Map<Class<? extends PluginContainerService>, Route> ROUTES =
            new HashMap<Class<? extends PluginContainerService>, Route>();
    private static final Map<PluginContainerService, PluginService> ACTIVE =
            new IdentityHashMap<PluginContainerService, PluginService>();
    private static final Map<ServiceConnection, RoutedConnection> CONNECTIONS =
            new IdentityHashMap<ServiceConnection, RoutedConnection>();
    @SuppressWarnings("unchecked")
    private static final Class<? extends PluginContainerService>[] CONTAINERS = new Class[] {
            PluginContainerServices.Slot0.class,
            PluginContainerServices.Slot1.class,
            PluginContainerServices.Slot2.class,
            PluginContainerServices.Slot3.class,
            PluginContainerServices.Slot4.class,
            PluginContainerServices.Slot5.class,
            PluginContainerServices.Slot6.class,
            PluginContainerServices.Slot7.class
    };

    private PluginServiceManager() {}

    static void install(PluginComponentRegistry registry) {
        synchronized (LOCK) {
            PluginComponentRegistry previous = PLUGINS.get(registry.pluginId());
            if (previous != null && previous != registry) {
                throw new IllegalStateException(
                        "Plugin Services are already installed: " + registry.pluginId());
            }
            PLUGINS.put(registry.pluginId(), registry);
        }
    }

    static void uninstall(String pluginId) {
        synchronized (LOCK) {
            for (PluginService service : ACTIVE.values()) {
                if (pluginId.equals(service.getPluginId())) {
                    throw new IllegalStateException(
                            "Cannot uninstall a plugin while one of its Services is running: "
                                    + pluginId);
                }
            }
            PLUGINS.remove(pluginId);
            String prefix = pluginId + '/';
            Iterator<String> keys = SLOTS.keySet().iterator();
            while (keys.hasNext()) if (keys.next().startsWith(prefix)) keys.remove();
            Iterator<Map.Entry<Class<? extends PluginContainerService>, Route>> routes =
                    ROUTES.entrySet().iterator();
            while (routes.hasNext()) {
                if (pluginId.equals(routes.next().getValue().pluginId)) routes.remove();
            }
        }
    }

    public static ComponentName startService(
            Context context, String pluginId, String serviceClassName, Intent pluginIntent) {
        return start(context, pluginId, serviceClassName, pluginIntent, false);
    }

    public static ComponentName startForegroundService(
            Context context, String pluginId, String serviceClassName, Intent pluginIntent) {
        return start(context, pluginId, serviceClassName, pluginIntent, true);
    }

    public static boolean stopService(
            Context context, String pluginId, String serviceClassName) {
        Objects.requireNonNull(context, "context");
        Intent routed = existingContainerIntent(context, pluginId, serviceClassName);
        return routed != null && context.stopService(routed);
    }

    public static boolean bindService(
            Context context,
            String pluginId,
            String serviceClassName,
            Intent pluginIntent,
            ServiceConnection connection,
            int flags) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(connection, "connection");
        Intent routed = containerIntent(context, pluginId, serviceClassName, pluginIntent);
        RoutedConnection wrapper = new RoutedConnection(
                connection, context.getPackageName(), serviceClassName);
        synchronized (LOCK) {
            if (CONNECTIONS.containsKey(connection)) {
                throw new IllegalStateException(
                        "ServiceConnection is already bound to a plugin Service");
            }
            CONNECTIONS.put(connection, wrapper);
        }
        boolean bound = false;
        try {
            bound = context.bindService(routed, wrapper, flags);
            return bound;
        } finally {
            if (!bound) {
                synchronized (LOCK) {
                    CONNECTIONS.remove(connection);
                }
            }
        }
    }

    public static void unbindService(Context context, ServiceConnection connection) {
        Objects.requireNonNull(context, "context");
        if (!unbindIfPlugin(context, Objects.requireNonNull(connection, "connection"))) {
            throw new IllegalArgumentException(
                    "ServiceConnection is not bound to a plugin Service");
        }
    }

    static ComponentName startFromPlugin(
            PluginService source, Intent intent, boolean foreground) {
        String target = target(source.getPluginId(), intent);
        if (target == null) {
            return foreground && Build.VERSION.SDK_INT >= 26
                    ? source.requireContainer().startForegroundService(intent)
                    : source.requireContainer().startService(intent);
        }
        return start(source.requireContainer(), source.getPluginId(), target, intent, foreground);
    }

    static boolean stopFromPlugin(PluginService source, Intent intent) {
        String target = target(source.getPluginId(), intent);
        if (target == null) return source.requireContainer().stopService(intent);
        return stopService(source.requireContainer(), source.getPluginId(), target);
    }

    static boolean bindFromPlugin(
            PluginService source, Intent intent, ServiceConnection connection, int flags) {
        String target = target(source.getPluginId(), intent);
        if (target == null) return source.requireContainer().bindService(intent, connection, flags);
        return bindService(
                source.requireContainer(), source.getPluginId(), target, intent, connection, flags);
    }

    static boolean unbindFromPlugin(PluginService source, ServiceConnection connection) {
        return unbindIfPlugin(source.requireContainer(), connection);
    }

    static boolean unbindIfPlugin(Context context, ServiceConnection connection) {
        RoutedConnection wrapper;
        synchronized (LOCK) {
            wrapper = CONNECTIONS.remove(connection);
        }
        if (wrapper == null) return false;
        context.unbindService(wrapper);
        return true;
    }

    static PluginService createDelegate(PluginContainerService container) {
        Route route;
        synchronized (LOCK) {
            route = ROUTES.get(container.getClass());
        }
        if (route == null) {
            throw new IllegalStateException(
                    "Plugin Service route is unavailable. Sticky restart and direct container "
                            + "startup are intentionally unsupported: " + container.getClass().getName());
        }
        String pluginId = route.pluginId;
        String target = route.target;
        PluginComponentRegistry registry = registry(pluginId);
        Service raw = registry.createService(target);
        if (!(raw instanceof PluginService)) {
            throw new IllegalStateException(
                    "Service was not transformed to PluginService: " + raw.getClass().getName());
        }
        PluginService service = (PluginService) raw;
        service.attachPlugin(container, pluginId, target);
        return service;
    }

    static void containerCreated(PluginContainerService container, PluginService service) {
        synchronized (LOCK) {
            ACTIVE.put(container, service);
        }
    }

    static void containerDestroyed(PluginContainerService container) {
        synchronized (LOCK) {
            ACTIVE.remove(container);
        }
    }

    static void verifyTarget(PluginService service, Intent containerIntent) {
        String pluginId = required(containerIntent, EXTRA_PLUGIN_ID);
        String target = required(containerIntent, EXTRA_TARGET_SERVICE);
        if (!pluginId.equals(service.getPluginId())
                || !target.equals(service.getPluginServiceClassName())) {
            throw new IllegalStateException("A plugin Service container was reused for another target");
        }
    }

    static Intent requirePluginIntent(Intent containerIntent) {
        if (containerIntent == null) return new Intent();
        Intent result = containerIntent.getParcelableExtra(EXTRA_PLUGIN_INTENT);
        return result == null ? new Intent() : result;
    }

    private static ComponentName start(
            Context context,
            String pluginId,
            String serviceClassName,
            Intent pluginIntent,
            boolean foreground) {
        Objects.requireNonNull(context, "context");
        Intent routed = containerIntent(context, pluginId, serviceClassName, pluginIntent);
        if (foreground) ensureForegroundCapable(context, routed.getComponent());
        ComponentName actual = foreground && Build.VERSION.SDK_INT >= 26
                ? context.startForegroundService(routed) : context.startService(routed);
        if (actual == null) return null;
        return new ComponentName(context.getPackageName(), serviceClassName);
    }

    private static void ensureForegroundCapable(Context context, ComponentName container) {
        if (Build.VERSION.SDK_INT < 34) return;
        try {
            ServiceInfo info = context.getPackageManager().getServiceInfo(container, 0);
            if (info.getForegroundServiceType() == ServiceInfo.FOREGROUND_SERVICE_TYPE_NONE) {
                throw new IllegalStateException(
                        "The selected plugin Service container has no foregroundServiceType. "
                                + "Declare only the required type and permission in the host "
                                + "Manifest before using startForegroundService.");
            }
        } catch (PackageManager.NameNotFoundException error) {
            throw new IllegalStateException(
                    "Plugin Service container is missing from the merged host Manifest: "
                            + container, error);
        }
    }

    private static Intent containerIntent(
            Context context, String pluginId, String target, Intent pluginIntent) {
        Objects.requireNonNull(pluginIntent, "pluginIntent");
        PluginComponentRegistry registry = registry(pluginId);
        if (!registry.containsService(target)) {
            throw new IllegalArgumentException("Unknown plugin Service: " + target);
        }
        Class<? extends PluginContainerService> container = CONTAINERS[allocate(pluginId, target)];
        synchronized (LOCK) {
            Route existing = ROUTES.get(container);
            if (existing != null
                    && (!pluginId.equals(existing.pluginId) || !target.equals(existing.target))) {
                throw new IllegalStateException(
                        "Plugin Service slot collision for " + container.getName());
            }
            ROUTES.put(container, new Route(pluginId, target));
        }
        Intent result = new Intent(context, container);
        result.putExtra(EXTRA_PLUGIN_ID, pluginId);
        result.putExtra(EXTRA_TARGET_SERVICE, target);
        result.putExtra(EXTRA_PLUGIN_INTENT, new Intent(pluginIntent));
        return result;
    }

    /** Returns no route for a Service that has never been started or bound. */
    private static Intent existingContainerIntent(
            Context context, String pluginId, String target) {
        Objects.requireNonNull(pluginId, "pluginId");
        Objects.requireNonNull(target, "serviceClassName");
        Class<? extends PluginContainerService> container;
        synchronized (LOCK) {
            PluginComponentRegistry registry = PLUGINS.get(pluginId);
            if (registry == null) {
                throw new IllegalStateException("Plugin is not installed: " + pluginId);
            }
            if (!registry.containsService(target)) {
                throw new IllegalArgumentException("Unknown plugin Service: " + target);
            }
            Integer slot = SLOTS.get(pluginId + '/' + target);
            if (slot == null) return null;
            container = CONTAINERS[slot.intValue()];
            Route route = ROUTES.get(container);
            if (route == null
                    || !pluginId.equals(route.pluginId)
                    || !target.equals(route.target)) {
                throw new IllegalStateException(
                        "Plugin Service slot has an inconsistent route: " + target);
            }
        }
        Intent result = new Intent(context, container);
        result.putExtra(EXTRA_PLUGIN_ID, pluginId);
        result.putExtra(EXTRA_TARGET_SERVICE, target);
        result.putExtra(EXTRA_PLUGIN_INTENT, new Intent());
        return result;
    }

    private static int allocate(String pluginId, String target) {
        synchronized (LOCK) {
            String key = pluginId + '/' + target;
            Integer existing = SLOTS.get(key);
            if (existing != null) return existing.intValue();
            if (SLOTS.size() >= CAPACITY) {
                throw new IllegalStateException("No free plugin Service container; capacity is 8");
            }
            int slot = 0;
            while (SLOTS.containsValue(Integer.valueOf(slot))) slot++;
            SLOTS.put(key, Integer.valueOf(slot));
            return slot;
        }
    }

    private static String target(String pluginId, Intent intent) {
        if (intent == null || intent.getComponent() == null) return null;
        String target = intent.getComponent().getClassName();
        return registry(pluginId).containsService(target) ? target : null;
    }

    static String targetForPlugin(String pluginId, Intent intent) {
        return target(pluginId, intent);
    }

    private static PluginComponentRegistry registry(String pluginId) {
        synchronized (LOCK) {
            PluginComponentRegistry registry = PLUGINS.get(pluginId);
            if (registry == null) throw new IllegalStateException("Plugin is not installed: " + pluginId);
            return registry;
        }
    }

    private static String required(Intent intent, String name) {
        String value = intent == null ? null : intent.getStringExtra(name);
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Missing plugin Service route: " + name);
        }
        return value;
    }

    private static final class Route {
        final String pluginId;
        final String target;

        Route(String pluginId, String target) {
            this.pluginId = pluginId;
            this.target = target;
        }
    }

    private static final class RoutedConnection implements ServiceConnection {
        private final ServiceConnection delegate;
        private final ComponentName pluginComponent;

        RoutedConnection(ServiceConnection delegate, String packageName, String className) {
            this.delegate = delegate;
            pluginComponent = new ComponentName(packageName, className);
        }

        @Override public void onServiceConnected(ComponentName name, IBinder service) {
            delegate.onServiceConnected(pluginComponent, service);
        }

        @Override public void onServiceDisconnected(ComponentName name) {
            delegate.onServiceDisconnected(pluginComponent);
        }

        @TargetApi(26)
        @Override public void onBindingDied(ComponentName name) {
            delegate.onBindingDied(pluginComponent);
        }

        @TargetApi(28)
        @Override public void onNullBinding(ComponentName name) {
            delegate.onNullBinding(pluginComponent);
        }
    }
}
