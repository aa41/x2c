package dev.x2c.plugin.loader;

import android.content.Context;
import android.content.SharedPreferences;
import dev.x2c.plugin.api.PluginActivityRegistry;
import dev.x2c.runtime.PluginClassLoader;
import dev.x2c.runtime.X2C;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.util.Locale;
import java.util.Objects;

/** Installs server-delivered code only after digest and publisher-signature verification. */
public final class SignedPluginInstaller {
    private static final String VERSION_STORE = "dev.x2c.plugin.installed_versions";
    private static final Object INSTALL_LOCK = new Object();

    private SignedPluginInstaller() {}

    public static PluginPackage install(
            Context context,
            PluginDescriptor descriptor,
            InputStream dexPayload,
            byte[] signature,
            PublicKey publisherKey) throws IOException, GeneralSecurityException {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(dexPayload, "dexPayload");
        Objects.requireNonNull(signature, "signature");
        Objects.requireNonNull(publisherKey, "publisherKey");

        Context app = context.getApplicationContext() == null
                ? context : context.getApplicationContext();
        synchronized (INSTALL_LOCK) {
            PluginSignatureVerifier.verify(descriptor, signature, publisherKey);
            if (descriptor.runtimeAbiVersion != PluginActivityRegistry.CURRENT_RUNTIME_ABI) {
                throw new GeneralSecurityException(
                        "Plugin runtime ABI mismatch: host="
                                + PluginActivityRegistry.CURRENT_RUNTIME_ABI
                                + ", plugin=" + descriptor.runtimeAbiVersion);
            }
            SharedPreferences versions = app.getSharedPreferences(VERSION_STORE, Context.MODE_PRIVATE);
            checkForDowngrade(versions, descriptor);
            PluginPackage installed = installVerifiedPayload(app, descriptor, dexPayload);
            if (!versions.edit()
                    .putLong(versionCodeKey(descriptor.pluginId), descriptor.versionCode)
                    .putString(digestKey(descriptor.pluginId), descriptor.sha256)
                    .commit()) {
                installed.dexFile.delete();
                throw new IOException("Cannot persist accepted plugin version: " + descriptor.pluginId);
            }
            return installed;
        }
    }

    private static PluginPackage installVerifiedPayload(
            Context app, PluginDescriptor descriptor, InputStream dexPayload)
            throws IOException, GeneralSecurityException {
        File directory = new File(app.getFilesDir(),
                "x2c-plugins/" + descriptor.pluginId + "/"
                        + descriptor.installationDirectoryName());
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IOException("Cannot create plugin directory: " + directory);
        }
        File temporary = new File(directory, "payload.dex.jar.tmp");
        File installed = new File(directory, "payload.dex.jar");
        if (temporary.exists() && !temporary.delete()) {
            throw new IOException("Cannot remove stale plugin payload: " + temporary);
        }
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = new BufferedInputStream(dexPayload);
             FileOutputStream file = openReadOnlyForWrite(temporary);
             BufferedOutputStream output = new BufferedOutputStream(file)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) >= 0) {
                digest.update(buffer, 0, count);
                output.write(buffer, 0, count);
            }
            output.flush();
            file.getFD().sync();
        } catch (IOException | RuntimeException | Error error) {
            temporary.delete();
            throw error;
        }
        String actual = hex(digest.digest());
        if (!descriptor.sha256.equals(actual)) {
            temporary.delete();
            throw new GeneralSecurityException("Plugin payload SHA-256 is invalid");
        }
        if (installed.exists() && !installed.delete()) {
            temporary.delete();
            throw new IOException("Cannot replace installed plugin: " + installed);
        }
        if (!temporary.renameTo(installed)) {
            temporary.delete();
            throw new IOException("Cannot atomically install plugin: " + installed);
        }
        return new PluginPackage(descriptor, installed);
    }

    public static PluginClassLoader createClassLoader(Context context, PluginPackage plugin) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(plugin, "plugin");
        File optimized = new File(context.getCodeCacheDir(),
                "x2c-plugins/" + plugin.pluginId + "/" + plugin.versionCode);
        if (!optimized.isDirectory() && !optimized.mkdirs()) {
            throw new IllegalStateException("Cannot create plugin code-cache directory: " + optimized);
        }
        return X2C.createPluginClassLoader(
                plugin.dexFile.getAbsolutePath(), optimized.getAbsolutePath(), null);
    }

    private static FileOutputStream openReadOnlyForWrite(File file) throws IOException {
        FileOutputStream output = new FileOutputStream(file);
        if (!file.setReadOnly()) {
            try {
                output.close();
            } finally {
                file.delete();
            }
            throw new IOException("Cannot mark dynamic-code file read-only before writing: " + file);
        }
        return output;
    }

    private static void checkForDowngrade(
            SharedPreferences versions, PluginDescriptor descriptor)
            throws GeneralSecurityException {
        long accepted = versions.getLong(versionCodeKey(descriptor.pluginId), -1L);
        if (descriptor.versionCode < accepted) {
            throw new GeneralSecurityException(
                    "Plugin downgrade rejected for " + descriptor.pluginId + ": "
                            + descriptor.versionCode + " < " + accepted);
        }
        if (descriptor.versionCode == accepted) {
            String acceptedDigest = versions.getString(digestKey(descriptor.pluginId), null);
            if (acceptedDigest != null && !acceptedDigest.equals(descriptor.sha256)) {
                throw new GeneralSecurityException(
                        "Plugin versionCode was reused with different content: "
                                + descriptor.pluginId + '@' + descriptor.versionCode);
            }
        }
    }

    private static String versionCodeKey(String pluginId) {
        return pluginId + ".versionCode";
    }

    private static String digestKey(String pluginId) {
        return pluginId + ".sha256";
    }

    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            result.append(String.format(Locale.ROOT, "%02x", value & 0xff));
        }
        return result.toString();
    }
}
