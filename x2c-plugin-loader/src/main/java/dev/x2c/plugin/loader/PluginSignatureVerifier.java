package dev.x2c.plugin.loader;

import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.security.Signature;
import java.util.Objects;

/** Pure-Java publisher signature verifier, separated from Android installation I/O for testing. */
public final class PluginSignatureVerifier {
    private PluginSignatureVerifier() {}

    public static void verify(
            PluginDescriptor descriptor, byte[] signature, PublicKey publisherKey)
            throws GeneralSecurityException {
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(signature, "signature");
        Objects.requireNonNull(publisherKey, "publisherKey");
        Signature verifier = Signature.getInstance(PluginDescriptor.SIGNATURE_ALGORITHM);
        verifier.initVerify(publisherKey);
        verifier.update(descriptor.signedBytes());
        if (!verifier.verify(signature)) {
            throw new GeneralSecurityException("Plugin publisher signature is invalid");
        }
    }
}
