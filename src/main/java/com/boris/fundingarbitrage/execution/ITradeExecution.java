package com.boris.fundingarbitrage.execution;

import com.boris.fundingarbitrage.model.exchange.snapshot.Snapshot;

import java.util.concurrent.CompletableFuture;

public interface ITradeExecution {
	CompletableFuture<Void> enterTrade();

	CompletableFuture<Void> exitTrade(Snapshot currLong, Snapshot currShort);

	TradeIds getEnterIds();

	TradeIds getExitIds();
}
