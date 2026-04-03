package com.boris.fundingarbitrage.execution;

import com.boris.fundingarbitrage.exchange.BaseExchange;
import com.boris.fundingarbitrage.logic.ExchangePair;
import com.boris.fundingarbitrage.logic.TradeLogger;
import com.boris.fundingarbitrage.model.assetops.*;
import com.boris.fundingarbitrage.strategy.pretradestrategy.TradeDirections;
import lombok.NonNull;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public class CrossCoinExecution extends CoinExecution {
	private static final MarginMode marginMode = MarginMode.CROSS;
	private final TradeParams params;
	private final Leverages leverages;
	private final ExchangePair exchanges;

	public CrossCoinExecution(
					@NonNull String coin,
					@NonNull ExchangePair exchanges,
					@NonNull TradeDirections tradeDirections,
					@NonNull TradeParams params,
					@NonNull Leverages leverages,
					TradeLogger tradeLogger
	) {
		super(coin, tradeLogger, tradeDirections);
		this.exchanges = exchanges;
		this.params = params;
		this.leverages = leverages;
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
		BaseExchange ex = isLong ? exchanges.longEx() : exchanges.shortEx();
		String side = isLong ? "long" : "short";
		return ex.privateHttpClient.setMarginMode(coin, marginMode)
						.exceptionally((t) -> {
							tradeLogger.error("Failed to set margin mode on " + side + ": " + t.getMessage());
							throw new RuntimeException(t);
						})
						.thenRun(() -> tradeLogger.log("Margin mode updated on " + side));
	}

	private CompletableFuture<Void> setLeverageRequest(boolean isLong) {
		BaseExchange ex = isLong ? exchanges.longEx() : exchanges.shortEx();
		String side = isLong ? "long" : "short";
		int leverage = isLong ? leverages.longLeverage() : leverages.shortLeverage();
		return ex.privateHttpClient.changeLeverage(coin, leverage)
						.exceptionally((t) -> {
							tradeLogger.error("Failed to set leverage on" + side + ": " + t.getMessage());
							throw new RuntimeException(t);
						})
						.thenRun(() -> tradeLogger.log("Leverage updated on " + side));
	}

	private CompletableFuture<TradeIds> enterAfterMarginModeIsSet() {
		FuturesOrder longOrder = getLongOrder(true);
		FuturesOrder shortOrder = getShortOrder(true);

		var LEnter = exchanges.longEx().privateHttpClient.placeFuturesOrder(coin, longOrder);
		var SEnter = exchanges.shortEx().privateHttpClient.placeFuturesOrder(coin, shortOrder);

		return CompletableFuture.allOf(LEnter, SEnter).thenApply(_ -> {
			String longId = LEnter.join();
			String shortId = SEnter.join();
			this.enterIds = new TradeIds(longId, shortId);
			tradeLogger.log("Entered trades. Long: " + longId + " | short: " + shortId);
			return new TradeIds(longId, shortId);
		}).exceptionallyComposeAsync(t -> {
			if (LEnter.isCompletedExceptionally() && SEnter.isCompletedExceptionally()) {
				tradeLogger.log("Failed to enter trade on both legs: " + t.getMessage());
				throw new RuntimeException(t);
			} else if (LEnter.isCompletedExceptionally()) return oneLegFailedScenario(t, SEnter, true);
			else if (SEnter.isCompletedExceptionally()) return oneLegFailedScenario(t, LEnter, false);

			tradeLogger.error("Error while failsafe exiting: " + t.getMessage());
			throw new RuntimeException(t);
		});
	}

	private CompletableFuture<TradeIds> oneLegFailedScenario(
					Throwable failedThrowable,
					CompletableFuture<String> successEnter,
					boolean longFailed
	) {
		String fName = longFailed ? "long" : "short";
		String sName = longFailed ? "short" : "long";
		Supplier<CompletableFuture<String>> sExit = longFailed ? this::exitShort : this::exitLong;

		tradeLogger.error("Failed to enter trade for %s: %s", fName, failedThrowable.getMessage());
		tradeLogger.error("Attempting to exit %s automatically.", sName);
		return successEnter.thenCompose(_ ->
						sExit.get()
										.exceptionally(t2 -> {
											tradeLogger.error("%s enter failed, %s compensation failed. Exit manually.", fName, sName);
											throw new RuntimeException(t2);
										})
										.thenApply(_ -> {
											tradeLogger.error("%s enter failed; %s was compensated", fName, sName);
											throw new RuntimeException(
															String.format("%s: %s enter failed, %s was compensated", coin, fName, sName)
											);
										})
		);
	}

	@Override
	protected CompletableFuture<TradeIds> enterInternal() {
		CompletableFuture<Void> longMargin = setMarginMode(true);
		CompletableFuture<Void> shortMargin = setMarginMode(false);

		CompletableFuture<Void> longLeverage = setLeverageRequest(true);
		CompletableFuture<Void> shortLeverage = setLeverageRequest(false);

		return CompletableFuture.allOf(longMargin, shortMargin, longLeverage, shortLeverage)
						.thenCompose(_ -> enterAfterMarginModeIsSet());
	}

	public CompletableFuture<Void> exitTrade() {
		CompletableFuture<String> LExit = exitLong();
		CompletableFuture<String> SExit = exitShort();

		CompletableFuture<Void> future = CompletableFuture.allOf(LExit, SExit).thenAccept(_ -> {
			String longId = LExit.join();
			String shortId = SExit.join();
			this.exitIds = new TradeIds(longId, shortId);
			tradeLogger.log(
							"Exited trade, long: %s | short: %s",
							longId,
							shortId
			);
		});

		exitFuture = future;
		return future;
	}

	private CompletableFuture<String> exitLong() {
		FuturesOrder longOrder = getLongOrder(false);
		CompletableFuture<String> LExit = exchanges.longEx().privateHttpClient.placeFuturesOrder(coin, longOrder);
		return LExit.exceptionally(t -> {
			tradeLogger.error("Failed to exit long leg: " + t.getMessage());
			throw new RuntimeException(t);
		});
	}

	private CompletableFuture<String> exitShort() {
		FuturesOrder shortOrder = getShortOrder(false);
		CompletableFuture<String> SExit = exchanges.shortEx().privateHttpClient.placeFuturesOrder(coin, shortOrder);
		return SExit.exceptionally(t -> {
			tradeLogger.error("Failed to exit short leg: " + t.getMessage());
			throw new RuntimeException(t);
		});
	}
}
