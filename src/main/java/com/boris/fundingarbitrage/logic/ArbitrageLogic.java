package com.boris.fundingarbitrage.logic;

import com.boris.fundingarbitrage.coinfilter.CoinFilterResult;
import com.boris.fundingarbitrage.exchange.BaseExchange;
import com.boris.fundingarbitrage.exchange.Instances;
import com.boris.fundingarbitrage.model.arbitrage.ArbitrageSnapshot;
import com.boris.fundingarbitrage.monitor.CoinMonitor;
import com.boris.fundingarbitrage.strategy.PreTradeStrategy;
import com.boris.fundingarbitrage.util.coinvector.CoinVector;
import com.boris.fundingarbitrage.util.logger.Logger;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public abstract class ArbitrageLogic {
	protected final PreTradeStrategy preTradeStrategy;
	protected final ArbitrageBotConfig config;
	protected final CoinMonitor monitor;
	protected final Set<String> activeCoins = ConcurrentHashMap.newKeySet();
	protected final ScheduledExecutorService logScheduler = Executors.newSingleThreadScheduledExecutor();
	protected final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
	protected final ExecutorService cpuPool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
	protected final int frequencyMs = 100;
	protected final CoinVector<ArbitrageSnapshot> bestArbSnapshots = new CoinVector<>();
	protected final CoinVector<ExchangePair> bestArbExchanges = new CoinVector<>();
	protected final Map<BaseExchange, BigDecimal> spotBalances = new ConcurrentHashMap<>();
	protected final Map<BaseExchange, BigDecimal> futuresBalances = new ConcurrentHashMap<>();
	protected final CompletableFuture<Void> initFuture;
	protected CoinVector<Set<BaseExchange>> availableExchangesByCoin;
	protected Map<BaseExchange, Set<String>> availableCoinsByExchange;
	protected ScheduledFuture<?> currentSchedulerTask;
	protected CompletableFuture<Void> balancesFuture;
	protected boolean shutdown = false;

	public ArbitrageLogic(
					PreTradeStrategy strategy,
					CoinFilterResult filterResult,
					ArbitrageBotConfig arbConfig
	) {
		this.preTradeStrategy = strategy;
		this.config = arbConfig;

		availableExchangesByCoin = filterResult.availableExchangesByCoin();
		availableCoinsByExchange = filterResult.availableCoinsByExchange();
		activeCoins.addAll(availableExchangesByCoin.keySet());

		if (activeCoins.size() > config.maxCoinAmount()) capCoinsToMaxAmount();

		this.monitor = new CoinMonitor(availableExchangesByCoin, availableCoinsByExchange);

		balancesFuture = initBalancesMap();
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
						})
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
						.thenRun(this::afterBalanceInit)
						.thenRun(() -> Logger.log("Balances initialized"));
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
		int newFrequencyMs = (int) (activeCoins.size() * 1.1);
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
		List<CompletableFuture<Void>> futures = activeCoins.stream()
						.map(coin -> CompletableFuture.runAsync(() -> computeBestArbSnapshotForCoin(coin), cpuPool))
						.toList();

		return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
	}

	private void computeBestArbSnapshotForCoin(String coin) {
		Set<BaseExchange> availableExchanges = availableExchangesByCoin.get(coin);
		if (availableExchanges == null) throw new IllegalStateException("Available exchanges for " + coin + " not found");

		ArbitrageSnapshot bestSnapshot = null;
		BaseExchange bestLongEx = null;
		BaseExchange bestShortEx = null;

		for (BaseExchange longEx : availableExchanges) {
			for (BaseExchange shortEx : availableExchanges) {
				if (longEx == shortEx) continue;

				var arbSnapshot = monitor.getSnapshot(new ExchangePair(longEx, shortEx), coin);
				if (bestSnapshot == null || preTradeStrategy.compareSnapshots(arbSnapshot, bestSnapshot) > 0) {
					bestSnapshot = arbSnapshot;
					bestLongEx = longEx;
					bestShortEx = shortEx;
				}
			}
		}

		assert bestSnapshot != null;

		bestArbSnapshots.put(coin, bestSnapshot);
		bestArbExchanges.put(coin, new ExchangePair(bestLongEx, bestShortEx));
	}

	private void logData() {
		Logger.log("The best arbitrage opportunities:");
		bestArbSnapshots.sortDesc(preTradeStrategy::compareSnapshots)
						.subList(0, Math.min(config.logBestArbSnapshotsAmount(), bestArbSnapshots.size()))
						.forEach(entry -> {
							var bestCoinExchanges = bestArbExchanges.get(entry.getKey());
							assert bestCoinExchanges != null;

							Logger.log(entry.getKey() +
												 ": " +
												 bestCoinExchanges.longEx().name +
												 " (long) / " +
												 bestCoinExchanges.shortEx().name +
												 " (short) - " +
												 (preTradeStrategy.snapshotGoodEnough(entry.getValue()) ? "GOOD" : "BAD"));
							Logger.log("Snaphshot: " + entry.getValue());
						});
	}

	protected void stopCalculatingBestOptionsForCoin(String coin) {
		activeCoins.remove(coin);
	}

	protected void stopCalculatingBestOptionsForAllCoins() {
		activeCoins.clear();
		logScheduler.shutdownNow();
		currentSchedulerTask.cancel(true);
		currentSchedulerTask = scheduler.scheduleAtFixedRate(this::processTick, 0, frequencyMs, TimeUnit.MILLISECONDS);
	}

	protected abstract void processTick();

	protected abstract void afterBalanceInit();

	protected abstract void afterFirstTick();

	protected void capCoinsToMaxAmount() {
		processCoins().join();
		List<Map.Entry<String, ArbitrageSnapshot>> bestOpportunities = bestArbSnapshots.sortDesc(preTradeStrategy::compareSnapshots)
						.subList(0, config.maxCoinAmount());
		Set<String> bestOpportunitiesCoins = bestOpportunities.stream().map(Map.Entry::getKey).collect(Collectors.toSet());
		Set<String> coinsToRemove = new HashSet<>();

		for (String coin : activeCoins) {
			if (bestOpportunitiesCoins.contains(coin)) continue;
			bestArbSnapshots.remove(coin);
			bestArbExchanges.remove(coin);
			activeCoins.remove(coin);
			availableExchangesByCoin.remove(coin);
			coinsToRemove.add(coin);
		}

		for (Set<String> set : availableCoinsByExchange.values()) set.removeAll(coinsToRemove);
	}

	public void shutdown() {
		monitor.shutdown();
		logScheduler.shutdownNow();
		scheduler.shutdownNow();
		cpuPool.shutdownNow();
		if (!balancesFuture.isDone()) balancesFuture.cancel(true);
		if (!monitor.getInitFuture().isDone()) monitor.getInitFuture().cancel(true);
		shutdown = true;
		Logger.log("Arbitrage logic stopped.");
	}
}
