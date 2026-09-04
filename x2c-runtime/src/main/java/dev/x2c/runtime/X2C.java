package dev.x2c.runtime;

import android.app.Activity;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;

/** Host-resident runtime shared by statically integrated and dynamically loaded X2C modules. */
public final class X2C {
    private static final String MODULE_BOOTSTRAP_SUFFIX = ".x2c.X2cModuleBootstrap";
    private static final Object LOCK = new Object();
    private static final Map<Integer, Map<String, RegisteredLayout>> LAYOUTS =
            new HashMap<Integer, Map<String, RegisteredLayout>>();
    private static final Map<String, RegisteredLayout> NAMED_LAYOUTS =
            new HashMap<String, RegisteredLayout>();
    private static final Map<String, X2cResourceProvider> RESOURCE_PROVIDERS =
            new HashMap<String, X2cResourceProvider>();
    private static final Map<ClassLoader, Set<String>> MODULES_BY_CLASS_LOADER =
            new WeakHashMap<ClassLoader, Set<String>>();
    private static final Map<Class<?>, String> MODULES_BY_ANCHOR =
            new WeakHashMap<Class<?>, String>();
    private static volatile Context applicationContext;
    private static volatile ClassLoader hostClassLoader;
    private static volatile String hostPackageName;

    private X2C() {}

    /** Initializes the host context and the ClassLoader used as the plugin fallback. */
    public static void init(Context context) {
        Objects.requireNonNull(context, "context");
        if (applicationContext != null) {
            requireInitialized(context);
            return;
        }
        Context candidate = applicationContext(context);
        String packageName = candidate.getPackageName();
        ClassLoader loader = candidate.getClassLoader();
        if (loader == null) {
            loader = X2C.class.getClassLoader();
        }
        synchronized (LOCK) {
            if (applicationContext != null) {
                requireSameHost(packageName);
                return;
            }
            hostPackageName = packageName;
            hostClassLoader = loader;
            // Publish last: readers that observe this volatile write also observe the host fields.
            applicationContext = candidate;
        }
    }

    /** Verifies that the process-wide host initialization has already completed. */
    public static void requireInitialized(Context context) {
        Objects.requireNonNull(context, "context");
        Context initializedContext = applicationContext;
        if (initializedContext == null) {
            throw new IllegalStateException(
                    "X2C.init(context) must be called once from Application before using X2C");
        }
        Context candidate = applicationContext(context);
        if (candidate == initializedContext) {
            return;
        }
        requireSameHost(candidate.getPackageName());
    }

    /** Invokes a generated X2cModule through the already initialized host loader. */
    public static void init(Context context, String moduleClassName) {
        init(context, moduleClassName, requireHostClassLoader());
    }

    /** Invokes a generated X2cModule through the plugin/host loader. */
    public static void init(Context context, String moduleClassName, ClassLoader pluginClassLoader) {
        requireInitialized(context);
        Objects.requireNonNull(moduleClassName, "moduleClassName");
        try {
            Class<?> module = loadClass(moduleClassName, pluginClassLoader);
            Method init = module.getMethod("init", Context.class);
            init.invoke(null, context);
        } catch (NoSuchMethodException error) {
            throw new IllegalStateException("Generated X2C module has no init(Context): " + moduleClassName, error);
        } catch (IllegalAccessException error) {
            throw new IllegalStateException("Cannot access generated X2C module: " + moduleClassName, error);
        } catch (InvocationTargetException error) {
            Throwable cause = error.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw new IllegalStateException("Generated X2C module initialization failed: " + moduleClassName, cause);
        } catch (ClassNotFoundException error) {
            throw new IllegalStateException("Generated X2C module not found: " + moduleClassName, error);
        }
    }

    /** Initializes a generated module reflectively and returns its stable runtime-only handle. */
    public static X2cResources loadModule(Context context, String moduleClassName) {
        init(context, moduleClassName);
        return resources(moduleName(moduleClassName));
    }

    /** Initializes through the exact plugin loader and returns a handle that exposes no generated type. */
    public static X2cResources loadModule(
            Context context, String moduleClassName, ClassLoader pluginClassLoader) {
        init(context, moduleClassName, pluginClassLoader);
        return resources(moduleName(moduleClassName));
    }

    /**
     * Discovers and initializes the generated module that owns {@code anchorClass}.
     *
     * <p>The Gradle plugin generates a bootstrap below the Android library namespace. This keeps
     * generated package names out of business code and works for both a shared application
     * ClassLoader and an isolated plugin ClassLoader.</p>
     */
    public static X2cResources loadModule(Context context, Class<?> anchorClass) {
        return resources(context, anchorClass);
    }

    public static boolean isInitialized() {
        return applicationContext != null;
    }

    public static Context requireContext() {
        Context context = applicationContext;
        if (context == null) {
            throw new IllegalStateException("X2C.init(context) must be called before using the runtime");
        }
        return context;
    }

    public static ClassLoader requireHostClassLoader() {
        ClassLoader loader = hostClassLoader;
        if (loader == null) {
            throw new IllegalStateException("X2C.init(context) must be called before creating a plugin ClassLoader");
        }
        return loader;
    }

    /** Creates the default plugin-first loader whose final lookup step is the host ClassLoader. */
    public static PluginClassLoader createPluginClassLoader(
            String dexPath, String optimizedDirectory, String librarySearchPath) {
        return new PluginClassLoader(
                dexPath, optimizedDirectory, librarySearchPath, requireHostClassLoader());
    }

    /** Loads from the supplied plugin loader and falls back to the initialized host loader. */
    public static Class<?> loadClass(String className, ClassLoader pluginClassLoader)
            throws ClassNotFoundException {
        Objects.requireNonNull(className, "className");
        if (pluginClassLoader != null) {
            try {
                return pluginClassLoader.loadClass(className);
            } catch (ClassNotFoundException ignored) {
                // Explicit host fallback below also supports third-party child-only loaders.
            }
        }
        return requireHostClassLoader().loadClass(className);
    }

    /** Called by generated modules after constructing their module-scoped resource provider. */
    public static void registerResourceProvider(
            String moduleName, X2cResourceProvider provider) {
        Objects.requireNonNull(moduleName, "moduleName");
        Objects.requireNonNull(provider, "provider");
        ClassLoader moduleLoader = provider.getClass().getClassLoader();
        synchronized (LOCK) {
            X2cResourceProvider previous = RESOURCE_PROVIDERS.get(moduleName);
            if (previous != null && previous != provider) {
                throw new IllegalStateException(
                        "Duplicate X2C module name: " + moduleName
                                + ". Every loaded JAR must use a unique x2c.generatedPackage.");
            }
            RESOURCE_PROVIDERS.put(moduleName, provider);
            if (moduleLoader != null) {
                Set<String> modules = MODULES_BY_CLASS_LOADER.get(moduleLoader);
                if (modules == null) {
                    modules = new HashSet<String>();
                    MODULES_BY_CLASS_LOADER.put(moduleLoader, modules);
                }
                modules.add(moduleName);
            }
        }
    }

    public static X2cResources resources(String moduleName) {
        Objects.requireNonNull(moduleName, "moduleName");
        X2cResourceProvider provider;
        synchronized (LOCK) {
            provider = RESOURCE_PROVIDERS.get(moduleName);
        }
        if (provider == null) {
            throw new IllegalStateException(
                    "X2C module is not initialized: " + moduleName
                            + ". Call X2C.loadModule(context, moduleClassName, pluginClassLoader) first.");
        }
        return new X2cResources(moduleName, provider);
    }

    /**
     * Resolves a statically integrated module, initializing its generated registry on first use.
     *
     * <p>This is the normal AAR integration entry point. The generated module is loaded from the
     * application's regular ClassLoader; callers do not need to reference generated Java types.</p>
     */
    public static X2cResources resources(Context context, String moduleName) {
        ensureModuleInitialized(context, moduleName);
        return resources(moduleName);
    }

    /** Automatically discovers the module owning an anchor class using the initialized Context. */
    public static X2cResources resources(Class<?> anchorClass) {
        return resources(requireContext(), anchorClass);
    }

    /**
     * Automatically initializes and resolves the generated module that owns {@code anchorClass}.
     * This is the preferred entry point for normal AAR business code.
     */
    public static X2cResources resources(Context context, Class<?> anchorClass) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(anchorClass, "anchorClass");
        requireInitialized(context);
        String moduleName;
        synchronized (LOCK) {
            moduleName = MODULES_BY_ANCHOR.get(anchorClass);
        }
        if (moduleName == null) {
            moduleName = discoverModule(context, anchorClass);
            synchronized (LOCK) {
                MODULES_BY_ANCHOR.put(anchorClass, moduleName);
            }
        }
        return resources(moduleName);
    }

    /** Called by generated X2cModule classes. IDs are isolated by module name. */
    public static void registerLayout(String moduleName, int layoutId, LayoutFactory factory) {
        registerLayout(moduleName, null, layoutId, factory);
    }

    /** Called by new generated modules to support business APIs without direct R2 references. */
    public static void registerLayout(
            String moduleName, String layoutName, int layoutId, LayoutFactory factory) {
        Objects.requireNonNull(moduleName, "moduleName");
        Objects.requireNonNull(factory, "factory");
        if (layoutId == 0) {
            throw new IllegalArgumentException("layoutId must not be 0 for module " + moduleName);
        }
        synchronized (LOCK) {
            Map<String, RegisteredLayout> modules = LAYOUTS.get(layoutId);
            if (modules != null && modules.containsKey(moduleName)) {
                throw new IllegalStateException(String.format(
                        "Duplicate X2C layout 0x%08X in module %s", layoutId, moduleName));
            }
            String key = layoutName == null ? null : resourceKey(moduleName, layoutName);
            if (key != null && NAMED_LAYOUTS.containsKey(key)) {
                throw new IllegalStateException(
                        "Duplicate X2C layout @" + moduleName + ":layout/" + layoutName);
            }
            if (modules == null) {
                modules = new HashMap<String, RegisteredLayout>();
                LAYOUTS.put(layoutId, modules);
            }
            RegisteredLayout registered = new RegisteredLayout(moduleName, layoutName, factory);
            modules.put(moduleName, registered);
            if (layoutName != null) {
                NAMED_LAYOUTS.put(key, registered);
            }
        }
    }

    public static void unregisterModule(String moduleName) {
        Objects.requireNonNull(moduleName, "moduleName");
        synchronized (LOCK) {
            Iterator<Map.Entry<Integer, Map<String, RegisteredLayout>>> iterator =
                    LAYOUTS.entrySet().iterator();
            while (iterator.hasNext()) {
                Map<String, RegisteredLayout> modules = iterator.next().getValue();
                modules.remove(moduleName);
                if (modules.isEmpty()) {
                    iterator.remove();
                }
            }
            Iterator<Map.Entry<String, RegisteredLayout>> names = NAMED_LAYOUTS.entrySet().iterator();
            while (names.hasNext()) {
                if (names.next().getValue().moduleName.equals(moduleName)) {
                    names.remove();
                }
            }
            RESOURCE_PROVIDERS.remove(moduleName);
            Iterator<Map.Entry<ClassLoader, Set<String>>> loaders =
                    MODULES_BY_CLASS_LOADER.entrySet().iterator();
            while (loaders.hasNext()) {
                Set<String> modules = loaders.next().getValue();
                modules.remove(moduleName);
                if (modules.isEmpty()) {
                    loaders.remove();
                }
            }
            Iterator<Map.Entry<Class<?>, String>> anchors =
                    MODULES_BY_ANCHOR.entrySet().iterator();
            while (anchors.hasNext()) {
                if (moduleName.equals(anchors.next().getValue())) {
                    anchors.remove();
                }
            }
        }
        X2cImages.unregisterModule(moduleName);
    }

    public static void setContentView(Activity activity, int layoutId) {
        Objects.requireNonNull(activity, "activity");
        activity.setContentView(getView(activity, layoutId));
    }

    public static void setContentView(Activity activity, String moduleName, String layoutName) {
        Objects.requireNonNull(activity, "activity");
        activity.setContentView(getView(activity, moduleName, layoutName));
    }

    public static void setContentView(Activity activity, String moduleName, int layoutId) {
        Objects.requireNonNull(activity, "activity");
        activity.setContentView(getView(activity, moduleName, layoutId));
    }

    public static View inflate(Context context, int layoutId, ViewGroup parent) {
        return inflate(context, layoutId, parent, parent != null);
    }

    public static View inflate(Context context, int layoutId, ViewGroup parent, boolean attachToRoot) {
        Objects.requireNonNull(context, "context");
        if (attachToRoot && parent == null) {
            throw new IllegalArgumentException("attachToRoot requires a non-null parent");
        }
        View view = getView(context, layoutId);
        return attach(view, parent, attachToRoot);
    }

    public static View inflate(
            Context context,
            String moduleName,
            String layoutName,
            ViewGroup parent,
            boolean attachToRoot) {
        Objects.requireNonNull(context, "context");
        if (attachToRoot && parent == null) {
            throw new IllegalArgumentException("attachToRoot requires a non-null parent");
        }
        View view = getView(context, moduleName, layoutName);
        return attach(view, parent, attachToRoot);
    }

    public static View inflate(
            Context context, String moduleName, String layoutName, ViewGroup parent) {
        return inflate(context, moduleName, layoutName, parent, parent != null);
    }

    public static View inflate(
            Context context,
            String moduleName,
            int layoutId,
            ViewGroup parent,
            boolean attachToRoot) {
        Objects.requireNonNull(context, "context");
        if (attachToRoot && parent == null) {
            throw new IllegalArgumentException("attachToRoot requires a non-null parent");
        }
        return attach(getView(context, moduleName, layoutId), parent, attachToRoot);
    }

    public static View inflate(
            Context context, String moduleName, int layoutId, ViewGroup parent) {
        return inflate(context, moduleName, layoutId, parent, parent != null);
    }

    private static View attach(View view, ViewGroup parent, boolean attachToRoot) {
        if (parent != null && attachToRoot) {
            ViewGroup.LayoutParams params = view.getLayoutParams();
            int width = params == null ? ViewGroup.LayoutParams.WRAP_CONTENT : params.width;
            int height = params == null ? ViewGroup.LayoutParams.WRAP_CONTENT : params.height;
            parent.addView(view, width, height);
        }
        return view;
    }

    public static View inflate(LayoutInflater inflater, int layoutId, ViewGroup parent) {
        Objects.requireNonNull(inflater, "inflater");
        return inflate(inflater.getContext(), layoutId, parent, parent != null);
    }

    public static View inflate(
            LayoutInflater inflater, int layoutId, ViewGroup parent, boolean attachToRoot) {
        Objects.requireNonNull(inflater, "inflater");
        return inflate(inflater.getContext(), layoutId, parent, attachToRoot);
    }

    public static View getView(Context context, int layoutId) {
        Objects.requireNonNull(context, "context");
        Map<String, RegisteredLayout> modules;
        synchronized (LOCK) {
            Map<String, RegisteredLayout> registered = LAYOUTS.get(layoutId);
            modules = registered == null
                    ? null : new HashMap<String, RegisteredLayout>(registered);
        }
        if (modules == null || modules.isEmpty()) {
            throw new IllegalArgumentException(String.format(
                    "Unknown generated layout id: 0x%08X. Initialize its generated X2cModule first.",
                    layoutId));
        }
        if (modules.size() != 1) {
            throw new IllegalArgumentException(String.format(
                    "Ambiguous generated layout id 0x%08X belongs to modules %s. "
                            + "Use the module-name X2C overload.",
                    layoutId, modules.keySet()));
        }
        return modules.values().iterator().next().factory.create(context);
    }

    public static View getView(Context context, String moduleName, String layoutName) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(moduleName, "moduleName");
        Objects.requireNonNull(layoutName, "layoutName");
        ensureModuleInitialized(context, moduleName);
        RegisteredLayout registered;
        synchronized (LOCK) {
            registered = NAMED_LAYOUTS.get(resourceKey(moduleName, layoutName));
        }
        if (registered == null) {
            throw new IllegalArgumentException(
                    "Unknown generated layout @" + moduleName + ":layout/" + layoutName
                            + ". Initialize its generated X2cModule first.");
        }
        return registered.factory.create(context);
    }

    public static View getView(Context context, String moduleName, int layoutId) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(moduleName, "moduleName");
        ensureModuleInitialized(context, moduleName);
        RegisteredLayout registered;
        synchronized (LOCK) {
            Map<String, RegisteredLayout> modules = LAYOUTS.get(layoutId);
            registered = modules == null ? null : modules.get(moduleName);
        }
        if (registered == null) {
            throw new IllegalArgumentException(String.format(
                    "Unknown generated layout 0x%08X in module %s", layoutId, moduleName));
        }
        return registered.factory.create(context);
    }

    private static void ensureModuleInitialized(Context context, String moduleName) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(moduleName, "moduleName");
        requireInitialized(context);
        synchronized (LOCK) {
            if (RESOURCE_PROVIDERS.containsKey(moduleName)) {
                return;
            }
        }
        ClassLoader loader = context.getClassLoader();
        if (loader == null) {
            loader = X2C.class.getClassLoader();
        }
        init(context, moduleName + ".X2cModule", loader);
    }

    private static Context applicationContext(Context context) {
        Context application = context.getApplicationContext();
        return application == null ? context : application;
    }

    private static void requireSameHost(String packageName) {
        String initializedPackage = hostPackageName;
        if (initializedPackage != null && !initializedPackage.equals(packageName)) {
            throw new IllegalStateException(
                    "X2C is already initialized for " + initializedPackage
                            + "; cannot bind " + packageName);
        }
    }

    private static String discoverModule(Context context, Class<?> anchorClass) {
        ClassLoader anchorLoader = anchorClass.getClassLoader();
        if (anchorLoader == null) {
            anchorLoader = requireHostClassLoader();
        }
        String packageName = packageName(anchorClass);
        while (!packageName.isEmpty()) {
            String bootstrapName = packageName + MODULE_BOOTSTRAP_SUFFIX;
            Class<?> bootstrap;
            try {
                bootstrap = anchorLoader.loadClass(bootstrapName);
            } catch (ClassNotFoundException ignored) {
                bootstrap = null;
            }
            // A plugin-first loader may fall back to a host class with the same package prefix.
            // It must never claim that host bootstrap as the plugin's module.
            if (bootstrap != null && bootstrap.getClassLoader() == anchorLoader) {
                return initializeBootstrap(context, anchorClass, bootstrap);
            }
            int separator = packageName.lastIndexOf('.');
            packageName = separator < 0 ? "" : packageName.substring(0, separator);
        }

        throw new IllegalStateException(
                "Cannot discover an X2C module for " + anchorClass.getName()
                        + " from ClassLoader " + anchorLoader
                        + ". Rebuild the library with the current X2C Gradle plugin.");
    }

    private static String initializeBootstrap(
            Context context, Class<?> anchorClass, Class<?> bootstrap) {
        try {
            Method initialize = bootstrap.getMethod("initialize", Context.class);
            Object result = initialize.invoke(null, context);
            if (!(result instanceof String) || ((String) result).trim().isEmpty()) {
                throw new IllegalStateException(
                        "Generated X2C bootstrap returned an invalid module name: "
                                + bootstrap.getName());
            }
            String moduleName = (String) result;
            ClassLoader loader = bootstrap.getClassLoader();
            Set<String> registered = modulesFor(loader);
            if (!registered.contains(moduleName)) {
                throw new IllegalStateException(
                        "Generated X2C bootstrap " + bootstrap.getName() + " returned "
                                + moduleName + " but registered " + registered);
            }
            return moduleName;
        } catch (NoSuchMethodException error) {
            throw new IllegalStateException(
                    "Generated X2C bootstrap has no initialize(Context): "
                            + bootstrap.getName(), error);
        } catch (IllegalAccessException error) {
            throw new IllegalStateException(
                    "Cannot access generated X2C bootstrap: " + bootstrap.getName(), error);
        } catch (InvocationTargetException error) {
            Throwable cause = error.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw new IllegalStateException(
                    "Generated X2C bootstrap initialization failed for "
                            + anchorClass.getName(), cause);
        }
    }

    private static Set<String> modulesFor(ClassLoader loader) {
        synchronized (LOCK) {
            Set<String> registered = MODULES_BY_CLASS_LOADER.get(loader);
            return registered == null
                    ? new HashSet<String>() : new HashSet<String>(registered);
        }
    }

    private static String packageName(Class<?> type) {
        String name = type.getName();
        int separator = name.lastIndexOf('.');
        if (separator <= 0) {
            throw new IllegalArgumentException(
                    "X2C anchor class must belong to a named package: " + name);
        }
        return name.substring(0, separator);
    }

    private static String moduleName(String moduleClassName) {
        Objects.requireNonNull(moduleClassName, "moduleClassName");
        String suffix = ".X2cModule";
        if (!moduleClassName.endsWith(suffix) || moduleClassName.length() == suffix.length()) {
            throw new IllegalArgumentException(
                    "Generated module class must end with .X2cModule: " + moduleClassName);
        }
        return moduleClassName.substring(0, moduleClassName.length() - suffix.length());
    }

    private static String resourceKey(String moduleName, String resourceName) {
        return moduleName + '\u0000' + resourceName;
    }

    private static final class RegisteredLayout {
        final String moduleName;
        final String layoutName;
        final LayoutFactory factory;

        RegisteredLayout(String moduleName, String layoutName, LayoutFactory factory) {
            this.moduleName = moduleName;
            this.layoutName = layoutName;
            this.factory = factory;
        }
    }
}
