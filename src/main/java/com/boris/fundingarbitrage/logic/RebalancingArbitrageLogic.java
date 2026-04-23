package com.boris.fundingarbitrage.logic;

import com.boris.fundingarbitrage.coinParser.ICoinSupplier;
import com.boris.fundingarbitrage.coinfilter.CoinFilterConfig;
import com.boris.fundingarbitrage.exchange.BaseExchange;
import com.boris.fundingarbitrage.exchange.Instances;
import com.boris.fundingarbitrage.intradelogic.InTradeCoinLogic;
import com.boris.fundingarbitrage.model.assetops.InternalAccount;
import com.boris.fundingarbitrage.model.assetops.InternalTransfer;
import com.boris.fundingarbitrage.model.assetops.Leverages;
import com.boris.fundingarbitrage.model.exchange.ExchangePair;
import com.boris.fundingarbitrage.strategy.TradeMarket;
import com.boris.fundingarbitrage.strategy.intradestrategy.factory.InTradeStrategyFactory;
import com.boris.fundingarbitrage.strategy.pretradestrategy.PreTradeStrategy;
import com.boris.fundingarbitrage.strategy.pretradestrategy.TradeDirections;
import com.boris.fundingarbitrage.util.coinvector.CoinVector;
import com.boris.fundingarbitrage.util.logger.Logger;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

public class RebalancingArbitrageLogic extends ArbitrageLogic {
	private final static BigDecimal rebalanceThreshold = new BigDecimal("0.3"); // 30%
	private final ScheduledExecutorService exitExecutor = Executors.newSingleThreadScheduledExecutor();
	private final Map<BaseExchange, Integer> spotUsedTimes = new ConcurrentHashMap<>();
	private final Map<BaseExchange, Integer> futuresUsedTimes = new ConcurrentHashMap<>();
	private final int maxSpotUsedTimes = 1;
	private final int maxFuturesUsedTimes = this.config.leverage();
	private final List<InTradeCoinLogic> inTradeLogicList = new ArrayList<>();
	private CompletableFuture<Void> exitFuture;
	private CompletableFuture<Void> internalTransfersFuture;

	public RebalancingArbitrageLogic(
					ICoinSupplier coinSupplier,
					PreTradeStrategy preTradeStrategy,
					InTradeStrategyFactory inTradeStrategyFactory,
					CoinFilterConfig filterConfig,
					ArbitrageBotConfig arbConfig
	) {
		super(coinSupplier, preTradeStrategy, inTradeStrategyFactory, filterConfig, arbConfig);
		int checkExitFreqMs = 10;
		exitExecutor.scheduleAtFixedRate(this::processExits, 0, checkExitFreqMs, TimeUnit.MILLISECONDS);
	}

	@Override
	protected void afterBalanceInit(Map<BaseExchange, BigDecimal> spotB, Map<BaseExchange, BigDecimal> futuresB) {
		analyzeBalances(spotB, futuresB);
		internalTransfersFuture = doInternalTransfers(spotB, futuresB);
	}

	@Override
	protected void afterMonitorInit() {
	}

	private void analyzeBalances(Map<BaseExchange, BigDecimal> spotB, Map<BaseExchange, BigDecimal> futuresB) {
		BigDecimal balancesSum = BigDecimal.ZERO;
		BigDecimal maxBalance = BigDecimal.ZERO;
		BigDecimal minBalance = new BigDecimal(Long.MAX_VALUE);

		BigDecimal requiredTotal = config.legUsdtAmount()
						.add(config.safetyMargin())
						.multiply(BigDecimal.TWO); // need same amount on futures and spot

		for (BaseExchange ex : Instances.getExchangeArray()) {
			BigDecimal spotBalance = spotB.get(ex);
			BigDecimal futuresBalance = futuresB.get(ex);
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

		BigDecimal avgBalance = balancesSum.divide(Instances.getExchangeCountBigDecimal(), RoundingMode.HALF_UP);
		BigDecimal maxDiff = maxBalance.subtract(minBalance);
		BigDecimal maxDiffRelative = maxDiff.divide(avgBalance, RoundingMode.HALF_UP);
		if (maxDiffRelative.compareTo(rebalanceThreshold) > 0) {
			Logger.log("Rebalance is suggested. Max balance diff is " + maxDiffRelative + " compared to average balance");
		}
	}

	private CompletableFuture<Void> doInternalTransfers(
					Map<BaseExchange, BigDecimal> spotB,
					Map<BaseExchange, BigDecimal> futuresB
	) {
		BigDecimal required = config.legUsdtAmount().add(config.safetyMargin());
		List<CompletableFuture<Void>> futures = new ArrayList<>();

		for (BaseExchange ex : Instances.getExchangeArray()) {
			BigDecimal futuresBalance = futuresB.get(ex);
			BigDecimal spotBalance = spotB.get(ex);
			if (futuresBalance.compareTo(required) >= 0 && spotBalance.compareTo(required) >= 0) continue;

			BigDecimal toTransfer = required.subtract(futuresBalance.min(spotBalance));
			InternalAccount from = futuresBalance.compareTo(required) < 0 ? InternalAccount.SPOT : InternalAccount.FUTURES;
			InternalAccount to = from == InternalAccount.SPOT ? InternalAccount.FUTURES : InternalAccount.SPOT;

			futures.add(ex.privateHttpClient().internalTransfer(new InternalTransfer(from, to, toTransfer))
							.exceptionally(t -> {
								Logger.error("Failed to transfer " +
														 toTransfer +
														 " to futures on " +
														 ex.name() +
														 ": " +
														 t.getMessage());
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

	private void registerTrade(TradeDirections directions, ExchangePair exchanges) {
		BaseExchange longEx = exchanges.longEx();
		BaseExchange shortEx = exchanges.shortEx();

		var longUsageMap = directions.longMarket() == TradeMarket.SPOT ? spotUsedTimes : futuresUsedTimes;
		var shortUsageMap = directions.shortMarket() == TradeMarket.SPOT ? spotUsedTimes : futuresUsedTimes;

		longUsageMap.merge(longEx, 1, Integer::sum);
		shortUsageMap.merge(shortEx, 1, Integer::sum);
	}

	private void unregisterTrade(TradeDirections directions, ExchangePair exchanges) {
		BaseExchange longEx = exchanges.longEx();
		BaseExchange shortEx = exchanges.shortEx();

		var longUsageMap = directions.longMarket() == TradeMarket.SPOT ? spotUsedTimes : futuresUsedTimes;
		var shortUsageMap = directions.shortMarket() == TradeMarket.SPOT ? spotUsedTimes : futuresUsedTimes;

		longUsageMap.merge(longEx, -1, Integer::sum);
		shortUsageMap.merge(shortEx, -1, Integer::sum);
	}

	private void enterCrossCoin(String coin, CoinOpportunity op) {
		ExchangePair exchanges = op.exchanges();
		try {
			InTradeCoinLogic logic = new InTradeCoinLogic(
							coin,
							monitor,
							inTradeStrategyFactory.create(op.longData(), op.shortData()),
							config.legUsdtAmount(),
							exchanges,
							new Leverages(config.leverage(), config.leverage()),
							op.directions(),
							op.longData().constantData(),
							op.shortData().constantData()
			);
			inTradeLogicList.add(logic);
			registerTrade(op.directions(), exchanges);
		} catch (Exception e) {
			Logger.error("Failed to initialize InTradeSingleCoinLogic: " + e.getMessage());
			unregisterTrade(op.directions(), op.exchanges());
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
