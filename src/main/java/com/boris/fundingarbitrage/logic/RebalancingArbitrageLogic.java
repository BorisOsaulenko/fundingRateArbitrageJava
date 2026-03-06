package com.boris.fundingarbitrage.logic;

import com.boris.fundingarbitrage.coinfilter.CoinFilterConfig;
import com.boris.fundingarbitrage.exchange.BaseExchange;
import com.boris.fundingarbitrage.exchange.Instances;
import com.boris.fundingarbitrage.model.arbitrage.ArbitrageSnapshot;
import com.boris.fundingarbitrage.model.assetops.EnterParams;
import com.boris.fundingarbitrage.model.assetops.InternalAccount;
import com.boris.fundingarbitrage.model.assetops.InternalTransfer;
import com.boris.fundingarbitrage.model.exchange.ExchangeSnapshot;
import com.boris.fundingarbitrage.strategy.ArbitrageStrategy;
import com.boris.fundingarbitrage.strategy.ClassicArbitrageStrategy;
import com.boris.fundingarbitrage.util.coinvector.CoinVector;
import com.boris.fundingarbitrage.util.logger.Logger;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class RebalancingArbitrageLogic extends ArbitrageLogic {
	private final BigDecimal rebalanceThreshold = new BigDecimal("0.4");
	private final Duration beforeEnter = Duration.ofSeconds(6);
	private final int maxParallelTrades = 1;
	private final List<String> coinsToEnter = new ArrayList<>();
	private final CoinVector<Integer> tradeIds = new CoinVector<>();
	private final CoinVector<ExchangeOrderIds> enterOrderIds = new CoinVector<>();
	private final CoinVector<ExchangeOrderIds> exitOrderIds = new CoinVector<>();
	private final CoinVector<ArbitrageStrategy> strategies = new CoinVector<>();
	private CompletableFuture<Void> internalTransfersFuture;
	private Instant enterTime;
	private boolean ready = false;
	private boolean tradesEntered = false;

	public RebalancingArbitrageLogic(
					ArbitrageStrategy strategy,
					ArbitrageBotConfig arbConfig,
					CoinFilterConfig filterConfig
	) {
		super(strategy, arbConfig, filterConfig);
		this.balancesFuture.thenAccept(v -> {
			checkRebalanceNeed();
			checkEnoughBalance();
			if (shutdown) return;
			internalTransfersFuture = proceedInternalTransfers();
			disconnectLaterOpportunities();
			adjustFrequencyToRecommended();
			ready = true;
		});
	}

	public static BigDecimal lcm(BigDecimal a, BigDecimal b) {
		int scale = Math.max(a.scale(), b.scale());

		BigInteger aInt = a.movePointRight(scale).toBigIntegerExact();
		BigInteger bInt = b.movePointRight(scale).toBigIntegerExact();

		BigInteger gcd = aInt.gcd(bInt);
		BigInteger lcm = aInt.divide(gcd).multiply(bInt);

		return new BigDecimal(lcm, scale);
	}

	private void checkRebalanceNeed() {
		BaseExchange maxBalanceExchange = null;
		BigDecimal maxBalance = null;

		BaseExchange minBalanceExchange = null;
		BigDecimal minBalance = null;

		BigDecimal balanceSum = BigDecimal.ZERO;

		for (BaseExchange exchange : Instances.getExchangeArray()) {
			BigDecimal spot = spotBalances.get(exchange);
			BigDecimal futures = futuresBalances.get(exchange);
			BigDecimal total = spot.add(futures);
			balanceSum = balanceSum.add(total);

			if (maxBalance == null || total.compareTo(maxBalance) > 0) {
				maxBalance = total;
				maxBalanceExchange = exchange;
			}

			if (minBalance == null || total.compareTo(minBalance) < 0) {
				minBalance = total;
				minBalanceExchange = exchange;
			}
		}

		assert maxBalanceExchange != null;
		assert minBalanceExchange != null;

		BigDecimal avgBalance = balanceSum.divide(BigDecimal.valueOf(spotBalances.size()), 8, RoundingMode.HALF_EVEN);
		BigDecimal maxMinDiff = maxBalance.subtract(minBalance);
		BigDecimal maxMinDiffPercent = maxMinDiff.divide(avgBalance, 8, RoundingMode.HALF_EVEN);
		if (maxMinDiffPercent.compareTo(rebalanceThreshold) > 0) {
			Logger.warn(String.format(
							"Rebalance is recommended: maxMinDiffPercent=%s on exchanges: %s (%f) | %s (%f)",
							maxMinDiffPercent,
							minBalanceExchange.name,
							minBalance,
							maxBalanceExchange.name,
							maxBalance
			));
		}
	}

	private void checkEnoughBalance() {
		BigDecimal minBalanceRequired = config.legUsdtAmount();

		for (BaseExchange exchange : Instances.getExchangeArray()) {
			BigDecimal spot = spotBalances.get(exchange);
			BigDecimal futures = futuresBalances.get(exchange);
			BigDecimal total = spot.add(futures);

			if (total.compareTo(minBalanceRequired) < 0) {
				Logger.error(String.format(
								"Low balance on exchange %s: total=%f, required=%f. Shutting down.",
								exchange.name,
								total,
								minBalanceRequired
				));
				this.shutdown();
			}
		}
	}

	private CompletableFuture<Void> proceedInternalTransfers() {
		List<CompletableFuture<Void>> futures = new ArrayList<>();

		for (BaseExchange exchange : Instances.getExchangeArray()) {
			BigDecimal futuresBalance = futuresBalances.get(exchange);
			BigDecimal toTransfer = config.legUsdtAmount().subtract(futuresBalance).max(BigDecimal.ZERO);

			if (toTransfer.equals(BigDecimal.ZERO)) continue;

			InternalTransfer transfer = new InternalTransfer(InternalAccount.SPOT, InternalAccount.FUTURES, toTransfer);
			CompletableFuture<Void> transferFuture = exchange.privateHttpClient.internalTransfer(transfer);
			futures.add(transferFuture);
		}

		return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
	}

	private void disconnectLaterOpportunities() {
		ArbitrageSnapshot closestOpportunity = Objects.requireNonNull(bestArbSnapshots.getMinEntry(Comparator.comparing(
						ArbitrageSnapshot::closestSettlement))).getValue();

		for (Map.Entry<String, ArbitrageSnapshot> snapshotEntry : bestArbSnapshots.entrySet()) {
			Instant settlementTime = snapshotEntry.getValue().closestSettlement();
			if (settlementTime.equals(closestOpportunity.closestSettlement())) continue;
			String coin = snapshotEntry.getKey();
			stopCalculatingBestOptionsForCoin(coin);
			this.monitor.unsubscribeCoin(coin);
		}

		enterTime = closestOpportunity.closestSettlement();
	}

	@Override
	protected void processTick() {
		if (!ready) return;
		if (tradesEntered) {
			processExits();
			return;
		}

		boolean enterClose = Instant.now().plus(beforeEnter).isAfter(enterTime);
		if (!enterClose) return;

		findCoinsToEnter();
		doEnter();
		stopCalculatingBestOptionsForAllCoins();
		collapseNonEnteredCoins();
		tradesEntered = true;
	}

	private void findCoinsToEnter() {
		List<Map.Entry<String, ArbitrageSnapshot>> bestOpportunities = bestArbSnapshots.filter(strategy::snapshotGoodEnough)
						.sortDesc(strategy::compareSnapshots);
		Set<BaseExchange> usedExchanges = new HashSet<>();

		int qualifiedQty = 0;
		for (Map.Entry<String, ArbitrageSnapshot> entry : bestOpportunities) {
			if (qualifiedQty >= maxParallelTrades) break;

			ExchangePair exchanges = bestArbExchanges.get(entry.getKey());
			if (usedExchanges.contains(exchanges.longEx())) continue;
			if (usedExchanges.contains(exchanges.shortEx())) continue;

			qualifiedQty++;
			usedExchanges.add(exchanges.longEx());
			usedExchanges.add(exchanges.shortEx());
			coinsToEnter.add(entry.getKey());
		}

		if (coinsToEnter.isEmpty()) {
			Logger.log("No opportunities are good enough to enter. Shutting down.");
			this.shutdown();
		}
	}

	private void doEnter() {
		if (this.shutdown) return;
		List<CompletableFuture<Void>> futures = new ArrayList<>();
		for (String coin : coinsToEnter) {
			EnterParams params = getEnterParams(coin);
			ArbitrageSnapshot enterSnapshot = bestArbSnapshots.get(coin);
			ArbitrageStrategy coinStrategy = new ClassicArbitrageStrategy();
			coinStrategy.setEnterSnapshot(enterSnapshot);
			strategies.put(coin, coinStrategy);
			var future = this.execution.enterTrade(params).thenAccept(ids -> {
				tradeIds.put(coin, ids.internalTradeId());
				enterOrderIds.put(coin, new ExchangeOrderIds(ids.longId(), ids.shortId()));
			});
			futures.add(future);
		}

		CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
	}

	private void collapseNonEnteredCoins() {
		for (String coin : availableExchangesByCoin.keySet()) {
			if (!coinsToEnter.contains(coin)) this.monitor.unsubscribeCoin(coin);
			else {
				Set<BaseExchange> exchanges = availableExchangesByCoin.get(coin);
				ExchangePair exchangesToKeep = bestArbExchanges.get(coin);
				assert exchangesToKeep != null && exchanges != null;

				for (BaseExchange toUnsubscribe : exchanges) {
					if (toUnsubscribe == exchangesToKeep.longEx() || toUnsubscribe == exchangesToKeep.shortEx()) continue;
					this.monitor.unsubscribeCoinExchange(coin, toUnsubscribe);
				}
			}
		}
	}

	private EnterParams getEnterParams(String coin) {
		var exchanges = bestArbExchanges.get(coin);
		var snapshot = bestArbSnapshots.get(coin);
		assert exchanges != null && snapshot != null;

		BigDecimal longLotSize = selector.getLotSize(exchanges.longEx(), coin); // 2 COIN
		BigDecimal shortLotSize = selector.getLotSize(exchanges.shortEx(), coin); // 3 COIN
		BigDecimal effectiveLotSize = lcm(longLotSize, shortLotSize); // 6 COIN

		BigDecimal longAsk = snapshot.longExchange().bookTicker().askPrice(); // 10 usdt/COIN
		BigDecimal shortBid = snapshot.shortExchange().bookTicker().bidPrice(); // 15 usdt/COIN

		BigDecimal longELSMultiplier = config.legUsdtAmount() // 300 usdt
						.divide(longAsk, RoundingMode.HALF_DOWN)
						.divide(effectiveLotSize, RoundingMode.FLOOR); // 5 -

		BigDecimal shortELSMultiplier = config.legUsdtAmount()
						.divide(shortBid, RoundingMode.HALF_DOWN)
						.divide(effectiveLotSize, RoundingMode.FLOOR); // 3 -

		BigDecimal effectiveELSMultiplier = longELSMultiplier.min(shortELSMultiplier); // 3 -
		BigDecimal baseAssetQty = effectiveELSMultiplier.multiply(effectiveLotSize); // 18 COIN

		int longContractQty = baseAssetQty.divide(longLotSize, RoundingMode.FLOOR).intValueExact(); // 9 contracts
		int shortContractQty = baseAssetQty.divide(shortLotSize, RoundingMode.FLOOR).intValueExact(); // 6 contracts

		return new EnterParams(
						coin,
						exchanges.longEx(),
						exchanges.shortEx(),
						baseAssetQty,
						longContractQty,
						shortContractQty
		);
	}

	private void processExits() {
		for (String coin : coinsToEnter) {
			ExchangePair exchanges = bestArbExchanges.get(coin);
			assert exchanges != null;

			ExchangeSnapshot longSnapshot = monitor.getSnapshot(exchanges.longEx(), coin);
			ExchangeSnapshot shortSnapshot = monitor.getSnapshot(exchanges.shortEx(), coin);
			assert longSnapshot != null && shortSnapshot != null;
		}
	}

	private record ExchangeOrderIds(String longId, String shortId) {
	}
}
