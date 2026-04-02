package com.boris.fundingarbitrage.exchange.impl.bitget.publicrest;

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

public class BitgetPublicHttpClient extends PublicHttpClient {
	private final RequestProcessingClientWrapper requestWrapper = new RequestProcessingClientWrapper(this.client);

	public BitgetPublicHttpClient(ExchangeContext context) {
		super(context, PrettyHttpClient.getINSTANCE());
	}

	@Override
	protected CompletableFuture<Map<String, FundingRate>> getFundingRateSymbols() {
		return requestWrapper.processRequest(
						PublicEndpoints.currentFundingRateRequest(),
						PublicResponses.CurrentFundingRateResponse.class,
						PublicResponses.CurrentFundingRateResponse::getFundingRates
		);
	}

	@Override
	protected CompletableFuture<Map<String, FuturesPublicOnePullData>> getFuturesPublicOnePullDataSymbols() {
		CompletableFuture<PublicResponses.ContractsResponse> contractsResponseFuture = requestWrapper.getResponse(
						PublicEndpoints.contractConfigRequest(),
						PublicResponses.ContractsResponse.class
		);
		CompletableFuture<PublicResponses.CurrentFundingRateResponse> fundingResponseFuture = requestWrapper.getResponse(
						PublicEndpoints.currentFundingRateRequest(),
						PublicResponses.CurrentFundingRateResponse.class
		);
		CompletableFuture<PublicResponses.TickerResponse> tickersResponse = requestWrapper.getResponse(
						PublicEndpoints.tickersRequest(),
						PublicResponses.TickerResponse.class
		);

		return CompletableFuture.allOf(contractsResponseFuture, fundingResponseFuture, tickersResponse).thenApply(_ -> {
			Map<String, BigDecimal> lotSizes = contractsResponseFuture.join().getLotSizes();
			Map<String, FuturesTradingState> tradingStates = contractsResponseFuture.join().getTradingStates();
			Map<String, BigDecimal> volume24h = tickersResponse.join().getUsdtVolumes();
			Map<String, BookTicker> bookTickers = tickersResponse.join().getBookTickers();
			Map<String, Integer> fundingGranularity = fundingResponseFuture.join().getFundingGranularity();
			Map<String, FundingRate> fundingRates = fundingResponseFuture.join().getFundingRates();

			Map<String, FuturesPublicOnePullData> data = new HashMap<>();
			for (String symbol : lotSizes.keySet()) {
				data.put(
								symbol,
								new FuturesPublicOnePullData(
												lotSizes.get(symbol),
												volume24h.get(symbol),
												fundingGranularity.get(symbol),
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
		CompletableFuture<PublicResponses.SpotSymbolsResponse> symbolsResponseFuture = requestWrapper.getResponse(
						PublicEndpoints.spotSymbolsRequest(),
						PublicResponses.SpotSymbolsResponse.class
		);
		CompletableFuture<PublicResponses.TickerResponse> tickersResponseFuture = requestWrapper.getResponse(
						PublicEndpoints.spotTickersRequest(),
						PublicResponses.TickerResponse.class
		);

		return CompletableFuture.allOf(symbolsResponseFuture, tickersResponseFuture).thenApply(_ -> {
			Map<String, BigDecimal> lotSizes = symbolsResponseFuture.join().getLotSizes();
			Map<String, BigDecimal> volumes24h = tickersResponseFuture.join().getUsdtVolumes();
			Map<String, BookTicker> bookTickers = tickersResponseFuture.join().getBookTickers();

			Map<String, SpotPublicOnePullData> data = new HashMap<>();
			for (String symbol : lotSizes.keySet()) {
				data.put(
								symbol,
								new SpotPublicOnePullData(
												lotSizes.get(symbol),
												volumes24h.get(symbol),
												bookTickers.get(symbol)
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
