package com.boris.fundingarbitrage.mocks;

import com.boris.fundingarbitrage.execution.TradeIds;
import com.boris.fundingarbitrage.model.exchange.snapshot.FuturesSnapshot;
import com.boris.fundingarbitrage.model.exchange.snapshot.Snapshot;
import com.boris.fundingarbitrage.tradelogger.ITradeSessionLogger;

import java.util.concurrent.CompletableFuture;

public class FakeTradeSessionLogger implements ITradeSessionLogger {
	@Override
	public void logEnterSuccess(Snapshot sn, boolean isLong) {
	}

	@Override
	public void logEnterCompensationSuccess(boolean isLong) {
	}

	@Override
	public void logEnterCompensationFailure(boolean isLong) {
	}

	@Override
	public void logEnterFailure(Throwable t, boolean isLong) {
	}

	@Override
	public void logFunding(FuturesSnapshot sn, boolean isLong) {
	}

	@Override
	public void logExitSuccess(Snapshot sn, boolean isLong) {
	}

	@Override
	public void logExitFailure(Throwable t, boolean isLong) {
	}

	@Override
	public CompletableFuture<Void> finish(TradeIds enterIds, TradeIds exitIds) {
		return CompletableFuture.completedFuture(null);
	}
}
