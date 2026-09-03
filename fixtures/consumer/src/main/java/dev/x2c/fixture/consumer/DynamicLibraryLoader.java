package dev.x2c.fixture.consumer;

import android.content.Context;
import dev.x2c.runtime.PluginClassLoader;
import dev.x2c.runtime.X2C;
import dev.x2c.runtime.X2cResourceProvider;
import dev.x2c.runtime.X2cResources;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Installs the signed APK asset as a private DEX JAR and owns one independent DexClassLoader. */
public final class DynamicLibraryLoader {
    public static final String PLUGIN_ACTIVITY_CLASS = "dev.x2c.fixture.producer.DemoActivity";
    public static final String SECONDARY_ACTIVITY_CLASS =
            "dev.x2c.fixture.secondary.SecondaryActivity";
    private static final String ENTRY_CLASS = "dev.x2c.fixture.producer.FixtureLibrary";
    private static final String PAYLOAD_ASSET = "x2c-demo/codegen-dex.jar";
    private static final String DIGEST_ASSET = "x2c-demo/codegen-dex.sha256";
    private static final String SECONDARY_ENTRY_CLASS =
            "dev.x2c.fixture.secondary.SecondaryLibrary";
    private static final String SECONDARY_PAYLOAD_ASSET =
            "x2c-demo-secondary/codegen-dex.jar";
    private static final String SECONDARY_DIGEST_ASSET =
            "x2c-demo-secondary/codegen-dex.sha256";

    private static volatile PluginClassLoader classLoader;
    private static volatile PluginClassLoader secondaryClassLoader;
    private static volatile File installedPayload;
    private static volatile File installedSecondaryPayload;
    private static volatile String installedDigest;
    private static volatile String installedSecondaryDigest;

    private DynamicLibraryLoader() {}

    public static synchronized void install(Context context) throws Exception {
        if (classLoader != null) {
            return;
        }
        Context appContext = context.getApplicationContext();
        if (appContext == null) {
            appContext = context;
        }
        X2C.init(appContext);
        InstalledPlugin primary = preparePayload(
                appContext, "x2c-demo", PAYLOAD_ASSET, DIGEST_ASSET);
        PluginClassLoader candidate = primary.loader;
        verifySharedRuntime(candidate);

        Class<?> entry = candidate.loadClass(ENTRY_CLASS);
        String declaredActivity = invokeString(entry, "activityClassName");
        if (!PLUGIN_ACTIVITY_CLASS.equals(declaredActivity)) {
            throw new IllegalStateException("Unexpected plugin Activity contract: " + declaredActivity);
        }
        candidate.loadClass(PLUGIN_ACTIVITY_CLASS);
        X2cResources resources = X2C.loadModule(appContext, entry);
        X2cResources resourcesFromAnchor = X2C.resources(entry);
        if (!resources.hasResource("content", "layout")
                || !resourcesFromAnchor.hasResource("framework_matrix", "layout")) {
            throw new IllegalStateException("Plugin module was not registered against its ClassLoader");
        }

        // Load a second physical DEX JAR through a second ClassLoader after the first module has
        // registered. Both resource handles must remain independently addressable.
        InstalledPlugin secondary = preparePayload(
                appContext,
                "x2c-demo-secondary",
                SECONDARY_PAYLOAD_ASSET,
                SECONDARY_DIGEST_ASSET);
        verifySharedRuntime(secondary.loader);
        Class<?> secondaryEntry = secondary.loader.loadClass(SECONDARY_ENTRY_CLASS);
        secondary.loader.loadClass(SECONDARY_ACTIVITY_CLASS);
        X2cResources secondaryResources = X2C.loadModule(appContext, secondaryEntry);
        X2cResources secondaryFromAnchor = X2C.resources(secondaryEntry);
        if (!secondaryResources.hasResource("secondary_screen", "layout")
                || !secondaryFromAnchor.hasResource("secondary_activity", "layout")
                || !"Secondary X2C JAR".equals(invokeString(secondaryEntry, "libraryName"))) {
            throw new IllegalStateException("Secondary X2C module registration failed");
        }
        if (!resources.hasResource("content", "layout")
                || resources.findIdentifier("secondary_screen", "layout") != 0
                || secondaryResources.findIdentifier("content", "layout") != 0) {
            throw new IllegalStateException("Loading the second JAR replaced the first X2C module");
        }

        installedPayload = primary.payload;
        installedDigest = primary.digest;
        installedSecondaryPayload = secondary.payload;
        installedSecondaryDigest = secondary.digest;
        classLoader = candidate;
        secondaryClassLoader = secondary.loader;
    }

    public static ClassLoader requireClassLoader() throws ClassNotFoundException {
        PluginClassLoader current = classLoader;
        if (current == null) {
            throw new ClassNotFoundException("X2C DEX payload was not installed by HostApplication");
        }
        return current;
    }

    public static ClassLoader requireSecondaryClassLoader() throws ClassNotFoundException {
        PluginClassLoader current = secondaryClassLoader;
        if (current == null) {
            throw new ClassNotFoundException("Secondary X2C DEX payload was not installed by HostApplication");
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

    public static String secondaryLibraryName() throws Exception {
        PluginClassLoader current = secondaryClassLoader;
        if (current == null) {
            throw new ClassNotFoundException("Secondary X2C DEX payload is not installed");
        }
        return invokeString(current.loadClass(SECONDARY_ENTRY_CLASS), "libraryName");
    }

    public static String installationSummary() {
        PluginClassLoader current = classLoader;
        File payload = installedPayload;
        PluginClassLoader secondary = secondaryClassLoader;
        File secondaryPayload = installedSecondaryPayload;
        if (current == null || payload == null || secondary == null || secondaryPayload == null) {
            return "not installed";
        }
        return "primary.loader=" + current.getClass().getName()
                + "\nprimary.payload=" + payload.getAbsolutePath()
                + "\nprimary.sha256=" + installedDigest
                + "\nsecondary.loader=" + secondary.getClass().getName()
                + "\nsecondary.payload=" + secondaryPayload.getAbsolutePath()
                + "\nsecondary.sha256=" + installedSecondaryDigest;
    }

    private static String invokeEntry(String methodName) throws Exception {
        Class<?> entry = requireClassLoader().loadClass(ENTRY_CLASS);
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
                || candidate.loadClass(DynamicLibraryLoader.class.getName()) != DynamicLibraryLoader.class) {
            throw new IllegalStateException("Plugin ClassLoader did not fall back to host classes");
        }
    }

    private static InstalledPlugin preparePayload(
            Context context, String directoryName, String payloadAsset, String digestAsset)
            throws IOException {
        String expectedDigest = readExpectedDigest(context, digestAsset);
        File pluginDirectory = new File(context.getFilesDir(), directoryName);
        if (!pluginDirectory.isDirectory() && !pluginDirectory.mkdirs()) {
            throw new IOException("Cannot create private plugin directory: " + pluginDirectory);
        }
        File payload = new File(pluginDirectory, "codegen-dex.jar");
        if (!payload.isFile() || !expectedDigest.equals(sha256(payload))) {
            copyVerifiedPayload(context, payload, expectedDigest, payloadAsset);
        }
        // Android 14+ requires dynamically loaded code files to be read-only before class loading.
        if (payload.canWrite() && !payload.setReadOnly()) {
            throw new IOException("Cannot mark dynamic DEX JAR read-only: " + payload);
        }
        File optimizedDirectory = new File(context.getCodeCacheDir(), directoryName + "-optimized");
        if (!optimizedDirectory.isDirectory() && !optimizedDirectory.mkdirs()) {
            throw new IOException("Cannot create private DEX optimization directory: " + optimizedDirectory);
        }
        PluginClassLoader loader = X2C.createPluginClassLoader(
                payload.getAbsolutePath(), optimizedDirectory.getAbsolutePath(), null);
        return new InstalledPlugin(loader, payload, expectedDigest);
    }

    private static String readExpectedDigest(Context context, String digestAsset) throws IOException {
        try (InputStream input = context.getAssets().open(digestAsset)) {
            ByteArrayOutputStream output = new ByteArrayOutputStream(80);
            copy(input, output);
            String digest = new String(output.toByteArray(), StandardCharsets.US_ASCII).trim();
            if (!digest.matches("[0-9a-f]{64}")) {
                throw new IOException("Invalid embedded plugin SHA-256: " + digest);
            }
            return digest;
        }
    }

    private static void copyVerifiedPayload(
            Context context, File target, String expectedDigest, String payloadAsset)
            throws IOException {
        File temporary = new File(target.getParentFile(), target.getName() + ".tmp");
        if (temporary.exists() && !temporary.delete()) {
            throw new IOException("Cannot remove stale temporary payload: " + temporary);
        }
        try (InputStream input = context.getAssets().open(payloadAsset);
             FileOutputStream output = new FileOutputStream(temporary)) {
            copy(input, output);
            output.getFD().sync();
        }
        String actualDigest = sha256(temporary);
        if (!expectedDigest.equals(actualDigest)) {
            temporary.delete();
            throw new IOException("Embedded DEX JAR digest mismatch: " + actualDigest);
        }
        if (target.exists() && !target.delete()) {
            temporary.delete();
            throw new IOException("Cannot replace installed payload: " + target);
        }
        if (!temporary.renameTo(target)) {
            temporary.delete();
            throw new IOException("Cannot atomically install DEX JAR: " + target);
        }
    }

    private static String sha256(File file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = new FileInputStream(file)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    digest.update(buffer, 0, read);
                }
            }
            return hex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    private static void copy(InputStream input, java.io.OutputStream output) throws IOException {
        byte[] buffer = new byte[8192];
        int read;
        while ((read = input.read(buffer)) >= 0) {
            output.write(buffer, 0, read);
        }
    }

    private static String hex(byte[] bytes) {
        char[] alphabet = "0123456789abcdef".toCharArray();
        char[] result = new char[bytes.length * 2];
        for (int index = 0; index < bytes.length; index++) {
            int value = bytes[index] & 0xff;
            result[index * 2] = alphabet[value >>> 4];
            result[index * 2 + 1] = alphabet[value & 0x0f];
        }
        return new String(result);
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
