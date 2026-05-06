package com.boris.fundingarbitrage.logic;

import com.boris.fundingarbitrage.exchange.BaseExchange;
import com.boris.fundingarbitrage.model.exchange.ExchangeBalance;
import com.boris.fundingarbitrage.model.exchange.constantdata.FuturesConstantData;
import com.boris.fundingarbitrage.model.exchange.constantdata.SpotConstantData;
import com.boris.fundingarbitrage.model.exchange.exchangedata.FuturesExchangeData;
import com.boris.fundingarbitrage.model.exchange.exchangedata.SpotExchangeData;
import com.boris.fundingarbitrage.model.exchange.snapshot.FuturesSnapshot;
import com.boris.fundingarbitrage.model.exchange.snapshot.SpotSnapshot;
import com.boris.fundingarbitrage.monitor.CoinMonitor;
import com.boris.fundingarbitrage.strategy.intradestrategy.factory.InTradeStrategyFactory;
import com.boris.fundingarbitrage.strategy.pretradestrategy.PreTradeStrategy;
import com.boris.fundingarbitrage.util.ModifiableFrequencyTask;
import com.boris.fundingarbitrage.util.coinvector.CoinVector;
import com.boris.fundingarbitrage.util.logger.Logger;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;

public abstract class ArbitrageLogic {
	protected final Set<BaseExchange> exchanges;
	protected final PreTradeStrategy preTradeStrategy;
	protected final InTradeStrategyFactory inTradeStrategyFactory;
	protected final ArbitrageBotConfig config;
	protected final ScheduledExecutorService logScheduler = Executors.newSingleThreadScheduledExecutor();
	protected CompletableFuture<Void> initFuture;
	protected CoinMonitor monitor;
	protected OpportunityAnalyzer opportunityAnalyzer;
	private ModifiableFrequencyTask opportunitiesProcessingTask;
	private volatile boolean shuttingDown = false;
	private volatile boolean processingActive = true;
	private volatile boolean logOnThisCycle = false;
	private BiFunction<BaseExchange, String, FuturesSnapshot> futuresSnapshotExtractor;
	private BiFunction<BaseExchange, String, SpotSnapshot> spotSnapshotExtractor;

	public ArbitrageLogic(
					Set<BaseExchange> exchanges,
					PreTradeStrategy preTradeStrategy,
					InTradeStrategyFactory inTradeStrategyFactory,
					ArbitrageBotConfig arbConfig,
					OpportunityAnalyzer opportunityAnalyzer
	) {
		this.exchanges = exchanges;
		this.preTradeStrategy = preTradeStrategy;
		this.inTradeStrategyFactory = inTradeStrategyFactory;
		this.config = arbConfig;
		this.opportunityAnalyzer = opportunityAnalyzer;
	}

	public ArbitrageLogic(
					Set<BaseExchange> exchanges,
					PreTradeStrategy preTradeStrategy,
					InTradeStrategyFactory inTradeStrategyFactory,
					ArbitrageBotConfig config
	) {
		this.exchanges = exchanges;
		this.preTradeStrategy = preTradeStrategy;
		this.inTradeStrategyFactory = inTradeStrategyFactory;
		this.config = config;
	}

	public CompletableFuture<Void> init(CoinMonitor monitor, BalanceProvider balanceProvider) {
		CompletableFuture<Void> balancesFuture = balanceProvider.load(exchanges).thenAccept(this::afterBalanceInit);

		capToMaxCoinAmount().join();

		this.monitor = monitor;
		monitor.start();

		this.futuresSnapshotExtractor = monitor::getFuturesSnapshot;
		this.spotSnapshotExtractor = monitor::getSpotSnapshot;
		return initFuture = CompletableFuture.allOf(prettyMonitorInitFuture(), balancesFuture);
	}

	CompletableFuture<Void> capToMaxCoinAmount() {
		if (filterData.coinExchangeSupport().coinCount() <= config.maxCoinAmount())
			return CompletableFuture.completedFuture(null);

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

	public void start() {
		initFuture.thenRun(this::startProcessingOpportunities);
	}

	CompletableFuture<Void> prettyMonitorInitFuture() {
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

	void attachWsDisconnectHandlers() {
		for (BaseExchange ex : filterData.coinExchangeSupport().getExchanges()) {
			ex.publicWsClient().onUnhandledDisconnect(() -> {
				Logger.error("Public ws client of " + ex.name() + " disconnected. Shutting down...");
				shutdown();
			});
		}
	}

	protected final void startProcessingOpportunities() {
		Logger.log("Starting arbitrage logic...");

		int logInterval = config.loggingIntervalSeconds();
		if (logInterval > 0)
			logScheduler.scheduleAtFixedRate(() -> this.logOnThisCycle = true, logInterval, logInterval, TimeUnit.SECONDS);
		this.opportunitiesProcessingTask = new ModifiableFrequencyTask(this::doTick, 50);
		this.opportunitiesProcessingTask.run();
	}

	void doTick() {
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
		int newFrequencyMs = (int) (filterData.coinExchangeSupport().coinCount() * 1.1);
		opportunitiesProcessingTask.setFrequency(newFrequencyMs);

		Logger.log("Adjusted frequency to recommended (" + newFrequencyMs + "ms)");
	}

	protected void adjustFrequency(int newFrequencyMs) {
		opportunitiesProcessingTask.setFrequency(newFrequencyMs);
		Logger.log("Adjusted frequency to " + newFrequencyMs + "ms");
	}

	FuturesExchangeData getFuturesExchangeData(
					BaseExchange ex,
					String coin
	) {
		if (!Boolean.TRUE.equals(filterData.initialPresentOnFutures().get(ex, coin))) return null;
		FuturesSnapshot snapshot = futuresSnapshotExtractor.apply(ex, coin);
		FuturesConstantData constantData = filterData.futuresConstantData().get(ex, coin);
		return new FuturesExchangeData(constantData, snapshot);
	}

	SpotExchangeData getSpotExchangeData(
					BaseExchange ex,
					String coin
	) {
		if (!Boolean.TRUE.equals(filterData.initialPresentOnSpot().get(ex, coin))) return null;
		SpotSnapshot snapshot = spotSnapshotExtractor.apply(ex, coin);
		SpotConstantData constantData = filterData.spotConstantData().get(ex, coin);
		return new SpotExchangeData(constantData, snapshot);
	}

	void logDataErrorHandled(CoinVector<CoinOpportunity> bestOpportunities) {
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
		filterData.coinExchangeSupport().coinsByExchange().forEach((ex, coins) -> {
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

	protected abstract void afterBalanceInit(Map<BaseExchange, ExchangeBalance> balanceMap);

	protected abstract void afterMonitorInit();

	public void shutdown() {
		if (shuttingDown) return;
		shuttingDown = true;
		if (monitor != null) {
			monitor.shutdown();
			if (!monitor.getInitFuture().isDone()) monitor.getInitFuture().cancel(true);
		}
		logScheduler.shutdownNow();
		opportunityAnalyzer.shutdown();
		if (opportunitiesProcessingTask != null) opportunitiesProcessingTask.cancelNow();
		Logger.log("Arbitrage logic stopped.");
		Logger.closeLogFile();
	}
}
