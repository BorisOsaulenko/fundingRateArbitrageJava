package com.boris.fundingarbitrage.execution;

import com.boris.fundingarbitrage.exchange.BaseExchange;
import com.boris.fundingarbitrage.logic.ArbitrageBotConfig;
import com.boris.fundingarbitrage.logic.CoinOpportunity;
import com.boris.fundingarbitrage.model.assetops.*;
import com.boris.fundingarbitrage.model.exchange.snapshot.Snapshot;
import com.boris.fundingarbitrage.strategy.TradeMarket;
import com.boris.fundingarbitrage.tradelogger.ITradeSessionLogger;
import lombok.Getter;
import lombok.NonNull;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.function.Supplier;

public final class TradeExecution implements ITradeExecution {
	private static final MarginMode FUTURES_MARGIN_MODE = MarginMode.CROSS;

	private final String coin;
	private final TradeParams tradeParams;
	private final Leverages leverages;
	private final CoinOpportunity op;
	private final ArbitrageBotConfig config;
	private final ITradeSessionLogger tradeLogger;
	private CompletableFuture<Void> enterFuture = null;
	private CompletableFuture<Void> exitFuture = null;
	@Getter private TradeIds enterIds;
	@Getter private TradeIds exitIds;
	private boolean failed = false;

	public TradeExecution(
					@NonNull String coin,
					@NonNull CoinOpportunity op,
					@NonNull ArbitrageBotConfig config,
					@NonNull ITradeSessionLogger tradeLogger
	) {
		this.coin = coin;
		this.op = op;
		this.tradeLogger = tradeLogger;
		this.config = config;
		this.tradeParams = getEnterParams(op.longData().snapshot(), op.shortData().snapshot());

		int longLeverage = op.longData().market() == TradeMarket.FUTURES ? config.leverage() : 1;
		int shortLeverage = op.shortData().market() == TradeMarket.FUTURES ? config.leverage() : 1;
		this.leverages = new Leverages(longLeverage, shortLeverage);
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
	public CompletableFuture<Void> enterTrade() {
		if (failed) return CompletableFuture.completedFuture(null);
		if (enterFuture != null) return enterFuture;

		return this.enterFuture = enterInternal()
						.thenAccept(ids -> this.enterIds = ids)
						.whenComplete((_, t) -> failed = (t != null));
	}

	@Override
	public CompletableFuture<Void> exitTrade(Snapshot currLong, Snapshot currShort) {
		if (!shouldAttemptExit()) return CompletableFuture.completedFuture(null);
		if (exitFuture != null) return exitFuture;

		return this.exitFuture = exitInternal(currLong, currShort)
						.thenAccept(ids -> this.exitIds = ids)
						.whenComplete((_, t) -> failed = (t != null));
	}

	private boolean shouldAttemptExit() {
		if (failed) return false;
		if (enterFuture == null) return false;
		return enterFuture.state().equals(Future.State.SUCCESS);
	}

	private CompletableFuture<String> placeFuturesOrder(OrderSide orderSide, TradeSide tradeSide) {
		BaseExchange ex = orderSide == OrderSide.LONG ? op.exchanges().longEx() : op.exchanges().shortEx();
		Supplier<CompletableFuture<Void>> configureFutures = orderSide == OrderSide.LONG ?
						() -> configureFutures(true) :
						() -> configureFutures(false);
		FuturesOrder order = buildFuturesOrder(orderSide, tradeSide);
		return configureFutures.get()
						.thenCompose(_ -> ex.privateHttpClient().placeFuturesOrder(coin, order))
						.exceptionally((t) -> {
							throw new RuntimeException("Failed to place futures order: " + t.getMessage());
						});
	}

	private CompletableFuture<String> placeSpotOrder(OrderSide orderSide, TradeSide tradeSide) {
		BaseExchange ex = orderSide == OrderSide.LONG ? op.exchanges().longEx() : op.exchanges().shortEx();
		SpotOrder order = buildSpotOrder(orderSide, tradeSide);
		return ex.privateHttpClient().placeSpotOrder(coin, order)
						.exceptionally((t) -> {
							throw new RuntimeException("Failed to place spot order: " + t.getMessage());
						});
	}

	private CompletableFuture<String> enterLong() {
		return switch (op.directions().longMarket()) {
			case FUTURES -> placeFuturesOrder(OrderSide.LONG, TradeSide.OPEN);
			case SPOT -> placeSpotOrder(OrderSide.LONG, TradeSide.OPEN);
		};
	}

	private CompletableFuture<String> enterShort() {
		return switch (op.directions().shortMarket()) {
			case FUTURES -> placeFuturesOrder(OrderSide.SHORT, TradeSide.OPEN);
			case SPOT -> placeSpotOrder(OrderSide.SHORT, TradeSide.OPEN);
		};
	}

	private CompletableFuture<String> exitLong() {
		return switch (op.directions().longMarket()) {
			case FUTURES -> placeFuturesOrder(OrderSide.LONG, TradeSide.CLOSE);
			case SPOT -> placeSpotOrder(OrderSide.LONG, TradeSide.CLOSE);
		};
	}

	private CompletableFuture<String> exitShort() {
		return switch (op.directions().shortMarket()) {
			case FUTURES -> placeFuturesOrder(OrderSide.SHORT, TradeSide.CLOSE);
			case SPOT -> placeSpotOrder(OrderSide.SHORT, TradeSide.CLOSE);
		};
	}

	private CompletableFuture<TradeIds> enterInternal() {
		var longEnterFuture = enterLong().whenComplete((_, t) -> {
			if (t != null) tradeLogger.logEnterFailure(unwrap(t), true);
			else tradeLogger.logEnterSuccess(op.longData().snapshot(), true);
		});
		var shortEnterFuture = enterShort().whenComplete((_, t) -> {
			if (t != null) tradeLogger.logEnterFailure(unwrap(t), false);
			else tradeLogger.logEnterSuccess(op.shortData().snapshot(), false);
		});

		return CompletableFuture.allOf(longEnterFuture, shortEnterFuture).thenApply(_ -> {
			String longId = longEnterFuture.join();
			String shortId = shortEnterFuture.join();
			return new TradeIds(longId, shortId);
		}).exceptionallyComposeAsync(t -> {
			if (longEnterFuture.isCompletedExceptionally() && shortEnterFuture.isCompletedExceptionally()) {
				throw new RuntimeException("Both long and short enter attempts failed: " + t.getMessage());
			} else if (longEnterFuture.isCompletedExceptionally()) return oneLegFailedScenario(t, shortEnterFuture, true);
			else if (shortEnterFuture.isCompletedExceptionally()) return oneLegFailedScenario(t, longEnterFuture, false);
			throw new RuntimeException("Error while failsafe exiting: " + t.getMessage());
		});
	}

	private CompletableFuture<TradeIds> oneLegFailedScenario(
					Throwable failedThrowable,
					CompletableFuture<String> successEnter,
					boolean longFailed
	) {
		String failedName = longFailed ? "long" : "short";
		String successName = longFailed ? "short" : "long";
		Supplier<CompletableFuture<String>> successExit = longFailed ? this::exitShort : this::exitLong;

		String commonError = "%s enter failed: %s, %s ".formatted(
						failedName,
						failedThrowable.getMessage(),
						successName
		);

		return successEnter.thenCompose(_ ->
						successExit.get()
										.exceptionally(t2 -> {
											tradeLogger.logEnterCompensationFailure(!longFailed);
											throw new RuntimeException(commonError +
																								 "compensation failed. Exit manually. " +
																								 t2.getMessage());
										})
										.thenApply(_ -> {
											tradeLogger.logEnterCompensationSuccess(!longFailed);
											throw new RuntimeException(commonError + "was compensated.");
										})
		);
	}

	private CompletableFuture<TradeIds> exitInternal(Snapshot currLong, Snapshot currShort) {
		var longExit = exitLong().whenComplete((_, t) -> {
			if (t != null) tradeLogger.logExitFailure(unwrap(t), true);
			else tradeLogger.logExitSuccess(currLong, true);
		});
		var shortExit = exitShort().whenComplete((_, t) -> {
			if (t != null) tradeLogger.logExitFailure(unwrap(t), false);
			else tradeLogger.logExitSuccess(currShort, false);
		});

		return CompletableFuture.allOf(longExit, shortExit).thenApply(_ -> {
			String longId = longExit.join();
			String shortId = shortExit.join();
			return new TradeIds(longId, shortId);
		});
	}

	private FuturesOrder buildFuturesOrder(OrderSide orderSide, TradeSide tradeSide) {
		return new FuturesOrder(
						orderSide,
						tradeSide,
						tradeParams.baseAssetQty(),
						tradeParams.longContractQty(),
						leverages.longLeverage(),
						FUTURES_MARGIN_MODE
		);
	}

	private SpotOrder buildSpotOrder(OrderSide orderSide, TradeSide tradeSide) {
		return new SpotOrder(orderSide, tradeSide, tradeParams.baseAssetQty());
	}

	private CompletableFuture<Void> configureFutures(boolean isLong) {
		String name = isLong ? "long" : "short";
		BaseExchange ex = isLong ? op.exchanges().longEx() : op.exchanges().shortEx();
		int leverage = isLong ? leverages.longLeverage() : leverages.shortLeverage();
		CompletableFuture<Void> marginModeFuture = ex.privateHttpClient().setMarginMode(coin, FUTURES_MARGIN_MODE)
						.exceptionally((t) -> {
							throw new RuntimeException("Failed to set " + name + " futures margin mode: " + t.getMessage());
						});
		CompletableFuture<Void> leverageFuture = ex.privateHttpClient().changeLeverage(coin, leverage)
						.exceptionally((t) -> {
							throw new RuntimeException("Failed to set " + name + " futures leverage: " + t.getMessage());
						});
		return CompletableFuture.allOf(marginModeFuture, leverageFuture);
	}

	private Throwable unwrap(Throwable t) {
		return t.getCause() == null ? t : t.getCause();
	}

	private TradeParams getEnterParams(Snapshot longEnter, Snapshot shortEnter) {
		BigDecimal baseAssetQty = getBaseAssetQty(longEnter, shortEnter);

		if (baseAssetQty.equals(BigDecimal.ZERO))
			throw new RuntimeException("Not enough margin deposited for coin: " + coin + ". Did not enter trades");

		BigDecimal longLotSize = op.longData().constantData().lotSize();
		BigDecimal shortLotSize = op.shortData().constantData().lotSize();

		int longContractQty = baseAssetQty.divide(longLotSize, RoundingMode.FLOOR).intValueExact();
		int shortContractQty = baseAssetQty.divide(shortLotSize, RoundingMode.FLOOR).intValueExact();

		return new TradeParams(baseAssetQty, longContractQty, shortContractQty);
	}

	private BigDecimal getBaseAssetQty(Snapshot longEnter, Snapshot shortEnter) {
		BigDecimal longLotSize = op.longData().constantData().lotSize(); // 2 COIN
		BigDecimal shortLotSize = op.shortData().constantData().lotSize(); // 3 COIN
		BigDecimal effectiveLotSize = lcm(longLotSize, shortLotSize); // 6 COIN

		BigDecimal longAsk = longEnter.bookTicker().askPrice(); // 10 usdt/COIN
		BigDecimal shortBid = shortEnter.bookTicker().bidPrice(); // 15 usdt/COIN

		BigDecimal longELSMultiplier = config.legUsdtAmount() // 300 usdt
						.divide(longAsk, RoundingMode.FLOOR)
						.divide(effectiveLotSize, RoundingMode.FLOOR); // 5 -

		BigDecimal shortELSMultiplier = config.legUsdtAmount()
						.divide(shortBid, RoundingMode.FLOOR)
						.divide(effectiveLotSize, RoundingMode.FLOOR); // 3 -

		BigDecimal effectiveELSMultiplier = longELSMultiplier.min(shortELSMultiplier); // 3 -
		return effectiveELSMultiplier.multiply(effectiveLotSize); // 18 COIN
	}
}
