package com.boris.fundingarbitrage.logic;

import com.boris.fundingarbitrage.coinParser.ICoinSupplier;
import com.boris.fundingarbitrage.coinfilter.CoinFilter;
import com.boris.fundingarbitrage.coinfilter.CoinFilterConfig;
import com.boris.fundingarbitrage.coinfilter.CoinFilterResult;
import com.boris.fundingarbitrage.exchange.BaseExchange;
import com.boris.fundingarbitrage.exchange.Instances;
import com.boris.fundingarbitrage.execution.CoinExecution;
import com.boris.fundingarbitrage.model.arbitrage.ArbitrageConstantData;
import com.boris.fundingarbitrage.model.arbitrage.ArbitrageData;
import com.boris.fundingarbitrage.model.exchange.ExchangeConstantData;
import com.boris.fundingarbitrage.monitor.CoinMonitor;
import com.boris.fundingarbitrage.monitor.ExchangeCoinMap;
import com.boris.fundingarbitrage.strategy.PreTradeStrategy;
import com.boris.fundingarbitrage.util.coinvector.CoinVector;
import com.boris.fundingarbitrage.util.logger.Logger;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;

public abstract class ArbitrageLogic {
	protected final PreTradeStrategy preTradeStrategy;
	protected final ArbitrageBotConfig config;
	protected final ScheduledExecutorService logScheduler = Executors.newSingleThreadScheduledExecutor();
	protected final ExecutorService cpuPool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
	private final ICoinSupplier coinSupplier;
	protected CompletableFuture<Void> initFuture;
	protected CoinMonitor monitor;
	protected CoinVector<Set<BaseExchange>> availableExchangesByCoin;
	protected Map<BaseExchange, Set<String>> availableCoinsByExchange;
	protected ExchangeCoinMap<ExchangeConstantData> constantDataMap;
	private ModifiableFrequencyTask opportunitiesProcessingTask;
	private volatile boolean shuttingDown = false;
	private volatile boolean processingActive = true;
	private volatile boolean logOnThisCycle = false;

	public ArbitrageLogic(
					ICoinSupplier coinSupplier,
					PreTradeStrategy strategy,
					CoinFilterConfig filterConfig,
					ArbitrageBotConfig arbConfig
	) {
		this.coinSupplier = coinSupplier;
		this.preTradeStrategy = strategy;
		this.config = arbConfig;
		CoinExecution.setLeverage(arbConfig.leverage());

		init(filterConfig);
	}

	private void init(CoinFilterConfig filterConfig) {
		CompletableFuture<Void> balancesFuture = initBalancesMap();

		Set<String> coins = coinSupplier.getCoinsAsync().join();
		CoinFilter coinFilter = new CoinFilter(coins, filterConfig);
		CoinFilterResult filterResult = coinFilter.filterAsync().join();
		Logger.log("Coins filtered according to CoinFilterConfig");

		if (filterResult.availableExchangesByCoin().isEmpty()) {
			Logger.log("No coins passed filter. Shutting down");
			shutdown();
			return;
		}

		availableExchangesByCoin = filterResult.availableExchangesByCoin();
		availableCoinsByExchange = filterResult.availableCoinsByExchange();
		constantDataMap = filterResult.constantData();

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
		Map<BaseExchange, BigDecimal> spotBalances = new HashMap<>();
		Map<BaseExchange, BigDecimal> futuresBalances = new HashMap<>();
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
						.thenRun(() -> afterBalanceInit(spotBalances, futuresBalances))
						.exceptionally(t -> {
							Logger.error("Error while processing balances: " + t.getMessage());
							shutdown();
							throw new RuntimeException(t);
						});
	}

	protected final void startProcessingOpportunities() {
		Logger.log("Starting arbitrage logic...");

		int logInterval = config.loggingIntervalSeconds();
		if (logInterval > 0)
			logScheduler.scheduleAtFixedRate(() -> this.logOnThisCycle = true, logInterval, logInterval, TimeUnit.SECONDS);
		this.opportunitiesProcessingTask = new ModifiableFrequencyTask(this::doTick, 50);
		this.opportunitiesProcessingTask.run();
	}

	private void doTick() {
		CoinVector<CoinOpportunity> bestOpportunities = null;
		if (processingActive) bestOpportunities = processCoins().join();

		try {
			processTick(bestOpportunities);
		} catch (Exception e) {
			Logger.error("Exception while processing tick: " + e.getMessage());
			shutdown();
			throw new RuntimeException(e);
		}

		if (logOnThisCycle) {
			logDataErrorHandled(bestOpportunities);
			logOnThisCycle = false;
		}
	}

	protected void adjustFrequencyToRecommended() {
		int newFrequencyMs = (int) (availableExchangesByCoin.size() * 1.1);
		opportunitiesProcessingTask.setFrequency(newFrequencyMs);

		Logger.log("Adjusted frequency to recommended (" + newFrequencyMs + "ms)");
	}

	protected void adjustFrequency(int newFrequencyMs) {
		opportunitiesProcessingTask.setFrequency(newFrequencyMs);
		Logger.log("Adjusted frequency to " + newFrequencyMs + "ms");
	}

	private CompletableFuture<CoinVector<CoinOpportunity>> processCoins() {
		CoinVector<CoinOpportunity> result = new CoinVector<>();
		List<CompletableFuture<Void>> futures = availableExchangesByCoin.keySet().stream().map(coin ->
										CompletableFuture.runAsync(
																		() -> result.put(coin, computeBestArbSnapshotForCoin(coin)),
																		cpuPool
														)
														.exceptionally(t -> {
															Logger.error("Failed to compute best arb snapshot for " + coin + ": " + t.getMessage());
															shutdown();
															throw new RuntimeException(t);
														})
						)
						.toList();

		return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).thenApply(v -> result);
	}

	private CoinOpportunity computeBestArbSnapshotForCoin(
					String coin
	) {
		Set<BaseExchange> availableExchanges = availableExchangesByCoin.get(coin);
		if (availableExchanges == null) throw new IllegalStateException("Available exchanges for " + coin + " not found");

		ArbitrageData bestSnapshot = null;
		BaseExchange bestLongEx = null;
		BaseExchange bestShortEx = null;

		for (BaseExchange longEx : availableExchanges) {
			for (BaseExchange shortEx : availableExchanges) {
				if (longEx == shortEx) continue;

				ArbitrageData coinArbData = getCurrentArbData(new ExchangePair(longEx, shortEx), coin);
				if (bestSnapshot == null || preTradeStrategy.compareArbData(coinArbData, bestSnapshot) > 0) {
					bestSnapshot = coinArbData;
					bestLongEx = longEx;
					bestShortEx = shortEx;
				}
			}
		}

		assert bestLongEx != null;
		return new CoinOpportunity(bestSnapshot, new ExchangePair(bestLongEx, bestShortEx));
	}

	private ArbitrageData getCurrentArbData(ExchangePair exchanges, String coin) {
		ExchangeConstantData longConstantData = constantDataMap.get(exchanges.longEx(), coin);
		ExchangeConstantData shortConstantData = constantDataMap.get(exchanges.shortEx(), coin);
		var arbSnapshot = monitor.getSnapshot(new ExchangePair(exchanges.longEx(), exchanges.shortEx()), coin);
		return new ArbitrageData(arbSnapshot, new ArbitrageConstantData(longConstantData, shortConstantData));
	}

	private void logDataErrorHandled(CoinVector<CoinOpportunity> bestOpportunities) {
		try {
			logData(bestOpportunities);
		} catch (Exception e) {
			Logger.error("Exception while logging best arb options: " + e.getMessage());
			shutdown();
			throw new RuntimeException(e);
		}
	}

	protected void logData(CoinVector<CoinOpportunity> bestOpportunities) {
		if (bestOpportunities == null && this.processingActive)
			throw new IllegalStateException("Best opportunities should not be null while processing");
		if (bestOpportunities == null) return;

		Logger.log("The best arbitrage opportunities:");
		bestOpportunities.sortDesc((a, b) -> preTradeStrategy.compareArbData(a.data(), b.data()))
						.subList(0, Math.min(config.logBestArbSnapshotsAmount(), bestOpportunities.size()))
						.forEach(entry -> {
							var bestCoinExchanges = entry.getValue().exchanges();
							assert bestCoinExchanges != null;

							Logger.log(entry.getKey() +
												 ": " +
												 bestCoinExchanges.longEx().name +
												 " (long) / " +
												 bestCoinExchanges.shortEx().name +
												 " (short) - " +
												 (preTradeStrategy.arbDataGoodEnough(entry.getValue().data()) ? "GOOD" : "BAD"));
							Logger.log("Snaphshot: " + entry.getValue().data());
						});
	}

	protected void forgetCoin(String coin) {
		availableExchangesByCoin.remove(coin);
		availableCoinsByExchange.forEach((_, coins) -> coins.remove(coin));
	}

	protected void stopCalculatingBestOptionsForAllCoins() {
		processingActive = false;
	}

	protected abstract void processTick(CoinVector<CoinOpportunity> bestOpportunities);

	protected abstract void afterBalanceInit(
					Map<BaseExchange, BigDecimal> spotBalances,
					Map<BaseExchange, BigDecimal> futuresBalances
	);

	public void shutdown() {
		if (shuttingDown) return;
		shuttingDown = true;
		monitor.shutdown();
		logScheduler.shutdownNow();
		cpuPool.shutdownNow();
		opportunitiesProcessingTask.cancelNow();
		if (!monitor.getInitFuture().isDone()) monitor.getInitFuture().cancel(true);
		Logger.log("Arbitrage logic stopped.");
		Logger.closeLogFile();
	}

	protected record CoinOpportunity(
					ArbitrageData data,
					ExchangePair exchanges
	) {
	}
}
