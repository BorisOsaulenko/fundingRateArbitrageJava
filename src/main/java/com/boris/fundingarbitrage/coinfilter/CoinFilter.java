package com.boris.fundingarbitrage.coinfilter;

import com.boris.fundingarbitrage.exchange.BaseExchange;
import com.boris.fundingarbitrage.exchange.Instances;
import com.boris.fundingarbitrage.exchange.publichttp.FuturesPublicOnePullData;
import com.boris.fundingarbitrage.exchange.publichttp.FuturesTradingState;
import com.boris.fundingarbitrage.exchange.publichttp.SpotPublicOnePullData;
import com.boris.fundingarbitrage.model.contract.BookTicker;
import com.boris.fundingarbitrage.model.contract.Fees;
import com.boris.fundingarbitrage.model.contract.Mark;
import com.boris.fundingarbitrage.model.exchange.constantdata.FuturesConstantData;
import com.boris.fundingarbitrage.model.exchange.constantdata.SpotConstantData;
import com.boris.fundingarbitrage.model.exchange.snapshot.FuturesSnapshot;
import com.boris.fundingarbitrage.model.exchange.snapshot.SpotSnapshot;
import com.boris.fundingarbitrage.monitor.ExchangeCoinMap;
import com.boris.fundingarbitrage.util.coinvector.CoinVector;
import com.boris.fundingarbitrage.util.logger.Logger;
import org.apache.commons.lang3.function.TriConsumer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

public class CoinFilter {
	private final Set<String> coins;
	private final CoinFilterConfig config;

	private final CoinVector<Set<BaseExchange>> availableExchangesByCoin = new CoinVector<>();
	private final Map<BaseExchange, Set<String>> availableCoinsByExchange = new ConcurrentHashMap<>();
	private final ExchangeCoinMap<Boolean> presentOnFutures = new ExchangeCoinMap<>();
	private final ExchangeCoinMap<Boolean> presentOnSpot = new ExchangeCoinMap<>();
	private final ExchangeCoinMap<BigDecimal> futuresTradingVolumeMap = new ExchangeCoinMap<>();
	private final ExchangeCoinMap<FuturesTradingState> futuresTradingStatesMap = new ExchangeCoinMap<>();
	private final ExchangeCoinMap<BigDecimal> spotTradingVolumeMap = new ExchangeCoinMap<>();

	private final ExchangeCoinMap<ExchangeSnapshot> snapshotsMap = new ExchangeCoinMap<>();
	private final ExchangeCoinMap<ExchangeConstantData> constantDataMap = new ExchangeCoinMap<>();

	public CoinFilter(Set<String> coins, CoinFilterConfig config) {
		if (coins.isEmpty()) throw new IllegalArgumentException("No coins provided");

		this.coins = coins;
		this.config = config;
	}

	private <T> CompletableFuture<T> withTimeOut(CompletableFuture<T> future, String name) {
		return future.orTimeout(10, TimeUnit.SECONDS).exceptionally(t -> {
			Logger.error("Failed to get " + name + ": " + t.getMessage());
			throw new RuntimeException(t);
		});
	}

	private <T> void fillInMaps(
					CoinVector<T> opdVector,
					CoinVector<Fees> fVector,
					BaseExchange exchange,
					TriConsumer<String, T, Fees> onSuccess
	) {
		for (String coin : opdVector.keySet()) {
			T data = opdVector.get(coin);
			Fees fees = fVector.get(coin);
			if (fees == null || data == null) continue;

			availableExchangesByCoin.computeIfAbsent(coin, _ -> ConcurrentHashMap.newKeySet()).add(exchange);
			availableCoinsByExchange.computeIfAbsent(exchange, _ -> ConcurrentHashMap.newKeySet()).add(coin);

			snapshotsMap.computeIfAbsent(exchange, coin, _ -> new ExchangeSnapshot(null, null));
			constantDataMap.computeIfAbsent(exchange, coin, _ -> new ExchangeConstantData(null, null));

			onSuccess.accept(coin, data, fees);
		}
	}

	private CompletableFuture<Void> fetchData(BaseExchange exchange) {
		CompletableFuture<CoinVector<FuturesPublicOnePullData>> futuresOnePullDataFuture =
						withTimeOut(exchange.publicHttpClient.getFuturesOnePullData(coins), "futures one pull " + exchange.name);

		CompletableFuture<CoinVector<SpotPublicOnePullData>> spotOnePullDataFuture =
						withTimeOut(exchange.publicHttpClient.getSpotOnePullData(coins), "spot one pull " + exchange.name);

		CompletableFuture<CoinVector<Fees>> futuresFeesFuture =
						withTimeOut(exchange.privateHttpClient.getFutureTradingFees(coins), "futures fees " + exchange.name);

		CompletableFuture<CoinVector<Fees>> spotFeesFuture =
						withTimeOut(exchange.privateHttpClient.getSpotTradingFees(coins), "spot fees " + exchange.name);

		return CompletableFuture.allOf(futuresOnePullDataFuture, futuresFeesFuture, spotOnePullDataFuture, spotFeesFuture)
						.thenRun(() -> {
							CoinVector<FuturesPublicOnePullData> futuresOpdVector = futuresOnePullDataFuture.join();
							CoinVector<SpotPublicOnePullData> spotOpdVector = spotOnePullDataFuture.join();
							CoinVector<Fees> ffVector = futuresFeesFuture.join();
							CoinVector<Fees> sfVector = spotFeesFuture.join();

							fillInMaps(
											futuresOpdVector, ffVector, exchange, (coin, data, fees) -> {
												presentOnFutures.put(exchange, coin, true);

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

												snapshotsMap.compute(
																exchange,
																coin,
																(exSn) -> new ExchangeSnapshot(snapshot, exSn.spotSnapshot())
												);
												constantDataMap.compute(
																exchange,
																coin,
																(exCd) -> new ExchangeConstantData(constantData, exCd.spotConstantData())
												);
												futuresTradingVolumeMap.put(exchange, coin, data.volume24h());
												futuresTradingStatesMap.put(exchange, coin, data.tradingState());
											}
							);

							fillInMaps(
											spotOpdVector, sfVector, exchange, (coin, data, fees) -> {
												presentOnSpot.put(exchange, coin, true);

												SpotSnapshot snapshot = new SpotSnapshot(data.ticker());
												SpotConstantData constantData = new SpotConstantData(data.lotSize(), fees);

												snapshotsMap.compute(
																exchange,
																coin,
																(exSn) -> new ExchangeSnapshot(exSn.futuresSnapshot(), snapshot)
												);
												constantDataMap.compute(
																exchange,
																coin,
																(exCd) -> new ExchangeConstantData(exCd.futuresConstantData(), constantData)
												);
												spotTradingVolumeMap.put(exchange, coin, data.volume24h());
											}
							);
						});
	}

	private CompletableFuture<Void> fetchData() {
		for (String coin : coins) availableExchangesByCoin.put(coin, ConcurrentHashMap.newKeySet());
		for (BaseExchange ex : Instances.getExchangeArray())
			availableCoinsByExchange.put(ex, ConcurrentHashMap.newKeySet());

		List<CompletableFuture<Void>> futures = new ArrayList<>();
		for (BaseExchange exchange : Instances.getExchangeArray()) futures.add(fetchData(exchange));

		return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
	}

	private void filterCoins() {
		for (Map.Entry<String, Set<BaseExchange>> entry : availableExchangesByCoin.entrySet()) {
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
				availableExchangesByCoin.remove(coin);
			}
		}
	}

	public CompletableFuture<CoinFilterResult> filterAsync() {
		return fetchData()
						.thenRun(this::filterCoins)
						.thenApply(_ -> new CoinFilterResult(
										availableExchangesByCoin,
										availableCoinsByExchange,
										constantDataMap,
										snapshotsMap,
										presentOnFutures,
										presentOnSpot
						));
	}

	private void forgetCoinExchange(String coin, BaseExchange exchange) {
		Set<BaseExchange> available = availableExchangesByCoin.get(coin);
		if (available != null) {
			available.remove(exchange);
			if (available.isEmpty()) availableExchangesByCoin.remove(coin);
		}

		Set<String> subscribedCoins = availableCoinsByExchange.get(exchange);
		if (subscribedCoins != null) {
			subscribedCoins.remove(coin);
			if (subscribedCoins.isEmpty()) availableCoinsByExchange.remove(exchange);
		}

		snapshotsMap.remove(exchange, coin);
		constantDataMap.remove(exchange, coin);
	}

	private void forgetFuturesCoinExchange(String coin, BaseExchange exchange) {
		futuresTradingVolumeMap.remove(exchange, coin);
		futuresTradingStatesMap.remove(exchange, coin);
		snapshotsMap.computeIfPresent(exchange, coin, (ex, sn) -> new ExchangeSnapshot(null, sn.spotSnapshot()));
		constantDataMap.computeIfPresent(exchange, coin, (ex, cd) -> new ExchangeConstantData(null, cd.spotConstantData()));
		presentOnFutures.put(exchange, coin, false);
	}

	private void forgetSpotCoinExchange(String coin, BaseExchange exchange) {
		spotTradingVolumeMap.remove(exchange, coin);
		snapshotsMap.computeIfPresent(exchange, coin, (ex, sn) -> new ExchangeSnapshot(sn.futuresSnapshot(), null));
		constantDataMap.computeIfPresent(
						exchange,
						coin,
						(ex, cd) -> new ExchangeConstantData(cd.futuresConstantData(), null)
		);
		presentOnSpot.put(exchange, coin, false);
	}

	private String getExcludeMessage(
					BaseExchange ex,
					String coin,
					ExchangeCoinMap<BigDecimal> volumeMap,
					Function<ExchangeSnapshot, BookTicker> bookTickerExtractor,
					Function<ExchangeConstantData, BigDecimal> lotSizeExtractor
	) {
		BigDecimal volume = volumeMap.get(ex, coin);
		if (volume.compareTo(config.min24hVolumeUsdt()) < 0) return "Volume not enough: " + volume;

		BigDecimal ask = bookTickerExtractor.apply(snapshotsMap.get(ex, coin)).askPrice();
		BigDecimal minPriceStep = lotSizeExtractor.apply(constantDataMap.get(ex, coin)).multiply(ask);
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
						(sn) -> sn.futuresSnapshot().bookTicker(),
						(sn) -> sn.futuresConstantData().lotSize()
		);
	}

	private String getExcludeSpotMessage(BaseExchange ex, String coin) {
		return getExcludeMessage(
						ex,
						coin,
						spotTradingVolumeMap,
						(sn) -> sn.spotSnapshot().bookTicker(),
						(sn) -> sn.spotConstantData().lotSize()
		);
	}

	public record CoinPresence(
					boolean existsOnFutures,
					boolean existsOnSpot
	) {
	}
}
