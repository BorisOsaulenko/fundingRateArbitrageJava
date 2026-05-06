package com.boris.fundingarbitrage.execution;

import com.boris.fundingarbitrage.exchange.BaseExchange;
import com.boris.fundingarbitrage.logic.ArbitrageBotConfig;
import com.boris.fundingarbitrage.logic.CoinOpportunity;
import com.boris.fundingarbitrage.model.assetops.*;
import lombok.NonNull;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public class ClassicCoinExecution extends CoinExecution {
	private static final MarginMode FUTURES_MARGIN_MODE = MarginMode.CROSS;

	public ClassicCoinExecution(
					@NonNull String coin,
					@NonNull CoinOpportunity op,
					@NonNull ArbitrageBotConfig config
	) {
		super(coin, op, config);
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
							throw new RuntimeException("Failed to enter long leg: " + t.getMessage());
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

	@Override
	protected CompletableFuture<TradeIds> enterInternal() {
		CompletableFuture<String> longEnter = enterLong();
		CompletableFuture<String> shortEnter = enterShort();

		return CompletableFuture.allOf(longEnter, shortEnter).thenApply(_ -> {
			String longId = longEnter.join();
			String shortId = shortEnter.join();
			this.enterIds = new TradeIds(longId, shortId);
			return new TradeIds(longId, shortId);
		}).exceptionallyComposeAsync(t -> {
			if (longEnter.isCompletedExceptionally() && shortEnter.isCompletedExceptionally()) {
				throw new RuntimeException("Both long and short enter attempts failed: " + t.getMessage());
			} else if (longEnter.isCompletedExceptionally()) return oneLegFailedScenario(t, shortEnter, true);
			else if (shortEnter.isCompletedExceptionally()) return oneLegFailedScenario(t, longEnter, false);
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
											throw new RuntimeException(commonError +
																								 "compensation failed. Exit manually. %s".formatted(t2.getMessage()));
										})
										.thenApply(_ -> {
											throw new RuntimeException(commonError + "was compensated.");
										})
		);
	}

	@Override
	protected CompletableFuture<TradeIds> exitInternal() {
		CompletableFuture<String> longExit = exitLong();
		CompletableFuture<String> shortExit = exitShort();

		return CompletableFuture.allOf(longExit, shortExit).thenApply(_ -> {
			String longId = longExit.join();
			String shortId = shortExit.join();
			return new TradeIds(longId, shortId);
		}).exceptionallyComposeAsync(t -> {
			String originalError = t.getMessage();
			if (longExit.isCompletedExceptionally() && shortExit.isCompletedExceptionally()) {
				throw new RuntimeException("Both long and short exit attempts failed. Original error: " + originalError);
			}
			if (longExit.isCompletedExceptionally()) {
				throw new RuntimeException("Long exit failed. Short exit succeeded. Original error: " + originalError);
			}
			if (shortExit.isCompletedExceptionally()) {
				throw new RuntimeException("Short exit failed. Long exit succeeded. Original error: " + originalError);
			}
			throw new RuntimeException("Unexpected exit failure state. Original error: " + originalError);
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
}
