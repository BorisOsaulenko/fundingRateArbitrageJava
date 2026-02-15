package com.boris.fundingarbitrage.exchange.publichttp;

import com.boris.fundingarbitrage.exchange.ExchangeContext;
import com.boris.fundingarbitrage.model.contract.FundingRate;
import com.boris.fundingarbitrage.util.coinvector.CoinVector;
import com.boris.fundingarbitrage.util.https.PrettyHttpClient;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public abstract class PublicHttpClient {
	protected final ExchangeContext exchangeContext;
	protected final PrettyHttpClient client;

	protected PublicHttpClient(ExchangeContext context, PrettyHttpClient client) {
		this.exchangeContext = context;
		this.client = client;
	}

	protected abstract CompletableFuture<Map<String, PublicOnePullData>> getPublicOnePullData();

	protected abstract CompletableFuture<Map<String, FundingRate>> getFundingRateSymbolBatch();

	private <T> CompletableFuture<CoinVector<T>> withSymbol(
					List<String> coins,
					Supplier<CompletableFuture<Map<String, T>>> symbolGetter
	) {
		return symbolGetter.get().thenApply(resultBySymbols -> {
			CoinVector<T> result = new CoinVector<>();
			for (String coin : coins) {
				String symbol = exchangeContext.getSymbol(coin);
				T value = resultBySymbols.get(symbol);
				result.put(coin, value);
			}
			return result;
		});
	}


	public CompletableFuture<CoinVector<FundingRate>> getFundingRate(List<String> coins) {
		return withSymbol(coins, this::getFundingRateSymbolBatch);
	}

	public CompletableFuture<CoinVector<PublicOnePullData>> getOnePullData(List<String> coins) {
		return withSymbol(coins, this::getPublicOnePullData);
	}
}
