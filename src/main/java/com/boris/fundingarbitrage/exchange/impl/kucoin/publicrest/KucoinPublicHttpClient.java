package com.boris.fundingarbitrage.exchange.impl.kucoin.publicrest;

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

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public class KucoinPublicHttpClient extends PublicHttpClient {
	private final ObjectMapper mapper = ObjectMapperSingleton.getInstance();

	public KucoinPublicHttpClient(ExchangeContext context) {
		super(context, PrettyHttpClient.getINSTANCE());
	}

	private <U> CompletableFuture<U> getResponse(SimpleHttpRequest req, Class<U> responseClass) {
		return this.client.send(req).thenApply((response) -> {
			try {
				return mapper.readValue(response.getBodyText(), responseClass);
			} catch (Exception e) {
				Logger.error(String.format("Error parsing KuCoin public rest response: %s", e.getMessage()));
				throw new RuntimeException("Failed to process KuCoin request", e);
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
						PublicEndpoints.activeContractsRequest(),
						PublicResponses.ActiveContractsResponse.class,
						PublicResponses.ActiveContractsResponse::getFundingRates
		);
	}

	@Override
	protected CompletableFuture<Map<String, PublicOnePullData>> getPublicOnePullData() {
		CompletableFuture<PublicResponses.ActiveContractsResponse> contractsResponseFuture = getResponse(
						PublicEndpoints.activeContractsRequest(),
						PublicResponses.ActiveContractsResponse.class
		);
		CompletableFuture<PublicResponses.AllTickersResponse> tickersResponseFuture = getResponse(
						PublicEndpoints.allTickersRequestSymbols(),
						PublicResponses.AllTickersResponse.class
		);

		return CompletableFuture.allOf(contractsResponseFuture, tickersResponseFuture).thenApply(_ -> {
			Map<String, Double> lotSizes = contractsResponseFuture.join().getLotSizes();
			Map<String, Integer> fundingGranularityHours = contractsResponseFuture.join().getFundingGranularityHours();
			Map<String, Double> volumes24h = contractsResponseFuture.join().getVolume24h();
			Map<String, BookTicker> bookTickers = tickersResponseFuture.join().getBookTickers();

			Map<String, PublicOnePullData> result = new HashMap<>();
			for (String symbol : lotSizes.keySet()) {
				BookTicker ticker = bookTickers.get(symbol);
				if (ticker == null) throw new RuntimeException("Book ticker missing for symbol: " + symbol);
				Integer granularity = fundingGranularityHours.get(symbol);
				if (granularity == null) throw new RuntimeException("Funding granularity missing for symbol: " + symbol);

				result.put(symbol, new PublicOnePullData(lotSizes.get(symbol), ticker, volumes24h.get(symbol), granularity));
			}
			return result;
		});
	}

	public CompletableFuture<URI> fetchPublicWsEndpoint() {
		return getResponse(PublicEndpoints.publicWsToken(), PublicResponses.PublicWsTokenResponse.class).thenApply(resp -> {
			String token = resp.token();
			String endpoint = resp.endpoint();
			String connectId = UUID.randomUUID().toString();
			return URI.create(endpoint + "?token=" + token + "&connectId=" + connectId);
		});
	}
}
