package com.boris.fundingarbitrage.logic;

import com.boris.fundingarbitrage.exchange.BaseExchange;
import com.boris.fundingarbitrage.model.exchange.ExchangeBalance;
import com.boris.fundingarbitrage.util.logger.Logger;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class ProdBalanceProvider implements BalanceProvider {
	@Override
	public CompletableFuture<Map<BaseExchange, ExchangeBalance>> load(Set<BaseExchange> exchanges) {
		Map<BaseExchange, ExchangeBalance> balances = new HashMap<>();
		List<CompletableFuture<Void>> futures = new ArrayList<>();

		for (BaseExchange exchange : exchanges) {
			CompletableFuture<BigDecimal> spotBalanceFuture = exchange.privateHttpClient().getSpotUsdtBalance()
							.exceptionally(t -> {
								Logger.error("Failed to fetch spot balance for " + exchange.name() + ": " + t.getMessage());
								throw new RuntimeException(t);
							});
			CompletableFuture<BigDecimal> futuresBalanceFuture = exchange.privateHttpClient().getFuturesUsdtBalance()
							.exceptionally(t -> {
								Logger.error("Failed to fetch futures balance for " + exchange.name() + ": " + t.getMessage());
								throw new RuntimeException(t);
							});

			futures.add(CompletableFuture.allOf(spotBalanceFuture, futuresBalanceFuture)
							.thenAccept(_ -> {
								BigDecimal spotBalance = spotBalanceFuture.join();
								BigDecimal futuresBalance = futuresBalanceFuture.join();
								balances.put(exchange, new ExchangeBalance(spotBalance, futuresBalance));
							})
			);
		}

		return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
						.thenApply(_ -> balances)
						.exceptionally(t -> {
							Logger.error("Error while loading balances: " + t.getMessage());
							throw new RuntimeException(t);
						});
	}
}
