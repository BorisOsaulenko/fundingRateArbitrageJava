package com.boris.fundingarbitrage.monitor;

import com.boris.fundingarbitrage.exchange.BaseExchange;
import com.boris.fundingarbitrage.model.contract.BookTicker;
import com.boris.fundingarbitrage.model.contract.FundingRate;
import com.boris.fundingarbitrage.model.contract.MarkPrice;
import com.boris.fundingarbitrage.model.exchange.ExchangeSnapshot;
import com.boris.fundingarbitrage.model.websocket.patch.BookTickerPatch;
import com.boris.fundingarbitrage.model.websocket.patch.FundingRatePatch;
import com.boris.fundingarbitrage.model.websocket.patch.MarkPricePatch;
import com.boris.fundingarbitrage.strategy.TradeMarket;
import com.boris.fundingarbitrage.util.coinvector.CoinVector;
import com.boris.fundingarbitrage.util.logger.Logger;
import lombok.Getter;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class CoinMonitor {
	private static final int waitForDataSeconds = 60;

	private final ExchangeCoinMap<FundingRate> futuresFundingRates = new ExchangeCoinMap<>();
	private final ExchangeCoinMap<BookTicker> futuresBookTickers = new ExchangeCoinMap<>();
	private final ExchangeCoinMap<MarkPrice> futuresMarkPrices = new ExchangeCoinMap<>();
	private final ExchangeCoinMap<BookTicker> spotBookTickers = new ExchangeCoinMap<>();

	private final ExchangeCoinMap<SortedSet<Long>> timestampsToProcess = new ExchangeCoinMap<>();
	private final ExchangeCoinMap<BookTicker> futuresTickerCompletions = new ExchangeCoinMap<>();
	private final ExchangeCoinMap<FundingRate> futuresFundingCompletions = new ExchangeCoinMap<>();
	private final ExchangeCoinMap<MarkPrice> futuresMarkCompletions = new ExchangeCoinMap<>();
	private final ExchangeCoinMap<BookTicker> spotBookTickerCompletions = new ExchangeCoinMap<>();
	private final ExchangeCoinMap<Map<Long, Set<Consumer<ExchangeSnapshot>>>> timestampHandlers = new ExchangeCoinMap<>();
	private final ExchangeCoinMap<Map<Long, Set<ScheduledFuture<?>>>> completionFutures = new ExchangeCoinMap<>();
	private final ScheduledExecutorService completionScheduler = Executors.newSingleThreadScheduledExecutor();
	private final long completionDelayMs = 500;

	private final CoinVector<Set<BaseExchange>> availableExchangesByCoin;
	private final Map<BaseExchange, Set<String>> availableCoinsByExchange;
	@Getter private final CompletableFuture<Void> initFuture;

	private final ExchangeCoinMap<Boolean> presentOnFutures;
	private final ExchangeCoinMap<Boolean> presentOnSpot;

	public CoinMonitor(
					CoinVector<Set<BaseExchange>> availableExchangesByCoin,
					Map<BaseExchange, Set<String>> availableCoinsByExchange,
					ExchangeCoinMap<Boolean> presentOnFutures,
					ExchangeCoinMap<Boolean> presentOnSpot
	) {
		this.availableExchangesByCoin = availableExchangesByCoin;
		this.availableCoinsByExchange = availableCoinsByExchange;
		this.presentOnFutures = presentOnFutures;
		this.presentOnSpot = presentOnSpot;

		Logger.log(availableCoinsByExchange.toString());

		this.initFuture = CompletableFuture.runAsync(() -> {
			openWsConnections().join(); // Has to be awaited
			Logger.debug("WS connections opened");
			fillEmptyData();
			Logger.debug("Empty data filled");
			subscribeData(presentOnFutures, presentOnSpot);
			Logger.debug("Subscribed to data");

			CompletableFuture<Void> timeout = CompletableFuture.runAsync(
							() -> {
							}, CompletableFuture.delayedExecutor(waitForDataSeconds, TimeUnit.SECONDS)
			);
			timeout.join();
			checkDataCompleteness();
			clearCoinsWithInsufficientExchanges();

			Logger.log("Coin monitor initialized:");
			Logger.logCoinVector(availableExchangesByCoin.transform((exchanges, _) -> exchanges.stream()
							.map(exchange -> exchange.name)
							.collect(Collectors.toSet())));
		});
	}

	private void checkDataCompleteness() {
		List<ExchangeCoinPair> toUnsubscribeFutures = new ArrayList<>();
		List<ExchangeCoinPair> toUnsubscribeSpot = new ArrayList<>();
		List<ExchangeCoinPair> toUnsubAll = new ArrayList<>();

		for (Map.Entry<BaseExchange, Set<String>> entry : availableCoinsByExchange.entrySet()) {
			BaseExchange ex = entry.getKey();
			for (String coin : entry.getValue()) {
				BookTicker ticker = futuresBookTickers.get(ex, coin);
				FundingRate funding = futuresFundingRates.get(ex, coin);
				MarkPrice mark = futuresMarkPrices.get(ex, coin);
				BookTicker spotTicker = spotBookTickers.get(ex, coin);

				boolean futuresTickerIncomplete = ticker == null || BookTicker.isPartiallyEmpty(ticker);
				boolean fundingIncomplete = funding == null || FundingRate.isPartiallyEmpty(funding);
				boolean markIncomplete = mark == null || MarkPrice.isPartiallyEmpty(mark);
				boolean spotTickerIncomplete = spotTicker == null || BookTicker.isPartiallyEmpty(spotTicker);

				boolean shouldStayFutures = false;
				if (futuresTickerIncomplete) Logger.warn("Futures ticker incomplete for " + coin + " on " + ex.name);
				else if (fundingIncomplete) Logger.warn("Futures funding incomplete for " + coin + " on " + ex.name);
				else if (markIncomplete) Logger.warn("Futures mark incomplete for " + coin + " on " + ex.name);
				else shouldStayFutures = true;

				boolean shouldStaySpot = false;
				if (spotTickerIncomplete) Logger.warn("Spot ticker incomplete for " + coin + " on " + ex.name);
				else shouldStaySpot = true;

				ExchangeCoinPair pair = new ExchangeCoinPair(ex, coin);
				if (!shouldStayFutures && !shouldStaySpot) toUnsubAll.add(pair);
				else if (!shouldStayFutures) toUnsubscribeFutures.add(pair);
				else if (!shouldStaySpot) toUnsubscribeSpot.add(pair);
			}
		}

		if (!toUnsubAll.isEmpty()) unsubscribeAllEntries(toUnsubAll);
		if (!toUnsubscribeFutures.isEmpty()) unsubscribeFuturesEntries(toUnsubscribeFutures);
		if (!toUnsubscribeSpot.isEmpty()) unsubscribeSpotEntries(toUnsubscribeSpot);
	}

	private void clearCoinsWithInsufficientExchanges() {
		for (String coin : availableExchangesByCoin.keySet()) {
			Set<BaseExchange> exchanges = availableExchangesByCoin.get(coin);
			if (exchanges == null || exchanges.isEmpty()) {
				Logger.warn("Not enough exchanges support " + coin + ". Removing from monitoring.");
				availableExchangesByCoin.remove(coin);
			}
		}
	}

	private void fillEmptyData() {
		availableExchangesByCoin.forEach((coin, exchanges) -> {
			for (BaseExchange exchange : exchanges) {
				futuresFundingRates.put(exchange, coin, FundingRate.empty());
				futuresBookTickers.put(exchange, coin, BookTicker.empty());
				futuresMarkPrices.put(exchange, coin, MarkPrice.empty());
				spotBookTickers.put(exchange, coin, BookTicker.empty());
			}
		});
	}

	private CompletableFuture<Void> openWsConnections() {
		List<CompletableFuture<Void>> futures = new ArrayList<>();
		for (BaseExchange exchange : availableCoinsByExchange.keySet()) {
			futures.add(exchange.publicWsClient.connect());
		}

		return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
	}

	private void subscribeData(ExchangeCoinMap<Boolean> presentOnFutures, ExchangeCoinMap<Boolean> presentOnSpot) {
		for (Map.Entry<BaseExchange, Set<String>> entry : availableCoinsByExchange.entrySet()) {
			BaseExchange exchange = entry.getKey();
			Set<String> supportedCoins = new HashSet<>(entry.getValue());
			if (supportedCoins.isEmpty()) continue;

			Set<String> supportedOnFutures = supportedCoins.stream()
							.filter(coin -> Boolean.TRUE.equals(presentOnFutures.get(exchange, coin)))
							.collect(Collectors.toSet());
			Set<String> supportedOnSpot = supportedCoins.stream()
							.filter(coin -> Boolean.TRUE.equals(presentOnSpot.get(exchange, coin)))
							.collect(Collectors.toSet());

			if (!supportedOnFutures.isEmpty()) {
				Consumer<BookTickerPatch> bookHandler = createFuturesTickerHandler(exchange);
				Consumer<FundingRatePatch> fundingHandler = createFuturesFundingHandler(exchange);
				Consumer<MarkPricePatch> markHandler = createFuturesMarkHandler(exchange);

				exchange.publicWsClient.subscribeFuturesBookTicker(supportedOnFutures, bookHandler);
				exchange.publicWsClient.subscribeFuturesFundingRates(supportedOnFutures, fundingHandler);
				exchange.publicWsClient.subscribeFuturesMarkPrice(supportedOnFutures, markHandler);
			}

			if (!supportedOnSpot.isEmpty()) {
				Consumer<BookTickerPatch> spotBookHandler = createSpotBookHandler(exchange);

				exchange.publicWsClient.subscribeSpotBookTicker(supportedOnSpot, spotBookHandler);
			}
		}
	}

	private Consumer<BookTickerPatch> createSpotBookHandler(BaseExchange ex) {
		return tickerPatch -> spotBookTickers.compute(
						ex, tickerPatch.coin(), (k, v) -> {
							if (v == null) return null;
							var timestamps = timestampsToProcess.get(ex, tickerPatch.coin());
							BookTicker result = new BookTicker(
											tickerPatch.bidPrice() != null ? tickerPatch.bidPrice() : v.bidPrice(),
											tickerPatch.bidSize() != null ? tickerPatch.bidSize() : v.bidSize(),
											tickerPatch.askPrice() != null ? tickerPatch.askPrice() : v.askPrice(),
											tickerPatch.askSize() != null ? tickerPatch.askSize() : v.askSize(),
											tickerPatch.timestamp()
							);
							if (timestamps != null && !timestamps.isEmpty()) {
								if (timestamps.first() < tickerPatch.timestamp().toEpochMilli()) return result;
								spotBookTickerCompletions.put(ex, tickerPatch.coin(), result);
							}
							return result;
						}
		);
	}

	private Consumer<BookTickerPatch> createFuturesTickerHandler(BaseExchange ex) {
		return tickerPatch -> futuresBookTickers.compute(
						ex, tickerPatch.coin(), (k, v) -> {
							if (v == null) return null;
							var timestamps = timestampsToProcess.get(ex, tickerPatch.coin());
							BookTicker result = new BookTicker(
											tickerPatch.bidPrice() != null ? tickerPatch.bidPrice() : v.bidPrice(),
											tickerPatch.bidSize() != null ? tickerPatch.bidSize() : v.bidSize(),
											tickerPatch.askPrice() != null ? tickerPatch.askPrice() : v.askPrice(),
											tickerPatch.askSize() != null ? tickerPatch.askSize() : v.askSize(),
											tickerPatch.timestamp()
							);
							if (timestamps != null && !timestamps.isEmpty()) {
								if (timestamps.first() < tickerPatch.timestamp().toEpochMilli()) return result;
								futuresTickerCompletions.put(ex, tickerPatch.coin(), result);
							}
							return result;
						}
		);
	}

	private Consumer<FundingRatePatch> createFuturesFundingHandler(BaseExchange ex) {
		return ratePatch -> futuresFundingRates.compute(
						ex, ratePatch.coin(), (k, v) -> {
							if (v == null) return null;
							var timestamps = timestampsToProcess.get(ex, ratePatch.coin());
							FundingRate result = new FundingRate(
											ratePatch.rate() != null ? ratePatch.rate() : v.rate(),
											ratePatch.settlement() != null ? ratePatch.settlement() : v.settlement(),
											ratePatch.timestamp()
							);
							if (timestamps != null && !timestamps.isEmpty()) {
								if (timestamps.first() < ratePatch.timestamp().toEpochMilli()) return result;
								futuresFundingCompletions.put(ex, ratePatch.coin(), result);
							}
							return result;
						}
		);
	}

	private Consumer<MarkPricePatch> createFuturesMarkHandler(BaseExchange ex) {
		return markPricePatch -> futuresMarkPrices.compute(
						ex, markPricePatch.coin(), (k, v) -> {
							if (v == null) return null;
							var timestamps = timestampsToProcess.get(ex, markPricePatch.coin());
							MarkPrice result = new MarkPrice(markPricePatch.price(), markPricePatch.timestamp());
							if (timestamps != null && !timestamps.isEmpty()) {
								if (timestamps.first() < markPricePatch.timestamp().toEpochMilli()) return result;
								futuresMarkCompletions.put(ex, markPricePatch.coin(), result);
							}
							return result;
						}
		);
	}

	private void unsubscribeFuturesEntries(List<ExchangeCoinPair> toUnsubscribe) {
		Map<BaseExchange, Set<String>> unsubByExchange = new HashMap<>();
		for (var entry : toUnsubscribe)
			unsubByExchange.computeIfAbsent(entry.ex(), k -> new HashSet<>()).add(entry.coin());

		for (Map.Entry<BaseExchange, Set<String>> entry : unsubByExchange.entrySet()) {
			BaseExchange ex = entry.getKey();

			ex.publicWsClient.unsubscribeCoinsFutures(entry.getValue());
			futuresFundingRates.removeAll(ex, entry.getValue());
			futuresBookTickers.removeAll(ex, entry.getValue());
			futuresMarkPrices.removeAll(ex, entry.getValue());
			futuresFundingCompletions.removeAll(ex, entry.getValue());
			futuresTickerCompletions.removeAll(ex, entry.getValue());
			futuresMarkCompletions.removeAll(ex, entry.getValue());
			presentOnFutures.removeAll(ex, entry.getValue());
		}
	}

	private void unsubscribeSpotEntries(List<ExchangeCoinPair> toUnsubscribe) {
		Map<BaseExchange, Set<String>> unsubByExchange = new HashMap<>();
		for (var entry : toUnsubscribe)
			unsubByExchange.computeIfAbsent(entry.ex(), k -> new HashSet<>()).add(entry.coin());

		for (Map.Entry<BaseExchange, Set<String>> entry : unsubByExchange.entrySet()) {
			BaseExchange ex = entry.getKey();

			ex.publicWsClient.unsubscribeCoinsSpot(entry.getValue());
			spotBookTickers.removeAll(entry.getKey(), entry.getValue());
			spotBookTickerCompletions.removeAll(entry.getKey(), entry.getValue());
			presentOnSpot.removeAll(entry.getKey(), entry.getValue());
		}
	}

	private void unsubscribeAllEntries(List<ExchangeCoinPair> toUnsubscribe) {
		Map<BaseExchange, Set<String>> unsubByExchange = new HashMap<>();
		for (var entry : toUnsubscribe)
			unsubByExchange.computeIfAbsent(entry.ex(), k -> new HashSet<>()).add(entry.coin());

		for (Map.Entry<BaseExchange, Set<String>> entry : unsubByExchange.entrySet()) {
			BaseExchange ex = entry.getKey();
			Set<String> coins = entry.getValue();

			ex.publicWsClient.unsubscribeCoinsFutures(coins);
			ex.publicWsClient.unsubscribeCoinsSpot(coins);
			futuresFundingRates.removeAll(ex, coins);
			futuresBookTickers.removeAll(ex, coins);
			futuresMarkPrices.removeAll(ex, coins);
			spotBookTickers.removeAll(ex, coins);
			futuresFundingCompletions.removeAll(ex, coins);
			futuresTickerCompletions.removeAll(ex, coins);
			futuresMarkCompletions.removeAll(ex, coins);
			spotBookTickerCompletions.removeAll(ex, coins);
			presentOnFutures.removeAll(ex, coins);
			presentOnSpot.removeAll(ex, coins);
			clearPendingEntries(ex, coins);
			removeAvailability(ex, coins);
		}
	}

	private void clearPendingEntries(BaseExchange ex, Set<String> coins) {
		for (String coin : coins) {
			Map<Long, Set<ScheduledFuture<?>>> futuresByTimestamp = completionFutures.get(ex, coin);
			if (futuresByTimestamp != null) {
				futuresByTimestamp.values().forEach(futures -> futures.forEach(future -> future.cancel(true)));
			}

			completionFutures.remove(ex, coin);
			timestampsToProcess.remove(ex, coin);
			timestampHandlers.remove(ex, coin);
		}
	}

	private void removeAvailability(BaseExchange ex, Set<String> coins) {
		Set<String> exchangeCoins = availableCoinsByExchange.get(ex);
		if (exchangeCoins != null) {
			exchangeCoins.removeAll(coins);
			if (exchangeCoins.isEmpty()) availableCoinsByExchange.remove(ex);
		}

		for (String coin : coins) {
			Set<BaseExchange> exchanges = availableExchangesByCoin.get(coin);
			if (exchanges != null) {
				exchanges.remove(ex);
				if (exchanges.isEmpty()) availableExchangesByCoin.remove(coin);
			}
		}
	}

	public void shutdown() {
		for (BaseExchange exchange : availableCoinsByExchange.keySet()) exchange.publicWsClient.close();
		completionScheduler.shutdownNow();
	}

	public ExchangeSnapshot getSnapshot(BaseExchange exchange, String coin) {
		BookTicker ticker = futuresBookTickers.get(exchange, coin);
		MarkPrice markPrice = futuresMarkPrices.get(exchange, coin);
		FundingRate fundingRate = futuresFundingRates.get(exchange, coin);
		BookTicker spotTicker = spotBookTickers.get(exchange, coin);

		return new ExchangeSnapshot(ticker, fundingRate, markPrice, spotTicker);
	}

	public void performOnTimestamp(
					long timestamp,
					BaseExchange exchange,
					String coin,
					Consumer<ExchangeSnapshot> handler
	) {
		long now = Instant.now().toEpochMilli();
		long duration = timestamp - now;

		if (duration < 0) throw new IllegalArgumentException("Timestamp is in the past: " + timestamp);

		registerTimestampAndHandler(timestamp, exchange, coin, handler);
		registerCurrentState(exchange, coin);

		ScheduledFuture<?> sFuture = completionScheduler.schedule(
						() -> fireCallbacksOnTimestamp(timestamp, exchange, coin),
						duration + completionDelayMs,
						TimeUnit.MILLISECONDS
		);

		completionFutures.get(exchange, coin).get(timestamp).add(sFuture);
	}

	public void cancelTimestampExecution(long timestamp, BaseExchange ex, String coin) {
		var futuresByTimestamps = this.completionFutures.get(ex, coin);
		if (futuresByTimestamps == null || !futuresByTimestamps.containsKey(timestamp)) return;

		Set<ScheduledFuture<?>> completionFutures = futuresByTimestamps.remove(timestamp);
		completionFutures.forEach(f -> f.cancel(true));
		timestampsToProcess.get(ex, coin).remove(timestamp);
		timestampHandlers.get(ex, coin).remove(timestamp);
	}

	private void registerCurrentState(BaseExchange ex, String coin) {
		ExchangeSnapshot current = getSnapshot(ex, coin);
		futuresFundingCompletions.put(ex, coin, current.futuresSnapshot().fundingRate());
		futuresTickerCompletions.put(ex, coin, current.bookTicker(TradeMarket.FUTURES));
		futuresMarkCompletions.put(ex, coin, current.futuresSnapshot().markPrice());
		spotBookTickerCompletions.put(ex, coin, current.bookTicker(TradeMarket.SPOT));
	}

	private void registerTimestampAndHandler(
					long timestamp,
					BaseExchange ex,
					String coin,
					Consumer<ExchangeSnapshot> handler
	) {
		Map<Long, Set<ScheduledFuture<?>>> futuresMap = completionFutures.get(ex, coin);
		if (futuresMap == null) {
			futuresMap = new ConcurrentHashMap<>();
			completionFutures.put(ex, coin, futuresMap);
		}
		futuresMap.computeIfAbsent(timestamp, k -> ConcurrentHashMap.newKeySet());

		SortedSet<Long> timestamps = timestampsToProcess.get(ex, coin);
		if (timestamps == null) {
			timestamps = new ConcurrentSkipListSet<>();
			timestampsToProcess.put(ex, coin, timestamps);
		}
		timestamps.add(timestamp);

		Map<Long, Set<Consumer<ExchangeSnapshot>>> handlers = timestampHandlers.get(ex, coin);
		if (handlers == null) {
			handlers = new ConcurrentHashMap<>();
			Set<Consumer<ExchangeSnapshot>> set = ConcurrentHashMap.newKeySet();
			handlers.put(timestamp, set);
			timestampHandlers.put(ex, coin, handlers);
		}
		handlers.computeIfAbsent(timestamp, k -> ConcurrentHashMap.newKeySet()).add(handler);
	}

	private void fireCallbacksOnTimestamp(long timestamp, BaseExchange ex, String coin) {
		ExchangeSnapshot timestampSnapshot = getTimestampCompletion(ex, coin);

		timestampsToProcess.get(ex, coin).remove(timestamp);
		Set<Consumer<ExchangeSnapshot>> handlers = timestampHandlers.get(ex, coin).get(timestamp);
		if (handlers != null) {
			handlers.forEach(handler -> handler.accept(timestampSnapshot));
			timestampHandlers.get(ex, coin).remove(timestamp);
		}
	}

	private ExchangeSnapshot getTimestampCompletion(BaseExchange ex, String coin) {
		BookTicker ticker = futuresTickerCompletions.get(ex, coin);
		MarkPrice markPrice = futuresMarkCompletions.get(ex, coin);
		FundingRate fundingRate = futuresFundingCompletions.get(ex, coin);
		BookTicker spotTicker = spotBookTickerCompletions.get(ex, coin);
		return new ExchangeSnapshot(ticker, fundingRate, markPrice, spotTicker);
	}

	private record ExchangeCoinPair(BaseExchange ex, String coin) {
	}
}
