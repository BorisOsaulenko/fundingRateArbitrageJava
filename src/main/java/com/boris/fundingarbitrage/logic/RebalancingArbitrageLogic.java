package com.boris.fundingarbitrage.logic;

import com.boris.fundingarbitrage.coinParser.ICoinSupplier;
import com.boris.fundingarbitrage.coinfilter.CoinFilterConfig;
import com.boris.fundingarbitrage.exchange.BaseExchange;
import com.boris.fundingarbitrage.exchange.Instances;
import com.boris.fundingarbitrage.logic.coinopportunities.CoinOpportunity;
import com.boris.fundingarbitrage.logic.coinopportunities.CrossCoinOpportunity;
import com.boris.fundingarbitrage.logic.coinopportunities.SingleCoinOpportunity;
import com.boris.fundingarbitrage.model.assetops.InternalAccount;
import com.boris.fundingarbitrage.model.assetops.InternalTransfer;
import com.boris.fundingarbitrage.model.assetops.Leverages;
import com.boris.fundingarbitrage.model.exchange.ExchangeData;
import com.boris.fundingarbitrage.strategy.pretradestrategy.CrossPreTradeStrategy;
import com.boris.fundingarbitrage.strategy.pretradestrategy.SinglePreTradeStrategy;
import com.boris.fundingarbitrage.util.coinvector.CoinVector;
import com.boris.fundingarbitrage.util.logger.Logger;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

public class RebalancingArbitrageLogic extends ArbitrageLogic {
	private final static BigDecimal rebalanceThreshold = new BigDecimal("0.3"); // 30%
	private final List<InTradeCoinLogic> inTradeCoins = new CopyOnWriteArrayList<>();
	private final List<CompletableFuture<Void>> exitFutures = new ArrayList<>();
	private final int maxParallelTrades = 1;
	private final AtomicInteger currentParallelTrades = new AtomicInteger(0);
	private Map<BaseExchange, Integer> exchangeUsageCounterMap;
	private CompletableFuture<Void> internalTransfersFuture;

	public RebalancingArbitrageLogic(
					ICoinSupplier coinSupplier,
					CrossPreTradeStrategy crossPreTradeStrategy,
					SinglePreTradeStrategy singlePreTradeStrategy,
					CoinFilterConfig filterConfig,
					ArbitrageBotConfig arbConfig
	) {
		super(coinSupplier, crossPreTradeStrategy, singlePreTradeStrategy, filterConfig, arbConfig);
	}

	@Override
	protected void afterBalanceInit(Map<BaseExchange, BigDecimal> spotB, Map<BaseExchange, BigDecimal> futuresB) {
		analyzeBalances(spotB, futuresB);
		internalTransfersFuture = doInternalTransfers(futuresB);
	}

	@Override
	protected void afterMonitorInit() {
		exchangeUsageCounterMap = new ConcurrentHashMap<>();
		for (BaseExchange ex : availableCoinsByExchange.keySet()) exchangeUsageCounterMap.put(ex, 0);
	}

	private void analyzeBalances(Map<BaseExchange, BigDecimal> spotB, Map<BaseExchange, BigDecimal> futuresB) {
		BigDecimal balancesSum = BigDecimal.ZERO;
		BigDecimal maxBalance = BigDecimal.ZERO;
		BigDecimal minBalance = new BigDecimal(Long.MAX_VALUE);

		BigDecimal requiredTotal = config.legUsdtAmount().add(config.safetyMargin());

		for (BaseExchange ex : Instances.getExchangeArray()) {
			BigDecimal spotBalance = spotB.get(ex);
			BigDecimal futuresBalance = futuresB.get(ex);
			BigDecimal totalBalance = spotBalance.add(futuresBalance);

			balancesSum = balancesSum.add(totalBalance);
			maxBalance = totalBalance.max(maxBalance);
			minBalance = totalBalance.min(minBalance);

			if (totalBalance.compareTo(requiredTotal) < 0) {
				Logger.log("Not enough balance to start arbitrage on " + ex.name);
				this.shutdown();
				throw new RuntimeException("Not enough balance to start arbitrage on " + ex.name);
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
					Map<BaseExchange, BigDecimal> futuresB
	) {
		BigDecimal requiredOnFutures = config.legUsdtAmount().add(config.safetyMargin());
		List<CompletableFuture<Void>> futures = new ArrayList<>();

		for (BaseExchange ex : Instances.getExchangeArray()) {
			BigDecimal futuresBalance = futuresB.get(ex);
			BigDecimal toTransfer = requiredOnFutures.subtract(futuresBalance);
			if (toTransfer.compareTo(BigDecimal.ZERO) <= 0) continue;
			futures.add(ex.privateHttpClient.internalTransfer(new InternalTransfer(
							InternalAccount.SPOT,
							InternalAccount.FUTURES,
							toTransfer
			)).exceptionally(t -> {
				Logger.error("Failed to transfer " + toTransfer + " to futures on " + ex.name + ": " + t.getMessage());
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

	@Override
	protected void processTick(CoinVector<CoinOpportunity> bestOpportunities) {
		if (!internalTransfersFuture.isDone()) return;
		processExits();
		processEnters(bestOpportunities);
	}

	private void processExits() {
		for (InTradeCoinLogic logic : inTradeCoins) {
			try {
				CompletableFuture<Void> exiting = logic.exitTradeIfShould();
				if (exiting != null) {
					exitFutures.add(exiting);
					inTradeCoins.remove(logic);
					currentParallelTrades.decrementAndGet();

					if (logic instanceof ClassicInCrossTradeCoinLogic) {

					} else if (logic instanceof ClassicInSingleTradeCoinLogic) {

					}
					exchangeUsageCounterMap.merge(logic.getExchanges().longEx(), -1, Integer::sum);
					exchangeUsageCounterMap.merge(logic.getExchanges().shortEx(), -1, Integer::sum);
				}
			} catch (Exception e) {
				Logger.error("Exiting trade for " + logic.getCoin() + " failed. Exit manually. " + e.getMessage());
				inTradeCoins.remove(logic);
			}
		}
	}

	private void processEnters(CoinVector<CoinOpportunity> bestOpportunities) {
		for (Map.Entry<String, CoinOpportunity> entry : bestOpportunities.entrySet()) {
			if (currentParallelTrades.get() >= maxParallelTrades) return;

			String coin = entry.getKey();
			CoinOpportunity opportunity = entry.getValue();
			if (opportunity instanceof SingleCoinOpportunity(
							BaseExchange exchange,
							BigDecimal expectedGain,
							ExchangeData data
			)) {
				if (singlePreTradeStrategy.goodToEnter(data)) enterSingleCoin(coin, (SingleCoinOpportunity) opportunity);
			} else if (opportunity instanceof CrossCoinOpportunity(
							ExchangePair exchanges,
							BigDecimal expectedGain,
							ExchangeData longData,
							ExchangeData shortData
			)) {
				if (crossPreTradeStrategy.goodToEnter(longData, shortData))
					enterCrossCoin(coin, (CrossCoinOpportunity) opportunity);
			}
		}
	}

	private void enterSingleCoin(String coin, SingleCoinOpportunity opportunity) {

	}

	private void enterCrossCoin(String coin, CrossCoinOpportunity opportunity) {
		ExchangePair exchanges = opportunity.exchanges();
		try {
			ClassicInCrossTradeCoinLogic inTradeLogic = new ClassicInCrossTradeCoinLogic(
							coin,
							monitor,
							exchanges,
							config.legUsdtAmount(),
							new Leverages(config.leverage(), config.leverage())
			);
			inTradeCoins.add(inTradeLogic);
			currentParallelTrades.incrementAndGet();
		} catch (Exception e) {
			Logger.error("Failed to initialize InTradeSingleCoinLogic: " + e.getMessage());
			exchangeUsageCounterMap.merge(exchanges.longEx(), -1, Integer::sum);
			exchangeUsageCounterMap.merge(exchanges.shortEx(), -1, Integer::sum);
			forgetCoin(coin);
		}
	}
}
