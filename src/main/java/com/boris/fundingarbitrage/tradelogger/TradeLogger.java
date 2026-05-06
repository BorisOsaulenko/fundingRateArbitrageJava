package com.boris.fundingarbitrage.tradelogger;

import com.boris.fundingarbitrage.exchange.BaseExchange;
import com.boris.fundingarbitrage.execution.TradeIds;
import com.boris.fundingarbitrage.logic.CoinOpportunity;
import com.boris.fundingarbitrage.model.assetops.TradeSide;
import com.boris.fundingarbitrage.model.contract.PartialFill;
import com.boris.fundingarbitrage.model.exchange.ExchangePair;
import com.boris.fundingarbitrage.model.exchange.constantdata.ConstantData;
import com.boris.fundingarbitrage.model.exchange.snapshot.FuturesSnapshot;
import com.boris.fundingarbitrage.model.exchange.snapshot.Snapshot;
import com.boris.fundingarbitrage.strategy.TradeMarket;
import com.boris.fundingarbitrage.strategy.pretradestrategy.TradeDirections;
import kotlin.jvm.functions.Function3;

import java.io.BufferedWriter;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

public class TradeLogger {
	private static final DateTimeFormatter fmt = DateTimeFormatter
					.ofPattern("yyyy_MM_dd-HH:mm:ss")
					.withZone(ZoneId.of("UTC"));
	private final BufferedWriter writer;
	private final String coin;
	private final AtomicBoolean fillsFetchSuccess = new AtomicBoolean(true);
	private final ExchangePair exchanges;
	private final TradeDirections tradeDirections;
	private final BigDecimal baseAssetQty;
	private final ConstantData longCd;
	private final ConstantData shortCd;
	private Snapshot longEnterSn;
	private Snapshot shortEnterSn;
	private Snapshot longExitSn;
	private Snapshot shortExitSn;
	private BigDecimal totalFundingGain = BigDecimal.ZERO;

	public TradeLogger(
					String coin,
					CoinOpportunity op,
					BigDecimal baseAssetQty
	) {
		this.coin = coin;
		this.exchanges = op.exchanges();
		this.tradeDirections = op.directions();
		this.baseAssetQty = baseAssetQty;
		this.longCd = op.longData().constantData();
		this.shortCd = op.shortData().constantData();

		String path = String.format("logs/%s_%s.log", coin, fmt.format(Instant.now()));
		Path logFilePath = Path.of(path);
		OutputStream out;
		try {
			Path parent = logFilePath.getParent();
			if (parent != null) {
				Files.createDirectories(parent);
			}
			out = Files.newOutputStream(logFilePath);
		} catch (Exception e) {
			out = System.out;
		}
		this.writer = new BufferedWriter(new OutputStreamWriter(out));
	}

	String getPrefix(String type) {
		return "[" + type.toUpperCase() + "] [" + fmt.format(Instant.now()) + "] ";
	}

	private void log(Object message) {
		try {
			writer.write(getPrefix("LOG") + message.toString() + "\n");
			writer.flush();
		} catch (Exception e) {
			System.out.println("Failed to log trade message: " + e.getMessage());
			e.printStackTrace();
		}
	}

	private void warn(Object message) {
		try {
			writer.write(getPrefix("WARN") + message.toString() + "\n");
			writer.flush();
		} catch (Exception e) {
			System.out.println("Failed to log trade message: " + e.getMessage());
			e.printStackTrace();
		}
	}

	private void error(Object message) {
		try {
			writer.write(getPrefix("ERROR") + message.toString() + "\n");
			writer.flush();
		} catch (Exception e) {
			System.out.println("Failed to log trade message: " + e.getMessage());
			e.printStackTrace();
		}
	}

	private void log(String message, Object... args) {
		log(String.format(message, args));
	}

	private void warn(String message, Object... args) {
		warn(String.format(message, args));
	}

	private void error(String message, Object... args) {
		error(String.format(message, args));
	}

	public void logEnterSuccess(Snapshot longSn, Snapshot shortSn) {
		this.longEnterSn = longSn;
		this.shortEnterSn = shortSn;

		BigDecimal longAsk = longSn.askPrice();
		BigDecimal shortBid = shortSn.bidPrice();
		BigDecimal longFee = longAsk.multiply(longCd.openTaker());
		BigDecimal shortFee = shortBid.multiply(shortCd.openTaker());
		log("Entered trades: [Long: %s], [Short: %s]", longAsk, shortBid);
		log("Fees: [Long: %s], [Short: %s]", longFee, shortFee);
	}

	public Void logEnterFailure(Throwable t) {
		error("Failed to enter trades: " + t.getMessage());
		return null;
	}

	public void logFunding(FuturesSnapshot sn, boolean isLong) {
		String tag = isLong ? "Long" : "Short";
		BigDecimal mark = sn.mark().price();
		BigDecimal funding = sn.funding().rate().multiply(mark);
		if (isLong) funding = funding.negate();
		log("Funding: [%s: %s]", tag, funding);
		totalFundingGain = totalFundingGain.add(funding);
	}

	public void logExit(Snapshot longSn, Snapshot shortSn) {
		this.longExitSn = longSn;
		this.shortExitSn = shortSn;

		BigDecimal longBid = longSn.bidPrice();
		BigDecimal shortAsk = shortSn.askPrice();
		BigDecimal longFee = longBid.multiply(longCd.closeTaker());
		BigDecimal shortFee = shortAsk.multiply(shortCd.closeTaker());
		log("Exited trades: [Long: %s], [Short: %s]", longBid, shortAsk);
		log("Fees: [Long: %s], [Short: %s]", longFee, shortFee);
	}

	public Void logExitFailure(Throwable t, BaseExchange exchange) {
		error("Failed to exit trade on %s: %s", exchange.name(), t.getMessage());
		return null;
	}

	public CompletableFuture<Void> finish(TradeIds enterIds, TradeIds exitIds) {
		CompletableFuture<List<PartialFill>> LEnterFuture = errorHandledFillsFetch(
						exchanges.longEx(),
						enterIds.longId(),
						TradeSide.OPEN,
						"Long Enter",
						tradeDirections.longMarket()
		);
		CompletableFuture<List<PartialFill>> SEnterFuture = errorHandledFillsFetch(
						exchanges.shortEx(),
						enterIds.shortId(),
						TradeSide.OPEN,
						"Short Enter",
						tradeDirections.shortMarket()
		);

		CompletableFuture<List<PartialFill>> LExitFuture = errorHandledFillsFetch(
						exchanges.longEx(),
						exitIds.longId(),
						TradeSide.CLOSE,
						"Long Exit",
						tradeDirections.longMarket()
		);

		CompletableFuture<List<PartialFill>> SExitFuture = errorHandledFillsFetch(
						exchanges.shortEx(),
						exitIds.shortId(),
						TradeSide.CLOSE,
						"Short Exit",
						tradeDirections.shortMarket()
		);

		return CompletableFuture.allOf(LEnterFuture, SEnterFuture, LExitFuture, SExitFuture)
						.whenComplete((v, t) -> {
							if (fillsFetchSuccess.get()) logOnFetchSuccess(
											LEnterFuture.join(),
											SEnterFuture.join(),
											LExitFuture.join(),
											SExitFuture.join()
							);
							else {
								warn("One or more fills fetches failed, only observed logs shown");
								logOnFetchFailure();
							}
						});
	}

	private void logOnFetchSuccess(
					List<PartialFill> longEnterFills,
					List<PartialFill> shortEnterFills,
					List<PartialFill> longExitFills,
					List<PartialFill> shortExitFills
	) {
		BigDecimal oLongEnterPrice = longEnterSn.bookTicker().askPrice();
		BigDecimal oShortEnterPrice = shortEnterSn.bookTicker().bidPrice();
		BigDecimal oLongExitPrice = longExitSn.bookTicker().bidPrice();
		BigDecimal oShortExitPrice = shortExitSn.bookTicker().askPrice();

		BigDecimal avgLongEnterPrice = getAvgPrice(longEnterFills);
		BigDecimal avgShortEnterPrice = getAvgPrice(shortEnterFills);
		BigDecimal avgLongExitPrice = getAvgPrice(longExitFills);
		BigDecimal avgShortExitPrice = getAvgPrice(shortExitFills);

		BigDecimal oLongEnterFee = longCd.openTaker().multiply(oLongEnterPrice);
		BigDecimal oShortEnterFee = shortCd.openTaker().multiply(oShortEnterPrice);
		BigDecimal oLongExitFee = longCd.closeTaker().multiply(oLongExitPrice);
		BigDecimal oShortExitFee = shortCd.closeTaker().multiply(oShortExitPrice);
		BigDecimal oTotalFees = oLongEnterFee.add(oShortEnterFee).add(oLongExitFee).add(oShortExitFee);

		BigDecimal eLongEnterFee = longCd.fees().openTaker().multiply(avgLongEnterPrice);
		BigDecimal eShortEnterFee = shortCd.fees().openTaker().multiply(avgShortEnterPrice);
		BigDecimal eLongExitFee = longCd.fees().closeTaker().multiply(avgLongExitPrice);
		BigDecimal eShortExitFee = shortCd.fees().closeTaker().multiply(avgShortExitPrice);
		BigDecimal eTotalFees = eLongEnterFee.add(eShortEnterFee).add(eLongExitFee).add(eShortExitFee);

		log("Trade info for " + coin + ": ");
		logFullTradeStep(oLongEnterPrice, avgLongEnterPrice, longCd.openTaker(), true, true);
		logFullTradeStep(oShortEnterPrice, avgShortEnterPrice, shortCd.openTaker(), false, true);
		logFullTradeStep(oLongExitPrice, avgLongExitPrice, longCd.closeTaker(), true, false);
		logFullTradeStep(oShortExitPrice, avgShortExitPrice, shortCd.closeTaker(), false, false);

		log("Observed vs executed total gain (for one coin): ");
		log("[Same] Funding gain: " + totalFundingGain);
		var o = calculateGainFromPriceMoves(oLongEnterPrice, oShortEnterPrice, oLongExitPrice, oShortExitPrice);
		var e = calculateGainFromPriceMoves(
						avgLongEnterPrice,
						avgShortEnterPrice,
						avgLongExitPrice,
						avgShortExitPrice
		);
		log("[Observed] Price moves: " + o);
		log("[Executed] Price moves: " + e);

		BigDecimal oTotalGain = o.add(totalFundingGain);
		BigDecimal eTotalGain = e.add(totalFundingGain);
		log("[Observed] Total gain: " + oTotalGain);
		log("[Executed] Total gain: " + eTotalGain);

		BigDecimal oAfterFees = oTotalGain.subtract(oTotalFees);
		BigDecimal eAfterFees = eTotalGain.subtract(eTotalFees);
		log("[Observed] After fees: " + oAfterFees);
		log("[Executed] After fees: " + eAfterFees);

		log("[Observed] PnL: " + oAfterFees.multiply(baseAssetQty));
		log("[Executed] PnL: " + eAfterFees.multiply(baseAssetQty));
	}

	private void logOnFetchFailure() {
		BigDecimal oLongEnterPrice = longEnterSn.bookTicker().askPrice();
		BigDecimal oShortEnterPrice = shortEnterSn.bookTicker().bidPrice();
		BigDecimal oLongExitPrice = longExitSn.bookTicker().bidPrice();
		BigDecimal oShortExitPrice = shortExitSn.bookTicker().askPrice();

		BigDecimal oLongEnterFee = longCd.openTaker().multiply(oLongEnterPrice);
		BigDecimal oShortEnterFee = shortCd.openTaker().multiply(oShortEnterPrice);
		BigDecimal oLongExitFee = longCd.closeTaker().multiply(oLongExitPrice);
		BigDecimal oShortExitFee = shortCd.closeTaker().multiply(oShortExitPrice);
		BigDecimal oTotalFees = oLongEnterFee.add(oShortEnterFee).add(oLongExitFee).add(oShortExitFee);

		log("Trade info for %s: (observed only)", coin);
		logObservedTradeStep(oLongEnterPrice, longCd.openTaker(), true, true);
		logObservedTradeStep(oShortEnterPrice, shortCd.openTaker(), false, true);
		logObservedTradeStep(oLongExitPrice, longCd.closeTaker(), true, false);
		logObservedTradeStep(oShortExitPrice, shortCd.closeTaker(), false, false);

		log("Funding gain: " + totalFundingGain);
		var o = calculateGainFromPriceMoves(oLongEnterPrice, oShortEnterPrice, oLongExitPrice, oShortExitPrice);
		log("Price moves: " + o);

		BigDecimal oTotalGain = o.add(totalFundingGain);
		log("Total gain: " + oTotalGain);

		BigDecimal oAfterFees = oTotalGain.subtract(oTotalFees);
		log("After fees: " + oAfterFees);
		log("PnL: " + oAfterFees.multiply(baseAssetQty));
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
										ex.privateHttpClient()::getFuturesOrderRecord :
										ex.privateHttpClient()::getSpotOrderRecord;

		return fetchFun.invoke(orderId, coin, tradeSide)
						.whenComplete((r, t) -> {
							if (t == null && r != null && !r.isEmpty()) return;
							fillsFetchSuccess.set(false);
							if (r == null || r.isEmpty()) warn("Fetched " + name + " fills: " + r);
							else warn("Failed to fetch " + name + " fills: " + t.getMessage());
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

	private void logFullTradeStep(BigDecimal o, BigDecimal a, BigDecimal feeRate, boolean isLong, boolean isEnter) {
		BigDecimal s = o.subtract(a);
		if ((!isLong && isEnter) || (isLong && !isEnter)) s = s.negate();
		char ss = getSlippageSign(s);

		String tag1 = isEnter ? "[Enter] " : "[Exit] ";
		String tag2 = isLong ? "[Long] " : "[Short] ";

		log(tag1 + tag2 + "Observed price: " + o + ", actual avg price: " + a + ". Slippage: " + ss + s);
		log(tag1 + tag2 + "Fee: [Observed]" + o.multiply(feeRate) + ", [Executed]" + a.multiply(feeRate));
	}

	private void logObservedTradeStep(BigDecimal o, BigDecimal feeRate, boolean isLong, boolean isEnter) {
		String tag1 = isEnter ? "[Enter] " : "[Exit] ";
		String tag2 = isLong ? "[Long] " : "[Short] ";

		log(tag1 + tag2 + "Observed price: " + o + ". Fee: [Observed]" + o.multiply(feeRate));
	}

	private char getSlippageSign(BigDecimal slippage) {
		return slippage.compareTo(BigDecimal.ZERO) >= 0 ? '+' : ' ';
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
}
