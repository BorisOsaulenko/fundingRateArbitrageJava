package com.boris.fundingarbitrage.logic;

import com.boris.fundingarbitrage.coinParser.ICoinSupplier;
import com.boris.fundingarbitrage.coinfilter.CoinFilter;
import com.boris.fundingarbitrage.coinfilter.CoinFilterConfig;
import com.boris.fundingarbitrage.coinfilter.CoinFilterResult;
import com.boris.fundingarbitrage.exchange.BaseExchange;
import com.boris.fundingarbitrage.exchange.Instances;
import com.boris.fundingarbitrage.model.arbitrage.ArbitrageConstantData;
import com.boris.fundingarbitrage.model.arbitrage.ArbitrageData;
import com.boris.fundingarbitrage.model.exchange.ExchangeConstantData;
import com.boris.fundingarbitrage.monitor.CoinMonitor;
import com.boris.fundingarbitrage.monitor.ExchangeCoinMap;
import com.boris.fundingarbitrage.strategy.PreTradeStrategy;
import com.boris.fundingarbitrage.util.coinvector.CoinVector;
import com.boris.fundingarbitrage.util.logger.Logger;
import kotlin.jvm.functions.Function2;

import java.math.BigDecimal;
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
	protected final CoinVector<ArbitrageData> bestArbData = new CoinVector<>();
	protected final CoinVector<ExchangePair> bestArbExchanges = new CoinVector<>();
	protected final Map<BaseExchange, BigDecimal> spotBalances = new ConcurrentHashMap<>();
	protected final Map<BaseExchange, BigDecimal> futuresBalances = new ConcurrentHashMap<>();
	private final ICoinSupplier coinSupplier;
	protected int frequencyMs = 100;
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


	private void init(CoinFilterConfig filterConfig) {
		CompletableFuture<Void> balancesFuture = initBalancesMap();

		Set<String> coins = coinSupplier.getCoinsAsync().join();
		CoinFilter coinFilter = new CoinFilter(coins, filterConfig);
		CoinFilterResult filterResult = coinFilter.filterAsync().join();
		Logger.log("Coins filtered according to CoinFilterConfig");

		availableExchangesByCoin = filterResult.availableExchangesByCoin();
		availableCoinsByExchange = filterResult.availableCoinsByExchange();

		this.monitor = new CoinMonitor(availableExchangesByCoin, availableCoinsByExchange);

		initFuture = CompletableFuture.allOf(prettyMonitorInitFuture(), balancesFuture);
	}

	public void waitForInitSync() {
		initFuture.join();
		startProcessingOpportunities();
		Logger.log("Arbitrage logic started.");
	}

	private CompletableFuture<Void> prettyMonitorInitFuture() {
		return monitor.getInitFuture()
						.thenRun(() -> {
							Logger.log("Monitor initialized");
							attachWsDisconnectHandlers();
						})
						.exceptionally(t -> {
							Logger.error("Failed to initialize monitor. " + t.getMessage());
							shutdown();
							throw new RuntimeException(t);
						});
	}

	private void attachWsDisconnectHandlers() {
		for (BaseExchange ex : availableCoinsByExchange.keySet()) {
			ex.publicWsClient.onUnhandledDisconnect(() -> {
				Logger.error("Public ws client of " + ex.name + " disconnected. Shutting down...");
				shutdown();
			});
		}
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
						.thenRun(this::afterBalanceInitErrorHandled);
	}

	protected final void startProcessingOpportunities() {
		Logger.log("Starting arbitrage logic...");

		int logInterval = config.loggingIntervalSeconds();
		if (logInterval > 0)
			logScheduler.scheduleAtFixedRate(this::logDataErrorHandled, logInterval, logInterval, TimeUnit.SECONDS
			);

		doFirstTick();
		this.currentSchedulerTask = scheduler.scheduleAtFixedRate(
						() -> {
							processCoins().join();
							processTickErrorHandled();
						}, frequencyMs, frequencyMs, TimeUnit.MILLISECONDS
		);
	}

	private void doFirstTick() {
		processCoins().join();
		processTickErrorHandled();
		afterFirstTickErrorHandled();
	}

	private void adjustFrequencyToRecommendedNoRestart() {
		frequencyMs = (int) (availableExchangesByCoin.size() * 1.1);
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

		Logger.log("Adjusted frequency to recommended (" + newFrequencyMs + "ms)");
	}

	protected void adjustFrequency(int newFrequencyMs) {
		currentSchedulerTask.cancel(true);
		currentSchedulerTask = scheduler.scheduleAtFixedRate(
						() -> {
							processCoins().join();
							processTick();
						}, 0, newFrequencyMs, TimeUnit.MILLISECONDS
		);

		Logger.log("Adjusted frequency to " + newFrequencyMs + "ms");
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

	private void logDataErrorHandled() {
		try {
			logData();
		} catch (Exception e) {
			Logger.error("Exception while logging best arb options: " + e.getMessage());
			shutdown();
		}
	}

	protected void logData() {
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
		availableCoinsByExchange.forEach((_, coins) -> coins.remove(coin));
	}

	protected void stopCalculatingBestOptionsForAllCoins() {
		logScheduler.shutdownNow();
		currentSchedulerTask.cancel(true);
		currentSchedulerTask = scheduler.scheduleAtFixedRate(this::processTick, 0, frequencyMs, TimeUnit.MILLISECONDS);
	}

	private void processTickErrorHandled() {
		try {
			processTick();
		} catch (Exception e) {
			Logger.error("Exception while processing tick: " + e.getMessage());
			shutdown();
		}
	}

	private void afterFirstTickErrorHandled() {
		try {
			afterFirstTick();
		} catch (Exception e) {
			Logger.error("Exception while after first tick: " + e.getMessage());
			shutdown();
		}
	}

	private void afterBalanceInitErrorHandled() {
		try {
			afterBalanceInit();
		} catch (Exception e) {
			Logger.error("Exception while after balance init");
			shutdown();
		}
	}

	protected abstract void processTick();

	protected abstract void afterBalanceInit();

	protected abstract void afterFirstTick();

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
