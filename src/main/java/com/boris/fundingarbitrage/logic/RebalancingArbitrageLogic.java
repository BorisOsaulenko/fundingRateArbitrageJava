package com.boris.fundingarbitrage.logic;

import com.boris.fundingarbitrage.coinParser.ICoinSupplier;
import com.boris.fundingarbitrage.coinfilter.CoinFilterConfig;
import com.boris.fundingarbitrage.exchange.BaseExchange;
import com.boris.fundingarbitrage.exchange.Instances;
import com.boris.fundingarbitrage.model.arbitrage.ArbitrageConstantData;
import com.boris.fundingarbitrage.model.arbitrage.ArbitrageData;
import com.boris.fundingarbitrage.model.exchange.ExchangeConstantData;
import com.boris.fundingarbitrage.strategy.PreTradeStrategy;
import com.boris.fundingarbitrage.util.logger.Logger;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

public class RebalancingArbitrageLogic extends ArbitrageLogic {
	private final static BigDecimal rebalanceThreshold = new BigDecimal("0.3"); // 30%
	private final Duration beforeEnter = Duration.ofSeconds(5);
	private final Duration afterEnter = Duration.ofSeconds(5);
	private final int maxParallelTrades = 2;
	private final List<InTradeSingleCoinLogic> inTradeCoins = new CopyOnWriteArrayList<>();
	private Instant closestEnter;
	private boolean tradesEntered;
	private boolean firstTick = true;

	public RebalancingArbitrageLogic(
					ICoinSupplier coinSupplier,
					PreTradeStrategy strategy,
					CoinFilterConfig filterConfig,
					ArbitrageBotConfig arbConfig
	) {
		super(coinSupplier, strategy, filterConfig, arbConfig);
	}

	@Override
	protected void afterBalanceInit() {
		checkEnoughBalance();
		suggestRebalanceIfNeeded();
	}

	@Override
	protected void afterFirstTick() {
		disconnectLaterOpportunities();
		adjustFrequencyToRecommended();
	}

	@Override
	protected void processTick() {
		if (firstTick) {
			firstTick = false;
			return;
		}

		if (tradesEntered) {
			boolean timeToAttemptExit = Instant.now().minus(afterEnter).isAfter(closestEnter);
			if (!timeToAttemptExit) return;

			for (InTradeSingleCoinLogic logic : inTradeCoins) {
				boolean exiting = logic.exitTradeIfShould();
				if (exiting) inTradeCoins.remove(logic);
			}

			if (inTradeCoins.isEmpty()) {
				Logger.log("All trades exited. Finishing...");
				this.shutdown();
			}

			return;
		}

		boolean enterClose = Instant.now().plus(beforeEnter).isAfter(closestEnter);
		if (enterClose) {
			enterGoodCoins();
			stopCalculatingBestOptionsForAllCoins();
			adjustFrequency(10);
			tradesEntered = true;
		}
	}

	private void disconnectLaterOpportunities() {
		closestEnter = bestArbData.getMinEntry(Comparator.comparing(a -> a.snapshot().closestSettlement()))
						.getValue()
						.snapshot()
						.closestSettlement();

		for (Map.Entry<String, ArbitrageData> entry : bestArbData.entrySet()) {
			if (!entry.getValue().snapshot().closestSettlement().equals(closestEnter))
				dropCoinProcessing(entry.getKey());
		}

		Logger.log("Disconnected later opportunities. Left only ones with closest settlement: " + closestEnter);
	}

	private void checkEnoughBalance() {
		for (BaseExchange ex : Instances.getExchangeArray()) {
			BigDecimal spotBalance = spotBalances.get(ex);
			BigDecimal futuresBalance = futuresBalances.get(ex);
			BigDecimal totalBalance = spotBalance.add(futuresBalance);

			if (totalBalance.compareTo(config.legUsdtAmount()) < 0) {
				Logger.log("Not enough balance to start arbitrage on " + ex.name);
				this.shutdown();
				throw new RuntimeException("Not enough balance to start arbitrage on " + ex.name);
			}
		}
	}

	private void suggestRebalanceIfNeeded() {
		BigDecimal balancesSum = BigDecimal.ZERO;
		BigDecimal maxBalance = BigDecimal.ZERO;
		BigDecimal minBalance = new BigDecimal(Long.MAX_VALUE);

		for (BaseExchange ex : Instances.getExchangeArray()) {
			BigDecimal spotBalance = spotBalances.get(ex);
			BigDecimal futuresBalance = futuresBalances.get(ex);
			BigDecimal totalBalance = spotBalance.add(futuresBalance);

			balancesSum = balancesSum.add(totalBalance);
			maxBalance = totalBalance.max(maxBalance);
			minBalance = totalBalance.min(minBalance);
		}

		BigDecimal avgBalance = balancesSum.divide(Instances.getExchangeCountBigDecimal(), RoundingMode.HALF_UP);
		BigDecimal maxDiff = maxBalance.subtract(minBalance);
		BigDecimal maxDiffRelative = maxDiff.divide(avgBalance, RoundingMode.HALF_UP);
		if (maxDiffRelative.compareTo(rebalanceThreshold) > 0) {
			Logger.log("Rebalance is suggested. Max balance diff is " + maxDiffRelative + "% compared to average balance");
		}
	}

	private synchronized void enterGoodCoins() {
		Set<BaseExchange> freeExchanges = Instances.getExchangesSet();
		List<Map.Entry<String, ArbitrageData>> coinsToReview = this.bestArbData
						.filter(preTradeStrategy::arbDataGoodEnough)
						.sortDesc(preTradeStrategy::compareArbData);

		if (coinsToReview.isEmpty()) {
			Logger.log("No good snapshots found. Finishing...");
			this.shutdown();
			return;
		}

		int parallelTrades = 0;

		for (var entry : coinsToReview) {
			if (parallelTrades >= maxParallelTrades) break;

			String coin = entry.getKey();
			ExchangePair exchanges = bestArbExchanges.get(coin);
			assert exchanges != null;

			if (!freeExchanges.contains(exchanges.longEx()) || !freeExchanges.contains(exchanges.shortEx())) continue;
			parallelTrades++;
			freeExchanges.remove(exchanges.longEx());
			freeExchanges.remove(exchanges.shortEx());

			ExchangeConstantData longConstantData = constantDataMap.get(exchanges.longEx(), coin);
			ExchangeConstantData shortConstantData = constantDataMap.get(exchanges.shortEx(), coin);

			InTradeSingleCoinLogic logic = new InTradeSingleCoinLogic(
							coin,
							monitor,
							exchanges,
							config.legUsdtAmount(),
							new ArbitrageConstantData(longConstantData, shortConstantData)
			);
			inTradeCoins.add(logic);
		}
	}
}
