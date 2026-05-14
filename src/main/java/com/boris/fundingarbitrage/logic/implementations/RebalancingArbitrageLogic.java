package com.boris.fundingarbitrage.logic.implementations;

import com.boris.fundingarbitrage.coinfilter.CoinAvailabilityRecord;
import com.boris.fundingarbitrage.exchange.BaseExchange;
import com.boris.fundingarbitrage.execution.factory.CoinExecutionFactory;
import com.boris.fundingarbitrage.logic.ArbitrageBotConfig;
import com.boris.fundingarbitrage.logic.ArbitrageLogic;
import com.boris.fundingarbitrage.logic.CoinOpportunity;
import com.boris.fundingarbitrage.logic.InTradeCoinLogic;
import com.boris.fundingarbitrage.logic.balancespolicy.IBalancesPolicy;
import com.boris.fundingarbitrage.logic.opportunityanalyzer.IOpportunityAnalyzer;
import com.boris.fundingarbitrage.model.assetops.InternalAccount;
import com.boris.fundingarbitrage.model.assetops.InternalTransfer;
import com.boris.fundingarbitrage.model.exchange.ExchangeBalance;
import com.boris.fundingarbitrage.model.exchange.ExchangePair;
import com.boris.fundingarbitrage.monitor.CoinMonitor;
import com.boris.fundingarbitrage.scheduler.IModifiableScheduler;
import com.boris.fundingarbitrage.scheduler.IModifiableSchedulerBuilder;
import com.boris.fundingarbitrage.strategy.TradeMarket;
import com.boris.fundingarbitrage.strategy.intradestrategy.factory.InTradeStrategyFactory;
import com.boris.fundingarbitrage.strategy.pretradestrategy.PreTradeStrategy;
import com.boris.fundingarbitrage.strategy.pretradestrategy.TradeDirections;
import com.boris.fundingarbitrage.util.coinvector.CoinVector;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class RebalancingArbitrageLogic extends ArbitrageLogic {
	private final static Logger log = LoggerFactory.getLogger(RebalancingArbitrageLogic.class);
	private final Map<BaseExchange, Integer> spotUsedTimes = new ConcurrentHashMap<>();
	private final Map<BaseExchange, Integer> futuresUsedTimes = new ConcurrentHashMap<>();
	private final int maxSpotUsedTimes = 1;
	private final int maxFuturesUsedTimes = this.config.leverage();
	private final List<InTradeCoinLogic> inTradeLogicList = new ArrayList<>();
	private final CoinExecutionFactory executionFactory;
	private final IModifiableSchedulerBuilder schedulerBuilder;
	private final IModifiableScheduler exitScheduler;
	private CompletableFuture<Void> exitFuture;
	private CompletableFuture<Void> internalTransfersFuture;

	public RebalancingArbitrageLogic(
					CoinMonitor monitor,
					IOpportunityAnalyzer opportunityAnalyzer,
					PreTradeStrategy preTradeStrategy,
					InTradeStrategyFactory inTradeStrategyFactory,
					CoinAvailabilityRecord coinAvailability,
					ArbitrageBotConfig arbConfig,
					IBalancesPolicy balancesPolicy,
					CoinExecutionFactory executionFactory,
					IModifiableSchedulerBuilder schedulerBuilder
	) {
		super(
						monitor,
						opportunityAnalyzer,
						preTradeStrategy,
						inTradeStrategyFactory,
						coinAvailability,
						arbConfig,
						balancesPolicy,
						schedulerBuilder
		);
		this.schedulerBuilder = schedulerBuilder;
		this.executionFactory = executionFactory;
		int checkExitFreqMs = 10;
		this.exitScheduler = schedulerBuilder.create(this::processExits, checkExitFreqMs);
	}

	@Override
	public void afterBalancesLoaded(@NotNull Map<BaseExchange, ExchangeBalance> balanceMap) {
		BigDecimal required = config.legUsdtAmount().add(config.safetyMargin());
		List<CompletableFuture<Void>> futures = new ArrayList<>();

		for (Map.Entry<BaseExchange, ExchangeBalance> entry : balanceMap.entrySet()) {
			BaseExchange ex = entry.getKey();
			BigDecimal futuresBalance = entry.getValue().futuresFreeUsdt();
			BigDecimal spotBalance = entry.getValue().spotFreeUsdt();
			if (futuresBalance.compareTo(required) >= 0 && spotBalance.compareTo(required) >= 0) continue;

			BigDecimal toTransfer = required.subtract(futuresBalance.min(spotBalance));
			InternalAccount from = futuresBalance.compareTo(required) < 0 ? InternalAccount.SPOT : InternalAccount.FUTURES;
			InternalAccount to = from == InternalAccount.SPOT ? InternalAccount.FUTURES : InternalAccount.SPOT;

			futures.add(ex.privateHttpClient().internalTransfer(new InternalTransfer(from, to, toTransfer))
							.exceptionally(t -> {
								log.error("Failed to transfer {} to futures on {}: {}", toTransfer, ex.name(), t.getMessage());
								throw new RuntimeException(t);
							}));
		}

		internalTransfersFuture = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
						.thenRun(() -> log.info("Internal transfers completed"))
						.exceptionally((t) -> {
							this.shutdown();
							throw new RuntimeException(t);
						});
	}

	private boolean allowEnter(ExchangePair exchanges, TradeDirections directions) {
		BaseExchange longEx = exchanges.longEx();
		BaseExchange shortEx = exchanges.shortEx();

		var longUsageMap = directions.longMarket() == TradeMarket.SPOT ? spotUsedTimes : futuresUsedTimes;
		var shortUsageMap = directions.shortMarket() == TradeMarket.SPOT ? spotUsedTimes : futuresUsedTimes;

		return longUsageMap.getOrDefault(longEx, 0) < maxSpotUsedTimes &&
					 shortUsageMap.getOrDefault(shortEx, 0) < maxFuturesUsedTimes;
	}

	@Override
	protected void processTick(@NotNull CoinVector<CoinOpportunity> bestOpportunities) {
		if (!internalTransfersFuture.isDone()) return;
		List<Map.Entry<String, CoinOpportunity>> tickBestOps = bestOpportunities.sortDesc(Comparator.comparing(
						CoinOpportunity::expectedGain));

		for (Map.Entry<String, CoinOpportunity> entry : tickBestOps) {
			String coin = entry.getKey();
			CoinOpportunity opportunity = entry.getValue();

			if (!allowEnter(opportunity.exchanges(), opportunity.directions())) continue;
			if (!preTradeStrategy.goodToEnter(opportunity.longData(), opportunity.shortData())) continue;
			enterCrossCoin(coin, opportunity);
		}
	}

	private void processExits() {
		for (InTradeCoinLogic logic : inTradeLogicList) {
			CompletableFuture<Void> exiting = logic.exitTradeIfShould();
			if (exiting == null) return;

			exitFuture = exiting.exceptionally(t -> {
				log.error("Exiting trade for {} failed. Exit manually. {}", logic.getCoin(), t.getMessage());
				return null;
			});
		}
	}

	private void mergeUsageMap(TradeDirections directions, ExchangePair exchanges, int value) {
		BaseExchange longEx = exchanges.longEx();
		BaseExchange shortEx = exchanges.shortEx();

		var longUsageMap = directions.longMarket() == TradeMarket.SPOT ? spotUsedTimes : futuresUsedTimes;
		var shortUsageMap = directions.shortMarket() == TradeMarket.SPOT ? spotUsedTimes : futuresUsedTimes;

		longUsageMap.merge(longEx, value, Integer::sum);
		shortUsageMap.merge(shortEx, value, Integer::sum);
	}

	private void enterCrossCoin(String coin, CoinOpportunity op) {
		mergeUsageMap(op.directions(), op.exchanges(), 1);
		try {
			InTradeCoinLogic logic = new InTradeCoinLogic(
							coin,
							op,
							config,
							monitor,
							inTradeStrategyFactory.create(op.longData(), op.shortData()),
							executionFactory.create(coin, op, config),
							schedulerBuilder
			);
			inTradeLogicList.add(logic);
		} catch (Exception e) {
			log.error("Failed to initialize InTradeSingleCoinLogic: {}", e.getMessage());
			mergeUsageMap(op.directions(), op.exchanges(), -1);
			coinAvailability.removeByCoin(coin);
		}
	}

	@Override
	public void shutdown() {
		super.shutdown();
		exitScheduler.cancelNow();
		exitFuture.join();
	}
}
