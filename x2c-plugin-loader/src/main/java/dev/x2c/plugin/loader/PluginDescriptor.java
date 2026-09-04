package dev.x2c.plugin.loader;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** Signed immutable metadata delivered alongside one plugin DEX JAR. */
public final class PluginDescriptor {
    public static final String FORMAT = "x2c-plugin-v2";
    public static final String SIGNATURE_ALGORITHM = "SHA256withRSA";
    public static final String EMPTY_DEPENDENCY_CLOSURE_SHA256 =
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

    public final String pluginId;
    public final long versionCode;
    public final String versionName;
    public final int runtimeAbiVersion;
    public final String dependencyClosureSha256;
    public final String sha256;

    /** Convenience constructor for a payload with no packaged runtime dependencies. */
    public PluginDescriptor(
            String pluginId, long versionCode, String versionName, String sha256) {
        this(pluginId, versionCode, versionName, 1, EMPTY_DEPENDENCY_CLOSURE_SHA256, sha256);
    }

    public PluginDescriptor(
            String pluginId,
            long versionCode,
            String versionName,
            int runtimeAbiVersion,
            String dependencyClosureSha256,
            String sha256) {
        this.pluginId = requireSegment(pluginId, "pluginId");
        if (versionCode < 1L) throw new IllegalArgumentException("versionCode must be positive");
        this.versionCode = versionCode;
        this.versionName = requireSegment(versionName, "versionName");
        if (runtimeAbiVersion < 1) {
            throw new IllegalArgumentException("runtimeAbiVersion must be positive");
        }
        this.runtimeAbiVersion = runtimeAbiVersion;
        this.dependencyClosureSha256 = requireDigest(
                dependencyClosureSha256, "dependencyClosureSha256");
        this.sha256 = requireDigest(sha256, "sha256");
    }

    /** Canonical UTF-8 bytes signed by the publisher; the digest binds these bytes to the payload. */
    public byte[] signedBytes() {
        String canonical = FORMAT + '\n'
                + pluginId + '\n'
                + versionCode + '\n'
                + versionName + '\n'
                + runtimeAbiVersion + '\n'
                + dependencyClosureSha256 + '\n'
                + sha256 + '\n';
        return canonical.getBytes(StandardCharsets.UTF_8);
    }

    /** Parses the strict seven-line descriptor format used by the server and demo assets. */
    public static PluginDescriptor parse(InputStream input) throws IOException {
        Objects.requireNonNull(input, "input");
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(input, StandardCharsets.UTF_8));
        String format = reader.readLine();
        String pluginId = reader.readLine();
        String versionCode = reader.readLine();
        String versionName = reader.readLine();
        String runtimeAbiVersion = reader.readLine();
        String dependencyClosureSha256 = reader.readLine();
        String sha256 = reader.readLine();
        if (!FORMAT.equals(format) || pluginId == null || versionCode == null
                || versionName == null || runtimeAbiVersion == null
                || dependencyClosureSha256 == null || sha256 == null
                || reader.readLine() != null) {
            throw new IOException("Invalid X2C plugin descriptor format");
        }
        try {
            return new PluginDescriptor(
                    pluginId, Long.parseLong(versionCode), versionName,
                    Integer.parseInt(runtimeAbiVersion), dependencyClosureSha256, sha256);
        } catch (IllegalArgumentException error) {
            throw new IOException("Invalid X2C plugin descriptor values", error);
        }
    }

    String installationDirectoryName() {
        return versionCode + "-" + versionName;
    }

    private static String requireSegment(String value, String label) {
        Objects.requireNonNull(value, label);
        if (!value.matches("[A-Za-z0-9._-]+")) {
            throw new IllegalArgumentException(label + " contains unsafe characters: " + value);
        }
        return value;
    }

    private static String requireDigest(String value, String label) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(
                    label + " must be 64 lowercase hex characters");
        }
        return value;
    }
}
