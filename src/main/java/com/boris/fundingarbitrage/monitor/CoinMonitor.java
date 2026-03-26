package com.boris.fundingarbitrage.monitor;

import com.boris.fundingarbitrage.exchange.BaseExchange;
import com.boris.fundingarbitrage.exchange.Instances;
import com.boris.fundingarbitrage.logic.ExchangePair;
import com.boris.fundingarbitrage.model.arbitrage.ArbitrageSnapshot;
import com.boris.fundingarbitrage.model.contract.BookTicker;
import com.boris.fundingarbitrage.model.contract.FundingRate;
import com.boris.fundingarbitrage.model.contract.MarkPrice;
import com.boris.fundingarbitrage.model.exchange.ExchangeName;
import com.boris.fundingarbitrage.model.exchange.ExchangeSnapshot;
import com.boris.fundingarbitrage.model.websocket.patch.BookTickerPatch;
import com.boris.fundingarbitrage.model.websocket.patch.FundingRatePatch;
import com.boris.fundingarbitrage.model.websocket.patch.MarkPricePatch;
import com.boris.fundingarbitrage.util.coinvector.CoinVector;
import com.boris.fundingarbitrage.util.logger.Logger;
import lombok.Getter;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class CoinMonitor {
	private static final int BIT_BOOK = 1;
	private static final int BIT_FUNDING = 1 << 1;
	private static final int BIT_MARK = 1 << 2;
	private static final int ALL_BITS = BIT_BOOK | BIT_FUNDING | BIT_MARK;

	private static final int waitForDataSeconds = 90;

	private final ExchangeCoinMap<FundingRate> fundingRates = new ExchangeCoinMap<>();
	private final ExchangeCoinMap<BookTicker> bookTickers = new ExchangeCoinMap<>();
	private final ExchangeCoinMap<MarkPrice> markPrices = new ExchangeCoinMap<>();

	private final ExchangeCoinMap<SortedSet<Long>> timestampsToProcess = new ExchangeCoinMap<>();
	private final ExchangeCoinMap<BookTicker> tickerCompletions = new ExchangeCoinMap<>();
	private final ExchangeCoinMap<FundingRate> fundingCompletions = new ExchangeCoinMap<>();
	private final ExchangeCoinMap<MarkPrice> markCompletions = new ExchangeCoinMap<>();
	private final ExchangeCoinMap<Map<Long, Set<Consumer<ExchangeSnapshot>>>> timestampHandlers = new ExchangeCoinMap<>();
	private final ExchangeCoinMap<Map<Long, Set<ScheduledFuture<?>>>> completionFutures = new ExchangeCoinMap<>();
	private final ScheduledExecutorService completionScheduler = Executors.newSingleThreadScheduledExecutor();
	private final long completionDelayMs = 500;

	private final CoinVector<Set<BaseExchange>> availableExchangesByCoin;
	private final Map<BaseExchange, Set<String>> availableCoinsByExchange;
	@Getter private final CompletableFuture<Void> initFuture;
	private final ExchangeCoinMap<Integer> initStateBits = new ExchangeCoinMap<>();
	private final AtomicInteger initPendingSignals = new AtomicInteger(0);
	private final CompletableFuture<Void> initDataReady = new CompletableFuture<>();
	private final ConcurrentHashMap<ExchangeName, Consumer<BookTickerPatch>> initBookHandlers = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<ExchangeName, Consumer<FundingRatePatch>> initFundingHandlers = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<ExchangeName, Consumer<MarkPricePatch>> initMarkHandlers = new ConcurrentHashMap<>();

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
			initCompletionTracking();
			Logger.debug("Init completion tracking initialized");
			subscribeData();
			Logger.debug("Subscribed to data");

			CompletableFuture<Void> timeout = CompletableFuture.runAsync(
							() -> {
							}, CompletableFuture.delayedExecutor(waitForDataSeconds, TimeUnit.SECONDS)
			);
			CompletableFuture.anyOf(initDataReady, timeout).join();
			if (!initDataReady.isDone()) {
				Logger.warn("Coin monitor init timed out after " +
										waitForDataSeconds +
										"s. Falling back to completeness check.");
				checkDataCompleteness();
				clearCoinsWithInsufficientExchanges();
			}
			switchToSteadyStateHandlers();
			Logger.log("Switched to steady state handlers");

			Logger.log("Coin monitor initialized:");
			Logger.logCoinVector(availableExchangesByCoin.transform((exchanges, _) -> exchanges.stream()
							.map(exchange -> exchange.name)
							.collect(Collectors.toSet())));
		});
	}

	private void initCompletionTracking() {
		int pending = 0;
		for (var entry : bookTickers.entrySet()) {
			initStateBits.put(entry.exchange(), entry.coin(), 0);
			pending += 3;
		}
		initPendingSignals.set(pending);
		if (pending == 0) initDataReady.complete(null);
	}

	private void checkDataCompleteness() {
		List<ExchangeCoinPair> toUnsubscribe = new ArrayList<>();

		for (BaseExchange ex : Instances.getExchangeArray()) {
			for (String coin : availableExchangesByCoin.keySet()) {
				BookTicker ticker = bookTickers.get(ex, coin);
				FundingRate funding = fundingRates.get(ex, coin);
				MarkPrice mark = markPrices.get(ex, coin);
				if (ticker == null || BookTicker.isPartiallyEmpty(ticker))
					Logger.warn("Book ticker data is incomplete for " + ex.name + " " + coin);
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
				fundingRates.put(exchange, coin, FundingRate.empty());
				bookTickers.put(exchange, coin, BookTicker.empty());
				markPrices.put(exchange, coin, MarkPrice.empty());
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

			Consumer<BookTickerPatch> bookHandler = createInitBookHandler(exchange);
			Consumer<FundingRatePatch> fundingHandler = createInitFundingHandler(exchange);
			Consumer<MarkPricePatch> markHandler = createInitMarkHandler(exchange);

			initBookHandlers.put(exchange.name, bookHandler);
			initFundingHandlers.put(exchange.name, fundingHandler);
			initMarkHandlers.put(exchange.name, markHandler);

			exchange.publicWsClient.subscribeFuturesBookTicker(supportedCoins, bookHandler);
			exchange.publicWsClient.subscribeFuturesFundingRates(supportedCoins, fundingHandler);
			exchange.publicWsClient.subscribeFuturesMarkPrice(supportedCoins, markHandler);
		}
	}

	private Consumer<BookTickerPatch> createInitBookHandler(BaseExchange exchange) {
		return tickerPatch -> bookTickers.compute(
						exchange, tickerPatch.coin(), (k, v) -> {
							if (v == null) return null;
							BookTicker updated = new BookTicker(
											tickerPatch.bidPrice() != null ? tickerPatch.bidPrice() : v.bidPrice(),
											tickerPatch.bidSize() != null ? tickerPatch.bidSize() : v.bidSize(),
											tickerPatch.askPrice() != null ? tickerPatch.askPrice() : v.askPrice(),
											tickerPatch.askSize() != null ? tickerPatch.askSize() : v.askSize(),
											tickerPatch.timestamp()
							);
							tryMarkComplete(exchange, tickerPatch.coin(), BIT_BOOK, !BookTicker.isPartiallyEmpty(v));
							return updated;
						}
		);
	}

	private Consumer<FundingRatePatch> createInitFundingHandler(BaseExchange exchange) {
		return ratePatch -> fundingRates.compute(
						exchange, ratePatch.coin(), (k, v) -> {
							if (v == null) return null;
							FundingRate updated = new FundingRate(
											ratePatch.rate() != null ? ratePatch.rate() : v.rate(),
											ratePatch.settlement() != null ? ratePatch.settlement() : v.settlement(),
											ratePatch.timestamp()
							);
							tryMarkComplete(exchange, ratePatch.coin(), BIT_FUNDING, !FundingRate.isPartiallyEmpty(v));
							return updated;
						}
		);
	}

	private Consumer<MarkPricePatch> createInitMarkHandler(BaseExchange exchange) {
		return markPricePatch -> markPrices.compute(
						exchange, markPricePatch.coin(), (k, v) -> {
							if (v == null) return null;
							MarkPrice updated = new MarkPrice(markPricePatch.price(), markPricePatch.timestamp());
							tryMarkComplete(exchange, markPricePatch.coin(), BIT_MARK, !MarkPrice.isPartiallyEmpty(v));
							return updated;
						}
		);
	}

	private Consumer<BookTickerPatch> createSteadyBookHandler(BaseExchange ex) {
		return tickerPatch -> bookTickers.compute(
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
								tickerCompletions.put(ex, tickerPatch.coin(), result);
							}
							return result;
						}
		);
	}

	private Consumer<FundingRatePatch> createSteadyFundingHandler(BaseExchange ex) {
		return ratePatch -> fundingRates.compute(
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
								fundingCompletions.put(ex, ratePatch.coin(), result);
							}
							return result;
						}
		);
	}

	private Consumer<MarkPricePatch> createSteadyMarkHandler(BaseExchange ex) {
		return markPricePatch -> markPrices.compute(
						ex, markPricePatch.coin(), (k, v) -> {
							if (v == null) return null;
							var timestamps = timestampsToProcess.get(ex, markPricePatch.coin());
							MarkPrice result = new MarkPrice(markPricePatch.price(), markPricePatch.timestamp());
							if (timestamps != null && !timestamps.isEmpty()) {
								if (timestamps.first() < markPricePatch.timestamp().toEpochMilli()) return result;
								markCompletions.put(ex, markPricePatch.coin(), result);
							}
							return result;
						}
		);
	}

	private void switchToSteadyStateHandlers() {
		for (Map.Entry<BaseExchange, Set<String>> entry : availableCoinsByExchange.entrySet()) {
			BaseExchange exchange = entry.getKey();
			Set<String> subscribedCoins = new HashSet<>(entry.getValue());
			if (subscribedCoins.isEmpty()) continue;

			exchange.publicWsClient.subscribeFuturesBookTicker(subscribedCoins, createSteadyBookHandler(exchange));
			exchange.publicWsClient.subscribeFuturesFundingRates(subscribedCoins, createSteadyFundingHandler(exchange));
			exchange.publicWsClient.subscribeFuturesMarkPrice(subscribedCoins, createSteadyMarkHandler(exchange));

			Consumer<BookTickerPatch> initBook = initBookHandlers.remove(exchange.name);
			if (initBook != null) {
				exchange.publicWsClient.removeFuturesBookTickerHandler(subscribedCoins, initBook);
			}

			Consumer<FundingRatePatch> initFunding = initFundingHandlers.remove(exchange.name);
			if (initFunding != null) {
				exchange.publicWsClient.removeFuturesFundingRatesHandler(subscribedCoins, initFunding);
			}

			Consumer<MarkPricePatch> initMark = initMarkHandlers.remove(exchange.name);
			if (initMark != null) {
				exchange.publicWsClient.removeFuturesMarkPriceHandler(subscribedCoins, initMark);
			}
		}
	}

	private void tryMarkComplete(BaseExchange exchange, String coin, int bit, boolean isComplete) {
		if (!isComplete) return;

		initStateBits.compute(
						exchange, coin, (k, bits) -> {
							if (bits == null || (bits & bit) != 0) return bits;

							int updated = bits | bit;
							if (initPendingSignals.decrementAndGet() == 0) {
								initDataReady.complete(null);
							}
							return updated;
						}
		);
	}

	private void dropInitTracking(BaseExchange exchange, String coin) {
		initStateBits.compute(
						exchange, coin, (k, bits) -> {
							if (bits == null) return null;

							int remainingForEntry = 3 - Integer.bitCount(bits & ALL_BITS);
							if (remainingForEntry > 0) {
								if (initPendingSignals.addAndGet(-remainingForEntry) == 0) {
									initDataReady.complete(null);
								}
							}
							return null;
						}
		);
	}

	private void unsubscribeEntries(List<ExchangeCoinPair> toUnsubscribe) {
		for (var entry : toUnsubscribe) dropInitTracking(entry.ex(), entry.coin());

		Map<BaseExchange, Set<String>> unsubBasedByExchange = new HashMap<>();
		for (var entry : toUnsubscribe)
			unsubBasedByExchange.computeIfAbsent(entry.ex(), k -> new HashSet<>()).add(entry.coin());

		for (Map.Entry<BaseExchange, Set<String>> entry : unsubBasedByExchange.entrySet()) {
			entry.getKey().publicWsClient.unsubscribeCoins(entry.getValue());
			fundingRates.removeAll(entry.getKey(), entry.getValue());
			bookTickers.removeAll(entry.getKey(), entry.getValue());
			markPrices.removeAll(entry.getKey(), entry.getValue());
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
		BookTicker ticker = bookTickers.get(exchange, coin);
		MarkPrice markPrice = markPrices.get(exchange, coin);
		FundingRate fundingRate = fundingRates.get(exchange, coin);

		return new ExchangeSnapshot(ticker, fundingRate, markPrice);
	}

	public ArbitrageSnapshot getSnapshot(ExchangePair exchanges, String coin) {
		ExchangeSnapshot longSnapshot = getSnapshot(exchanges.longEx(), coin);
		ExchangeSnapshot shortSnapshot = getSnapshot(exchanges.shortEx(), coin);
		return new ArbitrageSnapshot(longSnapshot, shortSnapshot);
	}

	private void performOnTimestamp(
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

	public void performOnTimestamp(
					long timestamp,
					ExchangePair exchanges,
					String coin,
					Consumer<ArbitrageSnapshot> handler
	) {
		AtomicReference<ExchangeSnapshot> longSnapshot = new AtomicReference<>();
		AtomicReference<ExchangeSnapshot> shortSnapshot = new AtomicReference<>();

		Runnable tryCallback = () -> {
			if (longSnapshot.get() != null && shortSnapshot.get() != null) {
				handler.accept(new ArbitrageSnapshot(longSnapshot.get(), shortSnapshot.get()));
			}
		};

		performOnTimestamp(
						timestamp, exchanges.longEx(), coin, (sn) -> {
							longSnapshot.set(sn);
							tryCallback.run();
						}
		);
		performOnTimestamp(
						timestamp, exchanges.shortEx(), coin, (sn) -> {
							shortSnapshot.set(sn);
							tryCallback.run();
						}
		);
	}

	public void cancelTimestampExecution(long timestamp, ExchangePair exchanges, String coin) {
		Set<ScheduledFuture<?>> longFuture = completionFutures.get(exchanges.longEx(), coin).remove(timestamp);
		longFuture.forEach(f -> f.cancel(true));
		timestampsToProcess.get(exchanges.longEx(), coin).remove(timestamp);
		timestampHandlers.get(exchanges.longEx(), coin).remove(timestamp);

		Set<ScheduledFuture<?>> shortFuture = completionFutures.get(exchanges.shortEx(), coin).remove(timestamp);
		shortFuture.forEach(f -> f.cancel(true));
		timestampsToProcess.get(exchanges.shortEx(), coin).remove(timestamp);
		timestampHandlers.get(exchanges.shortEx(), coin).remove(timestamp);
	}

	private void registerCurrentState(BaseExchange ex, String coin) {
		ExchangeSnapshot current = getSnapshot(ex, coin);
		fundingCompletions.put(ex, coin, current.fundingRate());
		tickerCompletions.put(ex, coin, current.bookTicker());
		markCompletions.put(ex, coin, current.markPrice());
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
		BookTicker ticker = tickerCompletions.get(ex, coin);
		MarkPrice markPrice = markCompletions.get(ex, coin);
		FundingRate fundingRate = fundingCompletions.get(ex, coin);
		return new ExchangeSnapshot(ticker, fundingRate, markPrice);
	}

	private record ExchangeCoinPair(BaseExchange ex, String coin) {
	}
}
