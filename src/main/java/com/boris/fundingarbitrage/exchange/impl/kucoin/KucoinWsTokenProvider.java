package com.boris.fundingarbitrage.exchange.impl.kucoin;

import com.boris.fundingarbitrage.ObjectMapperSingleton;
import com.boris.fundingarbitrage.exchange.ExchangeCredentials;
import com.boris.fundingarbitrage.util.https.PrettyHttpClient;
import com.boris.fundingarbitrage.util.logger.Logger;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;

import java.net.URI;
import java.util.UUID;

public final class KucoinWsTokenProvider {
	private static final String BASE_URL_FUTURES = "https://api-futures.kucoin.com";
	private static final ObjectMapper mapper = ObjectMapperSingleton.getInstance();

	private KucoinWsTokenProvider() {}

	public static URI getPublicEndpoint() {
		SimpleHttpRequest request = new SimpleHttpRequest(
					"POST",
					URI.create(BASE_URL_FUTURES + "/api/v1/bullet-public")
		);
		return fetchEndpoint(request, null);
	}

	public static URI getPrivateEndpoint(ExchangeCredentials credentials) {
		SimpleHttpRequest request = new SimpleHttpRequest(
					"POST",
					URI.create(BASE_URL_FUTURES + "/api/v1/bullet-private")
		);
		return fetchEndpoint(request, credentials);
	}

	private static URI fetchEndpoint(SimpleHttpRequest request, ExchangeCredentials credentials) {
		try {
			if (credentials != null) {
				request = KucoinAuth.sign(request, credentials);
			}
			String body = PrettyHttpClient.getINSTANCE().send(request).join().getBodyText();
			JsonNode root = mapper.readTree(body);
			String code = KucoinJson.requireText(root, "code");
			String msg = root.has("msg") ? root.get("msg").asText() : null;
			KucoinJson.requireCodeOk(code, msg);
			JsonNode data = KucoinJson.requireField(root, "data");
			String token = KucoinJson.requireText(data, "token");
			JsonNode servers = KucoinJson.requireArray(data, "instanceServers");
			if (servers.isEmpty()) {
				throw new IllegalStateException("KuCoin WS token response has no instance servers");
			}
			JsonNode server = servers.get(0);
			String endpoint = KucoinJson.requireText(server, "endpoint");
			String connectId = UUID.randomUUID().toString();
			return URI.create(endpoint + "?token=" + token + "&connectId=" + connectId);
		} catch (Exception ex) {
			Logger.error("Failed to get KuCoin WS token: " + ex.getMessage());
			throw new RuntimeException("Failed to get KuCoin WS token", ex);
		}
	}
}
