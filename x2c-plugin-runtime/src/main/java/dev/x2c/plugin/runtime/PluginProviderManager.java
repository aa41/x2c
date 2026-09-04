package dev.x2c.plugin.runtime;

import android.content.ContentProvider;
import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.Context;
import android.content.OperationApplicationException;
import android.content.pm.ProviderInfo;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Looper;
import dev.x2c.plugin.api.PluginComponentRegistry;
import dev.x2c.plugin.api.PluginProviderInfo;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Owns plugin Provider instances and maps their virtual authorities to one host Provider.
 *
 * <p>Providers are created at plugin-install time on the main thread, matching Android's
 * single-create contract. They are not visible to other applications and do not gain a real
 * PackageManager identity.</p>
 */
public final class PluginProviderManager {
    static final String CALL_URI_KEY = "dev.x2c.plugin.provider.ORIGINAL_URI";
    private static final String AUTHORITY_SUFFIX = ".x2c.plugin.provider";
    private static final Object LOCK = new Object();
    private static final Map<String, Entry> BY_AUTHORITY = new HashMap<String, Entry>();
    private static final Map<String, List<Entry>> BY_PLUGIN =
            new HashMap<String, List<Entry>>();
    private static volatile String containerAuthority;

    private PluginProviderManager() {}

    static void install(
            Context context,
            ClassLoader pluginClassLoader,
            PluginComponentRegistry registry) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            throw new IllegalStateException("Plugin Providers must be installed on the main thread");
        }
        Context appContext = Objects.requireNonNull(context, "context").getApplicationContext();
        if (appContext == null) appContext = context;
        String hostAuthority = appContext.getPackageName() + AUTHORITY_SUFFIX;
        String currentAuthority = containerAuthority;
        if (currentAuthority != null && !currentAuthority.equals(hostAuthority)) {
            throw new IllegalStateException("Plugin runtime is already attached to another host package");
        }
        containerAuthority = hostAuthority;

        PluginProviderInfo[] declared = registry.providers();
        if (declared == null) {
            throw new IllegalStateException("Plugin registry returned a null Provider array");
        }
        Map<String, Entry> pending = new LinkedHashMap<String, Entry>();
        for (PluginProviderInfo info : declared) {
            validate(registry, info);
            if (pending.containsKey(info.authority)) {
                throw new IllegalStateException(
                        "Duplicate plugin ContentProvider authority: " + info.authority);
            }
            pending.put(info.authority,
                    new Entry(registry.pluginId(), pluginClassLoader, info));
        }

        synchronized (LOCK) {
            if (BY_PLUGIN.containsKey(registry.pluginId())) {
                throw new IllegalStateException(
                        "Plugin Providers are already installed: " + registry.pluginId());
            }
            for (String authority : pending.keySet()) {
                Entry existing = BY_AUTHORITY.get(authority);
                if (existing != null) {
                    throw new IllegalStateException(
                            "Plugin ContentProvider authority is already installed: " + authority
                                    + " by " + existing.pluginId);
                }
            }
            BY_PLUGIN.put(registry.pluginId(), new ArrayList<Entry>(pending.values()));
            BY_AUTHORITY.putAll(pending);
        }

        List<ContentProvider> created = new ArrayList<ContentProvider>();
        try {
            for (Entry entry : pending.values()) {
                ContentProvider raw = registry.createProvider(entry.info.className);
                if (!(raw instanceof PluginContentProvider)) {
                    throw new IllegalStateException(
                            "Provider was not transformed to PluginContentProvider: "
                                    + raw.getClass().getName());
                }
                if (raw.getClass().getClassLoader() != pluginClassLoader) {
                    throw new IllegalStateException(
                            "Plugin Provider was created by an unexpected ClassLoader: "
                                    + raw.getClass().getName());
                }
                ProviderInfo frameworkInfo = new ProviderInfo();
                frameworkInfo.packageName = appContext.getPackageName();
                frameworkInfo.name = entry.info.className;
                frameworkInfo.authority = entry.info.authority;
                frameworkInfo.exported = false;
                frameworkInfo.grantUriPermissions = false;
                raw.attachInfo(
                        new PluginContext(appContext, registry.pluginId(), pluginClassLoader),
                        frameworkInfo);
                entry.provider = raw;
                created.add(raw);
            }
        } catch (RuntimeException | Error failure) {
            removeEntries(registry.pluginId());
            for (ContentProvider provider : created) provider.shutdown();
            throw failure;
        }
    }

    static void uninstall(String pluginId) {
        List<Entry> removed = removeEntries(pluginId);
        for (Entry entry : removed) {
            ContentProvider provider = entry.provider;
            if (provider != null) provider.shutdown();
        }
    }

    public static Uri route(Uri originalUri) {
        Objects.requireNonNull(originalUri, "originalUri");
        if (!"content".equals(originalUri.getScheme())) return originalUri;
        String authority = originalUri.getAuthority();
        if (authority == null) return originalUri;
        Entry entry;
        synchronized (LOCK) {
            entry = BY_AUTHORITY.get(authority);
        }
        if (entry == null) return originalUri;
        String hostAuthority = requireContainerAuthority();
        Uri.Builder builder = new Uri.Builder()
                .scheme("content")
                .authority(hostAuthority)
                .appendPath(entry.pluginId)
                .appendPath(authority);
        for (String segment : originalUri.getPathSegments()) builder.appendPath(segment);
        builder.encodedQuery(originalUri.getEncodedQuery());
        builder.encodedFragment(originalUri.getEncodedFragment());
        return builder.build();
    }

    public static Uri restore(Uri routedUri) {
        Objects.requireNonNull(routedUri, "routedUri");
        if (!"content".equals(routedUri.getScheme())
                || !requireContainerAuthority().equals(routedUri.getAuthority())) {
            return routedUri;
        }
        List<String> segments = routedUri.getPathSegments();
        if (segments.size() < 2) {
            throw new IllegalArgumentException("Malformed plugin Provider URI: " + routedUri);
        }
        String pluginId = segments.get(0);
        String authority = segments.get(1);
        Entry entry = requireEntry(authority);
        if (!entry.pluginId.equals(pluginId)) {
            throw new SecurityException("Plugin Provider URI identity mismatch: " + routedUri);
        }
        Uri.Builder builder = new Uri.Builder().scheme("content").authority(authority);
        for (int index = 2; index < segments.size(); index++) {
            builder.appendPath(segments.get(index));
        }
        builder.encodedQuery(routedUri.getEncodedQuery());
        builder.encodedFragment(routedUri.getEncodedFragment());
        return builder.build();
    }

    static ContentProvider provider(Uri routedUri) {
        Uri original = restore(routedUri);
        return requireProvider(requireEntry(original.getAuthority()));
    }

    static ContentProviderResult[] applyBatch(
            String authority, ArrayList<ContentProviderOperation> operations)
            throws OperationApplicationException {
        Entry entry;
        synchronized (LOCK) {
            entry = BY_AUTHORITY.get(authority);
        }
        if (entry == null) return null;
        return requireProvider(entry).applyBatch(operations);
    }

    static ClassLoader classLoaderForAuthority(String authority) {
        synchronized (LOCK) {
            Entry entry = BY_AUTHORITY.get(authority);
            return entry == null ? null : entry.classLoader;
        }
    }

    static void onConfigurationChanged(Configuration configuration) {
        for (ContentProvider provider : snapshotProviders()) {
            provider.onConfigurationChanged(configuration);
        }
    }

    static void onLowMemory() {
        for (ContentProvider provider : snapshotProviders()) provider.onLowMemory();
    }

    static void onTrimMemory(int level) {
        for (ContentProvider provider : snapshotProviders()) provider.onTrimMemory(level);
    }

    static String containerAuthority() {
        return requireContainerAuthority();
    }

    private static ContentProvider requireProvider(Entry entry) {
        ContentProvider provider = entry.provider;
        if (provider == null) {
            throw new IllegalStateException(
                    "Plugin ContentProvider is still being installed: " + entry.info.className);
        }
        return provider;
    }

    private static Entry requireEntry(String authority) {
        Entry entry;
        synchronized (LOCK) {
            entry = BY_AUTHORITY.get(authority);
        }
        if (entry == null) {
            throw new IllegalArgumentException("Unknown plugin ContentProvider authority: " + authority);
        }
        return entry;
    }

    private static String requireContainerAuthority() {
        String authority = containerAuthority;
        if (authority == null) {
            throw new IllegalStateException(
                    "Plugin Provider runtime is not initialized; install the plugin with a Context first");
        }
        return authority;
    }

    private static void validate(
            PluginComponentRegistry registry, PluginProviderInfo info) {
        if (info == null) {
            throw new IllegalStateException("Plugin registry returned a null Provider entry");
        }
        if (!registry.containsProvider(info.className)) {
            throw new IllegalStateException(
                    "Provider metadata is not constructible: " + info.className);
        }
        PluginProviderInfo direct = registry.providerInfo(info.className);
        if (!info.className.equals(direct.className)
                || !info.authority.equals(direct.authority)) {
            throw new IllegalStateException(
                    "Provider metadata mismatch for " + info.className);
        }
        if (!info.authority.matches("[A-Za-z0-9][A-Za-z0-9._-]*")) {
            throw new IllegalStateException(
                    "Invalid plugin ContentProvider authority: " + info.authority);
        }
    }

    private static List<Entry> removeEntries(String pluginId) {
        List<Entry> removed;
        synchronized (LOCK) {
            removed = BY_PLUGIN.remove(pluginId);
            if (removed == null) return new ArrayList<Entry>();
            Iterator<Map.Entry<String, Entry>> iterator = BY_AUTHORITY.entrySet().iterator();
            while (iterator.hasNext()) {
                if (pluginId.equals(iterator.next().getValue().pluginId)) iterator.remove();
            }
        }
        return removed;
    }

    private static List<ContentProvider> snapshotProviders() {
        List<ContentProvider> providers = new ArrayList<ContentProvider>();
        synchronized (LOCK) {
            for (List<Entry> entries : BY_PLUGIN.values()) {
                for (Entry entry : entries) {
                    if (entry.provider != null) providers.add(entry.provider);
                }
            }
        }
        return providers;
    }

    private static final class Entry {
        final String pluginId;
        final ClassLoader classLoader;
        final PluginProviderInfo info;
        volatile ContentProvider provider;

        Entry(
                String pluginId,
                ClassLoader classLoader,
                PluginProviderInfo info) {
            this.pluginId = pluginId;
            this.classLoader = classLoader;
            this.info = info;
        }
    }
}
