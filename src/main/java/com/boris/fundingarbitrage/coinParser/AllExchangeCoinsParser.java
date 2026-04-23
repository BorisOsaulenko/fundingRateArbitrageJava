package com.boris.fundingarbitrage.coinParser;

import com.boris.fundingarbitrage.exchange.BaseExchange;
import com.boris.fundingarbitrage.exchange.Instances;
import com.boris.fundingarbitrage.util.logger.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class AllExchangeCoinsParser implements ICoinSupplier {
	@Override
	public CompletableFuture<Set<String>> getCoinsAsync() {
		Set<String> allCoins = ConcurrentHashMap.newKeySet();
		List<CompletableFuture<Void>> futures = new ArrayList<>();

		for (BaseExchange exchange : Instances.getExchangeArray()) {
			futures.add(exchange.publicHttpClient().getAvailableCoins()
							.exceptionally(t -> {
								Logger.error("Failed to fetch coins for " + exchange.name() + ": " + t.getMessage());
								return null;
							})
							.thenAccept(allCoins::addAll));
		}

		return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).thenApply(v -> allCoins);
	}
}
