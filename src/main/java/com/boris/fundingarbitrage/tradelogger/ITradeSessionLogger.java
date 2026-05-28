package com.boris.fundingarbitrage.tradelogger;

import com.boris.fundingarbitrage.execution.TradeIds;
import com.boris.fundingarbitrage.model.exchange.snapshot.FuturesSnapshot;
import com.boris.fundingarbitrage.model.exchange.snapshot.Snapshot;

import java.util.concurrent.CompletableFuture;

public interface ITradeSessionLogger {
	void logEnterSuccess(Snapshot sn, boolean isLong);

	void logEnterCompensationSuccess(boolean isLong);

	void logEnterCompensationFailure(boolean isLong);

	void logEnterFailure(Throwable t, boolean isLong);

	void logFunding(FuturesSnapshot sn, boolean isLong);

	void logExitSuccess(Snapshot sn, boolean isLong);

	void logExitFailure(Throwable t, boolean isLong);

	CompletableFuture<Void> finish(TradeIds enterIds, TradeIds exitIds);
}
