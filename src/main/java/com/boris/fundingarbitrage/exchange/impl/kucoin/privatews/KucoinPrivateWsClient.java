package com.boris.fundingarbitrage.exchange.impl.kucoin.privatews;

import com.boris.fundingarbitrage.exchange.ExchangeContext;
import com.boris.fundingarbitrage.exchange.impl.kucoin.KucoinWsTokenProvider;
import com.boris.fundingarbitrage.exchange.impl.kucoin.ws.pojos.WsRequest;
import com.boris.fundingarbitrage.exchange.privatews.PrivateWsClient;

import java.net.URI;
import java.util.UUID;

public class KucoinPrivateWsClient extends PrivateWsClient {
	private static final KucoinPrivateMessageHandler messageHandler = new KucoinPrivateMessageHandler();
	private static final String WALLET_TOPIC = "/contractAccount/wallet";
	private static final String ORDERS_TOPIC = "/contractMarket/tradeOrders";
	public KucoinPrivateWsClient(ExchangeContext context) {
		super(context, resolveEndpoint(context), messageHandler);
	}

	private static URI resolveEndpoint(ExchangeContext context) {
		return KucoinWsTokenProvider.getPrivateEndpoint(context.getCredentialsOrThrow());
	}

	@Override
	protected void sendAuthenticationFrame() {
		// KuCoin private WS uses a tokenized endpoint; no extra auth frame required.
	}

	private void sendSubscribeFrame(String topic) {
		WsRequest request = new WsRequest(UUID.randomUUID().toString(), "subscribe", topic, true, true);
		this.prettyWsClient.sendObject(request);
	}

	private void sendUnsubscribeFrame(String topic) {
		WsRequest request = new WsRequest(UUID.randomUUID().toString(), "unsubscribe", topic, true, true);
		this.prettyWsClient.sendObject(request);
	}

	@Override
	protected void sendSubscribeDepositFrame() {
		sendSubscribeFrame(WALLET_TOPIC);
	}

	@Override
	protected void sendUnsubscribeDepositFrame() {
		sendUnsubscribeFrame(WALLET_TOPIC);
	}

	@Override
	protected void sendSubscribePartialFillsFrame() {
		sendSubscribeFrame(ORDERS_TOPIC);
	}

	@Override
	protected void sendUnsubscribePartialFillsFrame() {
		sendUnsubscribeFrame(ORDERS_TOPIC);
	}
}
