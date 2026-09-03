package dev.x2c.runtime;

import dalvik.system.DexClassLoader;

/**
 * Plugin-first DEX loader with an explicit host fallback.
 *
 * <p>Platform and X2C runtime classes always come from the parent so a plugin cannot create a
 * second runtime singleton or shadow Android/JDK classes. Other classes are resolved from the
 * plugin first and fall back to the host application ClassLoader when absent.</p>
 */
public final class PluginClassLoader extends DexClassLoader {
    public PluginClassLoader(
            String dexPath,
            String optimizedDirectory,
            String librarySearchPath,
            ClassLoader hostClassLoader) {
        super(dexPath, optimizedDirectory, librarySearchPath, hostClassLoader);
        if (hostClassLoader == null) {
            throw new NullPointerException("hostClassLoader");
        }
    }

    @Override
    protected synchronized Class<?> loadClass(String name, boolean resolve)
            throws ClassNotFoundException {
        Class<?> loaded = findLoadedClass(name);
        if (loaded == null) {
            if (isParentFirst(name)) {
                loaded = super.loadClass(name, false);
            } else {
                try {
                    loaded = findClass(name);
                } catch (ClassNotFoundException pluginMiss) {
                    ClassLoader host = getParent();
                    if (host == null) {
                        throw pluginMiss;
                    }
                    loaded = host.loadClass(name);
                }
            }
        }
        if (resolve) {
            resolveClass(loaded);
        }
        return loaded;
    }

    private static boolean isParentFirst(String name) {
        return name.startsWith("java.")
                || name.startsWith("javax.")
                || name.startsWith("android.")
                || name.startsWith("dalvik.")
                || name.startsWith("org.xml.")
                || name.startsWith("org.w3c.")
                || name.startsWith("dev.x2c.runtime.");
    }
}
