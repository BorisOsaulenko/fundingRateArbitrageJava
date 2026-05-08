package com.boris.fundingarbitrage.logic;

import com.boris.fundingarbitrage.coinfilter.CoinAvailabilityRecord;
import com.boris.fundingarbitrage.coinfilter.ConstantDataRecord;
import com.boris.fundingarbitrage.exchange.BaseExchange;
import com.boris.fundingarbitrage.logic.balanceprovider.IBalanceProvider;
import com.boris.fundingarbitrage.logic.opportunityanalyzer.IOpportunityAnalyzer;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public abstract class ArbitrageLogic {
	private static final Logger log = LoggerFactory.getLogger(ArbitrageLogic.class);
	protected final Set<BaseExchange> exchanges;
	protected final PreTradeStrategy preTradeStrategy;
	protected final InTradeStrategyFactory inTradeStrategyFactory;
	protected final CoinAvailabilityRecord coinAvailability;
	protected final ConstantDataRecord constantDataRecord;
	protected final ArbitrageBotConfig config;
	protected final ScheduledExecutorService logScheduler = Executors.newSingleThreadScheduledExecutor();
	protected final CoinMonitor monitor;
	protected final IOpportunityAnalyzer opportunityAnalyzer;
	protected CompletableFuture<Void> initFuture;
	private ModifiableFrequencyTask opportunitiesProcessingTask;
	private volatile boolean shuttingDown = false;
	private volatile boolean processingActive = true;
	private volatile boolean logOnThisCycle = false;

	public ArbitrageLogic(
					Set<BaseExchange> exchanges,
					CoinMonitor monitor,
					IOpportunityAnalyzer opportunityAnalyzer,
					PreTradeStrategy preTradeStrategy,
					InTradeStrategyFactory inTradeStrategyFactory,
					CoinAvailabilityRecord coinAvailability,
					ConstantDataRecord constantDataRecord,
					ArbitrageBotConfig arbConfig
	) {
		this.exchanges = exchanges;
		this.monitor = monitor;
		this.opportunityAnalyzer = opportunityAnalyzer;
		this.preTradeStrategy = preTradeStrategy;
		this.inTradeStrategyFactory = inTradeStrategyFactory;
		this.coinAvailability = coinAvailability;
		this.constantDataRecord = constantDataRecord;
		this.config = arbConfig;
	}

	public CompletableFuture<Void> init(IBalanceProvider balanceProvider) {
		CompletableFuture<Void> balancesFuture = balanceProvider.load(exchanges).thenAccept(this::afterBalanceInit);
		return initFuture = CompletableFuture.allOf(prettyMonitorInitFuture(), balancesFuture);
	}

	public void start() {
		if (initFuture == null) throw new IllegalStateException("Call ArbitrageLogic.init() before calling start().");
		initFuture.thenRun(this::startProcessingOpportunities);
	}

	CompletableFuture<Void> prettyMonitorInitFuture() {
		return monitor.getInitFuture()
						.thenRun(() -> {
							log.info("Monitor initialized");
							attachWsDisconnectHandlers();
							afterMonitorInit();
						})
						.exceptionally(t -> {
							log.error("Failed to initialize monitor. {}", t.getMessage());
							shutdown();
							throw new RuntimeException(t);
						});
	}

	void attachWsDisconnectHandlers() {
		for (BaseExchange ex : coinAvailability.getExchanges()) {
			ex.publicWsClient().onUnhandledDisconnect(() -> {
				log.error("Public ws client of {} disconnected. Shutting down...", ex.name());
				shutdown();
			});
		}
	}

	protected final void startProcessingOpportunities() {
		log.info("Starting arbitrage logic...");

		int logInterval = config.loggingIntervalSeconds();
		if (logInterval > 0)
			logScheduler.scheduleAtFixedRate(() -> this.logOnThisCycle = true, logInterval, logInterval, TimeUnit.SECONDS);
		this.opportunitiesProcessingTask = new ModifiableFrequencyTask(this::doTick, 50);
		this.opportunitiesProcessingTask.run();
	}

	void doTick() {
		CoinVector<CoinOpportunity> bestOpportunities = null;
		if (processingActive) bestOpportunities =
						opportunityAnalyzer.processCoins(this::getFuturesExchangeData, this::getSpotExchangeData).join();

		try {
			processTick(bestOpportunities);
		} catch (Exception e) {
			log.error("Exception while processing tick: {}", e.getMessage());
			shutdown();
			throw new RuntimeException(e);
		}

		if (logOnThisCycle) {
			logDataErrorHandled(bestOpportunities);
			logOnThisCycle = false;
		}
	}

	protected void adjustFrequencyToRecommended() {
		int newFrequencyMs = (int) (coinAvailability.coinCount() * 1.1);
		opportunitiesProcessingTask.setFrequency(newFrequencyMs);

		log.info("Adjusted frequency to recommended ({}ms)", newFrequencyMs);
	}

	protected void adjustFrequency(int newFrequencyMs) {
		opportunitiesProcessingTask.setFrequency(newFrequencyMs);
		log.info("Adjusted frequency to {}ms", newFrequencyMs);
	}

	FuturesExchangeData getFuturesExchangeData(
					BaseExchange ex,
					String coin
	) {
		if (!coinAvailability.isFutures(ex, coin)) return null;
		FuturesSnapshot snapshot = monitor.getFuturesSnapshot(ex, coin);
		FuturesConstantData constantData = constantDataRecord.getFuturesConstantData(ex, coin);
		return new FuturesExchangeData(constantData, snapshot);
	}

	SpotExchangeData getSpotExchangeData(
					BaseExchange ex,
					String coin
	) {
		if (!coinAvailability.isSpot(ex, coin)) return null;
		SpotSnapshot snapshot = monitor.getSpotSnapshot(ex, coin);
		SpotConstantData constantData = constantDataRecord.getSpotConstantData(ex, coin);
		return new SpotExchangeData(constantData, snapshot);
	}

	void logDataErrorHandled(CoinVector<CoinOpportunity> bestOpportunities) {
		try {
			logData(bestOpportunities);
		} catch (Exception e) {
			log.error("Exception while logging best arb options: {}", e.getMessage());
			shutdown();
			throw new RuntimeException(e);
		}
	}

	protected void logData(CoinVector<CoinOpportunity> bestOpportunities) {
		if (bestOpportunities == null && this.processingActive)
			throw new IllegalStateException("Best opportunities should not be null while processing");
		if (bestOpportunities == null) return;

		log.info("The best arbitrage opportunities:");
		bestOpportunities.sortDesc(Comparator.comparing(CoinOpportunity::expectedGain))
						.subList(0, Math.min(config.logBestArbSnapshotsAmount(), bestOpportunities.size()))
						.forEach(entry -> {

							CoinOpportunity op = entry.getValue();
							log.info(
											"{}: {} - {} [Expected Gain: {}]",
											entry.getKey(),
											op.exchanges(),
											preTradeStrategy.goodToEnter(op.longData(), op.shortData()) ? "GOOD" : "BAD",
											op.expectedGain()
							);

						});
	}

	protected void forgetCoin(String coin) {
		coinAvailability.removeByCoin(coin);
		constantDataRecord.removeByCoin(coin);
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
		log.info("Arbitrage logic stopped.");
	}
}
