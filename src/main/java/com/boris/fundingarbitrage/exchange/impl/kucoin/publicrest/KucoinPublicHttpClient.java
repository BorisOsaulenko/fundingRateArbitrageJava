package com.boris.fundingarbitrage.exchange.impl.kucoin.publicrest;

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

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public class KucoinPublicHttpClient extends PublicHttpClient {
	private final ObjectMapper mapper = ObjectMapperSingleton.getInstance();

	public KucoinPublicHttpClient(ExchangeContext context) {
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
				Logger.error(String.format("Error parsing KuCoin public rest response: %s", e.getMessage()));
				throw new RuntimeException("Failed to process KuCoin request", e);
			}
		});
	}

	@Override
	protected CompletableFuture<Double> getLotSizeSymbol(String symbol) {
		return processRequest(
						PublicEndpoints.contractDetailRequestSymbol(symbol),
						PublicResponses.ContractResponse.class,
						PublicResponses.ContractResponse::lotSize
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
						PublicResponses.FundingRateResponse::fundingRate
		);
	}

	@Override
	protected CompletableFuture<Map<String, FundingRate>> getFundingRateSymbols(List<String> symbols) {
		return processRequest(
						PublicEndpoints.activeContractsRequest(),
						PublicResponses.ActiveContractsResponse.class,
						(resp) -> resp.fundingRatesBySymbols(symbols)
		);
	}

	@Override
	protected CompletableFuture<Double> getTradingVolume24hSymbol(String symbol) {
		return processRequest(
						PublicEndpoints.contractDetailRequestSymbol(symbol),
						PublicResponses.ContractResponse.class,
						PublicResponses.ContractResponse::volume24h
		);
	}

	@Override
	protected CompletableFuture<Double> getTradingVolume1hSymbol(String symbol) {
		return processRequest(
						PublicEndpoints.klines1hRequestSymbol(symbol),
						PublicResponses.KlinesResponse.class,
						PublicResponses.KlinesResponse::volume1h
		);
	}

	@Override
	protected CompletableFuture<Boolean> checkExistsSymbol(String symbol) {
		SimpleHttpRequest request = PublicEndpoints.contractDetailRequestSymbol(symbol);
		return this.client.sendNoCodeCheck(request).thenApply((response) -> {
			try {
				JsonNode root = mapper.readTree(response.getBodyText());
				String code = root.path("code").asText();
				if (code.isEmpty()) throw new IllegalStateException("KuCoin response code missing");

				if (!"200000".equals(code)) return false;
				JsonNode data = root.get("data");
				if (data == null || !data.isObject()) throw new IllegalStateException("KuCoin response data missing");

				String status = data.path("status").asText();
				if (!"open".equalsIgnoreCase(status)) return false;

				String actual = data.path("symbol").asText();
				if (actual.isEmpty()) throw new IllegalStateException("KuCoin symbol missing in response");

				return symbol.equalsIgnoreCase(actual);
			} catch (RuntimeException e) {
				throw e;
			} catch (Exception e) {
				Logger.error(String.format("Error parsing KuCoin public rest response: %s", e.getMessage()));
				throw new RuntimeException("Failed to process KuCoin request", e);
			}
		});
	}

	@Override
	protected CompletableFuture<Map<String, Boolean>> checkExistsSymbols(List<String> symbols) {
		return processRequest(
						PublicEndpoints.activeContractsRequest(),
						PublicResponses.ActiveContractsResponse.class,
						(resp) -> resp.existsBySymbols(symbols)
		);
	}

	public CompletableFuture<URI> fetchPublicWsEndpoint() {
		SimpleHttpRequest request = PublicEndpoints.publicWsToken();
		return client.send(request).thenApply(response -> {
			try {
				JsonNode root = mapper.readTree(response.getBodyText());
				String code = root.path("code").asText();
				if (code.isEmpty()) {
					throw new IllegalStateException("KuCoin response code missing");
				}
				String msg = root.path("msg").asText();
				if (msg.isEmpty()) msg = null;
				if (!"200000".equals(code)) throw new IllegalStateException("Failed to get KuCoin WS token: " + msg);

				JsonNode data = root.get("data");
				if (data == null || !data.isObject()) {
					throw new IllegalStateException("KuCoin WS token data missing");
				}
				String token = data.path("token").asText();
				if (token.isEmpty()) {
					throw new IllegalStateException("KuCoin WS token missing");
				}
				JsonNode servers = data.get("instanceServers");
				if (servers == null || !servers.isArray()) {
					throw new IllegalStateException("KuCoin WS token instance servers missing");
				}
				if (servers.isEmpty()) throw new IllegalStateException("KuCoin WS token response has no instance servers");

				JsonNode server = servers.get(0);
				String endpoint = server.path("endpoint").asText();
				if (endpoint.isEmpty()) {
					throw new IllegalStateException("KuCoin WS endpoint missing");
				}
				String connectId = UUID.randomUUID().toString();
				return URI.create(endpoint + "?token=" + token + "&connectId=" + connectId);
			} catch (Exception ex) {
				Logger.error("Failed to get KuCoin WS token: " + ex.getMessage());
				throw new RuntimeException("Failed to get KuCoin WS token", ex);
			}
		});
	}
}
