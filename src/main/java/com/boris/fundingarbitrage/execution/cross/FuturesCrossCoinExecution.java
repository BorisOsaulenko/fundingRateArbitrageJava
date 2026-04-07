package com.boris.fundingarbitrage.execution.cross;

import com.boris.fundingarbitrage.execution.CrossCoinExecution;
import com.boris.fundingarbitrage.model.assetops.*;
import com.boris.fundingarbitrage.model.exchange.ExchangePair;
import com.boris.fundingarbitrage.strategy.TradeMarket;
import com.boris.fundingarbitrage.strategy.pretradestrategy.TradeDirections;
import com.boris.fundingarbitrage.tradelogger.TradeLogger;
import lombok.NonNull;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public class FuturesCrossCoinExecution extends CrossCoinExecution {
	private static final MarginMode FUTURES_MARGIN_MODE = MarginMode.CROSS;

	private final TradeParams tradeParams;
	private final Leverages leverages;

	public FuturesCrossCoinExecution(
					@NonNull String coin,
					@NonNull ExchangePair exchanges,
					@NonNull TradeParams tradeParams,
					@NonNull Leverages leverages,
					TradeLogger tradeLogger
	) {
		super(coin, tradeLogger, new TradeDirections(TradeMarket.FUTURES, TradeMarket.FUTURES), exchanges);
		this.tradeParams = tradeParams;
		this.leverages = leverages;
	}

	@Override
	protected CompletableFuture<TradeIds> enterInternal() {
		FuturesOrder longOrder = buildLongOrder(TradeSide.OPEN);
		FuturesOrder shortOrder = buildShortOrder(TradeSide.OPEN);

		CompletableFuture<String> longEnter = configureLongFutures()
						.thenCompose(_ -> exchanges.longEx().privateHttpClient.placeFuturesOrder(coin, longOrder))
						.whenComplete((orderId, t) -> logCompletion("long open order", orderId, t));
		CompletableFuture<String> shortEnter = configureShortFutures()
						.thenCompose(_ -> exchanges.shortEx().privateHttpClient.placeFuturesOrder(coin, shortOrder))
						.whenComplete((orderId, t) -> logCompletion("short open order", orderId, t));

		return CompletableFuture.allOf(longEnter, shortEnter).thenApply(_ -> {
			String longId = longEnter.join();
			String shortId = shortEnter.join();
			this.enterIds = new TradeIds(longId, shortId);
			tradeLogger.log("Entered trades. Long: " + longId + " | short: " + shortId);
			return new TradeIds(longId, shortId);
		}).exceptionallyComposeAsync(t -> {
			if (longEnter.isCompletedExceptionally() && shortEnter.isCompletedExceptionally()) {
				tradeLogger.log("Failed to enter trade on both legs: " + t.getMessage());
				throw new RuntimeException(t);
			} else if (longEnter.isCompletedExceptionally()) return oneLegFailedScenario(t, shortEnter, true);
			else if (shortEnter.isCompletedExceptionally()) return oneLegFailedScenario(t, longEnter, false);

			tradeLogger.error("Error while failsafe exiting: " + t.getMessage());
			throw new RuntimeException(t);
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

		tradeLogger.error("Failed to enter trade for %s: %s", failedName, failedThrowable.getMessage());
		tradeLogger.error("Attempting to exit %s automatically.", successName);
		return successEnter.thenCompose(_ ->
						successExit.get()
										.exceptionally(t2 -> {
											tradeLogger.error(
															"%s enter failed, %s compensation failed. Exit manually.",
															failedName,
															successName
											);
											throw new RuntimeException(t2);
										})
										.thenApply(_ -> {
											tradeLogger.error("%s enter failed; %s was compensated", failedName, successName);
											throw new RuntimeException(
															String.format("%s: %s enter failed, %s was compensated", coin, failedName, successName)
											);
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
			tradeLogger.log("Exited trade, long: %s | short: %s", longId, shortId);
			return new TradeIds(longId, shortId);
		});
	}

	private FuturesOrder buildLongOrder(TradeSide tradeSide) {
		return new FuturesOrder(
						OrderSide.LONG,
						tradeSide,
						tradeParams.baseAssetQty(),
						tradeParams.longContractQty(),
						leverages.longLeverage(),
						FUTURES_MARGIN_MODE
		);
	}

	private FuturesOrder buildShortOrder(TradeSide tradeSide) {
		return new FuturesOrder(
						OrderSide.SHORT,
						tradeSide,
						tradeParams.baseAssetQty(),
						tradeParams.shortContractQty(),
						leverages.shortLeverage(),
						FUTURES_MARGIN_MODE
		);
	}

	private CompletableFuture<Void> configureLongFutures() {
		CompletableFuture<Void> marginModeFuture = exchanges.longEx().privateHttpClient.setMarginMode(
										coin,
										FUTURES_MARGIN_MODE
						)
						.whenComplete((_, t) -> logCompletion("long futures margin mode update", FUTURES_MARGIN_MODE.name(), t));
		CompletableFuture<Void> leverageFuture = exchanges.longEx().privateHttpClient.changeLeverage(
										coin,
										leverages.longLeverage()
						)
						.whenComplete((_, t) -> logCompletion(
										"long futures leverage update",
										String.valueOf(leverages.longLeverage()),
										t
						));
		return CompletableFuture.allOf(marginModeFuture, leverageFuture);
	}

	private CompletableFuture<Void> configureShortFutures() {
		CompletableFuture<Void> marginModeFuture = exchanges.shortEx().privateHttpClient.setMarginMode(
										coin,
										FUTURES_MARGIN_MODE
						)
						.whenComplete((_, t) -> logCompletion("short futures margin mode update", FUTURES_MARGIN_MODE.name(), t));
		CompletableFuture<Void> leverageFuture = exchanges.shortEx().privateHttpClient.changeLeverage(
										coin,
										leverages.shortLeverage()
						)
						.whenComplete((_, t) -> logCompletion(
										"short futures leverage update",
										String.valueOf(leverages.shortLeverage()),
										t
						));
		return CompletableFuture.allOf(marginModeFuture, leverageFuture);
	}

	private CompletableFuture<String> exitLong() {
		FuturesOrder order = buildLongOrder(TradeSide.CLOSE);
		return exchanges.longEx().privateHttpClient.placeFuturesOrder(coin, order)
						.whenComplete((msg, t) -> {
							if (t == null) {
								tradeLogger.log("Exited long normally.");
							} else {
								tradeLogger.error("Failed to exit long leg: " + t.getMessage());
							}
						});
	}

	private CompletableFuture<String> exitShort() {
		FuturesOrder order = buildShortOrder(TradeSide.CLOSE);
		return exchanges.shortEx().privateHttpClient.placeFuturesOrder(coin, order)
						.whenComplete((msg, t) -> {
							if (t == null) {
								tradeLogger.log("Exited short normally.");
							} else {
								tradeLogger.error("Failed to exit short leg: " + t.getMessage());
							}
						});
	}

	private void logCompletion(String action, String successValue, Throwable t) {
		if (t == null) {
			tradeLogger.log("Successful %s: %s", action, successValue);
			return;
		}
		tradeLogger.error("Failed %s: %s", action, t.getMessage());
	}
}
