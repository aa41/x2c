package dev.x2c.runtime;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Immutable host-owned configuration for plugin Activity themes. */
public final class PluginHostConfig {
    final ThemeReference defaultActivityTheme;
    final Map<String, ThemeReference> activityThemes;

    private PluginHostConfig(
            ThemeReference defaultActivityTheme,
            Map<String, ThemeReference> activityThemes) {
        this.defaultActivityTheme = defaultActivityTheme;
        this.activityThemes = Collections.unmodifiableMap(
                new LinkedHashMap<String, ThemeReference>(activityThemes));
    }

    public static Builder builder() {
        return new Builder();
    }

    boolean sameAs(PluginHostConfig other) {
        return other != null
                && Objects.equals(defaultActivityTheme, other.defaultActivityTheme)
                && activityThemes.equals(other.activityThemes);
    }

    /** Builder used during host Application initialization; the built configuration is immutable. */
    public static final class Builder {
        private ThemeReference defaultActivityTheme;
        private final Map<String, ThemeReference> activityThemes =
                new LinkedHashMap<String, ThemeReference>();

        public Builder defaultActivityTheme(int styleResId) {
            return setDefault(ThemeReference.forId(styleResId));
        }

        /** Resolves a style entry from the host package once during {@link X2C#init}. */
        public Builder defaultActivityTheme(String hostStyleResourceName) {
            return setDefault(ThemeReference.forName(hostStyleResourceName));
        }

        public Builder activityTheme(String themeKey, int styleResId) {
            return putTheme(themeKey, ThemeReference.forId(styleResId));
        }

        /** Resolves a style entry from the host package once during {@link X2C#init}. */
        public Builder activityTheme(String themeKey, String hostStyleResourceName) {
            return putTheme(themeKey, ThemeReference.forName(hostStyleResourceName));
        }

        public PluginHostConfig build() {
            return new PluginHostConfig(defaultActivityTheme, activityThemes);
        }

        private Builder setDefault(ThemeReference reference) {
            if (defaultActivityTheme != null) {
                throw new IllegalStateException("defaultActivityTheme is already configured");
            }
            defaultActivityTheme = reference;
            return this;
        }

        private Builder putTheme(String themeKey, ThemeReference reference) {
            String key = requireThemeKey(themeKey);
            if (activityThemes.put(key, reference) != null) {
                throw new IllegalArgumentException("Duplicate plugin Activity themeKey: " + key);
            }
            return this;
        }
    }

    static String requireThemeKey(String value) {
        Objects.requireNonNull(value, "themeKey");
        if (!value.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")) {
            throw new IllegalArgumentException(
                    "themeKey must contain 1-128 letters, digits, '.', '_' or '-' and start "
                            + "with a letter or digit: " + value);
        }
        return value;
    }

    static final class ThemeReference {
        final int resourceId;
        final String resourceName;

        private ThemeReference(int resourceId, String resourceName) {
            this.resourceId = resourceId;
            this.resourceName = resourceName;
        }

        static ThemeReference forId(int resourceId) {
            if (resourceId == 0) {
                throw new IllegalArgumentException("Activity theme resource ID must not be 0");
            }
            return new ThemeReference(resourceId, null);
        }

        static ThemeReference forName(String resourceName) {
            Objects.requireNonNull(resourceName, "hostStyleResourceName");
            String normalized = resourceName.trim();
            if (normalized.startsWith("@")) {
                if (!normalized.startsWith("@style/")) {
                    throw invalidStyleName(resourceName);
                }
                normalized = normalized.substring(7);
            } else if (normalized.startsWith("style/")) {
                normalized = normalized.substring(6);
            }
            if (normalized.indexOf(':') >= 0 || normalized.indexOf('/') >= 0
                    || !normalized.matches("[A-Za-z][A-Za-z0-9_.]{0,126}")) {
                throw invalidStyleName(resourceName);
            }
            return new ThemeReference(0, normalized);
        }

        private static IllegalArgumentException invalidStyleName(String resourceName) {
            return new IllegalArgumentException(
                    "Host style name must be a local style entry such as "
                            + "HostPluginDefault or @style/HostPluginDefault: "
                            + resourceName);
        }

        @Override public boolean equals(Object value) {
            if (this == value) return true;
            if (!(value instanceof ThemeReference)) return false;
            ThemeReference other = (ThemeReference) value;
            return resourceId == other.resourceId
                    && Objects.equals(resourceName, other.resourceName);
        }

        @Override public int hashCode() {
            return 31 * resourceId + (resourceName == null ? 0 : resourceName.hashCode());
        }
    }
}
