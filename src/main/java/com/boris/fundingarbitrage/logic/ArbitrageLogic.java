package com.boris.fundingarbitrage.logic;

import com.boris.fundingarbitrage.coinfilter.CoinAvailabilityRecord;
import com.boris.fundingarbitrage.exchange.BaseExchange;
import com.boris.fundingarbitrage.logic.balanceprovider.IBalanceProvider;
import com.boris.fundingarbitrage.logic.balancespolicy.IBalancesPolicy;
import com.boris.fundingarbitrage.logic.opportunityanalyzer.IOpportunityAnalyzer;
import com.boris.fundingarbitrage.model.exchange.ExchangeBalance;
import com.boris.fundingarbitrage.monitor.ICoinMonitor;
import com.boris.fundingarbitrage.scheduler.IModifiableScheduler;
import com.boris.fundingarbitrage.scheduler.IModifiableSchedulerBuilder;
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
	protected final IModifiableSchedulerBuilder schedulerBuilder;
	protected final PreTradeStrategy preTradeStrategy;
	protected final CoinAvailabilityRecord coinAvailability;
	protected final ArbitrageBotConfig config;
	protected final ICoinMonitor monitor;
	protected final IOpportunityAnalyzer opportunityAnalyzer;
	private final IBalancesPolicy balancesPolicy;
	protected CompletableFuture<Void> initFuture;
	protected IModifiableScheduler opportunitiesProcessingTask;
	private IModifiableScheduler logScheduler;
	private volatile boolean shuttingDown = false;
	private volatile boolean logOnThisCycle = false;

	public ArbitrageLogic(
					ICoinMonitor monitor,
					IOpportunityAnalyzer opportunityAnalyzer,
					PreTradeStrategy preTradeStrategy,
					CoinAvailabilityRecord coinAvailability,
					ArbitrageBotConfig arbConfig,
					IBalancesPolicy balancesPolicy,
					IModifiableSchedulerBuilder schedulerBuilder
	) {
		this.monitor = monitor;
		this.opportunityAnalyzer = opportunityAnalyzer;
		this.preTradeStrategy = preTradeStrategy;
		this.coinAvailability = coinAvailability;
		this.config = arbConfig;
		this.balancesPolicy = balancesPolicy;
		this.schedulerBuilder = schedulerBuilder;
	}

	public final CompletableFuture<Void> init(IBalanceProvider balanceProvider) {
		if (coinAvailability.coinCount() == 0) {
			log.info("No coins available in CoinAvailabilityRecord. Shutting down.");
			shutdown();
			return CompletableFuture.completedFuture(null);
		}

		CompletableFuture<Void> balancesFuture = balanceProvider.loadBalances()
						.thenAccept((balances) -> {
							balancesPolicy.validateBalancesMap(balances);
							afterBalancesLoaded(balances);
						});
		return initFuture = CompletableFuture.allOf(monitor.getInitFuture(), balancesFuture);
	}

	public final void start() {
		if (initFuture == null) throw new IllegalStateException("Call ArbitrageLogic.init() before calling start().");
		initFuture.thenRun(this::startProcessingOpportunities);
	}

	private void startProcessingOpportunities() {
		log.info("Starting arbitrage logic...");

		long logInterval = config.loggingIntervalSeconds() * 1000L;
		if (logInterval > 0) {
			logScheduler = schedulerBuilder.create(() -> this.logOnThisCycle = true, logInterval);
			logScheduler.start();
		}

		opportunitiesProcessingTask = schedulerBuilder.create(this::doTick, 50);
		opportunitiesProcessingTask.start();
	}

	private void doTick() {
		var bestOpportunities = opportunityAnalyzer.processCoins(monitor::getSnapshot).join();
		if (bestOpportunities == null || bestOpportunities.isEmpty()) {
			log.error("Opportunity analyzer returned null. Shutting down.");
			shutdown();
			return;
		}

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

	private void logData(@NonNull CoinVector<CoinOpportunity> bestOpportunities) {
		List<Map.Entry<String, CoinOpportunity>> bestOps = bestOpportunities.sortDesc(Comparator.comparing(CoinOpportunity::expectedGain))
						.subList(0, Math.min(config.logBestArbSnapshotsAmount(), bestOpportunities.size()));

		log.info("The best arbitrage opportunities:");
		bestOps.forEach(entry -> {
			CoinOpportunity op = entry.getValue();
			log.info("{}: {}", entry.getKey(), preTradeStrategy.getDescription(op.longData(), op.shortData()));
		});
	}

	protected final void stopCalculatingBestOptions() {
		opportunitiesProcessingTask.cancelNow();
	}

	protected abstract void processTick(@NonNull CoinVector<CoinOpportunity> bestOpportunities);

	protected abstract void afterBalancesLoaded(@NonNull Map<BaseExchange, ExchangeBalance> balanceMap);

	public void shutdown() {
		if (shuttingDown) return;
		shuttingDown = true;
		if (logScheduler != null) logScheduler.cancelNow();
		opportunityAnalyzer.shutdown();
		if (opportunitiesProcessingTask != null) opportunitiesProcessingTask.cancelNow();
		if (monitor != null) {
			monitor.shutdown();
			if (!monitor.getInitFuture().isDone()) monitor.getInitFuture().cancel(true);
		}
		log.info("Arbitrage logic stopped.");
	}
}
