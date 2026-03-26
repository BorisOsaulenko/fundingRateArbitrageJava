package com.boris.fundingarbitrage.logic;

import com.boris.fundingarbitrage.coinParser.ICoinSupplier;
import com.boris.fundingarbitrage.coinfilter.CoinFilterConfig;
import com.boris.fundingarbitrage.exchange.BaseExchange;
import com.boris.fundingarbitrage.exchange.Instances;
import com.boris.fundingarbitrage.model.arbitrage.ArbitrageConstantData;
import com.boris.fundingarbitrage.model.assetops.InternalAccount;
import com.boris.fundingarbitrage.model.assetops.InternalTransfer;
import com.boris.fundingarbitrage.model.assetops.Leverages;
import com.boris.fundingarbitrage.strategy.PreTradeStrategy;
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
	private final List<InTradeSingleCoinLogic> inTradeCoins = new CopyOnWriteArrayList<>();
	private final List<CompletableFuture<Void>> exitFutures = new ArrayList<>();
	private final int maxParallelTrades = 1;
	private final AtomicInteger currentParallelTrades = new AtomicInteger(0);
	private Map<BaseExchange, Integer> exchangeUsageCounterMap;
	private CompletableFuture<Void> internalTransfersFuture;

	public RebalancingArbitrageLogic(
					ICoinSupplier coinSupplier,
					PreTradeStrategy strategy,
					CoinFilterConfig filterConfig,
					ArbitrageBotConfig arbConfig
	) {
		super(coinSupplier, strategy, filterConfig, arbConfig);
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
		for (InTradeSingleCoinLogic logic : inTradeCoins) {
			try {
				boolean exiting = logic.exitTradeIfShould();
				if (exiting) {
					exitFutures.add(logic.getExitFuture());
					exchangeUsageCounterMap.merge(logic.getExchanges().longEx(), -1, Integer::sum);
					exchangeUsageCounterMap.merge(logic.getExchanges().shortEx(), -1, Integer::sum);
					inTradeCoins.remove(logic);
					currentParallelTrades.decrementAndGet();
				}
			} catch (Exception e) {
				Logger.error("Exiting trade for " + logic.getCoin() + " failed. Exit manually. " + e.getMessage());
				inTradeCoins.remove(logic);
			}
		}
	}

	private void processEnters(CoinVector<CoinOpportunity> bestOpportunities) {
		if (currentParallelTrades.get() >= maxParallelTrades) return;

		for (Map.Entry<String, CoinOpportunity> entry : bestOpportunities.entrySet()) {
			String coin = entry.getKey();
			BaseExchange longEx = entry.getValue().exchanges().longEx();
			BaseExchange shortEx = entry.getValue().exchanges().shortEx();

			if (!preTradeStrategy.goodToEnter(entry.getValue().data())) continue;
			if (exchangeUsageCounterMap.get(longEx) >= config.leverage()) continue;
			if (exchangeUsageCounterMap.get(shortEx) >= config.leverage()) continue;

			exchangeUsageCounterMap.merge(longEx, 1, Integer::sum);
			exchangeUsageCounterMap.merge(shortEx, 1, Integer::sum);

			enterCoin(coin, entry.getValue());
			forgetCoin(coin);
		}
	}

	private void enterCoin(String coin, CoinOpportunity opportunity) {
		ExchangePair exchanges = opportunity.exchanges();
		try {
			InTradeSingleCoinLogic inTradeLogic = new InTradeSingleCoinLogic(
							coin,
							monitor,
							exchanges,
							config.legUsdtAmount(),
							new ArbitrageConstantData(
											constantDataMap.get(exchanges.longEx(), coin),
											constantDataMap.get(exchanges.shortEx(), coin)
							),
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
