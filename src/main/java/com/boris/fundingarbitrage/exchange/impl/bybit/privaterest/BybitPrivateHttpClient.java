package com.boris.fundingarbitrage.exchange.impl.bybit.privaterest;

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

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class BybitPrivateHttpClient extends PrivateHttpClient {
	private static final String recvWindow = "5000";
	private final ExchangeCredentials credentials;
	private final RequestProcessingClientWrapper requestWrapper = new RequestProcessingClientWrapper(this.client);

	public BybitPrivateHttpClient(ExchangeContext context) {
		super(context, PrettyHttpClient.getINSTANCE());
		this.credentials = context.credentials;
	}

	@Override
	protected SimpleHttpRequest signRequest(SimpleHttpRequest request) {
		try {
			String timestamp = String.valueOf(System.currentTimeMillis());
			String method = request.getMethod().toUpperCase();
			URI uri = request.getUri();
			String query = uri.getRawQuery();
			String body = request.getBodyText();
			if (body == null) body = "";

			StringBuilder payload = new StringBuilder();
			payload.append(timestamp).append(credentials.apiKey()).append(recvWindow);
			if ("GET".equals(method)) {
				payload.append(query == null ? "" : query);
			} else {
				payload.append(body);
			}

			String signature = Signers.signHmacSha256Hex(payload.toString(), credentials.apiSecret());

			request.setHeader("X-BAPI-API-KEY", credentials.apiKey());
			request.setHeader("X-BAPI-SIGN", signature);
			request.setHeader("X-BAPI-TIMESTAMP", timestamp);
			request.setHeader("X-BAPI-RECV-WINDOW", recvWindow);
			request.setHeader("Content-Type", "application/json");

			return request;
		} catch (Exception e) {
			Logger.error(e.getMessage());
			throw new RuntimeException(e);
		}
	}

	@Override
	protected CompletableFuture<Map<String, Fees>> getFuturesFeesSymbolBatch() {
		return requestWrapper.processRequest(
						signRequest(PrivateEndpoints.tradingFeesRequest()),
						PrivateResponses.FuturesTradingFeesResponse.class,
						PrivateResponses.FuturesTradingFeesResponse::getFeesBySymbols
		);
	}

	@Override
	protected CompletableFuture<Map<String, Fees>> getSpotFeesSymbolBatch() {
		return requestWrapper.processRequest(
						signRequest(PrivateEndpoints.spotTradingFeesRequest()),
						PrivateResponses.SpotTradingFeesResponse.class,
						PrivateResponses.SpotTradingFeesResponse::getFeesSymbolMap
		);
	}

	@Override
	protected CompletableFuture<Void> changeLeverageSymbol(String symbol, int leverage) {
		return requestWrapper.processRequest(
						signRequest(PrivateEndpoints.changeLeverageRequest(symbol, leverage)),
						PrivateResponses.ChangeLeverageResponse.class,
						(_) -> null
		);
	}

	@Override
	protected CompletableFuture<Void> setMarginModeSymbol(String symbol, MarginMode marginMode) {
		return requestWrapper.processRequest(
						signRequest(PrivateEndpoints.setMarginModeRequest(symbol, marginMode)),
						PrivateResponses.SetMarginModeResponse.class,
						(resp) -> null
		);
	}

	@Override
	public CompletableFuture<BigDecimal> getSpotUsdtBalance() {
		return requestWrapper.processRequest(
						signRequest(PrivateEndpoints.spotUsdtBalanceRequest()),
						PrivateResponses.SpotUsdtBalanceResponse.class,
						PrivateResponses.SpotUsdtBalanceResponse::get
		);
	}

	@Override
	public CompletableFuture<BigDecimal> getFuturesUsdtBalance() {
		return requestWrapper.processRequest(
						signRequest(PrivateEndpoints.futuresUsdtBalanceRequest()),
						PrivateResponses.FuturesUsdtBalanceResponse.class,
						PrivateResponses.FuturesUsdtBalanceResponse::get
		);
	}

	@Override
	protected CompletableFuture<Map<String, Integer>> getMaxLeverageSymbolBatch() {
		Map<String, Integer> leverageMap = new java.util.HashMap<>();
		return requestWrapper.processPaginatedRequest(
						PrivateEndpoints::maxLeverageRequest, PrivateResponses.MaxLeverageResponse.class, (res) -> {
							leverageMap.putAll(res.getMaxLeverages());
						}, null
		).thenApply(_ -> leverageMap);
	}

	@Override
	public CompletableFuture<ExchangeChains> getSupportedChains() {
		return requestWrapper.processRequest(
						signRequest(PrivateEndpoints.chainsRequest()),
						PrivateResponses.SupportedChainsResponse.class,
						PrivateResponses.SupportedChainsResponse::getSupportedChains
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
						(resp) -> null
		);
	}

	@Override
	protected CompletableFuture<String> placeFuturesOrderSymbol(String symbol, FuturesOrder futuresOrder) {
		return requestWrapper.processRequest(
						signRequest(PrivateEndpoints.placeFuturesOrderRequest(symbol, futuresOrder)),
						PrivateResponses.PlaceFuturesOrderResponse.class,
						PrivateResponses.PlaceFuturesOrderResponse::orderId
		);
	}

	@Override
	protected CompletableFuture<List<PartialFill>> getFuturesOrderRecordSymbol(
					String orderId,
					String symbol,
					TradeSide tradeSide
	) {
		return requestWrapper.processRequest(
						signRequest(PrivateEndpoints.orderRecordRequest(orderId, symbol, tradeSide)),
						PrivateResponses.GetOrderRecordResponse.class,
						PrivateResponses.GetOrderRecordResponse::get
		);
	}

	@Override
	protected CompletableFuture<List<PartialFill>> getSpotOrderRecordSymbol(
					String orderId,
					String symbol,
					TradeSide tradeSide
	) {
		return requestWrapper.processRequest(
						signRequest(PrivateEndpoints.spotOrderRecordRequest(orderId, symbol)),
						PrivateResponses.GetOrderRecordResponse.class,
						PrivateResponses.GetOrderRecordResponse::get
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
