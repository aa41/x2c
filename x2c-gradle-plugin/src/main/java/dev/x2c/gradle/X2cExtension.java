package dev.x2c.gradle;

import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;

public abstract class X2cExtension {
    /** Defaults to true for a configured module; no settings or explicit false uses Resources. */
    public abstract Property<Boolean> getX2cEnable();

    /** Lower-case DSL spelling supported alongside x2cEnable. */
    public Property<Boolean> getX2cenable() { return getX2cEnable(); }

    /** True for resource-free/dynamic plugins; false for normal Android resource integration. */
    public abstract Property<Boolean> getPluginMode();

    /** Stable install/catalog identity. Required when the component transform plugin is applied. */
    public abstract Property<String> getPluginId();

    /** Package used by generated source. Defaults to the Android namespace plus ".x2c". */
    public abstract Property<String> getGeneratedPackage();

    /** Optional immutable CDN lock file. Required when bitmap resources are present. */
    public abstract RegularFileProperty getAssetLockFile();

    /** Optional typed constructor and setter contract for custom Views used by layout XML. */
    public abstract RegularFileProperty getCustomViewsFile();

    /** Minimum Android API used by the explicit DEX JAR task. */
    public abstract Property<Integer> getMinApi();
}
