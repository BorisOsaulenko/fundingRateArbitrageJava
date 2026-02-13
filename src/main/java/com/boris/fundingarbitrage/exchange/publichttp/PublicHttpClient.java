package com.boris.fundingarbitrage.exchange.publichttp;

import com.boris.fundingarbitrage.exchange.ExchangeContext;
import com.boris.fundingarbitrage.model.contract.BookTicker;
import com.boris.fundingarbitrage.model.contract.FundingRate;
import com.boris.fundingarbitrage.util.coinvector.CoinVector;
import com.boris.fundingarbitrage.util.https.PrettyHttpClient;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public abstract class PublicHttpClient {
	protected final ExchangeContext exchangeContext;
	protected final PrettyHttpClient client;

	protected PublicHttpClient(ExchangeContext context, PrettyHttpClient client) {
		this.exchangeContext = context;
		this.client = client;
	}

	protected abstract CompletableFuture<Double> getLotSizeSymbol(String symbol);

	protected abstract CompletableFuture<BookTicker> getBookTickerSymbol(String symbol);

	protected abstract CompletableFuture<FundingRate> getFundingRateSymbol(String symbol);

	protected abstract CompletableFuture<Map<String, FundingRate>> getFundingRateSymbols(List<String> symbols);

	protected abstract CompletableFuture<Double> getTradingVolume24hSymbol(String symbol);

	protected abstract CompletableFuture<Double> getTradingVolume1hSymbol(String symbol);

	protected abstract CompletableFuture<Boolean> checkExistsSymbol(String symbol);

	protected abstract CompletableFuture<Map<String, Boolean>> checkExistsSymbols(List<String> symbols);

	private <T> CompletableFuture<T> withSymbol(String coin, Function<String, CompletableFuture<T>> symbolGetter) {
		String symbol = exchangeContext.getSymbol(coin);
		return symbolGetter.apply(symbol);
	}

	private <T> CompletableFuture<Map<String, T>> withSymbol(
					List<String> coins,
					Function<List<String>, CompletableFuture<Map<String, T>>> symbolGetter
	) {
		List<String> symbols = coins.stream().map(exchangeContext::getSymbol).toList();
		return symbolGetter.apply(symbols);
	}

	public CompletableFuture<Double> getLotSize(String coin) {
		return withSymbol(coin, this::getLotSizeSymbol);
	}

	public CompletableFuture<BookTicker> getBookTicker(String coin) {
		return withSymbol(coin, this::getBookTickerSymbol);
	}

	public CompletableFuture<FundingRate> getFundingRate(String coin) {
		return withSymbol(coin, this::getFundingRateSymbol);
	}

	public CompletableFuture<CoinVector<FundingRate>> getFundingRate(List<String> coins) {
		return withSymbol(coins, this::getFundingRateSymbols).thenApply(resultBySymbols -> {
			CoinVector<FundingRate> result = new CoinVector<>();
			for (String coin : coins) {
				String symbol = exchangeContext.getSymbol(coin);
				FundingRate fr = resultBySymbols.get(symbol);
				result.put(coin, fr);
			}
			return result;
		});
	}

	public CompletableFuture<Double> getTradingVolume24h(String coin) {
		return withSymbol(coin, this::getTradingVolume24hSymbol);
	}

	public CompletableFuture<Double> getTradingVolume1h(String coin) {
		return withSymbol(coin, this::getTradingVolume1hSymbol);
	}

	public CompletableFuture<Boolean> checkCoinExists(String coin) {
		return withSymbol(coin, this::checkExistsSymbol);
	}

	public CompletableFuture<CoinVector<Boolean>> checkCoinsExist(List<String> coins) {
		return withSymbol(coins, this::checkExistsSymbols).thenApply(resultBySymbols -> {
			CoinVector<Boolean> result = new CoinVector<>();
			for (String coin : coins) {
				String symbol = exchangeContext.getSymbol(coin);
				Boolean exists = resultBySymbols.get(symbol);
				result.put(coin, exists);
			}
			return result;
		});
	}
}
