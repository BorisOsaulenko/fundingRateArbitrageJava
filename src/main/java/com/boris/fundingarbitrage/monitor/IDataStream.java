package com.boris.fundingarbitrage.monitor;

import com.boris.fundingarbitrage.exchange.BaseExchange;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

public interface IDataStream {
	CompletableFuture<Void> initFuture();

	CompletableFuture<Void> openWsConnections();

	void onSteadyData(Runnable onSteadyData);

	void unsubscribeFutures(BaseExchange ex, Set<String> coins);

	void unsubscribeSpot(BaseExchange ex, Set<String> coins);

	default void unsubscribeAll(BaseExchange ex, Set<String> coins) {
		unsubscribeFutures(ex, coins);
		unsubscribeSpot(ex, coins);
	}
}
