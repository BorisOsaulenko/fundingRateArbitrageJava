package com.boris.fundingarbitrage.exchange.impl.okx.publicrest;

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

public class OkxPublicHttpClient extends PublicHttpClient {
	private final ObjectMapper mapper = ObjectMapperSingleton.getInstance();

	public OkxPublicHttpClient(ExchangeContext context) {
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
				Logger.error(String.format("Error parsing OKX public rest response: %s", e.getMessage()));
				throw new RuntimeException("Failed to process request", e);
			}
		});
	}

	@Override
	protected CompletableFuture<Double> getLotSizeSymbol(String symbol) {
		return processRequest(
						PublicEndpoints.instrumentsRequestSymbol(symbol),
						PublicResponses.InstrumentsResponse.class,
						(resp) -> resp.lotSizeSymbol(symbol)
		);
	}

	@Override
	protected CompletableFuture<BookTicker> getBookTickerSymbol(String symbol) {
		return processRequest(
						PublicEndpoints.tickerRequestSymbol(symbol),
						PublicResponses.TickerResponse.class,
						PublicResponses.TickerResponse::bookTicker
		);
	}

	@Override
	protected CompletableFuture<FundingRate> getFundingRateSymbol(String symbol) {
		return processRequest(
						PublicEndpoints.fundingRateRequestSymbol(symbol),
						PublicResponses.FundingRateResponse.class,
						PublicResponses.FundingRateResponse::fundingRate
		);
	}

	@Override
	protected CompletableFuture<Map<String, FundingRate>> getFundingRateSymbols(List<String> symbols) {
		return processRequest(
						PublicEndpoints.fundingRateRequestSymbols(),
						PublicResponses.FundingRatesSymbolsResponse.class,
						(resp) -> resp.getBySymbols(symbols)
		);
	}

	@Override
	protected CompletableFuture<Double> getTradingVolume24hSymbol(String symbol) {
		return processRequest(
						PublicEndpoints.tickerRequestSymbol(symbol),
						PublicResponses.TickerResponse.class,
						PublicResponses.TickerResponse::volume24h
		);
	}

	@Override
	protected CompletableFuture<Double> getTradingVolume1hSymbol(String symbol) {
		return processRequest(
						PublicEndpoints.candles1hRequestSymbol(symbol),
						PublicResponses.CandlesResponse.class,
						PublicResponses.CandlesResponse::volume1h
		);
	}

	@Override
	protected CompletableFuture<Boolean> checkExistsSymbol(String symbol) {
		var request = PublicEndpoints.instrumentsRequestSymbol(symbol);
		return client.sendNoCodeCheck(request).thenApply(res -> {
			try {
				var instrumentRes = mapper.readValue(res.getBodyText(), PublicResponses.InstrumentsResponse.class);
				return instrumentRes.code() != 51001; // OKX returns this code for non-existent instruments
			} catch (Exception e) {
				Logger.error(String.format(
								"Error parsing OKX instruments response for symbol existence check: %s",
								e.getMessage()
				));
				return false;
			}
		});
	}

	@Override
	protected CompletableFuture<Map<String, Boolean>> checkExistsSymbols(List<String> symbols) {
		return processRequest(
						PublicEndpoints.instrumentsRequestSymbols(),
						PublicResponses.InstrumentsSymbolsResponse.class,
						(resp) -> resp.existsBySymbols(symbols)
		);
	}

	@Override
	protected CompletableFuture<Map<String, Integer>> getFundingGranularityHoursSymbols(List<String> symbols) {
		return processRequest(
						PublicEndpoints.fundingGranularityRequestSymbols(),
						PublicResponses.FundingGranularityResponse.class,
						(resp) -> resp.get(symbols)
		);
	}
}
