package com.boris.fundingarbitrage.monitor;

import com.boris.fundingarbitrage.coinfilter.CoinFilterResult;
import com.boris.fundingarbitrage.exchange.BaseExchange;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
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

	private final CoinVector<Set<BaseExchange>> availableExchangesByCoin;
	private final Map<BaseExchange, Set<String>> availableCoinsByExchange;
	@Getter
	private final CompletableFuture<Void> initFuture;
	private final ExchangeCoinMap<Integer> initStateBits = new ExchangeCoinMap<>();
	private final AtomicInteger initPendingSignals = new AtomicInteger(0);
	private final CompletableFuture<Void> initDataReady = new CompletableFuture<>();
	private final ConcurrentHashMap<ExchangeName, Consumer<BookTickerPatch>> initBookHandlers = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<ExchangeName, Consumer<FundingRatePatch>> initFundingHandlers = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<ExchangeName, Consumer<MarkPricePatch>> initMarkHandlers = new ConcurrentHashMap<>();

	public CoinMonitor(
					CoinFilterResult filterResult
	) {
		this.availableExchangesByCoin = filterResult.availableExchangesByCoin();
		this.availableCoinsByExchange = filterResult.availableCoinsByExchange();

		this.initFuture = CompletableFuture.runAsync(() -> {
			initFees();
			fillEmptyData();
			initCompletionTracking();
			subscribeData();

			CompletableFuture<Void> timeout = CompletableFuture.runAsync(
							() -> {},
							CompletableFuture.delayedExecutor(waitForDataSeconds, TimeUnit.SECONDS)
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
			Logger.logCoinVector(availableExchangesByCoin.transform((exchanges, _) -> exchanges
							.stream()
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
				forgetCoinExchange(entry.coin(), entry.exchange());
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
				forgetCoinExchange(entry.coin(), entry.exchange());
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
				forgetCoinExchange(entry.coin(), entry.exchange());
			}
		}
	}

	private void clearCoinsWithInsufficientExchanges() {
		for (String coin : availableExchangesByCoin.keySet()) {
			Set<BaseExchange> exchanges = availableExchangesByCoin.get(coin);
			if (exchanges == null || exchanges.size() < 2) {
				Logger.warn("Not enough exchanges support " + coin + ". Removing from monitoring.");
				forgetCoin(coin);
			}
		}
	}

	private void initFees() {
		List<CompletableFuture<Void>> futures = new ArrayList<>();
		for (Map.Entry<BaseExchange, Set<String>> entry : availableCoinsByExchange.entrySet()) {
			BaseExchange exchange = entry.getKey();
			if (entry.getValue().isEmpty()) continue;

			CompletableFuture<Void> future = exchange.privateHttpClient
							.getTradingFees(entry.getValue())
							.thenAccept(result -> {
								result.forEach((coin, fee) -> {
									fees.put(exchange, coin, fee);
								});
							})
							.exceptionally(t -> {
								Logger.error("Failed to fetch trading fees for " + exchange.name + ": " + t.getMessage());
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

	private Consumer<BookTickerPatch> createSteadyBookHandler(BaseExchange exchangeName) {
		return tickerPatch -> bookTickers.compute(
						exchangeName, tickerPatch.coin(), (k, v) -> {
							if (v == null) return null;
							return new BookTicker(
											tickerPatch.bidPrice() != null ? tickerPatch.bidPrice() : v.bidPrice(),
											tickerPatch.bidSize() != null ? tickerPatch.bidSize() : v.bidSize(),
											tickerPatch.askPrice() != null ? tickerPatch.askPrice() : v.askPrice(),
											tickerPatch.askSize() != null ? tickerPatch.askSize() : v.askSize(),
											tickerPatch.timestamp()
							);
						}
		);
	}

	private Consumer<FundingRatePatch> createSteadyFundingHandler(BaseExchange exchangeName) {
		return ratePatch -> fundingRates.compute(
						exchangeName, ratePatch.coin(), (k, v) -> {
							if (v == null) return null;
							return new FundingRate(
											ratePatch.rate() != null ? ratePatch.rate() : v.rate(),
											ratePatch.settlement() != null ? ratePatch.settlement() : v.settlement(),
											ratePatch.timestamp()
							);
						}
		);
	}

	private Consumer<MarkPricePatch> createSteadyMarkHandler(BaseExchange exchangeName) {
		return markPricePatch -> markPrices.compute(
						exchangeName, markPricePatch.coin(), (k, v) -> {
							if (v == null) return null;
							return new MarkPrice(markPricePatch.price(), markPricePatch.timestamp());
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

	private void forgetCoinExchange(String coin, BaseExchange exchange) {
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

	private void forgetCoin(String coin) {
		Set<BaseExchange> available = availableExchangesByCoin.get(coin);
		if (available != null) {
			for (BaseExchange name : available) {
				forgetCoinExchange(coin, name);
			}
		}

		availableExchangesByCoin.remove(coin);
	}

	public void shutdown() {
		for (BaseExchange exchange : availableCoinsByExchange.keySet()) {
			exchange.publicWsClient.close();
		}
	}

	public ExchangeSnapshot getSnapshot(BaseExchange exchange, String coin) {
		BookTicker ticker = bookTickers.get(exchange, coin);
		MarkPrice markPrice = markPrices.get(exchange, coin);
		FundingRate fundingRate = fundingRates.get(exchange, coin);
		Fees fee = fees.get(exchange, coin);

		return new ExchangeSnapshot(ticker, fee, fundingRate, markPrice);
	}
}
