package com.boris.fundingarbitrage.logic;

import com.boris.fundingarbitrage.exchange.BaseExchange;
import com.boris.fundingarbitrage.execution.CoinExecution;
import com.boris.fundingarbitrage.model.arbitrage.ArbitrageSnapshot;
import com.boris.fundingarbitrage.model.assetops.TradeParams;
import com.boris.fundingarbitrage.model.assetops.TradeSide;
import com.boris.fundingarbitrage.model.contract.PartialFill;
import com.boris.fundingarbitrage.monitor.CoinMonitor;
import com.boris.fundingarbitrage.strategy.ClassicInTradeStrategy;
import com.boris.fundingarbitrage.strategy.InTradeStrategy;
import com.boris.fundingarbitrage.util.logger.Logger;
import lombok.NonNull;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class InTradeSingleCoinLogic {
	private final int leverage = 1;
	private final BigDecimal usdtAmount = new BigDecimal("30");

	private final String coin;
	private final CoinMonitor monitor;
	private final ExchangePair exchanges;
	private final InTradeStrategy strategy;
	private final CoinExecution execution;
	private final ArbitrageSnapshot enterSnapshot;
	private final CompletableFuture<Void> enterFuture;
	private final ScheduledExecutorService fundingRegisterExecutor = Executors.newSingleThreadScheduledExecutor();
	private volatile boolean shouldRegisterNewFunding = true;
	private int monitorCompletionId;

	public InTradeSingleCoinLogic(
					@NonNull String coin,
					@NonNull CoinMonitor monitor,
					@NonNull ExchangePair exchanges
	) {
		this.coin = coin;
		this.exchanges = exchanges;
		this.monitor = monitor;

		this.enterSnapshot = monitor.getSnapshot(exchanges, coin);
		this.strategy = new ClassicInTradeStrategy(enterSnapshot);
		this.execution = new CoinExecution(coin, getEnterParams(), leverage);
		this.enterFuture = this.execution.enterTrade();

		// Funding occurs at most once an hour, so checking every 30 minutes is sufficient to not miss any funding while also not checking too often
		this.fundingRegisterExecutor.scheduleAtFixedRate(this::registerFunding, 0, 30, TimeUnit.MINUTES);
	}

	private static BigDecimal lcm(BigDecimal a, BigDecimal b) {
		int scale = Math.max(a.scale(), b.scale());

		BigInteger aInt = a.movePointRight(scale).toBigIntegerExact();
		BigInteger bInt = b.movePointRight(scale).toBigIntegerExact();

		BigInteger gcd = aInt.gcd(bInt);
		BigInteger lcm = aInt.divide(gcd).multiply(bInt);

		return new BigDecimal(lcm, scale);
	}

	private TradeParams getEnterParams() {
		BigDecimal longLotSize = monitor.getLotSize(exchanges.longEx(), coin); // 2 COIN
		BigDecimal shortLotSize = monitor.getLotSize(exchanges.shortEx(), coin); // 3 COIN
		BigDecimal effectiveLotSize = lcm(longLotSize, shortLotSize); // 6 COIN

		BigDecimal longAsk = this.enterSnapshot.longExchange().bookTicker().askPrice(); // 10 usdt/COIN
		BigDecimal shortBid = this.enterSnapshot.shortExchange().bookTicker().bidPrice(); // 15 usdt/COIN

		BigDecimal longELSMultiplier = usdtAmount // 300 usdt
						.divide(longAsk, RoundingMode.HALF_DOWN)
						.divide(effectiveLotSize, RoundingMode.FLOOR); // 5 -

		BigDecimal shortELSMultiplier = usdtAmount
						.divide(shortBid, RoundingMode.HALF_DOWN)
						.divide(effectiveLotSize, RoundingMode.FLOOR); // 3 -

		BigDecimal effectiveELSMultiplier = longELSMultiplier.min(shortELSMultiplier); // 3 -
		BigDecimal baseAssetQty = effectiveELSMultiplier.multiply(effectiveLotSize); // 18 COIN

		int longContractQty = baseAssetQty.divide(longLotSize, RoundingMode.FLOOR).intValueExact(); // 9 contracts
		int shortContractQty = baseAssetQty.divide(shortLotSize, RoundingMode.FLOOR).intValueExact(); // 6 contracts

		return new TradeParams(
						exchanges.longEx(),
						exchanges.shortEx(),
						baseAssetQty,
						longContractQty,
						shortContractQty
		);
	}

	private void registerFunding() {
		if (!shouldRegisterNewFunding) return;
		shouldRegisterNewFunding = false;

		ArbitrageSnapshot currentSnapshot = monitor.getSnapshot(exchanges, coin);
		long closestFundingTimestamp = currentSnapshot.closestSettlement().toEpochMilli();
		monitorCompletionId = monitor.performOnTimestamp(
						closestFundingTimestamp, exchanges, coin, sn -> {
							strategy.addFundingSnapshot(sn);
							shouldRegisterNewFunding = true;
						}
		);
	}

	public CompletableFuture<Void> exitTrade() {
		if (enterFuture.isDone()) return CompletableFuture.completedFuture(null);
		monitor.cancelTimestampCompletion(monitorCompletionId);
		shouldRegisterNewFunding = false;
		fundingRegisterExecutor.shutdownNow();
		return execution.exitTrade();
	}

	private CompletableFuture<Void> logTradeInfo() {
		CompletableFuture<List<PartialFill>> LEnterFuture = getOrderRecordFuture(exchanges.longEx(), TradeSide.OPEN);
		CompletableFuture<List<PartialFill>> SEnterFuture = getOrderRecordFuture(exchanges.shortEx(), TradeSide.OPEN);

		CompletableFuture<List<PartialFill>> LExitFuture = getOrderRecordFuture(exchanges.longEx(), TradeSide.CLOSE);
		CompletableFuture<List<PartialFill>> SExitFuture = getOrderRecordFuture(exchanges.shortEx(), TradeSide.CLOSE);

		return CompletableFuture.allOf(LEnterFuture, SEnterFuture, LExitFuture, SExitFuture).thenAccept(_ -> {
			List<PartialFill> longEnterFills = LEnterFuture.join();
			List<PartialFill> shortEnterFills = SEnterFuture.join();

			List<PartialFill> longExitFills = LExitFuture.join();
			List<PartialFill> shortExitFills = SExitFuture.join();

			Logger.log("Trade info for " + coin + ": ");
			Logger.log(getLongEnterLog(enterSnapshot.longExchange().bookTicker().askPrice(), getAvgPrice(longEnterFills)));
		});
	}

	private CompletableFuture<List<PartialFill>> getOrderRecordFuture(BaseExchange ex, TradeSide tradeSide) {
		return ex.privateHttpClient.getOrderRecord(execution.getEnterIds().longId(), coin, tradeSide)
						.thenApply(fills -> {
							if (fills.isEmpty()) {
								Logger.log(ex.name + " getOrderRecord returned empty list");
								throw new RuntimeException("getOrderRecord returned empty list");
							}

							return fills;
						})
						.exceptionally(t -> {
							Logger.log(ex.name + " getOrderRecord failed: " + t.getMessage());
							throw new RuntimeException(t);
						});
	}

	private BigDecimal getAvgPrice(List<PartialFill> fills) {
		BigDecimal totalQty = fills.stream()
						.map(PartialFill::baseAssetQty)
						.reduce(BigDecimal.ZERO, BigDecimal::add);

		BigDecimal totalCost = fills.stream()
						.map(fill -> fill.baseAssetQty().multiply(fill.price()))
						.reduce(BigDecimal.ZERO, BigDecimal::add);

		return totalCost.divide(totalQty, RoundingMode.HALF_DOWN);
	}

	private String getLongEnterLog(BigDecimal o, BigDecimal a) {
		BigDecimal s = o.subtract(a);
		char ss = s.compareTo(BigDecimal.ZERO) > 0 ? '+' : '-';
		return "[Enter] [Long] Observed enter price: " + o + ", actual avg price: " + a + ". Slippage: " + ss + s;
	}
}
