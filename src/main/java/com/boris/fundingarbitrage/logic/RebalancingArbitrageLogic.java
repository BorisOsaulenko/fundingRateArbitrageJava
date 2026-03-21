package com.boris.fundingarbitrage.logic;

import com.boris.fundingarbitrage.coinParser.ICoinSupplier;
import com.boris.fundingarbitrage.coinfilter.CoinFilterConfig;
import com.boris.fundingarbitrage.exchange.BaseExchange;
import com.boris.fundingarbitrage.exchange.Instances;
import com.boris.fundingarbitrage.model.arbitrage.ArbitrageConstantData;
import com.boris.fundingarbitrage.model.arbitrage.ArbitrageData;
import com.boris.fundingarbitrage.model.assetops.InternalAccount;
import com.boris.fundingarbitrage.model.assetops.InternalTransfer;
import com.boris.fundingarbitrage.model.assetops.Leverages;
import com.boris.fundingarbitrage.model.exchange.ExchangeConstantData;
import com.boris.fundingarbitrage.strategy.PreTradeStrategy;
import com.boris.fundingarbitrage.util.coinvector.CoinVector;
import com.boris.fundingarbitrage.util.logger.Logger;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Future;

public class RebalancingArbitrageLogic extends ArbitrageLogic {
	private final static BigDecimal rebalanceThreshold = new BigDecimal("0.3"); // 30%
	private final Duration beforeEnter = Duration.ofSeconds(5);
	private final Duration afterFunding = Duration.ofSeconds(5);
	private final List<InTradeSingleCoinLogic> inTradeCoins = new CopyOnWriteArrayList<>();
	private final List<CompletableFuture<Void>> exitFutures = new ArrayList<>();
	private CompletableFuture<Void> internalTransfersFuture;
	private boolean firstTick = true;
	private Instant closestEnter;
	private boolean tradesEntered;

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
		if (firstTick) {
			disconnectLaterOpportunities(bestOpportunities);
			adjustFrequencyToRecommended();
			firstTick = false;
			return;
		}

		if (tradesEntered) processTickAfterEnter(bestOpportunities);
		else processTickBeforeEnter(bestOpportunities);
	}

	private void processTickAfterEnter(CoinVector<CoinOpportunity> bestOpportunities) {
		boolean timeToAttemptExit = Instant.now().minus(afterFunding).isAfter(closestEnter);
		if (!timeToAttemptExit) return;

		for (InTradeSingleCoinLogic logic : inTradeCoins) {
			try {
				boolean exiting = logic.exitTradeIfShould();
				if (exiting) {
					exitFutures.add(logic.getExitFuture());
					inTradeCoins.remove(logic);
				}
			} catch (Exception e) {
				Logger.error("Exiting trade for " + logic.getCoin() + " failed. Exit manually. " + e.getMessage());
				inTradeCoins.remove(logic);
			}
		}

		if (inTradeCoins.isEmpty() && exitFutures.stream().allMatch(CompletableFuture::isDone)) {
			Logger.log("All trades exited. Finishing...");
			this.shutdown();
		}
	}

	private void processTickBeforeEnter(CoinVector<CoinOpportunity> bestOpportunities) {
		boolean enterClose = Instant.now().plus(beforeEnter).isAfter(closestEnter);
		if (enterClose) {
			if (internalTransfersFuture.state() != Future.State.SUCCESS) {
				Logger.error("Failed to perform internal transfers before enter. Shutting down...");
				this.shutdown();
				return;
			}

			Logger.log("Entering good trades...");
			enterGoodCoins(bestOpportunities);
			stopCalculatingBestOptionsForAllCoins();
			adjustFrequency(10);
			tradesEntered = true;
			Logger.log("Entered good coins. Waiting for " + afterFunding + " seconds before exiting...");
		}

	}

	@Override
	protected void logData(CoinVector<CoinOpportunity> bestOpportunities) {
		super.logData(bestOpportunities);
		if (!tradesEntered) Logger.log("Time till enter: " + Duration.between(Instant.now(), closestEnter));
		else Logger.log("Time after enter: " + Duration.between(Instant.now(), closestEnter));
	}

	private void disconnectLaterOpportunities(CoinVector<CoinOpportunity> bestOpportunities) {
		CoinVector<Instant> settlementVector = bestOpportunities.transform((op, _) -> op.data()
						.snapshot()
						.closestSettlement());
		closestEnter = settlementVector.getMinEntry(Instant::compareTo).getValue();
		var toThrowOut = settlementVector.filter((enter) -> !enter.equals(closestEnter));
		for (String coin : toThrowOut.keySet()) forgetCoin(coin);

		Logger.log("Disconnected later opportunities. Left only ones with closest settlement: " + closestEnter
							 + " (Amount: " + (bestOpportunities.size() - toThrowOut.size()) + ")");
	}

	private synchronized void enterGoodCoins(CoinVector<CoinOpportunity> bestOpportunities) {
		Map<BaseExchange, Integer> appearanceCount = new HashMap<>();
		List<Map.Entry<String, ArbitrageData>> coinsToReview = bestOpportunities
						.transform((op, _) -> op.data())
						.filter(preTradeStrategy::arbDataGoodEnough)
						.sortDesc(preTradeStrategy::compareArbData);

		if (coinsToReview.isEmpty()) {
			Logger.log("No good snapshots found. Finishing...");
			this.shutdown();
			return;
		}

		List<String> coinsToEnter = new ArrayList<>();
		for (var entry : coinsToReview) {
			String coin = entry.getKey();
			CoinOpportunity opportunity = bestOpportunities.get(coin);
			assert opportunity != null;

			ExchangePair exchanges = opportunity.exchanges();
			appearanceCount.computeIfAbsent(exchanges.longEx(), _ -> 0);
			appearanceCount.computeIfAbsent(exchanges.shortEx(), _ -> 0);

			if (appearanceCount.get(exchanges.longEx()) >= config.maxLeverage() ||
					appearanceCount.get(exchanges.shortEx()) >= config.maxLeverage()) continue;

			appearanceCount.put(exchanges.longEx(), appearanceCount.get(exchanges.longEx()) + 1);
			appearanceCount.put(exchanges.shortEx(), appearanceCount.get(exchanges.shortEx()) + 1);
			coinsToEnter.add(coin);
		}

		for (String coin : coinsToEnter) {
			CoinOpportunity opportunity = bestOpportunities.get(coin);
			assert opportunity != null;
			ExchangePair exchanges = opportunity.exchanges();

			ExchangeConstantData longConstantData = constantDataMap.get(exchanges.longEx(), coin);
			ExchangeConstantData shortConstantData = constantDataMap.get(exchanges.shortEx(), coin);

			Leverages leverages = new Leverages(
							appearanceCount.get(exchanges.longEx()),
							appearanceCount.get(exchanges.shortEx())
			);

			try {
				InTradeSingleCoinLogic logic = new InTradeSingleCoinLogic(
								coin,
								monitor,
								exchanges,
								config.legUsdtAmount(),
								new ArbitrageConstantData(longConstantData, shortConstantData),
								leverages
				);
				inTradeCoins.add(logic);
			} catch (Exception e) {
				Logger.error("Failed to initialize InTradeSingleCoinLogic: " + e.getMessage());
			}
		}
	}
}
