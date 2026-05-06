package com.boris.fundingarbitrage.exchange.publicws;

import com.boris.fundingarbitrage.model.websocket.patch.BookTickerPatch;
import com.boris.fundingarbitrage.model.websocket.patch.FundingRatePatch;
import com.boris.fundingarbitrage.model.websocket.patch.MarkPricePatch;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public interface PublicMarketDataStream {
	void onUnhandledDisconnect(Runnable hook);

	CompletableFuture<Void> connect();

	void close();

	void subscribeFuturesFundingRates(Set<String> coins, Consumer<FundingRatePatch> handler);

	default void subscribeFuturesFundingRates(String coin, Consumer<FundingRatePatch> handler) {
		subscribeFuturesFundingRates(Set.of(coin), handler);
	}

	void subscribeFuturesBookTicker(Set<String> coins, Consumer<BookTickerPatch> handler);

	default void subscribeFuturesBookTicker(String coin, Consumer<BookTickerPatch> handler) {
		subscribeFuturesBookTicker(Set.of(coin), handler);
	}

	void subscribeFuturesMarkPrice(Set<String> coins, Consumer<MarkPricePatch> handler);

	default void subscribeFuturesMarkPrice(String coin, Consumer<MarkPricePatch> handler) {
		subscribeFuturesMarkPrice(Set.of(coin), handler);
	}

	void subscribeSpotBookTicker(Set<String> coins, Consumer<BookTickerPatch> handler);

	default void subscribeSpotBookTicker(String coin, Consumer<BookTickerPatch> handler) {
		subscribeSpotBookTicker(Set.of(coin), handler);
	}

	void unsubscribeCoinsFutures(Set<String> coins);

	void unsubscribeCoinsSpot(Set<String> coins);
}
