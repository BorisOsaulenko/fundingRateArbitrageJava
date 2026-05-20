package com.boris.fundingarbitrage.exchange.impl.kucoin.publicws;

import com.boris.fundingarbitrage.exchange.ExchangeContext;
import com.boris.fundingarbitrage.exchange.impl.kucoin.publicrest.KucoinPublicHttpClient;
import com.boris.fundingarbitrage.model.websocket.patch.FundingRatePatch;
import com.boris.fundingarbitrage.model.websocket.patch.MarkPricePatch;
import com.boris.fundingarbitrage.scheduler.ProdModifiableSchedulerBuilder;
import com.boris.fundingarbitrage.util.coinvector.CoinVector;
import com.boris.fundingarbitrage.util.wss.publicws.FullFundingViaRest;

import java.net.URI;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class KucoinPublicWsClient extends FullFundingViaRest {
	private final String instrumentTopic = "/contract/instrument:";
	private final String tickerTopic = "/contractMarket/tickerV2:";
	private final Set<String> fundingAndMarkPriceSubscribedCoins = new HashSet<>();

	public KucoinPublicWsClient(ExchangeContext context, KucoinPublicHttpClient publicHttp) {
		KucoinPublicMessageHandler messageHandler = new KucoinPublicMessageHandler(context);
		CompletableFuture<URI> endpointFuture = publicHttp.fetchPublicWsEndpoint();
		super(context, endpointFuture, messageHandler, publicHttp, new ProdModifiableSchedulerBuilder()
		);
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
					Function<String, String> symbolGetter,
					Function<Set<String>, String> subscribeMessage,
					Consumer<String> messageSender
	) {
		List<Set<String>> adjustedToLimits = split(coins);
		for (Set<String> chunk : adjustedToLimits) {
			super.addHandlers(chunk, handlersMap, handler, symbolGetter, subscribeMessage, messageSender);
		}
	}

	@Override
	protected <T> void unsubscribe(
					Set<String> coins,
					Map<String, Set<Consumer<T>>> handlersMap,
					Function<String, String> symbolGetter,
					Function<Set<String>, String> unsubscribeMessage,
					Consumer<String> messageSender
	) {
		List<Set<String>> adjustedToLimits = split(coins);
		for (Set<String> chunk : adjustedToLimits) {
			super.removeHandlers(chunk, handlersMap, symbolGetter, unsubscribeMessage, messageSender);
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
	public void subscribeFuturesFundingRates(Set<String> coinsToSub, Consumer<FundingRatePatch> handler) {
		subscribeFundingAndMark(coinsToSub, handler, futuresFundingRateHandlers);
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

	protected String getSpotPingFrame() {
		return new PingFrame().toJson();
	}
}
