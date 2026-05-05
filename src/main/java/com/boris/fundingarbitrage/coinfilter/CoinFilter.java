package com.boris.fundingarbitrage.coinfilter;

import com.boris.fundingarbitrage.exchange.BaseExchange;
import com.boris.fundingarbitrage.exchange.publichttp.FuturesPublicOnePullData;
import com.boris.fundingarbitrage.exchange.publichttp.FuturesTradingState;
import com.boris.fundingarbitrage.exchange.publichttp.SpotPublicOnePullData;
import com.boris.fundingarbitrage.model.contract.Fees;
import com.boris.fundingarbitrage.model.contract.Mark;
import com.boris.fundingarbitrage.model.exchange.constantdata.ConstantData;
import com.boris.fundingarbitrage.model.exchange.constantdata.FuturesConstantData;
import com.boris.fundingarbitrage.model.exchange.constantdata.SpotConstantData;
import com.boris.fundingarbitrage.model.exchange.snapshot.FuturesSnapshot;
import com.boris.fundingarbitrage.model.exchange.snapshot.Snapshot;
import com.boris.fundingarbitrage.model.exchange.snapshot.SpotSnapshot;
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
import java.util.concurrent.TimeUnit;

public class CoinFilter {
	private final Set<String> coins;
	private final CoinFilterConfig config;
	private final Set<BaseExchange> exchanges;

	private final CoinExchangeSupport availableSupport = new CoinExchangeSupport();
	private final ExchangeCoinMap<Boolean> presentOnFutures = new ExchangeCoinMap<>();
	private final ExchangeCoinMap<Boolean> presentOnSpot = new ExchangeCoinMap<>();
	private final ExchangeCoinMap<BigDecimal> futuresTradingVolumeMap = new ExchangeCoinMap<>();
	private final ExchangeCoinMap<FuturesTradingState> futuresTradingStatesMap = new ExchangeCoinMap<>();
	private final ExchangeCoinMap<BigDecimal> spotTradingVolumeMap = new ExchangeCoinMap<>();

	private final ExchangeCoinMap<SpotSnapshot> spotSnapshotsMap = new ExchangeCoinMap<>();
	private final ExchangeCoinMap<FuturesSnapshot> futuresSnapshotsMap = new ExchangeCoinMap<>();
	private final ExchangeCoinMap<SpotConstantData> spotConstantDataMap = new ExchangeCoinMap<>();
	private final ExchangeCoinMap<FuturesConstantData> futuresConstantDataMap = new ExchangeCoinMap<>();

	public CoinFilter(Set<String> coins, CoinFilterConfig config, Set<BaseExchange> exchanges) {
		if (coins.isEmpty()) throw new IllegalArgumentException("No coins provided");

		this.coins = coins;
		this.config = config;
		this.exchanges = exchanges;
	}

	private <T> CompletableFuture<T> withTimeOut(CompletableFuture<T> future, String name) {
		return future.orTimeout(10, TimeUnit.SECONDS).exceptionally(t -> {
			Logger.error("Failed to get " + name + ": " + t.getMessage());
			throw new RuntimeException(t);
		});
	}

	public CompletableFuture<Void> fetchData(BaseExchange exchange) {
		CompletableFuture<CoinVector<FuturesPublicOnePullData>> futuresOnePullDataFuture =
						withTimeOut(
										exchange.publicHttpClient().getFuturesOnePullData(coins),
										"futures one pull " + exchange.name()
						);

		CompletableFuture<CoinVector<SpotPublicOnePullData>> spotOnePullDataFuture =
						withTimeOut(exchange.publicHttpClient().getSpotOnePullData(coins), "spot one pull " + exchange.name());

		CompletableFuture<CoinVector<Fees>> futuresFeesFuture =
						withTimeOut(exchange.privateHttpClient().getFutureTradingFees(coins), "futures fees " + exchange.name());

		CompletableFuture<CoinVector<Fees>> spotFeesFuture =
						withTimeOut(exchange.privateHttpClient().getSpotTradingFees(coins), "spot fees " + exchange.name());

		return CompletableFuture.allOf(futuresOnePullDataFuture, futuresFeesFuture, spotOnePullDataFuture, spotFeesFuture)
						.thenRun(() -> {
							CoinVector<FuturesPublicOnePullData> futuresOpdVector = futuresOnePullDataFuture.join();
							CoinVector<SpotPublicOnePullData> spotOpdVector = spotOnePullDataFuture.join();
							CoinVector<Fees> ffVector = futuresFeesFuture.join();
							CoinVector<Fees> sfVector = spotFeesFuture.join();

							for (Map.Entry<String, FuturesPublicOnePullData> entry : futuresOpdVector.entrySet()) {
								String coin = entry.getKey();
								FuturesPublicOnePullData data = entry.getValue();
								Fees fees = ffVector.get(coin);
								if (fees == null) continue;
								presentOnFutures.put(exchange, coin, true);
								availableSupport.addSupport(coin, exchange);

								FuturesSnapshot snapshot = new FuturesSnapshot(
												data.ticker(),
												data.fundingRate(),
												new Mark(data.ticker().askPrice(), Instant.now())
								);
								FuturesConstantData constantData = new FuturesConstantData(
												data.lotSize(),
												fees,
												data.fundingInterval()
								);
								futuresSnapshotsMap.put(exchange, coin, snapshot);
								futuresConstantDataMap.put(exchange, coin, constantData);
								futuresTradingVolumeMap.put(exchange, coin, data.volume24h());
								futuresTradingStatesMap.put(exchange, coin, data.tradingState());
							}

							for (Map.Entry<String, SpotPublicOnePullData> entry : spotOpdVector.entrySet()) {
								String coin = entry.getKey();
								SpotPublicOnePullData data = entry.getValue();
								Fees fees = sfVector.get(coin);
								if (fees == null) continue;
								presentOnSpot.put(exchange, coin, true);
								availableSupport.addSupport(coin, exchange);

								SpotSnapshot snapshot = new SpotSnapshot(data.ticker());
								SpotConstantData constantData = new SpotConstantData(data.lotSize(), fees);

								spotSnapshotsMap.put(exchange, coin, snapshot);
								spotConstantDataMap.put(exchange, coin, constantData);
								spotTradingVolumeMap.put(exchange, coin, data.volume24h());
							}
						});
	}


	private CompletableFuture<Void> fetchData() {
		for (String coin : coins) availableSupport.addCoin(coin);
		for (BaseExchange ex : exchanges) {
			availableSupport.addExchange(ex);
			for (String coin : coins) availableSupport.addSupport(coin, ex);
		}

		List<CompletableFuture<Void>> futures = new ArrayList<>();
		for (BaseExchange exchange : exchanges) futures.add(fetchData(exchange));

		return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
	}

	private void filterCoins() {
		for (Map.Entry<String, Set<BaseExchange>> entry : availableSupport.coinEntries()) {
			String coin = entry.getKey();
			Set<BaseExchange> exchanges = entry.getValue();

			for (BaseExchange ex : exchanges) {
				if (Boolean.TRUE.equals(presentOnFutures.get(ex, coin))) {
					String excludeFuturesMsg = getExcludeFuturesMessage(ex, coin);
					if (excludeFuturesMsg != null) forgetFuturesCoinExchange(coin, ex);
				}

				if (Boolean.TRUE.equals(presentOnSpot.get(ex, coin))) {
					String excludedSpotMsg = getExcludeSpotMessage(ex, coin);
					if (excludedSpotMsg != null) forgetSpotCoinExchange(coin, ex);
				}

				if (!Boolean.TRUE.equals(presentOnFutures.get(ex, coin)) && !Boolean.TRUE.equals(presentOnSpot.get(ex, coin))) {
					forgetCoinExchange(coin, ex);
					exchanges.remove(ex);
				}
			}

			if (exchanges.isEmpty()) {
				Logger.warn("No exchanges left supporting " + coin);
			}
		}
	}

	public CompletableFuture<CoinFilterResult> filterAsync() {
		return fetchData()
						.thenRun(this::filterCoins)
						.thenApply(_ -> new CoinFilterResult(
										availableSupport,
										futuresConstantDataMap,
										spotConstantDataMap,
										futuresSnapshotsMap,
										spotSnapshotsMap,
										presentOnFutures,
										presentOnSpot
						));
	}

	private void forgetCoinExchange(String coin, BaseExchange exchange) {
		availableSupport.removeSupport(coin, exchange);
		futuresConstantDataMap.remove(exchange, coin);
		spotConstantDataMap.remove(exchange, coin);
		spotSnapshotsMap.remove(exchange, coin);
		spotConstantDataMap.remove(exchange, coin);
	}

	private void forgetFuturesCoinExchange(String coin, BaseExchange exchange) {
		futuresTradingVolumeMap.remove(exchange, coin);
		futuresTradingStatesMap.remove(exchange, coin);
		futuresConstantDataMap.remove(exchange, coin);
		futuresSnapshotsMap.remove(exchange, coin);
		presentOnFutures.put(exchange, coin, false);
	}

	private void forgetSpotCoinExchange(String coin, BaseExchange exchange) {
		spotTradingVolumeMap.remove(exchange, coin);
		spotSnapshotsMap.remove(exchange, coin);
		futuresConstantDataMap.remove(exchange, coin);
		presentOnSpot.put(exchange, coin, false);
	}

	private String getExcludeMessage(
					BaseExchange ex,
					String coin,
					ExchangeCoinMap<BigDecimal> volumeMap,
					ExchangeCoinMap<? extends Snapshot> snapshotMap,
					ExchangeCoinMap<? extends ConstantData> cdMap
	) {
		BigDecimal volume = volumeMap.get(ex, coin);
		if (volume.compareTo(config.min24hVolumeUsdt()) < 0) return "Volume not enough: " + volume;

		BigDecimal ask = snapshotMap.get(ex, coin).askPrice();
		BigDecimal minPriceStep = cdMap.get(ex, coin).lotSize().multiply(ask);
		if (minPriceStep.compareTo(config.maxAffordablePrice()) > 0)
			return "Price too high; min price step: " + minPriceStep;

		return null;
	}

	private String getExcludeFuturesMessage(BaseExchange ex, String coin) {
		FuturesTradingState tradingState = futuresTradingStatesMap.get(ex, coin);
		if (tradingState != FuturesTradingState.TRADING) return "Not trading";

		return getExcludeMessage(
						ex,
						coin,
						futuresTradingVolumeMap,
						futuresSnapshotsMap,
						futuresConstantDataMap
		);
	}

	private String getExcludeSpotMessage(BaseExchange ex, String coin) {
		return getExcludeMessage(
						ex,
						coin,
						spotTradingVolumeMap,
						spotSnapshotsMap,
						spotConstantDataMap
		);
	}
}
