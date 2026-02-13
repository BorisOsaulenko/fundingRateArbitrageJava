package com.boris.fundingarbitrage.exchange.impl.binance.publicrest;

import com.boris.fundingarbitrage.ObjectMapperSingleton;
import com.boris.fundingarbitrage.exchange.ExchangeContext;
import com.boris.fundingarbitrage.exchange.publichttp.PublicHttpClient;
import com.boris.fundingarbitrage.model.contract.BookTicker;
import com.boris.fundingarbitrage.model.contract.FundingRate;
import com.boris.fundingarbitrage.util.https.PrettyHttpClient;
import com.boris.fundingarbitrage.util.logger.Logger;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public class BinancePublicHttpClient extends PublicHttpClient {
	private final ObjectMapper mapper = ObjectMapperSingleton.getInstance();

	public BinancePublicHttpClient(ExchangeContext context) {
		super(context, PrettyHttpClient.getINSTANCE());
	}

	private <T, U> CompletableFuture<U> processRequest(
					SimpleHttpRequest request,
					Class<T> responseClass,
					Function<T, U> parser
	) {
		return this.client.send(request).thenApply((response) -> {
			try {
				T responseObj = mapper.readValue(response.getBodyText(), responseClass);
				return parser.apply(responseObj);
			} catch (Exception e) {
				Logger.error(String.format("Error parsing public rest response: %s", e.getMessage()));
				throw new RuntimeException("Failed to process request", e);
			}
		});
	}

	@Override
	public CompletableFuture<Double> getLotSizeSymbol(String symbol) {
		return processRequest(
						PublicEndpoints.lotSizeRequestSymbol(symbol),
						PublicResponses.LotSizeResponseSymbol.class,
						(resp) -> resp.get(symbol)
		);
	}

	@Override
	public CompletableFuture<Double> getTradingVolume24hSymbol(String symbol) {
		return processRequest(
						PublicEndpoints.tradingVolume24hRequestSymbol(symbol),
						PublicResponses.TradingVolume24hResponseSymbol.class,
						PublicResponses.TradingVolume24hResponseSymbol::get
		);
	}

	@Override
	public CompletableFuture<Double> getTradingVolume1hSymbol(String symbol) {
		return processRequest(
						PublicEndpoints.tradingVolume1hRequestSymbol(symbol),
						PublicResponses.TradingVolume1hResponse.class,
						PublicResponses.TradingVolume1hResponse::get
		);
	}

	@Override
	public CompletableFuture<BookTicker> getBookTickerSymbol(String symbol) {
		return processRequest(
						PublicEndpoints.bookTickerRequestSymbol(symbol),
						PublicResponses.BookTickerResponseSymbol.class,
						PublicResponses.BookTickerResponseSymbol::get
		);
	}

	@Override
	public CompletableFuture<FundingRate> getFundingRateSymbol(String symbol) {
		return processRequest(
						PublicEndpoints.fundingRateRequestSymbol(symbol),
						PublicResponses.FundingRateResponseSymbol.class,
						PublicResponses.FundingRateResponseSymbol::get
		);
	}

	@Override
	protected CompletableFuture<Map<String, FundingRate>> getFundingRateSymbols(List<String> symbols) {
		return processRequest(
						PublicEndpoints.fundingRateRequestSymbols(),
						PublicResponses.FundingRateResponseSymbols.class,
						(resp) -> resp.get(symbols)
		);
	}

	@Override
	public CompletableFuture<Boolean> checkExistsSymbol(String symbol) {
		return processRequest(
						PublicEndpoints.checkSymbolExistsRequestSymbol(symbol),
						PublicResponses.CheckExistsSymbolResponse.class,
						(resp) -> resp.get(symbol)
		);
	}

	@Override
	protected CompletableFuture<Map<String, Boolean>> checkExistsSymbols(List<String> symbols) {
		return processRequest(
						PublicEndpoints.checkSymbolExistsRequestSymbols(),
						PublicResponses.CheckExistsSymbolsResponse.class,
						(resp) -> resp.get(symbols)
		);
	}
}
