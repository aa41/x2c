package dev.x2c.runtime;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.os.Build;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Plugin-only resource provider that keeps declared plugin resources authoritative and falls back
 * to the host only for names that the plugin did not declare.
 *
 * <p>Plugin {@code id} and {@code layout} values are always synthetic and isolated from the host.
 * Host identifiers are exposed only for the value/drawable types that can be loaded losslessly
 * through {@link Resources}. Normal AAR modules never install this provider.</p>
 */
final class PluginFirstX2cResourceProvider implements X2cResourceProvider {
    private final Context hostContext;
    private final Resources hostResources;
    private final String hostPackageName;
    private final X2cResourceProvider plugin;
    private final ConcurrentMap<String, Integer> hostIdentifiers =
            new ConcurrentHashMap<String, Integer>();

    PluginFirstX2cResourceProvider(Context hostContext, X2cResourceProvider plugin) {
        Context context = Objects.requireNonNull(hostContext, "hostContext");
        Context application = context.getApplicationContext();
        this.hostContext = application == null ? context : application;
        this.hostResources = this.hostContext.getResources();
        this.hostPackageName = this.hostContext.getPackageName();
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    @Override
    public int getIdentifier(String type, String name) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(name, "name");
        int pluginIdentifier = plugin.findIdentifier(type, name);
        if (pluginIdentifier != 0) {
            return pluginIdentifier;
        }
        if (!supportsHostFallback(type)) {
            // Preserve the generated provider's precise unknown-resource error. In particular,
            // host id/layout values must never enter the plugin View or layout registries.
            return plugin.getIdentifier(type, name);
        }
        return requireHostIdentifier(type, name);
    }

    @Override
    public int findIdentifier(String type, String name) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(name, "name");
        int pluginIdentifier = plugin.findIdentifier(type, name);
        if (pluginIdentifier != 0) {
            return pluginIdentifier;
        }
        return supportsHostFallback(type) ? hostIdentifier(type, name) : 0;
    }

    @Override
    public boolean hasResource(String type, String name) {
        return findIdentifier(type, name) != 0;
    }

    @Override
    public CharSequence getText(String name) {
        if (plugin.hasResource("string", name)) {
            return plugin.getText(name);
        }
        return hostResources.getText(requireHostIdentifier("string", name));
    }

    @Override
    public String getString(String name, Object... arguments) {
        if (plugin.hasResource("string", name)) {
            return plugin.getString(name, arguments);
        }
        int identifier = requireHostIdentifier("string", name);
        return arguments.length == 0
                ? hostResources.getString(identifier)
                : hostResources.getString(identifier, arguments);
    }

    @Override
    @SuppressWarnings("deprecation")
    public int getColor(String name) {
        if (plugin.hasResource("color", name)) {
            return plugin.getColor(name);
        }
        int identifier = requireHostIdentifier("color", name);
        return Build.VERSION.SDK_INT >= 23
                ? hostResources.getColor(identifier, hostContext.getTheme())
                : hostResources.getColor(identifier);
    }

    @Override
    @SuppressWarnings("deprecation")
    public ColorStateList getColorStateList(String name) {
        if (plugin.hasResource("color", name)) {
            return plugin.getColorStateList(name);
        }
        int identifier = requireHostIdentifier("color", name);
        return Build.VERSION.SDK_INT >= 23
                ? hostResources.getColorStateList(identifier, hostContext.getTheme())
                : hostResources.getColorStateList(identifier);
    }

    @Override
    public boolean getBoolean(String name) {
        if (plugin.hasResource("bool", name)) {
            return plugin.getBoolean(name);
        }
        return hostResources.getBoolean(requireHostIdentifier("bool", name));
    }

    @Override
    public int getInteger(String name) {
        if (plugin.hasResource("integer", name)) {
            return plugin.getInteger(name);
        }
        return hostResources.getInteger(requireHostIdentifier("integer", name));
    }

    @Override
    public float getDimension(Context context, String name) {
        if (plugin.hasResource("dimen", name)) {
            return plugin.getDimension(context, name);
        }
        return hostResources.getDimension(requireHostIdentifier("dimen", name));
    }

    @Override
    public float getFraction(String name, float base, float parentBase) {
        if (plugin.hasResource("fraction", name)) {
            return plugin.getFraction(name, base, parentBase);
        }
        return hostResources.getFraction(
                requireHostIdentifier("fraction", name), Math.round(base), Math.round(parentBase));
    }

    @Override
    public String[] getStringArray(String name) {
        if (plugin.hasResource("array", name)) {
            return plugin.getStringArray(name);
        }
        return hostResources.getStringArray(requireHostIdentifier("array", name));
    }

    @Override
    public CharSequence[] getTextArray(String name) {
        if (plugin.hasResource("array", name)) {
            return plugin.getTextArray(name);
        }
        return hostResources.getTextArray(requireHostIdentifier("array", name));
    }

    @Override
    public int[] getIntegerArray(String name) {
        if (plugin.hasResource("array", name)) {
            return plugin.getIntegerArray(name);
        }
        return hostResources.getIntArray(requireHostIdentifier("array", name));
    }

    @Override
    public int[] getIntArray(String name) {
        return getIntegerArray(name);
    }

    @Override
    public Object[] getArray(Context context, String name) {
        // Android TypedArray cannot be converted to the generated heterogeneous Object[] without
        // losing element type information. This API intentionally remains plugin-only.
        return plugin.getArray(context, name);
    }

    @Override
    public String getPlural(String name, X2cQuantity quantity, Object... arguments) {
        // X2cQuantity is already a grammatical category, while Android Resources requires the
        // original integer quantity. Inventing one would select the wrong locale-specific rule.
        return plugin.getPlural(name, quantity, arguments);
    }

    @Override
    public CharSequence getQuantityText(String name, X2cQuantity quantity) {
        return plugin.getQuantityText(name, quantity);
    }

    @Override
    public String getQuantityString(
            String name, X2cQuantity quantity, Object... arguments) {
        return plugin.getQuantityString(name, quantity, arguments);
    }

    @Override
    @SuppressWarnings("deprecation")
    public Drawable getDrawable(Context context, String name) {
        if (plugin.hasResource("drawable", name)) {
            // Declared bitmap drawables deliberately fail here and must use X2cImages/CDN. A host
            // drawable with the same name is not allowed to replace a plugin-owned asset.
            return plugin.getDrawable(context, name);
        }
        int identifier = requireHostIdentifier("drawable", name);
        return Build.VERSION.SDK_INT >= 21
                ? hostResources.getDrawable(
                        identifier, Objects.requireNonNull(context, "context").getTheme())
                : hostResources.getDrawable(identifier);
    }

    ClassLoader moduleClassLoader() {
        return plugin.getClass().getClassLoader();
    }

    void clearIdentifierCache() {
        hostIdentifiers.clear();
    }

    private int requireHostIdentifier(String type, String name) {
        int identifier = hostIdentifier(type, name);
        if (identifier != 0) {
            return identifier;
        }
        throw new Resources.NotFoundException(
                "Missing X2C plugin resource and host resource @" + type + '/' + name
                        + " in " + hostPackageName);
    }

    private int hostIdentifier(String type, String name) {
        String key = type + '/' + name;
        Integer cached = hostIdentifiers.get(key);
        if (cached != null) {
            return cached.intValue();
        }
        int resolved = hostResources.getIdentifier(name, type, hostPackageName);
        Integer previous = hostIdentifiers.putIfAbsent(key, Integer.valueOf(resolved));
        return previous == null ? resolved : previous.intValue();
    }

    private static boolean supportsHostFallback(String type) {
        return "string".equals(type)
                || "color".equals(type)
                || "bool".equals(type)
                || "integer".equals(type)
                || "dimen".equals(type)
                || "fraction".equals(type)
                || "array".equals(type)
                || "drawable".equals(type);
    }
}
