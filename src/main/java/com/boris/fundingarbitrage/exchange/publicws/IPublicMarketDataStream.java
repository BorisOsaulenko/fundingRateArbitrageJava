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

	void unsubscribeCoinsFutures(Set<String> coins);

	void unsubscribeCoinsSpot(Set<String> coins);
}
