package com.boris.fundingarbitrage.logic;

import com.boris.fundingarbitrage.coinParser.ICoinSupplier;
import com.boris.fundingarbitrage.coinfilter.CoinFilter;
import com.boris.fundingarbitrage.coinfilter.CoinFilterConfig;
import com.boris.fundingarbitrage.coinfilter.CoinFilterResult;
import com.boris.fundingarbitrage.exchange.BaseExchange;
import com.boris.fundingarbitrage.exchange.Instances;
import com.boris.fundingarbitrage.model.arbitrage.ArbitrageConstantData;
import com.boris.fundingarbitrage.model.arbitrage.ArbitrageData;
import com.boris.fundingarbitrage.model.arbitrage.ArbitrageSnapshot;
import com.boris.fundingarbitrage.model.contract.BookTicker;
import com.boris.fundingarbitrage.model.contract.Fees;
import com.boris.fundingarbitrage.model.contract.FundingRate;
import com.boris.fundingarbitrage.model.contract.MarkPrice;
import com.boris.fundingarbitrage.model.exchange.ExchangeConstantData;
import com.boris.fundingarbitrage.model.exchange.ExchangeSnapshot;
import com.boris.fundingarbitrage.monitor.CoinMonitor;
import com.boris.fundingarbitrage.monitor.ExchangeCoinMap;
import com.boris.fundingarbitrage.strategy.PreTradeStrategy;
import com.boris.fundingarbitrage.util.coinvector.CoinVector;
import com.boris.fundingarbitrage.util.logger.Logger;
import kotlin.jvm.functions.Function2;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;

public abstract class ArbitrageLogic {
	protected final PreTradeStrategy preTradeStrategy;
	protected final ArbitrageBotConfig config;
	protected final ScheduledExecutorService logScheduler = Executors.newSingleThreadScheduledExecutor();
	protected final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
	protected final ExecutorService cpuPool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
	protected final int frequencyMs = 100;
	protected final CoinVector<ArbitrageData> bestArbData = new CoinVector<>();
	protected final CoinVector<ExchangePair> bestArbExchanges = new CoinVector<>();
	protected final Map<BaseExchange, BigDecimal> spotBalances = new ConcurrentHashMap<>();
	protected final Map<BaseExchange, BigDecimal> futuresBalances = new ConcurrentHashMap<>();
	private final ICoinSupplier coinSupplier;
	protected CompletableFuture<Void> initFuture;
	protected CoinMonitor monitor;
	protected CoinVector<Set<BaseExchange>> availableExchangesByCoin;
	protected Map<BaseExchange, Set<String>> availableCoinsByExchange;
	protected ScheduledFuture<?> currentSchedulerTask;
	protected boolean shutdown = false;
	protected ExchangeCoinMap<ExchangeConstantData> constantDataMap = new ExchangeCoinMap<>();

	public ArbitrageLogic(
					ICoinSupplier coinSupplier,
					PreTradeStrategy strategy,
					CoinFilterConfig filterConfig,
					ArbitrageBotConfig arbConfig
	) {
		this.coinSupplier = coinSupplier;
		this.preTradeStrategy = strategy;
		this.config = arbConfig;

		init(filterConfig);
	}

	private CompletableFuture<ExchangeCoinMap<Fees>> initFees() {
		ExchangeCoinMap<Fees> result = new ExchangeCoinMap<>();
		List<CompletableFuture<Void>> futures = new ArrayList<>();

		for (Map.Entry<BaseExchange, Set<String>> entry : availableCoinsByExchange.entrySet()) {
			BaseExchange ex = entry.getKey();
			Set<String> coins = entry.getValue();

			CompletableFuture<Void> future = ex.privateHttpClient.getTradingFees(coins)
							.thenAccept(feesVector -> {
								feesVector.forEach((coin, fee) -> result.put(ex, coin, fee));
							})
							.exceptionally(t -> {
								Logger.error("Failed to fetch trading fees for " + ex.name + ": " + t.getMessage());
								throw new RuntimeException(t);
							});

			futures.add(future);
		}
		return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).thenApply(_ -> result);
	}

	private void init(CoinFilterConfig filterConfig) {
		CompletableFuture<Void> balancesFuture = initBalancesMap();

		Set<String> coins = coinSupplier.getCoinsAsync().join();
		CoinFilter coinFilter = new CoinFilter(coins, filterConfig);
		CoinFilterResult filterResult = coinFilter.filterAsync().join();
		Logger.log("Coins filtered according to CoinFilterConfig");

		availableExchangesByCoin = filterResult.availableExchangesByCoin();
		availableCoinsByExchange = filterResult.availableCoinsByExchange();

		if (availableExchangesByCoin.size() > config.maxCoinAmount()) capCoinsToMaxAmount(filterResult);
		this.monitor = new CoinMonitor(availableExchangesByCoin, availableCoinsByExchange);

		CompletableFuture<Void> feesFuture = initFees().thenAccept((feesMap) -> {
			for (var entry : feesMap.entrySet()) {
				BigDecimal lotSize = filterResult.lotSizesMap().get(entry.exchange(), entry.coin());
				Integer fundingInterval = filterResult.fundingIntervalsMap().get(entry.exchange(), entry.coin());
				Fees fees = entry.value();

				assert lotSize != null && fundingInterval != null && fees != null;
				constantDataMap.put(
								entry.exchange(), entry.coin(), new ExchangeConstantData(lotSize, fees, fundingInterval)
				);
			}
		});

		initFuture = CompletableFuture.allOf(prettyMonitorInitFuture(), feesFuture, balancesFuture);
	}

	public void waitForInitSync() {
		initFuture.join();
		startProcessingOpportunities();
		Logger.log("Arbitrage logic started.");
	}

	private CompletableFuture<Void> prettyMonitorInitFuture() {
		return monitor.getInitFuture()
						.thenRun(() -> Logger.log("Monitor initialized"))
						.exceptionally(t -> {
							Logger.error("Failed to initialize monitor. " + t.getMessage());
							shutdown();
							throw new RuntimeException(t);
						});
	}

	private CompletableFuture<Void> initBalancesMap() {
		List<CompletableFuture<Void>> futures = new ArrayList<>();

		for (BaseExchange exchange : Instances.getExchangeArray()) {
			CompletableFuture<Void> spotBalanceFuture = exchange.privateHttpClient.getSpotUsdtBalance()
							.thenAccept(spotB -> spotBalances.put(exchange, spotB))
							.exceptionally(t -> {
								Logger.error("Failed to fetch spot balance for " + exchange.name + ": " + t.getMessage());
								shutdown();
								throw new RuntimeException(t);
							});
			CompletableFuture<Void> futuresBalanceFuture = exchange.privateHttpClient.getFuturesUsdtBalance()
							.thenAccept(futuresB -> futuresBalances.put(exchange, futuresB))
							.exceptionally(t -> {
								Logger.error("Failed to fetch futures balance for " + exchange.name + ": " + t.getMessage());
								shutdown();
								throw new RuntimeException(t);
							});

			futures.add(spotBalanceFuture);
			futures.add(futuresBalanceFuture);
		}

		return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
						.thenRun(() -> Logger.log("Balances initialized"))
						.thenRun(this::afterBalanceInit);
	}

	protected final void startProcessingOpportunities() {
		Logger.log("Starting arbitrage logic...");

		int logInterval = config.loggingIntervalSeconds();
		if (logInterval > 0) logScheduler.scheduleAtFixedRate(this::logData, logInterval, logInterval, TimeUnit.SECONDS);
		doFirstTick();
		this.currentSchedulerTask = scheduler.scheduleAtFixedRate(
						() -> {
							processCoins().join();
							processTick();
						}, 0, frequencyMs, TimeUnit.MILLISECONDS
		);
	}

	private void doFirstTick() {
		processCoins().join();
		processTick();
		afterFirstTick();
	}

	protected void adjustFrequencyToRecommended() {
		int newFrequencyMs = (int) (availableExchangesByCoin.size() * 1.1);
		if (currentSchedulerTask != null) currentSchedulerTask.cancel(true);
		currentSchedulerTask = scheduler.scheduleAtFixedRate(
						() -> {
							processCoins().join();
							processTick();
						}, 0, newFrequencyMs, TimeUnit.MILLISECONDS
		);
	}

	protected void adjustFrequency(int newFrequencyMs) {
		currentSchedulerTask.cancel(true);
		currentSchedulerTask = scheduler.scheduleAtFixedRate(
						() -> {
							processCoins().join();
							processTick();
						}, 0, newFrequencyMs, TimeUnit.MILLISECONDS
		);
	}

	private CompletableFuture<Void> processCoins() {
		return processCoins(this::getCurrentArbData);
	}

	private CompletableFuture<Void> processCoins(Function2<ExchangePair, String, ArbitrageData> getData) {
		List<CompletableFuture<Void>> futures = availableExchangesByCoin.keySet().stream()
						.map(coin ->
										CompletableFuture.runAsync(() -> computeBestArbSnapshotForCoin(coin, getData), cpuPool)
														.exceptionally(t -> {
															Logger.error("Failed to compute best arb snapshot for " + coin + ": " + t.getMessage());
															shutdown();
															throw new RuntimeException(t);
														})
						)
						.toList();

		return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
	}

	private void computeBestArbSnapshotForCoin(String coin, Function2<ExchangePair, String, ArbitrageData> getData) {
		Set<BaseExchange> availableExchanges = availableExchangesByCoin.get(coin);
		if (availableExchanges == null) throw new IllegalStateException("Available exchanges for " + coin + " not found");

		ArbitrageData bestSnapshot = null;
		BaseExchange bestLongEx = null;
		BaseExchange bestShortEx = null;

		for (BaseExchange longEx : availableExchanges) {
			for (BaseExchange shortEx : availableExchanges) {
				if (longEx == shortEx) continue;

				ArbitrageData coinArbData = getData.invoke(new ExchangePair(longEx, shortEx), coin);
				if (bestSnapshot == null || preTradeStrategy.compareArbData(coinArbData, bestSnapshot) > 0) {
					bestSnapshot = coinArbData;
					bestLongEx = longEx;
					bestShortEx = shortEx;
				}
			}
		}

		assert bestSnapshot != null;

		bestArbData.put(coin, bestSnapshot);
		bestArbExchanges.put(coin, new ExchangePair(bestLongEx, bestShortEx));
	}

	private ArbitrageData getCurrentArbData(ExchangePair exchanges, String coin) {
		ExchangeConstantData longConstantData = constantDataMap.get(exchanges.longEx(), coin);
		ExchangeConstantData shortConstantData = constantDataMap.get(exchanges.shortEx(), coin);
		var arbSnapshot = monitor.getSnapshot(new ExchangePair(exchanges.longEx(), exchanges.shortEx()), coin);
		return new ArbitrageData(arbSnapshot, new ArbitrageConstantData(longConstantData, shortConstantData));
	}

	private void logData() {
		Logger.log("The best arbitrage opportunities:");
		bestArbData.sortDesc(preTradeStrategy::compareArbData)
						.subList(0, Math.min(config.logBestArbSnapshotsAmount(), bestArbData.size()))
						.forEach(entry -> {
							var bestCoinExchanges = bestArbExchanges.get(entry.getKey());
							assert bestCoinExchanges != null;

							Logger.log(entry.getKey() +
												 ": " +
												 bestCoinExchanges.longEx().name +
												 " (long) / " +
												 bestCoinExchanges.shortEx().name +
												 " (short) - " +
												 (preTradeStrategy.arbDataGoodEnough(entry.getValue()) ? "GOOD" : "BAD"));
							Logger.log("Snaphshot: " + entry.getValue());
						});
	}

	protected void dropCoinProcessing(String coin) {
		availableExchangesByCoin.remove(coin);
		bestArbData.remove(coin);
		bestArbExchanges.remove(coin);
	}

	protected void stopCalculatingBestOptionsForAllCoins() {
		logScheduler.shutdownNow();
		currentSchedulerTask.cancel(true);
		currentSchedulerTask = scheduler.scheduleAtFixedRate(this::processTick, 0, frequencyMs, TimeUnit.MILLISECONDS);
	}

	protected abstract void processTick();

	protected abstract void afterBalanceInit();

	protected abstract void afterFirstTick();

	protected void capCoinsToMaxAmount(CoinFilterResult filterResult) {
		ArbitrageConstantData commonConstantData = new ArbitrageConstantData(
						new ExchangeConstantData(new BigDecimal("0.1"), Fees.allZero(), 4),
						new ExchangeConstantData(new BigDecimal("0.1"), Fees.allZero(), 4)
		);

		Function2<ExchangePair, String, ArbitrageData> getDataFunc = (exchanges, coin) -> {
			BookTicker longTicker = filterResult.bookTickersMap().get(exchanges.longEx(), coin);
			BookTicker shortTicker = filterResult.bookTickersMap().get(exchanges.shortEx(), coin);
			FundingRate longFunding = filterResult.fundingRatesMap().get(exchanges.longEx(), coin);
			FundingRate shortFunding = filterResult.fundingRatesMap().get(exchanges.shortEx(), coin);
			MarkPrice longMarkPrice = new MarkPrice(longTicker.askPrice(), Instant.now());
			MarkPrice shortMarkPrice = new MarkPrice(shortTicker.bidPrice(), Instant.now());
			ExchangeSnapshot longSn = new ExchangeSnapshot(longTicker, longFunding, longMarkPrice);
			ExchangeSnapshot shortSn = new ExchangeSnapshot(shortTicker, shortFunding, shortMarkPrice);
			return new ArbitrageData(new ArbitrageSnapshot(longSn, shortSn), commonConstantData);
		};

		processCoins(getDataFunc).join();
		List<Map.Entry<String, ArbitrageData>> worseCoins = bestArbData.sortDesc(preTradeStrategy::compareArbData)
						.subList(config.maxCoinAmount(), bestArbData.size());

		for (var entry : worseCoins) {
			String coin = entry.getKey();
			bestArbData.remove(coin);
			bestArbExchanges.remove(coin);
			availableExchangesByCoin.remove(coin);
			availableCoinsByExchange.forEach((_, coins) -> coins.remove(coin));
		}

		Logger.log("Capped chosen coins to: " + config.maxCoinAmount());
	}

	public void shutdown() {
		monitor.shutdown();
		logScheduler.shutdownNow();
		scheduler.shutdownNow();
		cpuPool.shutdownNow();
		if (!monitor.getInitFuture().isDone()) monitor.getInitFuture().cancel(true);
		shutdown = true;
		Logger.log("Arbitrage logic stopped.");
	}
}
