package com.boris.fundingarbitrage.exchange.impl.whitebit.privatews;

import com.boris.fundingarbitrage.ObjectMapperSingleton;
import com.boris.fundingarbitrage.exchange.ExchangeContext;
import com.boris.fundingarbitrage.exchange.ExchangeCredentials;
import com.boris.fundingarbitrage.exchange.impl.whitebit.privaterest.PrivateEndpoints;
import com.boris.fundingarbitrage.exchange.impl.whitebit.ws.WsRequest;
import com.boris.fundingarbitrage.exchange.privatews.PrivateWsClient;
import com.boris.fundingarbitrage.util.https.PrettyHttpClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;

import java.net.URI;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class WhitebitPrivateWsClient extends PrivateWsClient {
	private static final URI endpoint = URI.create("wss://api.whitebit.com/ws");
	private static final WhitebitPrivateMessageHandler messageHandler = new WhitebitPrivateMessageHandler();
	private final CompletableFuture<String> tokenFuture;

	public WhitebitPrivateWsClient(ExchangeContext context, CompletableFuture<String> tokenFuture) {
		super(context, endpoint, messageHandler);
		this.tokenFuture = tokenFuture;
	}

	@Override
	protected String getAuthenticationFrame() {
		List<Object> params = List.of(tokenFuture.join(), "public");
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
