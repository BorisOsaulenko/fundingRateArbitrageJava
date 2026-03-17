package com.boris.fundingarbitrage.coinfilter;

import com.boris.fundingarbitrage.exchange.BaseExchange;
import com.boris.fundingarbitrage.exchange.Instances;
import com.boris.fundingarbitrage.exchange.publichttp.PublicOnePullData;
import com.boris.fundingarbitrage.logic.ExchangePair;
import com.boris.fundingarbitrage.model.arbitrage.ArbitrageConstantData;
import com.boris.fundingarbitrage.model.arbitrage.ArbitrageData;
import com.boris.fundingarbitrage.model.arbitrage.ArbitrageSnapshot;
import com.boris.fundingarbitrage.model.contract.Fees;
import com.boris.fundingarbitrage.model.contract.MarkPrice;
import com.boris.fundingarbitrage.model.exchange.ExchangeConstantData;
import com.boris.fundingarbitrage.model.exchange.ExchangeName;
import com.boris.fundingarbitrage.model.exchange.ExchangeSnapshot;
import com.boris.fundingarbitrage.monitor.ExchangeCoinMap;
import com.boris.fundingarbitrage.util.coinvector.CoinVector;
import com.boris.fundingarbitrage.util.logger.Logger;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class CoinFilter {
	private final Set<String> coins;
	private final CoinFilterConfig config;

	private final CoinVector<Set<BaseExchange>> availableExchangesByCoin = new CoinVector<>();
	private final Map<BaseExchange, Set<String>> availableCoinsByExchange = new ConcurrentHashMap<>();
	private final ExchangeCoinMap<BigDecimal> tradingVolumeMap = new ExchangeCoinMap<>();

	private final ExchangeCoinMap<ExchangeSnapshot> snapshotsMap = new ExchangeCoinMap<>();
	private final ExchangeCoinMap<ExchangeConstantData> constantDataMap = new ExchangeCoinMap<>();

	public CoinFilter(Set<String> coins, CoinFilterConfig config) {
		if (coins.isEmpty()) throw new IllegalArgumentException("No coins provided");

		this.coins = coins;
		this.config = config;
	}

	private void ensureSameCoins(CoinVector<?> v1, CoinVector<?> v2, ExchangeName exName) {
		for (String coin : v1.keySet()) {
			if (!v2.containsKey(coin))
				v1.remove(coin);
		}

		for (String coin : v2.keySet()) {
			if (!v1.containsKey(coin))
				v2.remove(coin);
		}
	}

	private CompletableFuture<Void> fetchData(BaseExchange exchange) {
		CompletableFuture<CoinVector<PublicOnePullData>> onePullDataFuture =
						exchange.publicHttpClient.getOnePullData(coins)
										.orTimeout(10, TimeUnit.SECONDS)
										.exceptionally(t -> {
											Logger.error("Failed to get public one pull data for " + exchange.name + ": " + t.getMessage());
											throw new RuntimeException(t);
										});

		CompletableFuture<CoinVector<Fees>> feesFuture =
						exchange.privateHttpClient.getTradingFees(coins)
										.orTimeout(10, TimeUnit.SECONDS)
										.exceptionally(t -> {
											Logger.error("Failed to get trading fees for " + exchange.name + ": " + t.getMessage());
											throw new RuntimeException(t);
										});

		return CompletableFuture.allOf(onePullDataFuture, feesFuture).thenRun(() -> {
			CoinVector<PublicOnePullData> opdVector = onePullDataFuture.join();
			CoinVector<Fees> fVector = feesFuture.join();

			ensureSameCoins(opdVector, fVector, exchange.name);
			for (String coin : opdVector.keySet()) {
				PublicOnePullData data = opdVector.get(coin);
				Fees fees = fVector.get(coin);
				assert data != null && fees != null;

				availableExchangesByCoin.computeIfAbsent(coin, _ -> ConcurrentHashMap.newKeySet()).add(exchange);
				availableCoinsByExchange.computeIfAbsent(exchange, _ -> ConcurrentHashMap.newKeySet()).add(coin);

				ExchangeSnapshot snapshot = new ExchangeSnapshot(
								data.ticker(),
								data.fundingRate(),
								new MarkPrice(data.ticker().askPrice(), Instant.now())
				);
				ExchangeConstantData constantData = new ExchangeConstantData(data.lotSize(), fees, data.fundingInterval());

				snapshotsMap.put(exchange, coin, snapshot);
				constantDataMap.put(exchange, coin, constantData);
				tradingVolumeMap.put(exchange, coin, data.volume24h());
			}
		});
	}

	private CompletableFuture<Void> fetchData() {
		for (String coin : coins) availableExchangesByCoin.put(coin, ConcurrentHashMap.newKeySet());
		for (BaseExchange ex : Instances.getExchangeArray())
			availableCoinsByExchange.put(ex, ConcurrentHashMap.newKeySet());

		List<CompletableFuture<Void>> futures = new ArrayList<>();
		for (BaseExchange exchange : Instances.getExchangeArray()) {
			futures.add(fetchData(exchange));
		}

		return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
	}

	private void filterCoins() {
		for (Map.Entry<String, Set<BaseExchange>> entry : availableExchangesByCoin.entrySet()) {
			String coin = entry.getKey();
			Set<BaseExchange> exchanges = entry.getValue();

			for (BaseExchange ex : exchanges) {
				String excludeMsg = getExcludeMessage(ex, coin);
				if (excludeMsg != null) {
					Logger.warn("Excluding " + coin + " - " + ex.name + " from monitoring: " + excludeMsg);
					exchanges.remove(ex);
					forgetCoinExchange(coin, ex);
				}
			}

			if (exchanges.size() < 2) {
				Logger.warn("Not enough exchanges support " + coin + ". Removing from monitoring.");
				for (var ex : exchanges) forgetCoinExchange(coin, ex);
			}
		}
	}

	private void capCoinsToMaxAmount() {
		if (config.maxCoinCap() >= availableExchangesByCoin.size()) return;
		CoinVector<ArbitrageData> bestArbDataPerCoin = availableExchangesByCoin.transform((exchanges, coin) -> {
			if (exchanges.size() < 2) Logger.error(coin);
			ArbitrageData bestData = null;

			for (BaseExchange longEx : exchanges) {
				for (BaseExchange shortEx : exchanges) {
					if (longEx == shortEx) continue;

					ArbitrageData coinArbData = getArbData(new ExchangePair(longEx, shortEx), coin);
					if (bestData == null || config.coinsComparator().compare(coinArbData, bestData) > 0)
						bestData = coinArbData;
				}
			}

			return bestData;
		});

		List<Map.Entry<String, ArbitrageData>> worseCoins = bestArbDataPerCoin
						.sortDesc(config.coinsComparator())
						.subList(config.maxCoinCap(), bestArbDataPerCoin.size());

		for (var entry : worseCoins) {
			String coin = entry.getKey();
			Set<BaseExchange> available = availableExchangesByCoin.get(coin);
			assert available != null;
			for (BaseExchange ex : available) forgetCoinExchange(coin, ex);
		}
	}

	private ArbitrageData getArbData(ExchangePair exchanges, String coin) {
		ExchangeConstantData longConstantData = constantDataMap.get(exchanges.longEx(), coin);
		ExchangeConstantData shortConstantData = constantDataMap.get(exchanges.shortEx(), coin);
		ArbitrageConstantData constantData = new ArbitrageConstantData(longConstantData, shortConstantData);

		ExchangeSnapshot longSn = snapshotsMap.get(exchanges.longEx(), coin);
		ExchangeSnapshot shortSn = snapshotsMap.get(exchanges.shortEx(), coin);
		return new ArbitrageData(new ArbitrageSnapshot(longSn, shortSn), constantData);
	}

	public CompletableFuture<CoinFilterResult> filterAsync() {
		return fetchData()
						.thenRun(this::filterCoins)
						.thenRun(this::capCoinsToMaxAmount)
						.thenApply(_ -> new CoinFilterResult(availableExchangesByCoin, availableCoinsByExchange, constantDataMap));
	}

	private void forgetCoinExchange(String coin, BaseExchange exchange) {
		Set<BaseExchange> available = availableExchangesByCoin.get(coin);
		if (available != null) {
			available.remove(exchange);
			if (available.size() < 2) availableExchangesByCoin.remove(coin);
		}

		Set<String> subscribedCoins = availableCoinsByExchange.get(exchange);
		if (subscribedCoins != null) {
			subscribedCoins.remove(coin);
			if (subscribedCoins.isEmpty()) availableCoinsByExchange.remove(exchange);
		}
	}

	private String getExcludeMessage(BaseExchange ex, String coin) {
		BigDecimal volume = tradingVolumeMap.get(ex, coin);
		if (volume.compareTo(config.min24hVolumeUsdt()) < 0) return "Volume not enough: " + volume;

		BigDecimal ask = snapshotsMap.get(ex, coin).bookTicker().askPrice();
		BigDecimal minPriceStep = constantDataMap.get(ex, coin).lotSize().multiply(ask);
		if (minPriceStep.compareTo(config.maxAffordablePrice()) > 0) {
			return "Price too high; min price step: " + minPriceStep;
		}

		return null;
	}
}
