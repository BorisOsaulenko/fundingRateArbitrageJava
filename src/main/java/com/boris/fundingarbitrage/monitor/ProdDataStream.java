package com.boris.fundingarbitrage.monitor;

import com.boris.fundingarbitrage.exchange.BaseExchange;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class ProdDataStream implements IDataStream {
	private CompletableFuture<Void> timeoutFuture = null;

	public ProdDataStream() {
	}

	@Override
	public CompletableFuture<Void> initFuture() {
		return timeoutFuture;
	}

	public CompletableFuture<Void> openWsConnections(Set<BaseExchange> exchanges) {
		int timeoutSeconds = 60;
		this.timeoutFuture = CompletableFuture.runAsync(
						() -> {
						}, CompletableFuture.delayedExecutor(timeoutSeconds, TimeUnit.SECONDS)
		);

		List<CompletableFuture<Void>> futures = new ArrayList<>();
		for (BaseExchange exchange : exchanges) {
			futures.add(exchange.publicWsClient().connect());
		}

		return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
	}

	public void onSteadyData(Runnable onSteadyData) {
		if (timeoutFuture == null) throw new IllegalStateException("Data stream not started yet");
		timeoutFuture.thenRun(onSteadyData);
	}
}
