package com.boris.fundingarbitrage.exchange.impl.gate.publicrest;

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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class GatePublicHttpClient extends PublicHttpClient {
	private final RequestProcessingClientWrapper requestWrapper = new RequestProcessingClientWrapper(this.client);

	public GatePublicHttpClient(ExchangeContext context) {
		super(context, PrettyHttpClient.getINSTANCE());
	}

	@Override
	protected CompletableFuture<Map<String, FundingRate>> getFundingRateSymbols() {
		return requestWrapper.processRequest(
						PublicEndpoints.contractsRequestSymbols(),
						PublicResponses.ContractsResponse.class,
						PublicResponses.ContractsResponse::getFundingRates
		);
	}

	@Override
	protected CompletableFuture<Map<String, FuturesPublicOnePullData>> getFuturesPublicOnePullDataSymbols() {
		CompletableFuture<PublicResponses.ContractsResponse> contractsResponseFuture = requestWrapper.getResponse(
						PublicEndpoints.contractsRequestSymbols(),
						PublicResponses.ContractsResponse.class
		);

		CompletableFuture<PublicResponses.TickersResponse> tickersResponseFuture = requestWrapper.getResponse(
						PublicEndpoints.tickersRequestSymbols(),
						PublicResponses.TickersResponse.class
		);

		return CompletableFuture.allOf(contractsResponseFuture, tickersResponseFuture).thenApply(_ -> {
			Map<String, BigDecimal> lotSizes = contractsResponseFuture.join().getLotSizes();
			Map<String, Integer> fundingIntervals = contractsResponseFuture.join().getFundingGranularityHours();
			Map<String, FundingRate> fundingRates = contractsResponseFuture.join().getFundingRates();
			Map<String, BookTicker> bookTickers = tickersResponseFuture.join().getBookTickers();
			Map<String, BigDecimal> volumes24h = tickersResponseFuture.join().getVolume24h();
			Map<String, FuturesTradingState> tradingStates = contractsResponseFuture.join().getTradingStates();

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
		CompletableFuture<PublicResponses.SpotCurrencyPairsResponse> pairsResponseFuture = requestWrapper.getResponse(
						PublicEndpoints.spotCurrencyPairsRequest(),
						PublicResponses.SpotCurrencyPairsResponse.class
		);
		CompletableFuture<PublicResponses.SpotTickersResponse> tickersResponseFuture = requestWrapper.getResponse(
						PublicEndpoints.spotTickersRequest(),
						PublicResponses.SpotTickersResponse.class
		);

		return CompletableFuture.allOf(pairsResponseFuture, tickersResponseFuture).thenApply(_ -> {
			Map<String, BigDecimal> lotSizes = pairsResponseFuture.join().getLotSizes();
			Map<String, BookTicker> bookTickers = tickersResponseFuture.join().getBookTickers();
			Map<String, BigDecimal> volume24h = tickersResponseFuture.join().getVolume24h();

			Map<String, SpotPublicOnePullData> data = new HashMap<>();
			for (String symbol : lotSizes.keySet()) {
				data.put(
								symbol,
								new SpotPublicOnePullData(lotSizes.get(symbol), volume24h.get(symbol), bookTickers.get(symbol))
				);
			}
			return data;
		});
	}

	@Override
	public CompletableFuture<Set<String>> getAvailableCoins() {
		return requestWrapper.processRequest(
						PublicEndpoints.contractsRequestSymbols(),
						PublicResponses.ContractsResponse.class,
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
