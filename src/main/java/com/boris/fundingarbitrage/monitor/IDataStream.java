package com.boris.fundingarbitrage.monitor;

import com.boris.fundingarbitrage.exchange.BaseExchange;
import com.boris.fundingarbitrage.exchange.publicws.FuturesHandler;
import com.boris.fundingarbitrage.exchange.publicws.SpotHandler;
import lombok.NonNull;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

public interface IDataStream {
	@NonNull
	CompletableFuture<Void> initFuture();

	@NonNull
	CompletableFuture<Void> openWsConnections(Set<BaseExchange> exchanges);

	void onSteadyData(Runnable onSteadyData);

	default void subscribeFutures(BaseExchange ex, Set<String> coins, FuturesHandler handler) {
		ex.publicWsClient().subscribeFutures(coins, handler);
	}

	default void subscribeSpot(BaseExchange ex, Set<String> coins, SpotHandler handler) {
		ex.publicWsClient().subscribeSpot(coins, handler);
	}

	default void unsubscribeFutures(BaseExchange ex, Set<String> coins) {
		ex.publicWsClient().unsubscribeFutures(coins);
	}

	default void unsubscribeSpot(BaseExchange ex, Set<String> coins) {
		ex.publicWsClient().unsubscribeSpot(coins);
	}

	default void unsubscribeAll(BaseExchange ex, Set<String> coins) {
		unsubscribeFutures(ex, coins);
		unsubscribeSpot(ex, coins);
	}
}