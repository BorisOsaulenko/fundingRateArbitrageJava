package com.boris.fundingarbitrage.exchange.impl.kucoin.publicrest;

import com.boris.fundingarbitrage.exchange.ExchangeContext;
import com.boris.fundingarbitrage.exchange.publichttp.PublicHttpClient;
import com.boris.fundingarbitrage.exchange.publichttp.PublicOnePullData;
import com.boris.fundingarbitrage.model.contract.BookTicker;
import com.boris.fundingarbitrage.model.contract.FundingRate;
import com.boris.fundingarbitrage.util.https.PrettyHttpClient;
import com.boris.fundingarbitrage.util.https.RequestProcessingClientWrapper;

import java.math.BigDecimal;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class KucoinPublicHttpClient extends PublicHttpClient {
	private final RequestProcessingClientWrapper requestWrapper = new RequestProcessingClientWrapper(this.client);

	public KucoinPublicHttpClient(ExchangeContext context) {
		super(context, PrettyHttpClient.getINSTANCE());
	}

	@Override
	protected CompletableFuture<Map<String, FundingRate>> getFundingRateSymbolBatch() {
		return requestWrapper.processRequest(
						PublicEndpoints.activeContractsRequest(),
						PublicResponses.ActiveContractsResponse.class,
						PublicResponses.ActiveContractsResponse::getFundingRates
		);
	}

	@Override
	protected CompletableFuture<Map<String, PublicOnePullData>> getPublicOnePullData() {
		CompletableFuture<PublicResponses.ActiveContractsResponse>
						contractsResponseFuture =
						requestWrapper.getResponse(
										PublicEndpoints.activeContractsRequest(),
										PublicResponses.ActiveContractsResponse.class
						);
		CompletableFuture<PublicResponses.AllTickersResponse>
						tickersResponseFuture =
						requestWrapper.getResponse(
										PublicEndpoints.allTickersRequestSymbols(),
										PublicResponses.AllTickersResponse.class
						);

		return CompletableFuture.allOf(contractsResponseFuture, tickersResponseFuture).thenApply(_ -> {
			Map<String, BigDecimal> lotSizes = contractsResponseFuture.join().getLotSizes();
			Map<String, Integer> fundingGranularityHours = contractsResponseFuture.join().getFundingGranularityHours();
			Map<String, BigDecimal> volumes24h = contractsResponseFuture.join().getVolume24h();
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
		return requestWrapper.getResponse(PublicEndpoints.publicWsToken(), PublicResponses.PublicWsTokenResponse.class)
						.thenApply(resp -> {
							String token = resp.token();
							String endpoint = resp.endpoint();
							String connectId = UUID.randomUUID().toString();
							return URI.create(endpoint + "?token=" + token + "&connectId=" + connectId);
						});
	}
}
