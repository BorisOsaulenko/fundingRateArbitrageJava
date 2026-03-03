package com.boris.fundingarbitrage.exchange.impl.binance.publicrest;

import com.boris.fundingarbitrage.exchange.ExchangeContext;
import com.boris.fundingarbitrage.exchange.publichttp.PublicHttpClient;
import com.boris.fundingarbitrage.exchange.publichttp.PublicOnePullData;
import com.boris.fundingarbitrage.model.contract.BookTicker;
import com.boris.fundingarbitrage.model.contract.FundingRate;
import com.boris.fundingarbitrage.util.https.PrettyHttpClient;
import com.boris.fundingarbitrage.util.https.RequestProcessingClientWrapper;
import com.boris.fundingarbitrage.util.logger.Logger;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class BinancePublicHttpClient extends PublicHttpClient {
	private final RequestProcessingClientWrapper requestWrapper = new RequestProcessingClientWrapper(this.client);

	public BinancePublicHttpClient(ExchangeContext context) {
		super(context, PrettyHttpClient.getINSTANCE());
	}

	@Override
	protected CompletableFuture<Map<String, FundingRate>> getFundingRateSymbolBatch() {
		return requestWrapper.processRequest(
						PublicEndpoints.premiumIndexRequest(),
						PublicResponses.PremiumIndexResponse.class,
						PublicResponses.PremiumIndexResponse::getFundingRates
		);
	}

	@Override
	protected CompletableFuture<Map<String, PublicOnePullData>> getPublicOnePullData() {
		CompletableFuture<Map<String, BigDecimal>> lotSizesFuture = requestWrapper.processRequest(
						PublicEndpoints.exchangeInfoRequest(),
						PublicResponses.ExchangeInfoResponse.class,
						PublicResponses.ExchangeInfoResponse::getLotSizes
		);

		CompletableFuture<Map<String, Integer>> fundingGranularityFuture = requestWrapper.processRequest(
						PublicEndpoints.fundingInfoRequest(),
						PublicResponses.FundingInfoResponse.class,
						PublicResponses.FundingInfoResponse::getFundingGranularities
		);

		CompletableFuture<Map<String, BookTicker>> bookTickersFuture = requestWrapper.processRequest(
						PublicEndpoints.bookTickerRequest(),
						PublicResponses.BookTickerResponse.class,
						PublicResponses.BookTickerResponse::getBookTickers
		);

		CompletableFuture<Map<String, BigDecimal>> volumes24hFuture = requestWrapper.processRequest(
						PublicEndpoints.statistic24hRequest(),
						PublicResponses.Statistics24hResponse.class,
						PublicResponses.Statistics24hResponse::getVolume24h
		);

		return CompletableFuture.allOf(lotSizesFuture, fundingGranularityFuture, bookTickersFuture, volumes24hFuture)
						.thenApply(_ -> {
							Map<String, PublicOnePullData> data = new HashMap<>();
							for (String symbol : lotSizesFuture.join().keySet()) {
								try {
									BigDecimal lotSize = lotSizesFuture.join().get(symbol);
									BigDecimal volume24h = volumes24hFuture.join().get(symbol);
									BookTicker ticker = bookTickersFuture.join().get(symbol);
									int fundingGranularity = fundingGranularityFuture.join().get(symbol);
									data.put(symbol, new PublicOnePullData(lotSize, ticker, volume24h, fundingGranularity));
								} catch (Exception e) {
									Logger.error(e.getMessage());
									Logger.log("Failed to parse symbol: " +
														 symbol +
														 ". Data: BookTicker:" +
														 bookTickersFuture.join().get(symbol) +
														 ", Volume24h: " +
														 volumes24hFuture.join().get(symbol) +
														 ", LotSize: " +
														 lotSizesFuture.join().get(symbol) +
														 ", FundingGranularity: " +
														 fundingGranularityFuture.join().get(symbol));
								}
							}

							return data;
						});
	}
}
