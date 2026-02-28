package com.boris.fundingarbitrage.execution;

import com.boris.fundingarbitrage.exchange.BaseExchange;
import com.boris.fundingarbitrage.exchange.Instances;
import com.boris.fundingarbitrage.execution.withdrawer.OptimalWithdrawer;
import com.boris.fundingarbitrage.model.exchange.ExchangeChains;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class Execution {
	private final BaseExchange longExchange;
	private final BaseExchange shortExchange;
	private final double amount;

	private final Map<BaseExchange, Double> spotBalances = new ConcurrentHashMap<>();
	private final Map<BaseExchange, Double> futuresBalances = new ConcurrentHashMap<>();
	private final Map<BaseExchange, ExchangeChains> exchangeChains = new ConcurrentHashMap<>();

	private final CompletableFuture<Void> initFuture;

	public Execution(BaseExchange longExchange, BaseExchange shortExchange, double amount) {
		this.longExchange = longExchange;
		this.shortExchange = shortExchange;
		this.amount = amount;

		this.initFuture = CompletableFuture.allOf(fillBalancesMap(), fillChainsMap());
	}

	private CompletableFuture<Void> fillBalancesMap() {
		List<CompletableFuture<Void>> futures = new ArrayList<>();

		for (BaseExchange exchange : Instances.getExchangeArray()) {
			CompletableFuture<Void> spotBalanceFuture = exchange.privateHttpClient.getSpotUsdtBalance()
							.thenAccept(spotB -> spotBalances.put(exchange, spotB));
			CompletableFuture<Void> futuresBalanceFuture = exchange.privateHttpClient.getFuturesUsdtBalance()
							.thenAccept(futuresB -> futuresBalances.put(exchange, futuresB));


			futures.add(spotBalanceFuture);
			futures.add(futuresBalanceFuture);
		}

		return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
	}

	private CompletableFuture<Void> fillChainsMap() {
		List<CompletableFuture<Void>> futures = new ArrayList<>();

		for (BaseExchange exchange : Instances.getExchangeArray()) {
			CompletableFuture<Void> chainsFuture = exchange.privateHttpClient.getSupportedChains()
							.thenAccept(chains -> exchangeChains.put(exchange, chains));
			futures.add(chainsFuture);
		}

		return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
	}

	public CompletableFuture<Void> init() {
		return initFuture;
	}

	public CompletableFuture<Void> withdrawToExchanges() {
		if (!initFuture.isDone()) throw new IllegalCallerException("Execution must be initialized before withdrawal");
		OptimalWithdrawer withdrawer = new OptimalWithdrawer(
						longExchange,
						shortExchange,
						amount,
						spotBalances,
						futuresBalances,
						exchangeChains
		);

		CompletableFuture<Void> future = withdrawer.withdrawUsdtToExchanges();
		if (future == null)
			throw new RuntimeException("No withdrawal path found. Probably not enough usdt on spot of other exchanges");
		return future;
	}
}
