package com.boris.fundingarbitrage.monitor;

import com.boris.fundingarbitrage.coinfilter.CoinFilterResult;
import com.boris.fundingarbitrage.exchange.BaseExchange;
import com.boris.fundingarbitrage.model.contract.BookTicker;
import com.boris.fundingarbitrage.model.contract.Funding;
import com.boris.fundingarbitrage.model.contract.Mark;
import com.boris.fundingarbitrage.model.exchange.snapshot.FuturesSnapshot;
import com.boris.fundingarbitrage.model.exchange.snapshot.Snapshot;
import com.boris.fundingarbitrage.model.exchange.snapshot.SpotSnapshot;
import com.boris.fundingarbitrage.model.websocket.patch.BookTickerPatch;
import com.boris.fundingarbitrage.model.websocket.patch.FundingRatePatch;
import com.boris.fundingarbitrage.model.websocket.patch.MarkPricePatch;
import com.boris.fundingarbitrage.strategy.TradeMarket;
import com.boris.fundingarbitrage.util.logger.Logger;
import lombok.Getter;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class CoinMonitor {
	private static final int waitForDataSeconds = 60;

	private final ExchangeCoinMap<Funding> futuresFundingRates = new ExchangeCoinMap<>();
	private final ExchangeCoinMap<BookTicker> futuresBookTickers = new ExchangeCoinMap<>();
	private final ExchangeCoinMap<Mark> futuresMarkPrices = new ExchangeCoinMap<>();
	private final ExchangeCoinMap<BookTicker> spotBookTickers = new ExchangeCoinMap<>();

	private final ExchangeCoinMap<SortedSet<Long>> timestampsToProcess = new ExchangeCoinMap<>();
	private final ExchangeCoinMap<BookTicker> futuresTickerCompletions = new ExchangeCoinMap<>();
	private final ExchangeCoinMap<Funding> futuresFundingCompletions = new ExchangeCoinMap<>();
	private final ExchangeCoinMap<Mark> futuresMarkCompletions = new ExchangeCoinMap<>();
	private final ExchangeCoinMap<BookTicker> spotBookTickerCompletions = new ExchangeCoinMap<>();
	private final ExchangeCoinMap<Map<Long, Set<BiConsumer<FuturesSnapshot, SpotSnapshot>>>> timestampHandlers = new ExchangeCoinMap<>();
	private final ExchangeCoinMap<Map<Long, Set<ScheduledFuture<?>>>> completionFutures = new ExchangeCoinMap<>();
	private final ScheduledExecutorService completionScheduler = Executors.newSingleThreadScheduledExecutor();
	private final long completionDelayMs = 500;

	private final CoinFilterResult filterData;

	@Getter private final CompletableFuture<Void> initFuture;

	public CoinMonitor(
					CoinFilterResult filterData
	) {
		this.filterData = filterData;

		Logger.log(filterData.availableCoinsByExchange().toString());

		this.initFuture = CompletableFuture.runAsync(() -> {
			openWsConnections().join(); // Has to be awaited
			Logger.debug("WS connections opened");
			fillEmptyData();
			Logger.debug("Empty data filled");
			subscribeData(filterData.initialPresentOnFutures(), filterData.initialPresentOnSpot());
			Logger.debug("Subscribed to data");

			CompletableFuture<Void> timeout = CompletableFuture.runAsync(
							() -> {
							}, CompletableFuture.delayedExecutor(waitForDataSeconds, TimeUnit.SECONDS)
			);
			timeout.join();
			checkDataCompleteness();
			clearCoinsWithInsufficientExchanges();

			Logger.log("Coin monitor initialized:");
			Logger.logCoinVector(filterData.availableExchangesByCoin().transform((exchanges, _) -> exchanges.stream()
							.map(exchange -> exchange.name)
							.collect(Collectors.toSet())));
		});
	}

	private void checkDataCompleteness() {
		List<ExchangeCoinPair> toUnsubscribeFutures = new ArrayList<>();
		List<ExchangeCoinPair> toUnsubscribeSpot = new ArrayList<>();
		List<ExchangeCoinPair> toUnsubAll = new ArrayList<>();

		for (Map.Entry<BaseExchange, Set<String>> entry : filterData.availableCoinsByExchange().entrySet()) {
			BaseExchange ex = entry.getKey();
			for (String coin : entry.getValue()) {
				BookTicker ticker = futuresBookTickers.get(ex, coin);
				Funding funding = futuresFundingRates.get(ex, coin);
				Mark mark = futuresMarkPrices.get(ex, coin);
				BookTicker spotTicker = spotBookTickers.get(ex, coin);

				boolean futuresTickerIncomplete = ticker == null || BookTicker.isPartiallyEmpty(ticker);
				boolean fundingIncomplete = funding == null || Funding.isPartiallyEmpty(funding);
				boolean markIncomplete = mark == null || Mark.isPartiallyEmpty(mark);
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
		for (String coin : filterData.availableExchangesByCoin().keySet()) {
			Set<BaseExchange> exchanges = filterData.availableExchangesByCoin().get(coin);
			if (exchanges == null || exchanges.isEmpty()) {
				Logger.warn("Not enough exchanges support " + coin + ". Removing from monitoring.");
				filterData.availableExchangesByCoin().remove(coin);
			}
		}
	}

	private void fillEmptyData() {
		filterData.availableExchangesByCoin().forEach((coin, exchanges) -> {
			for (BaseExchange exchange : exchanges) {
				futuresFundingRates.put(exchange, coin, Funding.empty());
				futuresBookTickers.put(exchange, coin, BookTicker.empty());
				futuresMarkPrices.put(exchange, coin, Mark.empty());
				spotBookTickers.put(exchange, coin, BookTicker.empty());
			}
		});
	}

	private CompletableFuture<Void> openWsConnections() {
		List<CompletableFuture<Void>> futures = new ArrayList<>();
		for (BaseExchange exchange : filterData.availableCoinsByExchange().keySet()) {
			futures.add(exchange.publicWsClient.connect());
		}

		return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
	}

	private void subscribeData(ExchangeCoinMap<Boolean> presentOnFutures, ExchangeCoinMap<Boolean> presentOnSpot) {
		for (Map.Entry<BaseExchange, Set<String>> entry : filterData.availableCoinsByExchange().entrySet()) {
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
							Funding result = new Funding(
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
							Mark result = new Mark(markPricePatch.price(), markPricePatch.timestamp());
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
			filterData.initialPresentOnFutures().removeAll(ex, entry.getValue());
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
			filterData.initialPresentOnSpot().removeAll(entry.getKey(), entry.getValue());
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
			filterData.initialPresentOnFutures().removeAll(ex, coins);
			filterData.initialPresentOnSpot().removeAll(ex, coins);
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
		Set<String> exchangeCoins = filterData.availableCoinsByExchange().get(ex);
		if (exchangeCoins != null) {
			exchangeCoins.removeAll(coins);
			if (exchangeCoins.isEmpty()) filterData.availableCoinsByExchange().remove(ex);
		}

		for (String coin : coins) {
			Set<BaseExchange> exchanges = filterData.availableExchangesByCoin().get(coin);
			if (exchanges != null) {
				exchanges.remove(ex);
				if (exchanges.isEmpty()) filterData.availableExchangesByCoin().remove(coin);
			}
		}
	}

	public void shutdown() {
		for (BaseExchange exchange : filterData.availableCoinsByExchange().keySet()) exchange.publicWsClient.close();
		completionScheduler.shutdownNow();
	}

	public FuturesSnapshot getFuturesSnapshot(BaseExchange ex, String coin) {
		BookTicker ticker = futuresBookTickers.get(ex, coin);
		Mark markPrice = futuresMarkPrices.get(ex, coin);
		Funding fundingRate = futuresFundingRates.get(ex, coin);

		return new FuturesSnapshot(ticker, fundingRate, markPrice);
	}

	public SpotSnapshot getSpotSnapshot(BaseExchange ex, String coin) {
		BookTicker ticker = spotBookTickers.get(ex, coin);
		return new SpotSnapshot(ticker);
	}

	public Snapshot getSnapshot(BaseExchange ex, String coin, TradeMarket market) {
		if (market == TradeMarket.FUTURES) return getFuturesSnapshot(ex, coin);
		else return getSpotSnapshot(ex, coin);
	}

	public void performOnTimestamp(
					long timestamp,
					BaseExchange exchange,
					String coin,
					BiConsumer<FuturesSnapshot, SpotSnapshot> handler
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
		FuturesSnapshot currentFutures = getFuturesSnapshot(ex, coin);
		futuresFundingCompletions.put(ex, coin, currentFutures.funding());
		futuresTickerCompletions.put(ex, coin, currentFutures.bookTicker());
		futuresMarkCompletions.put(ex, coin, currentFutures.mark());

		SpotSnapshot currentSpot = getSpotSnapshot(ex, coin);
		spotBookTickerCompletions.put(ex, coin, currentSpot.bookTicker());
	}

	private void registerTimestampAndHandler(
					long timestamp,
					BaseExchange ex,
					String coin,
					BiConsumer<FuturesSnapshot, SpotSnapshot> handler
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

		Map<Long, Set<BiConsumer<FuturesSnapshot, SpotSnapshot>>> handlers = timestampHandlers.get(ex, coin);
		if (handlers == null) {
			handlers = new ConcurrentHashMap<>();
			Set<BiConsumer<FuturesSnapshot, SpotSnapshot>> set = ConcurrentHashMap.newKeySet();
			handlers.put(timestamp, set);
			timestampHandlers.put(ex, coin, handlers);
		}
		handlers.computeIfAbsent(timestamp, k -> ConcurrentHashMap.newKeySet()).add(handler);
	}

	private void fireCallbacksOnTimestamp(long timestamp, BaseExchange ex, String coin) {
		FuturesSnapshot futuresTimestampSn = getTimestampFuturesCompletion(ex, coin);
		SpotSnapshot spotTimestampSn = getTimestampSpotCompletion(ex, coin);

		timestampsToProcess.get(ex, coin).remove(timestamp);
		Set<BiConsumer<FuturesSnapshot, SpotSnapshot>> handlers = timestampHandlers.get(ex, coin).get(timestamp);
		if (handlers != null) {
			handlers.forEach(handler -> handler.accept(futuresTimestampSn, spotTimestampSn));
			timestampHandlers.get(ex, coin).remove(timestamp);
		}
	}

	private FuturesSnapshot getTimestampFuturesCompletion(BaseExchange ex, String coin) {
		BookTicker ticker = futuresTickerCompletions.get(ex, coin);
		Mark markPrice = futuresMarkCompletions.get(ex, coin);
		Funding fundingRate = futuresFundingCompletions.get(ex, coin);
		return new FuturesSnapshot(ticker, fundingRate, markPrice);
	}

	private SpotSnapshot getTimestampSpotCompletion(BaseExchange ex, String coin) {
		BookTicker ticker = spotBookTickerCompletions.get(ex, coin);
		return new SpotSnapshot(ticker);
	}

	private record ExchangeCoinPair(BaseExchange ex, String coin) {
	}
}
