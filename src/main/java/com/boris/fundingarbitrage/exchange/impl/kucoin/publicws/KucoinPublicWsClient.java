package com.boris.fundingarbitrage.exchange.impl.kucoin.publicws;

import com.boris.fundingarbitrage.exchange.ExchangeContext;
import com.boris.fundingarbitrage.exchange.impl.kucoin.publicrest.KucoinPublicHttpClient;
import com.boris.fundingarbitrage.util.wss.publicws.FullFundingViaRest;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;

public class KucoinPublicWsClient extends FullFundingViaRest {
	private final ScheduledExecutorService pingExecutor = Executors.newSingleThreadScheduledExecutor();
	private final String instrumentTopic = "/contract/instrument:";
	private final String tickerTopic = "/contractMarket/tickerV2:";

	public KucoinPublicWsClient(ExchangeContext context, KucoinPublicHttpClient publicHttp) {
		KucoinPublicMessageHandler messageHandler = new KucoinPublicMessageHandler(context);
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

	private <T> List<Set<T>> split(Set<T> items) {
		int chunkSize = 100; // Kucoin supports only up to 100 coins in one request

		List<Set<T>> result = new ArrayList<>();
		Set<T> current = new LinkedHashSet<>();

		for (T item : items) {
			current.add(item);
			if (current.size() == chunkSize) {
				result.add(current);
				current = new LinkedHashSet<>();
			}
		}

		if (!current.isEmpty()) {
			result.add(current);
		}

		return result;
	}

	@Override
	protected <T> void subscribe(
					Set<String> coins,
					Map<String, Set<Consumer<T>>> handlersMap,
					Consumer<T> handler,
					Function<Set<String>, String> subscribeMessage
	) {
		List<Set<String>> adjustedToLimits = split(coins);
		for (Set<String> chunk : adjustedToLimits) {
			super.subscribe(chunk, handlersMap, handler, subscribeMessage);
		}
	}

	@Override
	protected <T> void unsubscribe(
					Set<String> coins,
					Map<String, Set<Consumer<T>>> handlersMap,
					Function<Set<String>, String> unsubscribeMessage
	) {
		List<Set<String>> adjustedToLimits = split(coins);
		for (Set<String> chunk : adjustedToLimits) {
			super.unsubscribe(chunk, handlersMap, unsubscribeMessage);
		}
	}

	@Override
	protected String getSubscribeFundingRateFrame(Set<String> symbols) {
		return getSubscribeFrame(instrumentTopic + String.join(",", symbols));
	}

	@Override
	protected String getUnsubscribeFundingRateFrame(Set<String> symbols) {
		return getUnsubscribeFrame(instrumentTopic + String.join(",", symbols));
	}

	@Override
	protected String getSubscribeBookTickerFrame(Set<String> symbols) {
		return getSubscribeFrame(tickerTopic + String.join(",", symbols));
	}

	@Override
	protected String getUnsubscribeBookTickerFrame(Set<String> symbols) {
		return getUnsubscribeFrame(tickerTopic + String.join(",", symbols));
	}

	@Override
	protected String getSubscribeMarkPriceFrame(Set<String> symbols) {
		return getSubscribeFrame(instrumentTopic + String.join(",", symbols));
	}

	@Override
	protected String getUnsubscribeMarkPriceFrame(Set<String> symbols) {
		return getUnsubscribeFrame(instrumentTopic + String.join(",", symbols));
	}

	private void sendPingFrame() {
		if (connected()) this.sendObject(new PingFrame());
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
