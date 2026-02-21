package com.boris.fundingarbitrage.exchange.impl.bitget.privatews;

import com.boris.fundingarbitrage.exchange.ExchangeContext;
import com.boris.fundingarbitrage.exchange.ExchangeCredentials;
import com.boris.fundingarbitrage.exchange.privatews.PrivateWsClient;
import com.boris.fundingarbitrage.util.cryptography.Signers;

import java.net.URI;

public class BitgetPrivateWsClient extends PrivateWsClient {
	private static final URI endpoint = URI.create("wss://ws.bitget.com/v2/ws/private");
	private static final BitgetPrivateMessageHandler messageHandler = new BitgetPrivateMessageHandler();
	private static final String instType = "USDT-FUTURES";
	private final ExchangeCredentials credentials;

	public BitgetPrivateWsClient(ExchangeContext context) {
		super(context, endpoint, messageHandler);
		this.credentials = context.credentials;
	}

	@Override
	protected String getAuthenticationFrame() {
		String timestamp = String.valueOf(System.currentTimeMillis());
		String payload = timestamp + "GET" + "/user/verify";
		String sign = Signers.signHmacSha256Base64(payload, credentials.apiSecret());
		LoginRequest.LoginArg arg = new LoginRequest.LoginArg(
						credentials.apiKey(),
						credentials.passphrase(),
						timestamp,
						sign
		);
		return new LoginRequest("login", new LoginRequest.LoginArg[]{arg}).toJson();
	}

	private String getSubscribeFrame(String channel) {
		WsRequest.Arg arg = new WsRequest.Arg(instType, channel, null);
		return new WsRequest("subscribe", new WsRequest.Arg[]{arg}).toJson();
	}

	private String getUnsubscribeFrame(String channel) {
		WsRequest.Arg arg = new WsRequest.Arg(instType, channel, null);
		return new WsRequest("unsubscribe", new WsRequest.Arg[]{arg}).toJson();
	}

	@Override
	protected String getSubscribeDepositFrame() {
		return getSubscribeFrame("account");
	}

	@Override
	protected String getUnsubscribeDepositFrame() {
		return getUnsubscribeFrame("account");
	}

	@Override
	protected String getSubscribePartialFillsFrame() {
		return getSubscribeFrame("fill");
	}

	@Override
	protected String getUnsubscribePartialFillsFrame() {
		return getUnsubscribeFrame("fill");
	}
}
