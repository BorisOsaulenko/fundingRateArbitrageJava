package com.boris.fundingarbitrage.exchange.impl.kucoin.privaterest;

import com.boris.fundingarbitrage.ObjectMapperSingleton;
import com.boris.fundingarbitrage.exchange.ExchangeContext;
import com.boris.fundingarbitrage.exchange.ExchangeCredentials;
import com.boris.fundingarbitrage.exchange.impl.kucoin.KucoinAuth;
import com.boris.fundingarbitrage.exchange.impl.kucoin.publicrest.PublicEndpoints;
import com.boris.fundingarbitrage.exchange.privatehttp.PrivateHttpClient;
import com.boris.fundingarbitrage.model.assetops.*;
import com.boris.fundingarbitrage.model.contract.Fees;
import com.boris.fundingarbitrage.model.contract.PartialFill;
import com.boris.fundingarbitrage.model.exchange.ExchangeChains;
import com.boris.fundingarbitrage.model.exchange.WalletAddress;
import com.boris.fundingarbitrage.util.https.PrettyHttpClient;
import com.boris.fundingarbitrage.util.logger.Logger;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;

import java.net.URI;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public class KucoinPrivateHttpClient extends PrivateHttpClient {
	private final ObjectMapper mapper = ObjectMapperSingleton.getInstance();
	private final ExchangeCredentials credentials;

	public KucoinPrivateHttpClient(ExchangeContext context) {
		super(context, PrettyHttpClient.getINSTANCE());
		this.credentials = context.credentials;
	}

	@Override
	protected SimpleHttpRequest signRequest(SimpleHttpRequest request) {
		return KucoinAuth.sign(request, credentials);
	}

	private <T, U> CompletableFuture<U> processRequest(
					SimpleHttpRequest request,
					Class<T> responseClass,
					Function<T, U> parser
	) {
		SimpleHttpRequest signedRequest = signRequest(request);
		return this.client.sendNoCodeCheck(signedRequest).thenApply((response) -> {
			try {
				T responseObj = mapper.readValue(response.getBodyText(), responseClass);
				return parser.apply(responseObj);
			} catch (Exception e) {
				Logger.error(String.format("Error parsing KuCoin private rest response: %s", e.getMessage()));
				throw new RuntimeException("Failed to process KuCoin request", e);
			}
		});
	}

	@Override
	protected CompletableFuture<Fees> getTradingFeesSymbol(String symbol) {
		return processRequest(
						PrivateEndpoints.tradingFeesRequestSymbol(symbol),
						PrivateResponses.TradingFeesResponse.class,
						PrivateResponses.TradingFeesResponse::getFees
		);
	}

	@Override
	protected CompletableFuture<Void> changeLeverageSymbol(String symbol, int leverage) {
		return processRequest(
						PrivateEndpoints.changeLeverageRequestSymbol(symbol, leverage),
						PrivateResponses.ChangeLeverageResponse.class,
						(_) -> null
		);
	}

	@Override
	protected CompletableFuture<Void> setMarginModeSymbol(String symbol, MarginMode marginMode) {
		return processRequest(
						PrivateEndpoints.setMarginModeRequestSymbol(symbol, marginMode),
						PrivateResponses.SetMarginModeResponse.class,
						(_) -> null
		);
	}

	@Override
	public CompletableFuture<Double> getSpotUsdtBalance() {
		return processRequest(
						PrivateEndpoints.spotUsdtBalanceRequest(),
						PrivateResponses.SpotUsdtBalanceResponse.class,
						PrivateResponses.SpotUsdtBalanceResponse::get
		);
	}

	@Override
	public CompletableFuture<Double> getFuturesUsdtBalance() {
		return processRequest(
						PrivateEndpoints.futuresUsdtBalanceRequest(),
						PrivateResponses.FuturesUsdtBalanceResponse.class,
						PrivateResponses.FuturesUsdtBalanceResponse::get
		);
	}

	@Override
	protected CompletableFuture<Integer> getMaxLeverageSymbol(String symbol) {
		return processRequest(
						PrivateEndpoints.maxLeverageRequestSymbol(symbol),
						PrivateResponses.MaxLeverageResponse.class,
						PrivateResponses.MaxLeverageResponse::get
		);
	}

	@Override
	public CompletableFuture<ExchangeChains> getSupportedChains() {
		return processRequest(
						PrivateEndpoints.supportedChainsRequest(),
						PrivateResponses.SupportedChainsResponse.class,
						PrivateResponses.SupportedChainsResponse::get
		);
	}

	@Override
	public CompletableFuture<WalletAddress> getUsdtWalletAddress(SupportedChain chain) {
		return processRequest(
						PrivateEndpoints.usdtWalletAddressRequest(chain),
						PrivateResponses.UsdtWalletAddressResponse.class,
						(resp) -> resp.get(chain)
		);
	}

	@Override
	public CompletableFuture<Void> withdrawUsdt(Withdrawal withdrawal) {
		return processRequest(
						PrivateEndpoints.withdrawUsdtRequest(withdrawal),
						PrivateResponses.WithdrawUsdtResponse.class,
						(_) -> null
		);
	}

	@Override
	protected CompletableFuture<String> placeFuturesOrderSymbol(String symbol, FuturesOrder futuresOrder) {
		return processRequest(
						PrivateEndpoints.placeFuturesOrderRequestSymbol(symbol, futuresOrder),
						PrivateResponses.PlaceFuturesOrderResponse.class,
						PrivateResponses.PlaceFuturesOrderResponse::orderId
		);
	}

	@Override
	protected CompletableFuture<List<PartialFill>> getOrderRecordSymbol(
					String orderId,
					String symbol,
					TradeSide tradeSide
	) {
		return processRequest(
						PrivateEndpoints.orderRecordRequestSymbol(orderId, symbol, tradeSide),
						PrivateResponses.GetOrderRecordResponse.class,
						PrivateResponses.GetOrderRecordResponse::get
		);
	}

	@Override
	public CompletableFuture<Void> internalTransfer(InternalTransfer internalTransfer) {
		return processRequest(
						PrivateEndpoints.internalTransferRequest(internalTransfer),
						PrivateResponses.InternalTransferResponse.class,
						(_) -> null
		);
	}

	public CompletableFuture<URI> fetchPrivateWsEndpoint() {
		SimpleHttpRequest request = signRequest(PublicEndpoints.publicWsToken());
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