package com.boris.fundingarbitrage.exchange.impl.whitebit.publicrest;

import com.boris.fundingarbitrage.ObjectMapperSingleton;
import com.boris.fundingarbitrage.exchange.ExchangeContext;
import com.boris.fundingarbitrage.exchange.publichttp.PublicHttpClient;
import com.boris.fundingarbitrage.model.contract.BookTicker;
import com.boris.fundingarbitrage.model.contract.FundingRate;
import com.boris.fundingarbitrage.util.https.PrettyHttpClient;
import com.boris.fundingarbitrage.util.logger.Logger;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public class WhitebitPublicHttpClient extends PublicHttpClient {
	private final ObjectMapper mapper = ObjectMapperSingleton.getInstance();

	public WhitebitPublicHttpClient(ExchangeContext context) {
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
	protected CompletableFuture<Double> getLotSizeSymbol(String symbol) {
		return processRequest(
						PublicEndpoints.marketsRequest(),
						PublicResponses.MarketsResponse.class,
						(resp) -> resp.lotSizeSymbol(symbol)
		);
	}

	@Override
	protected CompletableFuture<BookTicker> getBookTickerSymbol(String symbol) {
		return processRequest(
						PublicEndpoints.orderBookRequestSymbol(symbol),
						PublicResponses.OrderBookResponse.class,
						PublicResponses.OrderBookResponse::bookTicker
		);
	}

	@Override
	protected CompletableFuture<FundingRate> getFundingRateSymbol(String symbol) {
		return processRequest(
						PublicEndpoints.futuresRequest(),
						PublicResponses.FuturesResponse.class,
						(resp) -> new FundingRate(resp.fundingRate(symbol), resp.nextFundingTime(symbol), Instant.now())
		);
	}

	@Override
	protected CompletableFuture<Map<String, FundingRate>> getFundingRateSymbolBatch(List<String> symbols) {
		return processRequest(
						PublicEndpoints.futuresRequest(),
						PublicResponses.FundingRatesResponseSymbols.class,
						(resp) -> resp.get(symbols)
		);
	}

	@Override
	protected CompletableFuture<Double> getTradingVolume24hSymbol(String symbol) {
		return processRequest(
						PublicEndpoints.futuresRequest(),
						PublicResponses.FuturesResponse.class,
						(resp) -> resp.volume24hMoney(symbol)
		);
	}

	@Override
	protected CompletableFuture<Double> getTradingVolume1hSymbol(String symbol) {
		return processRequest(
						PublicEndpoints.recentTradesRequestSymbol(symbol),
						PublicResponses.RecentTradesResponse.class,
						PublicResponses.RecentTradesResponse::volume1hQuote
		);
	}

	@Override
	protected CompletableFuture<Boolean> checkExistsSymbol(String symbol) {
		return processRequest(
						PublicEndpoints.marketsRequest(),
						PublicResponses.MarketsResponse.class,
						(resp) -> resp.symbolExists(symbol)
		);
	}

	@Override
	protected CompletableFuture<Map<String, Boolean>> getExistingSymbols(List<String> symbols) {
		return processRequest(
						PublicEndpoints.marketsRequest(),
						PublicResponses.SymbolsExistsResponse.class,
						(resp) -> resp.get(symbols)
		);
	}

	@Override
	protected CompletableFuture<Map<String, Integer>> getFundingGranularityHoursSymbolBatch(List<String> symbols) {
		return processRequest(
						PublicEndpoints.fundingGranularityRequestSymbols(),
						PublicResponses.FundingGranularityResponse.class,
						(resp) -> resp.get(symbols)
		);
	}
}
