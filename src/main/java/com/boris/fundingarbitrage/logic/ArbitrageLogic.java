package com.boris.fundingarbitrage.logic;

import com.boris.fundingarbitrage.coinfilter.CoinFilter;
import com.boris.fundingarbitrage.coinfilter.CoinFilterConfig;
import com.boris.fundingarbitrage.coinfilter.CoinFilterResult;
import com.boris.fundingarbitrage.exchange.BaseExchange;
import com.boris.fundingarbitrage.model.arbitrage.ArbitrageSnapshot;
import com.boris.fundingarbitrage.monitor.CoinMonitor;
import com.boris.fundingarbitrage.strategy.ArbitrageStrategy;
import com.boris.fundingarbitrage.util.coinvector.CoinVector;
import com.boris.fundingarbitrage.util.logger.Logger;
import kotlin.Pair;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class ArbitrageLogic {
	private final ArbitrageStrategy strategy;
	private final ArbitrageBotConfig config;
	private final CoinMonitor monitor;

	private final ScheduledExecutorService logScheduler = Executors.newSingleThreadScheduledExecutor();
	private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
	private final ExecutorService cpuPool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
	private final int frequencyMs = 100;

	private final CoinVector<Set<BaseExchange>> availableExchangesByCoin;
	private final Set<String> coins;

	private final CoinVector<Pair<BaseExchange, BaseExchange>> bestArbExchanges = new CoinVector<>();
	private final CoinVector<ArbitrageSnapshot> bestArbSnapshots = new CoinVector<>();
	private Map.Entry<String, ArbitrageSnapshot> bestCoinEntry;

	public ArbitrageLogic(ArbitrageStrategy strategy, ArbitrageBotConfig arbConfig, CoinFilterConfig filterConfig) {
		this.strategy = strategy;
		this.config = arbConfig;

		CoinFilter filter = new CoinFilter(arbConfig.coins(), filterConfig);
		CoinFilterResult filtered = filter.filterSync();

		availableExchangesByCoin = filtered.availableExchangesByCoin();
		coins = availableExchangesByCoin.keySet();

		this.monitor = new CoinMonitor(filtered);
	}

	private ArbitrageSnapshot computeBestArbSnapshotForCoin(String coin) {
		Set<BaseExchange> availableExchanges = availableExchangesByCoin.get(coin);
		if (availableExchanges == null) throw new IllegalStateException("Available exchanges for " + coin + " not found");

		ArbitrageSnapshot bestSnapshot = null;
		for (BaseExchange longEx : availableExchanges) {
			for (BaseExchange shortEx : availableExchanges) {
				if (longEx == shortEx) continue;

				var longSnapshot = monitor.getSnapshot(longEx, coin);
				var shortSnapshot = monitor.getSnapshot(shortEx, coin);
				var arbSnapshot = new ArbitrageSnapshot(longSnapshot, shortSnapshot);
				if (bestSnapshot == null || strategy.compareSnapshots(arbSnapshot, bestSnapshot) > 0) {
					bestSnapshot = arbSnapshot;
					bestArbExchanges.put(coin, new Pair<>(longEx, shortEx));
				}
			}
		}

		return bestSnapshot;
	}

	private void computeBestArbSnapshots() {
		bestCoinEntry = bestArbSnapshots.getMaxEntry(strategy::compareSnapshots);
	}

	public void start() {
		monitor.getInitFuture().join();
		Logger.log("Starting arbitrage logic...");

		if (config.loggingIntervalSeconds() > 0) {
			logScheduler.scheduleAtFixedRate(
							this::logData,
							config.loggingIntervalSeconds(),
							config.loggingIntervalSeconds(),
							TimeUnit.SECONDS
			);
		}

		scheduler.scheduleAtFixedRate(
						() -> {
							var futures = coins.stream().collect(Collectors.toMap(
											coin -> coin,
											coin -> CompletableFuture.supplyAsync(() -> computeBestArbSnapshotForCoin(coin), cpuPool)
							));

							CompletableFuture.allOf(futures.values().toArray(CompletableFuture[]::new)).join();
							futures.forEach((coin, future) -> bestArbSnapshots.put(coin, future.join()));

							computeBestArbSnapshots();
						}, 0, frequencyMs, TimeUnit.MILLISECONDS
		);
	}

	public void stop() {
		logScheduler.shutdownNow();
		scheduler.shutdownNow();
		cpuPool.shutdownNow();
		monitor.shutdown();

		Logger.log("Arbitrage logic stopped.");
	}

	private void logData() {
		Logger.log("The best arbitrage opportunities:");
		bestArbSnapshots
						.sortDesc(strategy::compareSnapshots)
						.subList(0, config.logBestArbSnapshotsAmount())
						.forEach(entry -> {
							var bestCoinExchanges = bestArbExchanges.get(entry.getKey());
							assert bestCoinExchanges != null;

							Logger.log(entry.getKey() +
												 ": " +
												 bestCoinExchanges.getFirst().name +
												 " (long) / " +
												 bestCoinExchanges.getSecond().name +
												 " (short) - " +
												 (strategy.snapshotGoodEnough(entry.getValue()) ? "GOOD" : "BAD"));
						});
	}
}
