package com.boris.fundingarbitrage.logic;

import com.boris.fundingarbitrage.coinParser.ICoinSupplier;
import com.boris.fundingarbitrage.coinfilter.CoinFilterConfig;
import com.boris.fundingarbitrage.exchange.BaseExchange;
import com.boris.fundingarbitrage.exchange.Instances;
import com.boris.fundingarbitrage.logic.coinopportunities.CoinOpportunity;
import com.boris.fundingarbitrage.logic.coinopportunities.CrossCoinOpportunity;
import com.boris.fundingarbitrage.logic.coinopportunities.SingleCoinOpportunity;
import com.boris.fundingarbitrage.logic.crosstrade.FuturesInCrossTradeCoinLogic;
import com.boris.fundingarbitrage.logic.crosstrade.SpotInCrossTradeCoinLogic;
import com.boris.fundingarbitrage.logic.singletrade.ClassicInSingleTradeCoinLogic;
import com.boris.fundingarbitrage.model.assetops.InternalAccount;
import com.boris.fundingarbitrage.model.assetops.InternalTransfer;
import com.boris.fundingarbitrage.model.assetops.Leverages;
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
import java.util.concurrent.atomic.AtomicInteger;

public class RebalancingArbitrageLogic extends ArbitrageLogic {
	private final static BigDecimal rebalanceThreshold = new BigDecimal("0.3"); // 30%
	private final List<InTradeCoinLogic> inTradeCoins = new CopyOnWriteArrayList<>();
	private final List<CompletableFuture<Void>> exitFutures = new ArrayList<>();
	private final int maxParallelTrades = 1;
	private final AtomicInteger currentParallelTrades = new AtomicInteger(0);
	private final int checkExitFreqMs = 10;
	private final ScheduledExecutorService exitExecutor = Executors.newSingleThreadScheduledExecutor();
	private Map<BaseExchange, Integer> exchangeUsageCounterMap;
	private CompletableFuture<Void> internalTransfersFuture;

	public RebalancingArbitrageLogic(
					ICoinSupplier coinSupplier,
					PreTradeStrategy preTradeStrategy,
					CoinFilterConfig filterConfig,
					ArbitrageBotConfig arbConfig
	) {
		super(coinSupplier, preTradeStrategy, filterConfig, arbConfig);
		exitExecutor.scheduleAtFixedRate(this::processExits, 0, checkExitFreqMs, TimeUnit.MILLISECONDS);
	}

	@Override
	protected void afterBalanceInit(Map<BaseExchange, BigDecimal> spotB, Map<BaseExchange, BigDecimal> futuresB) {
		analyzeBalances(spotB, futuresB);
		internalTransfersFuture = doInternalTransfers(spotB, futuresB);
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

			futures.add(ex.privateHttpClient.internalTransfer(new InternalTransfer(from, to, toTransfer))
							.exceptionally(t -> {
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
		processEnters(bestOpportunities);
	}

	private void processExits() {
		for (InTradeCoinLogic logic : inTradeCoins) {
			CompletableFuture<Void> exiting = logic.exitTradeIfShould();
			if (exiting == null) continue;

			inTradeCoins.remove(logic);
			exitFutures.add(exiting.thenRun(() -> {
				currentParallelTrades.decrementAndGet();

				if (logic instanceof SpotInCrossTradeCoinLogic) {
					ExchangePair exchanges = ((SpotInCrossTradeCoinLogic) logic).getExchanges();
					exchangeUsageCounterMap.merge(exchanges.longEx(), -1, Integer::sum);
					exchangeUsageCounterMap.merge(exchanges.shortEx(), -1, Integer::sum);
				} else if (logic instanceof ClassicInSingleTradeCoinLogic) {
					BaseExchange exchange = ((ClassicInSingleTradeCoinLogic) logic).getExchange();
					exchangeUsageCounterMap.merge(exchange, -1, Integer::sum);
				}
			}).exceptionally(t -> {
				Logger.error("Exiting trade for " + logic.getCoin() + " failed. Exit manually. " + t.getMessage());
				return null;
			}));
		}
	}

	private void processEnters(CoinVector<CoinOpportunity> bestOpportunities) {
		List<Map.Entry<String, CoinOpportunity>> ops = bestOpportunities.sortDesc(Comparator.comparing(CoinOpportunity::expectedGain));
		for (Map.Entry<String, CoinOpportunity> entry : ops) {
			if (currentParallelTrades.get() >= maxParallelTrades) return;

			String coin = entry.getKey();
			CoinOpportunity opportunity = entry.getValue();
			if (!preTradeStrategy.goodToEnter(opportunity)) continue;

			switch (opportunity) {
				case SingleCoinOpportunity single -> enterSingleCoin(coin, single);
				case CrossCoinOpportunity cross -> enterCrossCoin(coin, cross);
			}
		}
	}

	private void enterSingleCoin(String coin, SingleCoinOpportunity opportunity) {
		BaseExchange exchange = opportunity.exchange();
		Leverages leverages = new Leverages(1, 1);
		TradeDirections directions = preTradeStrategy.single().getDirections(opportunity.data());
		exchangeUsageCounterMap.merge(exchange, 1, Integer::sum);
		try {
			ClassicInSingleTradeCoinLogic inTradeLogic = new ClassicInSingleTradeCoinLogic(
							coin,
							monitor,
							exchange,
							config.legUsdtAmount(),
							leverages,
							constantDataMap.get(exchange, coin),
							directions
			);
			inTradeCoins.add(inTradeLogic);
			currentParallelTrades.incrementAndGet();
		} catch (Exception e) {
			Logger.error("Failed to initialize InTradeSingleCoinLogic: " + e.getMessage());
			exchangeUsageCounterMap.merge(exchange, -1, Integer::sum);
			forgetCoin(coin);
		}
	}

	private void enterCrossCoin(String coin, CrossCoinOpportunity op) {
		ExchangePair exchanges = op.exchanges();
		Leverages leverages = new Leverages(1, 1);
		TradeDirections directions = preTradeStrategy.cross().getDirections(op.longData(), op.shortData());
		exchangeUsageCounterMap.merge(exchanges.longEx(), 1, Integer::sum);
		exchangeUsageCounterMap.merge(exchanges.shortEx(), 1, Integer::sum);
		try {
			FuturesInCrossTradeCoinLogic inTradeLogic = new FuturesInCrossTradeCoinLogic(
							coin,
							monitor,
							exchanges,
							config.legUsdtAmount(),
							leverages,
							constantDataMap.get(exchanges.longEx(), coin),
							constantDataMap.get(exchanges.shortEx(), coin),
							directions
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

	@Override
	public void shutdown() {
		super.shutdown();
		exitExecutor.shutdownNow();
		CompletableFuture.allOf(exitFutures.toArray(new CompletableFuture[0])).join();
	}
}
