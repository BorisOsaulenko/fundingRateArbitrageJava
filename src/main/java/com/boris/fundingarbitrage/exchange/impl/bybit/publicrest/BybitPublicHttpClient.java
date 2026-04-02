package com.boris.fundingarbitrage.exchange.impl.bybit.publicrest;

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

public class BybitPublicHttpClient extends PublicHttpClient {
	private final RequestProcessingClientWrapper requestWrapper = new RequestProcessingClientWrapper(this.client);

	public BybitPublicHttpClient(ExchangeContext context) {
		super(context, PrettyHttpClient.getINSTANCE());
	}


	@Override
	protected CompletableFuture<Map<String, FundingRate>> getFundingRateSymbols() {
		return requestWrapper.processRequest(
						PublicEndpoints.tickersRequest(),
						PublicResponses.TickersResponseSymbols.class,
						PublicResponses.TickersResponseSymbols::getFundingRates
		);
	}

	@Override
	protected CompletableFuture<Map<String, FuturesPublicOnePullData>> getFuturesPublicOnePullDataSymbols() {
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
			Map<String, FundingRate> fundingRates = tickersResponseFuture.join().getFundingRates();
			Map<String, BigDecimal> volume24h = tickersResponseFuture.join().getVolume24h();

			Map<String, FuturesPublicOnePullData> data = new HashMap<>();
			for (String symbol : lotSizes.keySet()) {
				data.put(
								symbol, new FuturesPublicOnePullData(
												lotSizes.get(symbol),
												volume24h.get(symbol),
												fundingGranularityHours.get(symbol),
												bookTickers.get(symbol),
												fundingRates.get(symbol),
												FuturesTradingState.TRADING // Bybit only returns trading coins
								)
				);
			}

			return data;
		});
	}

	@Override
	protected CompletableFuture<Map<String, SpotPublicOnePullData>> getSpotPublicOnePullDataSymbols() {
		CompletableFuture<PublicResponses.SpotInstrumentsInfoResponse> instrumentsResponseFuture = requestWrapper.getResponse(
						PublicEndpoints.spotInstrumentsInfoRequest(),
						PublicResponses.SpotInstrumentsInfoResponse.class
		);
		CompletableFuture<PublicResponses.SpotTickersResponse> tickersResponseFuture = requestWrapper.getResponse(
						PublicEndpoints.spotTickersRequest(),
						PublicResponses.SpotTickersResponse.class
		);

		return CompletableFuture.allOf(instrumentsResponseFuture, tickersResponseFuture).thenApply(_ -> {
			Map<String, BigDecimal> lotSizes = instrumentsResponseFuture.join().getLotSizes();
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
		Set<String> coins = new HashSet<>();
		return requestWrapper.processPaginatedRequest(
						PublicEndpoints::instrumentsInfoRequest,
						PublicResponses.InstrumentsInfoSymbolsResponse.class,
						res -> {
							for (String symbol : res.getLotSizes().keySet()) {
								try {
									coins.add(exchangeContext.getFuturesSymbolInverse(symbol));
								} catch (Exception ignored) {
									// Ignore symbols that do not match exchange symbol format
								}
							}
						},
						null
		).thenApply(_ -> coins);
	}
}
