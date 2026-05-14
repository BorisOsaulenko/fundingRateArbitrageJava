package com.boris.fundingarbitrage.monitor;

import com.boris.fundingarbitrage.exchange.BaseExchange;
import com.boris.fundingarbitrage.model.websocket.patch.BookTickerPatch;
import com.boris.fundingarbitrage.model.websocket.patch.FundingRatePatch;
import com.boris.fundingarbitrage.model.websocket.patch.MarkPricePatch;
import lombok.NonNull;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public interface IDataStream {
	@NonNull
	CompletableFuture<Void> initFuture();

	@NonNull
	CompletableFuture<Void> openWsConnections(Set<BaseExchange> exchanges);

	void onSteadyData(Runnable onSteadyData);

	default void subscribeFuturesBookTicker(BaseExchange ex, Set<String> coins, Consumer<BookTickerPatch> handler) {
		ex.publicWsClient().subscribeFuturesBookTicker(coins, handler);
	}

	default void subscribeFuturesFundingRates(BaseExchange ex, Set<String> coins, Consumer<FundingRatePatch> handler) {
		ex.publicWsClient().subscribeFuturesFundingRates(coins, handler);
	}

	default void subscribeFuturesMarkPrice(BaseExchange ex, Set<String> coins, Consumer<MarkPricePatch> handler) {
		ex.publicWsClient().subscribeFuturesMarkPrice(coins, handler);
	}

	default void subscribeSpotBookTicker(BaseExchange ex, Set<String> coins, Consumer<BookTickerPatch> handler) {
		ex.publicWsClient().subscribeSpotBookTicker(coins, handler);
	}

	default void unsubscribeFutures(BaseExchange ex, Set<String> coins) {
		ex.publicWsClient().unsubscribeCoinsFutures(coins);
	}

	default void unsubscribeSpot(BaseExchange ex, Set<String> coins) {
		ex.publicWsClient().unsubscribeCoinsSpot(coins);
	}

	default void unsubscribeAll(BaseExchange ex, Set<String> coins) {
		unsubscribeFutures(ex, coins);
		unsubscribeSpot(ex, coins);
	}
}