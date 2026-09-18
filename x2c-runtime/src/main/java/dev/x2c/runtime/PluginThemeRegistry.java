package dev.x2c.runtime;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.view.ContextThemeWrapper;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Host-owned immutable plugin Activity theme table. */
public final class PluginThemeRegistry {
    private static final Object LOCK = new Object();
    private static volatile ResolvedCatalog catalog;

    private static final int[] STRUCTURAL_ATTRIBUTES = {
            android.R.attr.windowIsTranslucent,
            android.R.attr.windowIsFloating,
            android.R.attr.windowNoDisplay,
            android.R.attr.windowActionBar,
            android.R.attr.windowActionBarOverlay,
            android.R.attr.windowNoTitle
    };
    private static final String[] STRUCTURAL_ATTRIBUTE_NAMES = {
            "windowIsTranslucent",
            "windowIsFloating",
            "windowNoDisplay",
            "windowActionBar",
            "windowActionBarOverlay",
            "windowNoTitle"
    };
    private static final boolean[] OPAQUE_NO_ACTION_BAR_VALUES = {
            false, false, false, false, false, true
    };

    private PluginThemeRegistry() {}

    static void initialize(Application application, PluginHostConfig config) {
        synchronized (LOCK) {
            ResolvedCatalog current = catalog;
            if (current != null) {
                if (current.application != application || !current.config.sameAs(config)) {
                    throw new IllegalStateException(
                            "X2C was already initialized with a different host/plugin theme "
                                    + "configuration");
                }
                return;
            }
            // Keep name lookup inside the one-time publication lock. Repeated initialization with
            // the same immutable value configuration never calls Resources#getIdentifier again.
            catalog = resolve(application, config);
        }
    }

    /** Validates untrusted generated metadata before a plugin is registered. */
    public static void validateThemeKey(String themeKey) {
        if (!Objects.requireNonNull(themeKey, "themeKey").isEmpty()) {
            PluginHostConfig.requireThemeKey(themeKey);
        }
    }

    /** Resolves and validates one Activity contract without launching it. */
    public static void validateActivityTheme(String themeKey, String activityClassName) {
        resolveActivityTheme(themeKey, activityClassName);
    }

    /** Returns the real host style ID for one installed plugin Activity. */
    public static int resolveActivityTheme(String themeKey, String activityClassName) {
        validateThemeKey(themeKey);
        return requireCatalog().resolve(
                themeKey, Objects.requireNonNull(activityClassName, "activityClassName"));
    }

    /** Prevents plugin code from applying an arbitrary style outside the immutable host table. */
    public static void requireRegisteredStyle(int styleResId) {
        ResolvedCatalog current = requireCatalog();
        if (!current.registeredStyleIds.contains(Integer.valueOf(styleResId))) {
            throw new IllegalArgumentException(
                    "Plugin Activity may only apply a host-registered theme; unknown style ID: 0x"
                            + Integer.toHexString(styleResId));
        }
        // The ID table is immutable, but a qualified style body can change with Configuration.
        // Recheck the fixed container profile before each actual application of the theme.
        validateOpaqueTheme(
                current.application,
                "<registered:0x" + Integer.toHexString(styleResId) + '>',
                styleResId);
    }

    private static ResolvedCatalog requireCatalog() {
        ResolvedCatalog current = catalog;
        if (current == null) {
            throw new IllegalStateException(
                    "Plugin Activities require X2C.init(application, pluginHostConfig) "
                            + "before plugin installation");
        }
        return current;
    }

    private static ResolvedCatalog resolve(Application application, PluginHostConfig config) {
        Integer defaultTheme = config.defaultActivityTheme == null
                ? null : Integer.valueOf(resolveReference(
                        application, "<host-default>", config.defaultActivityTheme));
        Map<String, Integer> themes = new LinkedHashMap<String, Integer>();
        for (Map.Entry<String, PluginHostConfig.ThemeReference> entry
                : config.activityThemes.entrySet()) {
            themes.put(entry.getKey(), Integer.valueOf(
                    resolveReference(application, entry.getKey(), entry.getValue())));
        }
        return new ResolvedCatalog(application, config, defaultTheme, themes);
    }

    private static int resolveReference(
            Application application,
            String themeKey,
            PluginHostConfig.ThemeReference reference) {
        Resources resources = application.getResources();
        int styleResId = reference.resourceId;
        if (styleResId == 0) {
            styleResId = resources.getIdentifier(
                    reference.resourceName, "style", application.getPackageName());
            if (styleResId == 0) {
                throw new IllegalArgumentException(
                        "Host style not found for plugin Activity themeKey " + themeKey
                                + ": @style/" + reference.resourceName);
            }
        }
        validateStyleResource(resources, themeKey, styleResId);
        validateOpaqueTheme(application, themeKey, styleResId);
        return styleResId;
    }

    private static void validateStyleResource(
            Resources resources, String themeKey, int styleResId) {
        try {
            String type = resources.getResourceTypeName(styleResId);
            if (!"style".equals(type)) {
                throw new IllegalArgumentException(
                        "Plugin Activity themeKey " + themeKey
                                + " must resolve to a style, but was " + type + ": 0x"
                                + Integer.toHexString(styleResId));
            }
        } catch (Resources.NotFoundException error) {
            throw new IllegalArgumentException(
                    "Plugin Activity themeKey " + themeKey
                            + " references an unknown host resource: 0x"
                            + Integer.toHexString(styleResId), error);
        }
    }

    @SuppressLint("ResourceType") // The array contains public attr IDs, not resource values.
    private static void validateOpaqueTheme(
            Application application, String themeKey, int styleResId) {
        ContextThemeWrapper themed = new ContextThemeWrapper(application, styleResId);
        TypedArray values = themed.obtainStyledAttributes(STRUCTURAL_ATTRIBUTES);
        try {
            for (int index = 0; index < STRUCTURAL_ATTRIBUTES.length; index++) {
                boolean actual = values.getBoolean(index, !OPAQUE_NO_ACTION_BAR_VALUES[index]);
                if (actual != OPAQUE_NO_ACTION_BAR_VALUES[index]) {
                    throw new IllegalArgumentException(
                            "Plugin Activity themeKey " + themeKey + " has unsupported "
                                    + STRUCTURAL_ATTRIBUTE_NAMES[index] + '=' + actual
                                    + "; the current container profile requires "
                                    + OPAQUE_NO_ACTION_BAR_VALUES[index]);
                }
            }
        } finally {
            values.recycle();
        }
    }

    private static final class ResolvedCatalog {
        final Application application;
        final PluginHostConfig config;
        final Integer defaultTheme;
        final Map<String, Integer> activityThemes;
        final Set<Integer> registeredStyleIds;

        ResolvedCatalog(
                Application application,
                PluginHostConfig config,
                Integer defaultTheme,
                Map<String, Integer> activityThemes) {
            this.application = Objects.requireNonNull(application, "application");
            this.config = Objects.requireNonNull(config, "config");
            this.defaultTheme = defaultTheme;
            this.activityThemes = Collections.unmodifiableMap(
                    new LinkedHashMap<String, Integer>(activityThemes));
            Set<Integer> styleIds = new HashSet<Integer>(activityThemes.values());
            if (defaultTheme != null) styleIds.add(defaultTheme);
            this.registeredStyleIds = Collections.unmodifiableSet(styleIds);
        }

        int resolve(String themeKey, String activityClassName) {
            Integer style = themeKey.isEmpty() ? defaultTheme : activityThemes.get(themeKey);
            if (style == null) {
                String requested = themeKey.isEmpty() ? "<host-default>" : themeKey;
                throw new IllegalStateException(
                        "Host has not registered plugin Activity themeKey " + requested
                                + " required by " + activityClassName);
            }
            return style.intValue();
        }
    }
}
