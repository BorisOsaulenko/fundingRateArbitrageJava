package com.boris.fundingarbitrage.exchange.impl.okx.privaterest;

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
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public class OkxPrivateHttpClient extends PrivateHttpClient {
	private final ObjectMapper mapper = ObjectMapperSingleton.getInstance();
	private final ExchangeCredentials credentials;

	public OkxPrivateHttpClient(ExchangeContext context) {
		super(context, PrettyHttpClient.getINSTANCE());
		this.credentials = context.credentials;
	}

	@Override
	protected SimpleHttpRequest signRequest(SimpleHttpRequest request) {
		try {
			String timestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now().truncatedTo(ChronoUnit.MILLIS));
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

			Logger.log(credentials.apiKey());
			Logger.log(timestamp);
			Logger.log(credentials.passphrase());

			String signature = Signers.signHmacSha256Base64(payload.toString(), credentials.apiSecret());

			request.setHeader("OK-ACCESS-KEY", credentials.apiKey());
			request.setHeader("OK-ACCESS-SIGN", signature);
			request.setHeader("OK-ACCESS-TIMESTAMP", timestamp);
			request.setHeader("OK-ACCESS-PASSPHRASE", credentials.passphrase());
			request.setHeader("Content-Type", "application/json");
			return request;
		} catch (Exception e) {
			Logger.error("Error signing uri for OKX private rest.");
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
				T responseObj = mapper.readValue(response.getBodyText(), responseClass);
				return parser.apply(responseObj);
			} catch (Exception e) {
				Logger.error(String.format("Error parsing OKX private rest response: %s", e.getMessage()));
				throw new RuntimeException("Failed to process request", e);
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
						PrivateEndpoints.changeLeverageRequestSymbol(symbol, leverage, MarginMode.CROSS),
						PrivateResponses.ChangeLeverageResponse.class,
						(resp) -> null
		);
	}

	@Override
	protected CompletableFuture<Void> setMarginModeSymbol(String symbol, MarginMode marginMode) {
		return processRequest(
						PrivateEndpoints.leverageInfoRequestSymbol(symbol, marginMode),
						PrivateResponses.LeverageInfoResponse.class,
						(resp) -> resp.getLever(symbol)
		).thenCompose(lever -> processRequest(
						PrivateEndpoints.changeLeverageRequestSymbol(symbol, lever, marginMode),
						PrivateResponses.ChangeLeverageResponse.class,
						(resp) -> null
		));
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
						PrivateEndpoints.instrumentsRequestSymbol(symbol),
						PrivateResponses.MaxLeverageResponse.class,
						(resp) -> resp.get(symbol)
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
						PrivateEndpoints.supportedChainsRequest(),
						PrivateResponses.CurrencyInfoResponse.class,
						(resp) -> resp.minFee(withdrawal.address().chain())
		).thenCompose(fee -> processRequest(
						PrivateEndpoints.withdrawUsdtRequest(withdrawal, fee),
						PrivateResponses.WithdrawUsdtResponse.class,
						(resp) -> null
		));
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
						PrivateEndpoints.orderRecordRequestSymbol(orderId, symbol),
						PrivateResponses.GetOrderRecordResponse.class,
						(resp) -> resp.get(orderId)
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
