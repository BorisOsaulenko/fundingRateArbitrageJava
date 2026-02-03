package com.boris.fundingarbitrage.util.cryptography;

import com.boris.fundingarbitrage.util.logger.Logger;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
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

	public static String signHmacSha256Hex(String payload, String secret) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
			byte[] signData = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
			StringBuilder sb = new StringBuilder(signData.length * 2);
			for (byte b : signData) {
				sb.append(String.format("%02x", b));
			}
			return sb.toString();
		} catch (Exception e) {
			Logger.getInstance().error("Failed to sign payload with HmacSHA256: " + e.getMessage());
			throw new RuntimeException("Failed to sign payload", e);
		}
	}

	public static String signHmacSha256Base64(String payload, String secret) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
			byte[] signData = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
			return Base64.getEncoder().encodeToString(signData);
		} catch (Exception e) {
			Logger.getInstance().error("Failed to sign payload with HmacSHA256: " + e.getMessage());
			throw new RuntimeException("Failed to sign payload", e);
		}
	}
}
