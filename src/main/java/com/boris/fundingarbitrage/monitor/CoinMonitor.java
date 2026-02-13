package com.boris.fundingarbitrage.monitor;

import com.boris.fundingarbitrage.exchange.BaseExchange;
import com.boris.fundingarbitrage.exchange.Instances;
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

	private final List<String> coins;
	private final int waitForDataSecond = 60;
	private final ExchangeCoinMap<FundingRate> fundingRates = new ExchangeCoinMap<>();
	private final ExchangeCoinMap<BookTicker> bookTickers = new ExchangeCoinMap<>();
	private final ExchangeCoinMap<MarkPrice> markPrices = new ExchangeCoinMap<>();
	private final CoinVector<Set<ExchangeName>> availableExchanges = new CoinVector<>();
	private final Set<BaseExchange> usedExchanges = ConcurrentHashMap.newKeySet();
	@Getter
	private final CompletableFuture<Void> initFuture;
	private final ExchangeCoinMap<Fees> fees = new ExchangeCoinMap<>();
	private final ExchangeCoinMap<CompletableFuture<Void>> feesFutures = new ExchangeCoinMap<>();
	private final ExchangeCoinMap<Integer> initStateBits = new ExchangeCoinMap<>();
	private final AtomicInteger initPendingSignals = new AtomicInteger(0);
	private final CompletableFuture<Void> initDataReady = new CompletableFuture<>();
	private final ConcurrentHashMap<ExchangeName, Set<String>> subscribedCoinsByExchange = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<ExchangeName, Consumer<BookTickerPatch>> initBookHandlers = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<ExchangeName, Consumer<FundingRatePatch>> initFundingHandlers = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<ExchangeName, Consumer<MarkPricePatch>> initMarkHandlers = new ConcurrentHashMap<>();

	public CoinMonitor(List<String> coins) {
		this.coins = coins;

		this.initFuture = CompletableFuture.runAsync(() -> {
			initAvailableExchanges();
			fillEmptyData();
			initCompletionTracking();
			subscribeData();

			CompletableFuture<Void> timeout = CompletableFuture.runAsync(
							() -> {},
							CompletableFuture.delayedExecutor(waitForDataSecond, TimeUnit.SECONDS)
			);
			CompletableFuture.anyOf(initDataReady, timeout).join();
			if (!initDataReady.isDone()) {
				Logger.warn("Coin monitor init timed out after " +
										waitForDataSecond +
										"s. Falling back to completeness check.");
				checkDataCompleteness();
			}

			Logger.logCoinVector(availableExchanges);
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
			if (entry.value().bidPrice == 0 ||
					entry.value().bidSize == 0 ||
					entry.value().askPrice == 0 ||
					entry.value().askSize == 0 ||
					entry.value().timestamp == Instant.EPOCH) {
				Logger.warn("Book ticker data is incomplete for " + entry.coin() + ": " + entry.value());
				forgetCoinExchange(entry.coin(), entry.name());
			}
		}

		for (var entry : fundingRates.entrySet()) {
			if (entry.value().settlement == null || entry.value().settlement == Instant.EPOCH) {
				Logger.warn("Funding rate is incomplete for " + entry.coin() + ": " + entry.value());
				forgetCoinExchange(entry.coin(), entry.name());
			}
		}

		for (var entry : markPrices.entrySet()) {
			if (entry.value().price == 0 || entry.value().timestamp == Instant.EPOCH) {
				Logger.warn("Mark price is incomplete for " + entry.coin() + ": " + entry.value());
				forgetCoinExchange(entry.coin(), entry.name());
			}
		}
	}

	private void initAvailableExchanges() {
		List<CompletableFuture<Void>> futures = new ArrayList<>();
		for (String coin : coins) {
			availableExchanges.put(coin, ConcurrentHashMap.newKeySet());
			for (BaseExchange exchange : Instances.getExchangeArray()) {
				futures.add(exchange.publicHttpClient.checkCoinExists(coin).thenAccept(result -> {
					if (result) {
						availableExchanges.get(coin).add(exchange.name);
						usedExchanges.add(exchange);
						initFees(exchange, coin);
					}
				}));
			}
		}

		CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
	}

	private void fillEmptyData() {
		availableExchanges.forEach((coin, names) -> {
			for (ExchangeName exchangeName : names) {
				fundingRates.put(exchangeName, coin, FundingRate.empty());
				bookTickers.put(exchangeName, coin, BookTicker.empty());
				markPrices.put(exchangeName, coin, MarkPrice.empty());
			}
		});
	}

	private void initFees(BaseExchange exchange, String coin) {
		feesFutures.put(
						exchange.name, coin, exchange.privateHttpClient.getTradingFees(coin).thenAccept(fee -> {
							fees.put(exchange.name, coin, fee);
						}).exceptionally(ex -> {
							Logger.error("Failed to get fees for " + exchange.name + " " + coin + ": " + ex.getMessage());
							forgetCoinExchange(coin, exchange);
							return null;
						})
		);
	}

	private void subscribeData() {
		for (BaseExchange exchange : usedExchanges) {
			List<String> supportedCoins = availableExchanges
							.filter((exNames, _) -> exNames.contains(exchange.name))
							.keySet()
							.stream()
							.toList();
			if (supportedCoins.isEmpty()) continue;

			subscribedCoinsByExchange
							.computeIfAbsent(exchange.name, _ -> ConcurrentHashMap.newKeySet())
							.addAll(supportedCoins);

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
		for (BaseExchange exchange : usedExchanges) {
			List<String> subscribedCoins = getSubscribedCoins(exchange.name);
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

	private List<String> getSubscribedCoins(ExchangeName exchangeName) {
		Set<String> coins = subscribedCoinsByExchange.get(exchangeName);
		if (coins == null || coins.isEmpty()) return List.of();
		return List.copyOf(coins);
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

	private void forgetCoinExchange(String coin, BaseExchange exchange) {
		dropInitTracking(exchange.name, coin);

		Set<ExchangeName> available = availableExchanges.get(coin);
		if (available != null) available.remove(exchange.name);

		Set<String> subscribedCoins = subscribedCoinsByExchange.get(exchange.name);
		if (subscribedCoins != null) subscribedCoins.remove(coin);

		exchange.publicWsClient.unsubscribeCoin(coin);

		fundingRates.remove(exchange.name, coin);
		bookTickers.remove(exchange.name, coin);
		markPrices.remove(exchange.name, coin);
		fees.remove(exchange.name, coin);
		feesFutures.remove(exchange.name, coin);
	}

	private void forgetCoinExchange(String coin, ExchangeName name) {
		BaseExchange exchange = Instances.getExchange(name);
		dropInitTracking(name, coin);

		Set<ExchangeName> available = availableExchanges.get(coin);
		if (available != null) available.remove(name);

		Set<String> subscribedCoins = subscribedCoinsByExchange.get(name);
		if (subscribedCoins != null) subscribedCoins.remove(coin);

		exchange.publicWsClient.unsubscribeCoin(coin);

		fundingRates.remove(name, coin);
		bookTickers.remove(name, coin);
		markPrices.remove(name, coin);
		fees.remove(name, coin);
		feesFutures.remove(name, coin);
	}

	public void shutdown() {
		for (BaseExchange exchange : usedExchanges) {
			exchange.publicWsClient.close();
		}
	}
}
