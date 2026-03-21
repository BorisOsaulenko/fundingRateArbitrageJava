package com.boris.fundingarbitrage.execution;

import com.boris.fundingarbitrage.exchange.BaseExchange;
import com.boris.fundingarbitrage.model.assetops.*;
import com.boris.fundingarbitrage.util.logger.Logger;
import lombok.Getter;
import lombok.NonNull;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

public class CoinExecution {
	private static final MarginMode marginMode = MarginMode.CROSS;
	private final String coin;
	private final TradeParams params;
	private final Leverages leverages;
	private CompletableFuture<Void> enterFuture = null;
	private CompletableFuture<Void> exitFuture = null;
	private volatile boolean failed = false;

	@Getter private volatile TradeIds enterIds;
	@Getter private volatile TradeIds exitIds;

	public CoinExecution(
					@NonNull String coin, @NonNull TradeParams params, @NonNull Leverages leverages) {
		this.leverages = leverages;
		this.coin = coin;
		this.params = params;
	}

	private FuturesOrder getLongOrder(boolean opening) {
		TradeSide tradeSide = opening ? TradeSide.OPEN : TradeSide.CLOSE;
		return new FuturesOrder(
						OrderSide.LONG,
						tradeSide,
						params.baseAssetQty(),
						params.longContractQty(),
						leverages.longLeverage(),
						marginMode
		);
	}

	private FuturesOrder getShortOrder(boolean opening) {
		TradeSide tradeSide = opening ? TradeSide.OPEN : TradeSide.CLOSE;
		return new FuturesOrder(
						OrderSide.SHORT,
						tradeSide,
						params.baseAssetQty(),
						params.shortContractQty(),
						leverages.shortLeverage(),
						marginMode
		);
	}

	private CompletableFuture<Void> setMarginMode(boolean isLong) {
		BaseExchange ex = isLong ? params.longEx() : params.shortEx();
		String side = isLong ? "long" : "short";
		return ex.privateHttpClient.setMarginMode(coin, marginMode)
						.exceptionally((t) -> {
							Logger.error("Failed to set margin mode for " + coin + " on " + side + "(" + ex.name + ")");
							Logger.error("Message: " + t.getMessage());
							throw new RuntimeException(t);
						})
						.thenRun(() -> Logger.log("Margin mode updated for " + coin + " on " + side + "(" + ex.name + ")"));
	}

	private CompletableFuture<Void> setLeverageRequest(boolean isLong) {
		BaseExchange ex = isLong ? params.longEx() : params.shortEx();
		String side = isLong ? "long" : "short";
		int leverage = isLong ? leverages.longLeverage() : leverages.shortLeverage();
		return ex.privateHttpClient.changeLeverage(coin, leverage)
						.exceptionally((t) -> {
							Logger.error("Failed to set maxLeverage for " + coin + " on" + side + "(" + ex.name + ")");
							Logger.error("Message: " + t.getMessage());
							throw new RuntimeException(t);
						})
						.thenRun(() -> Logger.log("Leverage updated for " + coin + " on " + side + "(" + ex.name + ")"));
	}

	private CompletableFuture<Void> enterAfterMarginModeIsSet() {
		FuturesOrder longOrder = getLongOrder(true);
		FuturesOrder shortOrder = getShortOrder(true);

		var LEnter = params.longEx().privateHttpClient.placeFuturesOrder(coin, longOrder);
		var SEnter = params.shortEx().privateHttpClient.placeFuturesOrder(coin, shortOrder);

		CompletableFuture<Void> future = CompletableFuture.allOf(LEnter, SEnter).thenAccept(_ -> {
			String longId = LEnter.join();
			String shortId = SEnter.join();
			this.enterIds = new TradeIds(longId, shortId);
			Logger.log(
							"Entered trade for %s, long: %s (%s) | short: %s (%s)",
							coin,
							params.longEx().name,
							longId,
							params.shortEx().name,
							shortId
			);
		}).exceptionallyComposeAsync(t -> {
			if (LEnter.isCompletedExceptionally() && SEnter.isCompletedExceptionally()) {
				failed = true;
				Logger.log("Failed to enter trade (both legs) for " +
									 coin + ", " + params.longEx().name + " and " + params.shortEx().name + ": " + t.getMessage());
				throw new RuntimeException(t);
			} else if (LEnter.isCompletedExceptionally()) {
				Logger.error("Failed to enter trade for long" + coin + ", " + params.longEx().name + ": " + t.getMessage());
				Logger.error("Attempting to exit short (" + params.shortEx().name + ") automatically.");
				return SEnter.thenCompose(_ -> exitShort().thenAccept(_ -> failed = true))
								.thenCompose(_ -> CompletableFuture.failedFuture(
												new IllegalStateException(coin + ": long enter failed; short was compensated")
								));
			} else if (SEnter.isCompletedExceptionally()) {
				Logger.error("Failed to enter trade for short" + coin + ", " + params.shortEx().name + ": " + t.getMessage());
				Logger.error("Attempting to exit long (" + params.longEx().name + ") automatically.");
				return LEnter.thenCompose(_ -> exitLong().thenAccept(_ -> failed = true))
								.thenCompose(_ -> CompletableFuture.failedFuture(
												new IllegalStateException(coin + ": short enter failed; long was compensated")
								));
			}

			Logger.error("Error while failsafe exiting for " + coin + ": " + t.getMessage());
			throw new RuntimeException(t);
		});

		enterFuture = future;
		return future;
	}

	public synchronized CompletableFuture<Void> enterTrade() {
		if (failed) return CompletableFuture.completedFuture(null);
		if (enterFuture != null) return enterFuture;

		CompletableFuture<Void> longMargin = setMarginMode(true);
		CompletableFuture<Void> shortMargin = setMarginMode(false);

		CompletableFuture<Void> longLeverage = setLeverageRequest(true);
		CompletableFuture<Void> shortLeverage = setLeverageRequest(false);

		return CompletableFuture.allOf(longMargin, shortMargin, longLeverage, shortLeverage)
						.thenCompose(_ -> enterAfterMarginModeIsSet());
	}

	public synchronized CompletableFuture<Void> exitTrade() {
		if (!shouldAttemptExit()) return CompletableFuture.completedFuture(null);
		if (exitFuture != null) return exitFuture;

		CompletableFuture<String> LExit = exitLong();
		CompletableFuture<String> SExit = exitShort();

		CompletableFuture<Void> future = CompletableFuture.allOf(LExit, SExit).thenAccept(_ -> {
			String longId = LExit.join();
			String shortId = SExit.join();
			this.exitIds = new TradeIds(longId, shortId);
			Logger.log(
							"Exited trade for %s, long: %s (%s) | short: %s (%s)",
							coin,
							params.longEx().name,
							longId,
							params.shortEx().name,
							shortId
			);
		});

		exitFuture = future;
		return future;
	}

	private boolean shouldAttemptExit() {
		if (failed) return false;
		if (enterFuture == null) return false;
		return enterFuture.state().equals(Future.State.SUCCESS);
	}

	private CompletableFuture<String> exitLong() {
		FuturesOrder longOrder = getLongOrder(false);
		CompletableFuture<String> LExit = params.longEx().privateHttpClient.placeFuturesOrder(coin, longOrder);
		return LExit.exceptionally(t -> {
			Logger.error("Failed to exit trade for " + coin + ", " + params.longEx().name + ": " + t.getMessage());
			failed = true;
			throw new RuntimeException(t);
		});
	}

	private CompletableFuture<String> exitShort() {
		FuturesOrder shortOrder = getShortOrder(false);
		CompletableFuture<String> SExit = params.shortEx().privateHttpClient.placeFuturesOrder(coin, shortOrder);
		return SExit.exceptionally(t -> {
			Logger.error("Failed to exit trade for " + coin + ", " + params.shortEx().name + ": " + t.getMessage());
			failed = true;
			throw new RuntimeException(t);
		});
	}

	public record TradeIds(String longId, String shortId) {
	}
}
