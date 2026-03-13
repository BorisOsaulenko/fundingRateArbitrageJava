package com.boris.fundingarbitrage.exchange.impl.bitget.publicrest;

import com.boris.fundingarbitrage.exchange.ExchangeContext;
import com.boris.fundingarbitrage.exchange.publichttp.PublicHttpClient;
import com.boris.fundingarbitrage.exchange.publichttp.PublicOnePullData;
import com.boris.fundingarbitrage.model.contract.BookTicker;
import com.boris.fundingarbitrage.model.contract.FundingRate;
import com.boris.fundingarbitrage.util.https.PrettyHttpClient;
import com.boris.fundingarbitrage.util.https.RequestProcessingClientWrapper;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class BitgetPublicHttpClient extends PublicHttpClient {
	private final RequestProcessingClientWrapper requestWrapper = new RequestProcessingClientWrapper(this.client);

	public BitgetPublicHttpClient(ExchangeContext context) {
		super(context, PrettyHttpClient.getINSTANCE());
	}

	@Override
	protected CompletableFuture<Map<String, FundingRate>> getFundingRateSymbolBatch() {
		return requestWrapper.processRequest(
						PublicEndpoints.currentFundingRateRequest(),
						PublicResponses.CurrentFundingRateResponse.class,
						PublicResponses.CurrentFundingRateResponse::getFundingRates
		);
	}

	@Override
	protected CompletableFuture<Map<String, PublicOnePullData>> getPublicOnePullData() {
		CompletableFuture<Map<String, BigDecimal>> lotSizesFuture = requestWrapper.processRequest(
						PublicEndpoints.contractConfigRequest(),
						PublicResponses.ContractsResponse.class,
						PublicResponses.ContractsResponse::getLotSizes
		);
		CompletableFuture<Map<String, Integer>> fundingGranularityFuture = requestWrapper.processRequest(
						PublicEndpoints.currentFundingRateRequest(),
						PublicResponses.CurrentFundingRateResponse.class,
						PublicResponses.CurrentFundingRateResponse::getFundingGranularity
		);
		CompletableFuture<PublicResponses.TickerResponse>
						tickersResponse =
						requestWrapper.getResponse(PublicEndpoints.tickersRequest(), PublicResponses.TickerResponse.class);

		return CompletableFuture.allOf(lotSizesFuture, fundingGranularityFuture, tickersResponse).thenApply(_ -> {
			Map<String, BigDecimal> volume24h = tickersResponse.join().getUsdtVolumes();
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

	@Override
	public CompletableFuture<Set<String>> getAvailableCoins() {
		return requestWrapper.processRequest(
						PublicEndpoints.contractConfigRequest(),
						PublicResponses.ContractsResponse.class,
						res -> {
							Set<String> coins = new HashSet<>();
							for (String symbol : res.getLotSizes().keySet()) {
								try {
									coins.add(exchangeContext.getSymbolInverse(symbol));
								} catch (Exception ignored) {
									// Ignore symbols that do not match exchange symbol format
								}
							}
							return coins;
						}
		);
	}
}
