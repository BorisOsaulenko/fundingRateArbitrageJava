package com.boris.fundingarbitrage.monitor;

import com.boris.fundingarbitrage.exchange.BaseExchange;
import com.boris.fundingarbitrage.exchange.Instances;
import com.boris.fundingarbitrage.exchange.publichttp.PublicOnePullData;
import com.boris.fundingarbitrage.model.contract.BookTicker;
import com.boris.fundingarbitrage.model.contract.Fees;
import com.boris.fundingarbitrage.model.contract.FundingRate;
import com.boris.fundingarbitrage.model.contract.MarkPrice;
import com.boris.fundingarbitrage.model.exchange.ExchangeName;
import com.boris.fundingarbitrage.model.websocket.patch.BookTickerPatch;
import com.boris.fundingarbitrage.model.websocket.patch.FundingRatePatch;
import com.boris.fundingarbitrage.model.websocket.patch.MarkPricePatch;
import com.boris.fundingarbitrage.util.coinvector.CoinVector;
import com.boris.fundingarbitrage.util.logger.Logger;
import lombok.Getter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class CoinMonitor {
	private static final int BIT_BOOK = 1;
	private static final int BIT_FUNDING = 1 << 1;
	private static final int BIT_MARK = 1 << 2;
	private static final int ALL_BITS = BIT_BOOK | BIT_FUNDING | BIT_MARK;

	private final double min24hVolumeUsdt = 600_000;
	private final double maxAffordablePrice = 20;

	private final Set<String> coins;
	private final int waitForDataSeconds = 60;
	private final ExchangeCoinMap<FundingRate> fundingRates = new ExchangeCoinMap<>();
	private final ExchangeCoinMap<BookTicker> bookTickers = new ExchangeCoinMap<>();
	private final ExchangeCoinMap<MarkPrice> markPrices = new ExchangeCoinMap<>();
	private final ExchangeCoinMap<Fees> fees = new ExchangeCoinMap<>();
	private final ExchangeCoinMap<CompletableFuture<Void>> feesFutures = new ExchangeCoinMap<>();
	private final ExchangeCoinMap<PublicOnePullData> initialData = new ExchangeCoinMap<>();

	private final CoinVector<Set<ExchangeName>> availableExchangesByCoin = new CoinVector<>();
	private final Map<BaseExchange, Set<String>> availableCoinsByExchange = new ConcurrentHashMap<>();
	@Getter
	private final CompletableFuture<Void> initFuture;
	private final ExchangeCoinMap<Integer> initStateBits = new ExchangeCoinMap<>();
	private final AtomicInteger initPendingSignals = new AtomicInteger(0);
	private final CompletableFuture<Void> initDataReady = new CompletableFuture<>();
	private final ConcurrentHashMap<ExchangeName, Consumer<BookTickerPatch>> initBookHandlers = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<ExchangeName, Consumer<FundingRatePatch>> initFundingHandlers = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<ExchangeName, Consumer<MarkPricePatch>> initMarkHandlers = new ConcurrentHashMap<>();

	public CoinMonitor(Set<String> coins) {
		this.coins = coins;

		this.initFuture = CompletableFuture.runAsync(() -> {
			initAvailableExchanges();
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

			Logger.logCoinVector(availableExchangesByCoin);
			switchToSteadyStateHandlers();
		});
	}

	private void initCompletionTracking() {
		int pending = 0;
		for (var entry : bookTickers.entrySet()) {
			initStateBits.put(entry.name(), entry.coin(), 0);
			pending += 3;
		}
		initPendingSignals.set(pending);
		if (pending == 0) initDataReady.complete(null);
	}

	private void checkDataCompleteness() {
		for (var entry : bookTickers.entrySet()) {
			if (BookTicker.isPartiallyEmpty(entry.value())) {
				Logger.warn("Book ticker data is incomplete for " + entry.name() + " " + entry.coin() + ": " + entry.value());
				forgetCoinExchange(entry.coin(), entry.name());
			}
		}

		for (var entry : fundingRates.entrySet()) {
			if (FundingRate.isPartiallyEmpty(entry.value())) {
				Logger.warn("Funding rate is incomplete for " + entry.name() + " " + entry.coin() + ": " + entry.value());
				forgetCoinExchange(entry.coin(), entry.name());
			}
		}

		for (var entry : markPrices.entrySet()) {
			if (MarkPrice.isPartiallyEmpty(entry.value())) {
				Logger.warn("Mark price is incomplete for " + entry.name() + " " + entry.coin() + ": " + entry.value());
				forgetCoinExchange(entry.coin(), entry.name());
			}
		}
	}

	private void clearCoinsWithInsufficientExchanges() {
		for (String coin : availableExchangesByCoin.keySet()) {
			Set<ExchangeName> exchanges = availableExchangesByCoin.get(coin);
			if (exchanges == null || exchanges.size() < 2) {
				Logger.warn("Not enough exchanges support " + coin + ". Removing from monitoring.");
				forgetCoin(coin);
			}
		}
	}

	private String shouldExcludeCoin(PublicOnePullData data) {
		if (data.isEmpty()) return "Coin does not exist";
		if (data.volume24h() < min24hVolumeUsdt) return "Volume not enough: " + data.volume24h();
		if (data.bookTicker().askPrice * data.lotSize() > maxAffordablePrice) {
			return "Price too high; min price step: " + data.bookTicker().askPrice * data.lotSize();
		}

		return null;
	}

	private void initAvailableExchanges() {
		for (String coin : coins) availableExchangesByCoin.put(coin, ConcurrentHashMap.newKeySet());

		List<CompletableFuture<Void>> futures = new ArrayList<>();
		for (BaseExchange exchange : Instances.getExchangeArray()) {
			availableCoinsByExchange.put(exchange, ConcurrentHashMap.newKeySet());
			CompletableFuture<Void> future = exchange.publicHttpClient.getOnePullData(coins).thenAccept(coinsVect -> {
				coinsVect.forEach((coin, data) -> {
					String excludedMsg = shouldExcludeCoin(data);
					if (excludedMsg != null) {
						Logger.log("Excluding " + coin + " from monitoring: " + excludedMsg);
						forgetCoinExchange(coin, exchange.name);
						return;
					}
					availableExchangesByCoin.get(coin).add(exchange.name);
					availableCoinsByExchange.get(exchange).add(coin);
					initialData.put(exchange.name, coin, data);
				});
			}).exceptionally(err -> {
				Logger.log("Failed to fetch available coins for " + exchange.name + ": " + err.getMessage());
				return null;
			});
			futures.add(future);
		}

		CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
		clearCoinsWithInsufficientExchanges();
	}

	private void initFees() {
		for (Map.Entry<BaseExchange, Set<String>> entry : availableCoinsByExchange.entrySet()) {
			BaseExchange exchange = entry.getKey();
			if (entry.getValue().isEmpty()) return;

			exchange.privateHttpClient.getTradingFees(entry.getValue()).thenAccept(result -> {
				result.forEach((coin, fee) -> {
					fees.put(exchange.name, coin, fee);
				});
			}).exceptionally(t -> {
				Logger.error("Failed to fetch trading fees for " + exchange.name + ": " + t.getMessage());
				throw new RuntimeException(t);
			});
		}
	}

	private void fillEmptyData() {
		availableExchangesByCoin.forEach((coin, names) -> {
			for (ExchangeName exchangeName : names) {
				fundingRates.put(exchangeName, coin, FundingRate.empty());
				bookTickers.put(exchangeName, coin, BookTicker.empty());
				markPrices.put(exchangeName, coin, MarkPrice.empty());
			}
		});
	}

	private void subscribeData() {
		for (Map.Entry<BaseExchange, Set<String>> entry : availableCoinsByExchange.entrySet()) {
			BaseExchange exchange = entry.getKey();
			List<String> supportedCoins = entry.getValue().stream().toList();
			if (supportedCoins.isEmpty()) continue;

			Consumer<BookTickerPatch> bookHandler = createInitBookHandler(exchange.name);
			Consumer<FundingRatePatch> fundingHandler = createInitFundingHandler(exchange.name);
			Consumer<MarkPricePatch> markHandler = createInitMarkHandler(exchange.name);

			initBookHandlers.put(exchange.name, bookHandler);
			initFundingHandlers.put(exchange.name, fundingHandler);
			initMarkHandlers.put(exchange.name, markHandler);

			exchange.publicWsClient.subscribeBookTicker(supportedCoins, bookHandler);
			exchange.publicWsClient.subscribeFundingRates(supportedCoins, fundingHandler);
			exchange.publicWsClient.subscribeMarkPrice(supportedCoins, markHandler);
		}
	}

	private Consumer<BookTickerPatch> createInitBookHandler(ExchangeName exchangeName) {
		return tickerPatch -> bookTickers.compute(
						exchangeName, tickerPatch.coin(), (k, v) -> {
							if (v == null) return null;
							if (tickerPatch.bidSize() != null) v.bidSize = tickerPatch.bidSize();
							if (tickerPatch.bidPrice() != null) v.bidPrice = tickerPatch.bidPrice();
							if (tickerPatch.askPrice() != null) v.askPrice = tickerPatch.askPrice();
							if (tickerPatch.askSize() != null) v.askSize = tickerPatch.askSize();
							v.timestamp = tickerPatch.timestamp();
							tryMarkComplete(exchangeName, tickerPatch.coin(), BIT_BOOK, isBookComplete(v));
							return v;
						}
		);
	}

	private Consumer<FundingRatePatch> createInitFundingHandler(ExchangeName exchangeName) {
		return ratePatch -> fundingRates.compute(
						exchangeName, ratePatch.coin(), (k, v) -> {
							if (v == null) return null;
							if (ratePatch.rate() != null) v.rate = ratePatch.rate();
							if (ratePatch.settlement() != null) v.settlement = ratePatch.settlement();
							v.timestamp = ratePatch.timestamp();
							tryMarkComplete(exchangeName, ratePatch.coin(), BIT_FUNDING, isFundingComplete(v));
							return v;
						}
		);
	}

	private Consumer<MarkPricePatch> createInitMarkHandler(ExchangeName exchangeName) {
		return markPricePatch -> markPrices.compute(
						exchangeName, markPricePatch.coin(), (k, v) -> {
							if (v == null) return null;
							v.price = markPricePatch.price();
							v.timestamp = markPricePatch.timestamp();
							tryMarkComplete(exchangeName, markPricePatch.coin(), BIT_MARK, isMarkComplete(v));
							return v;
						}
		);
	}

	private Consumer<BookTickerPatch> createSteadyBookHandler(ExchangeName exchangeName) {
		return tickerPatch -> bookTickers.compute(
						exchangeName, tickerPatch.coin(), (k, v) -> {
							if (v == null) return null;
							if (tickerPatch.bidSize() != null) v.bidSize = tickerPatch.bidSize();
							if (tickerPatch.bidPrice() != null) v.bidPrice = tickerPatch.bidPrice();
							if (tickerPatch.askPrice() != null) v.askPrice = tickerPatch.askPrice();
							if (tickerPatch.askSize() != null) v.askSize = tickerPatch.askSize();
							v.timestamp = tickerPatch.timestamp();
							return v;
						}
		);
	}

	private Consumer<FundingRatePatch> createSteadyFundingHandler(ExchangeName exchangeName) {
		return ratePatch -> fundingRates.compute(
						exchangeName, ratePatch.coin(), (k, v) -> {
							if (v == null) return null;
							if (ratePatch.rate() != null) v.rate = ratePatch.rate();
							if (ratePatch.settlement() != null) v.settlement = ratePatch.settlement();
							v.timestamp = ratePatch.timestamp();
							return v;
						}
		);
	}

	private Consumer<MarkPricePatch> createSteadyMarkHandler(ExchangeName exchangeName) {
		return markPricePatch -> markPrices.compute(
						exchangeName, markPricePatch.coin(), (k, v) -> {
							if (v == null) return null;
							v.price = markPricePatch.price();
							v.timestamp = markPricePatch.timestamp();
							return v;
						}
		);
	}

	private void switchToSteadyStateHandlers() {
		for (Map.Entry<BaseExchange, Set<String>> entry : availableCoinsByExchange.entrySet()) {
			BaseExchange exchange = entry.getKey();
			List<String> subscribedCoins = entry.getValue().stream().toList();
			if (subscribedCoins.isEmpty()) continue;

			exchange.publicWsClient.subscribeBookTicker(subscribedCoins, createSteadyBookHandler(exchange.name));
			exchange.publicWsClient.subscribeFundingRates(subscribedCoins, createSteadyFundingHandler(exchange.name));
			exchange.publicWsClient.subscribeMarkPrice(subscribedCoins, createSteadyMarkHandler(exchange.name));

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

	private void tryMarkComplete(ExchangeName exchangeName, String coin, int bit, boolean isComplete) {
		if (!isComplete) return;

		initStateBits.compute(
						exchangeName, coin, (k, bits) -> {
							if (bits == null || (bits & bit) != 0) return bits;

							int updated = bits | bit;
							if (initPendingSignals.decrementAndGet() == 0) {
								initDataReady.complete(null);
							}
							return updated;
						}
		);
	}

	private void dropInitTracking(ExchangeName exchangeName, String coin) {
		initStateBits.compute(
						exchangeName, coin, (k, bits) -> {
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

	private boolean isBookComplete(BookTicker ticker) {
		return ticker.bidPrice > 0 &&
					 ticker.bidSize > 0 &&
					 ticker.askPrice > 0 &&
					 ticker.askSize > 0 &&
					 ticker.timestamp != Instant.EPOCH;
	}

	private boolean isFundingComplete(FundingRate rate) {
		return rate.settlement != null && rate.settlement != Instant.EPOCH;
	}

	private boolean isMarkComplete(MarkPrice price) {
		return price.price > 0 && price.timestamp != Instant.EPOCH;
	}

	private void forgetCoinExchange(String coin, ExchangeName name) {
		BaseExchange exchange = Instances.getExchange(name);
		dropInitTracking(name, coin);

		Set<ExchangeName> available = availableExchangesByCoin.get(coin);
		if (available != null) available.remove(name);

		Set<String> subscribedCoins = availableCoinsByExchange.get(exchange);
		if (subscribedCoins != null) subscribedCoins.remove(coin);

		exchange.publicWsClient.unsubscribeCoin(coin);

		fundingRates.remove(name, coin);
		bookTickers.remove(name, coin);
		markPrices.remove(name, coin);
		fees.remove(name, coin);
		feesFutures.remove(name, coin);
	}

	private void forgetCoin(String coin) {
		Set<ExchangeName> available = availableExchangesByCoin.get(coin);
		if (available != null) {
			for (ExchangeName name : available) {
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
}
