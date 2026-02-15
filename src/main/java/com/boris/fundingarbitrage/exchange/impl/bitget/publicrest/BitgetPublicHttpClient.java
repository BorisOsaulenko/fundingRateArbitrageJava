package com.boris.fundingarbitrage.exchange.impl.bitget.publicrest;

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

public class BitgetPublicHttpClient extends PublicHttpClient {
	private final ObjectMapper mapper = ObjectMapperSingleton.getInstance();

	public BitgetPublicHttpClient(ExchangeContext context) {
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
						PublicEndpoints.currentFundingRateRequest(),
						PublicResponses.CurrentFundingRateResponse.class,
						PublicResponses.CurrentFundingRateResponse::getFundingRates
		);
	}

	@Override
	protected CompletableFuture<Map<String, PublicOnePullData>> getPublicOnePullData() {
		CompletableFuture<Map<String, Double>> lotSizesFuture = processRequest(
						PublicEndpoints.contractConfigRequest(),
						PublicResponses.ContractsResponse.class,
						PublicResponses.ContractsResponse::getLotSizes
		);
		CompletableFuture<Map<String, Integer>> fundingGranularityFuture = processRequest(
						PublicEndpoints.currentFundingRateRequest(),
						PublicResponses.CurrentFundingRateResponse.class,
						PublicResponses.CurrentFundingRateResponse::getFundingGranularity
		);
		CompletableFuture<PublicResponses.TickerResponse> tickersResponse = getResponse(
						PublicEndpoints.tickersRequest(),
						PublicResponses.TickerResponse.class
		);

		return CompletableFuture.allOf(lotSizesFuture, fundingGranularityFuture, tickersResponse).thenApply(_ -> {
			Map<String, Double> volume24h = tickersResponse.join().getUsdtVolumes();
			Map<String, BookTicker> bookTickers = tickersResponse.join().getBookTickers();

			Map<String, PublicOnePullData> data = new HashMap<>();
			for (String symbol : lotSizesFuture.join().keySet()) {
				BookTicker ticker = bookTickers.get(symbol);
				if (ticker == null) throw new RuntimeException("Book ticker missing for symbol: " + symbol);
				data.put(
								symbol, new PublicOnePullData(
												lotSizesFuture.join().get(symbol),
												bookTickers.get(symbol),
												volume24h.get(symbol),
												fundingGranularityFuture.join().get(symbol)
								)
				);
			}
			return data;
		});
	}
}