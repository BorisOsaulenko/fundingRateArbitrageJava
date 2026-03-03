package com.boris.fundingarbitrage.exchange.impl.gate.publicrest;

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

public class GatePublicHttpClient extends PublicHttpClient {
	private final RequestProcessingClientWrapper requestWrapper = new RequestProcessingClientWrapper(this.client);

	public GatePublicHttpClient(ExchangeContext context) {
		super(context, PrettyHttpClient.getINSTANCE());
	}

	@Override
	protected CompletableFuture<Map<String, FundingRate>> getFundingRateSymbolBatch() {
		return requestWrapper.processRequest(
						PublicEndpoints.contractsRequestSymbols(),
						PublicResponses.ContractsResponse.class,
						PublicResponses.ContractsResponse::getFundingRates
		);
	}

	@Override
	protected CompletableFuture<Map<String, PublicOnePullData>> getPublicOnePullData() {
		CompletableFuture<PublicResponses.ContractsResponse>
						contractsResponseFuture =
						requestWrapper.getResponse(
										PublicEndpoints.contractsRequestSymbols(),
										PublicResponses.ContractsResponse.class
						);
		CompletableFuture<PublicResponses.TickersResponse>
						tickersResponseFuture =
						requestWrapper.getResponse(PublicEndpoints.tickersRequestSymbols(), PublicResponses.TickersResponse.class);

		return CompletableFuture.allOf(contractsResponseFuture, tickersResponseFuture).thenApply(_ -> {
			Map<String, BigDecimal> lotSizes = contractsResponseFuture.join().getLotSizes();
			Map<String, Integer> fundingGranularityHours = contractsResponseFuture.join().getFundingGranularityHours();
			Map<String, BookTicker> bookTickers = tickersResponseFuture.join().getBookTickers();
			Map<String, BigDecimal> volumes24h = tickersResponseFuture.join().getVolume24h();

			Map<String, PublicOnePullData> data = new HashMap<>();
			for (String symbol : lotSizes.keySet()) {
				BookTicker ticker = bookTickers.get(symbol);
				Integer granularity = fundingGranularityHours.get(symbol);

				data.put(symbol, new PublicOnePullData(lotSizes.get(symbol), ticker, volumes24h.get(symbol), granularity));
			}
			return data;
		});
	}
}
