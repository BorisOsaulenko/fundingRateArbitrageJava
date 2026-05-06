package com.boris.fundingarbitrage.logic;

import com.boris.fundingarbitrage.exchange.BaseExchange;
import com.boris.fundingarbitrage.execution.factory.CoinExecutionFactory;
import com.boris.fundingarbitrage.model.assetops.InternalAccount;
import com.boris.fundingarbitrage.model.assetops.InternalTransfer;
import com.boris.fundingarbitrage.model.exchange.ExchangeBalance;
import com.boris.fundingarbitrage.model.exchange.ExchangePair;
import com.boris.fundingarbitrage.strategy.TradeMarket;
import com.boris.fundingarbitrage.strategy.intradestrategy.factory.InTradeStrategyFactory;
import com.boris.fundingarbitrage.strategy.pretradestrategy.PreTradeStrategy;
import com.boris.fundingarbitrage.strategy.pretradestrategy.TradeDirections;
import com.boris.fundingarbitrage.util.coinvector.CoinVector;
import com.boris.fundingarbitrage.util.logger.Logger;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.*;

public class RebalancingArbitrageLogic extends ArbitrageLogic {
	private final static BigDecimal rebalanceThreshold = new BigDecimal("0.3"); // 30%
	private final ScheduledExecutorService exitExecutor = Executors.newSingleThreadScheduledExecutor();
	private final Map<BaseExchange, Integer> spotUsedTimes = new ConcurrentHashMap<>();
	private final Map<BaseExchange, Integer> futuresUsedTimes = new ConcurrentHashMap<>();
	private final int maxSpotUsedTimes = 1;
	private final int maxFuturesUsedTimes = this.config.leverage();
	private final List<InTradeCoinLogic> inTradeLogicList = new ArrayList<>();
	private final CoinExecutionFactory executionFactory;
	private CompletableFuture<Void> exitFuture;
	private CompletableFuture<Void> internalTransfersFuture;

	public RebalancingArbitrageLogic(
					Set<BaseExchange> exchanges,
					PreTradeStrategy preTradeStrategy,
					InTradeStrategyFactory inTradeStrategyFactory,
					ArbitrageBotConfig arbConfig,
					CoinExecutionFactory executionFactory
	) {
		super(exchanges, preTradeStrategy, inTradeStrategyFactory, arbConfig);
		this.executionFactory = executionFactory;
		int checkExitFreqMs = 10;
		exitExecutor.scheduleAtFixedRate(this::processExits, 0, checkExitFreqMs, TimeUnit.MILLISECONDS);
	}

	@Override
	protected void afterBalanceInit(Map<BaseExchange, ExchangeBalance> balanceMap) {
		analyzeBalances(balanceMap);
		internalTransfersFuture = doInternalTransfers(balanceMap);
	}

	@Override
	protected void afterMonitorInit() {
	}

	private void analyzeBalances(Map<BaseExchange, ExchangeBalance> balanceMap) {
		BigDecimal balancesSum = BigDecimal.ZERO;
		BigDecimal maxBalance = BigDecimal.ZERO;
		BigDecimal minBalance = new BigDecimal(Long.MAX_VALUE);

		BigDecimal requiredTotal = config.legUsdtAmount()
						.add(config.safetyMargin())
						.multiply(BigDecimal.TWO); // need same amount on futures and spot

		for (BaseExchange ex : exchanges) {
			BigDecimal spotBalance = balanceMap.get(ex).spotFreeUsdt();
			BigDecimal futuresBalance = balanceMap.get(ex).futuresFreeUsdt();
			BigDecimal totalBalance = spotBalance.add(futuresBalance);

			balancesSum = balancesSum.add(totalBalance);
			maxBalance = totalBalance.max(maxBalance);
			minBalance = totalBalance.min(minBalance);

			if (totalBalance.compareTo(requiredTotal) < 0) {
				Logger.log("Not enough balance to start arbitrage on " + ex.name());
				this.shutdown();
				throw new RuntimeException("Not enough balance to start arbitrage on " + ex.name());
			}
		}

		BigDecimal avgBalance = balancesSum.divide(new BigDecimal(exchanges.size()), RoundingMode.HALF_UP);
		BigDecimal maxDiff = maxBalance.subtract(minBalance);
		BigDecimal maxDiffRelative = maxDiff.divide(avgBalance, RoundingMode.HALF_UP);
		if (maxDiffRelative.compareTo(rebalanceThreshold) > 0) {
			Logger.log("Rebalance is suggested. Max balance diff is " + maxDiffRelative + " compared to average balance");
		}
	}

	private CompletableFuture<Void> doInternalTransfers(Map<BaseExchange, ExchangeBalance> balanceMap) {
		BigDecimal required = config.legUsdtAmount().add(config.safetyMargin());
		List<CompletableFuture<Void>> futures = new ArrayList<>();

		for (BaseExchange ex : exchanges) {
			BigDecimal futuresBalance = balanceMap.get(ex).futuresFreeUsdt();
			BigDecimal spotBalance = balanceMap.get(ex).spotFreeUsdt();
			if (futuresBalance.compareTo(required) >= 0 && spotBalance.compareTo(required) >= 0) continue;

			BigDecimal toTransfer = required.subtract(futuresBalance.min(spotBalance));
			InternalAccount from = futuresBalance.compareTo(required) < 0 ? InternalAccount.SPOT : InternalAccount.FUTURES;
			InternalAccount to = from == InternalAccount.SPOT ? InternalAccount.FUTURES : InternalAccount.SPOT;

			futures.add(ex.privateHttpClient().internalTransfer(new InternalTransfer(from, to, toTransfer))
							.exceptionally(t -> {
								Logger.error("Failed to transfer %s to futures on %s: %s", toTransfer, ex.name(), t.getMessage());
								throw new RuntimeException(t);
							}));
		}

		return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
						.thenRun(() -> Logger.log("Internal transfers completed"))
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
	protected void processTick(CoinVector<CoinOpportunity> bestOpportunities) {
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
				Logger.error("Exiting trade for " + logic.getCoin() + " failed. Exit manually. " + t.getMessage());
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
							executionFactory.create(coin, op, config)
			);
			inTradeLogicList.add(logic);
		} catch (Exception e) {
			Logger.error("Failed to initialize InTradeSingleCoinLogic: " + e.getMessage());
			mergeUsageMap(op.directions(), op.exchanges(), -1);
			forgetCoin(coin);
		}
	}

	@Override
	public void shutdown() {
		super.shutdown();
		exitExecutor.shutdownNow();
		exitFuture.join();
	}
}
