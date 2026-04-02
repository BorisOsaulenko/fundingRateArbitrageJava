package com.boris.fundingarbitrage.exchange.impl.binance.publicrest;

import com.boris.fundingarbitrage.exchange.ExchangeContext;
import com.boris.fundingarbitrage.exchange.publichttp.FuturesPublicOnePullData;
import com.boris.fundingarbitrage.exchange.publichttp.FuturesTradingState;
import com.boris.fundingarbitrage.exchange.publichttp.PublicHttpClient;
import com.boris.fundingarbitrage.exchange.publichttp.SpotPublicOnePullData;
import com.boris.fundingarbitrage.model.contract.BookTicker;
import com.boris.fundingarbitrage.model.contract.FundingRate;
import com.boris.fundingarbitrage.util.https.PrettyHttpClient;
import com.boris.fundingarbitrage.util.https.RequestProcessingClientWrapper;
import com.boris.fundingarbitrage.util.logger.Logger;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class BinancePublicHttpClient extends PublicHttpClient {
	private final RequestProcessingClientWrapper requestWrapper = new RequestProcessingClientWrapper(this.client);

	public BinancePublicHttpClient(ExchangeContext context) {
		super(context, PrettyHttpClient.getINSTANCE());
	}

	@Override
	protected CompletableFuture<Map<String, FundingRate>> getFundingRateSymbols() {
		return requestWrapper.processRequest(
						PublicEndpoints.premiumIndexRequest(),
						PublicResponses.PremiumIndexResponse.class,
						PublicResponses.PremiumIndexResponse::getFundingRates
		);
	}

	@Override
	protected CompletableFuture<Map<String, FuturesPublicOnePullData>> getFuturesPublicOnePullDataSymbols() {
		CompletableFuture<PublicResponses.ExchangeInfoResponse> exchangeInfoFuture = requestWrapper.getResponse(
						PublicEndpoints.exchangeInfoRequest(),
						PublicResponses.ExchangeInfoResponse.class
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

		CompletableFuture<Map<String, FundingRate>> fundingRates = requestWrapper.processRequest(
						PublicEndpoints.premiumIndexRequest(),
						PublicResponses.PremiumIndexResponse.class,
						PublicResponses.PremiumIndexResponse::getFundingRates
		);

		return CompletableFuture.allOf(exchangeInfoFuture, fundingGranularityFuture, bookTickersFuture, volumes24hFuture)
						.thenApply(_ -> {
							Map<String, FuturesPublicOnePullData> data = new HashMap<>();
							Map<String, BigDecimal> lotSizes = exchangeInfoFuture.join().getLotSizes();
							Map<String, FuturesTradingState> tradingStates = exchangeInfoFuture.join().getTradingStates();
							for (String symbol : lotSizes.keySet()) {
								try {
									BigDecimal lotSize = lotSizes.get(symbol);
									BigDecimal volume24h = volumes24hFuture.join().get(symbol);
									BookTicker ticker = bookTickersFuture.join().get(symbol);
									int fundingGranularity = fundingGranularityFuture.join().get(symbol);
									FundingRate rate = fundingRates.join().get(symbol);
									FuturesTradingState tradingState = tradingStates.get(symbol);

									data.put(
													symbol,
													new FuturesPublicOnePullData(
																	lotSize,
																	volume24h,
																	fundingGranularity,
																	ticker,
																	rate,
																	tradingState
													)
									);
								} catch (Exception e) {
									Logger.error(e.getMessage());
									Logger.log("Failed to parse symbol: " +
														 symbol +
														 ". Data: BookTicker:" +
														 bookTickersFuture.join().get(symbol) +
														 ", Volume24h: " +
														 volumes24hFuture.join().get(symbol) +
														 ", LotSize: " +
														 lotSizes.get(symbol) +
														 ", FundingGranularity: " +
														 fundingGranularityFuture.join().get(symbol));
								}
							}

							return data;
						});
	}

	@Override
	protected CompletableFuture<Map<String, SpotPublicOnePullData>> getSpotPublicOnePullDataSymbols() {
		CompletableFuture<PublicResponses.ExchangeInfoResponse> exchangeInfoFuture = requestWrapper.getResponse(
						PublicEndpoints.spotExchangeInfoRequest(),
						PublicResponses.ExchangeInfoResponse.class
		);
		CompletableFuture<Map<String, BookTicker>> bookTickersFuture = requestWrapper.processRequest(
						PublicEndpoints.spotBookTickerRequest(),
						PublicResponses.BookTickerResponse.class,
						PublicResponses.BookTickerResponse::getBookTickers
		);
		CompletableFuture<Map<String, BigDecimal>> volumes24hFuture = requestWrapper.processRequest(
						PublicEndpoints.spotStatistic24hRequest(),
						PublicResponses.Statistics24hResponse.class,
						PublicResponses.Statistics24hResponse::getVolume24h
		);

		return CompletableFuture.allOf(exchangeInfoFuture, bookTickersFuture, volumes24hFuture).thenApply(_ -> {
			Map<String, BigDecimal> lotSizes = exchangeInfoFuture.join().getSpotLotSizes();
			Map<String, SpotPublicOnePullData> data = new HashMap<>();
			for (String symbol : lotSizes.keySet()) {
				data.put(
								symbol,
								new SpotPublicOnePullData(
												lotSizes.get(symbol),
												volumes24hFuture.join().get(symbol),
												bookTickersFuture.join().get(symbol)
								)
				);
			}
			return data;
		});
	}

	@Override
	public CompletableFuture<Set<String>> getAvailableCoins() {
		return requestWrapper.processRequest(
						PublicEndpoints.exchangeInfoRequest(),
						PublicResponses.ExchangeInfoResponse.class,
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
