package com.boris.fundingarbitrage.coinfilter;

import com.boris.fundingarbitrage.coinparser.ICoinSupplier;
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
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class CoinFilter {
	private final CoinFilterConfig config;
	private final Set<BaseExchange> exchanges;
	private final ICoinSupplier coinSupplier;

	private final CoinAvailabilityRecord availabilityRecord = new CoinAvailabilityRecord();
	private final ConstantDataRecord cdRecord = new ConstantDataRecord();
	private final ExchangeCoinMap<BigDecimal> futuresTradingVolumeMap = new ExchangeCoinMap<>();
	private final ExchangeCoinMap<FuturesTradingState> futuresTradingStatesMap = new ExchangeCoinMap<>();
	private final ExchangeCoinMap<BigDecimal> spotTradingVolumeMap = new ExchangeCoinMap<>();

	private final ExchangeCoinMap<SpotSnapshot> spotSnapshotsMap = new ExchangeCoinMap<>();
	private final ExchangeCoinMap<FuturesSnapshot> futuresSnapshotsMap = new ExchangeCoinMap<>();

	public CoinFilter(ICoinSupplier coinSupplier, CoinFilterConfig config, Set<BaseExchange> exchanges) {
		this.coinSupplier = coinSupplier;
		this.config = config;
		this.exchanges = exchanges;
	}

	private <T> CompletableFuture<T> withTimeOut(CompletableFuture<T> future, String name) {
		return future.orTimeout(10, TimeUnit.SECONDS).exceptionally(t -> {
			Logger.error("Failed to get " + name + ": " + t.getMessage());
			throw new RuntimeException(t);
		});
	}

	private CompletableFuture<Void> fetchData(BaseExchange exchange, Set<String> coins) {
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
								availabilityRecord.addSupportFutures(coin, exchange);

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
								cdRecord.addFutures(exchange, coin, constantData);
								futuresTradingVolumeMap.put(exchange, coin, data.volume24h());
								futuresTradingStatesMap.put(exchange, coin, data.tradingState());
							}

							for (Map.Entry<String, SpotPublicOnePullData> entry : spotOpdVector.entrySet()) {
								String coin = entry.getKey();
								SpotPublicOnePullData data = entry.getValue();
								Fees fees = sfVector.get(coin);
								if (fees == null) continue;
								availabilityRecord.addSupportSpot(coin, exchange);

								SpotSnapshot snapshot = new SpotSnapshot(data.ticker());
								SpotConstantData constantData = new SpotConstantData(data.lotSize(), fees);

								spotSnapshotsMap.put(exchange, coin, snapshot);
								cdRecord.addSpot(exchange, coin, constantData);
								spotTradingVolumeMap.put(exchange, coin, data.volume24h());
							}
						});
	}


	private CompletableFuture<Void> fetchData(Set<String> coins) {
		for (String coin : coins) availabilityRecord.addCoin(coin);
		for (BaseExchange ex : exchanges) availabilityRecord.addExchange(ex);

		List<CompletableFuture<Void>> futures = new ArrayList<>();
		for (BaseExchange exchange : exchanges) futures.add(fetchData(exchange, coins));

		return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
	}

	private void filterCoins() {
		for (String coin : new HashSet<>(availabilityRecord.getCoins())) {
			for (BaseExchange ex : exchanges) {
				if (availabilityRecord.isFutures(ex, coin)) {
					String excludeFuturesMsg = getExcludeFuturesMessage(ex, coin);
					if (excludeFuturesMsg != null) forgetFuturesCoinExchange(coin, ex);
				}

				if (availabilityRecord.isSpot(ex, coin)) {
					String excludedSpotMsg = getExcludeSpotMessage(ex, coin);
					if (excludedSpotMsg != null) forgetSpotCoinExchange(coin, ex);
				}
			}

			Set<BaseExchange> supportedExchanges = availabilityRecord.getExchanges(coin);
			if (supportedExchanges == null || supportedExchanges.isEmpty()) {
				Logger.warn("No exchanges left supporting " + coin);
			}
		}
	}

	public CompletableFuture<CoinFilterResult> filterAsync() {
		return coinSupplier.getCoinsAsync()
						.thenCompose(this::fetchData)
						.thenRun(this::filterCoins)
						.thenApply(_ -> new CoinFilterResult(
										availabilityRecord,
										cdRecord,
										futuresSnapshotsMap,
										spotSnapshotsMap
						));
	}

	private void forgetCoinExchange(String coin, BaseExchange exchange) {
		availabilityRecord.removeSupportSpot(coin, exchange);
		availabilityRecord.removeSupportFutures(coin, exchange);
		cdRecord.removeFutures(exchange, coin);
		cdRecord.removeSpot(exchange, coin);
		futuresSnapshotsMap.remove(exchange, coin);
		spotSnapshotsMap.remove(exchange, coin);
	}

	private void forgetFuturesCoinExchange(String coin, BaseExchange exchange) {
		futuresTradingVolumeMap.remove(exchange, coin);
		futuresTradingStatesMap.remove(exchange, coin);
		cdRecord.removeFutures(exchange, coin);
		futuresSnapshotsMap.remove(exchange, coin);
		availabilityRecord.removeSupportFutures(coin, exchange);
		if (!availabilityRecord.isSpot(exchange, coin)) forgetCoinExchange(coin, exchange);
	}

	private void forgetSpotCoinExchange(String coin, BaseExchange exchange) {
		spotTradingVolumeMap.remove(exchange, coin);
		spotSnapshotsMap.remove(exchange, coin);
		cdRecord.removeFutures(exchange, coin);
		availabilityRecord.removeSupportSpot(coin, exchange);
		if (!availabilityRecord.isFutures(exchange, coin)) forgetCoinExchange(coin, exchange);
	}

	private String getExcludeMessage(
					BaseExchange ex,
					String coin,
					ExchangeCoinMap<BigDecimal> volumeMap,
					ExchangeCoinMap<? extends Snapshot> snapshotMap,
					ConstantData cd
	) {
		BigDecimal volume = volumeMap.get(ex, coin);
		if (volume.compareTo(config.min24hVolumeUsdt()) < 0) return "Volume not enough: " + volume;

		BigDecimal ask = snapshotMap.get(ex, coin).askPrice();
		BigDecimal minPriceStep = cd.lotSize().multiply(ask);
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
						cdRecord.getFuturesConstantData(ex, coin)
		);
	}

	private String getExcludeSpotMessage(BaseExchange ex, String coin) {
		return getExcludeMessage(
						ex,
						coin,
						spotTradingVolumeMap,
						spotSnapshotsMap,
						cdRecord.getFuturesConstantData(ex, coin)
		);
	}
}
