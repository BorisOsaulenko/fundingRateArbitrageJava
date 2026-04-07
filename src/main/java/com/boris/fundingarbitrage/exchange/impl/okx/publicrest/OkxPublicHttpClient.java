package com.boris.fundingarbitrage.exchange.impl.okx.publicrest;

import com.boris.fundingarbitrage.exchange.ExchangeContext;
import com.boris.fundingarbitrage.exchange.publichttp.FuturesPublicOnePullData;
import com.boris.fundingarbitrage.exchange.publichttp.FuturesTradingState;
import com.boris.fundingarbitrage.exchange.publichttp.PublicHttpClient;
import com.boris.fundingarbitrage.exchange.publichttp.SpotPublicOnePullData;
import com.boris.fundingarbitrage.model.contract.BookTicker;
import com.boris.fundingarbitrage.model.contract.Funding;
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
	protected CompletableFuture<Map<String, Funding>> getFundingRateSymbols() {
		return requestWrapper.processRequest(
						PublicEndpoints.fundingRateRequestSymbols(),
						PublicResponses.FundingRatesResponse.class,
						PublicResponses.FundingRatesResponse::getFundingRates
		);
	}

	@Override
	protected CompletableFuture<Map<String, FuturesPublicOnePullData>> getFuturesPublicOnePullDataSymbols() {
		CompletableFuture<PublicResponses.InstrumentsResponse> instrumentsResponseFuture = requestWrapper.getResponse(
						PublicEndpoints.instrumentsRequestSymbols(),
						PublicResponses.InstrumentsResponse.class
		);
		CompletableFuture<PublicResponses.TickersResponse> tickersResponseFuture = requestWrapper.getResponse(
						PublicEndpoints.tickersRequestSymbols(),
						PublicResponses.TickersResponse.class
		);
		CompletableFuture<PublicResponses.FundingRatesResponse> fundingResponseFuture = requestWrapper.getResponse(
						PublicEndpoints.fundingRateRequestSymbols(),
						PublicResponses.FundingRatesResponse.class
		);

		return CompletableFuture.allOf(instrumentsResponseFuture, tickersResponseFuture, fundingResponseFuture)
						.thenApply(_ -> {
							Map<String, BigDecimal> lotSizes = instrumentsResponseFuture.join().getLotSizes();
							Map<String, FuturesTradingState> tradingStates = instrumentsResponseFuture.join().getTradingStates();
							Map<String, BookTicker> bookTickers = tickersResponseFuture.join().getBookTickers();
							Map<String, BigDecimal> volumes24h = tickersResponseFuture.join().getVolume24h();
							Map<String, Integer> fundingIntervals = fundingResponseFuture.join().getFundingGranularityHours();
							Map<String, Funding> fundingRates = fundingResponseFuture.join().getFundingRates();

							Map<String, FuturesPublicOnePullData> data = new HashMap<>();
							for (String symbol : lotSizes.keySet()) {
								data.put(
												symbol,
												new FuturesPublicOnePullData(
																lotSizes.get(symbol),
																volumes24h.get(symbol),
																fundingIntervals.get(symbol),
																bookTickers.get(symbol),
																fundingRates.get(symbol),
																tradingStates.get(symbol)
												)
								);
							}
							return data;
						});
	}

	@Override
	protected CompletableFuture<Map<String, SpotPublicOnePullData>> getSpotPublicOnePullDataSymbols() {
		CompletableFuture<PublicResponses.InstrumentsResponse> instrumentsResponseFuture = requestWrapper.getResponse(
						PublicEndpoints.spotInstrumentsRequestSymbols(),
						PublicResponses.InstrumentsResponse.class
		);
		CompletableFuture<PublicResponses.TickersResponse> tickersResponseFuture = requestWrapper.getResponse(
						PublicEndpoints.spotTickersRequestSymbols(),
						PublicResponses.TickersResponse.class
		);

		return CompletableFuture.allOf(instrumentsResponseFuture, tickersResponseFuture).thenApply(_ -> {
			Map<String, BigDecimal> lotSizes = new HashMap<>();
			for (Map.Entry<String, BigDecimal> entry : instrumentsResponseFuture.join().getLotSizes().entrySet()) {
				if (entry.getKey().endsWith("-USDT")) lotSizes.put(entry.getKey(), entry.getValue());
			}
			Map<String, BookTicker> bookTickers = tickersResponseFuture.join().getBookTickers();
			Map<String, BigDecimal> volumes24h = tickersResponseFuture.join().getVolume24h();

			Map<String, SpotPublicOnePullData> data = new HashMap<>();
			for (String symbol : lotSizes.keySet()) {
				data.put(
								symbol,
								new SpotPublicOnePullData(lotSizes.get(symbol), volumes24h.get(symbol), bookTickers.get(symbol))
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
									coins.add(exchangeContext.getFuturesSymbolInverse(symbol));
								} catch (Exception ignored) {
									// Ignore symbols that do not match exchange symbol format
								}
							}
							return coins;
						}
		);
	}
}
