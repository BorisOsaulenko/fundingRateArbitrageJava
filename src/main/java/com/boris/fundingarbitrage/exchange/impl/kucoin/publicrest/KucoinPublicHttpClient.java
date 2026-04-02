package com.boris.fundingarbitrage.exchange.impl.kucoin.publicrest;

import com.boris.fundingarbitrage.exchange.ExchangeContext;
import com.boris.fundingarbitrage.exchange.publichttp.FuturesPublicOnePullData;
import com.boris.fundingarbitrage.exchange.publichttp.FuturesTradingState;
import com.boris.fundingarbitrage.exchange.publichttp.PublicHttpClient;
import com.boris.fundingarbitrage.exchange.publichttp.SpotPublicOnePullData;
import com.boris.fundingarbitrage.model.contract.BookTicker;
import com.boris.fundingarbitrage.model.contract.FundingRate;
import com.boris.fundingarbitrage.util.https.PrettyHttpClient;
import com.boris.fundingarbitrage.util.https.RequestProcessingClientWrapper;

import java.math.BigDecimal;
import java.net.URI;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class KucoinPublicHttpClient extends PublicHttpClient {
	private final RequestProcessingClientWrapper requestWrapper = new RequestProcessingClientWrapper(this.client);

	public KucoinPublicHttpClient(ExchangeContext context) {
		super(context, PrettyHttpClient.getINSTANCE());
	}

	@Override
	protected CompletableFuture<Map<String, FundingRate>> getFundingRateSymbols() {
		return requestWrapper.processRequest(
						PublicEndpoints.activeContractsRequest(),
						PublicResponses.ActiveContractsResponse.class,
						PublicResponses.ActiveContractsResponse::getFundingRates
		);
	}

	@Override
	protected CompletableFuture<Map<String, FuturesPublicOnePullData>> getFuturesPublicOnePullDataSymbols() {
		CompletableFuture<PublicResponses.ActiveContractsResponse> contractsResponseFuture = requestWrapper.getResponse(
						PublicEndpoints.activeContractsRequest(),
						PublicResponses.ActiveContractsResponse.class
		);
		CompletableFuture<PublicResponses.AllTickersResponse> tickersResponseFuture = requestWrapper.getResponse(
						PublicEndpoints.allTickersRequestSymbols(),
						PublicResponses.AllTickersResponse.class
		);

		return CompletableFuture.allOf(contractsResponseFuture, tickersResponseFuture).thenApply(_ -> {
			Map<String, BigDecimal> lotSizes = contractsResponseFuture.join().getLotSizes();
			Map<String, Integer> fundingIntervals = contractsResponseFuture.join().getFundingGranularityHours();
			Map<String, BigDecimal> volumes24h = contractsResponseFuture.join().getVolume24h();
			Map<String, FundingRate> fundingRates = contractsResponseFuture.join().getFundingRates();
			Map<String, FuturesTradingState> tradingStates = contractsResponseFuture.join().getTradingStates();
			Map<String, BookTicker> bookTickers = tickersResponseFuture.join().getBookTickers();

			Map<String, FuturesPublicOnePullData> result = new HashMap<>();
			for (String symbol : lotSizes.keySet()) {
				result.put(
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
			return result;
		});
	}

	@Override
	protected CompletableFuture<Map<String, SpotPublicOnePullData>> getSpotPublicOnePullDataSymbols() {
		CompletableFuture<PublicResponses.SpotSymbolsResponse> symbolsResponseFuture = requestWrapper.getResponse(
						PublicEndpoints.spotSymbolsRequest(),
						PublicResponses.SpotSymbolsResponse.class
		);
		CompletableFuture<PublicResponses.SpotAllTickersResponse> tickersResponseFuture = requestWrapper.getResponse(
						PublicEndpoints.spotAllTickersRequest(),
						PublicResponses.SpotAllTickersResponse.class
		);

		return CompletableFuture.allOf(symbolsResponseFuture, tickersResponseFuture).thenApply(_ -> {
			Map<String, BigDecimal> lotSizes = symbolsResponseFuture.join().getLotSizes();
			Map<String, BigDecimal> volumes24h = tickersResponseFuture.join().getVolume24h();
			Map<String, BookTicker> bookTickers = tickersResponseFuture.join().getBookTickers();

			Map<String, SpotPublicOnePullData> result = new HashMap<>();
			for (String symbol : lotSizes.keySet()) {
				result.put(
								symbol,
								new SpotPublicOnePullData(lotSizes.get(symbol), volumes24h.get(symbol), bookTickers.get(symbol))
				);
			}
			return result;
		});
	}

	@Override
	public CompletableFuture<Set<String>> getAvailableCoins() {
		return requestWrapper.processRequest(
						PublicEndpoints.activeContractsRequest(),
						PublicResponses.ActiveContractsResponse.class,
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
