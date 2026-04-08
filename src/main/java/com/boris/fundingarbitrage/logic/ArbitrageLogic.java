package com.boris.fundingarbitrage.logic;

import com.boris.fundingarbitrage.coinParser.ICoinSupplier;
import com.boris.fundingarbitrage.coinfilter.CoinFilter;
import com.boris.fundingarbitrage.coinfilter.CoinFilterConfig;
import com.boris.fundingarbitrage.coinfilter.CoinFilterResult;
import com.boris.fundingarbitrage.exchange.BaseExchange;
import com.boris.fundingarbitrage.exchange.Instances;
import com.boris.fundingarbitrage.model.exchange.ExchangePair;
import com.boris.fundingarbitrage.model.exchange.constantdata.FuturesConstantData;
import com.boris.fundingarbitrage.model.exchange.constantdata.SpotConstantData;
import com.boris.fundingarbitrage.model.exchange.exchangedata.ExchangeData;
import com.boris.fundingarbitrage.model.exchange.exchangedata.FuturesExchangeData;
import com.boris.fundingarbitrage.model.exchange.exchangedata.SpotExchangeData;
import com.boris.fundingarbitrage.model.exchange.snapshot.FuturesSnapshot;
import com.boris.fundingarbitrage.model.exchange.snapshot.SpotSnapshot;
import com.boris.fundingarbitrage.monitor.CoinMonitor;
import com.boris.fundingarbitrage.strategy.pretradestrategy.PreTradeStrategy;
import com.boris.fundingarbitrage.util.ModifiableFrequencyTask;
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
	protected CoinFilterResult filterData;
	private ModifiableFrequencyTask opportunitiesProcessingTask;
	private volatile boolean shuttingDown = false;
	private volatile boolean processingActive = true;
	private volatile boolean logOnThisCycle = false;
	private BiFunction<BaseExchange, String, FuturesSnapshot> futuresSnapshotExtractor;
	private BiFunction<BaseExchange, String, SpotSnapshot> spotSnapshotExtractor;

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

		this.filterData = filterResult;
		capToMaxCoinAmount().join();

		this.monitor = new CoinMonitor(filterData);
		this.futuresSnapshotExtractor = monitor::getFuturesSnapshot;
		initFuture = CompletableFuture.allOf(prettyMonitorInitFuture(), balancesFuture);
	}

	private CompletableFuture<Void> capToMaxCoinAmount() {
		if (filterData.availableExchangesByCoin().size() <= config.maxCoinAmount())
			return CompletableFuture.completedFuture(null);
		this.futuresSnapshotExtractor = (ex, coin) -> filterData.initialFuturesSnapshots().get(ex, coin);
		this.spotSnapshotExtractor = (ex, coin) -> filterData.initialSpotSnapshots().get(ex, coin);

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
		for (BaseExchange ex : filterData.availableCoinsByExchange().keySet()) {
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
		int newFrequencyMs = (int) (filterData.availableExchangesByCoin().size() * 1.1);
		opportunitiesProcessingTask.setFrequency(newFrequencyMs);

		Logger.log("Adjusted frequency to recommended (" + newFrequencyMs + "ms)");
	}

	protected void adjustFrequency(int newFrequencyMs) {
		opportunitiesProcessingTask.setFrequency(newFrequencyMs);
		Logger.log("Adjusted frequency to " + newFrequencyMs + "ms");
	}

	private CompletableFuture<CoinVector<CoinOpportunity>> processCoins() {
		CoinVector<CoinOpportunity> result = new CoinVector<>();
		List<CompletableFuture<Void>> futures = filterData.availableExchangesByCoin().keySet().stream().map(coin ->
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

	private CoinOpportunity computeBestArbSnapshotForCoin(
					String coin
	) {
		Set<BaseExchange> availableExchanges = filterData.availableExchangesByCoin().get(coin);
		if (availableExchanges == null) throw new IllegalStateException("Available exchanges for " + coin + " not found");

		BaseExchange bestLongEx = null;
		BaseExchange bestShortEx = null;
		BigDecimal bestGain = null;
		ExchangeData bestLongData = null;
		ExchangeData bestShortData = null;

		for (BaseExchange longEx : availableExchanges) {
			for (BaseExchange shortEx : availableExchanges) {
				List<ExchangeData> longData = new ArrayList<>();
				List<ExchangeData> shortData = new ArrayList<>();

				if (Boolean.TRUE.equals(filterData.initialPresentOnFutures().get(longEx, coin)))
					longData.add(getFuturesExchangeData(longEx, coin));

				if (Boolean.TRUE.equals(filterData.initialPresentOnSpot().get(longEx, coin)))
					longData.add(getSpotExchangeData(longEx, coin));

				if (Boolean.TRUE.equals(filterData.initialPresentOnFutures().get(shortEx, coin)))
					shortData.add(getFuturesExchangeData(shortEx, coin));

				if (Boolean.TRUE.equals(filterData.initialPresentOnSpot().get(shortEx, coin)))
					shortData.add(getSpotExchangeData(shortEx, coin));

				if (longData.isEmpty() || shortData.isEmpty()) continue;

				for (ExchangeData ld : longData) {
					for (ExchangeData sd : shortData) {
						if (ld.equalsRefs(sd)) continue; // same exchange and same market twice case
						BigDecimal gain = preTradeStrategy.expectedGain(ld, sd);
						if (bestGain == null || gain.compareTo(bestGain) >= 0) {
							bestLongEx = longEx;
							bestShortEx = shortEx;
							bestGain = gain;
							bestLongData = ld;
							bestShortData = sd;
						}
					}
				}
			}
		}
		assert bestGain != null;

		return new CoinOpportunity(
						new ExchangePair(bestLongEx, bestShortEx),
						bestGain,
						bestLongData,
						bestShortData
		);
	}

	private FuturesExchangeData getFuturesExchangeData(
					BaseExchange ex,
					String coin
	) {
		FuturesSnapshot snapshot = futuresSnapshotExtractor.apply(ex, coin);
		FuturesConstantData constantData = filterData.futuresConstantData().get(ex, coin);
		return new FuturesExchangeData(constantData, snapshot);
	}

	private SpotExchangeData getSpotExchangeData(
					BaseExchange ex,
					String coin
	) {
		SpotSnapshot snapshot = spotSnapshotExtractor.apply(ex, coin);
		SpotConstantData constantData = filterData.spotConstantData().get(ex, coin);
		return new SpotExchangeData(constantData, snapshot);
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

							CoinOpportunity op = entry.getValue();
							Logger.log(entry.getKey() + ": " + op.exchanges() + " - " +
												 (preTradeStrategy.goodToEnter(op.longData(), op.shortData()) ? "GOOD" : "BAD") +
												 "[Expected Gain: " + op.expectedGain() + "]");

						});
	}

	protected void forgetCoin(String coin) {
		filterData.availableExchangesByCoin().remove(coin);
		filterData.availableCoinsByExchange().forEach((ex, coins) -> {
			coins.remove(coin);
			filterData.futuresConstantData().remove(ex, coin);
			filterData.spotConstantData().remove(ex, coin);
			filterData.initialFuturesSnapshots().remove(ex, coin);
			filterData.initialSpotSnapshots().remove(ex, coin);
			filterData.initialPresentOnSpot().remove(ex, coin);
			filterData.initialPresentOnFutures().remove(ex, coin);
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
}
