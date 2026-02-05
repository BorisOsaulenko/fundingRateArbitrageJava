package com.boris.fundingarbitrage.exchange.impl.gate.publicrest;

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

public class GatePublicHttpClient extends PublicHttpClient {
	private final ObjectMapper mapper = ObjectMapperSingleton.getInstance();

	public GatePublicHttpClient(ExchangeContext context) {
		super(context, PrettyHttpClient.getINSTANCE());
	}

	private <T, U> CompletableFuture<U> processRequest(
					SimpleHttpRequest request,
					Class<T> responseClass,
					Function<T, U> parser
	) {
		Logger.getInstance().log(request.toString());
		return this.client.send(request).thenApply((response) -> {
			try {
				Logger.getInstance().log(response.toString());
				T responseObj = mapper.readValue(response.getBodyText(), responseClass);
				return parser.apply(responseObj);
			} catch (Exception e) {
				Logger
								.getInstance()
								.error(String.format("Error parsing public rest response: %s", e.getMessage()));
				throw new RuntimeException("Failed to process request", e);
			}
		});
	}

	@Override
	protected CompletableFuture<Double> getLotSizeSymbol(String symbol) {
		return processRequest(
						PublicEndpoints.contractRequestSymbol(symbol),
						PublicResponses.ContractResponse.class,
						PublicResponses.ContractResponse::lotSize
		);
	}

	@Override
	protected CompletableFuture<BookTicker> getBookTickerSymbol(String symbol) {
		return processRequest(
						PublicEndpoints.orderBookRequestSymbol(symbol),
						PublicResponses.OrderBookResponse.class,
						PublicResponses.OrderBookResponse::bookTicker
		);
	}

	@Override
	protected CompletableFuture<FundingRate> getFundingRateSymbol(String symbol) {
		return processRequest(
						PublicEndpoints.contractRequestSymbol(symbol),
						PublicResponses.ContractResponse.class,
						PublicResponses.ContractResponse::fundingRate
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
						PublicEndpoints.candlesticks1hRequestSymbol(symbol),
						PublicResponses.CandlesticksResponse.class,
						PublicResponses.CandlesticksResponse::volume1h
		);
	}

	@Override
	protected CompletableFuture<Boolean> checkExistsSymbol(String symbol) {
		SimpleHttpRequest request = PublicEndpoints.contractRequestSymbol(symbol);
		return this.client.sendNoCodeCheck(request).thenApply((response) -> {
			try {
				int status = response.getCode();
				JsonNode body = mapper.readTree(response.getBodyText());
				String label = body.path("label").asText();
				if ("CONTRACT_NOT_FOUND".equals(label) && status == 400) return false;

				if (status >= 200 && status < 300) return true;
				throw new RuntimeException(String.format(
								"Failed to check symbol existence: %d %s",
								status,
								response.getBodyText()
				));
			} catch (Exception e) {
				Logger
								.getInstance()
								.error(String.format("Error parsing public rest response: %s", e.getMessage()));
				throw new RuntimeException("Failed to process request", e);
			}
		});
	}
}
