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
		this.credentials = context.getCredentialsOrThrow();
	}

	@Override
	protected void sendAuthenticationFrame() {
		long expires = System.currentTimeMillis() + 10_000;
		String payload = "GET/realtime" + expires;
		String sign = Signers.signHmacSha256Hex(payload, credentials.apiSecret());
		Object[] args = new Object[]{credentials.apiKey(), String.valueOf(expires), sign};
		this.prettyWsClient.sendObject(new AuthRequest("auth", args));
	}

	private void sendSubscribeFrame(String channel) {
		this.prettyWsClient.sendObject(new WsRequest("subscribe", new String[]{channel}));
	}

	private void sendUnsubscribeFrame(String channel) {
		this.prettyWsClient.sendObject(new WsRequest("unsubscribe", new String[]{channel}));
	}

	@Override
	protected void sendSubscribeDepositFrame() {
		sendSubscribeFrame("wallet");
	}

	@Override
	protected void sendUnsubscribeDepositFrame() {
		sendUnsubscribeFrame("wallet");
	}

	@Override
	protected void sendSubscribePartialFillsFrame() {
		sendSubscribeFrame("execution");
	}

	@Override
	protected void sendUnsubscribePartialFillsFrame() {
		sendUnsubscribeFrame("execution");
	}
}
