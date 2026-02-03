package com.boris.fundingarbitrage.exchange.impl.bitget;

import com.boris.fundingarbitrage.util.logger.Logger;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class BitgetSignatures {
	private BitgetSignatures() {}

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
