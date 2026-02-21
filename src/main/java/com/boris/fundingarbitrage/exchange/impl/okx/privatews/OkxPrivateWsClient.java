package com.boris.fundingarbitrage.exchange.impl.okx.privatews;

import com.boris.fundingarbitrage.exchange.ExchangeContext;
import com.boris.fundingarbitrage.exchange.ExchangeCredentials;
import com.boris.fundingarbitrage.exchange.privatews.PrivateWsClient;
import com.boris.fundingarbitrage.util.cryptography.Signers;

import java.net.URI;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

public class OkxPrivateWsClient extends PrivateWsClient {
	private static final URI endpoint = URI.create("wss://ws.okx.com:8443/ws/v5/private");
	private static final OkxPrivateMessageHandler messageHandler = new OkxPrivateMessageHandler();
	private final ExchangeCredentials credentials;

	public OkxPrivateWsClient(ExchangeContext context) {
		super(context, endpoint, messageHandler);
		this.credentials = context.credentials;
	}

	@Override
	protected String getAuthenticationFrame() {
		String timestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now());
		String payload = timestamp + "GET" + "/users/self/verify";
		String sign = Signers.signHmacSha256Base64(payload, credentials.apiSecret());
		LoginRequest.Arg[] args = new LoginRequest.Arg[]{
						new LoginRequest.Arg(credentials.apiKey(), credentials.passphrase(), timestamp, sign)
		};
		return new LoginRequest("login", args).toJson();
	}

	private String subscribe(String channel, String instType) {
		WsRequest.Arg[] args = new WsRequest.Arg[]{new WsRequest.Arg(channel, instType)};
		return new WsRequest("subscribe", args).toJson();
	}

	private String unsubscribe(String channel, String instType) {
		WsRequest.Arg[] args = new WsRequest.Arg[]{new WsRequest.Arg(channel, instType)};
		return new WsRequest("unsubscribe", args).toJson();
	}

	@Override
	protected String getSubscribeDepositFrame() {
		return subscribe("deposit-info", null);
	}

	@Override
	protected String getUnsubscribeDepositFrame() {
		return unsubscribe("deposit-info", null);
	}

	@Override
	protected String getSubscribePartialFillsFrame() {
		return subscribe("orders", "SWAP");
	}

	@Override
	protected String getUnsubscribePartialFillsFrame() {
		return unsubscribe("orders", "SWAP");
	}
}
