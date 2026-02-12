package com.boris.fundingarbitrage.monitor;

import com.boris.fundingarbitrage.exchange.BaseExchange;
import com.boris.fundingarbitrage.exchange.Instances;
import com.boris.fundingarbitrage.model.contract.BookTicker;
import com.boris.fundingarbitrage.model.contract.Fees;
import com.boris.fundingarbitrage.model.contract.FundingRate;
import com.boris.fundingarbitrage.model.contract.MarkPrice;
import com.boris.fundingarbitrage.model.exchange.ExchangeName;
import com.boris.fundingarbitrage.util.coinvector.CoinVector;
import com.boris.fundingarbitrage.util.logger.Logger;
import lombok.Getter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class CoinMonitor {
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

	public CoinMonitor(List<String> coins) {
		this.coins = coins;

		this.initFuture = CompletableFuture.runAsync(() -> {
			initAvailableExchanges();
			Logger.logCoinVector(availableExchanges);
			fillEmptyData();
			subscribeData();

			try {
				Thread.sleep(waitForDataSecond * 1000L);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new RuntimeException("Coin monitor init interrupted", e);
			}

			checkDataCompleteness();
		});
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
			subscribeBookTickers(exchange, supportedCoins);
			subscribeFundingRates(exchange, supportedCoins);
			subscribeMarkPrices(exchange, supportedCoins);
		}
	}

	private void subscribeBookTickers(BaseExchange exchange, List<String> coins) {
		exchange.publicWsClient.subscribeBookTicker(
						coins, tickerPatch -> {
							bookTickers.compute(
											exchange.name, tickerPatch.coin(), (k, v) -> {
												if (tickerPatch.bidSize() != null) v.bidSize = tickerPatch.bidSize();
												if (tickerPatch.bidPrice() != null) v.bidPrice = tickerPatch.bidPrice();
												if (tickerPatch.askPrice() != null) v.askPrice = tickerPatch.askPrice();
												if (tickerPatch.askSize() != null) v.askSize = tickerPatch.askSize();
												v.timestamp = tickerPatch.timestamp();
												return v;
											}
							);
						}
		);
	}

	private void subscribeFundingRates(BaseExchange exchange, List<String> coins) {
		exchange.publicWsClient.subscribeFundingRates(
						coins, ratePatch -> {
							fundingRates.compute(
											exchange.name, ratePatch.coin(), (k, v) -> {
												if (ratePatch.rate() != null) v.rate = ratePatch.rate();
												if (ratePatch.settlement() != null) v.settlement = ratePatch.settlement();
												v.timestamp = ratePatch.timestamp();
												return v;
											}
							);
						}
		);
	}

	private void subscribeMarkPrices(BaseExchange exchange, List<String> coins) {
		exchange.publicWsClient.subscribeMarkPrice(
						coins, markPricePatch -> {
							markPrices.compute(
											exchange.name, markPricePatch.coin(), (k, v) -> {
												v.price = markPricePatch.price();
												v.timestamp = markPricePatch.timestamp();
												return v;
											}
							);
						}
		);
	}

	private void forgetCoinExchange(String coin, BaseExchange exchange) {
		Set<ExchangeName> available = availableExchanges.get(coin);
		if (available != null) available.remove(exchange.name);

		exchange.publicWsClient.unsubscribeCoin(coin);

		fundingRates.remove(exchange.name, coin);
		bookTickers.remove(exchange.name, coin);
		markPrices.remove(exchange.name, coin);
	}

	private void forgetCoinExchange(String coin, ExchangeName name) {
		BaseExchange exchange = Instances.getExchange(name);

		Set<ExchangeName> available = availableExchanges.get(coin);
		if (available != null) available.remove(name);

		exchange.publicWsClient.unsubscribeCoin(coin);

		fundingRates.remove(name, coin);
		bookTickers.remove(name, coin);
		markPrices.remove(name, coin);
	}

	public void shutdown() {
		for (BaseExchange exchange : usedExchanges) {
			exchange.publicWsClient.close();
		}
	}
}
