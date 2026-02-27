package com.boris.fundingarbitrage.exchange.impl.okx.privaterest;

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
import com.boris.fundingarbitrage.util.https.RequestProcessingClientWrapper;
import com.boris.fundingarbitrage.util.logger.Logger;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;

import java.net.URI;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class OkxPrivateHttpClient extends PrivateHttpClient {
	private final ExchangeCredentials credentials;
	private final RequestProcessingClientWrapper requestWrapper = new RequestProcessingClientWrapper(this.client);

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

	@Override
	protected CompletableFuture<Map<String, Fees>> getTradingFeesSymbolBatch() {
		CompletableFuture<Map<Integer, PrivateResponses.FeeGroup>> feeGroupMapFuture = requestWrapper.processRequest(
						signRequest(PrivateEndpoints.tradingFeesRequest()),
						PrivateResponses.TradingFeesSymbolsResponse.class,
						PrivateResponses.TradingFeesSymbolsResponse::getFeeGroups
		);

		CompletableFuture<Map<String, Integer>> instrumentsResponseFuture = requestWrapper.processRequest(
						signRequest(PrivateEndpoints.instrumentsRequest()),
						PrivateResponses.InstrumentsResponse.class,
						PrivateResponses.InstrumentsResponse::getFeeGroupId
		);

		return CompletableFuture.allOf(feeGroupMapFuture, instrumentsResponseFuture).thenApply(_ -> {
			Map<String, Integer> instruments = instrumentsResponseFuture.join();
			Map<Integer, PrivateResponses.FeeGroup> feeGroupMap = feeGroupMapFuture.join();
			Map<String, Fees> result = new HashMap<>();

			instruments.forEach((String symbol, Integer feeGroupId) -> {
				double maker = -feeGroupMap.get(feeGroupId).maker(); // okx expresses fees as negative
				double taker = -feeGroupMap.get(feeGroupId).taker();
				result.put(symbol, new Fees(maker, taker, maker, taker, Instant.now()));
			});

			return result;
		});
	}

	@Override
	protected CompletableFuture<Void> changeLeverageSymbol(String symbol, int leverage) {
		return requestWrapper.processRequest(
						signRequest(PrivateEndpoints.changeLeverageRequestSymbol(symbol, leverage, MarginMode.CROSS)),
						PrivateResponses.ChangeLeverageResponse.class,
						(resp) -> null
		);
	}

	@Override
	protected CompletableFuture<Void> setMarginModeSymbol(String symbol, MarginMode marginMode) {
		return requestWrapper.processRequest(
						signRequest(PrivateEndpoints.leverageInfoRequestSymbol(symbol, marginMode)),
						PrivateResponses.LeverageInfoResponse.class,
						(resp) -> resp.getLever(symbol)
		).thenCompose(lever -> requestWrapper.processRequest(
						signRequest(PrivateEndpoints.changeLeverageRequestSymbol(symbol, lever, marginMode)),
						PrivateResponses.ChangeLeverageResponse.class,
						(resp) -> null
		));
	}

	@Override
	public CompletableFuture<Double> getSpotUsdtBalance() {
		return requestWrapper.processRequest(
						signRequest(PrivateEndpoints.spotUsdtBalanceRequest()),
						PrivateResponses.SpotUsdtBalanceResponse.class,
						PrivateResponses.SpotUsdtBalanceResponse::get
		);
	}

	@Override
	public CompletableFuture<Double> getFuturesUsdtBalance() {
		return requestWrapper.processRequest(
						signRequest(PrivateEndpoints.futuresUsdtBalanceRequest()),
						PrivateResponses.FuturesUsdtBalanceResponse.class,
						PrivateResponses.FuturesUsdtBalanceResponse::get
		);
	}

	@Override
	protected CompletableFuture<Map<String, Integer>> getMaxLeverageSymbolBatch() {
		return requestWrapper.processRequest(
						signRequest(PrivateEndpoints.instrumentsRequest()),
						PrivateResponses.InstrumentsResponse.class,
						PrivateResponses.InstrumentsResponse::getMaxLeverage
		);
	}

	@Override
	public CompletableFuture<ExchangeChains> getSupportedChains() {
		return requestWrapper.processRequest(
						signRequest(PrivateEndpoints.supportedChainsRequest()),
						PrivateResponses.SupportedChainsResponse.class,
						PrivateResponses.SupportedChainsResponse::get
		);
	}

	@Override
	public CompletableFuture<WalletAddress> getUsdtWalletAddress(SupportedChain chain) {
		return requestWrapper.processRequest(
						signRequest(PrivateEndpoints.usdtWalletAddressRequest(chain)),
						PrivateResponses.UsdtWalletAddressResponse.class,
						(resp) -> resp.get(chain)
		);
	}

	@Override
	public CompletableFuture<Void> withdrawUsdt(Withdrawal withdrawal) {
		return requestWrapper.processRequest(
						signRequest(PrivateEndpoints.withdrawUsdtRequest(withdrawal)),
						PrivateResponses.WithdrawUsdtResponse.class,
						(_) -> null
		);
	}

	@Override
	protected CompletableFuture<String> placeFuturesOrderSymbol(String symbol, FuturesOrder futuresOrder) {
		return requestWrapper.processRequest(
						signRequest(PrivateEndpoints.placeFuturesOrderRequestSymbol(symbol, futuresOrder)),
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
		return requestWrapper.processRequest(
						signRequest(PrivateEndpoints.orderRecordRequestSymbol(orderId, symbol)),
						PrivateResponses.GetOrderRecordResponse.class,
						(resp) -> resp.get(orderId)
		);
	}

	@Override
	public CompletableFuture<Void> internalTransfer(InternalTransfer internalTransfer) {
		return requestWrapper.processRequest(
						signRequest(PrivateEndpoints.internalTransferRequest(internalTransfer)),
						PrivateResponses.InternalTransferResponse.class,
						(resp) -> null
		);
	}
}
