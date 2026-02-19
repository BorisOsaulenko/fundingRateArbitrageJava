package com.boris.fundingarbitrage.exchange.impl.whitebit.publicrest;

import com.boris.fundingarbitrage.exchange.ExchangeContext;
import com.boris.fundingarbitrage.exchange.publichttp.PublicHttpClient;
import com.boris.fundingarbitrage.exchange.publichttp.PublicOnePullData;
import com.boris.fundingarbitrage.model.contract.BookTicker;
import com.boris.fundingarbitrage.model.contract.FundingRate;
import com.boris.fundingarbitrage.util.https.PrettyHttpClient;
import com.boris.fundingarbitrage.util.https.RequestProcessingClientWrapper;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class WhitebitPublicHttpClient extends PublicHttpClient {
	private final RequestProcessingClientWrapper requestWrapper = new RequestProcessingClientWrapper(this.client);

	public WhitebitPublicHttpClient(ExchangeContext context) {
		super(context, PrettyHttpClient.getINSTANCE());
	}

	@Override
	protected CompletableFuture<Map<String, FundingRate>> getFundingRateSymbolBatch() {
		return requestWrapper.processRequest(
						PublicEndpoints.futuresRequest(),
						PublicResponses.FuturesResponse.class,
						PublicResponses.FuturesResponse::getFundingRates
		);
	}

	@Override
	protected CompletableFuture<Map<String, PublicOnePullData>> getPublicOnePullData() {
		CompletableFuture<PublicResponses.MarketsResponse> marketsResponseFuture = requestWrapper.getResponse(
						PublicEndpoints.marketsRequest(),
						PublicResponses.MarketsResponse.class
		);
		CompletableFuture<PublicResponses.FuturesResponse> futuresResponseFuture = requestWrapper.getResponse(
						PublicEndpoints.futuresRequest(),
						PublicResponses.FuturesResponse.class
		);

		return CompletableFuture.allOf(marketsResponseFuture, futuresResponseFuture).thenCompose(_ -> {
			Map<String, Double> lotSizes = marketsResponseFuture.join().getLotSizes();
			Map<String, Double> volumes24h = futuresResponseFuture.join().getVolume24h();
			Map<String, Integer> fundingGranularityHours = futuresResponseFuture.join().getFundingGranularityHours();
			Map<String, BookTicker> bookTickers = futuresResponseFuture.join().getBookTickers();

			Map<String, PublicOnePullData> data = new HashMap<>();
			for (String symbol : lotSizes.keySet()) {
				var symbolData = new PublicOnePullData(
								lotSizes.get(symbol),
								bookTickers.get(symbol),
								volumes24h.get(symbol),
								fundingGranularityHours.get(symbol)
				);
				data.put(symbol, symbolData);
			}
			return CompletableFuture.completedFuture(data);
		});
	}
}
