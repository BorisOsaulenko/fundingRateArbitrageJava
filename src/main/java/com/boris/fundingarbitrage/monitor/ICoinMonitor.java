package com.boris.fundingarbitrage.monitor;

import com.boris.fundingarbitrage.exchange.BaseExchange;
import com.boris.fundingarbitrage.model.exchange.snapshot.FuturesSnapshot;
import com.boris.fundingarbitrage.model.exchange.snapshot.Snapshot;
import com.boris.fundingarbitrage.model.exchange.snapshot.SpotSnapshot;
import com.boris.fundingarbitrage.strategy.TradeMarket;

import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

public interface ICoinMonitor {
	CompletableFuture<Void> getInitFuture();

	void shutdown();

	FuturesSnapshot getFuturesSnapshot(BaseExchange ex, String coin);

	SpotSnapshot getSpotSnapshot(BaseExchange ex, String coin);

	Snapshot getSnapshot(BaseExchange ex, String coin, TradeMarket market);

	void performOnTimestamp(
					long timestamp,
					BaseExchange exchange,
					String coin,
					BiConsumer<FuturesSnapshot, SpotSnapshot> handler
	);

	void cancelTimestampExecution(BaseExchange ex, String coin, long timestamp);
}
