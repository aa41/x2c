package dev.x2c.fixture.consumer;

import android.content.Context;
import android.util.Base64;
import dev.x2c.fixture.businessbase.BusinessBaseActivity;
import dev.x2c.plugin.api.PluginComponentRegistry;
import dev.x2c.plugin.base.BasePluginActivity;
import dev.x2c.plugin.loader.PluginDescriptor;
import dev.x2c.plugin.loader.PluginPackage;
import dev.x2c.plugin.loader.SignedPluginInstaller;
import dev.x2c.plugin.runtime.PluginActivity;
import dev.x2c.plugin.runtime.PluginComponentManager;
import dev.x2c.runtime.PluginClassLoader;
import dev.x2c.runtime.X2C;
import dev.x2c.runtime.X2cResourceProvider;
import dev.x2c.runtime.X2cResources;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;

/** Installs two signed APK assets and owns one independent DexClassLoader per showcase. */
public final class DynamicLibraryLoader {
    public static final String LAYOUT_PLUGIN_ID = "dev.x2c.fixture.layout-showcase";
    public static final String COMPONENT_PLUGIN_ID = "dev.x2c.fixture.component-showcase";
    public static final String LAYOUT_ACTIVITY_CLASS = "dev.x2c.fixture.producer.DemoActivity";
    public static final String COMPONENT_ACTIVITY_CLASS =
            "dev.x2c.fixture.secondary.ComponentShowcaseActivity";
    private static final String LAYOUT_ENTRY_CLASS = "dev.x2c.fixture.producer.FixtureLibrary";
    private static final String LAYOUT_PAYLOAD_ASSET = "x2c-layout-showcase/codegen-dex.jar";
    private static final String LAYOUT_DESCRIPTOR_ASSET =
            "x2c-layout-showcase/codegen-dex.descriptor";
    private static final String LAYOUT_SIGNATURE_ASSET = "x2c-layout-showcase/codegen-dex.sig";
    private static final String COMPONENT_ENTRY_CLASS =
            "dev.x2c.fixture.secondary.ComponentShowcaseLibrary";
    private static final String COMPONENT_PAYLOAD_ASSET =
            "x2c-component-showcase/codegen-dex.jar";
    private static final String COMPONENT_DESCRIPTOR_ASSET =
            "x2c-component-showcase/codegen-dex.descriptor";
    private static final String COMPONENT_SIGNATURE_ASSET =
            "x2c-component-showcase/codegen-dex.sig";
    // Test-only public key matching the fixture build key. Production pins its own publisher key.
    private static final String TEST_PUBLISHER_KEY =
            "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAtcn/eRfArbBdgtZTDv62baewjQ4G2CPD5kddvWx5ILcePvYDOLtqVG7izThmhLmDeBLZTLtWzGQvgzKCqj8hXFQpO02BmxMeEsjHA2eoQDsOCkHyOo714XDDaxsP4esAYxZ3+4YDBs/r2UVyftdnpenf2MFay2gcOc2JHzBjluazwWYQhE7xlkbX7HdDjfjDiIoP2gelfI7/91FLNvMowaCIoLaJrgkJkKorldReB/uONuFofbqPrq5dRBlh5Ut5Q2KwDottb/pfRr8L+NXXmjz2d3Uq+Hl0HGQli4N+O3QvJyGa1vtsgJmO+vKAqkylII7YmbK+L2fE0sgwTR0oiQIDAQAB";

    private static volatile PluginClassLoader layoutClassLoader;
    private static volatile PluginClassLoader componentClassLoader;
    private static volatile File installedLayoutPayload;
    private static volatile File installedComponentPayload;
    private static volatile String installedLayoutDigest;
    private static volatile String installedComponentDigest;

    private DynamicLibraryLoader() {}

    public static synchronized void install(Context context) throws Exception {
        if (layoutClassLoader != null) {
            return;
        }
        Context appContext = context.getApplicationContext();
        if (appContext == null) {
            appContext = context;
        }
        InstalledPlugin layout = preparePayload(
                appContext, LAYOUT_PLUGIN_ID, LAYOUT_PAYLOAD_ASSET,
                LAYOUT_DESCRIPTOR_ASSET, LAYOUT_SIGNATURE_ASSET);
        PluginClassLoader layoutLoader = layout.loader;
        verifySharedRuntime(layoutLoader);

        Class<?> entry = layoutLoader.loadClass(LAYOUT_ENTRY_CLASS);
        String declaredActivity = invokeString(entry, "activityClassName");
        if (!LAYOUT_ACTIVITY_CLASS.equals(declaredActivity)) {
            throw new IllegalStateException("Unexpected plugin Activity contract: " + declaredActivity);
        }
        PluginComponentRegistry layoutRegistry =
                PluginComponentManager.loadAndInstall(appContext, layoutLoader);
        if (!LAYOUT_PLUGIN_ID.equals(layoutRegistry.pluginId())
                || !layoutRegistry.contains(LAYOUT_ACTIVITY_CLASS)) {
            throw new IllegalStateException("Layout showcase component registry is invalid");
        }
        X2cResources resources = X2C.loadModule(appContext, entry);
        X2cResources resourcesFromAnchor = X2C.resources(entry);
        if (!resources.hasResource("content", "layout")
                || !resourcesFromAnchor.hasResource("framework_matrix", "layout")) {
            throw new IllegalStateException("Plugin module was not registered against its ClassLoader");
        }

        // Load a second physical DEX JAR through a second ClassLoader after the first module has
        // registered. Both resource handles must remain independently addressable.
        InstalledPlugin component = preparePayload(
                appContext,
                COMPONENT_PLUGIN_ID,
                COMPONENT_PAYLOAD_ASSET,
                COMPONENT_DESCRIPTOR_ASSET,
                COMPONENT_SIGNATURE_ASSET);
        verifySharedRuntime(component.loader);
        Class<?> componentEntry = component.loader.loadClass(COMPONENT_ENTRY_CLASS);
        PluginComponentRegistry componentRegistry =
                PluginComponentManager.loadAndInstall(appContext, component.loader);
        if (!COMPONENT_PLUGIN_ID.equals(componentRegistry.pluginId())
                || !componentRegistry.contains(COMPONENT_ACTIVITY_CLASS)
                || !componentRegistry.contains(
                        "dev.x2c.fixture.secondary.StandardActivity")
                || !componentRegistry.contains(
                        "dev.x2c.fixture.secondary.SingleTopActivity")
                || !componentRegistry.contains(
                        "dev.x2c.fixture.secondary.SingleTaskActivity")
                || !componentRegistry.contains(
                        "dev.x2c.fixture.secondary.SingleInstanceActivity")
                || !componentRegistry.containsService(
                        "dev.x2c.fixture.secondary.ComponentProbeService")
                || !componentRegistry.containsReceiver(
                        "dev.x2c.fixture.secondary.ComponentProbeReceiver")
                || !componentRegistry.containsProvider(
                        "dev.x2c.fixture.secondary.ComponentProbeProvider")) {
            throw new IllegalStateException("Component showcase registry is incomplete");
        }
        X2cResources componentResources = X2C.loadModule(appContext, componentEntry);
        X2cResources componentFromAnchor = X2C.resources(componentEntry);
        if (!componentResources.hasResource("component_showcase_activity", "layout")
                || !componentFromAnchor.hasResource("launch_mode_activity", "layout")
                || !"Component Showcase Plugin".equals(
                        invokeString(componentEntry, "libraryName"))) {
            throw new IllegalStateException("Component showcase module registration failed");
        }
        if (!resources.hasResource("content", "layout")
                || resources.findIdentifier("component_showcase_activity", "layout") != 0
                || componentResources.findIdentifier("content", "layout") != 0) {
            throw new IllegalStateException("Loading the second JAR replaced the first X2C module");
        }
        verifyBusinessBaseClosure(layoutLoader, component.loader);

        installedLayoutPayload = layout.payload;
        installedLayoutDigest = layout.digest;
        installedComponentPayload = component.payload;
        installedComponentDigest = component.digest;
        layoutClassLoader = layoutLoader;
        componentClassLoader = component.loader;
    }

    public static ClassLoader requireLayoutClassLoader() throws ClassNotFoundException {
        PluginClassLoader current = layoutClassLoader;
        if (current == null) {
            throw new ClassNotFoundException("X2C DEX payload was not installed by HostApplication");
        }
        return current;
    }

    public static ClassLoader requireComponentClassLoader() throws ClassNotFoundException {
        PluginClassLoader current = componentClassLoader;
        if (current == null) {
            throw new ClassNotFoundException(
                    "Component showcase DEX payload was not installed by HostApplication");
        }
        return current;
    }

    public static String libraryName() throws Exception {
        return invokeEntry("libraryName");
    }

    public static String runJvmSelfTests() throws Exception {
        return invokeEntry("runJvmSelfTests");
    }

    public static String runLoginSelfTests() throws Exception {
        return invokeEntry("runLoginSelfTests");
    }

    public static String componentLibraryName() throws Exception {
        PluginClassLoader current = componentClassLoader;
        if (current == null) {
            throw new ClassNotFoundException("Component showcase DEX payload is not installed");
        }
        return invokeString(current.loadClass(COMPONENT_ENTRY_CLASS), "libraryName");
    }

    public static String installationSummary() {
        PluginClassLoader layout = layoutClassLoader;
        File layoutPayload = installedLayoutPayload;
        PluginClassLoader component = componentClassLoader;
        File componentPayload = installedComponentPayload;
        if (layout == null || layoutPayload == null
                || component == null || componentPayload == null) {
            return "not installed";
        }
        return "layout.loader=" + layout.getClass().getName()
                + "\nlayout.payload=" + layoutPayload.getAbsolutePath()
                + "\nlayout.sha256=" + installedLayoutDigest
                + "\ncomponent.loader=" + component.getClass().getName()
                + "\ncomponent.payload=" + componentPayload.getAbsolutePath()
                + "\ncomponent.sha256=" + installedComponentDigest;
    }

    private static String invokeEntry(String methodName) throws Exception {
        Class<?> entry = requireLayoutClassLoader().loadClass(LAYOUT_ENTRY_CLASS);
        return invokeString(entry, methodName);
    }

    private static String invokeString(Class<?> owner, String methodName) throws Exception {
        Method method = owner.getMethod(methodName);
        try {
            return (String) method.invoke(null);
        } catch (InvocationTargetException error) {
            Throwable cause = error.getCause();
            if (cause instanceof Exception exception) {
                throw exception;
            }
            if (cause instanceof Error fatal) {
                throw fatal;
            }
            throw error;
        }
    }

    private static void verifySharedRuntime(PluginClassLoader candidate)
            throws ClassNotFoundException {
        if (candidate.loadClass(X2C.class.getName()) != X2C.class
                || candidate.loadClass(X2cResources.class.getName()) != X2cResources.class
                || candidate.loadClass(X2cResourceProvider.class.getName()) != X2cResourceProvider.class
                || candidate.loadClass(PluginActivity.class.getName()) != PluginActivity.class
                || candidate.loadClass(BasePluginActivity.class.getName()) != BasePluginActivity.class
                || candidate.loadClass(PluginComponentRegistry.class.getName())
                        != PluginComponentRegistry.class
                || candidate.loadClass(DynamicLibraryLoader.class.getName()) != DynamicLibraryLoader.class) {
            throw new IllegalStateException("Plugin ClassLoader did not fall back to host classes");
        }
    }

    private static void verifyBusinessBaseClosure(
            PluginClassLoader layoutLoader, PluginClassLoader componentLoader)
            throws ClassNotFoundException {
        Class<?> hostBase = BusinessBaseActivity.class;
        Class<?> layoutBase = layoutLoader.loadClass(hostBase.getName());
        Class<?> componentBase = componentLoader.loadClass(hostBase.getName());
        if (hostBase.getSuperclass() != android.app.Activity.class
                || layoutBase == hostBase
                || componentBase == hostBase
                || layoutBase == componentBase
                || layoutBase.getClassLoader() != layoutLoader
                || componentBase.getClassLoader() != componentLoader
                || layoutBase.getSuperclass() != PluginActivity.class
                || componentBase.getSuperclass() != PluginActivity.class) {
            throw new IllegalStateException(
                    "Shared-source BusinessBaseActivity ownership/transform boundary is invalid");
        }
    }

    private static InstalledPlugin preparePayload(
            Context context,
            String expectedPluginId,
            String payloadAsset,
            String descriptorAsset,
            String signatureAsset) throws Exception {
        PluginDescriptor descriptor;
        byte[] signature;
        try (InputStream descriptorInput = context.getAssets().open(descriptorAsset);
             InputStream signatureInput = context.getAssets().open(signatureAsset)) {
            descriptor = PluginDescriptor.parse(descriptorInput);
            signature = readFully(signatureInput);
        }
        if (!expectedPluginId.equals(descriptor.pluginId)) {
            throw new IOException("Unexpected signed plugin ID: " + descriptor.pluginId);
        }
        PluginPackage installed;
        try (InputStream payloadInput = context.getAssets().open(payloadAsset)) {
            installed = SignedPluginInstaller.install(
                    context, descriptor, payloadInput, signature, publisherKey());
        }
        PluginClassLoader loader = SignedPluginInstaller.createClassLoader(context, installed);
        return new InstalledPlugin(loader, installed.dexFile, installed.sha256);
    }

    private static PublicKey publisherKey() throws Exception {
        return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(
                Base64.decode(TEST_PUBLISHER_KEY, Base64.DEFAULT)));
    }

    private static byte[] readFully(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) >= 0) {
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }

    private static final class InstalledPlugin {
        final PluginClassLoader loader;
        final File payload;
        final String digest;

        InstalledPlugin(PluginClassLoader loader, File payload, String digest) {
            this.loader = loader;
            this.payload = payload;
            this.digest = digest;
        }
    }
}
