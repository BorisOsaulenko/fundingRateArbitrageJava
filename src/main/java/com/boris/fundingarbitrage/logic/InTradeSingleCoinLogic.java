package com.boris.fundingarbitrage.logic;

import com.boris.fundingarbitrage.exchange.BaseExchange;
import com.boris.fundingarbitrage.execution.CoinExecution;
import com.boris.fundingarbitrage.model.arbitrage.ArbitrageConstantData;
import com.boris.fundingarbitrage.model.arbitrage.ArbitrageData;
import com.boris.fundingarbitrage.model.arbitrage.ArbitrageSnapshot;
import com.boris.fundingarbitrage.model.assetops.Leverages;
import com.boris.fundingarbitrage.model.assetops.TradeParams;
import com.boris.fundingarbitrage.model.assetops.TradeSide;
import com.boris.fundingarbitrage.model.contract.PartialFill;
import com.boris.fundingarbitrage.monitor.CoinMonitor;
import com.boris.fundingarbitrage.strategy.ClassicInTradeStrategy;
import com.boris.fundingarbitrage.strategy.ClassicPreTradeStrategy;
import com.boris.fundingarbitrage.strategy.InTradeStrategy;
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
import java.util.concurrent.atomic.AtomicBoolean;

public class InTradeSingleCoinLogic {
	private final BigDecimal usdtAmount;
	@Getter private final String coin;
	@Getter private final ExchangePair exchanges;
	private final CoinMonitor monitor;
	private final InTradeStrategy strategy;
	private final CoinExecution execution;
	private final ArbitrageSnapshot enterSnapshot;
	private final CompletableFuture<Void> enterFuture;
	private final ScheduledExecutorService fundingRegisterExecutor = Executors.newSingleThreadScheduledExecutor();
	private final AtomicBoolean fillsFetchSuccess = new AtomicBoolean(true);
	private final ArbitrageConstantData constantData;
	private final TradeLogger tradeLogger;
	private final TradeParams enterParams;
	private long nextFundingTimestamp;
	@Getter private CompletableFuture<Void> exitFuture = null;
	private volatile boolean shouldRegisterNewFunding = true;
	private BigDecimal baseAssetQty;

	public InTradeSingleCoinLogic(
					@NonNull String coin,
					@NonNull CoinMonitor monitor,
					@NonNull ExchangePair exchanges,
					@NonNull BigDecimal usdtAmount,
					@NonNull ArbitrageConstantData constantData,
					@NonNull Leverages leverages
	) {
		this.coin = coin;
		this.exchanges = exchanges;
		this.monitor = monitor;
		this.usdtAmount = usdtAmount;
		this.constantData = constantData;
		this.enterSnapshot = monitor.getSnapshot(exchanges, coin);
		this.strategy = new ClassicInTradeStrategy(new ArbitrageData(enterSnapshot, constantData));
		this.tradeLogger = new TradeLogger(coin, exchanges);

		enterParams = getEnterParams();
		this.execution = new CoinExecution(coin, enterParams, leverages, tradeLogger);

		this.enterFuture = this.execution.enterTrade().exceptionally(this::fail);

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

	private Void fail(Throwable t) {
		tradeLogger.error("Failed to enter trade for " + coin + ". Long: "
											+ exchanges.longEx().name + " and short: " + exchanges.shortEx().name);
		tradeLogger.error("Message: " + t.getMessage());
		throw new RuntimeException(t);
	}

	private TradeParams getEnterParams() {
		BigDecimal longLotSize = constantData.longData().futuresLotSize(); // 2 COIN
		BigDecimal shortLotSize = constantData.shortData().futuresLotSize(); // 3 COIN
		BigDecimal effectiveLotSize = lcm(longLotSize, shortLotSize); // 6 COIN

		BigDecimal longAsk = enterSnapshot.longExchange().bookTicker().askPrice(); // 10 usdt/COIN
		BigDecimal shortBid = enterSnapshot.shortExchange().bookTicker().bidPrice(); // 15 usdt/COIN

		BigDecimal longELSMultiplier = usdtAmount // 300 usdt
						.divide(longAsk, RoundingMode.FLOOR)
						.divide(effectiveLotSize, RoundingMode.FLOOR); // 5 -

		BigDecimal shortELSMultiplier = usdtAmount
						.divide(shortBid, RoundingMode.FLOOR)
						.divide(effectiveLotSize, RoundingMode.FLOOR); // 3 -

		BigDecimal effectiveELSMultiplier = longELSMultiplier.min(shortELSMultiplier); // 3 -
		baseAssetQty = effectiveELSMultiplier.multiply(effectiveLotSize); // 18 COIN

		if (baseAssetQty.equals(BigDecimal.ZERO))
			throw new RuntimeException("Not enough margin deposited for coin: " + coin + ". Did not enter trades");

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
		nextFundingTimestamp = currentSnapshot.closestSettlement().toEpochMilli();
		monitor.performOnTimestamp(
						nextFundingTimestamp, exchanges, coin, sn -> {
							tradeLogger.log("Funding: [FSpread: " + ClassicPreTradeStrategy.closestFSpread(sn) + "] " + sn);
							strategy.addFundingSnapshot(sn);
							shouldRegisterNewFunding = true;
						}
		);
	}

	public boolean exitTradeIfShould() {
		if (!enterFuture.isDone()) return false;

		ArbitrageSnapshot current = monitor.getSnapshot(exchanges, coin);
		if (!strategy.shouldExitTrade(current)) return false;

		this.exitFuture = execution.exitTrade()
						.thenCompose(v -> new CompletableFuture<>().completeOnTimeout(
										null,
										5,
										TimeUnit.SECONDS
						)) // In case exit hangs for some reason, we don't want to wait indefinitely before finishing the logic and logging the trade info
						.thenCompose((v) -> finish(current));

		return true;
	}

	private CompletableFuture<Void> finish(ArbitrageSnapshot current) {
		monitor.cancelTimestampExecution(nextFundingTimestamp, exchanges, coin);
		shouldRegisterNewFunding = false;
		fundingRegisterExecutor.shutdownNow();
		return logTradeInfo(current);
	}

	private CompletableFuture<List<PartialFill>> errorHandledFillsFetch(
					BaseExchange ex,
					String orderId,
					TradeSide tradeSide,
					String name
	) {
		return ex.privateHttpClient.getFuturesOrderRecord(orderId, coin, tradeSide)
						.whenComplete((r, t) -> {
							if (t == null && r != null && !r.isEmpty()) return; // Everything good
							fillsFetchSuccess.set(false);
							if (r == null || r.isEmpty()) tradeLogger.warn("Fetched " + name + " fills: " + r);
							else tradeLogger.warn("Failed to fetch " + name + " fills: " + t.getMessage());
						});
	}

	private CompletableFuture<Void> logTradeInfo(ArbitrageSnapshot exitSnapshot) {
		CompletableFuture<List<PartialFill>> LEnterFuture = errorHandledFillsFetch(
						exchanges.longEx(),
						execution.getEnterIds().longId(),
						TradeSide.OPEN,
						"Long Enter"
		);
		CompletableFuture<List<PartialFill>> SEnterFuture = errorHandledFillsFetch(
						exchanges.shortEx(),
						execution.getEnterIds().shortId(),
						TradeSide.OPEN,
						"Short Enter"
		);

		CompletableFuture<List<PartialFill>> LExitFuture = errorHandledFillsFetch(
						exchanges.longEx(),
						execution.getExitIds().longId(),
						TradeSide.CLOSE,
						"Long Exit"
		);

		CompletableFuture<List<PartialFill>> SExitFuture = errorHandledFillsFetch(
						exchanges.shortEx(),
						execution.getExitIds().shortId(),
						TradeSide.CLOSE,
						"Short Exit"
		);

		return CompletableFuture.allOf(LEnterFuture, SEnterFuture, LExitFuture, SExitFuture)
						.whenComplete((v, t) -> {
							if (!fillsFetchSuccess.get()) return;

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

							BigDecimal longEnterFee = constantData.longData().futuresFees().openTaker().multiply(avgLongEnterPrice);
							BigDecimal shortEnterFee = constantData.shortData()
											.futuresFees()
											.openTaker()
											.multiply(avgShortEnterPrice);
							BigDecimal longExitFee = constantData.longData().futuresFees().closeTaker().multiply(avgLongExitPrice);
							BigDecimal shortExitFee = constantData.shortData().futuresFees().closeTaker().multiply(avgShortExitPrice);
							BigDecimal totalFees = longEnterFee.add(shortEnterFee).add(longExitFee).add(shortExitFee);

							List<ArbitrageSnapshot> fundingSnapshots = strategy.getFundingSnapshots();

							tradeLogger.log("Trade info for " + coin + ": ");
							logTradeStep(oLongEnterPrice, avgLongEnterPrice, longEnterFee, true, true);
							logTradeStep(oShortEnterPrice, avgShortEnterPrice, shortEnterFee, false, true);
							BigDecimal totalFundingGain = logFundingSteps(fundingSnapshots);
							logTradeStep(oLongExitPrice, avgLongExitPrice, longExitFee, true, false);
							logTradeStep(oShortExitPrice, avgShortExitPrice, shortExitFee, false, false);

							tradeLogger.log("Observed vs executed total gain (for one coin): ");
							tradeLogger.log("[Same] Funding gain: " + totalFundingGain);
							var o = calculateGainFromPriceMoves(oLongEnterPrice, oShortEnterPrice, oLongExitPrice, oShortExitPrice);
							var e = calculateGainFromPriceMoves(
											avgLongEnterPrice,
											avgShortEnterPrice,
											avgLongExitPrice,
											avgShortExitPrice
							);
							tradeLogger.log("[Observed] Price moves: " + o);
							tradeLogger.log("[Executed] Price moves: " + e);

							BigDecimal oTotalGain = o.add(totalFundingGain);
							BigDecimal eTotalGain = e.add(totalFundingGain);
							tradeLogger.log("[Observed] Total gain: " + oTotalGain);
							tradeLogger.log("[Executed] Total gain: " + eTotalGain);

							BigDecimal oAfterFees = oTotalGain.subtract(totalFees);
							BigDecimal eAfterFees = eTotalGain.subtract(totalFees);
							tradeLogger.log("[Observed] After futuresFees: " + oAfterFees);
							tradeLogger.log("[Executed] After futuresFees: " + eAfterFees);

							tradeLogger.log("[Observed] PnL: " + oAfterFees.multiply(baseAssetQty));
							tradeLogger.log("[Executed] PnL: " + eAfterFees.multiply(baseAssetQty));
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

	private void logTradeStep(BigDecimal o, BigDecimal a, BigDecimal fee, boolean isLong, boolean isEnter) {
		BigDecimal s = o.subtract(a);
		if ((!isLong && isEnter) || (isLong && !isEnter)) s = s.negate();
		char ss = getSlippageSign(s);

		String tag1 = isEnter ? "[Enter] " : "[Exit] ";
		String tag2 = isLong ? "[Long] " : "[Short] ";

		tradeLogger.log(tag1 + tag2 + "Observed price: " + o + ", actual avg price: " + a + ". Slippage: " + ss + s);
		tradeLogger.log(tag1 + tag2 + "Fee: " + fee);
	}

	private BigDecimal logFundingSteps(List<ArbitrageSnapshot> snapshots) {
		tradeLogger.log("Registered funding transactions: ");
		BigDecimal totalFundingGain = BigDecimal.ZERO;

		for (ArbitrageSnapshot sn : snapshots) {
			BigDecimal longMark = sn.longExchange().markPrice().price();
			BigDecimal longFunding = sn.longExchange().fundingRate().rate().multiply(longMark).negate();
			BigDecimal shortMark = sn.shortExchange().markPrice().price();
			BigDecimal shortFunding = sn.shortExchange().fundingRate().rate().multiply(shortMark);

			totalFundingGain = totalFundingGain.add(longFunding).add(shortFunding);

			tradeLogger.log("[Funding] [Long] Received funding: " + longFunding);
			tradeLogger.log("[Funding] [Short] Received funding: " + shortFunding);
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
