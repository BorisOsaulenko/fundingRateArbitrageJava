package com.boris.fundingarbitrage.exchange.publicws;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

public interface IPublicMarketDataStream {
	CompletableFuture<Void> connect();

	void close();

	void subscribeFutures(Set<String> coins, FuturesHandler handler);

	void subscribeSpot(Set<String> coins, SpotHandler handler);

	default void subscribeFutures(String coin, FuturesHandler handler) {
		subscribeFutures(Set.of(coin), handler);
	}

	default void subscribeSpot(String coin, SpotHandler handler) {
		subscribeSpot(Set.of(coin), handler);
	}

	void unsubscribeFutures(Set<String> coins);

	void unsubscribeSpot(Set<String> coins);

	default void unsubscribeFutures(String coin) {
		unsubscribeFutures(Set.of(coin));
	}

	default void unsubscribeSpot(String coin) {
		unsubscribeSpot(Set.of(coin));
	}
}
