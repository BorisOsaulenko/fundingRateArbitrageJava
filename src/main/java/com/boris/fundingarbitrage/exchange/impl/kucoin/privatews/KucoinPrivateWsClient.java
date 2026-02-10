package com.boris.fundingarbitrage.exchange.impl.kucoin.privatews;

import com.boris.fundingarbitrage.exchange.ExchangeContext;
import com.boris.fundingarbitrage.exchange.privatews.PrivateWsClient;

import java.net.URI;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class KucoinPrivateWsClient extends PrivateWsClient {
	private static final KucoinPrivateMessageHandler messageHandler = new KucoinPrivateMessageHandler();
	private static final String WALLET_TOPIC = "/contractAccount/wallet";
	private static final String ORDERS_TOPIC = "/contractMarket/tradeOrders";

	public KucoinPrivateWsClient(ExchangeContext context, CompletableFuture<URI> endpointFuture) {
		super(context, endpointFuture, messageHandler);
	}

	@Override
	protected String getAuthenticationFrame() {
		// KuCoin private WS uses a tokenized endpoint; no extra auth frame required.
		return null;
	}

	private String getSubscribeFrame(String topic) {
		WsRequest request = new WsRequest(UUID.randomUUID().toString(), "subscribe", topic, true, true);
		return request.toJson();
	}

	private String getUnsubscribeFrame(String topic) {
		WsRequest request = new WsRequest(UUID.randomUUID().toString(), "unsubscribe", topic, true, true);
		return request.toJson();
	}

	@Override
	protected String getSubscribeDepositFrame() {
		return getSubscribeFrame(WALLET_TOPIC);
	}

	@Override
	protected String getUnsubscribeDepositFrame() {
		return getUnsubscribeFrame(WALLET_TOPIC);
	}

	@Override
	protected String getSubscribePartialFillsFrame() {
		return getSubscribeFrame(ORDERS_TOPIC);
	}

	@Override
	protected String getUnsubscribePartialFillsFrame() {
		return getUnsubscribeFrame(ORDERS_TOPIC);
	}
}
