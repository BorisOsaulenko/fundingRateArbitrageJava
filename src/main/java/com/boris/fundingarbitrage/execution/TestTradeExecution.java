package com.boris.fundingarbitrage.execution;

import com.boris.fundingarbitrage.logic.CoinOpportunity;
import com.boris.fundingarbitrage.model.exchange.snapshot.Snapshot;
import com.boris.fundingarbitrage.tradelogger.ITradeSessionLogger;
import lombok.Getter;
import lombok.NonNull;

import java.util.concurrent.CompletableFuture;

public class TestTradeExecution implements ITradeExecution {
	private final CoinOpportunity op;
	private final ITradeSessionLogger tradeLogger;
	@Getter private TradeIds enterIds;
	@Getter private TradeIds exitIds;

	public TestTradeExecution(
					@NonNull CoinOpportunity op,
					@NonNull ITradeSessionLogger tradeLogger
	) {
		this.op = op;
		this.tradeLogger = tradeLogger;
	}

	@Override
	public CompletableFuture<Void> enterTrade() {
		enterIds = new TradeIds("longEnterId", "shortEnterId");
		tradeLogger.logEnterSuccess(op.longData().snapshot(), true);
		tradeLogger.logEnterSuccess(op.shortData().snapshot(), false);
		return CompletableFuture.completedFuture(null);
	}

	@Override
	public CompletableFuture<Void> exitTrade(Snapshot currLong, Snapshot currShort) {
		exitIds = new TradeIds("longExitId", "shortExitId");
		tradeLogger.logExitSuccess(currLong, true);
		tradeLogger.logExitSuccess(currShort, false);
		return CompletableFuture.completedFuture(null);
	}
}
