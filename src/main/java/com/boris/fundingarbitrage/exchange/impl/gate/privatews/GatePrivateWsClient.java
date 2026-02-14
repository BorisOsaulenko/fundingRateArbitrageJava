package com.boris.fundingarbitrage.exchange.impl.gate.privatews;

import com.boris.fundingarbitrage.exchange.ExchangeCredentials;
import com.boris.fundingarbitrage.exchange.impl.gate.GateContext;
import com.boris.fundingarbitrage.exchange.privatews.PrivateWsClient;
import com.boris.fundingarbitrage.util.cryptography.Signers;

import java.net.URI;

public class GatePrivateWsClient extends PrivateWsClient {
	private static final URI endpoint = URI.create("wss://fx-ws.gateio.ws/v4/ws/usdt");
	private static final GatePrivateMessageHandler messageHandler = new GatePrivateMessageHandler();
	private final ExchangeCredentials credentials;
	private final String userId;

	public GatePrivateWsClient(GateContext context) {
		super(context, endpoint, messageHandler);
		this.credentials = context.credentials;
		this.userId = context.getUserId();
	}

	@Override
	protected String getAuthenticationFrame() {
		// Gate uses per-request authentication fields instead of a login frame.
		return null;
	}

	private WsRequest.Auth buildAuth(String channel, String event, long time) {
		String signPayload = "channel=" + channel + "&event=" + event + "&time=" + time;
		String signature = Signers.signHmacSha512Hex(signPayload, credentials.apiSecret());
		return new WsRequest.Auth("api_key", credentials.apiKey(), signature);
	}

	private String getSubscribeFrame(String channel, String[] payload) {
		long time = System.currentTimeMillis() / 1000;
		WsRequest.Auth auth = buildAuth(channel, "subscribe", time);
		return new WsRequest(time, channel, "subscribe", payload, auth).toJson();
	}

	private String getUnsubscribeFrame(String channel, String[] payload) {
		long time = System.currentTimeMillis() / 1000;
		WsRequest.Auth auth = buildAuth(channel, "unsubscribe", time);
		return new WsRequest(time, channel, "unsubscribe", payload, auth).toJson();
	}

	@Override
	protected String getSubscribeDepositFrame() {
		return getSubscribeFrame("futures.balances", new String[]{userId});
	}

	@Override
	protected String getUnsubscribeDepositFrame() {
		return getUnsubscribeFrame("futures.balances", new String[]{userId});
	}

	@Override
	protected String getSubscribePartialFillsFrame() {
		return getSubscribeFrame("futures.usertrades", new String[]{userId});
	}

	@Override
	protected String getUnsubscribePartialFillsFrame() {
		return getUnsubscribeFrame("futures.usertrades", new String[]{userId});
	}
}
