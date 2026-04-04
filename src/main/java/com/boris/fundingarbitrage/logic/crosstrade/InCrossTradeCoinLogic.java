package com.boris.fundingarbitrage.logic.crosstrade;

import com.boris.fundingarbitrage.exchange.BaseExchange;
import com.boris.fundingarbitrage.execution.CrossCoinExecution;
import com.boris.fundingarbitrage.logic.ExchangePair;
import com.boris.fundingarbitrage.logic.InTradeCoinLogic;
import com.boris.fundingarbitrage.model.assetops.TradeSide;
import com.boris.fundingarbitrage.model.contract.PartialFill;
import com.boris.fundingarbitrage.model.exchange.ExchangeConstantData;
import com.boris.fundingarbitrage.model.exchange.ExchangeData;
import com.boris.fundingarbitrage.model.exchange.ExchangeSnapshot;
import com.boris.fundingarbitrage.monitor.CoinMonitor;
import com.boris.fundingarbitrage.strategy.TradeMarket;
import com.boris.fundingarbitrage.strategy.intradestrategy.InCrossTradeStrategy;
import com.boris.fundingarbitrage.strategy.intradestrategy.cross.ClassicInCrossTradeStrategy;
import com.boris.fundingarbitrage.strategy.pretradestrategy.TradeDirections;
import kotlin.jvm.functions.Function3;
import lombok.Getter;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public abstract class InCrossTradeCoinLogic extends InTradeCoinLogic {
	@Getter protected final ExchangePair exchanges;
	protected final ExchangeSnapshot longEnterSn;
	protected final ExchangeSnapshot shortEnterSn;
	protected final TradeMarket longMarket;
	protected final TradeMarket shortMarket;
	protected final ExchangeConstantData longConstantData;
	protected final ExchangeConstantData shortConstantData;
	protected final InCrossTradeStrategy strategy;
	protected final AtomicBoolean fillsFetchSuccess = new AtomicBoolean(true);
	private final ScheduledExecutorService fundingRegisterExecutor = Executors.newSingleThreadScheduledExecutor();
	private final AtomicBoolean shouldRegisterShortFunding = new AtomicBoolean();
	private final AtomicBoolean shouldRegisterLongFunding = new AtomicBoolean();
	private final AtomicLong longSettlementUtc = new AtomicLong(0);
	private final AtomicLong shortSettlementUtc = new AtomicLong(0);

	protected CrossCoinExecution execution;
	protected CompletableFuture<Void> enterFuture;

	public InCrossTradeCoinLogic(
					String coin,
					CoinMonitor monitor,
					BigDecimal legUsdtAmount,
					ExchangePair exchanges,
					TradeDirections tradeDirections,
					ExchangeConstantData longConstantData,
					ExchangeConstantData shortConstantData
	) {
		super(coin, monitor, legUsdtAmount);

		this.longEnterSn = monitor.getSnapshot(exchanges.longEx(), coin);
		this.shortEnterSn = monitor.getSnapshot(exchanges.shortEx(), coin);

		this.strategy = new ClassicInCrossTradeStrategy(
						new ExchangeData(longEnterSn, longConstantData),
						new ExchangeData(shortEnterSn, shortConstantData),
						tradeDirections
		);

		this.exchanges = exchanges;
		this.longMarket = tradeDirections.longMarket();
		this.shortMarket = tradeDirections.shortMarket();
		this.longConstantData = longConstantData;
		this.shortConstantData = shortConstantData;

		this.shouldRegisterLongFunding.set(longMarket == TradeMarket.FUTURES);
		this.shouldRegisterShortFunding.set(shortMarket == TradeMarket.FUTURES);

		this.fundingRegisterExecutor.scheduleAtFixedRate(this::registerFunding, 0, 30, TimeUnit.MINUTES);

		tradeLogger.log(coin + ". Long: " + exchanges.longEx().name + ", Short: " + exchanges.shortEx().name);
	}

	private static BigDecimal lcm(BigDecimal a, BigDecimal b) {
		int scale = Math.max(a.scale(), b.scale());

		BigInteger aInt = a.movePointRight(scale).toBigIntegerExact();
		BigInteger bInt = b.movePointRight(scale).toBigIntegerExact();

		BigInteger gcd = aInt.gcd(bInt);
		BigInteger lcm = aInt.divide(gcd).multiply(bInt);

		return new BigDecimal(lcm, scale);
	}

	@Override
	protected void registerFunding() {
		if (shouldRegisterLongFunding.get()) registerFunding(exchanges.longEx(), true);
		if (shouldRegisterShortFunding.get()) registerFunding(exchanges.shortEx(), false);
	}

	private void registerFunding(BaseExchange ex, boolean isLong) {
		AtomicBoolean shouldRegister = isLong ? shouldRegisterLongFunding : shouldRegisterShortFunding;
		AtomicLong settlementUtc = isLong ? longSettlementUtc : shortSettlementUtc;
		if (shouldRegister.get()) {
			shouldRegister.set(false);
			ExchangeSnapshot snapshot = monitor.getSnapshot(ex, coin);
			settlementUtc.set(snapshot.fundingSettlement().toEpochMilli());
			monitor.performOnTimestamp(
							settlementUtc.get(), ex, coin, (sn) -> {
								tradeLogger.log("Funding on " + ex.name + ": [Rate: " + sn.fundingRate() + "]");
								strategy.registerFunding(sn, isLong);
								shouldRegister.set(true);
							}
			);
		}
	}

	protected BigDecimal getBaseAssetQty(ExchangeSnapshot longEnter, ExchangeSnapshot shortEnter) {
		BigDecimal longLotSize = longConstantData.lotSize(longMarket); // 2 COIN
		BigDecimal shortLotSize = shortConstantData.lotSize(shortMarket); // 3 COIN
		BigDecimal effectiveLotSize = lcm(longLotSize, shortLotSize); // 6 COIN

		BigDecimal longAsk = longEnter.bookTicker(longMarket).askPrice(); // 10 usdt/COIN
		BigDecimal shortBid = shortEnter.bookTicker(shortMarket).bidPrice(); // 15 usdt/COIN

		BigDecimal longELSMultiplier = this.legUsdtAmount // 300 usdt
						.divide(longAsk, RoundingMode.FLOOR)
						.divide(effectiveLotSize, RoundingMode.FLOOR); // 5 -

		BigDecimal shortELSMultiplier = this.legUsdtAmount
						.divide(shortBid, RoundingMode.FLOOR)
						.divide(effectiveLotSize, RoundingMode.FLOOR); // 3 -

		BigDecimal effectiveELSMultiplier = longELSMultiplier.min(shortELSMultiplier); // 3 -
		return effectiveELSMultiplier.multiply(effectiveLotSize); // 18 COIN
	}

	protected CompletableFuture<List<PartialFill>> errorHandledFillsFetch(
					BaseExchange ex,
					String orderId,
					TradeSide tradeSide,
					String name,
					TradeMarket market
	) {
		Function3<String, String, TradeSide, CompletableFuture<List<PartialFill>>> fetchFun =
						market == TradeMarket.FUTURES ?
										ex.privateHttpClient::getFuturesOrderRecord :
										ex.privateHttpClient::getSpotOrderRecord;

		return fetchFun.invoke(orderId, coin, tradeSide)
						.whenComplete((r, t) -> {
							if (t == null && r != null && !r.isEmpty()) return;
							fillsFetchSuccess.set(false);
							if (r == null || r.isEmpty()) tradeLogger.warn("Fetched " + name + " fills: " + r);
							else tradeLogger.warn("Failed to fetch " + name + " fills: " + t.getMessage());
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

	private BigDecimal logFundingSteps(List<ExchangeSnapshot> snapshots, boolean isLong) {
		tradeLogger.log("Registered funding transactions: ");
		BigDecimal totalFundingGain = BigDecimal.ZERO;

		for (ExchangeSnapshot sn : snapshots) {
			BigDecimal mark = sn.markPrice();
			BigDecimal funding = sn.fundingRate().multiply(mark);
			if (isLong) funding = funding.negate();

			totalFundingGain = totalFundingGain.add(funding);

			String tag = isLong ? "[Long]" : "[Short]";
			tradeLogger.log("[Funding] " + tag + " Received funding: " + funding);
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

	protected CompletableFuture<Void> logTradeInfo(ExchangeSnapshot currLong, ExchangeSnapshot currShort) {
		CompletableFuture<List<PartialFill>> LEnterFuture = errorHandledFillsFetch(
						exchanges.longEx(),
						execution.getEnterIds().longId(),
						TradeSide.OPEN,
						"Long Enter",
						longMarket
		);
		CompletableFuture<List<PartialFill>> SEnterFuture = errorHandledFillsFetch(
						exchanges.shortEx(),
						execution.getEnterIds().shortId(),
						TradeSide.OPEN,
						"Short Enter",
						shortMarket
		);

		CompletableFuture<List<PartialFill>> LExitFuture = errorHandledFillsFetch(
						exchanges.longEx(),
						execution.getExitIds().longId(),
						TradeSide.CLOSE,
						"Long Exit",
						longMarket
		);

		CompletableFuture<List<PartialFill>> SExitFuture = errorHandledFillsFetch(
						exchanges.shortEx(),
						execution.getExitIds().shortId(),
						TradeSide.CLOSE,
						"Short Exit",
						shortMarket
		);

		return CompletableFuture.allOf(LEnterFuture, SEnterFuture, LExitFuture, SExitFuture)
						.whenComplete((v, t) -> {
							if (!fillsFetchSuccess.get()) return;

							List<PartialFill> longEnterFills = LEnterFuture.join();
							List<PartialFill> shortEnterFills = SEnterFuture.join();
							List<PartialFill> longExitFills = LExitFuture.join();
							List<PartialFill> shortExitFills = SExitFuture.join();

							BigDecimal oLongEnterPrice = longEnterSn.bookTicker(longMarket).askPrice();
							BigDecimal oShortEnterPrice = shortEnterSn.bookTicker(shortMarket).bidPrice();
							BigDecimal oLongExitPrice = currLong.bookTicker(longMarket).bidPrice();
							BigDecimal oShortExitPrice = currShort.bookTicker(shortMarket).askPrice();

							BigDecimal avgLongEnterPrice = getAvgPrice(longEnterFills);
							BigDecimal avgShortEnterPrice = getAvgPrice(shortEnterFills);
							BigDecimal avgLongExitPrice = getAvgPrice(longExitFills);
							BigDecimal avgShortExitPrice = getAvgPrice(shortExitFills);

							BigDecimal longEnterFee = longConstantData.fees(longMarket).openTaker().multiply(avgLongEnterPrice);
							BigDecimal shortEnterFee = shortConstantData.fees(shortMarket).openTaker().multiply(avgShortEnterPrice);
							BigDecimal longExitFee = longConstantData.fees(longMarket).closeTaker().multiply(avgLongExitPrice);
							BigDecimal shortExitFee = shortConstantData.fees(shortMarket).closeTaker().multiply(avgShortExitPrice);
							BigDecimal totalFees = longEnterFee.add(shortEnterFee).add(longExitFee).add(shortExitFee);

							List<ExchangeSnapshot> longFundingSnapshots = strategy.getLongFundingSnapshots();
							List<ExchangeSnapshot> shortFundingSnapshots = strategy.getShortFundingSnapshots();

							tradeLogger.log("Trade info for " + coin + ": ");
							logTradeStep(oLongEnterPrice, avgLongEnterPrice, longEnterFee, true, true);
							logTradeStep(oShortEnterPrice, avgShortEnterPrice, shortEnterFee, false, true);
							BigDecimal totalLongFundingGain = logFundingSteps(longFundingSnapshots, true);
							BigDecimal totalShortFundingGain = logFundingSteps(shortFundingSnapshots, false);
							BigDecimal totalFundingGain = totalLongFundingGain.add(totalShortFundingGain);
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
							tradeLogger.log("[Observed] After fees: " + oAfterFees);
							tradeLogger.log("[Executed] After fees: " + eAfterFees);

							BigDecimal baseAssetQty = getBaseAssetQty(longEnterSn, shortEnterSn);
							tradeLogger.log("[Observed] PnL: " + oAfterFees.multiply(baseAssetQty));
							tradeLogger.log("[Executed] PnL: " + eAfterFees.multiply(baseAssetQty));
						});
	}

	private CompletableFuture<Void> shutdown(ExchangeSnapshot currLong, ExchangeSnapshot currShort) {
		return new CompletableFuture<>().completeOnTimeout(
						null,
						5,
						TimeUnit.SECONDS
		).thenCompose(v -> logTradeInfo(currLong, currShort));
	}

	@Override
	public CompletableFuture<Void> exitTradeIfShould() {
		if (!enterFuture.isDone()) return null;

		ExchangeSnapshot currLong = monitor.getSnapshot(exchanges.longEx(), coin);
		ExchangeSnapshot currShort = monitor.getSnapshot(exchanges.shortEx(), coin);
		if (!strategy.shouldExitTrade(currLong, currShort)) return null;

		return execution.exitTrade().thenCompose(v -> shutdown(currLong, currShort));
	}

	protected Void failEnter(Throwable t) {
		tradeLogger.error("Failed to enter trade for " + coin + ". Long: "
											+ exchanges.longEx().name + " and short: " + exchanges.shortEx().name);
		tradeLogger.error("Message: " + t.getMessage());
		throw new RuntimeException(t);
	}

	@Override
	protected void finish() {
		fundingRegisterExecutor.shutdownNow();
		monitor.cancelTimestampExecution(longSettlementUtc.get(), exchanges.longEx(), coin);
		monitor.cancelTimestampExecution(shortSettlementUtc.get(), exchanges.shortEx(), coin);
	}
}
