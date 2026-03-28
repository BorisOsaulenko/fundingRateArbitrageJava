package com.boris.fundingarbitrage.monitor;

import com.boris.fundingarbitrage.exchange.BaseExchange;
import com.boris.fundingarbitrage.exchange.Instances;
import com.boris.fundingarbitrage.logic.ExchangePair;
import com.boris.fundingarbitrage.model.contract.BookTicker;
import com.boris.fundingarbitrage.model.contract.FundingRate;
import com.boris.fundingarbitrage.model.contract.MarkPrice;
import com.boris.fundingarbitrage.model.exchange.ExchangeSnapshot;
import com.boris.fundingarbitrage.model.exchange.FuturesSnapshot;
import com.boris.fundingarbitrage.model.websocket.patch.BookTickerPatch;
import com.boris.fundingarbitrage.model.websocket.patch.FundingRatePatch;
import com.boris.fundingarbitrage.model.websocket.patch.MarkPricePatch;
import com.boris.fundingarbitrage.util.coinvector.CoinVector;
import com.boris.fundingarbitrage.util.logger.Logger;
import lombok.Getter;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class CoinMonitor {
	private static final int waitForDataSeconds = 90;

	private final ExchangeCoinMap<FundingRate> futuresFundingRates = new ExchangeCoinMap<>();
	private final ExchangeCoinMap<BookTicker> futuresBookTickers = new ExchangeCoinMap<>();
	private final ExchangeCoinMap<MarkPrice> futuresMarkPrices = new ExchangeCoinMap<>();
	private final ExchangeCoinMap<BookTicker> spotBookTickers = new ExchangeCoinMap<>();

	private final ExchangeCoinMap<SortedSet<Long>> timestampsToProcess = new ExchangeCoinMap<>();
	private final ExchangeCoinMap<BookTicker> futuresTickerCompletions = new ExchangeCoinMap<>();
	private final ExchangeCoinMap<FundingRate> futuresFundingCompletions = new ExchangeCoinMap<>();
	private final ExchangeCoinMap<MarkPrice> futuresMarkCompletions = new ExchangeCoinMap<>();
	private final ExchangeCoinMap<BookTicker> spotBookTickerCompletions = new ExchangeCoinMap<>();
	private final ExchangeCoinMap<Map<Long, Set<Consumer<FuturesSnapshot>>>> timestampHandlers = new ExchangeCoinMap<>();
	private final ExchangeCoinMap<Map<Long, Set<ScheduledFuture<?>>>> completionFutures = new ExchangeCoinMap<>();
	private final ScheduledExecutorService completionScheduler = Executors.newSingleThreadScheduledExecutor();
	private final long completionDelayMs = 500;

	private final CoinVector<Set<BaseExchange>> availableExchangesByCoin;
	private final Map<BaseExchange, Set<String>> availableCoinsByExchange;
	@Getter private final CompletableFuture<Void> initFuture;

	public CoinMonitor(
					CoinVector<Set<BaseExchange>> availableExchangesByCoin,
					Map<BaseExchange, Set<String>> availableCoinsByExchange
	) {
		this.availableExchangesByCoin = availableExchangesByCoin;
		this.availableCoinsByExchange = availableCoinsByExchange;

		Logger.log(availableCoinsByExchange.toString());

		this.initFuture = CompletableFuture.runAsync(() -> {
			openWsConnections().join(); // Has to be awaited
			Logger.debug("WS connections opened");
			fillEmptyData();
			Logger.debug("Empty data filled");
			subscribeData();
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
		List<ExchangeCoinPair> toUnsubscribe = new ArrayList<>();

		for (BaseExchange ex : Instances.getExchangeArray()) {
			for (String coin : availableExchangesByCoin.keySet()) {
				BookTicker ticker = futuresBookTickers.get(ex, coin);
				FundingRate funding = futuresFundingRates.get(ex, coin);
				MarkPrice mark = futuresMarkPrices.get(ex, coin);
				BookTicker spotTicker = spotBookTickers.get(ex, coin);

				if (ticker == null || BookTicker.isPartiallyEmpty(ticker))
					Logger.warn("Book ticker data is incomplete for " + ex.name + " " + coin);
				else if (spotTicker == null || BookTicker.isPartiallyEmpty(spotTicker))
					Logger.warn("Spot book ticker data is incomplete for " + ex.name + " " + coin);
				else if (funding == null || FundingRate.isPartiallyEmpty(funding))
					Logger.warn("Funding rate data is incomplete for " + ex.name + " " + coin);
				else if (mark == null || MarkPrice.isPartiallyEmpty(mark))
					Logger.warn("Mark price data is incomplete for " + ex.name + " " + coin);
				else continue;

				toUnsubscribe.add(new ExchangeCoinPair(ex, coin));
			}
		}

		unsubscribeEntries(toUnsubscribe);
	}

	private void clearCoinsWithInsufficientExchanges() {
		for (String coin : availableExchangesByCoin.keySet()) {
			Set<BaseExchange> exchanges = availableExchangesByCoin.get(coin);
			if (exchanges == null || exchanges.size() < 2) {
				Logger.warn("Not enough exchanges support " + coin + ". Removing from monitoring.");
				unsubscribeCoin(coin);
			}
		}
	}

	private void fillEmptyData() {
		availableExchangesByCoin.forEach((coin, names) -> {
			for (BaseExchange exchange : names) {
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

	private void subscribeData() {
		for (Map.Entry<BaseExchange, Set<String>> entry : availableCoinsByExchange.entrySet()) {
			BaseExchange exchange = entry.getKey();
			Set<String> supportedCoins = new HashSet<>(entry.getValue());
			if (supportedCoins.isEmpty()) continue;

			Consumer<BookTickerPatch> bookHandler = createFuturesTickerHandler(exchange);
			Consumer<FundingRatePatch> fundingHandler = createFuturesFundingHandler(exchange);
			Consumer<MarkPricePatch> markHandler = createFuturesMarkHandler(exchange);
			Consumer<BookTickerPatch> spotBookHandler = createSpotBookHandler(exchange);

			exchange.publicWsClient.subscribeFuturesBookTicker(supportedCoins, bookHandler);
			exchange.publicWsClient.subscribeFuturesFundingRates(supportedCoins, fundingHandler);
			exchange.publicWsClient.subscribeFuturesMarkPrice(supportedCoins, markHandler);
			exchange.publicWsClient.subscribeSpotBookTicker(supportedCoins, spotBookHandler);
		}
	}

	private Consumer<BookTickerPatch> createSpotBookHandler(BaseExchange exchange) {
		return tickerPatch -> spotBookTickers.compute(
						exchange, tickerPatch.coin(), (k, v) -> {
							if (v == null) return null;
							BookTicker result = new BookTicker(
											tickerPatch.bidPrice() != null ? tickerPatch.bidPrice() : v.bidPrice(),
											tickerPatch.bidSize() != null ? tickerPatch.bidSize() : v.bidSize(),
											tickerPatch.askPrice() != null ? tickerPatch.askPrice() : v.askPrice(),
											tickerPatch.askSize() != null ? tickerPatch.askSize() : v.askSize(),
											tickerPatch.timestamp()
							);
							spotBookTickerCompletions.put(exchange, tickerPatch.coin(), result);
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

	private void unsubscribeEntries(List<ExchangeCoinPair> toUnsubscribe) {
		Map<BaseExchange, Set<String>> unsubByExchange = new HashMap<>();
		for (var entry : toUnsubscribe)
			unsubByExchange.computeIfAbsent(entry.ex(), k -> new HashSet<>()).add(entry.coin());

		for (Map.Entry<BaseExchange, Set<String>> entry : unsubByExchange.entrySet()) {
			entry.getKey().publicWsClient.unsubscribeCoins(entry.getValue());
			futuresFundingRates.removeAll(entry.getKey(), entry.getValue());
			futuresBookTickers.removeAll(entry.getKey(), entry.getValue());
			futuresMarkPrices.removeAll(entry.getKey(), entry.getValue());
			spotBookTickers.removeAll(entry.getKey(), entry.getValue());

			availableCoinsByExchange.get(entry.getKey()).removeAll(entry.getValue());
			for (String coin : entry.getValue()) availableExchangesByCoin.get(coin).remove(entry.getKey());
		}
	}

	private void unsubscribeCoin(String coin) {
		Set<BaseExchange> available = availableExchangesByCoin.get(coin);
		if (available != null) {
			List<ExchangeCoinPair> entries = available.stream()
							.map(ex -> new ExchangeCoinPair(ex, coin))
							.toList();
			unsubscribeEntries(entries);
		}

		availableExchangesByCoin.remove(coin);
	}

	public void shutdown() {
		for (BaseExchange exchange : availableCoinsByExchange.keySet()) exchange.publicWsClient.close();
		completionScheduler.shutdownNow();
	}

	public ExchangeSnapshot getSnapshot(BaseExchange exchange, String coin) {
		BookTicker ticker = futuresBookTickers.get(exchange, coin);
		MarkPrice markPrice = futuresMarkPrices.get(exchange, coin);
		FundingRate fundingRate = futuresFundingRates.get(exchange, coin);

		return new FuturesSnapshot(ticker, fundingRate, markPrice);
	}

	public ArbitrageSnapshot getSnapshot(ExchangePair exchanges, String coin) {
		ExchangeSnapshot longSnapshot = getSnapshot(exchanges.longEx(), coin);
		ExchangeSnapshot shortSnapshot = getSnapshot(exchanges.shortEx(), coin);
		return new ArbitrageSnapshot(longSnapshot, shortSnapshot);
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
		Set<ScheduledFuture<?>> longFuture = completionFutures.get(ex, coin).remove(timestamp);
		longFuture.forEach(f -> f.cancel(true));
		timestampsToProcess.get(ex, coin).remove(timestamp);
		timestampHandlers.get(ex, coin).remove(timestamp);
	}

	private void registerCurrentState(BaseExchange ex, String coin) {
		ExchangeSnapshot current = getSnapshot(ex, coin);
		futuresFundingCompletions.put(ex, coin, current.fundingRate());
		futuresTickerCompletions.put(ex, coin, current.bookTicker());
		futuresMarkCompletions.put(ex, coin, current.markPrice());
	}

	private void registerTimestampAndHandler(
					long timestamp,
					BaseExchange ex,
					String coin,
					Consumer<FuturesSnapshot> handler
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

		Map<Long, Set<Consumer<FuturesSnapshot>>> handlers = timestampHandlers.get(ex, coin);
		if (handlers == null) {
			handlers = new ConcurrentHashMap<>();
			Set<Consumer<FuturesSnapshot>> set = ConcurrentHashMap.newKeySet();
			handlers.put(timestamp, set);
			timestampHandlers.put(ex, coin, handlers);
		}
		handlers.computeIfAbsent(timestamp, k -> ConcurrentHashMap.newKeySet()).add(handler);
	}

	private void fireCallbacksOnTimestamp(long timestamp, BaseExchange ex, String coin) {
		FuturesSnapshot timestampSnapshot = getTimestampCompletion(ex, coin);

		timestampsToProcess.get(ex, coin).remove(timestamp);
		Set<Consumer<FuturesSnapshot>> handlers = timestampHandlers.get(ex, coin).get(timestamp);
		if (handlers != null) {
			handlers.forEach(handler -> handler.accept(timestampSnapshot));
			timestampHandlers.get(ex, coin).remove(timestamp);
		}
	}

	private FuturesSnapshot getTimestampCompletion(BaseExchange ex, String coin) {
		BookTicker ticker = futuresTickerCompletions.get(ex, coin);
		MarkPrice markPrice = futuresMarkCompletions.get(ex, coin);
		FundingRate fundingRate = futuresFundingCompletions.get(ex, coin);
		return new FuturesSnapshot(ticker, fundingRate, markPrice);
	}

	private record ExchangeCoinPair(BaseExchange ex, String coin) {
	}
}
