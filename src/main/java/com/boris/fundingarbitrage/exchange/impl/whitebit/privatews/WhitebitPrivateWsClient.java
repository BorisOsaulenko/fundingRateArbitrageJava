package com.boris.fundingarbitrage.exchange.impl.whitebit.privatews;

import com.boris.fundingarbitrage.ObjectMapperSingleton;
import com.boris.fundingarbitrage.exchange.ExchangeContext;
import com.boris.fundingarbitrage.exchange.ExchangeCredentials;
import com.boris.fundingarbitrage.exchange.impl.whitebit.privaterest.PrivateEndpoints;
import com.boris.fundingarbitrage.exchange.impl.whitebit.privaterest.WhitebitSigner;
import com.boris.fundingarbitrage.exchange.impl.whitebit.ws.WsRequest;
import com.boris.fundingarbitrage.exchange.privatews.PrivateWsClient;
import com.boris.fundingarbitrage.util.https.PrettyHttpClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;

import java.net.URI;
import java.util.List;

public class WhitebitPrivateWsClient extends PrivateWsClient {
	private static final URI endpoint = URI.create("wss://api.whitebit.com/ws");
	private static final WhitebitPrivateMessageHandler messageHandler = new WhitebitPrivateMessageHandler();
	private final ExchangeCredentials credentials;
	private final ObjectMapper mapper = ObjectMapperSingleton.getInstance();

	public WhitebitPrivateWsClient(ExchangeContext context) {
		super(context, endpoint, messageHandler);
		this.credentials = context.credentials;
	}

	private String fetchWebsocketToken() {
		SimpleHttpRequest request = PrivateEndpoints.websocketTokenRequest();
		WhitebitSigner.signRequest(request, credentials);
		String body = PrettyHttpClient.getINSTANCE().sendNoCodeCheck(request).join().getBodyText();
		try {
			JsonNode root = mapper.readTree(body);
			JsonNode token = root.get("websocket_token");
			if (token == null || token.isNull()) {
				throw new IllegalStateException("Missing websocket_token");
			}
			String tokenText = token.asText();
			if (tokenText == null || tokenText.isEmpty()) {
				throw new IllegalStateException("Missing websocket_token");
			}
			return tokenText;
		} catch (Exception e) {
			throw new RuntimeException("Failed to parse websocket token", e);
		}
	}

	@Override
	protected String getAuthenticationFrame() {
		String token = fetchWebsocketToken();
		List<Object> params = List.of(token, "public");
		return new WsRequest(System.currentTimeMillis(), "authorize", params).toJson();
	}

	@Override
	protected String getSubscribeDepositFrame() {
		List<Object> params = List.of("USDT");
		return new WsRequest(System.currentTimeMillis(), "balanceSpot_subscribe", params).toJson();
	}

	@Override
	protected String getUnsubscribeDepositFrame() {
		List<Object> params = List.of();
		return new WsRequest(System.currentTimeMillis(), "balanceSpot_unsubscribe", params).toJson();
	}

	@Override
	protected String getSubscribePartialFillsFrame() {
		List<Object> params = List.of();
		return new WsRequest(System.currentTimeMillis(), "deals_subscribe", params).toJson();
	}

	@Override
	protected String getUnsubscribePartialFillsFrame() {
		List<Object> params = List.of();
		return new WsRequest(System.currentTimeMillis(), "deals_unsubscribe", params).toJson();
	}
}
