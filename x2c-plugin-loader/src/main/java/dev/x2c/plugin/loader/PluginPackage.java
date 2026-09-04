package dev.x2c.plugin.loader;

import java.io.File;
import java.util.Objects;

/** A verified, immutable server-delivered DEX payload ready for DexClassLoader. */
public final class PluginPackage {
    public final String pluginId;
    public final long versionCode;
    public final String versionName;
    public final int runtimeAbiVersion;
    public final String dependencyClosureSha256;
    public final String sha256;
    public final File dexFile;

    PluginPackage(PluginDescriptor descriptor, File dexFile) {
        Objects.requireNonNull(descriptor, "descriptor");
        this.pluginId = descriptor.pluginId;
        this.versionCode = descriptor.versionCode;
        this.versionName = descriptor.versionName;
        this.runtimeAbiVersion = descriptor.runtimeAbiVersion;
        this.dependencyClosureSha256 = descriptor.dependencyClosureSha256;
        this.sha256 = descriptor.sha256;
        this.dexFile = Objects.requireNonNull(dexFile, "dexFile");
    }

}
