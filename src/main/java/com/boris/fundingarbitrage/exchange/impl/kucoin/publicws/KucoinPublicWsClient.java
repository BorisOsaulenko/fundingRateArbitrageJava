package com.boris.fundingarbitrage.exchange.impl.kucoin.publicws;

import com.boris.fundingarbitrage.exchange.ExchangeContext;
import com.boris.fundingarbitrage.exchange.impl.kucoin.publicrest.KucoinPublicHttpClient;
import com.boris.fundingarbitrage.util.wss.publicws.FullFundingViaRest;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class KucoinPublicWsClient extends FullFundingViaRest {
	private final ScheduledExecutorService pingExecutor = Executors.newSingleThreadScheduledExecutor();
	private final String instrumentTopic = "/contract/instrument:";
	private final String tickerTopic = "/contractMarket/tickerV2:";

	public KucoinPublicWsClient(
					ExchangeContext context,
					KucoinPublicMessageHandler messageHandler,
					KucoinPublicHttpClient publicHttp
	) {
		super(context, publicHttp.fetchPublicWsEndpoint(), messageHandler, publicHttp);
		pingExecutor.scheduleAtFixedRate(this::sendPingFrame, 10, 9, TimeUnit.SECONDS);
	}

	private String getSubscribeFrame(String topic) {
		WsRequest request = new WsRequest(UUID.randomUUID().toString(), "subscribe", topic, false, true);
		return request.toJson();
	}

	private String getUnsubscribeFrame(String topic) {
		WsRequest request = new WsRequest(UUID.randomUUID().toString(), "unsubscribe", topic, false, true);
		return request.toJson();
	}

	@Override
	protected String getSubscribeFundingRateFrame(List<String> symbols) {
		return getSubscribeFrame(instrumentTopic + String.join(",", symbols));
	}

	@Override
	protected String getUnsubscribeFundingRateFrame(List<String> symbols) {
		return getUnsubscribeFrame(instrumentTopic + String.join(",", symbols));
	}

	@Override
	protected String getSubscribeBookTickerFrame(List<String> symbols) {
		return getSubscribeFrame(tickerTopic + String.join(",", symbols));
	}

	@Override
	protected String getUnsubscribeBookTickerFrame(List<String> symbols) {
		return getUnsubscribeFrame(tickerTopic + String.join(",", symbols));
	}

	@Override
	protected String getSubscribeMarkPriceFrame(List<String> symbols) {
		return getSubscribeFrame(instrumentTopic + String.join(",", symbols));
	}

	@Override
	protected String getUnsubscribeMarkPriceFrame(List<String> symbols) {
		return getUnsubscribeFrame(instrumentTopic + String.join(",", symbols));
	}

	private void sendPingFrame() {
		this.sendObject(new PingFrame());
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
