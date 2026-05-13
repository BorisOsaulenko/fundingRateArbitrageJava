package com.boris.fundingarbitrage.logic;

import com.boris.fundingarbitrage.coinfilter.CoinAvailabilityRecord;
import com.boris.fundingarbitrage.coinfilter.ConstantDataRecord;
import com.boris.fundingarbitrage.exchange.BaseExchange;
import com.boris.fundingarbitrage.logic.balanceprovider.IBalanceProvider;
import com.boris.fundingarbitrage.logic.balancespolicy.IBalancesPolicy;
import com.boris.fundingarbitrage.logic.opportunityanalyzer.IOpportunityAnalyzer;
import com.boris.fundingarbitrage.model.exchange.ExchangeBalance;
import com.boris.fundingarbitrage.model.exchange.constantdata.FuturesConstantData;
import com.boris.fundingarbitrage.model.exchange.constantdata.SpotConstantData;
import com.boris.fundingarbitrage.model.exchange.exchangedata.FuturesExchangeData;
import com.boris.fundingarbitrage.model.exchange.exchangedata.SpotExchangeData;
import com.boris.fundingarbitrage.model.exchange.snapshot.FuturesSnapshot;
import com.boris.fundingarbitrage.model.exchange.snapshot.SpotSnapshot;
import com.boris.fundingarbitrage.monitor.CoinMonitor;
import com.boris.fundingarbitrage.scheduler.ModifiableScheduler;
import com.boris.fundingarbitrage.scheduler.ModifiableSchedulerBuilder;
import com.boris.fundingarbitrage.strategy.intradestrategy.factory.InTradeStrategyFactory;
import com.boris.fundingarbitrage.strategy.pretradestrategy.PreTradeStrategy;
import com.boris.fundingarbitrage.util.coinvector.CoinVector;
import lombok.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public abstract class ArbitrageLogic {
	private static final Logger log = LoggerFactory.getLogger(ArbitrageLogic.class);
	protected final ModifiableSchedulerBuilder schedulerBuilder;
	protected final PreTradeStrategy preTradeStrategy;
	protected final InTradeStrategyFactory inTradeStrategyFactory;
	protected final CoinAvailabilityRecord coinAvailability;
	protected final ConstantDataRecord constantDataRecord;
	protected final ArbitrageBotConfig config;
	protected final CoinMonitor monitor;
	protected final IOpportunityAnalyzer opportunityAnalyzer;
	private final IBalancesPolicy balancesPolicy;
	protected CompletableFuture<Void> initFuture;
	private ModifiableScheduler logScheduler;
	private ModifiableScheduler opportunitiesProcessingTask;
	private volatile boolean shuttingDown = false;
	private volatile boolean logOnThisCycle = false;

	public ArbitrageLogic(
					CoinMonitor monitor,
					IOpportunityAnalyzer opportunityAnalyzer,
					PreTradeStrategy preTradeStrategy,
					InTradeStrategyFactory inTradeStrategyFactory,
					CoinAvailabilityRecord coinAvailability,
					ConstantDataRecord constantDataRecord,
					ArbitrageBotConfig arbConfig,
					IBalancesPolicy balancesPolicy,
					ModifiableSchedulerBuilder schedulerBuilder
	) {
		this.monitor = monitor;
		this.opportunityAnalyzer = opportunityAnalyzer;
		this.preTradeStrategy = preTradeStrategy;
		this.inTradeStrategyFactory = inTradeStrategyFactory;
		this.coinAvailability = coinAvailability;
		this.constantDataRecord = constantDataRecord;
		this.config = arbConfig;
		this.balancesPolicy = balancesPolicy;
		this.schedulerBuilder = schedulerBuilder;
	}

	public CompletableFuture<Void> init(IBalanceProvider balanceProvider) {
		CompletableFuture<Void> balancesFuture = balanceProvider.loadBalances()
						.thenAccept((balances) -> {
							balancesPolicy.validateBalancesMap(balances);
							afterBalancesLoaded(balances);
						});
		return initFuture = CompletableFuture.allOf(monitor.getInitFuture(), balancesFuture);
	}

	public void start() {
		if (initFuture == null) throw new IllegalStateException("Call ArbitrageLogic.init() before calling start().");
		initFuture.thenRun(this::startProcessingOpportunities);
	}

	void startProcessingOpportunities() {
		log.info("Starting arbitrage logic...");

		int logInterval = config.loggingIntervalMs();
		if (logInterval > 0) logScheduler = schedulerBuilder.create(() -> this.logOnThisCycle = true, logInterval);
		opportunitiesProcessingTask = schedulerBuilder.create(this::doTick, 50);
	}

	void doTick() {
		var bestOpportunities = opportunityAnalyzer.processCoins(this::getFuturesExchangeData, this::getSpotExchangeData)
						.join();

		try {
			processTick(bestOpportunities);
		} catch (Exception e) {
			log.error("Exception while processing tick: {}", e.getMessage());
			shutdown();
			throw new RuntimeException(e);
		}

		if (logOnThisCycle) {
			logData(bestOpportunities);
			logOnThisCycle = false;
		}
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

	void logData(@NonNull CoinVector<CoinOpportunity> bestOpportunities) {
		List<Map.Entry<String, CoinOpportunity>> bestOps = bestOpportunities.sortDesc(Comparator.comparing(CoinOpportunity::expectedGain))
						.subList(0, Math.min(config.logBestArbSnapshotsAmount(), bestOpportunities.size()));

		log.info("The best arbitrage opportunities:");
		bestOps.forEach(entry -> {
			CoinOpportunity op = entry.getValue();
			log.info("{}: {}", entry.getKey(), preTradeStrategy.getDescription(op.longData(), op.shortData()));
		});
	}

	protected void forgetCoin(String coin) {
		coinAvailability.removeByCoin(coin);
		constantDataRecord.removeByCoin(coin);
	}

	protected void stopCalculatingBestOptions() {
		opportunitiesProcessingTask.cancelNow();
	}

	protected abstract void processTick(@NonNull CoinVector<CoinOpportunity> bestOpportunities);

	protected abstract void afterBalancesLoaded(@NonNull Map<BaseExchange, ExchangeBalance> balanceMap);

	public void shutdown() {
		if (shuttingDown) return;
		shuttingDown = true;
		if (monitor != null) {
			monitor.shutdown();
			if (!monitor.getInitFuture().isDone()) monitor.getInitFuture().cancel(true);
		}
		logScheduler.cancelNow();
		opportunityAnalyzer.shutdown();
		if (opportunitiesProcessingTask != null) opportunitiesProcessingTask.cancelNow();
		log.info("Arbitrage logic stopped.");
	}
}
