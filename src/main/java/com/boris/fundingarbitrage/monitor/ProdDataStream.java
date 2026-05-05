package com.boris.fundingarbitrage.monitor;

import com.boris.fundingarbitrage.coinfilter.CoinExchangeSupport;
import com.boris.fundingarbitrage.exchange.BaseExchange;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class ProdDataStream implements IDataStream {
	private final CoinExchangeSupport coinExchangeSupport;
	private final int timeoutSeconds = 60;
	private CompletableFuture<Void> timeoutFuture = null;

	public ProdDataStream(CoinExchangeSupport coinExchangeSupport) {
		this.coinExchangeSupport = coinExchangeSupport;
	}

	@Override
	public CompletableFuture<Void> initFuture() {
		return timeoutFuture;
	}

	public CompletableFuture<Void> openWsConnections() {
		this.timeoutFuture = CompletableFuture.runAsync(
						() -> {
						}, CompletableFuture.delayedExecutor(timeoutSeconds, TimeUnit.SECONDS)
		);

		List<CompletableFuture<Void>> futures = new ArrayList<>();
		for (BaseExchange exchange : coinExchangeSupport.getExchanges()) {
			futures.add(exchange.publicWsClient().connect());
		}

		return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
	}

	public void onSteadyData(Runnable onSteadyData) {
		if (timeoutFuture == null) throw new IllegalStateException("Data stream not started yet");
		timeoutFuture.thenRun(onSteadyData);
	}

	public void unsubscribeFutures(BaseExchange ex, Set<String> coins) {
		ex.publicWsClient().unsubscribeCoinsFutures(coins);
	}

	public void unsubscribeSpot(BaseExchange ex, Set<String> coins) {
		ex.publicWsClient().unsubscribeCoinsSpot(coins);
	}

	public void unsubscribeAll(BaseExchange ex, Set<String> coins) {
		ex.publicWsClient().unsubscribeCoinsFutures(coins);
		ex.publicWsClient().unsubscribeCoinsSpot(coins);
	}
}
