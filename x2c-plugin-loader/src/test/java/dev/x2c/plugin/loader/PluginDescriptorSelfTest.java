package dev.x2c.plugin.loader;

import java.io.ByteArrayInputStream;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.Arrays;

public final class PluginDescriptorSelfTest {
    private static final String DIGEST =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    private static final String DEPENDENCY_DIGEST =
            "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789";

    public static void main(String[] args) throws Exception {
        PluginDescriptor descriptor = new PluginDescriptor(
                "dev.x2c.sample", 7L, "7.0.0", 1, DEPENDENCY_DIGEST, DIGEST);
        String expected = "x2c-plugin-v2\ndev.x2c.sample\n7\n7.0.0\n1\n"
                + DEPENDENCY_DIGEST + '\n' + DIGEST + '\n';
        require(Arrays.equals(expected.getBytes("UTF-8"), descriptor.signedBytes()),
                "Canonical signed metadata changed");
        PluginDescriptor parsed = PluginDescriptor.parse(
                new ByteArrayInputStream(descriptor.signedBytes()));
        require(parsed.pluginId.equals(descriptor.pluginId)
                        && parsed.versionCode == descriptor.versionCode
                        && parsed.versionName.equals(descriptor.versionName)
                        && parsed.runtimeAbiVersion == descriptor.runtimeAbiVersion
                        && parsed.dependencyClosureSha256.equals(
                                descriptor.dependencyClosureSha256)
                        && parsed.sha256.equals(descriptor.sha256),
                "Descriptor did not round trip");

        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        Signature signer = Signature.getInstance(PluginDescriptor.SIGNATURE_ALGORITHM);
        signer.initSign(keyPair.getPrivate());
        signer.update(descriptor.signedBytes());
        byte[] signature = signer.sign();
        PluginSignatureVerifier.verify(descriptor, signature, keyPair.getPublic());

        PluginDescriptor tampered = new PluginDescriptor(
                "dev.x2c.sample", 8L, "8.0.0", 2, DEPENDENCY_DIGEST, DIGEST);
        try {
            PluginSignatureVerifier.verify(tampered, signature, keyPair.getPublic());
            throw new AssertionError("Tampered metadata was accepted");
        } catch (GeneralSecurityException expectedFailure) {
            // Expected: pluginId, version and digest are all publisher-authenticated.
        }
        System.out.println("PluginDescriptorSelfTest: passed");
    }
    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
