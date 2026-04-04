package com.boris.fundingarbitrage.logic;

import com.boris.fundingarbitrage.coinParser.ICoinSupplier;
import com.boris.fundingarbitrage.coinfilter.CoinFilter;
import com.boris.fundingarbitrage.coinfilter.CoinFilterConfig;
import com.boris.fundingarbitrage.coinfilter.CoinFilterResult;
import com.boris.fundingarbitrage.exchange.BaseExchange;
import com.boris.fundingarbitrage.exchange.Instances;
import com.boris.fundingarbitrage.logic.coinopportunities.CoinOpportunity;
import com.boris.fundingarbitrage.logic.coinopportunities.CrossCoinOpportunity;
import com.boris.fundingarbitrage.logic.coinopportunities.SingleCoinOpportunity;
import com.boris.fundingarbitrage.model.exchange.ExchangeConstantData;
import com.boris.fundingarbitrage.model.exchange.ExchangeData;
import com.boris.fundingarbitrage.model.exchange.ExchangeSnapshot;
import com.boris.fundingarbitrage.monitor.CoinMonitor;
import com.boris.fundingarbitrage.monitor.ExchangeCoinMap;
import com.boris.fundingarbitrage.strategy.pretradestrategy.PreTradeStrategy;
import com.boris.fundingarbitrage.util.coinvector.CoinVector;
import com.boris.fundingarbitrage.util.logger.Logger;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BiFunction;

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
	private BiFunction<BaseExchange, String, ExchangeSnapshot> snapshotExtractor;
	private ExchangeCoinMap<ExchangeSnapshot> initialSnapshots;
	private ExchangeCoinMap<Boolean> presentOnSpot;
	private ExchangeCoinMap<Boolean> presentOnFutures;

	public ArbitrageLogic(
					ICoinSupplier coinSupplier,
					PreTradeStrategy preTradeStrategy,
					CoinFilterConfig filterConfig,
					ArbitrageBotConfig arbConfig
	) {
		this.coinSupplier = coinSupplier;
		this.preTradeStrategy = preTradeStrategy;
		this.config = arbConfig;

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
		initialSnapshots = filterResult.snapshots();
		presentOnSpot = filterResult.presentOnSpot();
		presentOnFutures = filterResult.presentOnFutures();

		capToMaxCoinAmount().join();

		this.monitor = new CoinMonitor(
						availableExchangesByCoin,
						availableCoinsByExchange,
						presentOnFutures,
						presentOnSpot
		);
		this.snapshotExtractor = monitor::getSnapshot;
		initFuture = CompletableFuture.allOf(prettyMonitorInitFuture(), balancesFuture);
	}

	private CompletableFuture<Void> capToMaxCoinAmount() {
		if (availableExchangesByCoin.size() <= config.maxCoinAmount()) return CompletableFuture.completedFuture(null);
		this.snapshotExtractor = initialSnapshots::get;
		return processCoins().thenAccept(bestOps -> {
			List<Map.Entry<String, CoinOpportunity>> bestOpsSorted = bestOps.sortDesc(Comparator.comparing(CoinOpportunity::expectedGain));
			List<String> coinsToRemove = bestOpsSorted.subList(config.maxCoinAmount(), bestOpsSorted.size())
							.stream()
							.map(Map.Entry::getKey)
							.toList();

			coinsToRemove.forEach(this::forgetCoin);
			Logger.log("Capped coins to " + config.maxCoinAmount());
		});
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
							afterMonitorInit();
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
																		() -> {
																			CoinOpportunity bestOp = computeBestArbSnapshotForCoin(coin);
																			if (bestOp != null) result.put(coin, bestOp);
																			else forgetCoin(coin);
																		},
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

	private CrossData getBestCrossData(String coin) {
		Set<BaseExchange> availableExchanges = availableExchangesByCoin.get(coin);
		if (availableExchanges == null) throw new IllegalStateException("Available exchanges for " + coin + " not found");

		BaseExchange bestLongEx = null;
		BaseExchange bestShortEx = null;
		BigDecimal bestCrossGain = null;
		ExchangeData bestLongData = null;
		ExchangeData bestShortData = null;

		for (BaseExchange longEx : availableExchanges) {
			for (BaseExchange shortEx : availableExchanges) {
				if (longEx == shortEx) continue;

				ExchangeData longData = getExchangeData(longEx, coin);
				ExchangeData shortData = getExchangeData(shortEx, coin);

				BigDecimal gain = preTradeStrategy.expectedGain(longData, shortData);
				if (bestLongEx == null || gain.compareTo(bestCrossGain) > 0) {
					bestLongEx = longEx;
					bestShortEx = shortEx;
					bestCrossGain = gain;
					bestLongData = longData;
					bestShortData = shortData;
				}
			}
		}

		return new CrossData(bestLongEx, bestShortEx, bestCrossGain, bestLongData, bestShortData);
	}

	private SingleData getBestSingleData(String coin) {
		Set<BaseExchange> availableExchanges = availableExchangesByCoin.get(coin);
		if (availableExchanges == null) throw new IllegalStateException("Available exchanges for " + coin + " not found");

		BaseExchange bestSingleExchange = null;
		ExchangeData bestSingleData = null;
		BigDecimal bestSingleGain = null;
		for (BaseExchange ex : availableExchanges) {
			ExchangeData data = getExchangeData(ex, coin);
			if (data.spotConstantData() == null || data.futuresConstantData() == null)
				continue; // For single exchange trades, coin must be both in futures form and spot form
			BigDecimal gain = preTradeStrategy.expectedGain(data);
			if (bestSingleGain == null || gain.compareTo(bestSingleGain) >= 0) {
				bestSingleExchange = ex;
				bestSingleGain = gain;
				bestSingleData = data;
			}
		}
		if (bestSingleGain == null) return null;
		return new SingleData(bestSingleExchange, bestSingleData, bestSingleGain);
	}

	private CoinOpportunity computeBestArbSnapshotForCoin(
					String coin
	) {
		CrossData bestCrossOp = null;
		//		if (availableExchanges.size() >= 2) bestCrossOp = getBestCrossData(coin);

		SingleData bestSingleOp = getBestSingleData(coin);

		if (bestCrossOp == null && bestSingleOp == null) return null;

		if (bestCrossOp == null || bestCrossOp.gain().compareTo(bestSingleOp.gain()) < 0)
			return new SingleCoinOpportunity(bestSingleOp.exchange(), bestSingleOp.gain(), bestSingleOp.data());
		else return new CrossCoinOpportunity(
						new ExchangePair(bestCrossOp.longEx(), bestCrossOp.shortEx()),
						bestCrossOp.gain(),
						bestCrossOp.longData(),
						bestCrossOp.shortData()
		);
	}

	private ExchangeData getExchangeData(
					BaseExchange ex,
					String coin
	) {
		ExchangeSnapshot sn = snapshotExtractor.apply(ex, coin);
		ExchangeConstantData constantData = constantDataMap.get(ex, coin);
		return new ExchangeData(sn, constantData);
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
		bestOpportunities.sortDesc(Comparator.comparing(CoinOpportunity::expectedGain))
						.subList(0, Math.min(config.logBestArbSnapshotsAmount(), bestOpportunities.size()))
						.forEach(entry -> {

							if (entry.getValue() instanceof CrossCoinOpportunity(
											ExchangePair exchanges,
											BigDecimal expectedGain,
											ExchangeData longData,
											ExchangeData shortData
							)) {
								Logger.log(entry.getKey() + ": " + exchanges + " - " +
													 (preTradeStrategy.goodToEnter(longData, shortData) ? "GOOD" : "BAD") +
													 "[Expected Gain: " + expectedGain + "]");
							} else if (entry.getValue() instanceof SingleCoinOpportunity(
											BaseExchange exchange,
											BigDecimal expectedGain,
											ExchangeData data
							)) {
								Logger.log(entry.getKey() + ": " + exchange.name + " - " +
													 (preTradeStrategy.goodToEnter(data) ? "GOOD" : "BAD") +
													 "[Expected Gain: " + expectedGain + "]");
							}
						});
	}

	protected void forgetCoin(String coin) {
		availableExchangesByCoin.remove(coin);
		availableCoinsByExchange.forEach((ex, coins) -> {
			coins.remove(coin);
			constantDataMap.remove(ex, coin);
			initialSnapshots.remove(ex, coin);
			presentOnSpot.remove(ex, coin);
			presentOnFutures.remove(ex, coin);
		});
	}

	protected void stopCalculatingBestOptionsForAllCoins() {
		processingActive = false;
	}

	protected abstract void processTick(CoinVector<CoinOpportunity> bestOpportunities);

	protected abstract void afterBalanceInit(
					Map<BaseExchange, BigDecimal> spotBalances,
					Map<BaseExchange, BigDecimal> futuresBalances
	);

	protected abstract void afterMonitorInit();

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

	private record CrossData(
					BaseExchange longEx,
					BaseExchange shortEx,
					BigDecimal gain,
					ExchangeData longData,
					ExchangeData shortData
	) {
	}

	private record SingleData(
					BaseExchange exchange,
					ExchangeData data,
					BigDecimal gain
	) {
	}
}
