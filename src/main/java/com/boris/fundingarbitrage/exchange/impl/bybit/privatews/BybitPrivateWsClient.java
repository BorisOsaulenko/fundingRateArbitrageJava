package com.boris.fundingarbitrage.exchange.impl.bybit.privatews;

import com.boris.fundingarbitrage.exchange.ExchangeContext;
import com.boris.fundingarbitrage.exchange.ExchangeCredentials;
import com.boris.fundingarbitrage.exchange.impl.bybit.privatews.pojos.AuthRequest;
import com.boris.fundingarbitrage.exchange.impl.bybit.privatews.pojos.WsRequest;
import com.boris.fundingarbitrage.exchange.privatews.PrivateWsClient;
import com.boris.fundingarbitrage.util.cryptography.Signers;

import java.net.URI;

public class BybitPrivateWsClient extends PrivateWsClient {
	private static final URI endpoint = URI.create("wss://stream.bybit.com/v5/private");
	private static final BybitPrivateMessageHandler messageHandler = new BybitPrivateMessageHandler();
	private final ExchangeCredentials credentials;

	public BybitPrivateWsClient(ExchangeContext context) {
		super(context, endpoint, messageHandler);
		this.credentials = context.credentials;
	}

	@Override
	protected String getAuthenticationFrame() {
		long expires = System.currentTimeMillis() + 10_000;
		String payload = "GET/realtime" + expires;
		String sign = Signers.signHmacSha256Hex(payload, credentials.apiSecret());
		Object[] args = new Object[]{credentials.apiKey(), String.valueOf(expires), sign};
		return new AuthRequest("auth", args).toJson();
	}

	private String getSubscribeFrame(String channel) {
		return new WsRequest("subscribe", new String[]{channel}).toJson();
	}

	private String getUnsubscribeFrame(String channel) {
		return new WsRequest("unsubscribe", new String[]{channel}).toJson();
	}

	@Override
	protected String getSubscribeDepositFrame() {
		return getSubscribeFrame("wallet");
	}

	@Override
	protected String getUnsubscribeDepositFrame() {
		return getUnsubscribeFrame("wallet");
	}

	@Override
	protected String getSubscribePartialFillsFrame() {
		return getSubscribeFrame("execution");
	}

	@Override
	protected String getUnsubscribePartialFillsFrame() {
		return getUnsubscribeFrame("execution");
	}
}
