package dev.x2c.plugin.api;

import java.util.Objects;

/** Immutable generated ContentProvider metadata. */
public final class PluginProviderInfo {
    public final String className;
    public final String authority;

    public PluginProviderInfo(String className, String authority) {
        this.className = Objects.requireNonNull(className, "className");
        this.authority = Objects.requireNonNull(authority, "authority");
    }
}
