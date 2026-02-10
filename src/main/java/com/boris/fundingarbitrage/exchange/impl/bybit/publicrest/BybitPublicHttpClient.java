package com.boris.fundingarbitrage.exchange.impl.bybit.publicrest;

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

public class BybitPublicHttpClient extends PublicHttpClient {
	private final ObjectMapper mapper = ObjectMapperSingleton.getInstance();

	public BybitPublicHttpClient(ExchangeContext context) {
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
						PublicEndpoints.instrumentsInfoRequestSymbol(symbol),
						PublicResponses.InstrumentsInfoResponse.class,
						(resp) -> resp.lotSizeSymbol(symbol)
		);
	}

	@Override
	protected CompletableFuture<BookTicker> getBookTickerSymbol(String symbol) {
		return processRequest(
						PublicEndpoints.tickersRequestSymbol(symbol),
						PublicResponses.TickersResponse.class,
						PublicResponses.TickersResponse::bookTicker
		);
	}

	@Override
	protected CompletableFuture<FundingRate> getFundingRateSymbol(String symbol) {
		return processRequest(
						PublicEndpoints.tickersRequestSymbol(symbol),
						PublicResponses.TickersResponse.class,
						PublicResponses.TickersResponse::fundingRate
		);
	}

	@Override
	protected CompletableFuture<Double> getTradingVolume24hSymbol(String symbol) {
		return processRequest(
						PublicEndpoints.tickersRequestSymbol(symbol),
						PublicResponses.TickersResponse.class,
						PublicResponses.TickersResponse::volume24h
		);
	}

	@Override
	protected CompletableFuture<Double> getTradingVolume1hSymbol(String symbol) {
		return processRequest(
						PublicEndpoints.kline1hRequestSymbol(symbol),
						PublicResponses.KlineResponse.class,
						PublicResponses.KlineResponse::volume1h
		);
	}

	@Override
	protected CompletableFuture<Boolean> checkExistsSymbol(String symbol) {
		SimpleHttpRequest request = PublicEndpoints.instrumentsInfoRequestSymbol(symbol);
		return this.client.sendNoCodeCheck(request).thenApply((response) -> {
			try {
				String body = response.getBodyText();
				JsonNode root = mapper.readTree(body);
				int retCode = root.path("retCode").asInt(-1);
				if (retCode == 0) {
					PublicResponses.InstrumentsInfoResponse resp = mapper.readValue(
									body,
									PublicResponses.InstrumentsInfoResponse.class
					);
					return resp.symbolExists(symbol);
				}
				return false;
			} catch (RuntimeException e) {
				throw e;
			} catch (Exception e) {
				Logger.error(String.format("Error parsing public rest response: %s", e.getMessage()));
				throw new RuntimeException("Failed to process request", e);
			}
		});
	}
}
