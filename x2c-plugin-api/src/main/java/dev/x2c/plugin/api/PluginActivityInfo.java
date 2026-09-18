package dev.x2c.plugin.api;

import java.util.Objects;

/** Immutable generated metadata for one transformed plugin Activity. */
public final class PluginActivityInfo {
    public final String className;
    public final PluginLaunchMode launchMode;
    public final String themeKey;

    public PluginActivityInfo(
            String className, PluginLaunchMode launchMode, String themeKey) {
        this.className = Objects.requireNonNull(className, "className");
        this.launchMode = Objects.requireNonNull(launchMode, "launchMode");
        this.themeKey = Objects.requireNonNull(themeKey, "themeKey");
    }
}
