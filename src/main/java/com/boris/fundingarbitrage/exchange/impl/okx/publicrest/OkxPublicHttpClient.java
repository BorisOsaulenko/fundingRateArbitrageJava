package com.boris.fundingarbitrage.exchange.impl.okx.publicrest;

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

public class OkxPublicHttpClient extends PublicHttpClient {
	private final RequestProcessingClientWrapper requestWrapper = new RequestProcessingClientWrapper(this.client);

	public OkxPublicHttpClient(ExchangeContext context) {
		super(context, PrettyHttpClient.getINSTANCE());
	}

	@Override
	protected CompletableFuture<Map<String, FundingRate>> getFundingRateSymbolBatch() {
		return requestWrapper.processRequest(
						PublicEndpoints.fundingRateRequestSymbols(),
						PublicResponses.FundingRatesResponse.class,
						PublicResponses.FundingRatesResponse::getFundingRates
		);
	}

	@Override
	protected CompletableFuture<Map<String, PublicOnePullData>> getPublicOnePullData() {
		CompletableFuture<PublicResponses.InstrumentsResponse>
						instrumentsResponseFuture =
						requestWrapper.getResponse(
										PublicEndpoints.instrumentsRequestSymbols(),
										PublicResponses.InstrumentsResponse.class
						);
		CompletableFuture<PublicResponses.TickersResponse>
						tickersResponseFuture =
						requestWrapper.getResponse(PublicEndpoints.tickersRequestSymbols(), PublicResponses.TickersResponse.class);
		CompletableFuture<PublicResponses.FundingRatesResponse>
						fundingResponseFuture =
						requestWrapper.getResponse(
										PublicEndpoints.fundingRateRequestSymbols(),
										PublicResponses.FundingRatesResponse.class
						);

		return CompletableFuture.allOf(instrumentsResponseFuture, tickersResponseFuture, fundingResponseFuture)
						.thenApply(_ -> {
							Map<String, BigDecimal> lotSizes = instrumentsResponseFuture.join().getLotSizes();
							Map<String, BookTicker> bookTickers = tickersResponseFuture.join().getBookTickers();
							Map<String, BigDecimal> volumes24h = tickersResponseFuture.join().getVolume24h();
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

	@Override
	public CompletableFuture<Set<String>> getAvailableCoins() {
		return requestWrapper.processRequest(
						PublicEndpoints.instrumentsRequestSymbols(),
						PublicResponses.InstrumentsResponse.class,
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
