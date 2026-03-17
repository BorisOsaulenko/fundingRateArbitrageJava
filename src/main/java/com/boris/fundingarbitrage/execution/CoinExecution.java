package com.boris.fundingarbitrage.execution;

import com.boris.fundingarbitrage.model.assetops.*;
import com.boris.fundingarbitrage.util.logger.Logger;
import lombok.Getter;
import lombok.Setter;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

public class CoinExecution {
	@Setter private static Integer leverage = null;
	private final String coin;
	private final TradeParams params;

	private CompletableFuture<Void> enterFuture = null;
	private CompletableFuture<Void> exitFuture = null;
	private volatile boolean failed = false;

	@Getter private TradeIds enterIds;
	@Getter private TradeIds exitIds;

	public CoinExecution(String coin, TradeParams params) {
		if (leverage == null) throw new IllegalStateException("Leverage not set");
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
						leverage,
						MarginMode.CROSS
		);
	}

	private FuturesOrder getShortOrder(boolean opening) {
		TradeSide tradeSide = opening ? TradeSide.OPEN : TradeSide.CLOSE;
		return new FuturesOrder(
						OrderSide.SHORT,
						tradeSide,
						params.baseAssetQty(),
						params.shortContractQty(),
						leverage,
						MarginMode.CROSS
		);
	}

	public synchronized CompletableFuture<Void> enterTrade() {
		if (failed) return CompletableFuture.completedFuture(null);
		if (enterFuture != null) return enterFuture;
		FuturesOrder longOrder = getLongOrder(true);
		FuturesOrder shortOrder = getShortOrder(true);

		CompletableFuture<String> LEnter = params.longEx().privateHttpClient.placeFuturesOrder(coin, longOrder);
		CompletableFuture<String> SEnter = params.shortEx().privateHttpClient.placeFuturesOrder(coin, shortOrder);

		CompletableFuture<Void> future = CompletableFuture.allOf(LEnter, SEnter).thenAccept(_ -> {
			String longId = LEnter.join();
			String shortId = SEnter.join();
			this.enterIds = new TradeIds(longId, shortId);
		}).exceptionallyComposeAsync(t -> {
			if (LEnter.isCompletedExceptionally() && SEnter.isCompletedExceptionally()) {
				failed = true;
				Logger.log("Failed to enter trade (both legs) for " +
									 coin +
									 ", " +
									 params.longEx().name +
									 " and " +
									 params.shortEx().name +
									 ": " +
									 t.getMessage());
				throw new RuntimeException(t);
			} else if (LEnter.isCompletedExceptionally()) {
				Logger.error("Failed to enter trade for long" + coin + ", " + params.longEx().name + ": " + t.getMessage());
				Logger.error("Attempting to exit short automatically.");
				return SEnter.thenCompose(_ -> exitShort().thenAccept(_ -> failed = true))
								.thenCompose(_ -> CompletableFuture.failedFuture(
												new IllegalStateException(coin + ": long enter failed; short was compensated")
								));
			} else if (SEnter.isCompletedExceptionally()) {
				Logger.error("Failed to enter trade for short" + coin + ", " + params.shortEx().name + ": " + t.getMessage());
				Logger.error("Attempting to exit long automatically.");
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

	public synchronized CompletableFuture<Void> exitTrade() {
		if (!shouldAttemptExit()) return CompletableFuture.completedFuture(null);
		if (exitFuture != null) return exitFuture;

		CompletableFuture<String> LExit = exitLong();
		CompletableFuture<String> SExit = exitShort();

		CompletableFuture<Void> future = CompletableFuture.allOf(LExit, SExit).thenAccept(_ -> {
			String longId = LExit.join();
			String shortId = SExit.join();
			this.exitIds = new TradeIds(longId, shortId);
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
