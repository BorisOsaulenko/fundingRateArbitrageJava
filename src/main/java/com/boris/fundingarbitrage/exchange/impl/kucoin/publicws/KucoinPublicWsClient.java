package com.boris.fundingarbitrage.exchange.impl.kucoin.publicws;

import com.boris.fundingarbitrage.exchange.ExchangeContext;
import com.boris.fundingarbitrage.exchange.impl.kucoin.KucoinWsTokenProvider;
import com.boris.fundingarbitrage.exchange.impl.kucoin.ws.pojos.WsRequest;
import com.boris.fundingarbitrage.exchange.publicws.PublicWsClient;
import com.boris.fundingarbitrage.util.wss.publicmessagehandler.FundingSettlementViaRest;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class KucoinPublicWsClient extends PublicWsClient<FundingSettlementViaRest<KucoinPublicMessageHandler>> {
	private final ScheduledExecutorService pingExecutor = Executors.newSingleThreadScheduledExecutor();

	public KucoinPublicWsClient(ExchangeContext context, KucoinPublicMessageHandler messageHandler) {
		var kucoinMessageHandler = new FundingSettlementViaRest<>(messageHandler);
		super(context, KucoinWsTokenProvider.getPublicEndpoint(), kucoinMessageHandler);

		pingExecutor.scheduleAtFixedRate(this::sendPingFrame, 10, 9, TimeUnit.SECONDS);
	}

	private void sendSubscribeFrame(String topic) {
		WsRequest request = new WsRequest(UUID.randomUUID().toString(), "subscribe", topic, false, true);
		this.prettyWsClient.sendObject(request);
	}

	private void sendUnsubscribeFrame(String topic) {
		WsRequest request = new WsRequest(UUID.randomUUID().toString(), "unsubscribe", topic, false, true);
		this.prettyWsClient.sendObject(request);
	}

	private String instrumentTopic(String symbol) {
		return "/contract/instrument:" + symbol;
	}

	private String tickerTopic(String symbol) {
		return "/contractMarket/tickerV2:" + symbol;
	}

	@Override
	protected void sendSubscribeFundingRateFrame(String[] symbols) {
		for (String symbol : symbols) {
			sendSubscribeFrame(instrumentTopic(symbol));
		}
	}

	@Override
	protected void sendUnsubscribeFundingRateFrame(String[] symbols) {
		for (String symbol : symbols) {
			sendUnsubscribeFrame(instrumentTopic(symbol));
		}
	}

	@Override
	protected void sendSubscribeBookTickerFrame(String[] symbols) {
		for (String symbol : symbols) {
			sendSubscribeFrame(tickerTopic(symbol));
		}
	}

	@Override
	protected void sendUnsubscribeBookTickerFrame(String[] symbols) {
		for (String symbol : symbols) {
			sendUnsubscribeFrame(tickerTopic(symbol));
		}
	}

	@Override
	protected void sendSubscribeMarkPriceFrame(String[] symbols) {
		for (String symbol : symbols) {
			sendSubscribeFrame(instrumentTopic(symbol));
		}
	}

	@Override
	protected void sendUnsubscribeMarkPriceFrame(String[] symbols) {
		for (String symbol : symbols) {
			sendUnsubscribeFrame(instrumentTopic(symbol));
		}
	}

	private void sendPingFrame() {
		this.prettyWsClient.sendObject(new PingFrame());
	}

	@Override
	public void close() {
		super.close();
		pingExecutor.shutdownNow();
	}

	private record PingFrame(String id, String type) {
		public PingFrame() {
			this(String.valueOf(Instant.now().toEpochMilli()), "ping");
		}
	}
}
