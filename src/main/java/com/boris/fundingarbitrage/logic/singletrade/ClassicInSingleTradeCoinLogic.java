package com.boris.fundingarbitrage.logic.singletrade;

import com.boris.fundingarbitrage.exchange.BaseExchange;
import com.boris.fundingarbitrage.execution.ClassicSingleCoinExecution;
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
import com.boris.fundingarbitrage.strategy.intradestrategy.InSingleTradeStrategy;
import com.boris.fundingarbitrage.strategy.intradestrategy.single.ClassicInSingleTradeStrategy;
import com.boris.fundingarbitrage.strategy.pretradestrategy.TradeDirections;
import kotlin.jvm.functions.Function3;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class ClassicInSingleTradeCoinLogic extends InSingleTradeCoinLogic {
	private final CoinExecution execution;
	private final CompletableFuture<Void> enterFuture;
	private final AtomicBoolean fillsFetchSuccess = new AtomicBoolean(true);
	private final TradeParams enterParams;
	private final ExchangeSnapshot enterSn;
	private BigDecimal baseAssetQty;

	public ClassicInSingleTradeCoinLogic(
					String coin,
					CoinMonitor monitor,
					BaseExchange exchange,
					BigDecimal usdtAmount,
					Leverages leverages,
					ExchangeConstantData constantData,
					TradeDirections directions
	) {
		ExchangeSnapshot enterSnapshot = monitor.getSnapshot(exchange, coin);
		InSingleTradeStrategy strategy = new ClassicInSingleTradeStrategy(
						new ExchangeData(enterSnapshot, constantData),
						directions
		);

		super(coin, monitor, usdtAmount, exchange, directions, constantData, strategy);
		this.enterSn = enterSnapshot;

		this.enterParams = getEnterParams(enterSn);
		this.execution = new ClassicSingleCoinExecution(
						coin,
						exchange,
						enterParams.baseAssetQty(),
						enterParams.shortContractQty(),
						leverages.shortLeverage(),
						tradeLogger
		);
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
		tradeLogger.error("Failed to enter single-exchange trade for " + coin + " on " + exchange.name);
		tradeLogger.error("Message: " + t.getMessage());
		throw new RuntimeException(t);
	}

	private TradeParams getEnterParams(ExchangeSnapshot enterSnapshot) {
		BigDecimal longLotSize = constantData.lotSize(longMarket);
		BigDecimal shortLotSize = constantData.lotSize(shortMarket);
		BigDecimal effectiveLotSize = lcm(longLotSize, shortLotSize);

		BigDecimal longAsk = enterSnapshot.bookTicker(longMarket).askPrice();
		BigDecimal shortBid = enterSnapshot.bookTicker(shortMarket).bidPrice();

		BigDecimal longELSMultiplier = this.legUsdtAmount
						.divide(longAsk, RoundingMode.FLOOR)
						.divide(effectiveLotSize, RoundingMode.FLOOR);

		BigDecimal shortELSMultiplier = this.legUsdtAmount
						.divide(shortBid, RoundingMode.FLOOR)
						.divide(effectiveLotSize, RoundingMode.FLOOR);

		BigDecimal effectiveELSMultiplier = longELSMultiplier.min(shortELSMultiplier);
		baseAssetQty = effectiveELSMultiplier.multiply(effectiveLotSize);

		if (baseAssetQty.equals(BigDecimal.ZERO))
			throw new RuntimeException("Not enough margin deposited for coin: " + coin + ". Did not enter trades");

		int longContractQty = baseAssetQty.divide(longLotSize, RoundingMode.FLOOR).intValueExact();
		int shortContractQty = baseAssetQty.divide(shortLotSize, RoundingMode.FLOOR).intValueExact();

		return new TradeParams(baseAssetQty, longContractQty, shortContractQty);
	}

	@Override
	public CompletableFuture<Void> exitTradeIfShould() {
		if (!enterFuture.isDone()) return null;

		ExchangeSnapshot current = monitor.getSnapshot(exchange, coin);
		if (!strategy.shouldExitTrade(current)) return null;

		return execution.exitTrade().thenCompose(v -> shutdown(current));
	}

	private CompletableFuture<Void> shutdown(ExchangeSnapshot current) {
		return new CompletableFuture<>().completeOnTimeout(
						null,
						5,
						TimeUnit.SECONDS
		).thenCompose(v -> logTradeInfo(current));
	}

	private CompletableFuture<List<PartialFill>> errorHandledFillsFetch(
					String orderId,
					TradeSide tradeSide,
					String name,
					TradeMarket market
	) {
		Function3<String, String, TradeSide, CompletableFuture<List<PartialFill>>> fetchFun =
						market == TradeMarket.FUTURES ?
										exchange.privateHttpClient::getFuturesOrderRecord :
										exchange.privateHttpClient::getSpotOrderRecord;

		return fetchFun.invoke(orderId, coin, tradeSide)
						.whenComplete((r, t) -> {
							if (t == null && r != null && !r.isEmpty()) return;
							fillsFetchSuccess.set(false);
							if (r == null || r.isEmpty()) tradeLogger.warn("Fetched " + name + " fills: " + r);
							else tradeLogger.warn("Failed to fetch " + name + " fills: " + t.getMessage());
						});
	}

	private CompletableFuture<Void> logTradeInfo(ExchangeSnapshot current) {
		CompletableFuture<List<PartialFill>> longEnterFuture = errorHandledFillsFetch(
						execution.getEnterIds().longId(),
						TradeSide.OPEN,
						"Long Enter",
						longMarket
		);
		CompletableFuture<List<PartialFill>> shortEnterFuture = errorHandledFillsFetch(
						execution.getEnterIds().shortId(),
						TradeSide.OPEN,
						"Short Enter",
						shortMarket
		);
		CompletableFuture<List<PartialFill>> longExitFuture = errorHandledFillsFetch(
						execution.getExitIds().longId(),
						TradeSide.CLOSE,
						"Long Exit",
						longMarket
		);
		CompletableFuture<List<PartialFill>> shortExitFuture = errorHandledFillsFetch(
						execution.getExitIds().shortId(),
						TradeSide.CLOSE,
						"Short Exit",
						shortMarket
		);

		return CompletableFuture.allOf(longEnterFuture, shortEnterFuture, longExitFuture, shortExitFuture)
						.whenComplete((v, t) -> {
							if (!fillsFetchSuccess.get()) return;

							List<PartialFill> longEnterFills = longEnterFuture.join();
							List<PartialFill> shortEnterFills = shortEnterFuture.join();
							List<PartialFill> longExitFills = longExitFuture.join();
							List<PartialFill> shortExitFills = shortExitFuture.join();

							BigDecimal oLongEnterPrice = enterSn.bookTicker(longMarket).askPrice();
							BigDecimal oShortEnterPrice = enterSn.bookTicker(shortMarket).bidPrice();
							BigDecimal oLongExitPrice = current.bookTicker(longMarket).bidPrice();
							BigDecimal oShortExitPrice = current.bookTicker(shortMarket).askPrice();

							BigDecimal avgLongEnterPrice = getAvgPrice(longEnterFills);
							BigDecimal avgShortEnterPrice = getAvgPrice(shortEnterFills);
							BigDecimal avgLongExitPrice = getAvgPrice(longExitFills);
							BigDecimal avgShortExitPrice = getAvgPrice(shortExitFills);

							BigDecimal longEnterFee = constantData.fees(longMarket).openTaker().multiply(avgLongEnterPrice);
							BigDecimal shortEnterFee = constantData.fees(shortMarket).openTaker().multiply(avgShortEnterPrice);
							BigDecimal longExitFee = constantData.fees(longMarket).closeTaker().multiply(avgLongExitPrice);
							BigDecimal shortExitFee = constantData.fees(shortMarket).closeTaker().multiply(avgShortExitPrice);
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
							BigDecimal observedGain = calculateGainFromPriceMoves(
											oLongEnterPrice,
											oShortEnterPrice,
											oLongExitPrice,
											oShortExitPrice
							);
							BigDecimal executedGain = calculateGainFromPriceMoves(
											avgLongEnterPrice,
											avgShortEnterPrice,
											avgLongExitPrice,
											avgShortExitPrice
							);
							tradeLogger.log("[Observed] Price moves: " + observedGain);
							tradeLogger.log("[Executed] Price moves: " + executedGain);

							BigDecimal observedTotalGain = observedGain.add(totalFundingGain);
							BigDecimal executedTotalGain = executedGain.add(totalFundingGain);
							tradeLogger.log("[Observed] Total gain: " + observedTotalGain);
							tradeLogger.log("[Executed] Total gain: " + executedTotalGain);

							BigDecimal observedAfterFees = observedTotalGain.subtract(totalFees);
							BigDecimal executedAfterFees = executedTotalGain.subtract(totalFees);
							tradeLogger.log("[Observed] After fees: " + observedAfterFees);
							tradeLogger.log("[Executed] After fees: " + executedAfterFees);

							tradeLogger.log("[Observed] PnL: " + observedAfterFees.multiply(baseAssetQty));
							tradeLogger.log("[Executed] PnL: " + executedAfterFees.multiply(baseAssetQty));
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

	private void logTradeStep(BigDecimal observed, BigDecimal actual, BigDecimal fee, boolean isLong, boolean isEnter) {
		BigDecimal slippage = observed.subtract(actual);
		if ((!isLong && isEnter) || (isLong && !isEnter)) slippage = slippage.negate();
		char slippageSign = getSlippageSign(slippage);

		String tag1 = isEnter ? "[Enter] " : "[Exit] ";
		String tag2 = isLong ? "[Long] " : "[Short] ";

		tradeLogger.log(tag1 + tag2 + "Observed price: " + observed + ", actual avg price: " + actual
										+ ". Slippage: " + slippageSign + slippage);
		tradeLogger.log(tag1 + tag2 + "Fee: " + fee);
	}

	private BigDecimal logFundingSteps(List<ExchangeSnapshot> snapshots, boolean isLong) {
		tradeLogger.log("Registered funding transactions: ");
		BigDecimal totalFundingGain = BigDecimal.ZERO;

		for (ExchangeSnapshot sn : snapshots) {
			BigDecimal funding = sn.fundingRate().multiply(sn.markPrice());
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
