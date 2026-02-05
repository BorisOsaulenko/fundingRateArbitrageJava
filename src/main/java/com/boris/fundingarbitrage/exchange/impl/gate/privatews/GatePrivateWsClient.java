package com.boris.fundingarbitrage.exchange.impl.gate.privatews;

import com.boris.fundingarbitrage.exchange.ExchangeCredentials;
import com.boris.fundingarbitrage.exchange.impl.gate.GateContext;
import com.boris.fundingarbitrage.exchange.impl.gate.privatews.pojos.WsRequest;
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
		this.credentials = context.getCredentialsOrThrow();
		this.userId = context.getUserId();
	}

	@Override
	protected void sendAuthenticationFrame() {
		// Gate uses per-request authentication fields instead of a login frame.
	}

	private WsRequest.Auth buildAuth(String channel, String event, long time) {
		String signPayload = "channel=" + channel + "&event=" + event + "&time=" + time;
		String signature = Signers.signHmacSha512Hex(signPayload, credentials.apiSecret());
		return new WsRequest.Auth("api_key", credentials.apiKey(), signature, time);
	}

	private void sendSubscribeFrame(String channel, String[] payload) {
		long time = System.currentTimeMillis() / 1000;
		WsRequest.Auth auth = buildAuth(channel, "subscribe", time);
		this.prettyWsClient.sendObject(new WsRequest(time, channel, "subscribe", payload, auth));
	}

	private void sendUnsubscribeFrame(String channel, String[] payload) {
		long time = System.currentTimeMillis() / 1000;
		WsRequest.Auth auth = buildAuth(channel, "unsubscribe", time);
		this.prettyWsClient.sendObject(new WsRequest(time, channel, "unsubscribe", payload, auth));
	}

	@Override
	protected void sendSubscribeDepositFrame() {
		sendSubscribeFrame("futures.balances", new String[]{userId});
	}

	@Override
	protected void sendUnsubscribeDepositFrame() {
		sendUnsubscribeFrame("futures.balances", new String[]{userId});
	}

	@Override
	protected void sendSubscribePartialFillsFrame() {
		sendSubscribeFrame("futures.usertrades", new String[]{userId});
	}

	@Override
	protected void sendUnsubscribePartialFillsFrame() {
		sendUnsubscribeFrame("futures.usertrades", new String[]{userId});
	}
}
