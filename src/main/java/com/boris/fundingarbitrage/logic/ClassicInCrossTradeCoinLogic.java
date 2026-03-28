package com.boris.fundingarbitrage.logic;

import com.boris.fundingarbitrage.exchange.BaseExchange;
import com.boris.fundingarbitrage.execution.CoinExecution;
import com.boris.fundingarbitrage.model.assetops.Leverages;
import com.boris.fundingarbitrage.model.assetops.TradeParams;
import com.boris.fundingarbitrage.model.assetops.TradeSide;
import com.boris.fundingarbitrage.model.contract.PartialFill;
import com.boris.fundingarbitrage.model.exchange.ExchangeConstantData;
import com.boris.fundingarbitrage.model.exchange.ExchangeData;
import com.boris.fundingarbitrage.model.exchange.ExchangeSnapshot;
import com.boris.fundingarbitrage.monitor.CoinMonitor;
import com.boris.fundingarbitrage.strategy.TradeMarket;
import com.boris.fundingarbitrage.strategy.intradestrategy.ClassicInCrossTradeStrategy;
import com.boris.fundingarbitrage.strategy.intradestrategy.InCrossTradeStrategy;
import kotlin.jvm.functions.Function3;
import lombok.NonNull;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class ClassicInCrossTradeCoinLogic extends InCrossTradeCoinLogic {
	private final CoinExecution execution;
	private final CompletableFuture<Void> enterFuture;
	private final AtomicBoolean fillsFetchSuccess = new AtomicBoolean(true);
	private final TradeParams enterParams;
	private final ExchangeSnapshot longEnterSn;
	private final ExchangeSnapshot shortEnterSn;
	private BigDecimal baseAssetQty;

	public ClassicInCrossTradeCoinLogic(
					@NonNull String coin,
					@NonNull CoinMonitor monitor,
					@NonNull ExchangePair exchanges,
					@NonNull BigDecimal usdtAmount,
					@NonNull Leverages leverages,
					@NonNull ExchangeConstantData longConstantData,
					@NonNull ExchangeConstantData shortConstantData,
					@NonNull TradeMarket longMarket,
					@NonNull TradeMarket shortMarket
	) {
		ExchangeSnapshot longEnterSnapshot = monitor.getSnapshot(exchanges.longEx(), coin);
		ExchangeSnapshot shortEnterSnapshot = monitor.getSnapshot(exchanges.shortEx(), coin);

		InCrossTradeStrategy strategy = new ClassicInCrossTradeStrategy(
						new ExchangeData(longEnterSnapshot, longConstantData),
						new ExchangeData(shortEnterSnapshot, shortConstantData),
						longMarket,
						shortMarket
		);

		super(coin, monitor, usdtAmount, exchanges, longMarket, shortMarket, longConstantData, shortConstantData, strategy);
		this.longEnterSn = longEnterSnapshot;
		this.shortEnterSn = shortEnterSnapshot;

		enterParams = getEnterParams(longEnterSnapshot, shortEnterSnapshot);
		this.execution = new CoinExecution(coin, enterParams, leverages, tradeLogger);

		this.enterFuture = this.execution.enterTrade().exceptionally(this::fail);
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

	private TradeParams getEnterParams(ExchangeSnapshot longEnter, ExchangeSnapshot shortEnter) {
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

	public CompletableFuture<Void> exitTradeIfShould() {
		if (!enterFuture.isDone()) return null;

		ExchangeSnapshot currLong = monitor.getSnapshot(exchanges.longEx(), coin);
		ExchangeSnapshot currShort = monitor.getSnapshot(exchanges.shortEx(), coin);
		if (!strategy.shouldExitTrade(currLong, currShort)) return null;

		return execution.exitTrade().thenCompose((v) -> shutdown(currLong, currShort));
	}

	private CompletableFuture<Void> shutdown(ExchangeSnapshot currLong, ExchangeSnapshot currShort) {
		return new CompletableFuture<>().completeOnTimeout(
						null,
						5,
						TimeUnit.SECONDS
		).thenCompose(v -> logTradeInfo(currLong, currShort));
	}

	private CompletableFuture<List<PartialFill>> errorHandledFillsFetch(
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
							if (t == null && r != null && !r.isEmpty()) return; // Everything good
							fillsFetchSuccess.set(false);
							if (r == null || r.isEmpty()) tradeLogger.warn("Fetched " + name + " fills: " + r);
							else tradeLogger.warn("Failed to fetch " + name + " fills: " + t.getMessage());
						});
	}

	private CompletableFuture<Void> logTradeInfo(ExchangeSnapshot currLong, ExchangeSnapshot currShort) {
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
							BigDecimal oLongExitPrice = longEnterSn.bookTicker(longMarket).bidPrice();
							BigDecimal oShortExitPrice = shortEnterSn.bookTicker(shortMarket).askPrice();

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
}
