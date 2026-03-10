package com.boris.fundingarbitrage.monitor;

import com.boris.fundingarbitrage.coinfilter.CoinFilterResult;
import com.boris.fundingarbitrage.exchange.BaseExchange;
import com.boris.fundingarbitrage.logic.ExchangePair;
import com.boris.fundingarbitrage.model.arbitrage.ArbitrageSnapshot;
import com.boris.fundingarbitrage.model.contract.BookTicker;
import com.boris.fundingarbitrage.model.contract.Fees;
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

import java.math.BigDecimal;
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

	private static final int waitForDataSeconds = 60;

	private final ExchangeCoinMap<FundingRate> fundingRates = new ExchangeCoinMap<>();
	private final ExchangeCoinMap<BookTicker> bookTickers = new ExchangeCoinMap<>();
	private final ExchangeCoinMap<MarkPrice> markPrices = new ExchangeCoinMap<>();
	private final ExchangeCoinMap<Fees> fees = new ExchangeCoinMap<>();
	private final ExchangeCoinMap<BigDecimal> lotSizes;
	private final ExchangeCoinMap<Integer> fundingIntervals;

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
	private final Map<Integer, ArbitrageSnapshotCompletion> arbitrageSnapshotCompletions = new ConcurrentHashMap<>();

	public CoinMonitor(
					CoinFilterResult filterResult
	) {
		this.availableExchangesByCoin = filterResult.availableExchangesByCoin();
		this.availableCoinsByExchange = filterResult.availableCoinsByExchange();

		this.lotSizes = filterResult.lotSizes();
		this.fundingIntervals = filterResult.fundingIntervals();

		this.initFuture = CompletableFuture.runAsync(() -> {
			openWsConnections().join(); // Has to be awaited
			initFees();
			fillEmptyData();
			initCompletionTracking();
			subscribeData();

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

			Logger.log("Coin monitor initialized:");
			Logger.logCoinVector(availableExchangesByCoin.transform((exchanges, _) -> exchanges.stream()
							.map(exchange -> exchange.name)
							.collect(Collectors.toSet())));
			switchToSteadyStateHandlers();
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
		for (var entry : bookTickers.entrySet()) {
			if (BookTicker.isPartiallyEmpty(entry.value())) {
				Logger.warn("Book ticker data is incomplete for " +
										entry.exchange().name +
										" " +
										entry.coin() +
										": " +
										entry.value());
				unsubscribeCoinExchange(entry.coin(), entry.exchange());
			}
		}

		for (var entry : fundingRates.entrySet()) {
			if (FundingRate.isPartiallyEmpty(entry.value())) {
				Logger.warn("Funding rate is incomplete for " +
										entry.exchange().name +
										" " +
										entry.coin() +
										": " +
										entry.value());
				unsubscribeCoinExchange(entry.coin(), entry.exchange());
			}
		}

		for (var entry : markPrices.entrySet()) {
			if (MarkPrice.isPartiallyEmpty(entry.value())) {
				Logger.warn("Mark price is incomplete for " +
										entry.exchange().name +
										" " +
										entry.coin() +
										": " +
										entry.value());
				unsubscribeCoinExchange(entry.coin(), entry.exchange());
			}
		}
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

	private void initFees() {
		List<CompletableFuture<Void>> futures = new ArrayList<>();
		for (Map.Entry<BaseExchange, Set<String>> entry : availableCoinsByExchange.entrySet()) {
			BaseExchange exchange = entry.getKey();
			if (entry.getValue().isEmpty()) continue;

			CompletableFuture<Void> future = exchange.privateHttpClient.getTradingFees(entry.getValue())
							.thenAccept(result -> {
								result.forEach((coin, fee) -> {
									fees.put(exchange, coin, fee);
								});
							})
							.exceptionally(t -> {
								Logger.error("Failed to fetch trading fees for " +
														 exchange.name +
														 ": " +
														 t.getMessage());
								throw new RuntimeException(t);
							});

			futures.add(future);
		}
		CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
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
			List<String> supportedCoins = entry.getValue().stream().toList();
			if (supportedCoins.isEmpty()) continue;

			Consumer<BookTickerPatch> bookHandler = createInitBookHandler(exchange);
			Consumer<FundingRatePatch> fundingHandler = createInitFundingHandler(exchange);
			Consumer<MarkPricePatch> markHandler = createInitMarkHandler(exchange);

			initBookHandlers.put(exchange.name, bookHandler);
			initFundingHandlers.put(exchange.name, fundingHandler);
			initMarkHandlers.put(exchange.name, markHandler);

			exchange.publicWsClient.subscribeBookTicker(supportedCoins, bookHandler);
			exchange.publicWsClient.subscribeFundingRates(supportedCoins, fundingHandler);
			exchange.publicWsClient.subscribeMarkPrice(supportedCoins, markHandler);
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
			List<String> subscribedCoins = entry.getValue().stream().toList();
			if (subscribedCoins.isEmpty()) continue;

			exchange.publicWsClient.subscribeBookTicker(subscribedCoins, createSteadyBookHandler(exchange));
			exchange.publicWsClient.subscribeFundingRates(subscribedCoins, createSteadyFundingHandler(exchange));
			exchange.publicWsClient.subscribeMarkPrice(subscribedCoins, createSteadyMarkHandler(exchange));

			Consumer<BookTickerPatch> initBook = initBookHandlers.remove(exchange.name);
			if (initBook != null) {
				exchange.publicWsClient.removeBookTickerHandler(subscribedCoins, initBook);
			}

			Consumer<FundingRatePatch> initFunding = initFundingHandlers.remove(exchange.name);
			if (initFunding != null) {
				exchange.publicWsClient.removeFundingRatesHandler(subscribedCoins, initFunding);
			}

			Consumer<MarkPricePatch> initMark = initMarkHandlers.remove(exchange.name);
			if (initMark != null) {
				exchange.publicWsClient.removeMarkPriceHandler(subscribedCoins, initMark);
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

	public void unsubscribeCoinExchange(String coin, BaseExchange exchange) {
		dropInitTracking(exchange, coin);

		Set<BaseExchange> available = availableExchangesByCoin.get(coin);
		if (available != null) available.remove(exchange);

		Set<String> subscribedCoins = availableCoinsByExchange.get(exchange);
		if (subscribedCoins != null) subscribedCoins.remove(coin);

		exchange.publicWsClient.unsubscribeCoin(coin);

		fundingRates.remove(exchange, coin);
		bookTickers.remove(exchange, coin);
		markPrices.remove(exchange, coin);
		fees.remove(exchange, coin);
	}

	public void unsubscribeCoin(String coin) {
		Set<BaseExchange> available = availableExchangesByCoin.get(coin);
		if (available != null) {
			for (BaseExchange name : available) {
				unsubscribeCoinExchange(coin, name);
			}
		}

		availableExchangesByCoin.remove(coin);
	}

	public void shutdown() {
		for (BaseExchange exchange : availableCoinsByExchange.keySet()) {
			exchange.publicWsClient.close();
		}

		for (ArbitrageSnapshotCompletion completion : arbitrageSnapshotCompletions.values()) {
			completion.longSnapshotFuture().cancel(true);
			completion.shortSnapshotFuture().cancel(true);
		}
	}

	public ExchangeSnapshot getSnapshot(BaseExchange exchange, String coin) {
		BookTicker ticker = bookTickers.get(exchange, coin);
		MarkPrice markPrice = markPrices.get(exchange, coin);
		FundingRate fundingRate = fundingRates.get(exchange, coin);
		Fees fee = fees.get(exchange, coin);

		return new ExchangeSnapshot(ticker, fee, fundingRate, markPrice);
	}

	public ArbitrageSnapshot getSnapshot(ExchangePair exchanges, String coin) {
		ExchangeSnapshot longSnapshot = getSnapshot(exchanges.longEx(), coin);
		ExchangeSnapshot shortSnapshot = getSnapshot(exchanges.shortEx(), coin);
		return new ArbitrageSnapshot(longSnapshot, shortSnapshot);
	}

	public BigDecimal getLotSize(BaseExchange ex, String coin) {
		return lotSizes.get(ex, coin);
	}

	public Integer getFundingInterval(BaseExchange ex, String coin) {
		return fundingIntervals.get(ex, coin);
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
			timestamps = new TreeSet<>();
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
		Fees fee = fees.get(ex, coin);
		return new ExchangeSnapshot(ticker, fee, fundingRate, markPrice);
	}

	private record ArbitrageSnapshotCompletion(
					CompletableFuture<Void> longSnapshotFuture,
					CompletableFuture<Void> shortSnapshotFuture
	) {
	}
}
