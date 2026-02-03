package com.boris.fundingarbitrage.util.cryptography;

import com.boris.fundingarbitrage.util.logger.Logger;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.Signature;
import java.util.Base64;

public class Signers {
    public static String signEd25519(String payload, PrivateKey privateKey) {
        try {
            Signature sig = Signature.getInstance("Ed25519");
            sig.initSign(privateKey);
            sig.update(payload.getBytes(StandardCharsets.US_ASCII));
            return Base64.getEncoder().encodeToString(sig.sign());
        } catch (Exception e) {
            Logger.getInstance().error(String.format("Error while signing Ed25519: %s", e.getMessage()));
            throw new RuntimeException("Failed to sign payload with Ed25519", e);
        }
    }
}
