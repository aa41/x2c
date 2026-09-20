package dev.x2c.runtime;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.view.ContextThemeWrapper;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

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
    private static final boolean[] COMMON_NO_ACTION_BAR_VALUES = {
            false, false, false, false, true
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
        validateActivityTheme(themeKey, activityClassName, false);
    }

    /** Resolves and validates one Activity contract against its fixed Window profile. */
    public static void validateActivityTheme(
            String themeKey, String activityClassName, boolean translucent) {
        resolveActivityTheme(themeKey, activityClassName, translucent);
    }

    /** Returns the real host style ID for one installed plugin Activity. */
    public static int resolveActivityTheme(String themeKey, String activityClassName) {
        return resolveActivityTheme(themeKey, activityClassName, false);
    }

    /** Returns the real host style ID after verifying its fixed Window profile. */
    public static int resolveActivityTheme(
            String themeKey, String activityClassName, boolean translucent) {
        validateThemeKey(themeKey);
        return requireCatalog().resolve(
                themeKey,
                Objects.requireNonNull(activityClassName, "activityClassName"),
                translucent);
    }

    /** Prevents plugin code from applying an arbitrary style outside the immutable host table. */
    public static void requireRegisteredStyle(int styleResId) {
        requireRegisteredStyle(styleResId, false);
    }

    /** Prevents a delegate from changing the fixed Window profile selected before launch. */
    public static void requireRegisteredStyle(int styleResId, boolean translucent) {
        ResolvedCatalog current = requireCatalog();
        Boolean registeredProfile = current.registeredStyleProfiles.get(Integer.valueOf(styleResId));
        if (registeredProfile == null) {
            throw new IllegalArgumentException(
                    "Plugin Activity may only apply a host-registered theme; unknown style ID: 0x"
                            + Integer.toHexString(styleResId));
        }
        // The ID table is immutable, but a qualified style body can change with Configuration.
        // Recheck the fixed container profile before each actual application of the theme.
        boolean currentProfile = resolveWindowProfile(
                current.application,
                "<registered:0x" + Integer.toHexString(styleResId) + '>',
                styleResId);
        if (registeredProfile.booleanValue() != currentProfile) {
            throw new IllegalArgumentException(
                    "Host plugin Activity style 0x" + Integer.toHexString(styleResId)
                            + " changed Window profile across configurations; initialized="
                            + profileName(registeredProfile.booleanValue()) + ", current="
                            + profileName(currentProfile));
        }
        requireProfile("<registered:0x" + Integer.toHexString(styleResId) + '>',
                currentProfile, translucent);
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
        ResolvedStyle defaultTheme = config.defaultActivityTheme == null
                ? null : resolveReference(
                        application, "<host-default>", config.defaultActivityTheme);
        Map<String, ResolvedStyle> themes = new LinkedHashMap<String, ResolvedStyle>();
        for (Map.Entry<String, PluginHostConfig.ThemeReference> entry
                : config.activityThemes.entrySet()) {
            themes.put(entry.getKey(),
                    resolveReference(application, entry.getKey(), entry.getValue()));
        }
        return new ResolvedCatalog(application, config, defaultTheme, themes);
    }

    private static ResolvedStyle resolveReference(
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
        return new ResolvedStyle(
                styleResId, resolveWindowProfile(application, themeKey, styleResId));
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
    private static boolean resolveWindowProfile(
            Application application, String themeKey, int styleResId) {
        ContextThemeWrapper themed = new ContextThemeWrapper(application, styleResId);
        TypedArray values = themed.obtainStyledAttributes(STRUCTURAL_ATTRIBUTES);
        try {
            boolean translucent = values.getBoolean(0, false);
            for (int index = 1; index < STRUCTURAL_ATTRIBUTES.length; index++) {
                boolean expected = COMMON_NO_ACTION_BAR_VALUES[index - 1];
                // Android treats an absent Window boolean as false. Native framework themes such
                // as Theme.Translucent.NoTitleBar.Fullscreen therefore omit false-valued flags.
                boolean actual = values.getBoolean(index, false);
                if (actual != expected) {
                    throw new IllegalArgumentException(
                            "Plugin Activity themeKey " + themeKey + " has unsupported "
                                    + STRUCTURAL_ATTRIBUTE_NAMES[index] + '=' + actual
                                    + "; all plugin container profiles require " + expected);
                }
            }
            return translucent;
        } finally {
            values.recycle();
        }
    }

    private static void requireProfile(
            String themeKey, boolean actualTranslucent, boolean expectedTranslucent) {
        if (actualTranslucent != expectedTranslucent) {
            throw new IllegalArgumentException(
                    "Plugin Activity themeKey " + themeKey + " uses "
                            + profileName(actualTranslucent) + " Window structure, but the Activity "
                            + "declares " + profileName(expectedTranslucent));
        }
    }

    private static String profileName(boolean translucent) {
        return translucent ? "TRANSLUCENT" : "OPAQUE";
    }

    private static final class ResolvedStyle {
        final int resourceId;
        final boolean translucent;

        ResolvedStyle(int resourceId, boolean translucent) {
            this.resourceId = resourceId;
            this.translucent = translucent;
        }
    }

    private static final class ResolvedCatalog {
        final Application application;
        final PluginHostConfig config;
        final ResolvedStyle defaultTheme;
        final Map<String, ResolvedStyle> activityThemes;
        final Map<Integer, Boolean> registeredStyleProfiles;

        ResolvedCatalog(
                Application application,
                PluginHostConfig config,
                ResolvedStyle defaultTheme,
                Map<String, ResolvedStyle> activityThemes) {
            this.application = Objects.requireNonNull(application, "application");
            this.config = Objects.requireNonNull(config, "config");
            this.defaultTheme = defaultTheme;
            this.activityThemes = Collections.unmodifiableMap(
                    new LinkedHashMap<String, ResolvedStyle>(activityThemes));
            Map<Integer, Boolean> styleProfiles = new LinkedHashMap<Integer, Boolean>();
            if (defaultTheme != null) {
                styleProfiles.put(
                        Integer.valueOf(defaultTheme.resourceId),
                        Boolean.valueOf(defaultTheme.translucent));
            }
            for (ResolvedStyle style : activityThemes.values()) {
                styleProfiles.put(
                        Integer.valueOf(style.resourceId), Boolean.valueOf(style.translucent));
            }
            this.registeredStyleProfiles = Collections.unmodifiableMap(styleProfiles);
        }

        int resolve(String themeKey, String activityClassName, boolean translucent) {
            ResolvedStyle style = themeKey.isEmpty() ? defaultTheme : activityThemes.get(themeKey);
            if (style == null) {
                String requested = themeKey.isEmpty() ? "<host-default>" : themeKey;
                throw new IllegalStateException(
                        "Host has not registered plugin Activity themeKey " + requested
                                + " required by " + activityClassName);
            }
            requireProfile(themeKey.isEmpty() ? "<host-default>" : themeKey,
                    style.translucent, translucent);
            return style.resourceId;
        }
    }
}
