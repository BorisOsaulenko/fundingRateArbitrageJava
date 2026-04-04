package com.boris.fundingarbitrage.execution;

import com.boris.fundingarbitrage.logic.ExchangePair;
import com.boris.fundingarbitrage.logic.TradeLogger;
import com.boris.fundingarbitrage.model.assetops.OrderSide;
import com.boris.fundingarbitrage.model.assetops.SpotOrder;
import com.boris.fundingarbitrage.model.assetops.TradeSide;
import com.boris.fundingarbitrage.strategy.TradeMarket;
import com.boris.fundingarbitrage.strategy.pretradestrategy.TradeDirections;
import lombok.NonNull;

import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public class SpotCrossCoinExecution extends CrossCoinExecution {
	private final BigDecimal baseAssetQty;

	public SpotCrossCoinExecution(
					@NonNull String coin,
					@NonNull ExchangePair exchanges,
					@NonNull BigDecimal baseAssetQty,
					TradeLogger tradeLogger
	) {
		super(coin, tradeLogger, new TradeDirections(TradeMarket.SPOT, TradeMarket.SPOT), exchanges);
		this.baseAssetQty = baseAssetQty;
	}

	@Override
	protected CompletableFuture<TradeIds> enterInternal() {
		SpotOrder longOrder = new SpotOrder(OrderSide.LONG, TradeSide.OPEN, baseAssetQty);
		SpotOrder shortOrder = new SpotOrder(OrderSide.SHORT, TradeSide.OPEN, baseAssetQty);

		var LEnter = exchanges.longEx().privateHttpClient.placeSpotOrder(coin, longOrder);
		var SEnter = exchanges.shortEx().privateHttpClient.placeSpotOrder(coin, shortOrder);

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

	protected CompletableFuture<TradeIds> exitInternal() {
		CompletableFuture<String> LExit = exitLong();
		CompletableFuture<String> SExit = exitShort();

		return CompletableFuture.allOf(LExit, SExit).thenApply(_ -> {
			String longId = LExit.join();
			String shortId = SExit.join();
			tradeLogger.log("Exited trade, long: %s | short: %s", longId, shortId);
			return new TradeIds(longId, shortId);
		});
	}

	private CompletableFuture<String> exitLong() {
		SpotOrder order = new SpotOrder(OrderSide.LONG, TradeSide.CLOSE, baseAssetQty);
		return exchanges.longEx().privateHttpClient.placeSpotOrder(coin, order)
						.whenComplete((msg, t) -> {
							if (t == null) {
								tradeLogger.log("Exited long normally.");
							} else {
								tradeLogger.error("Failed to exit long leg: " + t.getMessage());
							}
						});
	}

	private CompletableFuture<String> exitShort() {
		SpotOrder order = new SpotOrder(OrderSide.SHORT, TradeSide.CLOSE, baseAssetQty);
		return exchanges.shortEx().privateHttpClient.placeSpotOrder(coin, order)
						.whenComplete((msg, t) -> {
							if (t == null) {
								tradeLogger.log("Exited short normally.");
							} else {
								tradeLogger.error("Failed to exit short leg: " + t.getMessage());
							}
						});
	}

}
