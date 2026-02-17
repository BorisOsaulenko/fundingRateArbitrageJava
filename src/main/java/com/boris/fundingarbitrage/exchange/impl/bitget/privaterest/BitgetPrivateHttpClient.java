package com.boris.fundingarbitrage.exchange.impl.bitget.privaterest;

import com.boris.fundingarbitrage.ObjectMapperSingleton;
import com.boris.fundingarbitrage.exchange.ExchangeContext;
import com.boris.fundingarbitrage.exchange.ExchangeCredentials;
import com.boris.fundingarbitrage.exchange.privatehttp.PrivateHttpClient;
import com.boris.fundingarbitrage.model.assetops.*;
import com.boris.fundingarbitrage.model.contract.Fees;
import com.boris.fundingarbitrage.model.contract.PartialFill;
import com.boris.fundingarbitrage.model.exchange.ExchangeChains;
import com.boris.fundingarbitrage.model.exchange.WalletAddress;
import com.boris.fundingarbitrage.util.cryptography.Signers;
import com.boris.fundingarbitrage.util.https.PrettyHttpClient;
import com.boris.fundingarbitrage.util.logger.Logger;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public class BitgetPrivateHttpClient extends PrivateHttpClient {
	private final ObjectMapper mapper = ObjectMapperSingleton.getInstance();
	private final ExchangeCredentials credentials;

	public BitgetPrivateHttpClient(ExchangeContext context) {
		super(context, PrettyHttpClient.getINSTANCE());
		this.credentials = context.credentials;
	}

	@Override
	protected SimpleHttpRequest signRequest(SimpleHttpRequest request) {
		try {
			String timestamp = String.valueOf(System.currentTimeMillis());
			String method = request.getMethod().toUpperCase();
			URI uri = request.getUri();
			String path = uri.getRawPath();
			String query = uri.getRawQuery();
			String body = request.getBodyText();
			if (body == null) body = "";

			StringBuilder payload = new StringBuilder();
			payload.append(timestamp).append(method).append(path);
			if (query != null && !query.isEmpty()) payload.append("?").append(query);
			payload.append(body);

			String signature = Signers.signHmacSha256Base64(payload.toString(), credentials.apiSecret());

			request.setHeader("ACCESS-KEY", credentials.apiKey());
			request.setHeader("ACCESS-SIGN", signature);
			request.setHeader("ACCESS-TIMESTAMP", timestamp);
			request.setHeader("ACCESS-PASSPHRASE", credentials.passphrase());
			request.setHeader("Content-Type", "application/json");
			request.setHeader("locale", "en-US");
			return request;

		} catch (Exception e) {
			Logger.error("Error signing uri for bitget private rest.");
			throw new RuntimeException(e);
		}
	}

	private <T, U> CompletableFuture<U> processRequest(
					SimpleHttpRequest request,
					Class<T> responseClass,
					Function<T, U> parser
	) {
		SimpleHttpRequest signedRequest = signRequest(request);
		return this.client.sendNoCodeCheck(signedRequest).thenApply((response) -> {
			try {
				T responseObj = mapper.readValue(response.getBodyBytes(), responseClass);
				return parser.apply(responseObj);
			} catch (Exception e) {
				Logger.error(String.format("Error parsing private rest response: %s", e.getMessage()));
				throw new RuntimeException("Failed to process request", e);
			}
		});
	}

	@Override
	protected CompletableFuture<Map<String, Fees>> getTradingFeesSymbolBatch(List<String> symbols) {
		return processRequest(
						PrivateEndpoints.tradingFeesRequestSymbols(),
						PrivateResponses.TradingFeesSymbolsResponse.class,
						(resp) -> resp.getFeesBySymbols(symbols)
		);
	}

	@Override
	protected CompletableFuture<Void> changeLeverageSymbol(String symbol, int leverage) {
		return processRequest(
						PrivateEndpoints.changeLeverageRequestSymbol(symbol, leverage),
						PrivateResponses.ChangeLeverageResponse.class,
						(resp) -> null
		);
	}

	@Override
	protected CompletableFuture<Void> setMarginModeSymbol(String symbol, MarginMode marginMode) {
		return processRequest(
						PrivateEndpoints.setMarginModeRequestSymbol(symbol, marginMode),
						PrivateResponses.SetMarginModeResponse.class,
						(resp) -> null
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
	protected CompletableFuture<Integer> getMaxLeverageSymbolBatch(String symbol) {
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
						(resp) -> null
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
						(resp) -> null
		);
	}
}
