package com.boris.fundingarbitrage.exchange.impl.okx.publicrest;

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

public class OkxPublicHttpClient extends PublicHttpClient {
	private final ObjectMapper mapper = ObjectMapperSingleton.getInstance();

	public OkxPublicHttpClient(ExchangeContext context) {
		super(context, PrettyHttpClient.getINSTANCE());
	}

	private <U> CompletableFuture<U> getResponse(SimpleHttpRequest req, Class<U> responseClass) {
		return this.client.send(req).thenApply((response) -> {
			try {
				return mapper.readValue(response.getBodyText(), responseClass);
			} catch (Exception e) {
				Logger.error(String.format("Error parsing OKX public rest response: %s", e.getMessage()));
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
						PublicEndpoints.fundingRateRequestSymbols(),
						PublicResponses.FundingRatesResponse.class,
						PublicResponses.FundingRatesResponse::getFundingRates
		);
	}

	@Override
	protected CompletableFuture<Map<String, PublicOnePullData>> getPublicOnePullData() {
		CompletableFuture<PublicResponses.InstrumentsResponse> instrumentsResponseFuture = getResponse(
						PublicEndpoints.instrumentsRequestSymbols(),
						PublicResponses.InstrumentsResponse.class
		);
		CompletableFuture<PublicResponses.TickersResponse> tickersResponseFuture = getResponse(
						PublicEndpoints.tickersRequestSymbols(),
						PublicResponses.TickersResponse.class
		);
		CompletableFuture<PublicResponses.FundingRatesResponse> fundingResponseFuture = getResponse(
						PublicEndpoints.fundingRateRequestSymbols(),
						PublicResponses.FundingRatesResponse.class
		);

		return CompletableFuture
						.allOf(instrumentsResponseFuture, tickersResponseFuture, fundingResponseFuture)
						.thenApply(_ -> {
							Map<String, Double> lotSizes = instrumentsResponseFuture.join().getLotSizes();
							Map<String, BookTicker> bookTickers = tickersResponseFuture.join().getBookTickers();
							Map<String, Double> volumes24h = tickersResponseFuture.join().getVolume24h();
							Map<String, Integer> fundingGranularityHours = fundingResponseFuture.join().getFundingGranularityHours();

							Map<String, PublicOnePullData> data = new HashMap<>();
							for (String symbol : lotSizes.keySet()) {
								BookTicker ticker = bookTickers.get(symbol);
								if (ticker == null) throw new RuntimeException("Book ticker missing for symbol: " + symbol);
								Integer granularity = fundingGranularityHours.get(symbol);
								if (granularity == null) {
									throw new RuntimeException("Funding granularity missing for symbol: " + symbol);
								}

								data.put(
										symbol,
										new PublicOnePullData(lotSizes.get(symbol), ticker, volumes24h.get(symbol), granularity)
								);
							}
							return data;
						});
	}
}
