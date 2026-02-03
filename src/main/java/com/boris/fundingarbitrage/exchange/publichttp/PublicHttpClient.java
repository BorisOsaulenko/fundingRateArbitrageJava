package com.boris.fundingarbitrage.exchange.publichttp;

import com.boris.fundingarbitrage.exchange.ExchangeContext;
import com.boris.fundingarbitrage.model.contract.BookTicker;
import com.boris.fundingarbitrage.model.contract.FundingRate;
import com.boris.fundingarbitrage.util.https.PrettyHttpClient;

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

	protected abstract CompletableFuture<Double> getTradingVolume24hSymbol(String symbol);

	protected abstract CompletableFuture<Double> getTradingVolume1hSymbol(String symbol);

	protected abstract CompletableFuture<Boolean> checkExistsSymbol(String symbol);

	private <T> CompletableFuture<T> withSymbol(
					String coin,
					Function<String, CompletableFuture<T>> symbolGetter
	) {
		String symbol = exchangeContext.getSymbol(coin);
		return symbolGetter.apply(symbol);
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

	public CompletableFuture<Double> getTradingVolume24h(String coin) {
		return withSymbol(coin, this::getTradingVolume24hSymbol);
	}

	public CompletableFuture<Double> getTradingVolume1h(String coin) {
		return withSymbol(coin, this::getTradingVolume1hSymbol);
	}

	public CompletableFuture<Boolean> checkSymbolExists(String coin) {
		return withSymbol(coin, this::checkExistsSymbol);
	}
}
