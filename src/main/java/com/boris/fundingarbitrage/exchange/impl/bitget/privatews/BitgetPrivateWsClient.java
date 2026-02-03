package com.boris.fundingarbitrage.exchange.impl.bitget.privatews;

import com.boris.fundingarbitrage.exchange.ExchangeContext;
import com.boris.fundingarbitrage.exchange.ExchangeCredentials;
import com.boris.fundingarbitrage.exchange.impl.bitget.BitgetSignatures;
import com.boris.fundingarbitrage.exchange.impl.bitget.privatews.pojos.LoginRequest;
import com.boris.fundingarbitrage.exchange.impl.bitget.privatews.pojos.WsRequest;
import com.boris.fundingarbitrage.exchange.privatews.PrivateWsClient;

import java.net.URI;

public class BitgetPrivateWsClient extends PrivateWsClient {
	private static final URI endpoint = URI.create("wss://ws.bitget.com/v2/ws/private");
	private static final BitgetPrivateMessageHandler messageHandler = new BitgetPrivateMessageHandler();
	private static final String instType = "USDT-FUTURES";
	private final ExchangeCredentials credentials;

	public BitgetPrivateWsClient(ExchangeContext context) {
		super(context, endpoint, messageHandler);
		this.credentials = context.getCredentialsOrThrow();
	}

	@Override
	protected void sendAuthenticationFrame() {
		String timestamp = String.valueOf(System.currentTimeMillis());
		String payload = timestamp + "GET" + "/user/verify";
		String sign = BitgetSignatures.signHmacSha256Base64(payload, credentials.apiSecret());
		LoginRequest.LoginArg arg = new LoginRequest.LoginArg(
					credentials.apiKey(),
					credentials.passphrase(),
					timestamp,
					sign
		);
		this.prettyWsClient.sendObject(new LoginRequest("login", new LoginRequest.LoginArg[]{arg}));
	}

	private void sendSubscribeFrame(String channel) {
		WsRequest.Arg arg = new WsRequest.Arg(instType, channel, null);
		this.prettyWsClient.sendObject(new WsRequest("subscribe", new WsRequest.Arg[]{arg}));
	}

	private void sendUnsubscribeFrame(String channel) {
		WsRequest.Arg arg = new WsRequest.Arg(instType, channel, null);
		this.prettyWsClient.sendObject(new WsRequest("unsubscribe", new WsRequest.Arg[]{arg}));
	}

	@Override
	protected void sendSubscribeDepositFrame() {
		sendSubscribeFrame("account");
	}

	@Override
	protected void sendUnsubscribeDepositFrame() {
		sendUnsubscribeFrame("account");
	}

	@Override
	protected void sendSubscribePartialFillsFrame() {
		sendSubscribeFrame("fill");
	}

	@Override
	protected void sendUnsubscribePartialFillsFrame() {
		sendUnsubscribeFrame("fill");
	}
}
