package com.boris.fundingarbitrage.exchange.impl.whitebit.publicrest;

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

public class WhitebitPublicHttpClient extends PublicHttpClient {
	private final RequestProcessingClientWrapper requestWrapper = new RequestProcessingClientWrapper(this.client);

	public WhitebitPublicHttpClient(ExchangeContext context) {
		super(context, PrettyHttpClient.getINSTANCE());
	}

	@Override
	protected CompletableFuture<Map<String, FundingRate>> getFundingRateSymbols() {
		return requestWrapper.processRequest(
						PublicEndpoints.futuresRequest(),
						PublicResponses.FuturesResponse.class,
						PublicResponses.FuturesResponse::getFundingRates
		);
	}

	@Override
	protected CompletableFuture<Map<String, FuturesPublicOnePullData>> getFuturesPublicOnePullDataSymbols() {
		CompletableFuture<PublicResponses.MarketsResponse> marketsResponseFuture = requestWrapper.getResponse(
						PublicEndpoints.marketsRequest(),
						PublicResponses.MarketsResponse.class
		);
		CompletableFuture<PublicResponses.FuturesResponse> futuresResponseFuture = requestWrapper.getResponse(
						PublicEndpoints.futuresRequest(),
						PublicResponses.FuturesResponse.class
		);

		return CompletableFuture.allOf(marketsResponseFuture, futuresResponseFuture).thenCompose(_ -> {
			Map<String, BigDecimal> lotSizes = marketsResponseFuture.join().getLotSizes();
			Map<String, FuturesTradingState> tradingStates = marketsResponseFuture.join().getTradingStates();
			Map<String, BigDecimal> volumes24h = futuresResponseFuture.join().getVolume24h();
			Map<String, Integer> fundingIntervals = futuresResponseFuture.join().getFundingGranularityHours();
			Map<String, BookTicker> bookTickers = futuresResponseFuture.join().getBookTickers();
			Map<String, FundingRate> fundingRates = futuresResponseFuture.join().getFundingRates();

			Map<String, FuturesPublicOnePullData> data = new HashMap<>();
			for (String symbol : lotSizes.keySet()) {
				var symbolData = new FuturesPublicOnePullData(
								lotSizes.get(symbol),
								volumes24h.get(symbol),
								fundingIntervals.get(symbol),
								bookTickers.get(symbol),
								fundingRates.get(symbol),
								tradingStates.getOrDefault(symbol, FuturesTradingState.PREMARKET)
				);
				data.put(symbol, symbolData);
			}
			return CompletableFuture.completedFuture(data);
		});
	}

	@Override
	protected CompletableFuture<Map<String, SpotPublicOnePullData>> getSpotPublicOnePullDataSymbols() {
		CompletableFuture<PublicResponses.MarketsResponse> marketsResponseFuture = requestWrapper.getResponse(
						PublicEndpoints.marketsRequest(),
						PublicResponses.MarketsResponse.class
		);
		CompletableFuture<PublicResponses.SpotTickersResponse> tickersResponseFuture = requestWrapper.getResponse(
						PublicEndpoints.tickerRequest(),
						PublicResponses.SpotTickersResponse.class
		);

		return CompletableFuture.allOf(marketsResponseFuture, tickersResponseFuture).thenApply(_ -> {
			Map<String, BigDecimal> lotSizes = marketsResponseFuture.join().getSpotLotSizes();
			Map<String, BigDecimal> volumes24h = tickersResponseFuture.join().getVolume24h();
			Map<String, BookTicker> bookTickers = tickersResponseFuture.join().getBookTickers();

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
						PublicEndpoints.futuresRequest(),
						PublicResponses.FuturesResponse.class,
						res -> {
							Set<String> coins = new HashSet<>();
							for (String symbol : res.getVolume24h().keySet()) {
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
