package com.boris.fundingarbitrage.coinparser;

import com.boris.fundingarbitrage.exchange.BaseExchange;
import com.boris.fundingarbitrage.exchange.Instances;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class AllExchangeCoinsParser implements ICoinSupplier {
	private static final Logger log = LoggerFactory.getLogger(AllExchangeCoinsParser.class);

	@Override
	public CompletableFuture<Set<String>> getCoinsAsync() {
		Set<String> allCoins = ConcurrentHashMap.newKeySet();
		List<CompletableFuture<Void>> futures = new ArrayList<>();

		for (BaseExchange exchange : Instances.getExchangeArray()) {
			futures.add(exchange.publicHttpClient().getAvailableCoins()
							.exceptionally(t -> {
								log.error("Failed to fetch coins for {}: {}", exchange.name(), t.getMessage());
								return null;
							})
							.thenAccept(allCoins::addAll));
		}

		return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).thenApply(v -> allCoins);
	}
}
