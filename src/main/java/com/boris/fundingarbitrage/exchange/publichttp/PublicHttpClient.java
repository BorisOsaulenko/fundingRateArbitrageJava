package com.boris.fundingarbitrage.exchange.publichttp;

import com.boris.fundingarbitrage.exchange.ExchangeContext;
import com.boris.fundingarbitrage.model.contract.FundingRate;
import com.boris.fundingarbitrage.util.coinvector.CoinVector;
import com.boris.fundingarbitrage.util.https.PrettyHttpClient;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;

public abstract class PublicHttpClient {
	protected final ExchangeContext exchangeContext;
	protected final PrettyHttpClient client;

	protected PublicHttpClient(ExchangeContext context, PrettyHttpClient client) {
		this.exchangeContext = context;
		this.client = client;
	}

	protected abstract CompletableFuture<Map<String, FuturesPublicOnePullData>> getFuturesPublicOnePullDataSymbols();

	protected abstract CompletableFuture<Map<String, SpotPublicOnePullData>> getSpotPublicOnePullDataSymbols();

	protected abstract CompletableFuture<Map<String, FundingRate>> getFundingRateSymbols();

	private <T> CompletableFuture<CoinVector<T>> withSymbol(
					Set<String> coins,
					Supplier<CompletableFuture<Map<String, T>>> symbolGetter,
					Function<String, String> getSymbol
	) {
		return symbolGetter.get().thenApply(resultBySymbols -> {
			CoinVector<T> result = new CoinVector<>();
			for (String coin : coins) {
				String symbol = getSymbol.apply(coin);
				T value = resultBySymbols.get(symbol);
				if (value == null) continue;
				result.put(coin, value);
			}
			return result;
		});
	}

	public CompletableFuture<CoinVector<FundingRate>> getFundingRate(Set<String> coins) {
		return withSymbol(coins, this::getFundingRateSymbols, exchangeContext::getFuturesSymbol);
	}

	public CompletableFuture<CoinVector<FuturesPublicOnePullData>> getFuturesOnePullData(Set<String> coins) {
		return withSymbol(coins, this::getFuturesPublicOnePullDataSymbols, exchangeContext::getFuturesSymbol);
	}

	public CompletableFuture<CoinVector<SpotPublicOnePullData>> getSpotOnePullData(Set<String> coins) {
		return withSymbol(coins, this::getSpotPublicOnePullDataSymbols, exchangeContext::getSpotSymbol);
	}

	public abstract CompletableFuture<Set<String>> getAvailableCoins();
}
