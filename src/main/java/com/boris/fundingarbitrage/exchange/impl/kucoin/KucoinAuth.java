package com.boris.fundingarbitrage.exchange.impl.kucoin;

import com.boris.fundingarbitrage.exchange.ExchangeCredentials;
import com.boris.fundingarbitrage.util.cryptography.Signers;
import com.boris.fundingarbitrage.util.logger.Logger;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;

import java.net.URI;

public final class KucoinAuth {
	private static final String KEY_VERSION_ENV = "KUCOIN_API_KEY_VERSION";
	private static final String DEFAULT_KEY_VERSION = "2";

	private KucoinAuth() {}

	public static SimpleHttpRequest sign(SimpleHttpRequest request, ExchangeCredentials credentials) {
		try {
			String timestamp = String.valueOf(System.currentTimeMillis());
			String method = request.getMethod().toUpperCase();
			URI uri = request.getUri();
			String query = uri.getRawQuery();
			String requestPath = uri.getRawPath();
			if (query != null && !query.isEmpty()) {
				requestPath += "?" + query;
			}

			String body = request.getBodyText();
			if (body == null) body = "";

			String payload = timestamp + method + requestPath + body;
			String signature = Signers.signHmacSha256Base64(payload, credentials.apiSecret());

			String passphrase = credentials.passphrase();
			if (passphrase == null) {
				throw new IllegalStateException("KuCoin API passphrase is required for signed requests");
			}
			String encodedPassphrase = Signers.signHmacSha256Base64(passphrase, credentials.apiSecret());
			String keyVersion = System.getenv(KEY_VERSION_ENV);
			if (keyVersion == null || keyVersion.isEmpty()) keyVersion = DEFAULT_KEY_VERSION;

			request.setHeader("KC-API-KEY", credentials.apiKey());
			request.setHeader("KC-API-SIGN", signature);
			request.setHeader("KC-API-TIMESTAMP", timestamp);
			request.setHeader("KC-API-PASSPHRASE", encodedPassphrase);
			request.setHeader("KC-API-KEY-VERSION", keyVersion);
			request.setHeader("Content-Type", "application/json");

			return request;
		} catch (Exception e) {
			Logger.error("Failed to sign KuCoin request: " + e.getMessage());
			throw new RuntimeException("Failed to sign KuCoin request", e);
		}
	}
}
