package com.boris.fundingarbitrage.exchange.impl.kucoin.publicws;

import com.boris.fundingarbitrage.exchange.ExchangeContext;
import com.boris.fundingarbitrage.exchange.impl.kucoin.publicrest.KucoinPublicHttpClient;
import com.boris.fundingarbitrage.model.websocket.patch.FundingRatePatch;
import com.boris.fundingarbitrage.model.websocket.patch.MarkPricePatch;
import com.boris.fundingarbitrage.util.coinvector.CoinVector;
import com.boris.fundingarbitrage.util.wss.publicws.FullFundingViaRest;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class KucoinPublicWsClient extends FullFundingViaRest {
	private final ScheduledExecutorService pingExecutor = Executors.newSingleThreadScheduledExecutor();
	private final String instrumentTopic = "/contract/instrument:";
	private final String tickerTopic = "/contractMarket/tickerV2:";
	private final Set<String> fundingAndMarkPriceSubscribedCoins = new HashSet<>();

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
		int chunkSize = 99; // Kucoin supports only up to 100 coins in one request

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
	protected String getSubscribeFuturesFundingRateFrame(Set<String> symbols) {
		return getSubscribeFrame(instrumentTopic + String.join(",", symbols));
	}

	@Override
	protected String getUnsubscribeFuturesFundingRateFrame(Set<String> symbols) {
		return getUnsubscribeFrame(instrumentTopic + String.join(",", symbols));
	}

	@Override
	protected String getSubscribeFuturesBookTickerFrame(Set<String> symbols) {
		return getSubscribeFrame(tickerTopic + String.join(",", symbols));
	}

	@Override
	protected String getUnsubscribeFuturesBookTickerFrame(Set<String> symbols) {
		return getUnsubscribeFrame(tickerTopic + String.join(",", symbols));
	}

	@Override
	protected String getSubscribeFuturesMarkPriceFrame(Set<String> symbols) {
		return null;
	}

	@Override
	protected String getUnsubscribeFuturesMarkPriceFrame(Set<String> symbols) {
		return null;
	}

	@Override
	protected String getSubscribeSpotBookTickerFrame(Set<String> symbols) {
		return getSubscribeFrame(tickerTopic + String.join(",", symbols));
	}

	@Override
	protected String getUnsubscribeSpotBookTickerFrame(Set<String> symbols) {
		return getUnsubscribeFrame(tickerTopic + String.join(",", symbols));
	}

	private void sendChunked(Set<String> symbols, Function<Set<String>, String> frameBuilder) {
		List<Set<String>> adjustedToLimits = split(symbols);
		for (Set<String> chunk : adjustedToLimits) {
			sendMessage(frameBuilder.apply(chunk));
		}
	}

	private <T> void subscribeFundingAndMark(
					Set<String> coins,
					Consumer<T> handler,
					CoinVector<Set<Consumer<T>>> handlersMap
	) {
		Set<String> coinsToSubscribe = new HashSet<>();
		for (String coin : coins) {
			handlersMap.computeIfAbsent(coin, v -> new HashSet<>()).add(handler);
			if (!fundingAndMarkPriceSubscribedCoins.contains(coin)) {
				coinsToSubscribe.add(coin);
				fundingAndMarkPriceSubscribedCoins.add(coin);
			}
		}

		Set<String> symbolsToSubscribe = coinsToSubscribe.stream()
						.map(context::getFuturesSymbol)
						.collect(Collectors.toSet());
		if (!coinsToSubscribe.isEmpty()) sendChunked(symbolsToSubscribe, this::getSubscribeFuturesFundingRateFrame);
	}

	private <T> void unsubscribeFundingAndMark(
					Set<String> coins,
					CoinVector<Set<Consumer<T>>> handlersMap
	) {
		Set<String> coinsToUnsubscribe = new HashSet<>();
		for (String coin : coins) {
			handlersMap.remove(coin);
			if (fundingAndMarkPriceSubscribedCoins.contains(coin)) {
				coinsToUnsubscribe.add(coin);
				fundingAndMarkPriceSubscribedCoins.remove(coin);
			}
		}

		Set<String> symbolsToUnsubscribe = coinsToUnsubscribe.stream()
						.map(context::getFuturesSymbol)
						.collect(Collectors.toSet());
		if (!coinsToUnsubscribe.isEmpty()) sendChunked(symbolsToUnsubscribe, this::getUnsubscribeFuturesFundingRateFrame);
	}

	@Override
	public void subscribeFuturesFundingRates(Set<String> coins, Consumer<FundingRatePatch> handler) {
		subscribeFundingAndMark(coins, handler, futuresFundingRateHandlers);
	}

	@Override
	public void unsubscribeFuturesFundingRates(Set<String> coins) {
		unsubscribeFundingAndMark(coins, futuresFundingRateHandlers);
	}

	@Override
	public void subscribeFuturesMarkPrice(Set<String> coins, Consumer<MarkPricePatch> handler) {
		subscribeFundingAndMark(coins, handler, futuresMarkPriceHandlers);
	}

	@Override
	public void unsubscribeFuturesMarkPrice(Set<String> coins) {
		unsubscribeFundingAndMark(coins, futuresMarkPriceHandlers);
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
