package com.boris.fundingarbitrage.exchange.impl.whitebit.privaterest;

import com.boris.fundingarbitrage.exchange.ExchangeCredentials;
import com.boris.fundingarbitrage.util.cryptography.Signers;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;

public final class WhitebitSigner {
	private WhitebitSigner() {}

	public static SimpleHttpRequest signRequest(SimpleHttpRequest request, ExchangeCredentials credentials) {
		if (Objects.equals(request.getMethod(), "GET")) return request; // only POST requests require signing
		
		String body = request.getBodyText();
		if (body == null) {
			throw new IllegalStateException("Whitebit request body is required for signing");
		}
		String payload = Base64.getEncoder().encodeToString(body.getBytes(StandardCharsets.UTF_8));
		String signature = Signers.signHmacSha512Hex(payload, credentials.apiSecret());

		request.setHeader("X-TXC-APIKEY", credentials.apiKey());
		request.setHeader("X-TXC-PAYLOAD", payload);
		request.setHeader("X-TXC-SIGNATURE", signature);
		request.setHeader("Content-Type", "application/json");
		return request;
	}
}
