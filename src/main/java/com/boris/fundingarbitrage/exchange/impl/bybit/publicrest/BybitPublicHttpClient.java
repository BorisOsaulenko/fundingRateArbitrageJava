package com.boris.fundingarbitrage.exchange.impl.bybit.publicrest;

import com.boris.fundingarbitrage.exchange.ExchangeContext;
import com.boris.fundingarbitrage.exchange.publichttp.PublicHttpClient;
import com.boris.fundingarbitrage.exchange.publichttp.PublicOnePullData;
import com.boris.fundingarbitrage.model.contract.BookTicker;
import com.boris.fundingarbitrage.model.contract.FundingRate;
import com.boris.fundingarbitrage.util.https.PrettyHttpClient;
import com.boris.fundingarbitrage.util.https.RequestProcessingClientWrapper;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class BybitPublicHttpClient extends PublicHttpClient {
	private final RequestProcessingClientWrapper requestWrapper = new RequestProcessingClientWrapper(this.client);

	public BybitPublicHttpClient(ExchangeContext context) {
		super(context, PrettyHttpClient.getINSTANCE());
	}


	@Override
	protected CompletableFuture<Map<String, FundingRate>> getFundingRateSymbolBatch() {
		return requestWrapper.processRequest(
						PublicEndpoints.tickersRequest(),
						PublicResponses.TickersResponseSymbols.class,
						PublicResponses.TickersResponseSymbols::getFundingRates
		);
	}

	@Override
	protected CompletableFuture<Map<String, PublicOnePullData>> getPublicOnePullData() {
		Map<String, BigDecimal> lotSizes = new HashMap<>();
		Map<String, Integer> fundingGranularityHours = new HashMap<>();

		CompletableFuture<Void> instrumentsResponseFuture = requestWrapper.processPaginatedRequest(
						PublicEndpoints::instrumentsInfoRequest, PublicResponses.InstrumentsInfoSymbolsResponse.class, res -> {
							lotSizes.putAll(res.getLotSizes());
							fundingGranularityHours.putAll(res.getFundingGranularityHours());
						}, null
		);

		CompletableFuture<PublicResponses.TickersResponseSymbols>
						tickersResponseFuture =
						requestWrapper.getResponse(PublicEndpoints.tickersRequest(), PublicResponses.TickersResponseSymbols.class);

		return CompletableFuture.allOf(instrumentsResponseFuture, tickersResponseFuture).thenApply(_ -> {
			Map<String, BookTicker> bookTickers = tickersResponseFuture.join().getBookTickers();
			Map<String, BigDecimal> volume24h = tickersResponseFuture.join().getVolume24h();

			Map<String, PublicOnePullData> data = new HashMap<>();
			for (String symbol : lotSizes.keySet()) {
				data.put(
								symbol, new PublicOnePullData(
												lotSizes.get(symbol),
												bookTickers.get(symbol),
												volume24h.get(symbol),
												fundingGranularityHours.get(symbol)
								)
				);
			}

			return data;
		});
	}
}