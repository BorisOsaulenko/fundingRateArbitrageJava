package com.boris.fundingarbitrage.logic;

import com.boris.fundingarbitrage.coinfilter.CoinFilterConfig;
import com.boris.fundingarbitrage.coinfilter.CoinFilterResult;
import com.boris.fundingarbitrage.coinfilter.CoinSelector;
import com.boris.fundingarbitrage.exchange.BaseExchange;
import com.boris.fundingarbitrage.exchange.Instances;
import com.boris.fundingarbitrage.model.arbitrage.ArbitrageSnapshot;
import com.boris.fundingarbitrage.monitor.CoinMonitor;
import com.boris.fundingarbitrage.strategy.PreTradeStrategy;
import com.boris.fundingarbitrage.util.coinvector.CoinVector;
import com.boris.fundingarbitrage.util.logger.Logger;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;

public abstract class ArbitrageLogic {
	protected final PreTradeStrategy preTradeStrategy;
	protected final ArbitrageBotConfig config;
	protected final CoinMonitor monitor;
	protected final CoinVector<Set<BaseExchange>> availableExchangesByCoin;
	protected final Set<String> activeCoins = ConcurrentHashMap.newKeySet();

	protected final ScheduledExecutorService logScheduler = Executors.newSingleThreadScheduledExecutor();
	protected final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
	protected final ExecutorService cpuPool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
	protected final int frequencyMs = 100;

	protected final CoinVector<ArbitrageSnapshot> bestArbSnapshots = new CoinVector<>();
	protected final CoinVector<ExchangePair> bestArbExchanges = new CoinVector<>();
	protected final Map<BaseExchange, BigDecimal> spotBalances = new ConcurrentHashMap<>();
	protected final Map<BaseExchange, BigDecimal> futuresBalances = new ConcurrentHashMap<>();
	protected CompletableFuture<Void> balancesFuture;
	protected boolean shutdown = false;

	public ArbitrageLogic(
					PreTradeStrategy strategy,
					ArbitrageBotConfig arbConfig,
					CoinFilterConfig filterConfig
	) {
		this.preTradeStrategy = strategy;
		this.config = arbConfig;

		CoinSelector coinSelector = new CoinSelector(arbConfig.coins(), filterConfig);
		CoinFilterResult filtered = coinSelector.filterSync();

		availableExchangesByCoin = filtered.availableExchangesByCoin();
		activeCoins.addAll(availableExchangesByCoin.keySet());
		monitor = new CoinMonitor(filtered);

		balancesFuture = fillBalancesMap();
		startProcessingOpportunities();
	}

	private CompletableFuture<Void> fillBalancesMap() {
		List<CompletableFuture<Void>> futures = new ArrayList<>();

		for (BaseExchange exchange : Instances.getExchangeArray()) {
			CompletableFuture<Void> spotBalanceFuture = exchange.privateHttpClient.getSpotUsdtBalance()
							.thenAccept(spotB -> spotBalances.put(exchange, spotB));
			CompletableFuture<Void> futuresBalanceFuture = exchange.privateHttpClient.getFuturesUsdtBalance()
							.thenAccept(futuresB -> futuresBalances.put(exchange, futuresB));

			futures.add(spotBalanceFuture);
			futures.add(futuresBalanceFuture);
		}

		return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
	}

	protected void startProcessingOpportunities() {
		monitor.getInitFuture().thenAccept(_ -> {
			Logger.log("Starting arbitrage logic...");

			int logInterval = config.loggingIntervalSeconds();
			if (logInterval > 0) logScheduler.scheduleAtFixedRate(this::logData, logInterval, logInterval, TimeUnit.SECONDS);
			scheduler.scheduleAtFixedRate(
							() -> {
								processCoins();
								processTick();
							}, 0, frequencyMs, TimeUnit.MILLISECONDS
			);
		});
	}

	protected void adjustFrequencyToRecommended() {
		int newFrequencyMs = (int) (activeCoins.size() * 1.1);
		scheduler.shutdownNow();
		scheduler.scheduleAtFixedRate(
						() -> {
							processCoins();
							processTick();
						}, 0, newFrequencyMs, TimeUnit.MILLISECONDS
		);
	}

	protected void adjustFrequency(int newFrequencyMs) {
		scheduler.shutdownNow();
		scheduler.scheduleAtFixedRate(
						() -> {
							processCoins();
							processTick();
						}, 0, newFrequencyMs, TimeUnit.MILLISECONDS
		);
	}

	private void processCoins() {
		List<CompletableFuture<Void>> futures = activeCoins.stream()
						.map(coin -> CompletableFuture.runAsync(() -> computeBestArbSnapshotForCoin(coin), cpuPool))
						.toList();

		CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
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

				var longSnapshot = monitor.getSnapshot(longEx, coin);
				var shortSnapshot = monitor.getSnapshot(shortEx, coin);
				var arbSnapshot = new ArbitrageSnapshot(longSnapshot, shortSnapshot);
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
						});
	}

	protected void stopCalculatingBestOptionsForCoin(String coin) {
		activeCoins.remove(coin);
	}

	protected void stopCalculatingBestOptionsForAllCoins() {
		activeCoins.clear();
		scheduler.shutdownNow();
		logScheduler.shutdownNow();
		scheduler.scheduleAtFixedRate(this::processTick, 0, frequencyMs, TimeUnit.MILLISECONDS);
	}

	protected abstract void processTick();

	public void shutdown() {
		monitor.shutdown();
		logScheduler.shutdownNow();
		scheduler.shutdownNow();
		cpuPool.shutdownNow();
		shutdown = true;
		Logger.log("Arbitrage logic stopped.");
	}
}
