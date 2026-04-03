package com.boris.fundingarbitrage.execution;

import com.boris.fundingarbitrage.logic.TradeLogger;
import com.boris.fundingarbitrage.strategy.pretradestrategy.TradeDirections;
import lombok.Getter;
import lombok.NonNull;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

public abstract class CoinExecution {
	protected final String coin;
	protected final TradeDirections tradeDirections;
	protected final TradeLogger tradeLogger;
	protected CompletableFuture<Void> enterFuture = null;
	protected CompletableFuture<Void> exitFuture = null;
	@Getter protected TradeIds enterIds;
	@Getter protected TradeIds exitIds;
	private volatile boolean failed = false;

	public CoinExecution(
					@NonNull String coin,
					TradeLogger tradeLogger,
					TradeDirections tradeDirections
	) {
		this.coin = coin;
		this.tradeLogger = tradeLogger;
		this.tradeDirections = tradeDirections;
		tradeLogger.log("Logs for %s", coin);
	}

	protected abstract CompletableFuture<TradeIds> enterInternal();

	public CompletableFuture<Void> enterTrade() {
		if (failed) return CompletableFuture.completedFuture(null);
		if (enterFuture != null) return enterFuture;

		return this.enterFuture = enterInternal().thenAccept(ids -> {
			this.enterIds = ids;
			tradeLogger.log("Entered trade: " + this.enterIds);
		}).exceptionally(t -> {
			tradeLogger.error("Failed to enter trade: " + t.getMessage());
			failed = true;
			return null;
		});
	}

	protected abstract CompletableFuture<TradeIds> exitInternal();

	public CompletableFuture<Void> exitTrade() {
		if (!shouldAttemptExit()) return CompletableFuture.completedFuture(null);
		if (exitFuture != null) return exitFuture;

		return this.exitFuture = exitInternal().thenAccept(ids -> {
			this.exitIds = ids;
			tradeLogger.log("Exited trade: " + this.exitIds);
		}).exceptionally(t -> {
			tradeLogger.error("Failed to exit trade: " + t.getMessage());
			failed = true;
			return null;
		});
	}

	private boolean shouldAttemptExit() {
		if (failed) return false;
		if (enterFuture == null) return false;
		return enterFuture.state().equals(Future.State.SUCCESS);
	}

	public record TradeIds(String longId, String shortId) {
	}
}
