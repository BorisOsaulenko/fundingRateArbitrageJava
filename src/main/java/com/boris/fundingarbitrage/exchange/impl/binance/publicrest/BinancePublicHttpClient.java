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
				Logger
								.getInstance()
								.error(String.format("Error parsing public rest response: %s", e.getMessage()));
				throw new RuntimeException("Failed to process request", e);
			}
		});
	}

	@Override
	public CompletableFuture<Double> getLotSizeSymbol(String symbol) {
		return processRequest(
						PublicEndpoints.lotSizeRequest(symbol),
						PublicResponses.LotSizeResponse.class,
						(resp) -> resp.get(symbol)
		);
	}

	@Override
	public CompletableFuture<Double> getTradingVolume24hSymbol(String symbol) {
		return processRequest(
						PublicEndpoints.tradingVolume24hRequest(symbol),
						PublicResponses.TradingVolume24hResponse.class,
						PublicResponses.TradingVolume24hResponse::get
		);
	}

	@Override
	public CompletableFuture<Double> getTradingVolume1hSymbol(String symbol) {
		return processRequest(
						PublicEndpoints.tradingVolume1hRequest(symbol),
						PublicResponses.TradingVolume1hResponse.class,
						PublicResponses.TradingVolume1hResponse::get
		);
	}

	@Override
	public CompletableFuture<BookTicker> getBookTickerSymbol(String symbol) {
		return processRequest(
						PublicEndpoints.bookTickerRequest(symbol),
						PublicResponses.BookTickerResponse.class,
						PublicResponses.BookTickerResponse::get
		);
	}

	@Override
	public CompletableFuture<FundingRate> getFundingRateSymbol(String symbol) {
		return processRequest(
						PublicEndpoints.fundingRateRequest(symbol),
						PublicResponses.FundingRateResponse.class,
						PublicResponses.FundingRateResponse::get
		);
	}

	@Override
	public CompletableFuture<Boolean> checkExistsSymbol(String symbol) {
		return processRequest(
						PublicEndpoints.checkSymbolExistsRequest(symbol),
						PublicResponses.CheckSymbolExistsResponse.class,
						(resp) -> resp.get(symbol)
		);
	}
}
