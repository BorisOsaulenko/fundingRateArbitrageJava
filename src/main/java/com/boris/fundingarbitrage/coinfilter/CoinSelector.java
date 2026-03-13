package com.boris.fundingarbitrage.coinfilter;

import com.boris.fundingarbitrage.exchange.BaseExchange;
import com.boris.fundingarbitrage.exchange.Instances;
import com.boris.fundingarbitrage.exchange.publichttp.PublicOnePullData;
import com.boris.fundingarbitrage.monitor.ExchangeCoinMap;
import com.boris.fundingarbitrage.util.coinvector.CoinVector;
import com.boris.fundingarbitrage.util.logger.Logger;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class CoinSelector {
	private final Set<String> coins;
	private final CoinFilterConfig config;

	private final CoinVector<Set<BaseExchange>> availableExchangesByCoin = new CoinVector<>();
	private final Map<BaseExchange, Set<String>> availableCoinsByExchange = new ConcurrentHashMap<>();
	private final ExchangeCoinMap<BigDecimal> lotSizes = new ExchangeCoinMap<>();
	private final ExchangeCoinMap<Integer> fundingIntervals = new ExchangeCoinMap<>();

	public CoinSelector(Set<String> coins, CoinFilterConfig config) {
		if (coins.isEmpty()) throw new IllegalArgumentException("No coins provided");

		this.coins = coins;
		this.config = config;
	}

	public CoinFilterResult filterSync() {
		for (String coin : coins) {
			availableExchangesByCoin.put(coin, ConcurrentHashMap.newKeySet());
		}

		List<CompletableFuture<Void>> futures = new ArrayList<>();
		for (BaseExchange exchange : Instances.getExchangeArray()) {
			availableCoinsByExchange.put(exchange, ConcurrentHashMap.newKeySet());
			CompletableFuture<Void> future = exchange.publicHttpClient.getOnePullData(coins).thenAccept(coinsVect -> {
				coinsVect.forEach((coin, data) -> {
					String excludedMsg = shouldExcludeCoin(data);
					if (excludedMsg != null) {
						Logger.log("Excluding " + coin + " - " + exchange.name + " from monitoring: " + excludedMsg);
						forgetCoinExchange(coin, exchange);
						return;
					}
					availableExchangesByCoin.get(coin).add(exchange);
					availableCoinsByExchange.get(exchange).add(coin);
					lotSizes.put(exchange, coin, data.lotSize());
					fundingIntervals.put(exchange, coin, data.fundingInterval());
				});
			}).exceptionally(err -> {
				Logger.log("Failed to fetch available coins for " + exchange.name + ": " + err.getMessage());
				return null;
			});
			futures.add(future);
		}

		CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
		clearCoinsWithInsufficientExchanges();

		return new CoinFilterResult(availableExchangesByCoin, availableCoinsByExchange, lotSizes, fundingIntervals);
	}

	private String shouldExcludeCoin(PublicOnePullData data) {
		if (data.isEmpty()) {
			return "Coin does not exist";
		}
		if (data.volume24h().compareTo(config.min24hVolumeUsdt()) < 0) {
			return "Volume not enough: " + data.volume24h();
		}

		BigDecimal ask = data.bookTicker().askPrice();
		BigDecimal minPriceStep = data.lotSize().multiply(ask);
		if (minPriceStep.compareTo(config.maxAffordablePrice()) > 0) {
			return "Price too high; min price step: " + minPriceStep;
		}

		return null;
	}

	private void clearCoinsWithInsufficientExchanges() {
		for (String coin : availableExchangesByCoin.keySet()) {
			Set<BaseExchange> exchanges = availableExchangesByCoin.get(coin);
			if (exchanges == null || exchanges.size() < 2) {
				Logger.warn("Not enough exchanges support " + coin + ". Removing from monitoring.");
				forgetCoin(coin);
			}
		}

		for (BaseExchange exchange : Instances.getExchangesSet()) {
			Set<String> coins = availableCoinsByExchange.get(exchange);
			if (coins == null || coins.isEmpty()) {
				Logger.warn("No coins left for " + exchange.name + ". Removing from monitoring.");
				forgetExchange(exchange);
			}
		}
	}

	private void forgetCoinExchange(String coin, BaseExchange exchange) {
		Set<BaseExchange> available = availableExchangesByCoin.get(coin);
		if (available != null) available.remove(exchange);

		Set<String> subscribedCoins = availableCoinsByExchange.get(exchange);
		if (subscribedCoins != null) subscribedCoins.remove(coin);
	}

	private void forgetCoin(String coin) {
		Set<BaseExchange> available = availableExchangesByCoin.get(coin);
		if (available != null) {
			for (BaseExchange exchange : available) {
				forgetCoinExchange(coin, exchange);
			}
		}

		availableExchangesByCoin.remove(coin);
	}

	private void forgetExchange(BaseExchange exchange) {
		Set<String> subscribedCoins = availableCoinsByExchange.get(exchange);
		if (subscribedCoins != null) {
			for (String coin : subscribedCoins) {
				forgetCoinExchange(coin, exchange);
			}
		}
	}
}
