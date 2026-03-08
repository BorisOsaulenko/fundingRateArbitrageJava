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
import lombok.Getter;
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
	private final BigDecimal usdtAmount;

	private final String coin;
	private final CoinMonitor monitor;
	private final ExchangePair exchanges;
	private final InTradeStrategy strategy;
	private final CoinExecution execution;
	private final ArbitrageSnapshot enterSnapshot;
	private final CompletableFuture<Void> enterFuture;
	private final ScheduledExecutorService fundingRegisterExecutor = Executors.newSingleThreadScheduledExecutor();
	@Getter private CompletableFuture<Void> exitFuture = null;
	private volatile boolean shouldRegisterNewFunding = true;
	private int monitorCompletionId;
	private BigDecimal baseAssetQty;

	public InTradeSingleCoinLogic(
					@NonNull String coin,
					@NonNull CoinMonitor monitor,
					@NonNull ExchangePair exchanges,
					@NonNull BigDecimal usdtAmount
	) {
		this.coin = coin;
		this.exchanges = exchanges;
		this.monitor = monitor;
		this.usdtAmount = usdtAmount;

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
		baseAssetQty = effectiveELSMultiplier.multiply(effectiveLotSize); // 18 COIN

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

	public boolean exitTradeIfShould() {
		if (!enterFuture.isDone()) throw new RuntimeException("Enter trade not completed yet");

		ArbitrageSnapshot current = monitor.getSnapshot(exchanges, coin);
		if (!strategy.shouldExitTrade(current)) return false;

		monitor.cancelTimestampCompletion(monitorCompletionId);
		shouldRegisterNewFunding = false;
		fundingRegisterExecutor.shutdownNow();
		this.exitFuture = execution.exitTrade().thenCompose(v -> logTradeInfo(current));

		return true;
	}

	private CompletableFuture<Void> logTradeInfo(ArbitrageSnapshot exitSnapshot) {
		CompletableFuture<List<PartialFill>> LEnterFuture = getOrderRecordFuture(
						exchanges.longEx(),
						execution.getEnterIds().longId(),
						TradeSide.OPEN
		);
		CompletableFuture<List<PartialFill>> SEnterFuture = getOrderRecordFuture(
						exchanges.shortEx(),
						execution.getEnterIds().shortId(),
						TradeSide.OPEN
		);

		CompletableFuture<List<PartialFill>> LExitFuture = getOrderRecordFuture(
						exchanges.longEx(),
						execution.getExitIds().longId(),
						TradeSide.CLOSE
		);
		CompletableFuture<List<PartialFill>> SExitFuture = getOrderRecordFuture(
						exchanges.shortEx(),
						execution.getExitIds().shortId(),
						TradeSide.CLOSE
		);

		return CompletableFuture.allOf(LEnterFuture, SEnterFuture, LExitFuture, SExitFuture).thenAccept(_ -> {
			List<PartialFill> longEnterFills = LEnterFuture.join();
			List<PartialFill> shortEnterFills = SEnterFuture.join();

			List<PartialFill> longExitFills = LExitFuture.join();
			List<PartialFill> shortExitFills = SExitFuture.join();

			BigDecimal oLongEnterPrice = enterSnapshot.longExchange().bookTicker().askPrice();
			BigDecimal oShortEnterPrice = enterSnapshot.shortExchange().bookTicker().bidPrice();
			BigDecimal oLongExitPrice = exitSnapshot.longExchange().bookTicker().bidPrice();
			BigDecimal oShortExitPrice = exitSnapshot.shortExchange().bookTicker().askPrice();

			BigDecimal avgLongEnterPrice = getAvgPrice(longEnterFills);
			BigDecimal avgShortEnterPrice = getAvgPrice(shortEnterFills);
			BigDecimal avgLongExitPrice = getAvgPrice(longExitFills);
			BigDecimal avgShortExitPrice = getAvgPrice(shortExitFills);

			BigDecimal longEnterFeeRate = enterSnapshot.longExchange().fees().openTaker();
			BigDecimal shortEnterFeeRate = enterSnapshot.shortExchange().fees().openTaker();
			BigDecimal longExitFeeRate = exitSnapshot.longExchange().fees().closeTaker();
			BigDecimal shortExitFeeRate = exitSnapshot.shortExchange().fees().closeTaker();

			List<ArbitrageSnapshot> fundingSnapshots = strategy.getFundingSnapshots();

			Logger.log("Trade info for " + coin + ": ");
			logTradeStep(oLongEnterPrice, avgLongEnterPrice, longEnterFeeRate, true, true);
			logTradeStep(oShortEnterPrice, avgShortEnterPrice, shortEnterFeeRate, false, true);
			BigDecimal totalFundingGain = logFundingSteps(fundingSnapshots);
			logTradeStep(oLongExitPrice, avgLongExitPrice, longExitFeeRate, true, false);
			logTradeStep(oShortExitPrice, avgShortExitPrice, shortExitFeeRate, false, false);

			Logger.log("Observed vs executed total gain (for one coin): ");
			Logger.log("[Same] Funding gain: " + totalFundingGain);
			var o = calculateGainFromPriceMoves(oLongEnterPrice, oShortEnterPrice, oLongExitPrice, oShortExitPrice);
			var e = calculateGainFromPriceMoves(avgLongEnterPrice, avgShortEnterPrice, avgLongExitPrice, avgShortExitPrice);
			Logger.log("[Observed] Price moves: " + o);
			Logger.log("[Executed] Price moves: " + e);
			Logger.log("[Observed] Total gain: " + o.add(totalFundingGain));
			Logger.log("[Executed] Total gain: " + e.add(totalFundingGain));
		});
	}

	private CompletableFuture<List<PartialFill>> getOrderRecordFuture(
					BaseExchange ex,
					String orderId,
					TradeSide tradeSide
	) {
		return ex.privateHttpClient.getOrderRecord(orderId, coin, tradeSide)
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

	private void logTradeStep(BigDecimal o, BigDecimal a, BigDecimal fr, boolean isLong, boolean isEnter) {
		BigDecimal s = o.subtract(a);
		if ((!isLong && isEnter) || (isLong && !isEnter)) s = s.negate();
		char ss = getSlippageSign(s);

		String tag1 = isEnter ? "[Enter] " : "[Exit] ";
		String tag2 = isLong ? "[Long] " : "[Short] ";

		Logger.log(tag1 + tag2 + "Observed price: " + o + ", actual avg price: " + a + ". Slippage: " + ss + s);
		Logger.log(tag1 + tag2 + "Fee: " + a.multiply(fr));
	}

	private BigDecimal logFundingSteps(List<ArbitrageSnapshot> snapshots) {
		Logger.log("Registered funding transactions: ");
		BigDecimal totalFundingGain = BigDecimal.ZERO;

		for (ArbitrageSnapshot sn : snapshots) {
			BigDecimal longMark = sn.longExchange().markPrice().price();
			BigDecimal longFunding = sn.longExchange().fundingRate().rate().multiply(longMark).negate();
			BigDecimal shortMark = sn.shortExchange().markPrice().price();
			BigDecimal shortFunding = sn.shortExchange().fundingRate().rate().multiply(shortMark);

			totalFundingGain = totalFundingGain.add(longFunding).add(shortFunding);

			Logger.log("[Funding] [Long] Received funding: " + longFunding);
			Logger.log("[Funding] [Short] Received funding: " + shortFunding);
		}

		return totalFundingGain;
	}

	private BigDecimal calculateGainFromPriceMoves(
					BigDecimal longEnter,
					BigDecimal shortEnter,
					BigDecimal longExit,
					BigDecimal shortExit
	) {
		BigDecimal longGain = longExit.subtract(longEnter);
		BigDecimal shortGain = shortEnter.subtract(shortExit);
		return longGain.add(shortGain);
	}

	private char getSlippageSign(BigDecimal slippage) {
		return slippage.compareTo(BigDecimal.ZERO) >= 0 ? '+' : ' ';
	}
}
