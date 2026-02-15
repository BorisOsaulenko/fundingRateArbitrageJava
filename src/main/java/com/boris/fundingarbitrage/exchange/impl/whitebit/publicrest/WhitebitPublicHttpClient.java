package com.boris.fundingarbitrage.exchange.impl.whitebit.publicrest;

import com.boris.fundingarbitrage.ObjectMapperSingleton;
import com.boris.fundingarbitrage.exchange.ExchangeContext;
import com.boris.fundingarbitrage.exchange.publichttp.PublicHttpClient;
import com.boris.fundingarbitrage.exchange.publichttp.PublicOnePullData;
import com.boris.fundingarbitrage.model.contract.BookTicker;
import com.boris.fundingarbitrage.model.contract.FundingRate;
import com.boris.fundingarbitrage.util.https.PrettyHttpClient;
import com.boris.fundingarbitrage.util.logger.Logger;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public class WhitebitPublicHttpClient extends PublicHttpClient {
	private final ObjectMapper mapper = ObjectMapperSingleton.getInstance();

	public WhitebitPublicHttpClient(ExchangeContext context) {
		super(context, PrettyHttpClient.getINSTANCE());
	}

	private <U> CompletableFuture<U> getResponse(SimpleHttpRequest req, Class<U> responseClass) {
		return this.client.send(req).thenApply((response) -> {
			try {
				return mapper.readValue(response.getBodyBytes(), responseClass);
			} catch (Exception e) {
				Logger.error(String.format("Error parsing public rest response: %s", e.getMessage()));
				throw new RuntimeException("Failed to process request", e);
			}
		});
	}

	private <T, U> CompletableFuture<U> processRequest(
					SimpleHttpRequest request,
					Class<T> responseClass,
					Function<T, U> parser
	) {
		return getResponse(request, responseClass).thenApply(parser);
	}

	@Override
	protected CompletableFuture<Map<String, FundingRate>> getFundingRateSymbolBatch() {
		return processRequest(
						PublicEndpoints.futuresRequest(),
						PublicResponses.FuturesResponse.class,
						PublicResponses.FuturesResponse::getFundingRates
		);
	}

	@Override
	protected CompletableFuture<Map<String, PublicOnePullData>> getPublicOnePullData() {
		CompletableFuture<PublicResponses.MarketsResponse> marketsResponseFuture = getResponse(
						PublicEndpoints.marketsRequest(),
						PublicResponses.MarketsResponse.class
		);
		CompletableFuture<PublicResponses.FuturesResponse> futuresResponseFuture = getResponse(
						PublicEndpoints.futuresRequest(),
						PublicResponses.FuturesResponse.class
		);

		return CompletableFuture.allOf(marketsResponseFuture, futuresResponseFuture).thenCompose(_ -> {
			Map<String, Double> lotSizes = marketsResponseFuture.join().getLotSizes();
			Map<String, Double> volumes24h = futuresResponseFuture.join().getVolume24h();
			Map<String, Integer> fundingGranularityHours = futuresResponseFuture.join().getFundingGranularityHours();

			Map<String, CompletableFuture<BookTicker>> tickerFutures = new HashMap<>();
			for (String symbol : lotSizes.keySet()) {
				tickerFutures.put(
								symbol, processRequest(
												PublicEndpoints.orderBookRequestSymbol(symbol),
												PublicResponses.OrderBookResponse.class,
												PublicResponses.OrderBookResponse::bookTicker
								)
				);
			}

			CompletableFuture<?>[] allTickerFutures = tickerFutures.values().toArray(new CompletableFuture[0]);
			return CompletableFuture.allOf(allTickerFutures).thenApply(__ -> {
				Map<String, PublicOnePullData> result = new HashMap<>();
				for (String symbol : lotSizes.keySet()) {
					BookTicker ticker = tickerFutures.get(symbol).join();
					Integer granularity = fundingGranularityHours.get(symbol);
					if (granularity == null) {
						throw new RuntimeException("Funding granularity missing for symbol: " + symbol);
					}
					result.put(symbol, new PublicOnePullData(lotSizes.get(symbol), ticker, volumes24h.get(symbol), granularity));
				}
				return result;
			});
		});
	}
}