package com.boris.fundingarbitrage.exchange.impl.binance.publicrest;

import com.boris.fundingarbitrage.ObjectMapperSingleton;
import com.boris.fundingarbitrage.exchange.ExchangeContext;
import com.boris.fundingarbitrage.exchange.publichttp.PublicHttpClient;
import com.boris.fundingarbitrage.exchange.publichttp.PublicOnePullData;
import com.boris.fundingarbitrage.model.contract.BookTicker;
import com.boris.fundingarbitrage.model.contract.FundingRate;
import com.boris.fundingarbitrage.util.https.PrettyHttpClient;
import com.boris.fundingarbitrage.util.logger.Logger;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public class BinancePublicHttpClient extends PublicHttpClient {
	private final ObjectMapper mapper = ObjectMapperSingleton.getInstance();

	public BinancePublicHttpClient(ExchangeContext context) {
		super(context, PrettyHttpClient.getINSTANCE());
	}

	private <T, U> CompletableFuture<U> processRequest(
					SimpleHttpRequest request,
					Class<T> responseClass,
					Function<T, U> parser
	) {
		return this.client.send(request).thenApply((response) -> {
			try {
				T responseObj = mapper.readValue(response.getBodyBytes(), responseClass);
				return parser.apply(responseObj);
			} catch (Exception e) {
				Logger.error(String.format("Error parsing public rest response: %s", e.getMessage()));
				throw new RuntimeException("Failed to process request", e);
			}
		});
	}

	@Override
	protected CompletableFuture<Map<String, FundingRate>> getFundingRateSymbolBatch() {
		return processRequest(
						PublicEndpoints.premiumIndexRequest(),
						PublicResponses.PremiumIndexResponse.class,
						PublicResponses.PremiumIndexResponse::getFundingRates
		);
	}

	@Override
	protected CompletableFuture<Map<String, PublicOnePullData>> getPublicOnePullData() {
		CompletableFuture<Map<String, Double>> lotSizesFuture = processRequest(
						PublicEndpoints.exchangeInfoRequest(),
						PublicResponses.ExchangeInfoResponse.class,
						PublicResponses.ExchangeInfoResponse::getLotSizes
		);

		CompletableFuture<Map<String, Integer>> fundingGranularityFuture = processRequest(
						PublicEndpoints.fundingInfoRequest(),
						PublicResponses.FundingInfoResponse.class,
						PublicResponses.FundingInfoResponse::getFundingGranularities
		);

		CompletableFuture<Map<String, BookTicker>> bookTickersFuture = processRequest(
						PublicEndpoints.bookTickerRequest(),
						PublicResponses.BookTickerResponse.class,
						PublicResponses.BookTickerResponse::getBookTickers
		);

		CompletableFuture<Map<String, Double>> volumes24hFuture = processRequest(
						PublicEndpoints.statistic24hRequest(),
						PublicResponses.Statistics24hResponse.class,
						PublicResponses.Statistics24hResponse::getVolume24h
		);

		return CompletableFuture
						.allOf(lotSizesFuture, fundingGranularityFuture, bookTickersFuture, volumes24hFuture)
						.thenApply(_ -> {
							Map<String, PublicOnePullData> data = new HashMap<>();
							for (String symbol : lotSizesFuture.join().keySet()) {
								try {
									double lotSize = lotSizesFuture.join().get(symbol);
									double volume24h = volumes24hFuture.join().get(symbol);
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