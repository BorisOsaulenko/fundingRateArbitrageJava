package com.boris.fundingarbitrage.logic;

import com.boris.fundingarbitrage.exchange.BaseExchange;
import com.boris.fundingarbitrage.model.contract.Fees;
import com.boris.fundingarbitrage.monitor.ExchangeCoinMap;
import com.boris.fundingarbitrage.util.logger.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

class ArbitrageLogicUtils {
	public static CompletableFuture<ExchangeCoinMap<Fees>> fetchFeesMap(Map<BaseExchange, Set<String>> availableCoinsByExchange) {
		ExchangeCoinMap<Fees> result = new ExchangeCoinMap<>();
		List<CompletableFuture<Void>> futures = new ArrayList<>();

		for (Map.Entry<BaseExchange, Set<String>> entry : availableCoinsByExchange.entrySet()) {
			BaseExchange ex = entry.getKey();
			Set<String> coins = entry.getValue();

			CompletableFuture<Void> future = ex.privateHttpClient.getTradingFees(coins)
							.thenAccept(feesVector -> {
								feesVector.forEach((coin, fee) -> result.put(ex, coin, fee));
							})
							.exceptionally(t -> {
								Logger.error("Failed to fetch trading fees for " + ex.name + ": " + t.getMessage());
								throw new RuntimeException(t);
							});

			futures.add(future);
		}
		return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).thenApply(_ -> result);
	}
}
