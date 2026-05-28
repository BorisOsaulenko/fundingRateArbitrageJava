package com.boris.fundingarbitrage;

import com.boris.fundingarbitrage.exchange.BaseExchange;
import com.boris.fundingarbitrage.model.exchange.snapshot.FuturesSnapshot;
import com.boris.fundingarbitrage.model.exchange.snapshot.Snapshot;
import com.boris.fundingarbitrage.model.exchange.snapshot.SpotSnapshot;
import com.boris.fundingarbitrage.monitor.ICoinMonitor;
import com.boris.fundingarbitrage.strategy.TradeMarket;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

public class FakeCoinMonitor implements ICoinMonitor {
	private final Map<Key, FuturesSnapshot> futuresSnapshots = new java.util.HashMap<>();
	private final Map<Key, SpotSnapshot> spotSnapshots = new java.util.HashMap<>();
	private boolean shutdown;

	public void reset() {
		futuresSnapshots.clear();
		spotSnapshots.clear();
		shutdown = false;
	}

	public void setFuturesSnapshot(BaseExchange ex, String coin, FuturesSnapshot sn) {
		futuresSnapshots.put(new Key(ex, coin), sn);
	}

	public void setSpotSnapshot(BaseExchange ex, String coin, SpotSnapshot sn) {
		spotSnapshots.put(new Key(ex, coin), sn);
	}

	@Override
	public CompletableFuture<Void> getInitFuture() {
		return CompletableFuture.completedFuture(null);
	}

	@Override
	public void shutdown() {
		shutdown = true;
	}

	public void verifyShutdown() {
		if (!shutdown) throw new IllegalStateException("Expected monitor to be shutdown.");
	}

	public void verifyNotShutdown() {
		if (shutdown) throw new IllegalStateException("Expected monitor to not be shutdown.");
	}

	@Override
	public FuturesSnapshot getFuturesSnapshot(BaseExchange ex, String coin) {
		return futuresSnapshots.get(new Key(ex, coin));
	}

	@Override
	public SpotSnapshot getSpotSnapshot(BaseExchange ex, String coin) {
		return spotSnapshots.get(new Key(ex, coin));
	}

	@Override
	public Snapshot getSnapshot(BaseExchange ex, String coin, TradeMarket market) {
		return market == TradeMarket.FUTURES
						? getFuturesSnapshot(ex, coin)
						: getSpotSnapshot(ex, coin);
	}

	@Override
	public void performOnTimestamp(
					long timestamp,
					BaseExchange exchange,
					String coin,
					BiConsumer<FuturesSnapshot, SpotSnapshot> handler
	) {
		throw new UnsupportedOperationException("Not implemented in FakeCoinMonitor.");
	}

	@Override
	public void cancelTimestampExecution(BaseExchange ex, String coin, long timestamp) {
		throw new UnsupportedOperationException("Not implemented in FakeCoinMonitor.");
	}

	private record Key(BaseExchange exchange, String coin) {
	}
}
