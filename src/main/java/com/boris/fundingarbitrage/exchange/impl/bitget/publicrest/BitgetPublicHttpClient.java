package com.boris.fundingarbitrage.exchange.impl.bitget.publicrest;

import com.boris.fundingarbitrage.ObjectMapperSingleton;
import com.boris.fundingarbitrage.exchange.ExchangeContext;
import com.boris.fundingarbitrage.exchange.publichttp.PublicHttpClient;
import com.boris.fundingarbitrage.model.contract.BookTicker;
import com.boris.fundingarbitrage.model.contract.FundingRate;
import com.boris.fundingarbitrage.util.https.PrettyHttpClient;
import com.boris.fundingarbitrage.util.logger.Logger;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public class BitgetPublicHttpClient extends PublicHttpClient {
	private final ObjectMapper mapper = ObjectMapperSingleton.getInstance();

	public BitgetPublicHttpClient(ExchangeContext context) {
		super(context, PrettyHttpClient.getINSTANCE());
	}

	private <T, U> CompletableFuture<U> processRequest(
					SimpleHttpRequest request,
					Class<T> responseClass,
					Function<T, U> parser
	) {
		return this.client.send(request).thenApply((response) -> {
			try {
				T responseObj = mapper.readValue(response.getBodyText(), responseClass);
				return parser.apply(responseObj);
			} catch (Exception e) {
				Logger.error(String.format("Error parsing public rest response: %s", e.getMessage()));
				throw new RuntimeException("Failed to process request", e);
			}
		});
	}

	@Override
	protected CompletableFuture<Double> getLotSizeSymbol(String symbol) {
		return processRequest(
						PublicEndpoints.contractsRequestSymbol(symbol),
						PublicResponses.ContractsResponse.class,
						(resp) -> resp.lotSizeSymbol(symbol)
		);
	}

	@Override
	protected CompletableFuture<BookTicker> getBookTickerSymbol(String symbol) {
		return processRequest(
						PublicEndpoints.tickerRequestSymbol(symbol),
						PublicResponses.TickerResponse.class,
						PublicResponses.TickerResponse::bookTicker
		);
	}

	@Override
	protected CompletableFuture<FundingRate> getFundingRateSymbol(String symbol) {
		return processRequest(
						PublicEndpoints.fundingRateRequestSymbol(symbol),
						PublicResponses.FundingRateResponse.class,
						PublicResponses.FundingRateResponse::get
		);
	}

	@Override
	protected CompletableFuture<Double> getTradingVolume24hSymbol(String symbol) {
		return processRequest(
						PublicEndpoints.tickerRequestSymbol(symbol),
						PublicResponses.TickerResponse.class,
						PublicResponses.TickerResponse::volume24h
		);
	}

	@Override
	protected CompletableFuture<Double> getTradingVolume1hSymbol(String symbol) {
		return processRequest(
						PublicEndpoints.candles1hRequestSymbol(symbol),
						PublicResponses.CandlesResponse.class,
						PublicResponses.CandlesResponse::volume1h
		);
	}

	@Override
	protected CompletableFuture<Boolean> checkExistsSymbol(String symbol) {
		SimpleHttpRequest request = PublicEndpoints.contractsRequestSymbol(symbol);
		return this.client.sendNoCodeCheck(request).thenApply((response) -> {
			try {
				String body = response.getBodyText();
				JsonNode root = mapper.readTree(body);
				String code = root.path("code").asText();
				if ("00000".equals(code)) {
					PublicResponses.ContractsResponse resp = mapper.readValue(body, PublicResponses.ContractsResponse.class);
					return resp.existsSymbol(symbol);
				}

				// Bitget returns 40034 code when symbol does not exist
				// Bitget returns 40309 code when symbol has been delisted
				if ("40034".equals(code) || "40309".equals(code)) {
					return false;
				}

				String msg = root.path("msg").asText();
				throw new RuntimeException("Bitget checkSymbolExists failed: " + code + " " + msg);
			} catch (RuntimeException e) {
				throw e;
			} catch (Exception e) {
				Logger.error(String.format("Error parsing public rest response: %s", e.getMessage()));
				throw new RuntimeException("Failed to process request", e);
			}
		});
	}
}
